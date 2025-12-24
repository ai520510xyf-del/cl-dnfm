package com.gamebot.ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gamebot.ai.service.GameBotAccessibilityService
import com.gamebot.ai.utils.DebugLogger

/**
 * 主Activity
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var settingsButton: Button

    // MediaProjection相关
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var screenCaptureResultCode = 0
    private var screenCaptureData: Intent? = null

    // 屏幕录制权限请求
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        DebugLogger.i("=== 屏幕录制权限回调 ===")
        DebugLogger.i("resultCode=${result.resultCode}, RESULT_OK=${Activity.RESULT_OK}")
        DebugLogger.i("Activity状态: finishing=$isFinishing, destroyed=$isDestroyed")

        if (result.resultCode == Activity.RESULT_OK) {
            screenCaptureResultCode = result.resultCode
            screenCaptureData = result.data
            DebugLogger.i("权限授予成功，data=${result.data != null}")

            Toast.makeText(this, "屏幕录制权限已授予", Toast.LENGTH_SHORT).show()

            try {
                // 获取MediaProjection并传递给Service
                result.data?.let { data ->
                    DebugLogger.i("开始创建MediaProjection...")
                    val projection = mediaProjectionManager?.getMediaProjection(result.resultCode, data)

                    if (projection != null) {
                        DebugLogger.i("MediaProjection创建成功")
                        val serviceInstance = GameBotAccessibilityService.instance
                        DebugLogger.i("Service实例: ${serviceInstance != null}")

                        serviceInstance?.setMediaProjection(projection)
                        DebugLogger.i("MediaProjection已传递给Service")
                    } else {
                        DebugLogger.e("MediaProjection创建失败 - 返回null")
                        Toast.makeText(this, "获取屏幕录制失败", Toast.LENGTH_LONG).show()
                        return@registerForActivityResult
                    }
                } ?: run {
                    DebugLogger.e("result.data为null")
                }

                // 权限授予后，延迟启动以确保MediaProjection已设置
                DebugLogger.i("延迟500ms后启动机器人...")
                window.decorView.postDelayed({
                    DebugLogger.i("延迟回调执行")
                    DebugLogger.i("Activity状态检查: finishing=$isFinishing, destroyed=$isDestroyed")

                    if (!isFinishing && !isDestroyed) {
                        DebugLogger.i("Activity状态正常，开始启动机器人")
                        try {
                            startBot()
                            DebugLogger.i("startBot()调用成功")
                        } catch (e: Exception) {
                            DebugLogger.e("startBot()调用失败", e)
                            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        DebugLogger.w("Activity已销毁或正在finish，无法启动")
                    }
                }, 500)

            } catch (e: Exception) {
                DebugLogger.e("权限回调处理失败", e)
                Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            DebugLogger.w("用户拒绝屏幕录制权限")
            Toast.makeText(this, "需要屏幕录制权限才能运行", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化调试日志系统
        DebugLogger.init(this)
        DebugLogger.i("=== MainActivity onCreate ===")
        DebugLogger.i("应用启动")

        setContentView(R.layout.activity_main)

        // 初始化MediaProjectionManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        initViews()
        setupListeners()
        updateUI()

        DebugLogger.i("MainActivity初始化完成")
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    /**
     * 初始化视图
     */
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        settingsButton = findViewById(R.id.settingsButton)
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        startButton.setOnClickListener {
            startBot()
        }

        stopButton.setOnClickListener {
            stopBot()
        }

        settingsButton.setOnClickListener {
            openAccessibilitySettings()
        }
    }

    /**
     * 启动机器人
     */
    private fun startBot() {
        // 1. 检查无障碍服务
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "步骤1: 请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            openAccessibilitySettings()
            return
        }

        // 2. 检查悬浮窗权限
        if (!checkOverlayPermission()) {
            Toast.makeText(this, "步骤2: 需要悬浮窗权限，让机器人保持运行", Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }

        // 3. 检查电池优化
        if (!isIgnoringBatteryOptimizations()) {
            Toast.makeText(this, "步骤3: 建议关闭电池优化，防止服务被杀", Toast.LENGTH_SHORT).show()
            requestIgnoreBatteryOptimizations()
            return
        }

        // 4. 检查服务实例
        val service = GameBotAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "服务未就绪，请稍后再试", Toast.LENGTH_SHORT).show()
            return
        }

        // 5. 检查屏幕录制权限
        if (screenCaptureData == null) {
            Toast.makeText(this, "步骤4: 需要屏幕录制权限", Toast.LENGTH_SHORT).show()
            requestScreenCapturePermission()
            return
        }

        // 6. 检查模型文件并启动
        try {
            // 检查是否有训练好的模型
            var modelPath = "dnf_detection_model.tflite"
            var hasModel = false

            try {
                assets.open(modelPath).use {
                    hasModel = true
                }
            } catch (e: java.io.FileNotFoundException) {
                // 如果没有训练模型，使用基础模型
                modelPath = "mobilenet_ssd_base.tflite"
                try {
                    assets.open(modelPath).use {
                        hasModel = true
                    }
                } catch (e2: java.io.FileNotFoundException) {
                    hasModel = false
                }
            }

            if (!hasModel) {
                // 模型不存在，显示友好提示，但仍然启动悬浮窗（用于数据收集）
                Toast.makeText(this, """
                    ⚠️ 缺少AI模型

                    将启动数据收集模式，您可以：
                    1. 使用悬浮窗截图收集DNF数据
                    2. 在应用中训练模型
                    3. 使用训练好的模型进行游戏自动化
                """.trimIndent(), Toast.LENGTH_LONG).show()

                // 即使没有模型，也启动服务显示悬浮窗（数据收集模式）
                service.startBot(null)
            } else {
                // 模型存在，启动机器人
                service.startBot(modelPath)
                Toast.makeText(this, "✅ 机器人已启动！\n悬浮窗已显示", Toast.LENGTH_SHORT).show()
            }

            updateUI()

            // 提示用户可以最小化应用，悬浮窗会保持显示
            Toast.makeText(this, "✅ 启动成功！\n您可以按Home键最小化，悬浮窗会保持在屏幕上", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("MainActivity", "启动失败", e)
        }
    }

    /**
     * 检查悬浮窗权限
     */
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    /**
     * 请求悬浮窗权限
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    /**
     * 检查是否在电池优化白名单
     */
    private fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    /**
     * 请求忽略电池优化
     */
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 请求屏幕录制权限
     */
    private fun requestScreenCapturePermission() {
        mediaProjectionManager?.let { manager ->
            val intent = manager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(intent)
        } ?: run {
            Toast.makeText(this, "无法获取屏幕录制服务", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 停止机器人
     */
    private fun stopBot() {
        val service = GameBotAccessibilityService.instance
        service?.stopBot()

        Toast.makeText(this, "机器人已停止", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    /**
     * 打开无障碍设置
     */
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    /**
     * 检查无障碍服务是否开启
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/${GameBotAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceName) == true
    }

    /**
     * 更新UI
     */
    private fun updateUI() {
        val isServiceEnabled = isAccessibilityServiceEnabled()
        val hasOverlay = checkOverlayPermission()
        val isOptimized = isIgnoringBatteryOptimizations()
        val isRunning = GameBotAccessibilityService.isRunning

        // 更新状态文本
        statusText.text = when {
            !isServiceEnabled -> {
                "状态: 未授权\n" +
                "✗ 无障碍服务未开启\n" +
                "点击「设置」按钮开启"
            }
            !hasOverlay -> {
                "状态: 权限不足\n" +
                "✓ 无障碍服务\n" +
                "✗ 悬浮窗权限\n" +
                "点击「启动」继续配置"
            }
            !isOptimized -> {
                "状态: 建议优化\n" +
                "✓ 无障碍服务\n" +
                "✓ 悬浮窗权限\n" +
                "⚠ 建议关闭电池优化\n" +
                "点击「启动」继续"
            }
            isRunning -> {
                val service = GameBotAccessibilityService.instance
                val fps = service?.getCurrentFPS()?.toInt() ?: 0
                val frames = service?.getFrameCount() ?: 0
                "状态: 运行中 🟢\n" +
                "FPS: $fps | 帧数: $frames\n" +
                "查看通知栏了解详情"
            }
            else -> {
                "状态: 就绪 ✓\n" +
                "✓ 所有权限已授予\n" +
                "点击「启动」开始运行"
            }
        }

        // 更新按钮状态
        startButton.isEnabled = isServiceEnabled && !isRunning
        stopButton.isEnabled = isRunning
    }
}
