package com.mxcfe.key.action

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.net.Uri
import android.os.Process
import android.util.Log
import com.mxcfe.key.privilege.ShizukuAccess
import com.mxcfe.key.privilege.ShizukuShell
import org.xmlpull.v1.XmlPullParser

data class AppShortcut(
    val packageName: String,
    val id: String,
    val label: String,
    val intents: List<Intent> = emptyList(),
)

object AppShortcutCatalog {
    private const val TAG = "AppShortcutCatalog"
    private const val META_SHORTCUTS = "android.app.shortcuts"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val TAG_SHORTCUT = "shortcut"
    private const val TAG_INTENT = "intent"
    private const val TAG_EXTRA = "extra"

    // LauncherApps.ShortcutQuery: DYNAMIC|PINNED|MANIFEST|CACHED
    private const val MATCH_FLAGS = 1 or 2 or 8 or 16

    fun list(context: Context, packageName: String): List<AppShortcut> {
        val seen = linkedMapOf<String, AppShortcut>()
        fun put(shortcut: AppShortcut) {
            val existing = seen[shortcut.id]
            if (existing == null) {
                seen[shortcut.id] = shortcut
            } else if (existing.intents.isEmpty() && shortcut.intents.isNotEmpty()) {
                seen[shortcut.id] = shortcut.copy(label = existing.label.ifBlank { shortcut.label })
            } else if (existing.label == existing.id && shortcut.label != shortcut.id) {
                seen[shortcut.id] = existing.copy(label = shortcut.label)
            }
        }
        // Deep links first so QQ/微信 etc. always have usable entries.
        WellKnownAppShortcuts.forPackage(packageName).forEach(::put)
        listFromShell(packageName).forEach(::put)
        listFromManifest(context, packageName).forEach(::put)
        return seen.values.sortedBy { it.label.lowercase() }
    }

    fun find(context: Context, packageName: String, shortcutId: String): AppShortcut? {
        WellKnownAppShortcuts.forPackage(packageName)
            .firstOrNull { it.id == shortcutId }
            ?.let { return it }
        listFromShell(packageName).firstOrNull { it.id == shortcutId }?.let { return it }
        return listFromManifest(context, packageName).firstOrNull { it.id == shortcutId }
    }

    private fun listFromShell(packageName: String): List<AppShortcut> {
        if (!ShizukuAccess.granted()) return emptyList()
        val user = Process.myUid() / 100_000
        val commands = listOf(
            "cmd shortcut get-shortcuts --user $user --flags $MATCH_FLAGS $packageName",
            "cmd shortcut get-shortcuts --user $user $packageName",
            "cmd shortcut get-shortcuts --package $packageName",
            "cmd shortcut get-shortcuts $packageName",
            "dumpsys shortcut $packageName",
            // Full dump last; can be large on ColorOS.
            "dumpsys shortcut",
        )
        for (command in commands) {
            val timeout = if (command == "dumpsys shortcut") 12L else 8L
            val output = ShizukuShell.execOutput(command, timeout) ?: continue
            if (output.contains("Can't find service", ignoreCase = true)) continue
            if (output.contains("Unknown command", ignoreCase = true) &&
                !output.contains("ShortcutInfo")
            ) {
                continue
            }
            val parsed = parseShellShortcutOutput(output, packageName)
            if (parsed.isNotEmpty()) {
                Log.i(TAG, "shell listed ${parsed.size} shortcuts for $packageName via: $command")
                return parsed
            }
            Log.i(TAG, "shell returned ${output.length} chars but 0 shortcuts via: $command")
        }
        return emptyList()
    }

    internal fun parseShellShortcutOutput(output: String, packageName: String): List<AppShortcut> {
        val fromBlocks = parseShortcutInfoBlocks(output, packageName)
        if (fromBlocks.isNotEmpty()) return fromBlocks
        return parseIndentedShortcutDump(output, packageName)
    }

