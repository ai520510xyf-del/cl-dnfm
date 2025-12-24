package com.gamebot.ai.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.gamebot.ai.MainActivity
import com.gamebot.ai.MainActivityNew
import com.gamebot.ai.R
import com.gamebot.ai.controller.GameController
import com.gamebot.ai.data.DatasetManager
import com.gamebot.ai.data.StatisticsManager
import com.gamebot.ai.detector.Detection
import com.gamebot.ai.detector.Detector
import com.gamebot.ai.detector.YoloDetector
import com.gamebot.ai.detector.SSDDetector
import com.gamebot.ai.strategy.GameStrategy
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import android.os.Handler
import android.os.Looper

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
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "gamebot_service_channel"

        var instance: GameBotAccessibilityService? = null
        var isRunning = false
    }

    // 核心组件
    private lateinit var controller: GameController
    private var detector: Detector? = null
    private var strategy: GameStrategy? = null
    private lateinit var datasetManager: DatasetManager
    private lateinit var statisticsManager: StatisticsManager

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
    private val fpsHistory = ArrayDeque<Long>(30)  // 保存最近30帧的时间戳用于平滑FPS计算

    // 悬浮窗相关
    private var floatingView: View? = null
    private var windowManager: WindowManager? = null
    private var isFloatingExpanded = true
    private var lastX = 0
    private var lastY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // 悬浮窗View缓存，避免重复findViewById
    private var tvFps: TextView? = null
    private var tvFrames: TextView? = null
    private var tvMiniFps: TextView? = null

    // UI更新Handler，避免频繁创建协程
    private val mainHandler = Handler(Looper.getMainLooper())

    // 数据收集相关
    private var autoCapture = false
    private var captureInterval = 3000L // 默认3秒
    private var lastCaptureTime = 0L
    private val captureHandler = Handler(Looper.getMainLooper())
    private var captureRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "无障碍服务已连接")

        try {
            instance = this

            // 创建通知渠道
            createNotificationChannel()

            // 启动前台服务
            startForegroundService()

            // 初始化控制器
            controller = GameController(this)

            // 初始化数据集管理器
            datasetManager = DatasetManager(this)

            // 初始化统计管理器
            statisticsManager = StatisticsManager(this)

            // 获取屏幕信息
            initScreenInfo()

            Log.i(TAG, "服务初始化完成 - 屏幕: ${screenWidth}x${screenHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "服务初始化失败", e)
        }
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "游戏机器人服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI游戏机器人后台运行通知"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        try {
            val notification = createNotification("服务已就绪", "等待启动...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10-13
                startForeground(NOTIFICATION_ID, notification)
            } else {
                // Android 9及以下
                startForeground(NOTIFICATION_ID, notification)
            }

            Log.i(TAG, "前台服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
            // 即使前台服务启动失败，服务也应该继续运行
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(title: String, content: String): Notification {
        // 点击通知打开MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * 更新通知
     */
    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可以在这里监听特定的界面事件
    }

    override fun onInterrupt() {
        Log.w(TAG, "服务被中断")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "服务解绑")
        // 返回true表示希望onRebind被调用（如果服务重新绑定）
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.i(TAG, "服务重新绑定")
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "服务销毁")

        stopBot()
        releaseScreenCapture()
        removeFloatingWindow()

        // 清理Handler回调，防止内存泄漏
        mainHandler.removeCallbacksAndMessages(null)

        serviceScope.cancel()

        instance = null
        isRunning = false
    }

    /**
     * 初始化屏幕信息
     */
    private fun initScreenInfo() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用新API
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()

            val metrics = resources.displayMetrics
            screenDensity = metrics.densityDpi
        } else {
            // Android 10及以下使用旧API
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }

        controller.setScreenSize(screenWidth, screenHeight)
    }

    /**
     * 启动机器人
     * @param modelPath 模型文件路径，null表示数据收集模式
     */
    fun startBot(modelPath: String?) {
        if (isRunning) {
            Log.w(TAG, "机器人已在运行")
            return
        }

        Log.i(TAG, "启动机器人...")

        try {
            // 显示悬浮窗（可能失败，不应阻止启动）
            showFloatingWindow()
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败（继续启动）", e)
            updateNotification("悬浮窗错误", "无法显示悬浮窗: ${e.message}")
        }

        isRunning = true

        // 开始统计会话
        statisticsManager.startSession()

        if (modelPath == null) {
            // 数据收集模式：只显示悬浮窗，不启动检测
            updateNotification("数据收集模式", "准备收集DNF截图...")
            Log.i(TAG, "数据收集模式已启动")
            return
        }

        // 正常模式：初始化检测器并启动主循环
        try {
            updateNotification("AI机器人", "正在启动...")

            // 根据模型文件名自动选择检测器类型
            detector = when {
                modelPath.contains("ssd", ignoreCase = true) ||
                modelPath.contains("mobilenet", ignoreCase = true) ||
                modelPath.contains("youtube", ignoreCase = true) -> {
                    Log.i(TAG, "使用SSD检测器")
                    SSDDetector(this, modelPath)
                }
                else -> {
                    Log.i(TAG, "使用YOLO检测器")
                    YoloDetector(this, modelPath)
                }
            }
            strategy = GameStrategy()

            // 更新通知
            updateNotification("AI机器人运行中", "正在检测游戏...")

            // 启动主循环
            botJob = serviceScope.launch {
                runBotLoop()
            }

            Log.i(TAG, "机器人已启动")

        } catch (e: Exception) {
            Log.e(TAG, "启动失败", e)
            isRunning = false
            updateNotification("AI机器人", "启动失败: ${e.message}")
            removeFloatingWindow()
        }
    }

    /**
     * 停止机器人
     */
    fun stopBot() {
        if (!isRunning) return

        Log.i(TAG, "停止机器人...")

        isRunning = false

        // 结束统计会话
        statisticsManager.endSession()

        botJob?.cancel()
        botJob = null

        detector?.close()
        detector = null

        // 清理屏幕捕获资源
        cleanupScreenCapture()

        // 移除悬浮窗
        removeFloatingWindow()

        // 更新通知
        updateNotification("服务已就绪", "机器人已停止")

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
     * 使用MediaProjection API进行屏幕录制
     */
    private fun captureScreen(): Bitmap? {
        if (mediaProjection == null) {
            return null
        }

        // 确保ImageReader已初始化
        if (imageReader == null) {
            setupScreenCapture()
        }

        try {
            // 从ImageReader获取最新图像
            val image = imageReader?.acquireLatestImage()

            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                // 创建Bitmap
                val bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
                )

                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                // 裁剪掉padding部分
                if (rowPadding > 0) {
                    val croppedBitmap = Bitmap.createBitmap(
                        bitmap,
                        0, 0,
                        screenWidth, screenHeight
                    )
                    bitmap.recycle()
                    return croppedBitmap
                }

                return bitmap
            }

        } catch (e: Exception) {
            Log.e(TAG, "截图失败", e)
        }

        return null
    }

    /**
     * 设置屏幕捕获
     */
    private fun setupScreenCapture() {
        if (mediaProjection == null) {
            Log.w(TAG, "MediaProjection未初始化，无法设置屏幕捕获")
            return
        }

        try {
            // 注册MediaProjection回调（Android 14+要求）
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection已停止")
                    cleanupScreenCapture()
                }
            }, null)

            // 创建ImageReader
            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2  // 缓冲区数量
            )

            // 创建VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "GameBotScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

            Log.i(TAG, "屏幕捕获已设置")

        } catch (e: Exception) {
            Log.e(TAG, "设置屏幕捕获失败", e)
        }
    }

    /**
     * 清理屏幕捕获资源
     */
    private fun cleanupScreenCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    /**
     * 设置MediaProjection
     * 从MainActivity传递过来
     */
    fun setMediaProjection(projection: MediaProjection?) {
        mediaProjection = projection
        if (projection != null) {
            Log.i(TAG, "MediaProjection已设置")
            if (isRunning) {
                setupScreenCapture()
            }
        }
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
     * 使用滑动窗口平均计算FPS，更加平滑准确
     */
    private fun updateStats() {
        frameCount++
        val currentTime = System.currentTimeMillis()

        // 记录到统计管理器
        statisticsManager.recordDetection()

        // 添加到历史记录
        fpsHistory.add(currentTime)

        // 保持最近30帧
        while (fpsHistory.size > 30) {
            fpsHistory.removeFirst()
        }

        // 计算平均FPS（基于最近30帧）
        if (fpsHistory.size >= 2) {
            val timeSpan = fpsHistory.last() - fpsHistory.first()
            if (timeSpan > 0) {
                fps = (fpsHistory.size - 1) * 1000f / timeSpan
            }
        }

        lastFrameTime = currentTime

        // 每30帧输出一次日志并更新悬浮窗
        if (frameCount % 30 == 0) {
            val currentFps = fps.toInt()
            Log.d(TAG, "统计: 帧数=$frameCount, FPS=$currentFps")

            // 记录FPS到统计管理器
            statisticsManager.recordFPS(currentFps)

            updateFloatingWindow()
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

    // ==================== 悬浮窗管理 ====================

    /**
     * 创建并显示悬浮窗
     */
    fun showFloatingWindow() {
        if (floatingView != null) {
            Log.w(TAG, "悬浮窗已存在")
            return
        }

        try {
            // 检查悬浮窗权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    Log.e(TAG, "没有悬浮窗权限，无法显示悬浮窗")
                    updateNotification("权限不足", "需要悬浮窗权限")
                    return
                }
            }

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            // 加载悬浮窗布局
            floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null)

            // 设置窗口参数
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 100
            }

            // 添加到窗口
            windowManager?.addView(floatingView, params)

            // 缓存View引用，避免重复findViewById
            tvFps = floatingView?.findViewById(R.id.tvFps)
            tvFrames = floatingView?.findViewById(R.id.tvFrames)
            tvMiniFps = floatingView?.findViewById(R.id.tvMiniFps)

            // 设置拖动监听
            setupFloatingWindowListeners(params)

            Log.i(TAG, "悬浮窗已显示")

        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败", e)
            // 如果显示悬浮窗失败，不应该导致应用崩溃
            updateNotification("悬浮窗错误", "显示失败: ${e.message}")
        }
    }

    /**
     * 设置悬浮窗监听器
     */
    private fun setupFloatingWindowListeners(params: WindowManager.LayoutParams) {
        val view = floatingView ?: return

        // 拖动功能
        val header = view.findViewById<LinearLayout>(R.id.floatingHeader)
        header.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    lastX = params.x
                    lastY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    params.x = lastX + deltaX
                    params.y = lastY + deltaY
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        // 关闭按钮 - 真正移除浮窗
        val btnMinimize = view.findViewById<View>(R.id.btnMinimize)
        btnMinimize.setOnClickListener {
            removeFloatingWindow()
        }

        // 停止按钮
        val btnStop = view.findViewById<Button>(R.id.btnStop)
        btnStop.setOnClickListener {
            stopBot()
        }

        // 设置按钮
        val btnSettings = view.findViewById<Button>(R.id.btnSettings)
        btnSettings.setOnClickListener {
            // 打开MainActivityNew并回到前台
            val intent = Intent(this, MainActivityNew::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }
    }

    /**
     * 切换悬浮窗展开/最小化状态
     */
    private fun toggleFloatingWindow() {
        val view = floatingView ?: return

        isFloatingExpanded = !isFloatingExpanded

        val content = view.findViewById<LinearLayout>(R.id.floatingContent)
        val minimized = view.findViewById<LinearLayout>(R.id.floatingMinimized)

        if (isFloatingExpanded) {
            content.visibility = View.VISIBLE
            minimized.visibility = View.GONE
        } else {
            content.visibility = View.GONE
            minimized.visibility = View.VISIBLE
        }
    }

    /**
     * 更新悬浮窗显示内容
     * 使用Handler避免频繁创建协程
     */
    private fun updateFloatingWindow() {
        if (floatingView == null) return

        // 使用缓存的View引用和Handler，优化性能
        mainHandler.post {
            tvFps?.text = "FPS: ${fps.toInt()}"
            tvFrames?.text = "帧数: $frameCount"
            tvMiniFps?.text = "${fps.toInt()}"
        }
    }

    /**
     * 移除悬浮窗
     */
    private fun removeFloatingWindow() {
        try {
            if (floatingView != null) {
                windowManager?.removeView(floatingView)
                floatingView = null

                // 清空缓存的View引用
                tvFps = null
                tvFrames = null
                tvMiniFps = null

                Log.i(TAG, "悬浮窗已移除")
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮窗失败", e)
        }
    }

    // ========== 数据收集功能 ==========

    /**
     * 手动截图
     */
    fun captureScreenshot(): Boolean {
        return try {
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image)
                image.close()

                if (bitmap != null) {
                    val filename = datasetManager.saveScreenshot(bitmap)
                    if (filename != null) {
                        Log.i(TAG, "手动截图成功: $filename")
                        // 记录截图到统计
                        statisticsManager.recordCapture()
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "手动截图失败", e)
            false
        }
    }

    /**
     * 启动自动截图
     */
    fun startAutoCapture(intervalSeconds: Int) {
        if (autoCapture) {
            stopAutoCapture()
        }

        captureInterval = intervalSeconds * 1000L
        autoCapture = true

        captureRunnable = object : Runnable {
            override fun run() {
                if (autoCapture && isRunning) {
                    captureScreenshot()
                    captureHandler.postDelayed(this, captureInterval)
                }
            }
        }

        captureHandler.postDelayed(captureRunnable!!, captureInterval)
        Log.i(TAG, "自动截图已启动，间隔: ${intervalSeconds}秒")
    }

    /**
     * 停止自动截图
     */
    fun stopAutoCapture() {
        autoCapture = false
        captureRunnable?.let {
            captureHandler.removeCallbacks(it)
        }
        captureRunnable = null
        Log.i(TAG, "自动截图已停止")
    }

    /**
     * 获取数据集管理器
     */
    fun getDatasetManager(): DatasetManager {
        return datasetManager
    }

    /**
     * 获取统计管理器
     */
    fun getStatisticsManager(): StatisticsManager {
        return statisticsManager
    }

    /**
     * Image转Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // 如果有padding，裁剪到实际屏幕大小
            if (rowPadding > 0) {
                Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image转Bitmap失败", e)
            null
        }
    }
}
