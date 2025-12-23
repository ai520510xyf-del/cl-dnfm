package com.gamebot.ai.strategy

import android.graphics.Bitmap
import com.gamebot.ai.controller.GameAction
import com.gamebot.ai.detector.Detection

/**
 * 游戏策略 - AI决策逻辑
 */
class GameStrategy {

    private var lastActionTime = System.currentTimeMillis()
    private val actionCooldown = 500L // 操作冷却时间(毫秒)

    /**
     * 做出决策
     *
     * @param screenshot 当前屏幕截图
     * @param detections YOLO检测结果
     * @return 游戏操作
     */
    fun makeDecision(screenshot: Bitmap?, detections: List<Detection>): GameAction {
        // 检查冷却时间
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActionTime < actionCooldown) {
            return GameAction.Wait
        }

        // 根据检测结果做决策
        val action = when {
            // 优先级1: 查找敌人并攻击
            hasDetection(detections, "enemy") -> {
                val enemy = getDetectionsByClass(detections, "enemy").first()
                GameAction.Tap(enemy.centerX, enemy.centerY)
            }

            // 优先级2: 使用技能
            hasDetection(detections, "skill_button") -> {
                val skill = getDetectionsByClass(detections, "skill_button").first()
                GameAction.Tap(skill.centerX, skill.centerY)
            }

            // 优先级3: 点击开始按钮
            hasDetection(detections, "start_button") -> {
                val button = getDetectionsByClass(detections, "start_button").first()
                GameAction.Tap(button.centerX, button.centerY)
            }

            // 优先级4: 领取奖励
            hasDetection(detections, "claim_button") -> {
                val button = getDetectionsByClass(detections, "claim_button").first()
                GameAction.Tap(button.centerX, button.centerY)
            }

            // 默认: 等待
            else -> GameAction.Wait
        }

        // 记录操作时间
        if (action !is GameAction.Wait) {
            lastActionTime = currentTime
        }

        return action
    }

    /**
     * 检查是否存在某类检测目标
     */
    private fun hasDetection(detections: List<Detection>, className: String): Boolean {
        return detections.any { it.className == className }
    }

    /**
     * 获取指定类别的检测结果
     */
    private fun getDetectionsByClass(detections: List<Detection>, className: String): List<Detection> {
        return detections.filter { it.className == className }
    }
}
