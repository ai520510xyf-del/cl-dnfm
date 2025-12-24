package com.gamebot.ai.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MobileNet SSD目标检测器
 *
 * 支持标准MobileNet SSD模型格式:
 * - 输入: [1, 300, 300, 3] RGB图像
 * - 输出:
 *   output[0]: detection_boxes [1, num_detections, 4]
 *   output[1]: detection_classes [1, num_detections]
 *   output[2]: detection_scores [1, num_detections]
 *   output[3]: num_detections [1]
 */
class SSDDetector(
    private val context: Context,
    modelPath: String
) : Detector {
    companion object {
        private const val TAG = "SSDDetector"

        // 模型参数
        private const val INPUT_SIZE = 300  // MobileNet SSD使用300x300
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private const val MAX_DETECTIONS = 10
    }

    // TensorFlow Lite解释器
    private var interpreter: Interpreter? = null

    // 类别标签
    private val labels = listOf(
        "search_box",       // 0: 搜索框
        "home_tab",         // 1: 首页标签
        "shorts_tab",       // 2: Shorts标签
        "subscriptions_tab", // 3: 订阅标签
        "library_tab",      // 4: 资料库标签
        "video_thumbnail",  // 5: 视频缩略图
        "play_button",      // 6: 播放按钮
        "like_button",      // 7: 点赞按钮
        "share_button",     // 8: 分享按钮
        "comment_button"    // 9: 评论按钮
    )

    // 图像预处理器
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    init {
        loadModel(modelPath)
    }

    /**
     * 加载TFLite模型
     *
     * 支持两种路径格式:
     * 1. 绝对路径 (以 "/" 开头): 从文件系统加载 (用于云端训练的模型)
     * 2. 相对路径: 从 assets 加载 (用于预打包的模型)
     */
    private fun loadModel(modelPath: String) {
        try {
            Log.i(TAG, "加载SSD模型: $modelPath")

            // 根据路径类型选择加载方式
            val modelBuffer = when {
                // 如果是绝对路径，从文件系统加载
                modelPath.startsWith("/") -> {
                    val file = java.io.File(modelPath)

                    // 检查文件是否存在
                    if (!file.exists()) {
                        throw RuntimeException("模型文件不存在: $modelPath")
                    }

                    val fileSize = file.length()
                    Log.i(TAG, "从文件系统加载SSD模型: ${fileSize / 1024}KB")

                    if (fileSize < 1000) {
                        throw RuntimeException("模型文件过小 (${fileSize}字节)，可能是占位文件。")
                    }

                    // 使用FileInputStream映射为ByteBuffer
                    java.io.FileInputStream(file).channel.map(
                        java.nio.channels.FileChannel.MapMode.READ_ONLY,
                        0,
                        file.length()
                    )
                }
                // 否则从assets加载
                else -> {
                    // 检查文件是否存在
                    val assetManager = context.assets
                    try {
                        assetManager.open(modelPath).use { inputStream ->
                            val fileSize = inputStream.available()
                            Log.i(TAG, "从assets加载SSD模型: ${fileSize / 1024}KB")

                            if (fileSize < 1000) {
                                throw RuntimeException("模型文件过小，可能是占位文件。")
                            }
                        }
                    } catch (e: Exception) {
                        throw RuntimeException("无法找到模型文件: $modelPath", e)
                    }

                    // 从assets加载模型
                    FileUtil.loadMappedFile(context, modelPath)
                }
            }

            // 配置解释器选项
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)
            }

            interpreter = Interpreter(modelBuffer, options)

            // 输出模型信息
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val numOutputs = interpreter?.outputTensorCount ?: 0

            Log.i(TAG, "✅ SSD模型加载成功")
            Log.i(TAG, "输入形状: ${inputShape?.contentToString()}")
            Log.i(TAG, "输出数量: $numOutputs")

            for (i in 0 until numOutputs) {
                val outputShape = interpreter?.getOutputTensor(i)?.shape()
                Log.i(TAG, "输出[$i]形状: ${outputShape?.contentToString()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 模型加载失败", e)
            throw RuntimeException("无法加载SSD模型: $modelPath\n错误: ${e.message}", e)
        }
    }

    /**
     * 执行检测
     *
     * @param bitmap 输入图像
     * @return 检测结果列表
     */
    override fun detect(bitmap: Bitmap): List<Detection> {
        val startTime = System.currentTimeMillis()

        try {
            // 1. 图像预处理
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val processedImage = imageProcessor.process(tensorImage)

            // 2. 准备输入
            val inputBuffer = processedImage.buffer

            // 3. 准备输出缓冲区
            // MobileNet SSD 有 4 个输出
            val outputLocations = Array(1) { Array(MAX_DETECTIONS) { FloatArray(4) } }  // [1, 10, 4]
            val outputClasses = Array(1) { FloatArray(MAX_DETECTIONS) }                 // [1, 10]
            val outputScores = Array(1) { FloatArray(MAX_DETECTIONS) }                  // [1, 10]
            val numDetections = FloatArray(1)                                            // [1]

            val outputs = mutableMapOf<Int, Any>()
            outputs[0] = outputLocations
            outputs[1] = outputClasses
            outputs[2] = outputScores
            outputs[3] = numDetections

            // 4. 执行推理
            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

            // 5. 解析输出
            val detections = parseOutput(
                outputLocations[0],
                outputClasses[0],
                outputScores[0],
                numDetections[0].toInt(),
                bitmap.width,
                bitmap.height
            )

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "检测完成: 用时${elapsed}ms, 发现${detections.size}个目标")

            return detections

        } catch (e: Exception) {
            Log.e(TAG, "检测失败", e)
            return emptyList()
        }
    }

    /**
     * 解析MobileNet SSD模型输出
     *
     * @param locations 边界框坐标 [num_detections, 4] - 格式: [ymin, xmin, ymax, xmax]
     * @param classes 类别ID [num_detections]
     * @param scores 置信度分数 [num_detections]
     * @param numDetections 检测到的对象数量
     * @param imageWidth 原始图像宽度
     * @param imageHeight 原始图像高度
     */
    private fun parseOutput(
        locations: Array<FloatArray>,
        classes: FloatArray,
        scores: FloatArray,
        numDetections: Int,
        imageWidth: Int,
        imageHeight: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()

        val actualDetections = minOf(numDetections, MAX_DETECTIONS)

        for (i in 0 until actualDetections) {
            val score = scores[i]

            // 检查置信度阈值
            if (score < CONFIDENCE_THRESHOLD) {
                continue
            }

            // MobileNet SSD 输出归一化坐标 [ymin, xmin, ymax, xmax]
            val ymin = locations[i][0] * imageHeight
            val xmin = locations[i][1] * imageWidth
            val ymax = locations[i][2] * imageHeight
            val xmax = locations[i][3] * imageWidth

            val bbox = RectF(xmin, ymin, xmax, ymax)

            // 类别ID（注意：MobileNet SSD的类别ID从1开始，0是背景）
            val classId = (classes[i].toInt() - 1).coerceIn(0, labels.size - 1)
            val className = if (classId in labels.indices) {
                labels[classId]
            } else {
                "unknown_$classId"
            }

            detections.add(Detection(className, score, bbox))

            Log.d(TAG, "检测到: $className (${(score * 100).toInt()}%) at [${xmin.toInt()}, ${ymin.toInt()}, ${xmax.toInt()}, ${ymax.toInt()}]")
        }

        return detections
    }

    /**
     * 关闭检测器，释放资源
     */
    override fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Log.i(TAG, "SSD检测器已关闭")
        } catch (e: Exception) {
            Log.e(TAG, "关闭检测器失败", e)
        }
    }
}
