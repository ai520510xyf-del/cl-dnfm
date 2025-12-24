package com.gamebot.ai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.gamebot.ai.R
import com.gamebot.ai.service.GameBotAccessibilityService

/**
 * 统计数据Fragment
 */
class StatisticsFragment : Fragment() {

    // 今日统计
    private lateinit var tvTodayRuntime: TextView
    private lateinit var tvTodayDetections: TextView
    private lateinit var tvTodayCaptures: TextView
    private lateinit var tvAvgFps: TextView

    // 历史统计
    private lateinit var tvTotalRuntime: TextView
    private lateinit var tvTotalFrames: TextView
    private lateinit var tvTotalCaptures: TextView
    private lateinit var tvTotalDays: TextView

    // FPS分布
    private lateinit var tvFpsHigh: TextView
    private lateinit var tvFpsMedium: TextView
    private lateinit var tvFpsLow: TextView

    // 操作按钮
    private lateinit var btnExportStats: Button
    private lateinit var btnClearStats: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_statistics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        updateStatistics()
    }

    override fun onResume() {
        super.onResume()
        updateStatistics()
    }

    private fun initViews(view: View) {
        // 今日统计
        tvTodayRuntime = view.findViewById(R.id.tvTodayRuntime)
        tvTodayDetections = view.findViewById(R.id.tvTodayDetections)
        tvTodayCaptures = view.findViewById(R.id.tvTodayCaptures)
        tvAvgFps = view.findViewById(R.id.tvAvgFps)

        // 历史统计
        tvTotalRuntime = view.findViewById(R.id.tvTotalRuntime)
        tvTotalFrames = view.findViewById(R.id.tvTotalFrames)
        tvTotalCaptures = view.findViewById(R.id.tvTotalCaptures)
        tvTotalDays = view.findViewById(R.id.tvTotalDays)

        // FPS分布
        tvFpsHigh = view.findViewById(R.id.tvFpsHigh)
        tvFpsMedium = view.findViewById(R.id.tvFpsMedium)
        tvFpsLow = view.findViewById(R.id.tvFpsLow)

        // 操作按钮
        btnExportStats = view.findViewById(R.id.btnExportStats)
        btnClearStats = view.findViewById(R.id.btnClearStats)
    }

    private fun setupListeners() {
        btnExportStats.setOnClickListener {
            exportStatistics()
        }

        btnClearStats.setOnClickListener {
            clearStatistics()
        }
    }

    private fun updateStatistics() {
        val service = GameBotAccessibilityService.instance
        if (service != null) {
            try {
                val statisticsManager = service.getStatisticsManager()

                // 今日统计（真实数据）
                val todayStats = statisticsManager.getTodayStatistics()
                tvTodayRuntime.text = todayStats.runtimeFormatted
                tvTodayDetections.text = todayStats.detections.toString()
                tvTodayCaptures.text = todayStats.captures.toString()
                tvAvgFps.text = todayStats.avgFps.toString()

                // 历史统计（真实数据）
                val historicalStats = statisticsManager.getHistoricalStatistics()
                tvTotalRuntime.text = historicalStats.totalRuntimeFormatted
                tvTotalFrames.text = String.format("%,d", historicalStats.totalFrames)
                tvTotalCaptures.text = String.format("%,d", historicalStats.totalCaptures)
                tvTotalDays.text = "${historicalStats.totalDays}天"

                // FPS分布（真实数据）
                val fpsDistribution = statisticsManager.getFPSDistribution()
                tvFpsHigh.text = "${fpsDistribution.highPercent}%"
                tvFpsMedium.text = "${fpsDistribution.mediumPercent}%"
                tvFpsLow.text = "${fpsDistribution.lowPercent}%"
            } catch (e: Exception) {
                // 服务未完全初始化或出错，显示0
                tvTodayRuntime.text = "0m"
                tvTodayDetections.text = "0"
                tvTodayCaptures.text = "0"
                tvAvgFps.text = "0"

                tvTotalRuntime.text = "0m"
                tvTotalFrames.text = "0"
                tvTotalCaptures.text = "0"
                tvTotalDays.text = "0天"

                tvFpsHigh.text = "0%"
                tvFpsMedium.text = "0%"
                tvFpsLow.text = "0%"
            }
        } else {
            // 服务未运行，显示0
            tvTodayRuntime.text = "0m"
            tvTodayDetections.text = "0"
            tvTodayCaptures.text = "0"
            tvAvgFps.text = "0"

            tvTotalRuntime.text = "0m"
            tvTotalFrames.text = "0"
            tvTotalCaptures.text = "0"
            tvTotalDays.text = "0天"

            tvFpsHigh.text = "0%"
            tvFpsMedium.text = "0%"
            tvFpsLow.text = "0%"
        }
    }

    private fun exportStatistics() {
        Toast.makeText(context, "导出统计数据功能开发中", Toast.LENGTH_SHORT).show()
        // TODO: 实现统计数据导出为JSON或CSV
    }

    private fun clearStatistics() {
        // 显示确认对话框
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("确认清空")
            .setMessage("确定要清空所有统计数据吗？此操作不可恢复。")
            .setPositiveButton("确定") { _, _ ->
                val service = GameBotAccessibilityService.instance
                if (service != null) {
                    try {
                        service.getStatisticsManager().clearAll()
                        updateStatistics()
                        Toast.makeText(context, "统计数据已清空", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "清空失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "服务未运行", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    companion object {
        fun newInstance() = StatisticsFragment()
    }
}
