package com.gamebot.ai.detector

import android.graphics.Bitmap

/**
 * 目标检测器接口
 * 支持多种检测器实现（YOLO、SSD等）
 */
interface Detector {
    /**
     * 执行检测
     * @param bitmap 输入图像
     * @return 检测结果列表
     */
    fun detect(bitmap: Bitmap): List<Detection>

    /**
     * 关闭检测器，释放资源
     */
    fun close()
}
