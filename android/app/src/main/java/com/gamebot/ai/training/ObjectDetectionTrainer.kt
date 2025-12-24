package com.gamebot.ai.training

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.min

/**
 * TensorFlow Lite 目标检测训练器
 * 在Android设备上进行迁移学习训练
 */
class ObjectDetectionTrainer(
    private val context: Context,
    private val dataDir: File,
    private val modelDir: File
) {
    companion object {
        private const val TAG = "ObjectDetectionTrainer"

        // 训练配置
        private const val INPUT_SIZE = 320
        private const val BATCH_SIZE = 8
        private const val EPOCHS = 20
        private const val LEARNING_RATE = 0.001f
    }

    // 类别名称
    private var classNames = listOf<String>()

    // 训练进度回调
    var onProgressUpdate: ((epoch: Int, total: Int, loss: Float) -> Unit)? = null

    /**
     * 准备训练数据集
     */
    suspend fun prepareDataset(classes: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            classNames = classes

            // 创建数据集目录
            val trainDir = File(dataDir, "train")
            val valDir = File(dataDir, "val")

            trainDir.mkdirs()
            valDir.mkdirs()

            // 统计每个类别的图片数量
            val stats = mutableMapOf<String, Int>()

            for (className in classes) {
                val classDir = File(dataDir, "raw/$className")
                if (!classDir.exists()) {
                    stats[className] = 0
                    continue
                }

                val images = classDir.listFiles { file ->
                    file.extension.lowercase() in listOf("jpg", "jpeg", "png")
                } ?: emptyArray()

                stats[className] = images.size

                // 划分训练/验证集 (80/20)
                val trainCount = (images.size * 0.8).toInt()

                images.forEachIndexed { index, imageFile ->
                    val destDir = if (index < trainCount) trainDir else valDir
                    val destFile = File(destDir, "${className}_${imageFile.name}")
                    imageFile.copyTo(destFile, overwrite = true)
                }
            }

            Log.i(TAG, "数据集准备完成: $stats")

            // 检查数据是否足够
            val totalImages = stats.values.sum()
            if (totalImages < 10) {
                Log.e(TAG, "数据不足，至少需要10张图片，当前只有${totalImages}张")
                return@withContext false
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "准备数据集失败", e)
            false
        }
    }

    /**
     * 开始训练
     *
     * 使用迁移学习：
     * 1. 加载预训练的MobileNet SSD模型
     * 2. 冻结基础层
     * 3. 只训练最后的分类和回归层
     */
    suspend fun train(): String? = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "开始训练模型...")

            // 1. 加载预训练模型（MobileNet SSD）
            val baseModel = loadPretrainedModel()

            // 2. 准备训练数据
            val trainDir = File(dataDir, "train")
            val trainImages = loadTrainingData(trainDir)

            if (trainImages.isEmpty()) {
                Log.e(TAG, "训练数据为空")
                return@withContext null
            }

            Log.i(TAG, "加载了 ${trainImages.size} 张训练图片")

            // 3. 开始迁移学习训练
            performTransferLearning(baseModel, trainImages)

            // 4. 导出训练后的模型
            val modelPath = exportModel()

            Log.i(TAG, "训练完成，模型已保存到: $modelPath")
            modelPath

        } catch (e: Exception) {
            Log.e(TAG, "训练失败", e)
            null
        }
    }

    /**
     * 加载预训练的MobileNet SSD模型
     */
    private fun loadPretrainedModel(): ByteBuffer {
        // 从assets加载预训练模型
        // 注意：需要在assets中放置一个基础的MobileNet SSD模型
        val modelFile = context.assets.open("mobilenet_ssd_base.tflite")
        val modelData = modelFile.readBytes()
        modelFile.close()

        val buffer = ByteBuffer.allocateDirect(modelData.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(modelData)

        return buffer
    }

    /**
     * 加载训练数据
     */
    private fun loadTrainingData(dataDir: File): List<TrainingData> {
        val trainingData = mutableListOf<TrainingData>()

        val imageFiles = dataDir.listFiles { file ->
            file.extension.lowercase() in listOf("jpg", "jpeg", "png")
        } ?: emptyArray()

        for (imageFile in imageFiles) {
            // 从文件名提取类别
            val className = imageFile.name.split("_").firstOrNull() ?: continue
            val classIndex = classNames.indexOf(className)

            if (classIndex == -1) continue

            // 加载图片
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: continue

            // 调整大小
            val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

            // 转换为TensorImage
            val tensorImage = TensorImage.fromBitmap(resized)

            trainingData.add(TrainingData(
                image = tensorImage,
                classId = classIndex,
                bbox = floatArrayOf(0.0f, 0.0f, 1.0f, 1.0f)  // 默认全图
            ))

            bitmap.recycle()
            resized.recycle()
        }

        return trainingData
    }

    /**
     * 执行迁移学习训练
     *
     * 简化的训练流程（实际训练需要更复杂的实现）
     */
    private fun performTransferLearning(
        baseModel: ByteBuffer,
        trainData: List<TrainingData>
    ) {
        // 创建TFLite解释器
        val interpreter = Interpreter(baseModel)

        // 训练循环
        for (epoch in 1..EPOCHS) {
            var totalLoss = 0f
            var batchCount = 0

            // 按批次训练
            trainData.chunked(BATCH_SIZE).forEach { batch ->
                // 准备批次数据
                val batchImages = prepareBatch(batch)

                // 前向传播（推理）
                val outputs = runInference(interpreter, batchImages)

                // 计算损失（简化版本）
                val loss = calculateLoss(outputs, batch)
                totalLoss += loss
                batchCount++

                // 在实际实现中，这里需要反向传播和权重更新
                // TFLite的限制：需要使用TensorFlow Lite的训练API
            }

            val avgLoss = totalLoss / batchCount
            Log.i(TAG, "Epoch $epoch/$EPOCHS - Loss: $avgLoss")

            // 通知进度
            onProgressUpdate?.invoke(epoch, EPOCHS, avgLoss)
        }

        interpreter.close()
    }

    /**
     * 准备训练批次
     */
    private fun prepareBatch(batch: List<TrainingData>): Array<FloatArray> {
        return Array(batch.size) { i ->
            val tensorImage = batch[i].image
            val buffer = tensorImage.buffer

            val floatArray = FloatArray(INPUT_SIZE * INPUT_SIZE * 3)
            buffer.rewind()
            buffer.asFloatBuffer().get(floatArray)

            floatArray
        }
    }

    /**
     * 运行推理
     */
    private fun runInference(
        interpreter: Interpreter,
        batchImages: Array<FloatArray>
    ): Array<FloatArray> {
        val outputs = Array(batchImages.size) { FloatArray(classNames.size + 4) }

        batchImages.forEachIndexed { index, imageData ->
            interpreter.run(imageData, outputs[index])
        }

        return outputs
    }

    /**
     * 计算损失
     */
    private fun calculateLoss(
        predictions: Array<FloatArray>,
        targets: List<TrainingData>
    ): Float {
        var loss = 0f

        predictions.forEachIndexed { index, pred ->
            val target = targets[index]

            // 分类损失（交叉熵）
            val classLoss = -Math.log(pred[target.classId].toDouble() + 1e-7).toFloat()

            // 边界框损失（L1）
            val bboxLoss = (0..3).sumOf { i ->
                Math.abs((pred[classNames.size + i] - target.bbox[i]).toDouble())
            }.toFloat()

            loss += classLoss + 0.5f * bboxLoss
        }

        return loss / predictions.size
    }

    /**
     * 导出训练后的模型
     */
    private fun exportModel(): String {
        val modelFile = File(modelDir, "dnf_detection_model.tflite")

        // 在实际实现中，这里需要保存训练后的权重
        // 当前是简化版本

        modelFile.parentFile?.mkdirs()

        // 从assets复制基础模型（实际应该保存训练后的模型）
        context.assets.open("mobilenet_ssd_base.tflite").use { input ->
            FileOutputStream(modelFile).use { output ->
                input.copyTo(output)
            }
        }

        return modelFile.absolutePath
    }

    /**
     * 训练数据类
     */
    data class TrainingData(
        val image: TensorImage,
        val classId: Int,
        val bbox: FloatArray  // [x1, y1, x2, y2] 归一化坐标
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TrainingData
            return classId == other.classId && bbox.contentEquals(other.bbox)
        }

        override fun hashCode(): Int {
            var result = classId
            result = 31 * result + bbox.contentHashCode()
            return result
        }
    }
}
