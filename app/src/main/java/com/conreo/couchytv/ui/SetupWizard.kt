package com.conreo.couchytv.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.conreo.couchytv.Actions
import com.conreo.couchytv.R
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * First-launch wizard. Detects the device type and guides the user to set
 * Couchy as the default home — including certified Google TV devices, where
 * the system UI does not allow changing the home app and ADB is required.
 * [onVpnChosen] receives the picked VPN package, or null to hide the VPN icon.
 */
@Composable
fun SetupWizard(onDone: () -> Unit, onVpnChosen: (String?) -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var gtvPage by remember { mutableIntStateOf(0) } // carousel page on the Google-TV step
    var defaultHome by remember { mutableStateOf(defaultHomePackage(context)) }
    val isDefault = defaultHome == context.packageName
    val isGoogleTv = remember {
        // "Amati" feature = certified Google TV experience (home app locked by Google)
        context.packageManager.hasSystemFeature("com.google.android.feature.AMATI_EXPERIENCE")
    }
    val ip = remember { deviceIp() }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { defaultHome = defaultHomePackage(context) }

    fun requestDefault() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !rm.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                val ok = runCatching {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }.isSuccess
                if (ok) return
            }
        }
        runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure {
                Actions.toast(context, context.getString(R.string.wizard_no_chooser))
            }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(WALLPAPERS[0].brush()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 36.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Branding: the couch, on every wizard step.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_couch),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
            }
            when (step) {
                0 -> {
                    Title(stringResource(R.string.wizard_welcome_title))
                    Body(stringResource(R.string.wizard_welcome_body))
                    Body(stringResource(R.string.wizard_privacy_body))
                    NavRow(nextLabel = stringResource(R.string.next)) { step = 1 }
                }
                1 -> {
                    Title(stringResource(R.string.wizard_home_title))
                    if (isDefault) {
                        Body(stringResource(R.string.wizard_already_default))
                        NavRow(
                            backAction = { step = 0 },
                            nextLabel = stringResource(R.string.next),
                        ) { step = 2 }
                    } else if (isGoogleTv) {
                        // Carousel: 3 short pages so it fits & stays centered at
                        // any DPI. Prev/Next moves between pages; on the ends it
                        // steps out of the wizard.
                        when (gtvPage) {
                            0 -> {
                                Body(stringResource(R.string.wizard_gtv_intro))
                                Body(stringResource(R.string.wizard_gtv_adb))
                                CodeLine("adb connect ${ip ?: "<TV-IP>"}:5555")
                                CodeLine("adb shell cmd package set-home-activity com.conreo.couchytv/.MainActivity")
                            }
                            1 -> {
                                Body(stringResource(R.string.wizard_gtv_overlay))
                                CodeLine("adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME")
                                CodeLine("adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx")
                                CodeLine("adb shell pm disable-user --user 0 com.google.android.tvlauncher")
                                CodeLine("adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith")
                            }
                            else -> {
                                Body(stringResource(R.string.wizard_gtv_which))
                                Body(stringResource(R.string.wizard_gtv_remap))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = { requestDefault() }) {
                                        Text(stringResource(R.string.wizard_try_dialog))
                                    }
                                    Button(onClick = { defaultHome = defaultHomePackage(context) }) {
                                        Text(stringResource(R.string.wizard_check_again))
                                    }
                                }
                            }
                        }
                        PagerRow(
                            page = gtvPage,
                            count = 3,
                            onPrev = { if (gtvPage > 0) gtvPage-- else step = 0 },
                            onNext = { if (gtvPage < 2) gtvPage++ else step = 2 },
                        )
                    } else {
                        Body(stringResource(R.string.wizard_aosp_body))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { requestDefault() }) {
                                Text(stringResource(R.string.wizard_set_default))
                            }
                            Button(onClick = { defaultHome = defaultHomePackage(context) }) {
                                Text(stringResource(R.string.wizard_check_again))
                            }
                        }
                        Body(stringResource(R.string.wizard_no_dialog_hint))
                        NavRow(
                            backAction = { step = 0 },
                            nextLabel = stringResource(R.string.wizard_skip),
                        ) { step = 2 }
                    }
                }
                2 -> {
                    Title(stringResource(R.string.wizard_vpn_title))
                    Body(stringResource(R.string.wizard_vpn_body))
                    val vpns = remember { vpnApps(context) }
                    if (vpns.isEmpty()) {
                        Body(stringResource(R.string.wizard_vpn_none))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            vpns.forEach { (pkg, label) ->
                                Button(onClick = { onVpnChosen(pkg); step = 3 }) { Text(label) }
                            }
                        }
                    }
                    NavRow(
                        backAction = { step = 1 },
                        nextLabel = stringResource(R.string.wizard_vpn_skip),
                    ) { onVpnChosen(null); step = 3 }
                }
                else -> {
                    Title(stringResource(R.string.wizard_done_title))
                    Body(
                        stringResource(R.string.wizard_tips) +
                            if (!isDefault) "\n\n" + stringResource(R.string.wizard_rerun_note) else ""
                    )
                    NavRow(
                        backAction = { step = 2 },
                        nextLabel = stringResource(R.string.wizard_start),
                    ) { onDone() }
                }
            }
        }
    }
}

/* ------------------------------ pieces ------------------------------ */

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Color.White)
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f),
    )
}

@Composable
private fun CodeLine(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF8AB4F8),
        )
    }
}

@Composable
private fun NavRow(
    backAction: (() -> Unit)? = null,
    nextLabel: String,
    nextAction: () -> Unit,
) {
    // Focus the primary (next) button on entry so every step opens with a
    // button selected and the D-pad ready.
    val nextFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { nextFocus.requestFocus() }
    }
    Row(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (backAction != null) {
            Button(onClick = backAction) { Text(stringResource(R.string.back)) }
        }
        Button(onClick = nextAction, modifier = Modifier.focusRequester(nextFocus)) {
            Text(nextLabel)
        }
    }
}

/** Carousel controls: Prev / "n / N" / Next. On the ends they step out. */
@Composable
private fun PagerRow(page: Int, count: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val nextFocus = remember { FocusRequester() }
    LaunchedEffect(page) {
        delay(80)
        runCatching { nextFocus.requestFocus() }
    }
    Row(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onPrev) {
            Text(if (page > 0) "◄" else stringResource(R.string.back))
        }
        Text(
            "${page + 1} / $count",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Button(onClick = onNext, modifier = Modifier.focusRequester(nextFocus)) {
            Text(if (page < count - 1) "►" else stringResource(R.string.wizard_skip))
        }
    }
}

/* ------------------------------ helpers ------------------------------ */

fun defaultHomePackage(context: Context): String? {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager
        .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
}

private fun deviceIp(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
        ?.hostAddress
}.getOrNull()

/** Installed VPN apps = anything implementing android.net.VpnService. */
private fun vpnApps(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    return pm.queryIntentServices(Intent("android.net.VpnService"), 0)
        .mapNotNull { ri ->
            val si = ri.serviceInfo ?: return@mapNotNull null
            if (si.packageName == context.packageName) return@mapNotNull null
            val label = runCatching { ri.loadLabel(pm).toString() }.getOrNull() ?: si.packageName
            si.packageName to label
        }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
}
