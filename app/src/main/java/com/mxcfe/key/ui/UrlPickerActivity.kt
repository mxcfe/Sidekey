package com.mxcfe.key.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mxcfe.key.R
import com.mxcfe.key.action.BrowserUrl
import com.mxcfe.key.prefs.Prefs

class UrlPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyWindowBackground(this)
        val prefs = Prefs(this)
        val initial = intent.getStringExtra(EXTRA_URL).orEmpty()
        setContent {
            SideKeyTheme(prefs.themeMode()) {
                UrlForm(
                    initial = initial,
                    onBack = { finish() },
                    onSave = { url ->
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_URL, url))
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}

@Composable
private fun UrlForm(
    initial: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    val invalid = stringResource(R.string.ak_url_invalid)
    val hint = stringResource(R.string.ak_url_hint)
    fun save() {
        val url = BrowserUrl.normalize(value)
        if (url == null) {
            error = true
            return
        }
        onSave(url)
    }
    PickerScaffold(
        title = stringResource(R.string.ak_pick_url),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.ak_url_label)) },
                placeholder = { Text("https://") },
                supportingText = { Text(if (error) invalid else hint) },
                isError = error,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.ak_url_save))
            }
        }
    }
}
