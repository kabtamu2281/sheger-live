package et.ethiolive.app

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Test builds only: keeps the last crash so the next launch can show it and testers can copy it into a bug
 * report. Kotlin/Java crashes are written by an uncaught-exception handler; native crashes (for example inside
 * the streaming SDK) are read back from the system's record of how the process last exited.
 */
object CrashReport {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeText("${BuildConfig.VERSION_NAME}\n${error.stackTraceToString()}".take(6000)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun showPending(activity: Activity) {
        val file = File(activity.filesDir, FILE)
        val report = runCatching { file.readText() }.getOrNull()?.also { file.delete() } ?: nativeCrash(activity) ?: return
        AlertDialog.Builder(activity)
            .setTitle("Ethio Live closed unexpectedly")
            .setMessage(report.take(1500))
            .setPositiveButton("Copy report") { _, _ ->
                activity.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Ethio Live crash", report))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /** The previous run's native crash, reported once (the system keeps the record, so remember what was shown). */
    private fun nativeCrash(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val exit = am.getHistoricalProcessExitReasons(null, 0, 1).firstOrNull() ?: return null
        if (exit.reason != ApplicationExitInfo.REASON_CRASH_NATIVE) return null
        val prefs = context.getSharedPreferences("crash_report", Context.MODE_PRIVATE)
        if (prefs.getLong("shown", 0) == exit.timestamp) return null
        prefs.edit().putLong("shown", exit.timestamp).apply()
        return "${BuildConfig.VERSION_NAME}\nNative crash: ${exit.description}"
    }
}
