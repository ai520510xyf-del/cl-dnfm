package com.gamebot.ai

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gamebot.ai.service.GameBotAccessibilityService

/**
 * 主Activity
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var settingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updateUI()
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
        // 检查无障碍服务是否开启
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            openAccessibilitySettings()
            return
        }

        // 检查服务实例
        val service = GameBotAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "服务未就绪，请稍后再试", Toast.LENGTH_SHORT).show()
            return
        }

        // 启动机器人
        try {
            // 模型文件路径（需要将模型文件放在assets目录）
            val modelPath = "game_model_320.tflite"
            service.startBot(modelPath)

            Toast.makeText(this, "机器人已启动", Toast.LENGTH_SHORT).show()
            updateUI()

        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
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
        val isRunning = GameBotAccessibilityService.isRunning

        // 更新状态文本
        statusText.text = when {
            !isServiceEnabled -> "状态: 未授权\n请开启无障碍服务"
            isRunning -> {
                val service = GameBotAccessibilityService.instance
                val fps = service?.getCurrentFPS()?.toInt() ?: 0
                val frames = service?.getFrameCount() ?: 0
                "状态: 运行中\nFPS: $fps | 帧数: $frames"
            }
            else -> "状态: 就绪\n点击启动开始"
        }

        // 更新按钮状态
        startButton.isEnabled = isServiceEnabled && !isRunning
        stopButton.isEnabled = isRunning
    }
}
