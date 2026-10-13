package com.mxcfe.sidekey.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mxcfe.sidekey.R
import com.mxcfe.sidekey.prefs.Prefs

class AppPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyWindowBackground(this)
        val prefs = Prefs(this)
        val apps = loadApps()
        setContent {
            SidekeyTheme(prefs.themeMode()) {
                AppList(apps, onBack = { finish() }) { app ->
                    setResult(
                        RESULT_OK,
                        Intent()
                            .putExtra(EXTRA_PKG, app.packageName)
                            .putExtra(EXTRA_LABEL, app.label),
                    )
                    finish()
                }
            }
        }
    }

    private fun loadApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map {
                InstalledApp(
                    packageName = it.activityInfo.packageName,
                    label = it.loadLabel(packageManager).toString(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    data class InstalledApp(val packageName: String, val label: String)

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_LABEL = "label"
    }
}

@Composable
private fun AppList(
    apps: List<AppPickerActivity.InstalledApp>,
    onBack: () -> Unit,
    onPick: (AppPickerActivity.InstalledApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, apps) {
        val key = query.trim()
        if (key.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.contains(key, ignoreCase = true) ||
                    it.packageName.contains(key, ignoreCase = true)
            }
        }
    }
    val focusManager = LocalFocusManager.current
    PickerScaffold(
        title = stringResource(R.string.ak_pick_app),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    label = { Text(stringResource(R.string.ak_custom_app_search)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (query.isEmpty()) {
                        null
                    } else {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.ak_search_clear),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                )
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.ak_search_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 48.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                if (index > 0) RowDivider(start = 20.dp)
                PreferenceRow(
                    title = app.label,
                    subtitle = app.packageName,
                    onClick = { onPick(app) },
                    horizontalPadding = 20.dp,
                )
            }
        }
    }
}
