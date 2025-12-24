package com.gamebot.ai.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试日志收集器
 * 将关键日志同时输出到logcat和文件
 */
object DebugLogger {
    private const val TAG = "DebugLogger"
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            logFile = File(logDir, "debug_$timestamp.log")

            log("DEBUG", "日志系统已初始化")
            log("DEBUG", "日志文件: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "初始化日志文件失败", e)
        }
    }

    fun log(level: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val fullMessage = "[$timestamp] [$level] $message"

        // 输出到logcat
        when (level) {
            "ERROR" -> Log.e(TAG, fullMessage, throwable)
            "WARN" -> Log.w(TAG, fullMessage)
            "INFO" -> Log.i(TAG, fullMessage)
            else -> Log.d(TAG, fullMessage)
        }

        // 写入文件
        try {
            logFile?.appendText(fullMessage + "\n")
            throwable?.let {
                logFile?.appendText(it.stackTraceToString() + "\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入日志文件失败", e)
        }
    }

    fun i(message: String) = log("INFO", message)
    fun w(message: String) = log("WARN", message)
    fun e(message: String, throwable: Throwable? = null) = log("ERROR", message, throwable)
    fun d(message: String) = log("DEBUG", message)

    fun getLogFilePath(): String? = logFile?.absolutePath
}
