package com.conreo.couchytv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.conreo.couchytv.data.ConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Headless entry point so ADB can save or restore the same local JSON backup
 * as Settings → Save/Load locally. Not shown in any launcher.
 *
 * ```
 * adb shell am start -n com.conreo.couchytv/.BackupActivity \
 *   -a com.conreo.couchytv.action.BACKUP_SAVE
 * adb shell am start -n com.conreo.couchytv/.BackupActivity \
 *   -a com.conreo.couchytv.action.BACKUP_RESTORE
 * ```
 *
 * Optional `--es path /sdcard/Download/my.json` overrides the default
 * Downloads/CouchyBackup.json. `--es op save` / `--es op restore` work
 * if you start the component without an action.
 */
class BackupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val msg = runCatching { runBackup() }
                .onFailure { Log.w(TAG, "backup failed", it) }
                .getOrElse { getString(R.string.toast_config_bad) }
            Actions.toast(applicationContext, msg)
            finish()
        }
    }

    private suspend fun runBackup(): String {
        val file = resolveFile()
        return when (op()) {
            Op.Save -> {
                val store = ConfigStore(applicationContext)
                store.exportTo(file)
                getString(R.string.toast_config_saved_path, file.absolutePath)
            }
            Op.Restore -> {
                val exists = withContext(Dispatchers.IO) { file.isFile }
                if (!exists) {
                    getString(R.string.toast_config_missing, file.absolutePath)
                } else if (ConfigStore(applicationContext).importFrom(file)) {
                    getString(R.string.toast_config_loaded)
                } else {
                    getString(R.string.toast_config_bad)
                }
            }
            Op.Unknown -> getString(R.string.toast_backup_need_action)
        }
    }

    private fun op(): Op {
        when (intent.action) {
            ACTION_SAVE -> return Op.Save
            ACTION_RESTORE -> return Op.Restore
        }
        return when (intent.getStringExtra(EXTRA_OP)?.trim()?.lowercase()) {
            "save", "backup", "export" -> Op.Save
            "restore", "load", "import" -> Op.Restore
            else -> Op.Unknown
        }
    }

    private fun resolveFile(): File {
        val raw = intent.getStringExtra(EXTRA_PATH)?.trim().orEmpty()
            .ifEmpty { intent.getStringExtra(EXTRA_FILE)?.trim().orEmpty() }
        if (raw.isEmpty()) return ConfigStore.defaultLocalFile()
        val file = File(raw)
        return if (file.isDirectory) File(file, ConfigStore.LOCAL_FILE) else file
    }

    private enum class Op { Save, Restore, Unknown }

    companion object {
        private const val TAG = "CouchyBackup"
        const val ACTION_SAVE = "com.conreo.couchytv.action.BACKUP_SAVE"
        const val ACTION_RESTORE = "com.conreo.couchytv.action.BACKUP_RESTORE"
        const val EXTRA_OP = "op"
        const val EXTRA_PATH = "path"
        const val EXTRA_FILE = "file"
    }
}
