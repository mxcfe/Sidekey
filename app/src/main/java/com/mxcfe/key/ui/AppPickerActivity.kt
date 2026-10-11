package com.mxcfe.key.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mxcfe.key.R
import com.mxcfe.key.prefs.Prefs

class AppPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val apps = loadApps()
        setContent {
            SideKeyTheme(prefs.themeMode()) {
                AppList(apps) { app ->
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
private fun AppList(apps: List<AppPickerActivity.InstalledApp>, onPick: (AppPickerActivity.InstalledApp) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    val shown = remember(query, apps) {
        val key = query.trim()
        if (key.isEmpty()) apps else apps.filter {
            it.label.contains(key, ignoreCase = true) || it.packageName.contains(key, ignoreCase = true)
        }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text(context.getString(R.string.ak_custom_app_search)) },
                singleLine = true,
            )
        }
        items(shown, key = { it.packageName }) { app ->
            ListItem(
                headlineContent = { Text(app.label) },
                supportingContent = { Text(app.packageName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(app) },
            )
        }
    }
}
