package com.conreo.couchytv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.conreo.couchytv.R
import com.conreo.couchytv.data.TelegramAuthUi
import com.conreo.couchytv.data.TelegramCloudBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Telegram device-link on the TV: show a QR the phone app scans
 * (Settings → Devices → Link Desktop Device). If the account has two-step
 * verification, a password dialog follows.
 */
@Composable
fun TelegramAuthDialog(
    backup: TelegramCloudBackup,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ui by backup.authUi.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    val waiting = stringResource(R.string.cloud_auth_waiting)
    val genericFail = stringResource(R.string.cloud_auth_fail)
    val unsupported = stringResource(R.string.cloud_auth_unsupported)
    val wrongPw = stringResource(R.string.cloud_auth_2fa_wrong)

    LaunchedEffect(Unit) {
        backup.beginLogin()
        runCatching { backup.awaitLoggedIn() }
            .onSuccess { onSuccess() }
    }

    val qrLink = (ui as? TelegramAuthUi.Qr)?.link
    val qr by produceState<ImageBitmap?>(initialValue = null, qrLink) {
        val link = qrLink ?: return@produceState
        value = withContext(Dispatchers.Default) { encodeQrBitmap(link) }
    }

    val status = when (val s = ui) {
        is TelegramAuthUi.Working -> waiting
        is TelegramAuthUi.Qr -> waiting
        is TelegramAuthUi.Password -> s.error?.let { wrongPw } ?: ""
        is TelegramAuthUi.Failed ->
            if (s.message.startsWith("authorizationStateWait")) unsupported else s.message.ifBlank { genericFail }
        else -> ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                Modifier
                    .width(520.dp)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(
                        if (ui is TelegramAuthUi.Password) R.string.cloud_auth_password_title
                        else R.string.cloud_auth_title,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    stringResource(
                        if (ui is TelegramAuthUi.Password) R.string.cloud_auth_password_body
                        else R.string.cloud_auth_body,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
                val pw = ui as? TelegramAuthUi.Password
                if (pw != null) {
                    if (pw.hint.isNotBlank()) {
                        Text(
                            stringResource(R.string.cloud_auth_password_hint, pw.hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                    val fieldFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        BasicTextField(
                            value = password,
                            onValueChange = { password = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (password.isNotBlank()) {
                                        scope.launch { backup.submitPassword(password) }
                                    }
                                },
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (password.isNotBlank()) {
                                    scope.launch { backup.submitPassword(password) }
                                }
                            },
                        ) { Text(stringResource(R.string.cloud_auth_password_submit)) }
                        Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    }
                } else {
                    val bitmap = qr
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(R.string.cloud_auth_qr_cd),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(240.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                        )
                    }
                    val cancelFocus = remember { FocusRequester() }
                    LaunchedEffect(qr) {
                        runCatching { cancelFocus.requestFocus() }
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .focusRequester(cancelFocus),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
