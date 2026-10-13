package com.mxcfe.sidekey.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mxcfe.sidekey.R
import com.mxcfe.sidekey.action.AppShortcut
import com.mxcfe.sidekey.action.AppShortcutCatalog
import com.mxcfe.sidekey.action.KeyGesture
import com.mxcfe.sidekey.prefs.Prefs
import com.mxcfe.sidekey.privilege.ShizukuAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShortcutPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyWindowBackground(this)
        val gesture = KeyGesture.fromId(intent.getStringExtra(EXTRA_GESTURE))
        val packageName = intent.getStringExtra(EXTRA_PKG).orEmpty()
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty().ifBlank { packageName }
        if (packageName.isBlank()) {
            finish()
            return
        }
        val prefs = Prefs(this)
        setContent {
            SidekeyTheme(prefs.themeMode()) {
                var shortcuts by remember { mutableStateOf<List<AppShortcut>?>(null) }
                LaunchedEffect(packageName) {
                    shortcuts = withContext(Dispatchers.IO) {
                        AppShortcutCatalog.list(this@ShortcutPickerActivity, packageName)
                    }
                }
                ShortcutList(
                    shortcuts = shortcuts,
                    shizukuGranted = ShizukuAccess.granted(),
                    onBack = { finish() },
                    onPick = { shortcut ->
                        prefs.setAppShortcut(
                            gesture = gesture,
                            packageName = packageName,
                            appLabel = appLabel,
                            shortcutId = shortcut.id,
                            shortcutLabel = shortcut.label,
                        )
                        setResult(RESULT_OK)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_GESTURE = "gesture"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_APP_LABEL = "app_label"
    }
}

@Composable
private fun ShortcutList(
    shortcuts: List<AppShortcut>?,
    shizukuGranted: Boolean,
    onBack: () -> Unit,
    onPick: (AppShortcut) -> Unit,
) {
    PickerScaffold(
        title = stringResource(R.string.ak_pick_shortcut),
        onBack = onBack,
    ) { padding ->
        when {
            shortcuts == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            shortcuts.isEmpty() -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(
                                if (shizukuGranted) {
                                    R.string.ak_shortcut_empty
                                } else {
                                    R.string.ak_shortcut_need_shizuku
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 48.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(shortcuts, key = { _, item -> item.id }) { index, shortcut ->
                        if (index > 0) RowDivider(start = 20.dp)
                        PreferenceRow(
                            title = shortcut.label,
                            subtitle = shortcut.id,
                            onClick = { onPick(shortcut) },
                            horizontalPadding = 20.dp,
                        )
                    }
                }
            }
        }
    }
}
