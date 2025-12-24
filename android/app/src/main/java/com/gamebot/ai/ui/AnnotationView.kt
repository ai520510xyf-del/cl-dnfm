package com.gamebot.ai.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 图片标注自定义View
 * 支持在图片上绘制边界框
 */
class AnnotationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 显示的图片
    private var bitmap: Bitmap? = null

    // 图片绘制矩形（保持宽高比）
    private val imageRect = RectF()

    // 当前绘制的边界框
    private var currentBox: BoundingBox? = null

    // 已保存的边界框列表
    private val boundingBoxes = mutableListOf<BoundingBox>()

    // 当前选中的类别
    private var currentCategory = "enemy"

    // 画笔
    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        textSize = 40f
        color = Color.WHITE
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    // 类别颜色映射
    private val categoryColors = mapOf(
        "enemy" to Color.RED,
        "item" to Color.GREEN,
        "skill" to Color.BLUE,
        "boss" to Color.MAGENTA
    )

    // 类别中文名
    private val categoryNames = mapOf(
        "enemy" to "敌人",
        "item" to "物品",
        "skill" to "技能",
        "boss" to "Boss"
    )

    // 触摸起始点
    private var touchStartX = 0f
    private var touchStartY = 0f

    init {
        setBackgroundColor(Color.BLACK)
    }

    /**
     * 设置要标注的图片
     */
    fun setImage(bmp: Bitmap?) {
        bitmap = bmp
        calculateImageRect()
        invalidate()
    }

    /**
     * 设置当前类别
     */
    fun setCurrentCategory(category: String) {
        currentCategory = category
    }

    /**
     * 设置已有的标注
     */
    fun setAnnotations(annotations: List<BoundingBox>) {
        boundingBoxes.clear()
        boundingBoxes.addAll(annotations)
        invalidate()
    }

    /**
     * 获取所有标注
     */
    fun getAnnotations(): List<BoundingBox> {
        return boundingBoxes.toList()
    }

    /**
     * 撤销最后一个标注
     */
    fun undo() {
        if (boundingBoxes.isNotEmpty()) {
            boundingBoxes.removeAt(boundingBoxes.size - 1)
            invalidate()
        }
    }

    /**
     * 清空所有标注
     */
    fun clear() {
        boundingBoxes.clear()
        currentBox = null
        invalidate()
    }

    /**
     * 计算图片显示矩形（保持宽高比，居中显示）
     */
    private fun calculateImageRect() {
        val bmp = bitmap ?: return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val bmpWidth = bmp.width.toFloat()
        val bmpHeight = bmp.height.toFloat()

        if (viewWidth == 0f || viewHeight == 0f) return

        val viewRatio = viewWidth / viewHeight
        val bmpRatio = bmpWidth / bmpHeight

        if (bmpRatio > viewRatio) {
            // 图片更宽，以宽度为准
            val scaledWidth = viewWidth
            val scaledHeight = viewWidth / bmpRatio
            val top = (viewHeight - scaledHeight) / 2
            imageRect.set(0f, top, scaledWidth, top + scaledHeight)
        } else {
            // 图片更高，以高度为准
            val scaledHeight = viewHeight
            val scaledWidth = viewHeight * bmpRatio
            val left = (viewWidth - scaledWidth) / 2
            imageRect.set(left, 0f, left + scaledWidth, scaledHeight)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateImageRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制图片
        bitmap?.let {
            canvas.drawBitmap(it, null, imageRect, null)
        }

        // 绘制已保存的边界框
        boundingBoxes.forEach { box ->
            drawBoundingBox(canvas, box)
        }

        // 绘制当前正在绘制的边界框
        currentBox?.let {
            drawBoundingBox(canvas, it)
        }
    }

    /**
     * 绘制边界框
     */
    private fun drawBoundingBox(canvas: Canvas, box: BoundingBox) {
        val color = categoryColors[box.category] ?: Color.RED

        // 绘制半透明填充
        fillPaint.color = Color.argb(50, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRect(box.rect, fillPaint)

        // 绘制边框
        boxPaint.color = color
        canvas.drawRect(box.rect, boxPaint)

        // 绘制类别标签
        val label = categoryNames[box.category] ?: box.category
        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)

        val labelX = box.rect.left + 8
        val labelY = box.rect.top - 8

        // 标签背景
        fillPaint.color = color
        canvas.drawRect(
            labelX - 4,
            labelY - textBounds.height() - 4,
            labelX + textBounds.width() + 4,
            labelY + 4,
            fillPaint
        )

        // 标签文字
        canvas.drawText(label, labelX, labelY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        // 确保触摸在图片范围内
        if (!imageRect.contains(x, y) && event.action == MotionEvent.ACTION_DOWN) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = x
                touchStartY = y
                currentBox = BoundingBox(
                    RectF(x, y, x, y),
                    currentCategory
                )
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                currentBox?.let {
                    val left = min(touchStartX, x)
                    val top = min(touchStartY, y)
                    val right = max(touchStartX, x)
                    val bottom = max(touchStartY, y)

                    // 限制在图片范围内
                    it.rect.set(
                        max(left, imageRect.left),
                        max(top, imageRect.top),
                        min(right, imageRect.right),
                        min(bottom, imageRect.bottom)
                    )
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                currentBox?.let { box ->
                    // 只有当边界框足够大时才保存
                    val width = box.rect.width()
                    val height = box.rect.height()
                    if (width > 20 && height > 20) {
                        boundingBoxes.add(box)
                    }
                    currentBox = null
                    invalidate()
                }
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    /**
     * 将View坐标转换为图片坐标
     */
    fun viewToImageCoordinates(box: BoundingBox): Rect {
        val bmp = bitmap ?: return Rect()

        val scaleX = bmp.width / imageRect.width()
        val scaleY = bmp.height / imageRect.height()

        val left = ((box.rect.left - imageRect.left) * scaleX).toInt()
        val top = ((box.rect.top - imageRect.top) * scaleY).toInt()
        val right = ((box.rect.right - imageRect.left) * scaleX).toInt()
        val bottom = ((box.rect.bottom - imageRect.top) * scaleY).toInt()

        return Rect(left, top, right, bottom)
    }

    /**
     * 边界框数据类
     */
    data class BoundingBox(
        val rect: RectF,
        val category: String
    )
}
