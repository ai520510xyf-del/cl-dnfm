package com.gamebot.ai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.gamebot.ai.R
import com.gamebot.ai.cloud.CloudTrainingManager
import com.gamebot.ai.cloud.SupabaseManager
import com.gamebot.ai.cloud.TrainingStatus
import com.gamebot.ai.service.GameBotAccessibilityService
import com.gamebot.ai.utils.ValidationUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import java.io.File

/**
 * 云端训练Fragment
 * 管理整个云端训练流程的UI
 */
class CloudTrainingFragment : Fragment() {

    // Supabase 配置
    private lateinit var etSupabaseUrl: EditText
    private lateinit var etSupabaseKey: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvConnectionStatus: TextView

    // 数据集上传
    private lateinit var tvLabeledCount: TextView
    private lateinit var etDatasetName: EditText
    private lateinit var btnUpload: Button

    // 训练控制
    private lateinit var cardTraining: MaterialCardView
    private lateinit var sliderEpochs: Slider
    private lateinit var tvEpochsValue: TextView
    private lateinit var btnStartTraining: Button

    // 训练进度
    private lateinit var cardProgress: MaterialCardView
    private lateinit var progressTraining: LinearProgressIndicator
    private lateinit var tvTrainingStatus: TextView
    private lateinit var tvTrainingDetails: TextView

    // 模型管理
    private lateinit var cardModel: MaterialCardView
    private lateinit var tvModelInfo: TextView
    private lateinit var btnDownload: Button
    private lateinit var btnDeploy: Button

    // 训练管理器
    private lateinit var cloudManager: CloudTrainingManager

    // 当前状态
    private var currentDatasetId: String? = null
    private var currentJobId: String? = null
    private var downloadedModelFile: File? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_cloud_training, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        updateDatasetCount()

