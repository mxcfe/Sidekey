package com.mxcfe.key.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyWindowBackground(this)
        val prefs = Prefs(this)
        setContent {
            SideKeyTheme(prefs.themeMode()) {
                ActionList(
                    selected = prefs.shortActionId(),
                    onBack = { finish() },
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
private fun ActionList(
    selected: String,
    onBack: () -> Unit,
    onPick: (ActionId) -> Unit,
) {
    val groups = listOf(
        stringResource(R.string.ak_cat_app) to ActionId.entries.filter { it.inAppGroup },
        stringResource(R.string.ak_cat_quick) to ActionId.entries.filter { !it.inAppGroup },
    )
    PickerScaffold(
        title = stringResource(R.string.ak_pick_action),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp),
        ) {
            items(groups, key = { it.first }) { (title, actions) ->
                Section(title = title) {
                    actions.forEachIndexed { index, action ->
                        if (index > 0) RowDivider()
                        PreferenceRow(
                            title = stringResource(action.title),
                            onClick = { onPick(action) },
                            trailing = if (action.id == selected) {
                                { CheckMark() }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}
