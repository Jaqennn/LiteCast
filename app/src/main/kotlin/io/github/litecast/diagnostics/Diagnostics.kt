/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.litecast.diagnostics

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import io.github.litecast.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Local, bounded crash record. No network upload and no attempt to restart a dying process. */
object Diagnostics {
    private lateinit var app: Context
    private val current = MutableStateFlow<DiagnosticReport?>(null)
    val reports = current.asStateFlow()
    @Volatile private var stage = "application"
    private var installed = false
    private val lock = Any()
    private val preferences get() = app.getSharedPreferences("litecast.diagnostics", Context.MODE_PRIVATE)
    private val file get() = AtomicFile(File(app.filesDir, "litecast-diagnostic.json"))

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        app = context.applicationContext ?: context
        current.value = read()
        val interrupted = preferences.getString("startup_stage", null)
        if (interrupted != null) {
            record("LC-START-INTERRUPTED", interrupted,
                "Previous startup did not finish. No crash stack was captured; the process may have crashed or been ended by the system/user.", true)
            endStartup()
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                record("LC-JAVA-CRASH", stage, DiagnosticReport.describe(error), true)
                endStartup()
            } catch (_: Throwable) {
                // Never replace the original exception or block the system's crash handling.
            } finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
        }
        installed = true
    }

    fun device(context: Context): String {
        val info = DeviceInfo.current(context)
        @Suppress("DEPRECATION")
        val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        return "${info.brand} / ${info.model}\n${info.android} / API ${Build.VERSION.SDK_INT}\n" +
            "LiteCast ${info.app} ($versionCode) / ${Build.SUPPORTED_ABIS.joinToString()}\nBuild: ${Build.DISPLAY}"
    }

    fun record(code: String, at: String, error: Throwable): DiagnosticReport =
        record(code, at, DiagnosticReport.describe(error))

    fun record(code: String, at: String, detail: String, recovery: Boolean = false): DiagnosticReport {
        val report = DiagnosticReport(UUID.randomUUID().toString().take(8), System.currentTimeMillis(), code,
            at, runCatching { device(app) }.getOrDefault("Device information unavailable"),
            DiagnosticReport.redact(detail), recovery)
        synchronized(lock) {
            runCatching {
                val json = JSONObject().put("id", report.id).put("time", report.time).put("code", code)
                    .put("stage", at).put("device", report.device).put("detail", report.detail).put("recovery", recovery)
                val target = file
                val stream = target.startWrite()
                try {
                    stream.write(json.toString().toByteArray(Charsets.UTF_8))
                    target.finishWrite(stream)
                } catch (error: Exception) {
                    target.failWrite(stream)
                    throw error
                }
            }
            current.value = report
        }
        return report
    }

    fun needsRecovery(): Boolean = current.value?.let {
        it.recoveryRequired && preferences.getString("retry_id", null) != it.id
    } ?: false

    fun needsAttention(report: DiagnosticReport): Boolean = preferences.getString("viewed_id", null) != report.id
    fun acknowledge(report: DiagnosticReport) { preferences.edit().putString("viewed_id", report.id).apply() }
    fun allowRetry() { current.value?.let { preferences.edit().putString("retry_id", it.id).commit() } }

    fun beginStartup(at: String) {
        stage = at
        // Synchronous: a native crash cannot run the Java uncaught exception handler.
        preferences.edit().putString("startup_stage", at).commit()
    }

    fun endStartup() {
        preferences.edit().remove("startup_stage").commit()
        stage = "receiver runtime"
    }

    private fun read(): DiagnosticReport? = runCatching {
        val data = file.openRead().use { input ->
            val bytes = ByteArray(96_001)
            var size = 0
            while (size < bytes.size) {
                val n = input.read(bytes, size, bytes.size - size)
                if (n < 0) break
                size += n
            }
            check(size <= 96_000)
            String(bytes, 0, size, Charsets.UTF_8)
        }
        val json = JSONObject(data)
        DiagnosticReport(json.getString("id"), json.getLong("time"), json.getString("code"),
            json.getString("stage"), json.getString("device"), json.getString("detail"), json.optBoolean("recovery"))
    }.getOrNull()
}
