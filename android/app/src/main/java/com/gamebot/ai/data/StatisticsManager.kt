package com.gamebot.ai.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * 统计数据管理器
 * 负责管理运行时统计数据的存储和计算
 */
class StatisticsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "gamebot_statistics"

        // 今日统计
        private const val KEY_TODAY_DATE = "today_date"
        private const val KEY_TODAY_RUNTIME = "today_runtime_ms"
        private const val KEY_TODAY_DETECTIONS = "today_detections"
        private const val KEY_TODAY_CAPTURES = "today_captures"
        private const val KEY_TODAY_FPS_SUM = "today_fps_sum"
        private const val KEY_TODAY_FPS_COUNT = "today_fps_count"

        // 历史统计
        private const val KEY_TOTAL_RUNTIME = "total_runtime_ms"
        private const val KEY_TOTAL_FRAMES = "total_frames"
        private const val KEY_TOTAL_CAPTURES = "total_captures"
        private const val KEY_FIRST_RUN_DATE = "first_run_date"

        // FPS分布
        private const val KEY_FPS_HIGH_COUNT = "fps_high_count"    // >30
        private const val KEY_FPS_MEDIUM_COUNT = "fps_medium_count" // 15-30
        private const val KEY_FPS_LOW_COUNT = "fps_low_count"      // <15
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // 运行时追踪
    private var sessionStartTime: Long = 0
    private var isRunning = false

    init {
        checkAndResetDaily()
    }

    /**
     * 检查是否需要重置今日统计
     */
    private fun checkAndResetDaily() {
        val today = dateFormat.format(Date())
        val savedDate = prefs.getString(KEY_TODAY_DATE, "")

        if (savedDate != today) {
            // 新的一天，重置今日统计
            prefs.edit().apply {
                putString(KEY_TODAY_DATE, today)
                putLong(KEY_TODAY_RUNTIME, 0)
                putInt(KEY_TODAY_DETECTIONS, 0)
                putInt(KEY_TODAY_CAPTURES, 0)
                putFloat(KEY_TODAY_FPS_SUM, 0f)
                putInt(KEY_TODAY_FPS_COUNT, 0)
                apply()
            }
        }

        // 记录首次运行日期
        if (!prefs.contains(KEY_FIRST_RUN_DATE)) {
            prefs.edit().putString(KEY_FIRST_RUN_DATE, today).apply()
        }
    }

    /**
     * 开始运行会话
     */
    fun startSession() {
        if (!isRunning) {
            sessionStartTime = System.currentTimeMillis()
            isRunning = true
        }
    }

    /**
     * 结束运行会话
     */
    fun endSession() {
        if (isRunning) {
            val duration = System.currentTimeMillis() - sessionStartTime
            addRuntime(duration)
            isRunning = false
        }
    }

    /**
     * 添加运行时长
     */
    private fun addRuntime(durationMs: Long) {
        prefs.edit().apply {
            putLong(KEY_TODAY_RUNTIME, prefs.getLong(KEY_TODAY_RUNTIME, 0) + durationMs)
            putLong(KEY_TOTAL_RUNTIME, prefs.getLong(KEY_TOTAL_RUNTIME, 0) + durationMs)
            apply()
        }
    }

    /**
     * 记录一次检测
     */
    fun recordDetection() {
        prefs.edit().apply {
            putInt(KEY_TODAY_DETECTIONS, prefs.getInt(KEY_TODAY_DETECTIONS, 0) + 1)
            putLong(KEY_TOTAL_FRAMES, prefs.getLong(KEY_TOTAL_FRAMES, 0) + 1)
            apply()
        }
    }

    /**
     * 记录一次截图
     */
    fun recordCapture() {
        prefs.edit().apply {
            putInt(KEY_TODAY_CAPTURES, prefs.getInt(KEY_TODAY_CAPTURES, 0) + 1)
            putInt(KEY_TOTAL_CAPTURES, prefs.getInt(KEY_TOTAL_CAPTURES, 0) + 1)
            apply()
        }
    }

    /**
     * 记录FPS
     */
    fun recordFPS(fps: Int) {
        // 记录平均FPS
        prefs.edit().apply {
            putFloat(KEY_TODAY_FPS_SUM, prefs.getFloat(KEY_TODAY_FPS_SUM, 0f) + fps)
            putInt(KEY_TODAY_FPS_COUNT, prefs.getInt(KEY_TODAY_FPS_COUNT, 0) + 1)
            apply()
        }

        // 记录FPS分布
        when {
            fps > 30 -> incrementFPSDistribution(KEY_FPS_HIGH_COUNT)
            fps >= 15 -> incrementFPSDistribution(KEY_FPS_MEDIUM_COUNT)
            else -> incrementFPSDistribution(KEY_FPS_LOW_COUNT)
        }
    }

    private fun incrementFPSDistribution(key: String) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    /**
     * 获取今日统计
     */
    fun getTodayStatistics(): TodayStatistics {
        val runtimeMs = prefs.getLong(KEY_TODAY_RUNTIME, 0)
        val detections = prefs.getInt(KEY_TODAY_DETECTIONS, 0)
        val captures = prefs.getInt(KEY_TODAY_CAPTURES, 0)

        val fpsSum = prefs.getFloat(KEY_TODAY_FPS_SUM, 0f)
        val fpsCount = prefs.getInt(KEY_TODAY_FPS_COUNT, 0)
        val avgFps = if (fpsCount > 0) (fpsSum / fpsCount).roundToInt() else 0

        return TodayStatistics(
            runtimeFormatted = formatDuration(runtimeMs),
            detections = detections,
            captures = captures,
            avgFps = avgFps
        )
    }

    /**
     * 获取历史统计
     */
    fun getHistoricalStatistics(): HistoricalStatistics {
        val totalRuntimeMs = prefs.getLong(KEY_TOTAL_RUNTIME, 0)
        val totalFrames = prefs.getLong(KEY_TOTAL_FRAMES, 0)
        val totalCaptures = prefs.getInt(KEY_TOTAL_CAPTURES, 0)

        val firstRunDate = prefs.getString(KEY_FIRST_RUN_DATE, dateFormat.format(Date())) ?: ""
        val today = dateFormat.format(Date())
        val daysSinceFirst = calculateDaysBetween(firstRunDate, today)

        return HistoricalStatistics(
            totalRuntimeFormatted = formatDuration(totalRuntimeMs),
            totalFrames = totalFrames,
            totalCaptures = totalCaptures,
            totalDays = daysSinceFirst
        )
    }

    /**
     * 获取FPS分布
     */
    fun getFPSDistribution(): FPSDistribution {
        val highCount = prefs.getInt(KEY_FPS_HIGH_COUNT, 0)
        val mediumCount = prefs.getInt(KEY_FPS_MEDIUM_COUNT, 0)
        val lowCount = prefs.getInt(KEY_FPS_LOW_COUNT, 0)
        val total = highCount + mediumCount + lowCount

        return if (total > 0) {
            FPSDistribution(
                highPercent = (highCount * 100 / total),
                mediumPercent = (mediumCount * 100 / total),
                lowPercent = (lowCount * 100 / total)
            )
        } else {
            FPSDistribution(0, 0, 0)
        }
    }

    /**
     * 清空所有统计数据
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        checkAndResetDaily()
    }

    /**
     * 格式化时长
     */
    private fun formatDuration(durationMs: Long): String {
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs / (1000 * 60)) % 60

        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }

    /**
     * 计算两个日期之间的天数
     */
    private fun calculateDaysBetween(startDate: String, endDate: String): Int {
        return try {
            val start = dateFormat.parse(startDate)
            val end = dateFormat.parse(endDate)
            val diffMs = end.time - start.time
            (diffMs / (1000 * 60 * 60 * 24)).toInt() + 1
        } catch (e: Exception) {
            1
        }
    }

    /**
     * 今日统计数据
     */
    data class TodayStatistics(
        val runtimeFormatted: String,
        val detections: Int,
        val captures: Int,
        val avgFps: Int
    )

    /**
     * 历史统计数据
     */
    data class HistoricalStatistics(
        val totalRuntimeFormatted: String,
        val totalFrames: Long,
        val totalCaptures: Int,
        val totalDays: Int
    )

    /**
     * FPS分布数据
     */
    data class FPSDistribution(
        val highPercent: Int,  // >30 FPS百分比
        val mediumPercent: Int, // 15-30 FPS百分比
        val lowPercent: Int    // <15 FPS百分比
    )
}