        // 自动连接Supabase（使用硬编码配置）
        autoConnectSupabase()
    }

    private fun initViews(view: View) {
        // 云端状态显示
        tvConnectionStatus = view.findViewById(R.id.tvCloudStatus)

        // Supabase 配置（整个卡片已在布局中隐藏）
        etSupabaseUrl = view.findViewById(R.id.etSupabaseUrl)
        etSupabaseKey = view.findViewById(R.id.etSupabaseKey)
        btnConnect = view.findViewById(R.id.btnConnect)

        // 数据集上传
        tvLabeledCount = view.findViewById(R.id.tvLabeledCount)
        etDatasetName = view.findViewById(R.id.etDatasetName)
        btnUpload = view.findViewById(R.id.btnUpload)

        // 训练控制
        cardTraining = view.findViewById(R.id.cardTraining)
        sliderEpochs = view.findViewById(R.id.sliderEpochs)
        tvEpochsValue = view.findViewById(R.id.tvEpochsValue)
        btnStartTraining = view.findViewById(R.id.btnStartTraining)

        // 训练进度
        cardProgress = view.findViewById(R.id.cardProgress)
        progressTraining = view.findViewById(R.id.progressTraining)
        tvTrainingStatus = view.findViewById(R.id.tvTrainingStatus)
        tvTrainingDetails = view.findViewById(R.id.tvTrainingDetails)

        // 模型管理
        cardModel = view.findViewById(R.id.cardModel)
        tvModelInfo = view.findViewById(R.id.tvModelInfo)
        btnDownload = view.findViewById(R.id.btnDownload)
        btnDeploy = view.findViewById(R.id.btnDeploy)
    }

    private fun setupListeners() {
        // 连接 Supabase
        btnConnect.setOnClickListener {
            connectToSupabase()
        }

        // 上传数据集
        btnUpload.setOnClickListener {
            uploadDataset()
        }

        // Epochs 滑块
        sliderEpochs.addOnChangeListener { _, value, _ ->
            tvEpochsValue.text = "当前: ${value.toInt()} 轮"
        }

        // 开始训练
        btnStartTraining.setOnClickListener {
            startTraining()
        }

        // 下载模型
        btnDownload.setOnClickListener {
            downloadModel()
        }

        // 部署模型
        btnDeploy.setOnClickListener {
            deployModel()
        }
    }

    /**
     * 自动连接Supabase（使用硬编码配置）
     */
    private fun autoConnectSupabase() {
        // 从BuildConfig读取Supabase配置（安全存储在local.properties中）
        val url = com.gamebot.ai.BuildConfig.SUPABASE_URL
        val key = com.gamebot.ai.BuildConfig.SUPABASE_ANON_KEY

        try {
            // Supabase连接不依赖服务，可以直接初始化
            SupabaseManager.initialize(requireContext(), url, key)

            tvConnectionStatus.text = "✅ 已自动连接"
            tvConnectionStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))

            // 尝试初始化训练管理器（如果服务已启动）
            tryInitializeCloudManager()
        } catch (e: Exception) {
            tvConnectionStatus.text = "❌ 连接失败: ${e.message}"
            tvConnectionStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            android.util.Log.e("CloudTraining", "自动连接失败", e)
        }
    }

    /**
     * 尝试初始化云端训练管理器
     */
    private fun tryInitializeCloudManager() {
        val service = GameBotAccessibilityService.instance
        if (service != null && !::cloudManager.isInitialized) {
            try {
                cloudManager = CloudTrainingManager(requireContext(), service.getDatasetManager())
                android.util.Log.i("CloudTraining", "CloudManager初始化成功")
            } catch (e: Exception) {
                android.util.Log.e("CloudTraining", "CloudManager初始化失败", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次页面恢复时尝试初始化（以防服务在页面打开后才启动）
        tryInitializeCloudManager()
        updateDatasetCount()
    }

    /**
     * 连接到 Supabase（手动方式，已弃用）
     */
    private fun connectToSupabase() {
        val url = etSupabaseUrl.text.toString().trim()
        val key = etSupabaseKey.text.toString().trim()

        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(context, "请填写完整的 Supabase 配置", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            SupabaseManager.initialize(requireContext(), url, key)

            // 初始化训练管理器
            val service = GameBotAccessibilityService.instance
            if (service != null) {
                cloudManager = CloudTrainingManager(requireContext(), service.getDatasetManager())

                tvConnectionStatus.text = "✅ 已连接"
                tvConnectionStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))

                Toast.makeText(context, "Supabase 连接成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "服务未运行，请先启动机器人", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            tvConnectionStatus.text = "❌ 连接失败"
            tvConnectionStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
            Toast.makeText(context, "连接失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 上传数据集
     */
    private fun uploadDataset() {
        // 检查服务是否运行（需要DatasetManager）
        val service = GameBotAccessibilityService.instance
        if (service == null) {
            Toast.makeText(context, "请先在首页启动机器人（需要收集数据）", Toast.LENGTH_LONG).show()
            return
        }

        // 尝试初始化cloudManager
        if (!::cloudManager.isInitialized) {
            try {
                cloudManager = CloudTrainingManager(requireContext(), service.getDatasetManager())
            } catch (e: Exception) {
                Toast.makeText(context, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }
        }

        val datasetName = etDatasetName.text.toString().trim()

        // 验证数据集名称
        val validationResult = ValidationUtils.validateDatasetName(datasetName)
        if (!validationResult.isSuccess) {
            Toast.makeText(context, validationResult.errorMessage, Toast.LENGTH_SHORT).show()
            return
        }

        btnUpload.isEnabled = false
        btnUpload.text = "上传中..."

        lifecycleScope.launch {
            try {
                val result = cloudManager.uploadDataset(datasetName)

                result.onSuccess { uploadResult ->
                    if (uploadResult.success) {
                        currentDatasetId = uploadResult.datasetId
                        Toast.makeText(
                            context,
                            "上传成功！${uploadResult.uploadedCount}/${uploadResult.totalCount} 张图片",
                            Toast.LENGTH_LONG
                        ).show()

                        // 显示训练控制卡片
                        cardTraining.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(context, "上传失败: ${uploadResult.error}", Toast.LENGTH_LONG).show()
                    }
                }

                result.onFailure { e ->
                    Toast.makeText(context, "上传失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "上传出错: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnUpload.isEnabled = true
                btnUpload.text = "上传数据集"
            }
        }
    }

    /**
     * 开始训练
     */
    private fun startTraining() {
        if (currentDatasetId == null) {
            Toast.makeText(context, "请先上传数据集", Toast.LENGTH_SHORT).show()
            return
        }

        val epochs = sliderEpochs.value.toInt()

        btnStartTraining.isEnabled = false
        cardProgress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = cloudManager.startTraining(currentDatasetId!!, epochs)

                result.onSuccess { jobId ->
                    currentJobId = jobId
                    Toast.makeText(context, "训练已启动！Job ID: $jobId", Toast.LENGTH_SHORT).show()

                    // 监控训练进度
                    monitorTraining(jobId)
                }

                result.onFailure { e ->
                    Toast.makeText(context, "启动训练失败: ${e.message}", Toast.LENGTH_LONG).show()
                    btnStartTraining.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "启动训练出错: ${e.message}", Toast.LENGTH_LONG).show()
                btnStartTraining.isEnabled = true
            }
        }
    }

    /**
     * 监控训练进度
     */
    private fun monitorTraining(jobId: String) {
        lifecycleScope.launch {
            cloudManager.monitorTraining(jobId).collect { progress ->
                // 更新进度条
                progressTraining.progress = progress.progress

                // 更新状态文本
                val statusText = when (progress.status) {
                    TrainingStatus.PENDING -> "等待中..."
                    TrainingStatus.UPLOADING -> "上传中..."
                    TrainingStatus.TRAINING -> "训练中 ${progress.currentEpoch}/${progress.totalEpochs}"
                    TrainingStatus.COMPLETED -> "✅ 训练完成！"
                    TrainingStatus.FAILED -> "❌ 训练失败"
                }
                tvTrainingStatus.text = "状态: $statusText"

                // 更新详细信息
                val details = buildString {
                    append("Epoch: ${progress.currentEpoch ?: 0}/${progress.totalEpochs}")
                    if (progress.loss != null) {
                        append(" | Loss: %.4f".format(progress.loss))
                    }
                    if (progress.accuracy != null) {
                        append(" | Accuracy: %.2f%%".format(progress.accuracy * 100))
                    }
                }
                tvTrainingDetails.text = details

                // 训练完成
                if (progress.status == TrainingStatus.COMPLETED) {
                    Toast.makeText(context, "🎉 训练完成！可以下载模型了", Toast.LENGTH_LONG).show()
                    cardModel.visibility = View.VISIBLE

                    if (progress.accuracy != null) {
                        tvModelInfo.text = "模型准确率: %.2f%%".format(progress.accuracy * 100)
                    }
                }

                // 训练失败
                if (progress.status == TrainingStatus.FAILED) {
                    Toast.makeText(context, "训练失败: ${progress.message}", Toast.LENGTH_LONG).show()
                    btnStartTraining.isEnabled = true
                }
            }
        }
    }

    /**
     * 下载模型
     */
    private fun downloadModel() {
        if (currentJobId == null) {
            Toast.makeText(context, "没有可下载的模型", Toast.LENGTH_SHORT).show()
            return
        }

        btnDownload.isEnabled = false
        btnDownload.text = "下载中..."

        lifecycleScope.launch {
            try {
                // 模型路径格式: models/{job_id}/model.tflite
                val modelPath = "models/$currentJobId/model.tflite"
                val result = cloudManager.downloadModel(modelPath)

                result.onSuccess { modelFile ->
                    downloadedModelFile = modelFile
                    Toast.makeText(
                        context,
                        "模型已下载: ${modelFile.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    btnDeploy.isEnabled = true
                }

                result.onFailure { e ->
                    Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "下载出错: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnDownload.isEnabled = true
                btnDownload.text = "下载模型"
            }
        }
    }

    /**
     * 部署模型
     */
    private fun deployModel() {
        if (downloadedModelFile == null) {
            Toast.makeText(context, "请先下载模型", Toast.LENGTH_SHORT).show()
            return
        }

        btnDeploy.isEnabled = false
        btnDeploy.text = "部署中..."

        lifecycleScope.launch {
            try {
                val result = cloudManager.deployModel(downloadedModelFile!!)

                result.onSuccess {
                    Toast.makeText(
                        context,
                        "✅ 模型已部署！可以在首页启动使用了",
                        Toast.LENGTH_LONG
                    ).show()
                }

                result.onFailure { e ->
                    Toast.makeText(context, "部署失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "部署出错: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnDeploy.isEnabled = true
                btnDeploy.text = "部署模型"
            }
        }
    }

    /**
     * 更新数据集计数
     */
    private fun updateDatasetCount() {
        val service = GameBotAccessibilityService.instance
        if (service != null) {
            try {
                val stats = service.getDatasetManager().getStatistics()
                tvLabeledCount.text = "${stats.labeledImages} 张"
            } catch (e: Exception) {
                tvLabeledCount.text = "0 张"
            }
        }
    }

    companion object {
        fun newInstance() = CloudTrainingFragment()
    }
}
