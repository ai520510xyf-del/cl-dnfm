package com.gamebot.ai.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.gamebot.ai.controller.GameController
import com.gamebot.ai.detector.Detection
import com.gamebot.ai.detector.YoloDetector
import com.gamebot.ai.strategy.GameStrategy
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * 游戏机器人无障碍服务
 *
 * 核心功能：
 * 1. 屏幕截图
 * 2. YOLO检测
 * 3. 策略决策
 * 4. 模拟操作
 */
@RequiresApi(Build.VERSION_CODES.N)
class GameBotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GameBotService"
        var instance: GameBotAccessibilityService? = null
        var isRunning = false
    }

    // 核心组件
    private lateinit var controller: GameController
    private var detector: YoloDetector? = null
    private var strategy: GameStrategy? = null

    // 屏幕捕获相关
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    // 屏幕尺寸
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    // 协程
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var botJob: Job? = null

    // 统计信息
    private var frameCount = 0
    private var fps = 0f
    private var lastFrameTime = System.currentTimeMillis()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "无障碍服务已连接")

        instance = this

        // 初始化控制器
        controller = GameController(this)

        // 获取屏幕信息
        initScreenInfo()

        Log.i(TAG, "服务初始化完成 - 屏幕: ${screenWidth}x${screenHeight}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可以在这里监听特定的界面事件
    }

    override fun onInterrupt() {
        Log.w(TAG, "服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "服务销毁")

        stopBot()
        releaseScreenCapture()
        serviceScope.cancel()

        instance = null
    }

    /**
     * 初始化屏幕信息
     */
    private fun initScreenInfo() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = windowManager.defaultDisplay
            display.getRealMetrics(metrics)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }

        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        controller.setScreenSize(screenWidth, screenHeight)
    }

    /**
     * 启动机器人
     */
    fun startBot(modelPath: String) {
        if (isRunning) {
            Log.w(TAG, "机器人已在运行")
            return
        }

        Log.i(TAG, "启动机器人...")

        // 初始化检测器
        try {
            detector = YoloDetector(this, modelPath)
            strategy = GameStrategy()

            isRunning = true

            // 启动主循环
            botJob = serviceScope.launch {
                runBotLoop()
            }

            Log.i(TAG, "机器人已启动")

        } catch (e: Exception) {
            Log.e(TAG, "启动失败", e)
            isRunning = false
        }
    }

    /**
     * 停止机器人
     */
    fun stopBot() {
        if (!isRunning) return

        Log.i(TAG, "停止机器人...")

        isRunning = false
        botJob?.cancel()
        botJob = null

        detector?.close()
        detector = null

        Log.i(TAG, "机器人已停止")
    }

    /**
     * 机器人主循环
     */
    private suspend fun runBotLoop() {
        Log.i(TAG, "进入主循环")

        while (isRunning) {
            try {
                val loopStart = System.currentTimeMillis()

                // 1. 截图
                val screenshot = captureScreen()

                if (screenshot != null) {
                    // 2. YOLO检测
                    val detections = detector?.detect(screenshot) ?: emptyList()

                    // 3. 策略决策
                    val action = strategy?.makeDecision(screenshot, detections)

                    // 4. 执行操作
                    action?.let { executeAction(it) }

                    // 5. 更新统计
                    updateStats()

                    // 回收bitmap
                    screenshot.recycle()
                }

                // FPS限制 (30 FPS)
                val elapsed = System.currentTimeMillis() - loopStart
                val frameTime = 33L // 1000/30
                if (elapsed < frameTime) {
                    delay(frameTime - elapsed)
                }

            } catch (e: Exception) {
                Log.e(TAG, "主循环错误", e)
                delay(1000) // 出错后等待1秒
            }
        }

        Log.i(TAG, "退出主循环")
    }

    /**
     * 捕获屏幕截图
     *
     * 注意：这需要MediaProjection权限
     * 实际使用时需要在MainActivity中获取权限并传递给服务
     */
    private fun captureScreen(): Bitmap? {
        // TODO: 实现MediaProjection屏幕截图
        // 这里返回null，实际使用时需要实现完整的截图逻辑

        // 示例代码结构：
        // 1. 在MainActivity获取MediaProjection权限
        // 2. 通过Intent传递到Service
        // 3. 使用ImageReader获取屏幕内容

        return null
    }

    /**
     * 执行游戏操作
     */
    private fun executeAction(action: com.gamebot.ai.controller.GameAction) {
        when (action) {
            is com.gamebot.ai.controller.GameAction.Tap -> {
                controller.tapRandom(action.x, action.y)
                Log.d(TAG, "执行点击: (${action.x}, ${action.y})")
            }
            is com.gamebot.ai.controller.GameAction.Swipe -> {
                controller.swipe(action.startX, action.startY, action.endX, action.endY)
                Log.d(TAG, "执行滑动")
            }
            is com.gamebot.ai.controller.GameAction.LongPress -> {
                controller.longPress(action.x, action.y)
                Log.d(TAG, "执行长按")
            }
            is com.gamebot.ai.controller.GameAction.Wait -> {
                // 等待
            }
        }
    }

    /**
     * 更新统计信息
     */
    private fun updateStats() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastFrameTime

        if (elapsed > 0) {
            fps = 1000f / elapsed
        }

        lastFrameTime = currentTime

        // 每30帧输出一次日志
        if (frameCount % 30 == 0) {
            Log.d(TAG, "统计: 帧数=$frameCount, FPS=${fps.toInt()}")
        }
    }

    /**
     * 释放屏幕捕获资源
     */
    private fun releaseScreenCapture() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()

        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    /**
     * 获取当前FPS
     */
    fun getCurrentFPS(): Float = fps

    /**
     * 获取帧数
     */
    fun getFrameCount(): Int = frameCount
}
