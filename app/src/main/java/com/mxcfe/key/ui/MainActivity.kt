package com.mxcfe.key.ui

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mxcfe.key.R
import com.mxcfe.key.action.ActionId
import com.mxcfe.key.prefs.Prefs
import com.mxcfe.key.privilege.ShizukuAccess
import com.mxcfe.key.service.ActionKeyService
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { Prefs(this) }
            SideKeyTheme(prefs.themeMode()) {
                MainScreen(
                    prefs = prefs,
                    onPickAction = {
                        startActivity(Intent(this, ActionPickerActivity::class.java))
                    },
                    onRequestShizuku = { requestShizuku() },
                    onRequestBattery = { requestBattery() },
                    onHideRecents = { hide ->
                        prefs.setHideFromRecents(hide)
                        applyHideFromRecents(hide)
                    },
                    onTheme = { mode ->
                        prefs.setThemeMode(mode)
                        recreate()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyHideFromRecents(Prefs(this).hideFromRecents())
    }

    private fun applyHideFromRecents(hide: Boolean) {
        getSystemService(ActivityManager::class.java)
            .appTasks
            .firstOrNull()
            ?.setExcludeFromRecents(hide)
    }

    private fun requestShizuku() {
        if (!ShizukuAccess.binderAlive()) {
            val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launch != null) startActivity(launch)
            return
        }
        if (!ShizukuAccess.granted()) {
            Shizuku.requestPermission(ShizukuAccess.REQUEST_CODE)
        }
    }

    private fun requestBattery() {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }
}

@Composable
private fun MainScreen(
    prefs: Prefs,
    onPickAction: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestBattery: () -> Unit,
    onHideRecents: (Boolean) -> Unit,
    onTheme: (String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener { tick++ }
        val dead = Shizuku.OnBinderDeadListener { tick++ }
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> tick++ }
        try {
            Shizuku.addBinderReceivedListenerSticky(received)
            Shizuku.addBinderDeadListener(dead)
            Shizuku.addRequestPermissionResultListener(permission)
        } catch (_: Throwable) {
            return@DisposableEffect onDispose { }
        }
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val enabled = remember(tick) { prefs.enabled() }
    val vibrate = remember(tick) { prefs.vibrate() }
    val shizuku = remember(tick) { ShizukuAccess.granted() }
    val shizukuAlive = remember(tick) { ShizukuAccess.binderAlive() }
    val battery = remember(tick) {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }
    val subtitle = remember(tick) { shortSubtitle(context, prefs) }
    val monitor by ActionKeyService.state.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                    label = { Text(context.getString(R.string.nav_action_key)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(context.getString(R.string.nav_settings)) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tab == 0) {
                ActionKeyPage(
                    enabled = enabled,
                    vibrate = vibrate,
                    shizuku = shizuku,
                    shizukuAlive = shizukuAlive,
                    battery = battery,
                    subtitle = subtitle,
                    monitor = monitorText(context, monitor, enabled),
                    onEnabled = { on ->
                        ActionKeyService.setEnabled(context, on)
                        tick++
                    },
                    onVibrate = { on ->
                        prefs.setVibrate(on)
                        tick++
                    },
                    onPickAction = onPickAction,
                    onRequestShizuku = onRequestShizuku,
                    onRequestBattery = onRequestBattery,
                )
            } else {
                SettingsPage(
                    theme = prefs.themeMode(),
                    hide = prefs.hideFromRecents(),
                    shizuku = shizuku,
                    shizukuAlive = shizukuAlive,
                    battery = battery,
                    onTheme = onTheme,
                    onHide = { value ->
                        onHideRecents(value)
                        tick++
                    },
                    onRequestShizuku = onRequestShizuku,
                    onRequestBattery = onRequestBattery,
                )
            }
        }
    }
}

