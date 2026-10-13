package com.mxcfe.sidekey.ui

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mxcfe.sidekey.R
import com.mxcfe.sidekey.action.ActionId
import com.mxcfe.sidekey.action.KeyGesture
import com.mxcfe.sidekey.prefs.Prefs
import com.mxcfe.sidekey.privilege.ShizukuAccess
import com.mxcfe.sidekey.service.ActionKeyService
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyWindowBackground(this)
        setContent {
            val prefs = remember { Prefs(this) }
            SidekeyTheme(prefs.themeMode()) {
                MainScreen(
                    prefs = prefs,
                    onPickAction = { gesture ->
                        startActivity(
                            Intent(this, ActionPickerActivity::class.java)
                                .putExtra(ActionPickerActivity.EXTRA_GESTURE, gesture.id),
                        )
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
    onPickAction: (KeyGesture) -> Unit,
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
    val longSubtitle = remember(tick) { actionSubtitle(context, prefs, KeyGesture.LONG) }
    val doubleSubtitle = remember(tick) { actionSubtitle(context, prefs, KeyGesture.DOUBLE) }
    val monitor by ActionKeyService.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 0.dp,
                ) {
                    NavItem(
                        selected = tab == 0,
                        label = stringResource(R.string.nav_action_key),
                        icon = if (tab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                        onClick = { tab = 0 },
                    )
                    NavItem(
                        selected = tab == 1,
                        label = stringResource(R.string.nav_settings),
                        icon = if (tab == 1) Icons.Filled.Settings else Icons.Outlined.Settings,
                        onClick = { tab = 1 },
                    )
                }
            }
        },
    ) { padding ->
        if (tab == 0) {
            ActionKeyPage(
                modifier = Modifier.padding(padding),
                enabled = enabled,
                vibrate = vibrate,
                shizuku = shizuku,
                shizukuAlive = shizukuAlive,
                battery = battery,
                longSubtitle = longSubtitle,
                doubleSubtitle = doubleSubtitle,
                monitor = monitorText(context, monitor, enabled),
                monitorState = monitor,
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
                modifier = Modifier.padding(padding),
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

@Composable
private fun RowScope.NavItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun ActionKeyPage(
    modifier: Modifier,
    enabled: Boolean,
    vibrate: Boolean,
    shizuku: Boolean,
    shizukuAlive: Boolean,
    battery: Boolean,
    longSubtitle: String,
    doubleSubtitle: String,
    monitor: String,
    monitorState: ActionKeyService.MonitorState,
    onEnabled: (Boolean) -> Unit,
    onVibrate: (Boolean) -> Unit,
    onPickAction: (KeyGesture) -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestBattery: () -> Unit,
) {
    PageColumn(modifier) {
        PageHeader(title = stringResource(R.string.nav_action_key)) {
            StatusLine(text = monitor, color = monitorColor(monitorState, enabled))
        }
        Section(title = stringResource(R.string.ak_section_key)) {
            PreferenceRow(
                title = stringResource(R.string.ak_enable_title),
                subtitle = stringResource(R.string.ak_enable_desc),
                icon = Icons.Outlined.Star,
                checked = enabled,
                onCheckedChange = onEnabled,
            )
            RowDivider(start = IconDividerInset)
            PreferenceRow(
                title = stringResource(R.string.ak_vibrate_title),
                subtitle = stringResource(R.string.ak_vibrate_desc),
                icon = Icons.Outlined.Notifications,
                checked = vibrate,
                onCheckedChange = onVibrate,
            )
        }
        Section(title = stringResource(R.string.ak_hw_single)) {
            PreferenceRow(
                title = stringResource(R.string.ak_ai_long),
                subtitle = longSubtitle,
                icon = Icons.Outlined.Info,
                onClick = { onPickAction(KeyGesture.LONG) },
                showChevron = true,
            )
            RowDivider(start = IconDividerInset)
            PreferenceRow(
                title = stringResource(R.string.ak_ai_double),
                subtitle = doubleSubtitle,
                icon = Icons.Filled.Check,
                onClick = { onPickAction(KeyGesture.DOUBLE) },
                showChevron = true,
            )
        }
        PermissionSection(
            shizuku = shizuku,
            shizukuAlive = shizukuAlive,
            battery = battery,
            onRequestShizuku = onRequestShizuku,
            onRequestBattery = onRequestBattery,
        )
    }
}

@Composable
private fun SettingsPage(
    modifier: Modifier,
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
    PageColumn(modifier) {
        PageHeader(title = stringResource(R.string.settings_title)) {
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Section(title = stringResource(R.string.theme_title)) {
            SegmentedChoice(
                options = listOf(
                    SegmentOption(Prefs.THEME_SYSTEM, stringResource(R.string.theme_system)),
                    SegmentOption(Prefs.THEME_LIGHT, stringResource(R.string.theme_light)),
                    SegmentOption(Prefs.THEME_DARK, stringResource(R.string.theme_dark)),
                ),
                selected = theme,
                onSelect = onTheme,
                modifier = Modifier.padding(12.dp),
            )
        }
        Section(title = stringResource(R.string.settings_section_background)) {
            PreferenceRow(
                title = stringResource(R.string.settings_hide_recents_title),
                subtitle = stringResource(R.string.settings_hide_recents_desc),
                icon = Icons.Outlined.Lock,
                checked = hide,
                onCheckedChange = onHide,
            )
        }
        PermissionSection(
            shizuku = shizuku,
            shizukuAlive = shizukuAlive,
            battery = battery,
            onRequestShizuku = onRequestShizuku,
            onRequestBattery = onRequestBattery,
        )
    }
}

@Composable
private fun PermissionSection(
    shizuku: Boolean,
    shizukuAlive: Boolean,
    battery: Boolean,
    onRequestShizuku: () -> Unit,
    onRequestBattery: () -> Unit,
) {
    val readyIcon = MaterialTheme.colorScheme.primaryContainer
    val readyTint = MaterialTheme.colorScheme.onPrimaryContainer
    val pendingIcon = MaterialTheme.colorScheme.errorContainer
    val pendingTint = MaterialTheme.colorScheme.onErrorContainer
    Section(
        title = stringResource(R.string.ak_privilege_title),
        description = stringResource(R.string.ak_privilege_desc),
    ) {
        PreferenceRow(
            title = stringResource(R.string.permission_shizuku),
            subtitle = shizukuSubtitle(shizuku, shizukuAlive),
            icon = Icons.Outlined.Settings,
            iconBackground = if (shizuku) readyIcon else pendingIcon,
            iconTint = if (shizuku) readyTint else pendingTint,
            onClick = if (shizuku) null else onRequestShizuku,
            showChevron = !shizuku,
            trailing = if (shizuku) {
                { CheckMark() }
            } else {
                null
            },
        )
        RowDivider(start = IconDividerInset)
        PreferenceRow(
            title = stringResource(R.string.permission_battery),
            subtitle = if (battery) stringResource(R.string.permission_summary_ok) else null,
            icon = if (battery) Icons.Filled.Star else Icons.Outlined.Warning,
            iconBackground = if (battery) readyIcon else pendingIcon,
            iconTint = if (battery) readyTint else pendingTint,
            onClick = if (battery) null else onRequestBattery,
            showChevron = !battery,
            trailing = if (battery) {
                { CheckMark() }
            } else {
                null
            },
        )
    }
}

@Composable
private fun shizukuSubtitle(granted: Boolean, alive: Boolean): String {
    return when {
        granted -> stringResource(R.string.settings_shizuku_ok)
        alive -> stringResource(R.string.settings_shizuku_need_auth)
        else -> stringResource(R.string.settings_shizuku_not_running)
    }
}

@Composable
private fun monitorColor(state: ActionKeyService.MonitorState, enabled: Boolean): Color {
    if (!enabled) return MaterialTheme.colorScheme.outline
    return when (state) {
        ActionKeyService.MonitorState.LISTENING -> MaterialTheme.colorScheme.secondary
        ActionKeyService.MonitorState.RETRYING -> MaterialTheme.colorScheme.error
        ActionKeyService.MonitorState.WAITING -> MaterialTheme.colorScheme.tertiary
        ActionKeyService.MonitorState.STARTING,
        ActionKeyService.MonitorState.STOPPED,
        -> MaterialTheme.colorScheme.primary
    }
}

private fun actionSubtitle(
    context: android.content.Context,
    prefs: Prefs,
    gesture: KeyGesture,
): String {
    val action = ActionId.fromId(prefs.actionId(gesture))
    if (action == ActionId.CUSTOM_APP) {
        val label = prefs.customLabel(gesture).ifBlank { prefs.customPackage(gesture) }
        if (label.isBlank()) return context.getString(R.string.ak_custom_app_none)
        return context.getString(R.string.ak_open_named, label)
    }
    if (action == ActionId.OPEN_URL) {
        val url = prefs.pageUrl(gesture)
        if (url.isBlank()) return context.getString(R.string.ak_url_none)
        return context.getString(R.string.ak_open_url_named, url)
    }
    return context.getString(action.title)
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
