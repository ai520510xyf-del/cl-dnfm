package com.gamebot.ai.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.gamebot.ai.data.DatasetManager
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 云端训练管理器
 * 负责与 Supabase 交互，管理整个训练流程
 */
class CloudTrainingManager(
    private val context: Context,
    private val datasetManager: DatasetManager
) {
    companion object {
        private const val TAG = "CloudTrainingManager"
        private const val BUCKET_DATASETS = "datasets"
        private const val BUCKET_MODELS = "models"
    }

    /**
     * 上传数据集到 Supabase
     *
     * @param datasetName 数据集名称
     * @return 上传结果
     */
    suspend fun uploadDataset(datasetName: String): Result<UploadResult> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "开始上传数据集: $datasetName")

            // 1. 获取所有已标注的图片
            val images = datasetManager.getAllImages().filter { it.isLabeled }
            if (images.isEmpty()) {
                return@withContext Result.failure(Exception("没有已标注的图片"))
            }

            // 2. 创建数据集记录
            val datasetId = UUID.randomUUID().toString()
            val dataset = Dataset(
                id = datasetId,
                name = datasetName,
                total_images = images.size,
                labeled_images = images.size,
                classes = listOf("enemy", "skill", "door", "button", "boss", "item"),
                storage_path = "datasets/$datasetId/"
            )

            SupabaseManager.client.from("datasets").insert(dataset)
            Log.i(TAG, "数据集记录已创建: $datasetId")

            // 3. 上传图片和标注到 Storage
            var uploadedCount = 0
            images.forEach { imageData ->
                try {
                    // 读取图片文件
                    val imageFile = File(imageData.path)
                    val imageBytes = imageFile.readBytes()

                    // 上传图片
                    val imagePath = "datasets/$datasetId/images/${imageData.filename}"
                    SupabaseManager.storage.from(BUCKET_DATASETS)
                        .upload(imagePath, imageBytes)

                    // 读取标注
                    val annotations = datasetManager.getAnnotations(imageData.filename)

                    // 构建标注JSON并上传
                    val annotationJson = buildAnnotationJson(imageData.filename, annotations)
                    val annotationPath = "datasets/$datasetId/annotations/${imageData.filename.replace(".jpg", ".json")}"
                    SupabaseManager.storage.from(BUCKET_DATASETS)
                        .upload(annotationPath, annotationJson.toByteArray())

                    uploadedCount++
                    Log.d(TAG, "已上传: ${imageData.filename} ($uploadedCount/${images.size})")
                } catch (e: Exception) {
                    Log.e(TAG, "上传失败: ${imageData.filename}", e)
                }
            }

            Log.i(TAG, "数据集上传完成: $uploadedCount/${images.size}")

            Result.success(
                UploadResult(
                    success = true,
                    datasetId = datasetId,
                    uploadedCount = uploadedCount,
                    totalCount = images.size,
                    error = null
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "上传数据集失败", e)
            Result.failure(e)
        }
    }

    /**
     * 开始训练任务
     *
     * @param datasetId 数据集ID
     * @param epochs 训练轮数
     * @return 训练任务ID
     */
    suspend fun startTraining(datasetId: String, epochs: Int = 50): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "创建训练任务: dataset=$datasetId, epochs=$epochs")

            // 创建训练任务记录
            val jobId = UUID.randomUUID().toString()
            val trainingJob = TrainingJob(
                id = jobId,
                dataset_id = datasetId,
                status = "pending",
                total_epochs = epochs
            )

            SupabaseManager.client.from("training_jobs").insert(trainingJob)
            Log.i(TAG, "训练任务已创建: $jobId")

            // 调用 Edge Function 触发训练
            try {
                SupabaseManager.functions.invoke(
                    "trigger-training",
                    mapOf(
                        "job_id" to jobId,
                        "dataset_id" to datasetId,
                        "epochs" to epochs
                    )
                )
                Log.i(TAG, "训练已触发")
            } catch (e: Exception) {
                Log.w(TAG, "触发训练失败，任务已创建但需要手动触发", e)
            }

            Result.success(jobId)
        } catch (e: Exception) {
            Log.e(TAG, "创建训练任务失败", e)
            Result.failure(e)
        }
    }

    /**
     * 监控训练进度（轮询方式）
     *
     * @param jobId 训练任务ID
     * @return 训练进度流
     */
    fun monitorTraining(jobId: String): Flow<TrainingProgress> = flow {
        try {
            Log.i(TAG, "开始监控训练: $jobId")

            // 使用轮询方式查询训练状态
            var lastProgress = -1
            while (true) {
                try {
                    // 查询训练状态
                    val jobs = SupabaseManager.client.from("training_jobs")
                        .select {
                            filter {
                                eq("id", jobId)
                            }
                        }
                        .decodeList<TrainingJob>()

                    if (jobs.isEmpty()) {
                        Log.w(TAG, "训练任务不存在: $jobId")
                        break
                    }

                    val job = jobs[0]

                    val progress = TrainingProgress(
                        jobId = job.id,
                        status = TrainingStatus.valueOf(job.status.uppercase()),
                        progress = job.progress,
                        currentEpoch = job.current_epoch,
                        totalEpochs = job.total_epochs,
                        loss = job.loss,
                        accuracy = job.accuracy,
                        message = job.error_message
                    )

                    // 只在进度变化时发送更新
                    if (job.progress != lastProgress) {
                        emit(progress)
                        lastProgress = job.progress
                    }

                    // 如果训练完成或失败，停止监控
                    if (job.status == "completed" || job.status == "failed") {
                        break
                    }

                    // 等待5秒后再次查询
                    kotlinx.coroutines.delay(5000)
                } catch (e: Exception) {
                    Log.e(TAG, "查询训练状态失败", e)
                    kotlinx.coroutines.delay(5000)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "监控训练失败", e)
        }
    }

    /**
     * 下载训练好的模型
     *
     * @param modelPath Storage中的模型路径
     * @return 下载的本地文件
     */
    suspend fun downloadModel(modelPath: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "开始下载模型: $modelPath")

            // 从 Supabase Storage 下载
            val modelBytes = SupabaseManager.storage.from(BUCKET_MODELS)
                .downloadPublic(modelPath)

            // 保存到本地
            val modelFile = File(context.filesDir, "downloaded_model.tflite")
            FileOutputStream(modelFile).use { out ->
                out.write(modelBytes)
            }

            Log.i(TAG, "模型已下载: ${modelFile.absolutePath}, 大小: ${modelBytes.size} bytes")
            Result.success(modelFile)
        } catch (e: Exception) {
            Log.e(TAG, "下载模型失败", e)
            Result.failure(e)
        }
    }

    /**
     * 部署模型到应用
     *
     * @param modelFile 下载的模型文件
     * @param modelName 模型名称（如 dnf_detection_model.tflite）
     * @return 是否成功
     */
    suspend fun deployModel(modelFile: File, modelName: String = "dnf_detection_model.tflite"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "开始部署模型: $modelName")

            // 复制到 assets 目录（实际上是内部存储的特定位置）
            val assetsDir = File(context.filesDir, "models")
            if (!assetsDir.exists()) {
                assetsDir.mkdirs()
            }

            val targetFile = File(assetsDir, modelName)
            modelFile.copyTo(targetFile, overwrite = true)

            Log.i(TAG, "模型已部署: ${targetFile.absolutePath}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "部署模型失败", e)
            Result.failure(e)
        }
    }

    /**
     * 获取所有训练任务
     */
    suspend fun getAllTrainingJobs(): Result<List<TrainingJob>> = withContext(Dispatchers.IO) {
        try {
            val jobs = SupabaseManager.client.from("training_jobs")
                .select()
                .decodeList<TrainingJob>()

            Result.success(jobs)
        } catch (e: Exception) {
            Log.e(TAG, "获取训练任务列表失败", e)
            Result.failure(e)
        }
    }

    /**
     * 构建标注JSON
     */
    private fun buildAnnotationJson(
        filename: String,
        annotations: List<DatasetManager.Annotation>
    ): String {
        val json = StringBuilder()
        json.append("{\n")
        json.append("  \"image\": \"$filename\",\n")
        json.append("  \"annotations\": [\n")

        annotations.forEachIndexed { index, annotation ->
            json.append("    {\n")
            json.append("      \"class\": \"${annotation.className}\",\n")
            json.append("      \"x\": ${annotation.rect.left},\n")
            json.append("      \"y\": ${annotation.rect.top},\n")
            json.append("      \"width\": ${annotation.rect.width()},\n")
            json.append("      \"height\": ${annotation.rect.height()}\n")
            json.append("    }")
            if (index < annotations.size - 1) json.append(",")
            json.append("\n")
        }

        json.append("  ]\n")
        json.append("}")
        return json.toString()
    }
}