@Composable
private fun ActionKeyPage(
    enabled: Boolean,
    vibrate: Boolean,
    shizuku: Boolean,
    shizukuAlive: Boolean,
    battery: Boolean,
    subtitle: String,
    monitor: String,
    onEnabled: (Boolean) -> Unit,
    onVibrate: (Boolean) -> Unit,
    onPickAction: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestBattery: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Text(context.getString(R.string.nav_action_key), style = MaterialTheme.typography.headlineSmall)
    SwitchRow(
        title = context.getString(R.string.ak_enable_title),
        subtitle = context.getString(R.string.ak_enable_desc),
        checked = enabled,
        onChecked = onEnabled,
    )
    Text(monitor, style = MaterialTheme.typography.bodySmall)
    PermissionCard(
        shizuku = shizuku,
        shizukuAlive = shizukuAlive,
        battery = battery,
        onRequestShizuku = onRequestShizuku,
        onRequestBattery = onRequestBattery,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(context.getString(R.string.ak_hw_single), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickAction)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(context.getString(R.string.ak_ai_short))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    SwitchRow(
        title = context.getString(R.string.ak_vibrate_title),
        subtitle = context.getString(R.string.ak_vibrate_desc),
        checked = vibrate,
        onChecked = onVibrate,
    )
}

@Composable
private fun SettingsPage(
    theme: String,
    hide: Boolean,
    shizuku: Boolean,
    shizukuAlive: Boolean,
    battery: Boolean,
    onTheme: (String) -> Unit,
    onHide: (Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestBattery: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Text(context.getString(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
    Text(context.getString(R.string.settings_subtitle), style = MaterialTheme.typography.bodySmall)
    Text(context.getString(R.string.theme_title), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeChip(context.getString(R.string.theme_system), theme == Prefs.THEME_SYSTEM) {
            onTheme(Prefs.THEME_SYSTEM)
        }
        ThemeChip(context.getString(R.string.theme_light), theme == Prefs.THEME_LIGHT) {
            onTheme(Prefs.THEME_LIGHT)
        }
        ThemeChip(context.getString(R.string.theme_dark), theme == Prefs.THEME_DARK) {
            onTheme(Prefs.THEME_DARK)
        }
    }
    SwitchRow(
        title = context.getString(R.string.settings_hide_recents_title),
        subtitle = context.getString(R.string.settings_hide_recents_desc),
        checked = hide,
        onChecked = onHide,
    )
    PermissionCard(
        shizuku = shizuku,
        shizukuAlive = shizukuAlive,
        battery = battery,
        onRequestShizuku = onRequestShizuku,
        onRequestBattery = onRequestBattery,
    )
}

@Composable
private fun PermissionCard(
    shizuku: Boolean,
    shizukuAlive: Boolean,
    battery: Boolean,
    onRequestShizuku: () -> Unit,
    onRequestBattery: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                context.getString(R.string.ak_privilege_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                context.getString(R.string.ak_privilege_desc),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(shizukuStatus(context, shizuku, shizukuAlive))
            if (!shizuku) {
                TextButton(onClick = onRequestShizuku) {
                    Text(context.getString(R.string.settings_shizuku_auth))
                }
            }
            Text(
                if (battery) {
                    context.getString(R.string.permission_summary_ok)
                } else {
                    context.getString(R.string.permission_battery)
                },
            )
            if (!battery) {
                TextButton(onClick = onRequestBattery) {
                    Text(context.getString(R.string.permission_battery))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(if (selected) "● $label" else label)
    }
}

private fun shortSubtitle(context: android.content.Context, prefs: Prefs): String {
    val action = ActionId.fromId(prefs.shortActionId())
    if (action == ActionId.CUSTOM_APP) {
        val label = prefs.customLabel().ifBlank { prefs.customPackage() }
        if (label.isBlank()) return context.getString(R.string.ak_custom_app_none)
        return context.getString(R.string.ak_open_named, label)
    }
    return context.getString(action.title)
}

private fun shizukuStatus(context: android.content.Context, granted: Boolean, alive: Boolean): String {
    return when {
        granted -> context.getString(R.string.settings_shizuku_ok)
        alive -> context.getString(R.string.settings_shizuku_need_auth)
        else -> context.getString(R.string.settings_shizuku_not_running)
    }
}

private fun monitorText(
    context: android.content.Context,
    state: ActionKeyService.MonitorState,
    enabled: Boolean,
): String {
    if (!enabled) return context.getString(R.string.ak_monitor_stopped)
    val res = when (state) {
        ActionKeyService.MonitorState.LISTENING -> R.string.ak_monitor_listening
        ActionKeyService.MonitorState.RETRYING -> R.string.ak_monitor_retrying
        ActionKeyService.MonitorState.WAITING -> R.string.ak_monitor_waiting_privilege
        ActionKeyService.MonitorState.STARTING -> R.string.ak_monitor_starting
        ActionKeyService.MonitorState.STOPPED -> R.string.ak_monitor_starting
    }
    return context.getString(res)
}
