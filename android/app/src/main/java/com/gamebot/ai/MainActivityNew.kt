package com.gamebot.ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.gamebot.ai.service.GameBotAccessibilityService
import com.gamebot.ai.ui.CloudTrainingFragment
import com.gamebot.ai.ui.DataCollectionFragment
import com.gamebot.ai.ui.HomeFragment
import com.gamebot.ai.ui.StatisticsFragment
import com.gamebot.ai.utils.DebugLogger
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 新版MainActivity - 支持多页面导航
 */
class MainActivityNew : AppCompatActivity() {

    // MediaProjection相关
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var screenCaptureResultCode = 0
    private var screenCaptureData: Intent? = null

    // Fragments
    private val homeFragment = HomeFragment.newInstance()
    private val dataFragment = DataCollectionFragment.newInstance()
    private val trainFragment = CloudTrainingFragment.newInstance()
    private val statsFragment = StatisticsFragment.newInstance()

    private var activeFragment: Fragment = homeFragment

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
                            startBotInternal()
                            DebugLogger.i("startBotInternal()调用成功")
                        } catch (e: Exception) {
                            DebugLogger.e("startBotInternal()调用失败", e)
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
        DebugLogger.i("=== MainActivityNew onCreate ===")
        DebugLogger.i("应用启动")

        setContentView(R.layout.activity_main_new)

        // 初始化MediaProjectionManager
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // 设置Toolbar
        setSupportActionBar(findViewById(R.id.toolbar))

        // 初始化Fragments
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragment_container, homeFragment, "home")
            add(R.id.fragment_container, dataFragment, "data").hide(dataFragment)
            add(R.id.fragment_container, trainFragment, "train").hide(trainFragment)
            add(R.id.fragment_container, statsFragment, "stats").hide(statsFragment)
        }.commit()

        // 设置底部导航
        setupBottomNavigation()

        DebugLogger.i("MainActivityNew初始化完成")
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchFragment(homeFragment)
                    true
                }
                R.id.nav_data -> {
                    switchFragment(dataFragment)
                    true
                }
                R.id.nav_train -> {
                    switchFragment(trainFragment)
                    true
                }
                R.id.nav_stats -> {
                    switchFragment(statsFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun switchFragment(fragment: Fragment) {
        if (fragment != activeFragment) {
            supportFragmentManager.beginTransaction().apply {
                hide(activeFragment)
                show(fragment)
            }.commit()
            activeFragment = fragment
        }
    }

    /**
     * 启动机器人 - 由HomeFragment调用
     */
    fun startBot() {
        // 一次性检查所有权限和条件
        val missingPermissions = mutableListOf<Pair<String, () -> Unit>>()

        // 1. 检查无障碍服务
        if (!isAccessibilityServiceEnabled()) {
            missingPermissions.add("无障碍服务" to { openAccessibilitySettings() })
        }

        // 2. 检查悬浮窗权限
        if (!checkOverlayPermission()) {
            missingPermissions.add("悬浮窗权限" to { requestOverlayPermission() })
        }

        // 3. 检查电池优化
        if (!isIgnoringBatteryOptimizations()) {
            missingPermissions.add("电池优化豁免" to { requestIgnoreBatteryOptimizations() })
        }

        // 4. 检查屏幕录制权限
        if (screenCaptureData == null) {
            missingPermissions.add("屏幕录制权限" to { requestScreenCapturePermission() })
        }

        // 如果有缺失的权限，显示对话框
        if (missingPermissions.isNotEmpty()) {
            showMissingPermissionsDialog(missingPermissions)
            return
        }

        // 5. 检查服务实例（最后检查，因为需要无障碍服务先就绪）
        checkServiceAndStart()
    }

    /**
     * 检查服务实例并启动（带重试机制）
     */
    private fun checkServiceAndStart(retryCount: Int = 0) {
        val service = GameBotAccessibilityService.instance

        if (service != null) {
            // 服务实例就绪，启动机器人
            startBotInternal()
            return
        }

        // 服务实例为null，检查是否在设置中启用
        if (isAccessibilityServiceEnabled()) {
            // 已启用但实例未连接，可能需要等待
            if (retryCount < 3) {
                // 重试次数未超限，等待后重试
                DebugLogger.i("服务已启用但实例未连接，${retryCount + 1}/3次重试...")
                Toast.makeText(this, "服务连接中，请稍候...", Toast.LENGTH_SHORT).show()

                window.decorView.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        checkServiceAndStart(retryCount + 1)
                    }
                }, 1500)
            } else {
                // 重试次数已用完
                DebugLogger.w("服务已启用但实例连接失败，已重试3次")
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("服务连接超时")
                    .setMessage("无障碍服务已启用，但连接超时。\\n\\n可能的原因：\\n1. 服务刚启用，需要更多时间\\n2. 系统资源紧张\\n\\n建议操作：\\n1. 等待10秒后重试\\n2. 或关闭后重新开启无障碍服务")
                    .setPositiveButton("重新尝试") { _, _ ->
                        checkServiceAndStart(0)
                    }
                    .setNegativeButton("打开设置") { _, _ ->
                        openAccessibilitySettings()
                    }
                    .show()
            }
        } else {
            // 服务未启用
            Toast.makeText(this, "请先启用无障碍服务", Toast.LENGTH_SHORT).show()
            openAccessibilitySettings()
        }
    }

    /**
     * 显示缺失权限的对话框
     */
    private fun showMissingPermissionsDialog(missingPermissions: List<Pair<String, () -> Unit>>) {
        val message = buildString {
            append("需要以下权限才能启动机器人:\n\n")
            missingPermissions.forEachIndexed { index, (name, _) ->
                append("${index + 1}. $name\n")
            }
            append("\n点击\"开始授权\"逐步完成设置")
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("权限检查")
            .setMessage(message)
            .setPositiveButton("开始授权") { _, _ ->
                // 授予第一个缺失的权限
                if (missingPermissions.isNotEmpty()) {
                    missingPermissions[0].second.invoke()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startBotInternal() {
        val service = GameBotAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "服务未就绪", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 优先级顺序：
            // 1. filesDir中的云端训练模型 (绝对路径)
            // 2. assets中的预打包模型 (相对路径)

            var modelPath: String? = null
            var hasModel = false

            // 1. 检查filesDir中的云端训练模型
            val cloudModelFile = java.io.File(filesDir, "models/dnf_detection_model.tflite")
            if (cloudModelFile.exists() && cloudModelFile.length() > 1000) {
                modelPath = cloudModelFile.absolutePath  // 使用绝对路径
                hasModel = true
                android.util.Log.i("MainActivityNew", "✅ 使用云端训练的模型: $modelPath (${cloudModelFile.length() / 1024}KB)")
                Toast.makeText(this, "使用云端训练的模型", Toast.LENGTH_SHORT).show()
            }

            // 2. 如果没有云端模型，检查assets中的预打包模型
            if (!hasModel) {
                val assetModels = listOf(
                    "youtube_detector.tflite",
                    "dnf_detection_model.tflite",
                    "mobilenet_ssd_base.tflite"
                )

                for (model in assetModels) {
                    try {
                        assets.open(model).use { inputStream ->
                            val size = inputStream.available()
                            if (size > 1000) {
                                modelPath = model  // 使用相对路径（assets路径）
                                hasModel = true
                                android.util.Log.i("MainActivityNew", "✅ 使用预打包模型: $modelPath (${size / 1024}KB)")
                                break
                            }
                        }
                    } catch (e: java.io.FileNotFoundException) {
                        continue
                    }
                }
            }

            // 3. 启动服务
            if (!hasModel || modelPath == null) {
                Toast.makeText(this, "⚠️ 缺少AI模型\n\n将启动数据收集模式", Toast.LENGTH_LONG).show()
                service.startBot(null)
            } else {
                service.startBot(modelPath)
                Toast.makeText(this, "✅ 机器人已启动！", Toast.LENGTH_SHORT).show()
            }

            Toast.makeText(this, "✅ 启动成功！悬浮窗已显示\n应用将最小化到后台", Toast.LENGTH_LONG).show()

            // 延迟1秒后将应用移到后台，让用户看到提示信息
            Handler(Looper.getMainLooper()).postDelayed({
                moveTaskToBack(true)
            }, 1000)

        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("MainActivityNew", "启动失败", e)
        }
    }

    /**
     * 停止机器人 - 由HomeFragment调用
     */
    fun stopBot() {
        val service = GameBotAccessibilityService.instance
        service?.stopBot()
        Toast.makeText(this, "机器人已停止", Toast.LENGTH_SHORT).show()
    }

    /**
     * 打开无障碍设置 - 由HomeFragment调用
     */
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    /**
     * 检查是否有屏幕录制权限 - 由HomeFragment调用
     */
    fun hasScreenCapturePermission(): Boolean {
        return screenCaptureData != null
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
     * 请求屏幕录制权限 - 直接请求整个屏幕
     */
    private fun requestScreenCapturePermission() {
        mediaProjectionManager?.let { manager ->
            val intent = if (Build.VERSION.SDK_INT >= 34) {
                // Android 14+ (API 34+): 使用MediaProjectionConfig直接请求整个屏幕
                try {
                    val config = android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
                    manager.createScreenCaptureIntent(config)
                } catch (e: Exception) {
                    DebugLogger.w("无法使用MediaProjectionConfig，回退到默认方式: ${e.message}")
                    manager.createScreenCaptureIntent()
                }
            } else {
                // Android 13及以下：使用默认方式
                // 注意：在某些设备上可能仍会显示应用选择，但大多数会直接请求整个屏幕
                manager.createScreenCaptureIntent()
            }
            DebugLogger.i("发起屏幕录制权限请求")
            screenCaptureLauncher.launch(intent)
        } ?: run {
            Toast.makeText(this, "无法获取屏幕录制服务", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 检查无障碍服务是否开启
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/${GameBotAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val isEnabledInSettings = enabledServices?.contains(serviceName) == true

        // 同时检查服务实例是否已连接
        val isServiceConnected = GameBotAccessibilityService.instance != null

        // 如果在设置中启用但实例未连接，给一些时间让服务连接
        if (isEnabledInSettings && !isServiceConnected) {
            DebugLogger.w("无障碍服务已在设置中启用，但实例未连接。可能需要等待...")
        }

        return isEnabledInSettings
    }
}
