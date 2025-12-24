package com.gamebot.ai.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.gamebot.ai.R
import com.gamebot.ai.service.GameBotAccessibilityService
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File

/**
 * 数据收集Fragment
 */
class DataCollectionFragment : Fragment() {

    private lateinit var tvTotalImages: TextView
    private lateinit var tvLabeledImages: TextView
    private lateinit var tvUnlabeledImages: TextView
    private lateinit var tvIntervalValue: TextView

    private lateinit var btnCapture: Button
    private lateinit var btnViewDataset: Button
    private lateinit var btnLabelImages: Button
    private lateinit var btnExportDataset: Button
    private lateinit var btnClearDataset: Button

    private lateinit var switchAutoCapture: SwitchMaterial
    private lateinit var sliderInterval: Slider

    // 定期更新统计数据
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStatistics()
            updateHandler.postDelayed(this, 2000) // 每2秒更新一次
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_data_collection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        updateStatistics()
    }

    override fun onResume() {
        super.onResume()
        // 开始定期更新统计数据
        updateHandler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        // 停止定期更新
        updateHandler.removeCallbacks(updateRunnable)
    }

    private fun initViews(view: View) {
        tvTotalImages = view.findViewById(R.id.tvTotalImages)
        tvLabeledImages = view.findViewById(R.id.tvLabeledImages)
        tvUnlabeledImages = view.findViewById(R.id.tvUnlabeledImages)
        tvIntervalValue = view.findViewById(R.id.tvIntervalValue)

        btnCapture = view.findViewById(R.id.btnCapture)
        btnViewDataset = view.findViewById(R.id.btnViewDataset)
        btnLabelImages = view.findViewById(R.id.btnLabelImages)
        btnExportDataset = view.findViewById(R.id.btnExportDataset)
        btnClearDataset = view.findViewById(R.id.btnClearDataset)

        switchAutoCapture = view.findViewById(R.id.switchAutoCapture)
        sliderInterval = view.findViewById(R.id.sliderInterval)
    }

    private fun setupListeners() {
        // 手动截图
        btnCapture.setOnClickListener {
            captureScreenshot()
        }

        // 查看数据集
        btnViewDataset.setOnClickListener {
            Toast.makeText(context, "查看数据集功能开发中", Toast.LENGTH_SHORT).show()
        }

        // 标注图片
        btnLabelImages.setOnClickListener {
            val service = GameBotAccessibilityService.instance
            if (service == null) {
                Toast.makeText(context, "服务未运行", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val stats = service.getDatasetManager().getStatistics()
            if (stats.totalImages == 0) {
                Toast.makeText(context, "还没有截图数据，请先截图", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 启动标注Activity
            val intent = Intent(requireContext(), ImageAnnotationActivity::class.java)
            startActivity(intent)
        }

        // 导出数据集
        btnExportDataset.setOnClickListener {
            exportDataset()
        }

        // 清空数据集
        btnClearDataset.setOnClickListener {
            clearDataset()
        }

        // 自动截图开关
        switchAutoCapture.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startAutoCapture()
            } else {
                stopAutoCapture()
            }
        }

        // 截图间隔滑块
        sliderInterval.addOnChangeListener { _, value, _ ->
            tvIntervalValue.text = "当前: ${value.toInt()}秒"
        }
    }

    private fun updateStatistics() {
        val service = GameBotAccessibilityService.instance
        if (service != null) {
            try {
                val stats = service.getDatasetManager().getStatistics()
                tvTotalImages.text = stats.totalImages.toString()
                tvLabeledImages.text = stats.labeledImages.toString()
                tvUnlabeledImages.text = stats.unlabeledImages.toString()
            } catch (e: Exception) {
                // 服务未完全初始化
                tvTotalImages.text = "0"
                tvLabeledImages.text = "0"
                tvUnlabeledImages.text = "0"
            }
        } else {
            tvTotalImages.text = "0"
            tvLabeledImages.text = "0"
            tvUnlabeledImages.text = "0"
        }
    }

    private fun captureScreenshot() {
        val service = GameBotAccessibilityService.instance
        if (service == null) {
            Toast.makeText(context, "服务未运行，请先启动机器人", Toast.LENGTH_SHORT).show()
            return
        }

        if (!GameBotAccessibilityService.isRunning) {
            Toast.makeText(context, "请先在首页启动数据收集模式", Toast.LENGTH_SHORT).show()
            return
        }

        val success = service.captureScreenshot()
        if (success) {
            Toast.makeText(context, "截图成功", Toast.LENGTH_SHORT).show()
            updateStatistics()
        } else {
            Toast.makeText(context, "截图失败，请检查屏幕录制权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAutoCapture() {
        val service = GameBotAccessibilityService.instance
        if (service == null) {
            Toast.makeText(context, "服务未运行，请先启动机器人", Toast.LENGTH_SHORT).show()
            switchAutoCapture.isChecked = false
            return
        }

        if (!GameBotAccessibilityService.isRunning) {
            Toast.makeText(context, "请先在首页启动数据收集模式", Toast.LENGTH_SHORT).show()
            switchAutoCapture.isChecked = false
            return
        }

        val interval = sliderInterval.value.toInt()
        service.startAutoCapture(interval)
        Toast.makeText(context, "已启动自动截图，间隔${interval}秒", Toast.LENGTH_SHORT).show()
    }

    private fun stopAutoCapture() {
        val service = GameBotAccessibilityService.instance
        service?.stopAutoCapture()
        Toast.makeText(context, "已停止自动截图", Toast.LENGTH_SHORT).show()
    }

    private fun exportDataset() {
        val service = GameBotAccessibilityService.instance
        if (service == null) {
            Toast.makeText(context, "服务未运行", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val exportFile = service.getDatasetManager().exportToCOCO()
            if (exportFile != null) {
                Toast.makeText(context, "导出成功: ${exportFile.name}", Toast.LENGTH_LONG).show()

                // 提示用户文件位置
                AlertDialog.Builder(requireContext())
                    .setTitle("导出成功")
                    .setMessage("数据集已导出到:\n${exportFile.absolutePath}\n\n是否分享文件?")
                    .setPositiveButton("分享") { _, _ ->
                        shareFile(exportFile)
                    }
                    .setNegativeButton("关闭", null)
                    .show()
            } else {
                Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "导出出错: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearDataset() {
        AlertDialog.Builder(requireContext())
            .setTitle("确认清空")
            .setMessage("确定要删除所有截图和标注数据吗？此操作不可恢复！")
            .setPositiveButton("确定") { _, _ ->
                val service = GameBotAccessibilityService.instance
                if (service != null) {
                    val success = service.getDatasetManager().clearAll()
                    if (success) {
                        Toast.makeText(context, "已清空所有数据", Toast.LENGTH_SHORT).show()
                        updateStatistics()
                    } else {
                        Toast.makeText(context, "清空失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun shareFile(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "分享数据集"))
        } catch (e: Exception) {
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun newInstance() = DataCollectionFragment()
    }
}
