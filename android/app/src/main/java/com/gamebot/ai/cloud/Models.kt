package com.gamebot.ai.cloud

import kotlinx.serialization.Serializable

/**
 * 训练任务状态
 */
enum class TrainingStatus {
    PENDING,      // 等待中
    UPLOADING,    // 上传中
    TRAINING,     // 训练中
    COMPLETED,    // 已完成
    FAILED        // 失败
}

/**
 * 训练任务数据模型
 */
@Serializable
data class TrainingJob(
    val id: String,
    val dataset_id: String,
    val status: String = "pending",
    val progress: Int = 0,
    val current_epoch: Int? = null,
    val total_epochs: Int = 50,
    val loss: Float? = null,
    val accuracy: Float? = null,
    val model_url: String? = null,
    val error_message: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

/**
 * 数据集数据模型
 */
@Serializable
data class Dataset(
    val id: String,
    val name: String,
    val description: String? = null,
    val total_images: Int = 0,
    val labeled_images: Int = 0,
    val classes: List<String> = emptyList(),
    val storage_path: String? = null,
    val created_at: String? = null
)

/**
 * 模型数据模型
 */
@Serializable
data class Model(
    val id: String,
    val name: String,
    val version: String,
    val training_job_id: String,
    val accuracy: Float? = null,
    val file_size: Int? = null,
    val storage_path: String,
    val is_deployed: Boolean = false,
    val created_at: String? = null
)

/**
 * 训练进度更新
 */
data class TrainingProgress(
    val jobId: String,
    val status: TrainingStatus,
    val progress: Int,
    val currentEpoch: Int?,
    val totalEpochs: Int,
    val loss: Float?,
    val accuracy: Float?,
    val message: String?
)

/**
 * 上传结果
 */
data class UploadResult(
    val success: Boolean,
    val datasetId: String?,
    val uploadedCount: Int,
    val totalCount: Int,
    val error: String?
)

/**
 * 训练结果
 */
data class TrainingResult(
    val success: Boolean,
    val jobId: String?,
    val modelUrl: String?,
    val accuracy: Float?,
    val error: String?
)
