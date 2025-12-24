package com.gamebot.ai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.gamebot.ai.MainActivityNew
import com.gamebot.ai.R
import com.gamebot.ai.service.GameBotAccessibilityService

/**
 * 首页Fragment
 */
class HomeFragment : Fragment() {

    private lateinit var statusContainer: LinearLayout
    private lateinit var statusIcon: TextView
    private lateinit var statusText: TextView
    private lateinit var tvFps: TextView
    private lateinit var tvFrames: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var settingsButton: Button

    // 权限状态指示器
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvScreenCaptureStatus: TextView
    private lateinit var indicatorAccessibility: View
    private lateinit var indicatorOverlay: View
    private lateinit var indicatorBattery: View
    private lateinit var indicatorScreenCapture: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化视图
        initViews(view)

        // 设置监听器
        setupListeners()

        // 更新UI
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        // 开始定期更新UI（每秒刷新一次）
        startUIUpdates()
    }

    override fun onPause() {
        super.onPause()
        // 停止定期更新
        stopUIUpdates()
    }

    private val uiUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            uiUpdateHandler.postDelayed(this, 1000) // 每秒更新一次
        }
    }

    private fun startUIUpdates() {
        uiUpdateHandler.post(uiUpdateRunnable)
    }

    private fun stopUIUpdates() {
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
    }

    private fun initViews(view: View) {
        statusContainer = view.findViewById(R.id.statusContainer)
        statusIcon = view.findViewById(R.id.statusIcon)
        statusText = view.findViewById(R.id.statusText)
        tvFps = view.findViewById(R.id.tvFps)
        tvFrames = view.findViewById(R.id.tvFrames)
        startButton = view.findViewById(R.id.startButton)
        stopButton = view.findViewById(R.id.stopButton)
        settingsButton = view.findViewById(R.id.settingsButton)

        // 权限状态视图
        tvAccessibilityStatus = view.findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = view.findViewById(R.id.tvOverlayStatus)
        tvBatteryStatus = view.findViewById(R.id.tvBatteryStatus)
        tvScreenCaptureStatus = view.findViewById(R.id.tvScreenCaptureStatus)
        indicatorAccessibility = view.findViewById(R.id.indicatorAccessibility)
        indicatorOverlay = view.findViewById(R.id.indicatorOverlay)
        indicatorBattery = view.findViewById(R.id.indicatorBattery)
        indicatorScreenCapture = view.findViewById(R.id.indicatorScreenCapture)
    }

    private fun setupListeners() {
        startButton.setOnClickListener {
            // 调用MainActivity的启动方法
            (activity as? MainActivityNew)?.startBot()
        }

        stopButton.setOnClickListener {
            (activity as? MainActivityNew)?.stopBot()
        }

        settingsButton.setOnClickListener {
            (activity as? MainActivityNew)?.openAccessibilitySettings()
        }
    }

    private fun updateUI() {
        val isRunning = GameBotAccessibilityService.isRunning
        val service = GameBotAccessibilityService.instance

        // 更新运行状态和背景
        if (isRunning && service != null) {
            // 运行中 - 绿色渐变背景
            statusContainer.setBackgroundResource(R.drawable.bg_gradient_running)
            statusIcon.text = "▶"
            statusIcon.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            statusText.text = "运行中"
            statusText.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            tvFps.text = service.getCurrentFPS().toInt().toString()
            tvFrames.text = service.getFrameCount().toString()
        } else {
            // 未运行 - 灰色渐变背景
            statusContainer.setBackgroundResource(R.drawable.bg_gradient_stopped)
            statusIcon.text = "⏸"
            statusIcon.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            statusText.text = "未运行"
            statusText.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            tvFps.text = "0"
            tvFrames.text = "0"
        }

        // 更新按钮状态
        startButton.isEnabled = !isRunning
        stopButton.isEnabled = isRunning

        // 更新权限状态
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val context = requireContext()

        // 检查无障碍服务
        val isAccessibilityEnabled = GameBotAccessibilityService.instance != null
        updatePermissionIndicator(
            tvAccessibilityStatus,
            indicatorAccessibility,
            isAccessibilityEnabled,
            "✓ 无障碍服务",
            "✗ 无障碍服务"
        )

        // 检查悬浮窗权限
        val hasOverlayPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
        updatePermissionIndicator(
            tvOverlayStatus,
            indicatorOverlay,
            hasOverlayPermission,
            "✓ 悬浮窗权限",
            "✗ 悬浮窗权限"
        )

        // 检查电池优化
        val hasBatteryOptimization = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
        updatePermissionIndicator(
            tvBatteryStatus,
            indicatorBattery,
            hasBatteryOptimization,
            "✓ 电池优化豁免",
            "✗ 电池优化豁免"
        )

        // 屏幕录制权限状态（这个需要MainActivity传递）
        val mainActivity = activity as? MainActivityNew
        val hasScreenCapture = mainActivity?.hasScreenCapturePermission() ?: false
        updatePermissionIndicator(
            tvScreenCaptureStatus,
            indicatorScreenCapture,
            hasScreenCapture,
            "✓ 屏幕录制权限",
            "✗ 屏幕录制权限"
        )
    }

    private fun updatePermissionIndicator(
        textView: TextView,
        indicator: View,
        hasPermission: Boolean,
        grantedText: String,
        deniedText: String
    ) {
        if (hasPermission) {
            textView.text = grantedText
            textView.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
            indicator.setBackgroundResource(android.R.drawable.presence_online)
        } else {
            textView.text = deniedText
            textView.setTextColor(resources.getColor(android.R.color.darker_gray, null))
            indicator.setBackgroundResource(android.R.drawable.presence_busy)
        }
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
