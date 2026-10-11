package com.mxcfe.key.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mxcfe.key.R
import com.mxcfe.key.action.ActionId
import com.mxcfe.key.prefs.Prefs

class ActionPickerActivity : ComponentActivity() {
    private val pickApp = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val pkg = result.data?.getStringExtra(AppPickerActivity.EXTRA_PKG) ?: return@registerForActivityResult
        val label = result.data?.getStringExtra(AppPickerActivity.EXTRA_LABEL) ?: pkg
        Prefs(this).setCustomApp(pkg, label)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            SideKeyTheme(prefs.themeMode()) {
                ActionList(
                    selected = prefs.shortActionId(),
                    onPick = { action ->
                        if (action == ActionId.CUSTOM_APP) {
                            pickApp.launch(Intent(this, AppPickerActivity::class.java))
                        } else {
                            prefs.setShortAction(action.id)
                            finish()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionList(selected: String, onPick: (ActionId) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val groups = listOf(
        context.getString(R.string.ak_cat_app) to ActionId.entries.filter { it.inAppGroup },
        context.getString(R.string.ak_cat_quick) to ActionId.entries.filter { !it.inAppGroup },
    )
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                context.getString(R.string.ak_pick_action),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        groups.forEach { (title, actions) ->
            item {
                Text(
                    title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(actions) { action ->
                ListItem(
                    headlineContent = { Text(context.getString(action.title)) },
                    supportingContent = if (action.id == selected) {
                        { Text(context.getString(R.string.permission_summary_ok)) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(action) },
                )
                HorizontalDivider()
            }
        }
    }
}
