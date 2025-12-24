package com.gamebot.ai.ui

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gamebot.ai.R
import com.gamebot.ai.data.DatasetManager
import com.gamebot.ai.service.GameBotAccessibilityService
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.io.File

/**
 * 图片标注Activity
 * 用于手动标注训练数据
 */
class ImageAnnotationActivity : AppCompatActivity() {

    private lateinit var annotationView: AnnotationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvImageIndex: TextView
    private lateinit var btnPrevious: Button
    private lateinit var btnNext: Button
    private lateinit var btnUndo: Button
    private lateinit var btnClear: Button
    private lateinit var btnSave: Button
    private lateinit var chipGroupCategories: ChipGroup
    private lateinit var progressBar: ProgressBar

    private var datasetManager: DatasetManager? = null
    private var imageList: List<DatasetManager.ImageData> = emptyList()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_annotation)

        initViews()
        setupListeners()
        loadData()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        annotationView = findViewById(R.id.annotationView)
        tvImageIndex = findViewById(R.id.tvImageIndex)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnUndo = findViewById(R.id.btnUndo)
        btnClear = findViewById(R.id.btnClear)
        btnSave = findViewById(R.id.btnSave)
        chipGroupCategories = findViewById(R.id.chipGroupCategories)
        progressBar = findViewById(R.id.progressBar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        btnPrevious.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                loadImage()
            }
        }

        btnNext.setOnClickListener {
            if (currentIndex < imageList.size - 1) {
                currentIndex++
                loadImage()
            }
        }

        btnUndo.setOnClickListener {
            annotationView.undo()
        }

        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("确定要清空当前图片的所有标注吗？")
                .setPositiveButton("确定") { _, _ ->
                    annotationView.clear()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        btnSave.setOnClickListener {
            saveAnnotations()
        }

        // 类别选择
        chipGroupCategories.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val checkedId = checkedIds[0]
                val category = when (checkedId) {
                    R.id.chipEnemy -> "enemy"
                    R.id.chipItem -> "item"
                    R.id.chipSkill -> "skill"
                    R.id.chipBoss -> "boss"
                    else -> "enemy"
                }
                annotationView.setCurrentCategory(category)
            }
        }
    }

    private fun loadData() {
        val service = GameBotAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "服务未运行", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        datasetManager = service.getDatasetManager()
        imageList = datasetManager?.getAllImages() ?: emptyList()

        if (imageList.isEmpty()) {
            Toast.makeText(this, "没有可标注的图片", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 默认从第一张未标注的图片开始
        currentIndex = imageList.indexOfFirst { !it.isLabeled }
        if (currentIndex == -1) {
            currentIndex = 0
        }

        loadImage()
    }

    private fun loadImage() {
        if (currentIndex < 0 || currentIndex >= imageList.size) return

        val imageData = imageList[currentIndex]

        // 更新索引显示
        tvImageIndex.text = "${currentIndex + 1} / ${imageList.size}"

        // 更新按钮状态
        btnPrevious.isEnabled = currentIndex > 0
        btnNext.isEnabled = currentIndex < imageList.size - 1

        // 加载图片
        try {
            val imageFile = File(imageData.path)
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

            if (bitmap != null) {
                annotationView.setImage(bitmap)

                // 加载已有的标注
                if (imageData.isLabeled) {
                    val annotations = datasetManager?.getAnnotations(imageData.filename) ?: emptyList()
                    val viewAnnotations = annotations.map { annotation ->
                        // 这里需要将图片坐标转换为View坐标
                        // 为简化，直接使用原始坐标（后续在保存时转换）
                        AnnotationView.BoundingBox(
                            android.graphics.RectF(annotation.rect),
                            annotation.className
                        )
                    }
                    annotationView.setAnnotations(viewAnnotations)
                } else {
                    annotationView.clear()
                }
            } else {
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "加载图片出错: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAnnotations() {
        if (currentIndex < 0 || currentIndex >= imageList.size) return

        val imageData = imageList[currentIndex]
        val viewAnnotations = annotationView.getAnnotations()

        if (viewAnnotations.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("没有标注")
                .setMessage("当前图片没有任何标注，是否仍要保存？")
                .setPositiveButton("保存") { _, _ ->
                    doSave(imageData.filename, viewAnnotations)
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        doSave(imageData.filename, viewAnnotations)
    }

    private fun doSave(filename: String, viewAnnotations: List<AnnotationView.BoundingBox>) {
        try {
            // 转换为图片坐标
            val annotations = viewAnnotations.map { viewBox ->
                val imageRect = annotationView.viewToImageCoordinates(viewBox)
                DatasetManager.Annotation(
                    className = viewBox.category,
                    rect = imageRect
                )
            }

            // 保存
            datasetManager?.saveAnnotation(filename, annotations)

            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()

            // 自动跳转到下一张未标注的图片
            val nextUnlabeledIndex = imageList.withIndex()
                .firstOrNull { (index, imageData) ->
                    index > currentIndex && !imageData.isLabeled
                }?.index ?: -1

            if (nextUnlabeledIndex != -1) {
                currentIndex = nextUnlabeledIndex
                loadImage()
            } else if (currentIndex < imageList.size - 1) {
                // 如果没有未标注的，就跳到下一张
                currentIndex++
                loadImage()
            } else {
                // 已经是最后一张了
                AlertDialog.Builder(this)
                    .setTitle("标注完成")
                    .setMessage("所有图片已标注完成")
                    .setPositiveButton("关闭") { _, _ ->
                        finish()
                    }
                    .show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        val annotations = annotationView.getAnnotations()
        if (annotations.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("有未保存的标注")
                .setMessage("当前图片有未保存的标注，是否保存？")
                .setPositiveButton("保存") { _, _ ->
                    saveAnnotations()
                    super.onBackPressed()
                }
                .setNegativeButton("不保存") { _, _ ->
                    super.onBackPressed()
                }
                .setNeutralButton("取消", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }
}
