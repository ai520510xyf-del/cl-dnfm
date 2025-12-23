package com.gamebot.ai.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.random.Random

/**
 * 游戏控制器 - 模拟点击和滑动操作
 */
@RequiresApi(Build.VERSION_CODES.N)
class GameController(private val service: AccessibilityService) {

    private var screenWidth = 0
    private var screenHeight = 0

    /**
     * 设置屏幕尺寸
     */
    fun setScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    /**
     * 点击屏幕
     *
     * @param x X坐标
     * @param y Y坐标
     * @param duration 点击持续时间(毫秒)
     */
    fun tap(x: Float, y: Float, duration: Long = 50) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        service.dispatchGesture(gesture, null, null)
    }

    /**
     * 随机偏移点击 - 模拟人类操作
     *
     * @param x X坐标
     * @param y Y坐标
     * @param radius 随机半径
     * @param duration 点击持续时间
     */
    fun tapRandom(x: Float, y: Float, radius: Int = 10, duration: Long = 50) {
        val offsetX = Random.nextInt(-radius, radius + 1)
        val offsetY = Random.nextInt(-radius, radius + 1)
        tap(x + offsetX, y + offsetY, duration)
    }

    /**
     * 滑动操作
     *
     * @param startX 起始X坐标
     * @param startY 起始Y坐标
     * @param endX 结束X坐标
     * @param endY 结束Y坐标
     * @param duration 滑动持续时间(毫秒)
     */
    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 500
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        service.dispatchGesture(gesture, null, null)
    }

    /**
     * 贝塞尔曲线滑动 - 更自然的滑动效果
     */
    fun swipeSmooth(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 500
    ) {
        // 生成随机控制点
        val controlX = (startX + endX) / 2 + Random.nextInt(-50, 51)
        val controlY = (startY + endY) / 2 + Random.nextInt(-50, 51)

        val path = Path().apply {
            moveTo(startX, startY)
            quadTo(controlX, controlY, endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        service.dispatchGesture(gesture, null, null)
    }

    /**
     * 长按操作
     */
    fun longPress(x: Float, y: Float, duration: Long = 1000) {
        tap(x, y, duration)
    }

    /**
     * 多点连续点击
     */
    fun multiTap(positions: List<Pair<Float, Float>>, interval: Long = 100) {
        var delay = 0L
        for ((x, y) in positions) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                tap(x, y)
            }, delay)
            delay += interval
        }
    }

    /**
     * 方向滑动
     *
     * @param direction 方向: "up", "down", "left", "right"
     * @param distance 滑动距离(像素)
     */
    fun swipeDirection(direction: String, distance: Int = 300, duration: Long = 300) {
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f

        when (direction.lowercase()) {
            "up" -> swipe(centerX, centerY, centerX, centerY - distance, duration)
            "down" -> swipe(centerX, centerY, centerX, centerY + distance, duration)
            "left" -> swipe(centerX, centerY, centerX - distance, centerY, duration)
            "right" -> swipe(centerX, centerY, centerX + distance, centerY, duration)
        }
    }

    /**
     * 相对坐标点击
     *
     * @param rx 相对X坐标 (0.0-1.0)
     * @param ry 相对Y坐标 (0.0-1.0)
     */
    fun tapRelative(rx: Float, ry: Float, duration: Long = 50) {
        val x = rx * screenWidth
        val y = ry * screenHeight
        tap(x, y, duration)
    }
}

/**
 * 操作动作枚举
 */
sealed class GameAction {
    data class Tap(val x: Float, val y: Float) : GameAction()
    data class Swipe(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float
    ) : GameAction()
    data class LongPress(val x: Float, val y: Float) : GameAction()
    object Wait : GameAction()
}
