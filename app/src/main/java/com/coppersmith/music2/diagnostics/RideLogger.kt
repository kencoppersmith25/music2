package com.coppersmith.music2.diagnostics

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object RideLogger {

    private const val TAG = "RideLogger"
    private const val LOG_FILE_NAME = "ride_log.txt"
    private const val PUBLIC_FILE_NAME = "nhr_ride_log.txt"
    private const val LOG_SUBDIRECTORY = "logs"
    private const val MAX_LINES = 10_000

    private val lock = Any()

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var appContext: Context? = null
    private var logFile: File? = null
    private var publicMediaUri: Uri? = null
    private var lineCount: Int = 0

    val isActive: Boolean
        get() = synchronized(lock) { logFile != null }

    val hasLog: Boolean
        get() = synchronized(lock) { lineCount > 0 }

    private var defaultUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    fun startAutomatically(context: Context): Result<Unit> = runCatching {
        synchronized(lock) {
            if (logFile != null) return@runCatching

            val applicationContext = context.applicationContext
            this.appContext = applicationContext

            if (defaultUncaughtHandler == null) {
                defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                    val stackTrace = throwable.stackTraceToString()
                    log("FATAL_CRASH thread='${thread.name}' error='${throwable.message}'\n$stackTrace")
                    defaultUncaughtHandler?.uncaughtException(thread, throwable)
                }
            }

            val logDir = File(applicationContext.filesDir, LOG_SUBDIRECTORY)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val file = File(logDir, LOG_FILE_NAME)
            this.logFile = file
            android.util.Log.d(TAG, "INTERNAL FILE PATH: ${file.absolutePath}")

            setupPublicMediaStoreFile(applicationContext)

            pruneOldEntries(file)
            prunePublicMediaStoreFile(applicationContext)

            lineCount = if (file.exists()) file.readLines().size else 0

            log("LOG_SYSTEM: Ride log started")
            log("LOG_SYSTEM: Device Android version=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
        }
    }

    private fun setupPublicMediaStoreFile(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("nhr_ride_log%.txt")

                val matchingUris = mutableListOf<Uri>()

                resolver.query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Downloads.DATE_ADDED} ASC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol)
                        val uri = ContentUris.withAppendedId(collection, id)

                        if (name == PUBLIC_FILE_NAME) {
                            matchingUris.add(0, uri)
                        } else {
                            matchingUris.add(uri)
                        }
                    }
                }

                if (matchingUris.isNotEmpty()) {
                    publicMediaUri = matchingUris.first()
                    if (matchingUris.size > 1) {
                        for (i in 1 until matchingUris.size) {
                            try {
                                resolver.delete(matchingUris[i], null, null)
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, PUBLIC_FILE_NAME)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/Music2Radio")
                    }
                    publicMediaUri = resolver.insert(collection, contentValues)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "MediaStore setup error: ${e.message}", e)
            }
        }
    }

    fun log(message: String) {
        android.util.Log.d(TAG, message)

        synchronized(lock) {
            val file = logFile ?: return
            if (lineCount >= MAX_LINES) return

            val timestamp = timestampFormat.format(Date())
            val line = "$timestamp  ${message.replace('\n', ' ')}\n"

            try {
                file.appendText(line)
                lineCount++
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed writing to internal log: ${e.message}")
            }

            publicMediaUri?.let { uri ->
                try {
                    appContext?.contentResolver?.openOutputStream(uri, "wa")?.use { stream ->
                        stream.write(line.toByteArray())
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed writing to MediaStore log: ${e.message}")
                }
            }
        }
    }

    fun share(context: Context): Result<Unit> = runCatching {
        val file = synchronized(lock) { logFile } ?: error("No log file available.")
        if (!file.exists()) error("Log file is empty.")

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Music 2 Radio Ride Log - $timestamp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share Diagnostic Ride Log via...").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun emailToSupport(context: Context): Result<Unit> = runCatching {
        val file = synchronized(lock) { logFile } ?: error("No log file available.")
        if (!file.exists()) error("Log file is empty.")

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        val logSnippet = try { file.readText().takeLast(4000) } catch (_: Exception) { "" }

        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("kencoppersmith@yahoo.com"))
            putExtra(Intent.EXTRA_SUBJECT, "No Hands Radio Diagnostic Ride Log")
            putExtra(Intent.EXTRA_TEXT, "Hi Ken,\n\nAttached is my diagnostic Ride Log file for No Hands Radio.\n\n---\nLog Snippet:\n$logSnippet")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("RideLog", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(emailIntent, "Send Ride Log via Email...").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun emailToSupportDirect(context: Context, onResult: (Boolean, String) -> Unit) {
        val file = synchronized(lock) { logFile }
        if (file == null || !file.exists() || file.length() == 0L) {
            onResult(false, "Ride log is empty.")
            return
        }

        val logText = file.readText()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://formsubmit.co/ajax/kencoppersmith@yahoo.com")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", "NoHandsRadio/2.0 Android")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.doOutput = true

                val jsonPayload = JSONObject().apply {
                    put("_subject", "No Hands Radio Diagnostic Ride Log - $timestamp")
                    put("_template", "table")
                    put("_captcha", "false")
                    put("device", deviceModel)
                    put("timestamp", timestamp)
                    put("ride_log", logText.takeLast(10000))
                }

                conn.outputStream.use { os ->
                    os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    withContext(Dispatchers.Main) {
                        onResult(true, "Log sent successfully to kencoppersmith@yahoo.com! ✉️")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        emailToSupport(context)
                        onResult(true, "Opening email app to send log...")
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    emailToSupport(context)
                    onResult(true, "Opening email app to send log...")
                }
            }
        }
    }

    fun readLogText(): String {
        return synchronized(lock) {
            logFile?.takeIf { it.exists() }?.readText() ?: "No log entries recorded yet."
        }
    }

    fun clearLog() {
        synchronized(lock) {
            logFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                }
                lineCount = 0
            }
            publicMediaUri?.let { uri ->
                try {
                    appContext?.contentResolver?.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write("".toByteArray())
                    }
                } catch (_: Exception) {}
            }
            log("LOG_SYSTEM: Log cleared.")
        }
    }

    private fun getCutoffDateString(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    private fun pruneOldEntries(file: File) {
        if (!file.exists()) return

        try {
            val cutoffDate = getCutoffDateString()
            val lines = file.readLines()

            val filteredLines = lines.filter { line ->
                if (line.length < 10) return@filter true
                val lineDate = line.substring(0, 10)
                lineDate >= cutoffDate
            }

            if (filteredLines.size < lines.size) {
                file.writeText(filteredLines.joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Internal pruning failed: ${e.message}")
        }
    }

    private fun prunePublicMediaStoreFile(context: Context) {
        val uri = publicMediaUri ?: return
        try {
            val cutoffDate = getCutoffDateString()
            val lines = mutableListOf<String>()

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        lines.add(line)
                        line = reader.readLine()
                    }
                }
            }

            val filteredLines = lines.filter { line ->
                if (line.length < 10) return@filter true
                val lineDate = line.substring(0, 10)
                lineDate >= cutoffDate
            }

            if (filteredLines.size < lines.size) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    val output = filteredLines.joinToString("\n") + "\n"
                    stream.write(output.toByteArray())
                }
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Public log pruning failed: ${e.message}")
        }
    }
}