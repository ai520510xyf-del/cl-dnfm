package com.gamebot.ai.detector

import android.graphics.RectF

/**
 * YOLO检测结果数据类
 *
 * @property className 类别名称 (如: "enemy", "skill_button", "start_button")
 * @property confidence 置信度 (0.0-1.0)
 * @property bbox 边界框坐标 (left, top, right, bottom)
 */
data class Detection(
    val className: String,
    val confidence: Float,
    val bbox: RectF
) {
    /**
     * 获取中心点X坐标
     */
    val centerX: Float
        get() = (bbox.left + bbox.right) / 2

    /**
     * 获取中心点Y坐标
     */
    val centerY: Float
        get() = (bbox.top + bbox.bottom) / 2

    /**
     * 获取宽度
     */
    val width: Float
        get() = bbox.width()

    /**
     * 获取高度
     */
    val height: Float
        get() = bbox.height()

    /**
     * 获取面积
     */
    val area: Float
        get() = width * height

    override fun toString(): String {
        return "Detection(class='$className', conf=${"%.2f".format(confidence)}, " +
                "center=(${centerX.toInt()}, ${centerY.toInt()}), " +
                "size=${width.toInt()}x${height.toInt()})"
    }
}
