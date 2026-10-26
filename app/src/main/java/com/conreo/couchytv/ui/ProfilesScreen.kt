package com.conreo.couchytv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.conreo.couchytv.R
import com.conreo.couchytv.data.ConfigStore
import com.conreo.couchytv.data.LocalProfile
import com.conreo.couchytv.data.LocalProfiles
import kotlinx.coroutines.launch

@Composable
internal fun ProfilesScreen(store: ConfigStore, onSwitched: () -> Unit) {
    val state by store.state.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LocalProfile?>(null) }
    var deleting by remember { mutableStateOf<LocalProfile?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val directory = state?.profiles

    fun act(action: suspend () -> Unit, done: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try { action(); failed = false; done() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.profiles_notice), style = MaterialTheme.typography.bodySmall)
        if (failed || (state != null && directory == null)) Text(stringResource(R.string.profile_action_failed), color = MaterialTheme.colorScheme.error)
        if (directory != null) {
            val first = initialFocus()
            directory.entries.forEachIndexed { index, p ->
                SettingsItem(
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    enabled = !busy,
                    selected = p.id == directory.activeId,
                    onClick = { if (p.id != directory.activeId) act({ store.switchProfile(p.id) }, onSwitched) },
                    headlineContent = { Text(p.name) },
                    trailingContent = { CheckMark(p.id == directory.activeId) },
                )
            }
            SettingsItem(enabled = !busy && directory.entries.size < LocalProfiles.MAX_PROFILES,
                onClick = { failed = false; creating = true }, headlineContent = { Text(stringResource(R.string.profile_add)) })
            val active = directory.entries.first { it.id == directory.activeId }
            SettingsItem(enabled = !busy, onClick = { failed = false; renaming = active },
                headlineContent = { Text(stringResource(R.string.profile_rename)) })
            directory.entries.filter { it.id != "default" && it.id != directory.activeId }.forEach { p ->
                SettingsItem(enabled = !busy, onClick = { deleting = p },
                    headlineContent = { Text(stringResource(R.string.profile_delete_named, p.name)) })
            }
        }
    }

    if (creating || renaming != null) ProfileNameDialog(
        initial = renaming?.name.orEmpty(),
        creating = creating, busy = busy, failed = failed,
        names = directory?.entries.orEmpty().filter { it.id != renaming?.id }.map { it.name },
        onDismiss = { creating = false; renaming = null },
        onSave = { name, easy ->
            val id = renaming?.id
            act({ if (id == null) store.createProfile(name, easy) else store.renameProfile(id, name) }) {
                creating = false; renaming = null
            }
        },
    )
    deleting?.let { profile ->
        ConfirmSettingsDialog(
            title = stringResource(R.string.profile_delete_named, profile.name),
            body = stringResource(R.string.profile_delete_notice),
            confirm = stringResource(R.string.profile_delete),
            onCancel = { deleting = null },
            onConfirm = { act({ store.deleteProfile(profile.id) }) { deleting = null } },
        )
    }
}

@Composable
private fun ProfileNameDialog(initial: String, creating: Boolean, busy: Boolean, failed: Boolean, names: List<String>, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var easy by remember { mutableStateOf(false) }
    val clean = name.trim()
    val valid = LocalProfiles.validName(clean) && names.none { it.equals(clean, ignoreCase = true) }
    Dialog(onDismissRequest = onDismiss) {
        val focusManager = LocalFocusManager.current
        Surface(shape = RoundedCornerShape(16.dp), colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (creating) R.string.profile_add else R.string.profile_rename), style = MaterialTheme.typography.titleLarge)
                val nameLabel = stringResource(R.string.profile_name)
                Text(nameLabel)
                val first = initialFocus()
                BasicTextField(
                    value = name, onValueChange = { if (it.length <= 32 && it.none(Char::isISOControl)) name = it },
                    singleLine = true, enabled = !busy,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp).focusRequester(first).semantics { contentDescription = nameLabel }
                        .onPreviewKeyEvent { event ->
                            // Once the TV keyboard is dismissed, Down must leave the editor
                            // instead of trapping the remote in single-line cursor navigation.
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            } else false
                        },
                )
                if (!valid && clean.isNotEmpty()) Text(stringResource(R.string.profile_name_invalid), color = MaterialTheme.colorScheme.error)
                if (failed) Text(stringResource(R.string.profile_action_failed), color = MaterialTheme.colorScheme.error)
                if (creating) {
                    Text(stringResource(R.string.profile_copy_notice), style = MaterialTheme.typography.bodySmall)
                    SettingsItem(selected = easy, enabled = !busy, onClick = { easy = !easy },
                        headlineContent = { Text(stringResource(R.string.profile_easy)) }, trailingContent = { CheckMark(easy) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = onDismiss, enabled = !busy) { Text(stringResource(android.R.string.cancel)) }
                    Button(onClick = { onSave(clean, easy) }, enabled = valid && !busy) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}
