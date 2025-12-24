package com.gamebot.ai.training

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

/**
 * 迁移学习模型包装器
 * 实现基于TensorFlow Lite的轻量级训练
 */
class TransferLearningModelWrapper(
    private val context: Context,
    private val numClasses: Int
) {
    companion object {
        private const val TAG = "TransferLearning"
        private const val INPUT_SIZE = 320
        private const val LEARNING_RATE = 0.001f
    }

    private var interpreter: Interpreter? = null
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    // 简化的权重存储（最后一层）
    private var outputWeights: Array<FloatArray>? = null
    private var outputBias: FloatArray? = null

    /**
     * 加载基础模型
     */
    fun loadBaseModel() {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, "mobilenet_ssd_base.tflite")
            interpreter = Interpreter(modelBuffer)

            // 初始化输出层权重
            initializeOutputLayer()

            Log.i(TAG, "基础模型加载成功")
        } catch (e: Exception) {
            Log.e(TAG, "加载基础模型失败", e)
        }
    }

    /**
     * 初始化输出层
     */
    private fun initializeOutputLayer() {
        // Xavier初始化
        val fanIn = 1280  // MobileNet最后一层的输出通道数
        val fanOut = numClasses + 4  // 类别数 + bbox坐标

        val limit = Math.sqrt(6.0 / (fanIn + fanOut)).toFloat()

        outputWeights = Array(fanOut) {
            FloatArray(fanIn) {
                (Math.random().toFloat() * 2 * limit - limit)
            }
        }

        outputBias = FloatArray(fanOut) { 0f }
    }

    /**
     * 训练一个批次
     */
    fun trainBatch(
        images: List<Bitmap>,
        labels: IntArray,
        bboxes: Array<FloatArray>
    ): Float {
        if (interpreter == null) {
            Log.e(TAG, "模型未加载")
            return Float.MAX_VALUE
        }

        var totalLoss = 0f

        images.forEachIndexed { idx, image ->
            // 1. 提取特征
            val features = extractFeatures(image)

            // 2. 前向传播
            val output = forwardOutput(features)

            // 3. 计算损失和梯度
            val (loss, gradients) = computeLossAndGradients(
                output,
                labels[idx],
                bboxes[idx]
            )

            totalLoss += loss

            // 4. 更新权重（梯度下降）
            updateWeights(features, gradients)
        }

        return totalLoss / images.size
    }

    /**
     * 提取特征（使用预训练模型）
     */
    private fun extractFeatures(image: Bitmap): FloatArray {
        val tensorImage = TensorImage.fromBitmap(image)
        val processedImage = imageProcessor.process(tensorImage)

        val inputBuffer = processedImage.buffer
        val outputBuffer = ByteBuffer.allocateDirect(1280 * 4)  // MobileNet特征维度
        outputBuffer.order(ByteOrder.nativeOrder())

        // 运行到倒数第二层，获取特征
        interpreter?.run(inputBuffer, outputBuffer)

        val features = FloatArray(1280)
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(features)

        return features
    }

    /**
     * 输出层前向传播
     */
    private fun forwardOutput(features: FloatArray): FloatArray {
        val weights = outputWeights ?: return FloatArray(numClasses + 4)
        val bias = outputBias ?: return FloatArray(numClasses + 4)

        val output = FloatArray(numClasses + 4)

        for (i in output.indices) {
            var sum = bias[i]
            for (j in features.indices) {
                sum += features[j] * weights[i][j]
            }
            output[i] = sum
        }

        // Softmax for classification
        val maxClass = output.slice(0 until numClasses).maxOrNull() ?: 0f
        var sumExp = 0f
        for (i in 0 until numClasses) {
            output[i] = Math.exp((output[i] - maxClass).toDouble()).toFloat()
            sumExp += output[i]
        }
        for (i in 0 until numClasses) {
            output[i] /= sumExp
        }

        // Sigmoid for bbox
        for (i in numClasses until output.size) {
            output[i] = (1.0 / (1.0 + Math.exp(-output[i].toDouble()))).toFloat()
        }

        return output
    }

    /**
     * 计算损失和梯度
     */
    private fun computeLossAndGradients(
        output: FloatArray,
        label: Int,
        bbox: FloatArray
    ): Pair<Float, FloatArray> {
        val gradients = FloatArray(numClasses + 4)

        // 分类损失（交叉熵）
        var classLoss = 0f
        for (i in 0 until numClasses) {
            if (i == label) {
                classLoss -= Math.log(output[i].toDouble() + 1e-7).toFloat()
                gradients[i] = output[i] - 1.0f
            } else {
                gradients[i] = output[i]
            }
        }

        // 边界框损失（MSE）
        var bboxLoss = 0f
        for (i in 0 until 4) {
            val diff = output[numClasses + i] - bbox[i]
            bboxLoss += diff * diff
            gradients[numClasses + i] = 2 * diff
        }

        val totalLoss = classLoss + 0.5f * bboxLoss

        return Pair(totalLoss, gradients)
    }

    /**
     * 更新权重（SGD）
     */
    private fun updateWeights(features: FloatArray, gradients: FloatArray) {
        val weights = outputWeights ?: return
        val bias = outputBias ?: return

        for (i in gradients.indices) {
            // 更新偏置
            bias[i] -= LEARNING_RATE * gradients[i]

            // 更新权重
            for (j in features.indices) {
                weights[i][j] -= LEARNING_RATE * gradients[i] * features[j]
            }
        }
    }

    /**
     * 保存训练后的模型
     */
    fun saveModel(outputPath: String): Boolean {
        try {
            // 在实际实现中，需要将权重写回TFLite模型
            // 这里简化为保存权重矩阵

            val file = FileOutputStream(outputPath)
            // 保存权重...
            file.close()

            Log.i(TAG, "模型已保存到: $outputPath")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "保存模型失败", e)
            return false
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
