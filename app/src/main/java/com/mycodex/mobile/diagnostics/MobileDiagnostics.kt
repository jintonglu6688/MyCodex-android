package com.mycodex.mobile.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MobileDiagnostics {
    private const val LogTag = "MyCodex"
    private const val ProviderSuffix = ".fileprovider"
    @Volatile private var store: DiagnosticLogStore? = null

    @Synchronized
    fun initialize(context: Context) {
        if (store != null) return
        val application = context.applicationContext
        store = DiagnosticLogStore(File(application.filesDir, "diagnostics"))
        val version = runCatching {
            application.packageManager.getPackageInfo(application.packageName, 0).versionName
        }.getOrNull().orEmpty()
        info(
            "Application",
            "session_started version=${safeValue(version)} sdk=${Build.VERSION.SDK_INT} " +
                "device=${safeValue(Build.MANUFACTURER)}/${safeValue(Build.MODEL)} pid=${Process.myPid()}",
        )
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            error("Crash", "uncaught_exception thread=${safeValue(thread.name)}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun info(category: String, message: String) {
        runCatching { Log.i(LogTag, "[$category] $message") }
        store?.append("INFO", category, message)
    }

    fun warn(category: String, message: String, error: Throwable? = null) {
        val safeMessage = withErrorType(message, error)
        runCatching { Log.w(LogTag, "[$category] $safeMessage") }
        store?.append("WARN", category, safeMessage)
    }

    fun error(category: String, message: String, error: Throwable? = null) {
        val safeMessage = withErrorType(message, error)
        runCatching { Log.e(LogTag, "[$category] $safeMessage") }
        store?.append("ERROR", category, safeMessage)
    }

    fun prepareShareIntent(context: Context): Intent {
        val currentStore = checkNotNull(store) { "Diagnostics are not initialized." }
        info("Diagnostics", "share_report_requested")
        val report = currentStore.createShareReport(
            File(context.cacheDir, "shared-diagnostics"),
            "MyCodex Android diagnostics\nGenerated: ${DiagnosticLogStore.formatTimestamp(System.currentTimeMillis())}\n" +
                "App: ${context.packageName}\nSDK: ${Build.VERSION.SDK_INT}\n\n",
        )
        val uri = FileProvider.getUriForFile(context, context.packageName + ProviderSuffix, report)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "MyCodex Android diagnostics")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("MyCodex diagnostics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shortId(value: String?): String = value?.trim()?.takeIf(String::isNotEmpty)?.take(10) ?: "-"

    private fun withErrorType(message: String, error: Throwable?): String =
        if (error == null) message else "$message errorType=${error.javaClass.simpleName}"

    private fun safeValue(value: String): String = value.replace(Regex("[\\r\\n=]+"), "_").take(80)
}

internal class DiagnosticLogStore(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val processId: Int = Process.myPid(),
    private val retainedFiles: Int = 12,
    private val maxFileBytes: Long = 2L * 1024L * 1024L,
) {
    private val lock = Any()
    private var segment = 0
    private var currentFile: File? = null

    fun append(level: String, category: String, message: String) = synchronized(lock) {
        directory.mkdirs()
        var target = currentFile ?: createSessionFile().also { currentFile = it }
        if (target.length() >= maxFileBytes) {
            segment += 1
            target = createSessionFile().also { currentFile = it }
        }
        val line = "${formatTimestamp(now())} [$level] [${sanitize(category)}] ${sanitize(message)}\n"
        target.appendText(line, Charsets.UTF_8)
        deleteExpiredLogs()
    }

    fun createShareReport(outputDirectory: File, header: String): File = synchronized(lock) {
        outputDirectory.mkdirs()
        outputDirectory.listFiles { file -> file.name.startsWith("mycodex-android-diagnostics-") }
            ?.sortedByDescending(File::lastModified)
            ?.drop(3)
            ?.forEach(File::delete)
        val output = File(outputDirectory, "mycodex-android-diagnostics-${fileTimestamp(now())}.txt")
        val builder = StringBuilder(header)
        directory.listFiles { file -> file.isFile && file.name.startsWith("android-") && file.extension == "log" }
            ?.sortedByDescending(File::lastModified)
            ?.take(4)
            ?.forEach { file ->
                if (builder.length >= MaxReportCharacters) return@forEach
                builder.append("===== ").append(file.name).append(" =====\n")
                val remaining = MaxReportCharacters - builder.length
                builder.append(file.readText(Charsets.UTF_8).takeLast(remaining.coerceAtLeast(0))).append('\n')
            }
        output.writeText(builder.toString(), Charsets.UTF_8)
        output
    }

    private fun createSessionFile(): File {
        val suffix = if (segment == 0) "" else "-s$segment"
        var candidate = File(directory, "android-${fileTimestamp(now())}-p$processId$suffix.log")
        var collision = 0
        while (candidate.exists()) {
            collision += 1
            candidate = File(directory, "android-${fileTimestamp(now())}-p$processId$suffix-$collision.log")
        }
        candidate.createNewFile()
        return candidate
    }

    private fun deleteExpiredLogs() {
        directory.listFiles { file -> file.isFile && file.name.startsWith("android-") && file.extension == "log" }
            ?.filterNot { it == currentFile }
            ?.sortedByDescending(File::lastModified)
            ?.drop((retainedFiles.coerceAtLeast(1) - 1).coerceAtLeast(0))
            ?.forEach(File::delete)
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("(?i)Authorization\\s*[:=]\\s*Bearer\\s+[^\\s,;]+"), "Authorization: Bearer [REDACTED]")
        .replace(Regex("(?i)(tenantSecret|deviceToken|claimAccessToken|ticket)\\s*[:=]\\s*[^\\s,;]+"), "$1=[REDACTED]")
        .replace("\r", " ")
        .replace("\n", " ")
        .take(1000)

    companion object {
        private const val MaxReportCharacters = 4 * 1024 * 1024

        internal fun formatTimestamp(value: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(value))

        private fun fileTimestamp(value: Long): String =
            SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(value))
    }
}