    private fun parseShortcutInfoBlocks(output: String, packageName: String): List<AppShortcut> {
        val result = linkedMapOf<String, AppShortcut>()
        val regex = Regex("""ShortcutInfo\s*\{([\s\S]*?)\n\s*\}""")
        for (match in regex.findAll(output)) {
            val body = match.groupValues[1]
            val pkg = fieldValue(body, "packageName") ?: continue
            if (pkg != packageName) continue
            val id = fieldValue(body, "id")?.trim().orEmpty()
            if (id.isEmpty()) continue
            if (body.contains("[Disabled]", ignoreCase = true)) continue
            val shortLabel = fieldValue(body, "shortLabel").orEmpty()
            val longLabel = fieldValue(body, "longLabel").orEmpty()
            val label = shortLabel.ifBlank { longLabel }.ifBlank { id }
            val intents = parseIntentsField(body, packageName)
            result[id] = AppShortcut(packageName, id, label, intents)
        }
        if (result.isNotEmpty()) return result.values.toList()

        // Single-line ShortcutInfo dumps
        val lineRegex = Regex("""ShortcutInfo \{([^}]*)\}""")
        for (match in lineRegex.findAll(output)) {
            val body = match.groupValues[1]
            val pkg = fieldValue(body, "packageName") ?: continue
            if (pkg != packageName) continue
            val id = fieldValue(body, "id")?.trim().orEmpty()
            if (id.isEmpty()) continue
            val shortLabel = fieldValue(body, "shortLabel").orEmpty()
            val longLabel = fieldValue(body, "longLabel").orEmpty()
            val label = shortLabel.ifBlank { longLabel }.ifBlank { id }
            val intents = parseIntentsField(body, packageName)
            result[id] = AppShortcut(packageName, id, label, intents)
        }
        return result.values.toList()
    }

