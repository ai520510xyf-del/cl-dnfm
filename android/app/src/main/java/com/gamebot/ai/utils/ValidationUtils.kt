package com.gamebot.ai.utils

import android.util.Log
import java.io.File

/**
 * 输入验证工具类
 * 提供各种输入验证和清理功能，防止注入攻击和路径遍历
 */
object ValidationUtils {
    private const val TAG = "ValidationUtils"

    // 保留的文件名（不允许使用）
    private val RESERVED_NAMES = setOf(
        "con", "prn", "aux", "nul",
        "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
        "system", "root", "admin", "config", "temp"
    )

    /**
     * 验证结果密封类
     */
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()

        val isSuccess: Boolean
            get() = this is Success

        val errorMessage: String?
            get() = (this as? Error)?.message
    }

    /**
     * 验证数据集名称
     *
     * 规则:
     * - 不能为空
     * - 长度1-50字符
     * - 只能包含字母、数字、下划线和连字符
     * - 不能包含路径遍历字符
     * - 不能是保留名称
     *
     * @param name 数据集名称
     * @return 验证结果
     */
    fun validateDatasetName(name: String): ValidationResult {
        return when {
            name.isEmpty() ->
                ValidationResult.Error("数据集名称不能为空")

            name.length > 50 ->
                ValidationResult.Error("数据集名称过长（最多50字符）")

            name.length < 1 ->
                ValidationResult.Error("数据集名称过短（至少1字符）")

            !name.matches(Regex("^[a-zA-Z0-9_-]+$")) ->
                ValidationResult.Error("数据集名称只能包含字母、数字、下划线和连字符")

            name.contains("..") ->
                ValidationResult.Error("数据集名称包含非法字符")

            RESERVED_NAMES.contains(name.lowercase()) ->
                ValidationResult.Error("该名称为系统保留名称")

            name.startsWith(".") ->
                ValidationResult.Error("数据集名称不能以点开头")

            name.startsWith("-") ->
                ValidationResult.Error("数据集名称不能以连字符开头")

            else -> ValidationResult.Success
        }
    }

    /**
     * 验证文件名
     *
     * 用于验证图片文件名、模型文件名等
     *
     * 规则:
     * - 不能为空
     * - 长度1-255字符
     * - 不能包含路径分隔符
     * - 不能包含特殊字符
     * - 不能是保留名称
     *
     * @param filename 文件名
     * @return 验证结果
     */
    fun validateFilename(filename: String): ValidationResult {
        return when {
            filename.isEmpty() ->
                ValidationResult.Error("文件名不能为空")

            filename.length > 255 ->
                ValidationResult.Error("文件名过长（最多255字符）")

            filename.contains("/") || filename.contains("\\") ->
                ValidationResult.Error("文件名不能包含路径分隔符")

            filename.contains("..") ->
                ValidationResult.Error("文件名包含非法字符")

            filename.contains("\u0000") ->
                ValidationResult.Error("文件名包含空字符")

            RESERVED_NAMES.contains(filename.lowercase().substringBeforeLast('.')) ->
                ValidationResult.Error("该文件名为系统保留名称")

            filename.startsWith(".") ->
                ValidationResult.Error("文件名不能以点开头")

            !filename.matches(Regex("^[a-zA-Z0-9_.-]+$")) ->
                ValidationResult.Error("文件名包含非法字符")

            else -> ValidationResult.Success
        }
    }

    /**
     * 验证DNF截图文件名格式
     *
     * 期望格式: dnf_YYYYMMDD_HHMMSS_XXX.jpg
     *
     * @param filename 文件名
     * @return 验证结果
     */
    fun validateDnfScreenshotFilename(filename: String): ValidationResult {
        val pattern = Regex("^dnf_\\d{8}_\\d{6}_\\d{3}\\.jpg$")
        return if (filename.matches(pattern)) {
            ValidationResult.Success
        } else {
            ValidationResult.Error("文件名格式不正确，期望格式: dnf_YYYYMMDD_HHMMSS_XXX.jpg")
        }
    }

    /**
     * 验证路径是否在指定目录内（防止路径遍历攻击）
     *
     * @param file 要验证的文件
     * @param baseDir 基准目录
     * @return 验证结果
     */
    fun validatePathInDirectory(file: File, baseDir: File): ValidationResult {
        return try {
            val canonicalFile = file.canonicalPath
            val canonicalBase = baseDir.canonicalPath

            if (canonicalFile.startsWith(canonicalBase)) {
                ValidationResult.Success
            } else {
                Log.w(TAG, "路径遍历攻击尝试: $canonicalFile 不在 $canonicalBase 内")
                ValidationResult.Error("文件路径无效")
            }
        } catch (e: Exception) {
            Log.e(TAG, "路径验证失败", e)
            ValidationResult.Error("文件路径验证失败")
        }
    }

    /**
     * 清理并验证标注类名
     *
     * 只允许预定义的类名
     *
     * @param className 类名
     * @return 验证结果
     */
    fun validateAnnotationClassName(className: String): ValidationResult {
        val allowedClasses = setOf(
            "enemy",      // 敌人
            "item",       // 物品
            "skill",      // 技能
            "boss",       // BOSS
            "door",       // 门
            "button",     // 按钮
            "npc",        // NPC
            "portal",     // 传送门
            "chest",      // 宝箱
            "trap",       // 陷阱
            "obstacle",   // 障碍物
            "player"      // 玩家
        )

        return if (className in allowedClasses) {
            ValidationResult.Success
        } else {
            ValidationResult.Error("未知的标注类别: $className")
        }
    }

    /**
     * 验证URL格式
     *
     * @param url URL字符串
     * @return 验证结果
     */
    fun validateUrl(url: String): ValidationResult {
        return when {
            url.isEmpty() ->
                ValidationResult.Error("URL不能为空")

            !url.startsWith("https://") && !url.startsWith("http://") ->
                ValidationResult.Error("URL必须以http://或https://开头")

            url.length > 2048 ->
                ValidationResult.Error("URL过长")

            !url.matches(Regex("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")) ->
                ValidationResult.Error("URL格式不正确")

            else -> ValidationResult.Success
        }
    }

    /**
     * 验证Supabase配置
     *
     * @param url Supabase URL
     * @param key Supabase Key
     * @return 验证结果
     */
    fun validateSupabaseConfig(url: String, key: String): ValidationResult {
        // 验证URL
        val urlResult = validateUrl(url)
        if (urlResult !is ValidationResult.Success) {
            return urlResult
        }

        // 验证URL是否为supabase.co域名
        if (!url.contains("supabase.co")) {
            return ValidationResult.Error("Supabase URL必须包含supabase.co域名")
        }

        // 验证Key
        return when {
            key.isEmpty() ->
                ValidationResult.Error("Supabase Key不能为空")

            key.length < 32 ->
                ValidationResult.Error("Supabase Key格式不正确（过短）")

            !key.startsWith("eyJ") ->
                ValidationResult.Error("Supabase Key格式不正确（应为JWT格式）")

            else -> ValidationResult.Success
        }
    }

    /**
     * 验证数字范围
     *
     * @param value 数值
     * @param min 最小值
     * @param max 最大值
     * @param fieldName 字段名称（用于错误消息）
     * @return 验证结果
     */
    fun validateNumberRange(value: Int, min: Int, max: Int, fieldName: String = "数值"): ValidationResult {
        return when {
            value < min ->
                ValidationResult.Error("$fieldName 不能小于 $min")

            value > max ->
                ValidationResult.Error("$fieldName 不能大于 $max")

            else -> ValidationResult.Success
        }
    }

    /**
     * 清理字符串（移除潜在危险字符）
     *
     * @param input 输入字符串
     * @return 清理后的字符串
     */
    fun sanitizeString(input: String): String {
        return input
            .replace("\u0000", "")  // 移除空字符
            .replace("\r", "")      // 移除回车
            .replace("\n", " ")     // 替换换行为空格
            .trim()
    }

    /**
     * 验证模型文件路径
     *
     * @param modelPath 模型路径
     * @return 验证结果
     */
    fun validateModelPath(modelPath: String): ValidationResult {
        return when {
            modelPath.isEmpty() ->
                ValidationResult.Error("模型路径不能为空")

            !modelPath.endsWith(".tflite") ->
                ValidationResult.Error("模型文件必须是.tflite格式")

            modelPath.contains("..") ->
                ValidationResult.Error("模型路径包含非法字符")

            !modelPath.matches(Regex("^[a-zA-Z0-9_/.-]+$")) ->
                ValidationResult.Error("模型路径包含非法字符")

            else -> ValidationResult.Success
        }
    }

    /**
     * 验证训练轮数（Epochs）
     *
     * @param epochs 训练轮数
     * @return 验证结果
     */
    fun validateEpochs(epochs: Int): ValidationResult {
        return validateNumberRange(epochs, 1, 1000, "训练轮数")
    }

    /**
     * 批量验证
     *
     * @param validations 验证函数列表
     * @return 第一个失败的验证结果，或Success
     */
    fun validateAll(vararg validations: () -> ValidationResult): ValidationResult {
        for (validation in validations) {
            val result = validation()
            if (result !is ValidationResult.Success) {
                return result
            }
        }
        return ValidationResult.Success
    }
}
