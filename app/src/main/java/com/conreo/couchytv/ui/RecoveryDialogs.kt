package com.conreo.couchytv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.conreo.couchytv.R
import com.conreo.couchytv.data.LauncherConfig

@Composable
internal fun RestorePreviewDialog(backup: LauncherConfig, onCancel: () -> Unit, onRestore: (Boolean) -> Unit) {
    var keepWallpaper by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.restore_preview), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.restore_summary, backup.categories.size, backup.hidden.size))
                Text(backup.categories.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.restore_notice), style = MaterialTheme.typography.bodyMedium)
                SettingsItem(
                    selected = keepWallpaper,
                    onClick = { keepWallpaper = !keepWallpaper },
                    headlineContent = { Text(stringResource(R.string.restore_keep_wallpaper)) },
                    trailingContent = { CheckMark(keepWallpaper) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val first = initialFocus()
                    Button(onClick = onCancel, modifier = Modifier.focusRequester(first)) { Text(stringResource(android.R.string.cancel)) }
                    Button(onClick = { onRestore(keepWallpaper) }) { Text(stringResource(R.string.restore_apply)) }
                }
            }
        }
    }
}

@Composable
internal fun ConfirmSettingsDialog(title: String, body: String, confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.widthIn(max = 520.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val first = initialFocus()
                    Button(onClick = onCancel, modifier = Modifier.focusRequester(first)) { Text(stringResource(android.R.string.cancel)) }
                    Button(onClick = onConfirm) { Text(confirm) }
                }
            }
        }
    }
}