    private fun parseIndentedShortcutDump(output: String, packageName: String): List<AppShortcut> {
        val shortcuts = mutableListOf<AppShortcut>()
        val hasPackageHeader = output.contains("$packageName:") ||
            output.contains("Package: $packageName")
        // Only treat whole dump as one package when it was clearly package-scoped.
        var inPackage = !hasPackageHeader && output.length < 8_000
        var currentId: String? = null
        var shortLabel = ""
        var longLabel = ""
        fun flush() {
            val id = currentId ?: return
            shortcuts += AppShortcut(
                packageName = packageName,
                id = id,
                label = shortLabel.ifBlank { longLabel }.ifBlank { id },
            )
            currentId = null
            shortLabel = ""
            longLabel = ""
        }
        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val indented = line.startsWith(" ") || line.startsWith("\t")
            when {
                trimmed.startsWith("Package:") -> {
                    flush()
                    inPackage = trimmed.contains(packageName)
                    continue
                }
                !indented && (trimmed == "$packageName:" || trimmed.startsWith("$packageName ")) -> {
                    flush()
                    inPackage = true
                    continue
                }
                !indented && trimmed.endsWith(":") && trimmed.contains('.') -> {
                    flush()
                    inPackage = trimmed.removeSuffix(":") == packageName
                    continue
                }
            }
            if (!inPackage) continue
            if (indented && trimmed.endsWith(":") && !trimmed.contains(' ') &&
                !trimmed.startsWith("ShortLabel", true) &&
                !trimmed.startsWith("LongLabel", true)
            ) {
                flush()
                currentId = trimmed.removeSuffix(":")
                continue
            }
            if (currentId != null) {
                when {
                    trimmed.startsWith("ShortLabel:", true) ->
                        shortLabel = trimmed.substringAfter(':').trim()
                    trimmed.startsWith("LongLabel:", true) ->
                        longLabel = trimmed.substringAfter(':').trim()
                }
            }
        }
        flush()
        return shortcuts
    }

    private fun fieldValue(body: String, key: String): String? {
        val regex = Regex("""(?:^|[\s,])$key=([^,\n]+)""")
        val raw = regex.find(body)?.groupValues?.get(1)?.trim() ?: return null
        return raw.removeSuffix(",").trim()
    }

    private fun parseIntentsField(body: String, packageName: String): List<Intent> {
        val start = body.indexOf("intents=")
        if (start < 0) return emptyList()
        val slice = body.substring(start + "intents=".length).trim()
        if (slice.startsWith("null")) return emptyList()
        if (!slice.startsWith("[")) return emptyList()
        val inner = slice.removePrefix("[").substringBeforeLast(']').trim()
        if (inner.isEmpty()) return emptyList()
        val chunks = splitIntentChunks(inner)
        return chunks.mapNotNull { parseIntentDump(it, packageName) }
    }

    private fun splitIntentChunks(inner: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c == '{') depth++
            if (c == '}') depth--
            if (c == ',' && depth == 0 && inner.getOrNull(i + 1) == ' ') {
                val piece = current.toString().trim()
                if (piece.isNotEmpty()) result += piece
                current.clear()
                i += 2
                continue
            }
            current.append(c)
            i++
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) result += last
        return result
    }

    internal fun parseIntentDump(chunk: String, packageName: String): Intent? {
        val intentPart = chunk.substringBefore('/').trim()
        val extrasPart = chunk.substringAfter('/', missingDelimiterValue = "").trim()
        val content = intentPart
            .removePrefix("Intent")
            .trim()
            .removePrefix("{")
            .removeSuffix("}")
            .trim()
        if (content.isEmpty()) return null
        val intent = Intent()
        Regex("""\bact=([^\s]+)""").find(content)?.groupValues?.get(1)?.let { intent.action = it }
        Regex("""\bdat=([^\s]+)""").find(content)?.groupValues?.get(1)?.let {
            intent.data = Uri.parse(it)
        }
        Regex("""\btyp=([^\s]+)""").find(content)?.groupValues?.get(1)?.let { intent.type = it }
        Regex("""\bcmp=([^\s]+)""").find(content)?.groupValues?.get(1)?.let { cmp ->
            val slash = cmp.indexOf('/')
            if (slash > 0) {
                val pkg = cmp.substring(0, slash)
                val cls = cmp.substring(slash + 1).let { c ->
                    if (c.startsWith('.')) pkg + c else c
                }
                intent.component = ComponentName(pkg, cls)
            }
        }
        Regex("""\bpkg=([^\s]+)""").find(content)?.groupValues?.get(1)?.let {
            if (intent.component == null) intent.setPackage(it)
        }
        if (intent.component == null && intent.`package` == null) {
            intent.setPackage(packageName)
        }
        if (intent.action == null && intent.component == null && intent.data == null) {
            return null
        }
        parsePersistableExtras(extrasPart).forEach { (key, value) ->
            intent.putExtra(key, value)
        }
        return intent
    }

    private fun parsePersistableExtras(raw: String): Map<String, String> {
        if (raw.isBlank() || raw == "null") return emptyMap()
        val inner = raw
            .substringAfter('{', missingDelimiterValue = "")
            .substringBeforeLast('}', missingDelimiterValue = "")
            .trim()
        if (inner.isEmpty()) return emptyMap()
        val map = linkedMapOf<String, String>()
        for (part in inner.split(',')) {
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
        return map
    }

    private fun listFromManifest(context: Context, packageName: String): List<AppShortcut> {
        val pm = context.packageManager
        val resources = try {
            pm.getResourcesForApplication(packageName)
        } catch (error: Throwable) {
            Log.w(TAG, "resources missing for $packageName", error)
            return emptyList()
        }
        val seen = linkedMapOf<String, AppShortcut>()
        for (parser in shortcutParsers(pm, packageName, resources)) {
            parser.use { xml ->
                parseShortcuts(xml, packageName, resources).forEach { shortcut ->
                    seen.putIfAbsent(shortcut.id, shortcut)
                }
            }
        }
        return seen.values.toList()
    }

    private fun shortcutParsers(
        pm: PackageManager,
        packageName: String,
        resources: Resources,
    ): List<XmlResourceParser> {
        val parsers = mutableListOf<XmlResourceParser>()
        val seenResIds = mutableSetOf<Int>()

        fun addResId(resId: Int, applicationInfo: android.content.pm.ApplicationInfo?) {
            if (resId == 0 || !seenResIds.add(resId)) return
            try {
                val parser = pm.getXml(packageName, resId, applicationInfo)
                    ?: resources.getXml(resId)
                parsers += parser
            } catch (error: Throwable) {
                Log.w(TAG, "getXml failed $packageName/$resId", error)
            }
        }

        fun addFromMeta(meta: android.os.Bundle?, applicationInfo: android.content.pm.ApplicationInfo?) {
            if (meta == null) return
            addResId(meta.getInt(META_SHORTCUTS, 0), applicationInfo)
        }

        try {
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            addFromMeta(appInfo.metaData, appInfo)
        } catch (error: Throwable) {
            Log.w(TAG, "application meta failed $packageName", error)
        }

        val launcher = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        val activities = pm.queryIntentActivities(launcher, PackageManager.GET_META_DATA)
        for (resolve in activities) {
            val activity = resolve.activityInfo ?: continue
            addFromMeta(activity.metaData, activity.applicationInfo)
        }

        // Fallback: many apps ship res/xml/shortcuts.xml even if meta lookup fails.
        try {
            val resId = resources.getIdentifier("shortcuts", "xml", packageName)
            addResId(resId, pm.getApplicationInfo(packageName, 0))
        } catch (_: Throwable) {
        }
        return parsers
    }

    private fun parseShortcuts(
        parser: XmlResourceParser,
        packageName: String,
        resources: Resources,
    ): List<AppShortcut> {
        val result = mutableListOf<AppShortcut>()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == TAG_SHORTCUT) {
                    parseShortcut(parser, packageName, resources)?.let(result::add)
                }
                event = parser.next()
            }
        } catch (error: Throwable) {
            Log.w(TAG, "parse failed $packageName", error)
        }
        return result
    }

    private fun parseShortcut(
        parser: XmlResourceParser,
        packageName: String,
        resources: Resources,
    ): AppShortcut? {
        val id = attr(parser, "shortcutId")?.trim().orEmpty()
        val enabled = parser.getAttributeBooleanValue(ANDROID_NS, "enabled", true) &&
            parser.getAttributeBooleanValue(null, "enabled", true)
        if (id.isEmpty() || !enabled) {
            skip(parser)
            return null
        }
        val shortLabel = resolveLabel(parser, resources, "shortcutShortLabel")
        val longLabel = resolveLabel(parser, resources, "shortcutLongLabel")
        val label = shortLabel.ifBlank { longLabel }.ifBlank { id }
        val intents = mutableListOf<Intent>()
        val depth = parser.depth
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth <= depth)
        ) {
            if (event == XmlPullParser.START_TAG && parser.name == TAG_INTENT) {
                parseIntent(parser, packageName)?.let(intents::add)
            }
            event = parser.next()
        }
        if (intents.isEmpty()) return null
        return AppShortcut(packageName, id, label, intents)
    }

    private fun parseIntent(parser: XmlResourceParser, packageName: String): Intent? {
        val action = attr(parser, "action") ?: Intent.ACTION_VIEW
        val data = attr(parser, "data")
        val mimeType = attr(parser, "mimeType")
        val targetPackage = attr(parser, "targetPackage") ?: packageName
        val targetClass = attr(parser, "targetClass")
        val intent = Intent(action)
        if (!data.isNullOrBlank()) {
            val uri = Uri.parse(data)
            if (!mimeType.isNullOrBlank()) {
                intent.setDataAndType(uri, mimeType)
            } else {
                intent.data = uri
            }
        } else if (!mimeType.isNullOrBlank()) {
            intent.type = mimeType
        }
        if (!targetClass.isNullOrBlank()) {
            intent.component = ComponentName(targetPackage, targetClass)
        } else {
            intent.setPackage(targetPackage)
        }
        val depth = parser.depth
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth <= depth)
        ) {
            if (event == XmlPullParser.START_TAG && parser.name == TAG_EXTRA) {
                val name = attr(parser, "name")
                val value = attr(parser, "value")
                if (!name.isNullOrBlank() && value != null) {
                    intent.putExtra(name, value)
                }
            }
            event = parser.next()
        }
        return intent
    }

    private fun resolveLabel(
        parser: XmlResourceParser,
        resources: Resources,
        attribute: String,
    ): String {
        val resId = parser.getAttributeResourceValue(ANDROID_NS, attribute, 0)
            .takeIf { it != 0 }
            ?: parser.getAttributeResourceValue(null, attribute, 0)
        if (resId != 0) {
            return try {
                resources.getString(resId)
            } catch (_: Throwable) {
                ""
            }
        }
        return attr(parser, attribute).orEmpty()
    }

    private fun attr(parser: XmlResourceParser, name: String): String? {
        return parser.getAttributeValue(ANDROID_NS, name)
            ?: parser.getAttributeValue(null, name)
    }

    private fun skip(parser: XmlResourceParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }
}
