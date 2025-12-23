package com.gamebot.ai.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * YOLO检测器 - 使用TensorFlow Lite
 */
class YoloDetector(
    private val context: Context,
    private val modelPath: String = "game_model_320.tflite",
    private val confThreshold: Float = 0.25f,
    private val iouThreshold: Float = 0.45f
) {
    private var interpreter: Interpreter? = null
    private var inputSize: Int = 320
    private var classNames: List<String> = listOf()

    // 输入输出缓冲区
    private lateinit var inputBuffer: ByteBuffer
    private lateinit var outputBuffer: Array<FloatArray>

    init {
        loadModel()
    }

    /**
     * 加载TFLite模型
     */
    private fun loadModel() {
        try {
            // 加载模型文件
            val modelBuffer = FileUtil.loadMappedFile(context, modelPath)

            // 配置解释器选项
            val options = Interpreter.Options().apply {
                // 使用GPU加速 (如果支持)
                // addDelegate(GpuDelegate())

                // 使用NNAPI (如果支持)
                // setUseNNAPI(true)

                // 设置线程数
                setNumThreads(4)
            }

            // 创建解释器
            interpreter = Interpreter(modelBuffer, options)

            // 获取输入输出信息
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            inputSize = inputShape?.get(1) ?: 320

            // 初始化缓冲区
            initBuffers()

            android.util.Log.i("YoloDetector", "模型加载成功: $modelPath")
            android.util.Log.i("YoloDetector", "输入尺寸: $inputSize x $inputSize")

        } catch (e: Exception) {
            android.util.Log.e("YoloDetector", "模型加载失败", e)
            throw e
        }
    }

    /**
     * 初始化输入输出缓冲区
     */
    private fun initBuffers() {
        // 输入: [1, 320, 320, 3] - NHWC格式
        val inputSize = 1 * inputSize * inputSize * 3 * 4 // float32
        inputBuffer = ByteBuffer.allocateDirect(inputSize).apply {
            order(ByteOrder.nativeOrder())
        }

        // 输出: [1, 2100, 85] - (x, y, w, h, conf, ...classes)
        outputBuffer = Array(1) { FloatArray(2100 * 85) }
    }

    /**
     * 检测图像中的目标
     *
     * @param bitmap 输入图像
     * @return 检测结果列表
     */
    fun detect(bitmap: Bitmap): List<Detection> {
        if (interpreter == null) {
            android.util.Log.w("YoloDetector", "模型未加载")
            return emptyList()
        }

        val startTime = System.currentTimeMillis()

        // 1. 预处理图像
        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            inputSize,
            inputSize,
            true
        )
        preprocessImage(resizedBitmap)

        // 2. 运行推理
        interpreter?.run(inputBuffer, outputBuffer)

        // 3. 后处理
        val detections = postprocess(
            outputBuffer[0],
            bitmap.width,
            bitmap.height
        )

        val elapsed = System.currentTimeMillis() - startTime
        android.util.Log.d("YoloDetector", "检测完成: ${detections.size}个目标, 耗时: ${elapsed}ms")

        return detections
    }

    /**
     * 图像预处理
     */
    private fun preprocessImage(bitmap: Bitmap) {
        inputBuffer.rewind()

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            // 归一化到 [0, 1]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }
    }

    /**
     * 后处理 - NMS
     */
    private fun postprocess(
        output: FloatArray,
        originalWidth: Int,
        originalHeight: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()

        // 解析输出
        val numBoxes = 2100
        val numValues = 85

        for (i in 0 until numBoxes) {
            val offset = i * numValues

            // 获取置信度
            val conf = output[offset + 4]

            if (conf < confThreshold) continue

            // 获取类别分数
            var maxClassScore = 0f
            var maxClassId = 0

            for (j in 0 until 80) { // 80个类别
                val classScore = output[offset + 5 + j]
                if (classScore > maxClassScore) {
                    maxClassScore = classScore
                    maxClassId = j
                }
            }

            val finalConf = conf * maxClassScore

            if (finalConf < confThreshold) continue

            // 获取边界框
            val cx = output[offset]
            val cy = output[offset + 1]
            val w = output[offset + 2]
            val h = output[offset + 3]

            // 转换为原始尺寸
            val scaleX = originalWidth.toFloat() / inputSize
            val scaleY = originalHeight.toFloat() / inputSize

            val x1 = ((cx - w / 2) * scaleX).coerceIn(0f, originalWidth.toFloat())
            val y1 = ((cy - h / 2) * scaleY).coerceIn(0f, originalHeight.toFloat())
            val x2 = ((cx + w / 2) * scaleX).coerceIn(0f, originalWidth.toFloat())
            val y2 = ((cy + h / 2) * scaleY).coerceIn(0f, originalHeight.toFloat())

            detections.add(
                Detection(
                    classId = maxClassId,
                    className = getClassName(maxClassId),
                    confidence = finalConf,
                    bbox = RectF(x1, y1, x2, y2)
                )
            )
        }

        // NMS - 非极大值抑制
        return nms(detections, iouThreshold)
    }

    /**
     * 非极大值抑制
     */
    private fun nms(detections: List<Detection>, threshold: Float): List<Detection> {
        val sortedDetections = detections.sortedByDescending { it.confidence }
        val selected = mutableListOf<Detection>()

        for (detection in sortedDetections) {
            var shouldSelect = true

            for (selectedDet in selected) {
                if (detection.classId == selectedDet.classId) {
                    val iou = calculateIoU(detection.bbox, selectedDet.bbox)
                    if (iou > threshold) {
                        shouldSelect = false
                        break
                    }
                }
            }

            if (shouldSelect) {
                selected.add(detection)
            }
        }

        return selected
    }

    /**
     * 计算IoU
     */
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectLeft = max(box1.left, box2.left)
        val intersectTop = max(box1.top, box2.top)
        val intersectRight = min(box1.right, box2.right)
        val intersectBottom = min(box1.bottom, box2.bottom)

        if (intersectRight < intersectLeft || intersectBottom < intersectTop) {
            return 0f
        }

        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)

        return intersectArea / (box1Area + box2Area - intersectArea)
    }

    /**
     * 获取类别名称
     */
    private fun getClassName(classId: Int): String {
        // TODO: 从配置文件加载类别名称
        val defaultNames = listOf(
            "enemy", "skill_button", "start_button", "claim_button",
            "hp_bar", "menu_bg", "loading_icon", "reward_icon"
        )

        return if (classId < defaultNames.size) {
            defaultNames[classId]
        } else {
            "class_$classId"
        }
    }

    /**
     * 释放资源
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

/**
 * 检测结果数据类
 */
data class Detection(
    val classId: Int,
    val className: String,
    val confidence: Float,
    val bbox: RectF
) {
    // 中心点
    val centerX: Float get() = (bbox.left + bbox.right) / 2
    val centerY: Float get() = (bbox.top + bbox.bottom) / 2
}
