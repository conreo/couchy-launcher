package com.conreo.couchytv

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.atomic.AtomicBoolean

object Actions {

    fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun launchApp(context: Context, pkg: String) {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            runCatching { context.startActivity(intent) }
                .onFailure { toast(context, context.getString(R.string.toast_cannot_open)) }
        } else toast(context, context.getString(R.string.toast_no_launchable))
    }

    fun openAppInfo(context: Context, pkg: String) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$pkg")
                )
            )
        }
    }

    fun uninstall(context: Context, pkg: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        }
    }

    /**
     * Same two App-info actions: Force stop, then Clear cache.
     * Both are hidden + privileged (`FORCE_STOP_PACKAGES`, `CLEAR_APP_CACHE`);
     * they succeed when the OS grants those (priv-app / some AOSP boxes) and
     * otherwise fall back to [ActivityManager.killBackgroundProcesses].
     */
    fun close(context: Context, pkg: String) {
        if (pkg == context.packageName) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        runCatching { forceStopPackage(am, pkg) }
            .onFailure { Log.w(TAG, "forceStopPackage $pkg", it) }
        runCatching { clearAppCache(context.packageManager, pkg) }
            .onFailure { Log.w(TAG, "deleteApplicationCacheFiles $pkg", it) }
        runCatching { am.killBackgroundProcesses(pkg) }
    }

    fun openSystemSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    fun openNetworkSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            .onFailure { openSystemSettings(context) }
    }

    fun openVpnSettings(context: Context) {
        runCatching { context.startActivity(Intent("android.settings.VPN_SETTINGS")) }
            .onFailure { openSystemSettings(context) }
    }

    /** AerialViews screensaver (github.com/theothernt/AerialViews) */
    const val AERIAL_PKG = "com.neilturner.aerialviews"

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    @SuppressLint("PrivateApi")
    private fun forceStopPackage(am: ActivityManager, pkg: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            allowHiddenApis()
            HiddenApiBypass.invoke(ActivityManager::class.java, am, "forceStopPackage", pkg)
        } else {
            ActivityManager::class.java.getMethod("forceStopPackage", String::class.java)
                .invoke(am, pkg)
        }
    }

    @SuppressLint("PrivateApi")
    private fun clearAppCache(pm: PackageManager, pkg: String) {
        val observer = Class.forName("android.content.pm.IPackageDataObserver")
        val method = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            allowHiddenApis()
            HiddenApiBypass.getDeclaredMethod(
                PackageManager::class.java,
                "deleteApplicationCacheFiles",
                String::class.java,
                observer,
            )
        } else {
            PackageManager::class.java.getMethod(
                "deleteApplicationCacheFiles",
                String::class.java,
                observer,
            )
        }
        method.invoke(pm, pkg, null)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun allowHiddenApis() {
        if (!hiddenApisReady.compareAndSet(false, true)) return
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
            .onFailure { Log.w(TAG, "hidden API exemptions", it) }
    }

    private val hiddenApisReady = AtomicBoolean(false)
    private const val TAG = "LiteTV"

    fun openAppStore(context: Context, pkg: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        }.onFailure {
            toast(context, context.getString(R.string.toast_no_store))
        }
    }

}
