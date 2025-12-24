package com.gamebot.ai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.gamebot.ai.utils.ValidationUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据集管理器
 * 负责管理训练数据的存储、标注、导出
 */
class DatasetManager(private val context: Context) {

    companion object {
        private const val TAG = "DatasetManager"
        private const val IMAGES_DIR = "images"
        private const val ANNOTATIONS_DIR = "annotations"
        private const val METADATA_FILE = "metadata.json"
    }

    private val datasetDir: File = File(context.getExternalFilesDir(null), "dataset")
    private val imagesDir: File = File(datasetDir, IMAGES_DIR)
    private val annotationsDir: File = File(datasetDir, ANNOTATIONS_DIR)

    init {
        // 创建必要的目录
        if (!datasetDir.exists()) {
            datasetDir.mkdirs()
        }
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        if (!annotationsDir.exists()) {
            annotationsDir.mkdirs()
        }

        Log.d(TAG, "数据集目录: ${datasetDir.absolutePath}")
    }

    /**
     * 保存截图
     */
    fun saveScreenshot(bitmap: Bitmap): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val filename = "dnf_$timestamp.jpg"
            val imageFile = File(imagesDir, filename)

            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            Log.d(TAG, "截图已保存: $filename")
            filename
        } catch (e: Exception) {
            Log.e(TAG, "保存截图失败", e)
            null
        }
    }

    /**
     * 保存标注数据
     */
    fun saveAnnotation(imageFilename: String, annotations: List<Annotation>) {
        try {
            // 验证文件名格式
            val filenameValidation = ValidationUtils.validateDnfScreenshotFilename(imageFilename)
            if (!filenameValidation.isSuccess) {
                Log.e(TAG, "非法文件名: $imageFilename")
                return
            }

            val annotationFile = File(annotationsDir, imageFilename.replace(".jpg", ".json"))

            // 验证路径在预期目录内
            val pathValidation = ValidationUtils.validatePathInDirectory(annotationFile, annotationsDir)
            if (!pathValidation.isSuccess) {
                Log.e(TAG, "路径遍历攻击尝试: $imageFilename")
                return
            }

            val jsonObject = JSONObject()
            jsonObject.put("image", imageFilename)
            jsonObject.put("width", 1080) // 默认分辨率
            jsonObject.put("height", 2400)

            val annotationsArray = JSONArray()
            annotations.forEach { annotation ->
                // 验证标注类名
                val classValidation = ValidationUtils.validateAnnotationClassName(annotation.className)
                if (!classValidation.isSuccess) {
                    Log.w(TAG, "跳过无效标注类名: ${annotation.className}")
                    return@forEach
                }

                val annotationObj = JSONObject()
                annotationObj.put("class", annotation.className)
                annotationObj.put("x", annotation.rect.left)
                annotationObj.put("y", annotation.rect.top)
                annotationObj.put("width", annotation.rect.width())
                annotationObj.put("height", annotation.rect.height())
                annotationObj.put("confidence", 1.0)
                annotationsArray.put(annotationObj)
            }

            jsonObject.put("annotations", annotationsArray)

            annotationFile.writeText(jsonObject.toString(2))

            Log.d(TAG, "标注已保存: ${annotationFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "保存标注失败", e)
        }
    }

    /**
     * 获取所有图片
     */
    fun getAllImages(): List<ImageData> {
        val images = mutableListOf<ImageData>()

        imagesDir.listFiles { file -> file.extension == "jpg" }?.forEach { imageFile ->
            val annotationFile = File(annotationsDir, imageFile.name.replace(".jpg", ".json"))
            images.add(
                ImageData(
                    filename = imageFile.name,
                    path = imageFile.absolutePath,
                    isLabeled = annotationFile.exists(),
                    timestamp = imageFile.lastModified()
                )
            )
        }

        return images.sortedByDescending { it.timestamp }
    }

    /**
     * 获取图片的标注
     */
    fun getAnnotations(imageFilename: String): List<Annotation> {
        val annotations = mutableListOf<Annotation>()

        try {
            // 验证文件名格式
            val filenameValidation = ValidationUtils.validateDnfScreenshotFilename(imageFilename)
            if (!filenameValidation.isSuccess) {
                Log.e(TAG, "非法文件名: $imageFilename")
                return annotations
            }

            val annotationFile = File(annotationsDir, imageFilename.replace(".jpg", ".json"))

            // 验证路径在预期目录内
            val pathValidation = ValidationUtils.validatePathInDirectory(annotationFile, annotationsDir)
            if (!pathValidation.isSuccess) {
                Log.e(TAG, "路径遍历攻击尝试: $imageFilename")
                return annotations
            }

            if (!annotationFile.exists()) {
                return annotations
            }

            val jsonObject = JSONObject(annotationFile.readText())
            val annotationsArray = jsonObject.getJSONArray("annotations")

            for (i in 0 until annotationsArray.length()) {
                val annotationObj = annotationsArray.getJSONObject(i)
                val x = annotationObj.getInt("x")
                val y = annotationObj.getInt("y")
                val width = annotationObj.getInt("width")
                val height = annotationObj.getInt("height")

                annotations.add(
                    Annotation(
                        className = annotationObj.getString("class"),
                        rect = Rect(x, y, x + width, y + height)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取标注失败", e)
        }

        return annotations
    }

    /**
     * 删除图片及其标注
     */
    fun deleteImage(imageFilename: String): Boolean {
        return try {
            // 验证文件名格式（防止路径遍历攻击）
            val filenameValidation = ValidationUtils.validateDnfScreenshotFilename(imageFilename)
            if (!filenameValidation.isSuccess) {
                Log.e(TAG, "非法文件名: $imageFilename")
                return false
            }

            val imageFile = File(imagesDir, imageFilename)
            val annotationFile = File(annotationsDir, imageFilename.replace(".jpg", ".json"))

            // 验证路径在预期目录内（防止路径遍历）
            val imagePathValidation = ValidationUtils.validatePathInDirectory(imageFile, imagesDir)
            if (!imagePathValidation.isSuccess) {
                Log.e(TAG, "路径遍历攻击尝试: $imageFilename")
                return false
            }

            val annotationPathValidation = ValidationUtils.validatePathInDirectory(annotationFile, annotationsDir)
            if (!annotationPathValidation.isSuccess) {
                Log.e(TAG, "路径遍历攻击尝试: $imageFilename")
                return false
            }

            var success = true
            if (imageFile.exists()) {
                success = imageFile.delete()
            }
            if (annotationFile.exists()) {
                success = success && annotationFile.delete()
            }

            Log.d(TAG, "删除图片: $imageFilename, 结果: $success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "删除图片失败", e)
            false
        }
    }

    /**
     * 清空所有数据
     */
    fun clearAll(): Boolean {
        return try {
            imagesDir.listFiles()?.forEach { it.delete() }
            annotationsDir.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "已清空所有数据")
            true
        } catch (e: Exception) {
            Log.e(TAG, "清空数据失败", e)
            false
        }
    }

    /**
     * 导出数据集为COCO格式
     */
    fun exportToCOCO(): File? {
        return try {
            val cocoJson = JSONObject()

            // 基本信息
            val info = JSONObject()
            info.put("description", "DNF Mobile Game Dataset")
            info.put("version", "1.0")
            info.put("year", Calendar.getInstance().get(Calendar.YEAR))
            info.put("date_created", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
            cocoJson.put("info", info)

            // 类别定义
            val categories = JSONArray()
            val categoryMap = mapOf(
                "enemy" to 1,
                "item" to 2,
                "skill" to 3,
                "boss" to 4
            )
            categoryMap.forEach { (name, id) ->
                val category = JSONObject()
                category.put("id", id)
                category.put("name", name)
                category.put("supercategory", "object")
                categories.put(category)
            }
            cocoJson.put("categories", categories)

            // 图片和标注
            val images = JSONArray()
            val annotations = JSONArray()
            var imageId = 1
            var annotationId = 1

            getAllImages().forEach { imageData ->
                // 图片信息
                val imageObj = JSONObject()
                imageObj.put("id", imageId)
                imageObj.put("file_name", imageData.filename)
                imageObj.put("width", 1080)
                imageObj.put("height", 2400)
                images.put(imageObj)

                // 标注信息
                getAnnotations(imageData.filename).forEach { annotation ->
                    val annotationObj = JSONObject()
                    annotationObj.put("id", annotationId++)
                    annotationObj.put("image_id", imageId)
                    annotationObj.put("category_id", categoryMap[annotation.className] ?: 1)
                    annotationObj.put("bbox", JSONArray().apply {
                        put(annotation.rect.left)
                        put(annotation.rect.top)
                        put(annotation.rect.width())
                        put(annotation.rect.height())
                    })
                    annotationObj.put("area", annotation.rect.width() * annotation.rect.height())
                    annotationObj.put("iscrowd", 0)
                    annotations.put(annotationObj)
                }

                imageId++
            }

            cocoJson.put("images", images)
            cocoJson.put("annotations", annotations)

            // 保存到文件
            val exportFile = File(datasetDir, "coco_annotations.json")
            exportFile.writeText(cocoJson.toString(2))

            Log.d(TAG, "数据集已导出: ${exportFile.absolutePath}")
            exportFile
        } catch (e: Exception) {
            Log.e(TAG, "导出数据集失败", e)
            null
        }
    }

    /**
     * 获取统计信息
     */
    fun getStatistics(): DatasetStatistics {
        val allImages = getAllImages()
        val totalImages = allImages.size
        val labeledImages = allImages.count { it.isLabeled }
        val unlabeledImages = totalImages - labeledImages

        var totalAnnotations = 0
        allImages.forEach { imageData ->
            if (imageData.isLabeled) {
                totalAnnotations += getAnnotations(imageData.filename).size
            }
        }

        return DatasetStatistics(
            totalImages = totalImages,
            labeledImages = labeledImages,
            unlabeledImages = unlabeledImages,
            totalAnnotations = totalAnnotations
        )
    }

    /**
     * 图片数据
     */
    data class ImageData(
        val filename: String,
        val path: String,
        val isLabeled: Boolean,
        val timestamp: Long
    )

    /**
     * 标注数据
     */
    data class Annotation(
        val className: String,
        val rect: Rect
    )

    /**
     * 统计数据
     */
    data class DatasetStatistics(
        val totalImages: Int,
        val labeledImages: Int,
        val unlabeledImages: Int,
        val totalAnnotations: Int
    )
}
