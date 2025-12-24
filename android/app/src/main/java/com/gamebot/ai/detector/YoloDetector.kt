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
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * YOLO目标检测器
 *
 * 支持标准YOLO模型格式:
 * - 输入: [1, 320, 320, 3] RGB图像
 * - 输出: [1, 2100, 85] 或 [1, num_detections, 85]
 *   格式: [x, y, w, h, confidence, class1_prob, class2_prob, ...]
 */
class YoloDetector(
    private val context: Context,
    modelPath: String
) : Detector {
    companion object {
        private const val TAG = "YoloDetector"

        // 模型参数
        private const val INPUT_SIZE = 300  // MobileNet SSD使用300x300
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private const val IOU_THRESHOLD = 0.45f
        private const val MAX_DETECTIONS = 100
    }

    // TensorFlow Lite解释器
    private var interpreter: Interpreter? = null

    // 输出缓冲区，复用以减少内存分配
    private var outputBuffer: ByteBuffer? = null

    // 类别标签
    private val labels = listOf(
        "enemy",           // 0: 敌人
        "skill_button",    // 1: 技能按钮
        "start_button",    // 2: 开始按钮
        "claim_button",    // 3: 领取按钮
        "close_button",    // 4: 关闭按钮
        "item",            // 5: 道具
        "character",       // 6: 角色
        "obstacle"         // 7: 障碍物
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
            Log.i(TAG, "加载模型: $modelPath")

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
                    Log.i(TAG, "从文件系统加载模型: ${fileSize / 1024}KB")

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
                            Log.i(TAG, "从assets加载模型: ${fileSize / 1024}KB")

                            if (fileSize < 1000) {
                                throw RuntimeException("模型文件过小，可能是占位文件。请放置真实的训练模型。")
                            }
                        }
                    } catch (e: Exception) {
                        throw RuntimeException("无法找到模型文件: $modelPath\n请将训练好的模型放入 assets/ 目录", e)
                    }

                    // 从assets加载模型
                    FileUtil.loadMappedFile(context, modelPath)
                }
            }

            // 配置解释器选项
            val options = Interpreter.Options().apply {
                setNumThreads(4)  // 使用4个线程
                setUseNNAPI(false) // 可选: 使用Android NNAPI加速
            }

            interpreter = Interpreter(modelBuffer, options)

            // 输出模型信息
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            Log.i(TAG, "✅ 模型加载成功")
            Log.i(TAG, "输入形状: ${inputShape?.contentToString()}")
            Log.i(TAG, "输出形状: ${outputShape?.contentToString()}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 模型加载失败", e)
            throw RuntimeException("""
                无法加载模型: $modelPath

                错误: ${e.message}

                解决方法:
                1. 训练YOLO模型并转换为TFLite格式
                2. 将模型文件命名为 game_model_320.tflite
                3. 放入 app/src/main/assets/ 目录
                4. 参考 assets/README.md 了解详情
            """.trimIndent(), e)
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

            // 3. 准备输出（复用缓冲区，减少内存分配）
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
                ?: throw RuntimeException("无法获取输出形状")

            val bufferSize = outputShape[0] * outputShape[1] * outputShape[2] * 4
            if (outputBuffer == null || outputBuffer!!.capacity() != bufferSize) {
                // 首次或形状变化时才重新分配
                outputBuffer = ByteBuffer.allocateDirect(bufferSize).apply {
                    order(ByteOrder.nativeOrder())
                }
                Log.d(TAG, "分配输出缓冲区: ${bufferSize / 1024}KB")
            }

            // 重置位置
            outputBuffer!!.rewind()

            // 4. 执行推理
            interpreter?.run(inputBuffer, outputBuffer)

            // 5. 解析输出
            outputBuffer!!.rewind()
            val detections = parseOutput(
                outputBuffer!!,
                outputShape,
                bitmap.width,
                bitmap.height
            )

            // 6. NMS非极大值抑制
            val finalDetections = applyNMS(detections)

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "检测完成: 用时${elapsed}ms, 发现${finalDetections.size}个目标")

            return finalDetections

        } catch (e: Exception) {
            Log.e(TAG, "检测失败", e)
            return emptyList()
        }
    }

    /**
     * 解析模型输出
     *
     * YOLO输出格式: [batch, num_boxes, num_classes + 5]
     * 每个box: [x, y, w, h, objectness, class1, class2, ...]
     */
    private fun parseOutput(
        outputBuffer: ByteBuffer,
        outputShape: IntArray,
        imageWidth: Int,
        imageHeight: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()

        val numBoxes = outputShape[1]  // 例如: 2100
        val numValues = outputShape[2]  // 例如: 85 (4 + 1 + 80)
        val numClasses = minOf(numValues - 5, labels.size)

        // 计算缩放比例
        val scaleX = imageWidth.toFloat() / INPUT_SIZE
        val scaleY = imageHeight.toFloat() / INPUT_SIZE

        for (i in 0 until numBoxes) {
            val baseIndex = i * numValues

            // 读取边界框坐标 (中心点格式)
            outputBuffer.position(baseIndex * 4)
            val cx = outputBuffer.float * scaleX
            val cy = outputBuffer.float * scaleY
            val w = outputBuffer.float * scaleX
            val h = outputBuffer.float * scaleY
            val objectness = outputBuffer.float

            // 检查置信度阈值
            if (objectness < CONFIDENCE_THRESHOLD) {
                continue
            }

            // 找到最大类别概率
            var maxClassProb = 0f
            var maxClassIndex = 0

            for (c in 0 until numClasses) {
                val classProb = outputBuffer.float
                if (classProb > maxClassProb) {
                    maxClassProb = classProb
                    maxClassIndex = c
                }
            }

            // 计算最终置信度
            val confidence = objectness * maxClassProb

            if (confidence < CONFIDENCE_THRESHOLD) {
                continue
            }

            // 转换为边界框格式 (left, top, right, bottom)
            val left = cx - w / 2
            val top = cy - h / 2
            val right = cx + w / 2
            val bottom = cy + h / 2

            val bbox = RectF(left, top, right, bottom)
            val className = if (maxClassIndex < labels.size) {
                labels[maxClassIndex]
            } else {
                "unknown_$maxClassIndex"
            }

            detections.add(Detection(className, confidence, bbox))
        }

        return detections
    }

    /**
     * 非极大值抑制 (NMS)
     * 去除重叠的检测框
     */
    private fun applyNMS(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        // 按置信度排序
        val sortedDetections = detections.sortedByDescending { it.confidence }

        val finalDetections = mutableListOf<Detection>()

        for (detection in sortedDetections) {
            if (finalDetections.size >= MAX_DETECTIONS) break

            // 检查是否与已选择的框重叠过多
            var shouldAdd = true
            for (finalDetection in finalDetections) {
                if (detection.className == finalDetection.className) {
                    val iou = calculateIOU(detection.bbox, finalDetection.bbox)
                    if (iou > IOU_THRESHOLD) {
                        shouldAdd = false
                        break
                    }
                }
            }

            if (shouldAdd) {
                finalDetections.add(detection)
            }
        }

        return finalDetections
    }

    /**
     * 计算两个边界框的IoU (Intersection over Union)
     */
    private fun calculateIOU(box1: RectF, box2: RectF): Float {
        val intersectLeft = maxOf(box1.left, box2.left)
        val intersectTop = maxOf(box1.top, box2.top)
        val intersectRight = minOf(box1.right, box2.right)
        val intersectBottom = minOf(box1.bottom, box2.bottom)

        if (intersectRight < intersectLeft || intersectBottom < intersectTop) {
            return 0f
        }

        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectArea

        return if (unionArea > 0) intersectArea / unionArea else 0f
    }

    /**
     * 关闭检测器，释放资源
     */
    override fun close() {
        try {
            interpreter?.close()
            interpreter = null
            outputBuffer = null
            Log.i(TAG, "检测器已关闭")
        } catch (e: Exception) {
            Log.e(TAG, "关闭检测器失败", e)
        }
    }
}
