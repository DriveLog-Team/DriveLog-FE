package kr.drivelog.app

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kr.drivelog.app.Palette.blue
import kr.drivelog.app.Palette.hex
import kr.drivelog.app.Palette.ink
import kr.drivelog.app.Palette.line

internal object Palette {
    val blue = Color.rgb(22, 100, 232)
    val ink = Color.rgb(17, 23, 34)
    val muted = Color.rgb(116, 128, 142)
    val line = Color.rgb(227, 231, 237)
    val soft = Color.rgb(248, 250, 252)
    val green = Color.rgb(29, 146, 95)

    fun hex(value: String) = Color.parseColor(value)
}

/** Positions native views in the Figma reference space, adapting to the available app window. */
internal class DesignFrame
@JvmOverloads
constructor(
    context: Context,
    val sx: Float = 1f,
    val sy: Float = 1f,
    private val designAssets: DesignAssets = DesignAssets(context.assets),
) : FrameLayout(context) {
    fun place(view: View, x: Int, y: Int, width: Int, height: Int) {
        addView(
            view,
            LayoutParams((width * sx).toInt(), (height * sy).toInt()).apply {
                leftMargin = (x * sx).toInt()
                topMargin = (y * sy).toInt()
            },
        )
    }

    fun surface(color: Int, radius: Int = 0, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * minOf(sx, sy)
            stroke?.let { setStroke(maxOf(1, sx.toInt()), it) }
        }

    fun text(
        value: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        size: Int = 16,
        color: Int = ink,
        weight: Int = 400,
        center: Boolean = false,
    ): TextView {
        return TextView(context).apply {
            text = value
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size * minOf(sx, sy))
            typeface =
                when {
                    weight >= 800 -> designAssets.heavy
                    weight >= 600 -> designAssets.bold
                    else -> designAssets.regular
                }
            includeFontPadding = false
            gravity = if (center) Gravity.CENTER else Gravity.CENTER_VERTICAL
            place(this, x, y, w, h)
        }
    }

    fun box(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        color: Int = Color.WHITE,
        radius: Int = 18,
        stroke: Int? = null,
    ): View =
        View(context).also {
            it.background = surface(color, radius, stroke)
            place(it, x, y, w, h)
        }

    fun icon(name: String, x: Int, y: Int, size: Int = 24, color: Int = blue): View =
        DesignIcon(context, name, color).also { place(it, x, y, size, size) }

    fun button(
        value: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int = 68,
        primary: Boolean = true,
        action: () -> Unit,
    ): TextView =
        text(value, x, y, w, h, 18, if (primary) Color.WHITE else ink, 700, true).apply {
            background =
                surface(if (primary) blue else Color.WHITE, 16, if (primary) null else line)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    fun image(name: String, x: Int, y: Int, w: Int, h: Int, radius: Int = 0): ImageView =
        ImageView(context).apply {
            val bmp = designAssets.image(name)
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = surface(Color.WHITE, radius)
            clipToOutline = radius > 0
            place(this, x, y, w, h)
        }

    fun eyebrow(value: String, x: Int, y: Int, w: Int = 600) =
        text(value, x, y, w, 28, 12, blue, 800).apply { letterSpacing = .16f }

    fun tag(value: String, x: Int, y: Int): TextView {
        val funny = value == "Funny"
        return text(value, x, y, 78, 32, 11, hex(if (funny) "#D47836" else "#3776D0"), 700, true)
            .apply { background = surface(hex(if (funny) "#FFF1E6" else "#EAF2FF"), 18) }
    }
}

/** Small native vector icons following the original Figma paths. */
internal class DesignIcon
@JvmOverloads
constructor(
    context: Context,
    private val name: String = "play",
    private val tint: Int = Palette.blue,
) : View(context) {
    private val p =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            strokeWidth = 1.8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val path = Path()

    private fun Canvas.line(vararg values: Float) {
        path.reset()
        path.moveTo(values[0], values[1])
        for (i in 2 until values.size step 2) path.lineTo(values[i], values[i + 1])
        drawPath(path, p)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width, height) / 24f
        canvas.save()
        canvas.translate((width - scale * 24) / 2, (height - scale * 24) / 2)
        canvas.scale(scale, scale)
        with(canvas) {
            when (name) {
                "play" -> {
                    p.style = Paint.Style.FILL
                    line(9f, 6f, 18f, 12f, 9f, 18f, 9f, 6f)
                }
                "pause" -> {
                    line(8f, 6f, 8f, 18f)
                    line(16f, 6f, 16f, 18f)
                }
                "back" -> line(15f, 5f, 8f, 12f, 15f, 19f)
                "arrow" -> {
                    line(4f, 12f, 20f, 12f)
                    line(14f, 6f, 20f, 12f, 14f, 18f)
                }
                "clock" -> {
                    drawCircle(12f, 12f, 9f, p)
                    line(12f, 6f, 12f, 12f, 16f, 14f)
                }
                "camera" -> {
                    drawRoundRect(3f, 6f, 21f, 20f, 3f, 3f, p)
                    line(8f, 6f, 10f, 3f, 14f, 3f, 16f, 6f)
                    drawCircle(12f, 13f, 3.5f, p)
                }
                "mic" -> {
                    drawRoundRect(9f, 3f, 15f, 15f, 3f, 3f, p)
                    drawArc(5f, 5f, 19f, 19f, 0f, 180f, false, p)
                    line(12f, 19f, 12f, 22f)
                    line(8f, 22f, 16f, 22f)
                }
                "spark" ->
                    line(
                        12f,
                        2f,
                        15f,
                        9f,
                        22f,
                        12f,
                        15f,
                        15f,
                        12f,
                        22f,
                        9f,
                        15f,
                        2f,
                        12f,
                        9f,
                        9f,
                        12f,
                        2f,
                    )
                "download" -> {
                    line(12f, 2f, 12f, 16f)
                    line(7f, 11f, 12f, 16f, 17f, 11f)
                    line(4f, 21f, 20f, 21f)
                }
                "edit" -> {
                    line(4f, 20f, 5f, 15f, 17f, 3f, 21f, 7f, 9f, 19f, 4f, 20f)
                    line(14f, 6f, 18f, 10f)
                }
                "volume" -> {
                    line(3f, 9f, 7f, 9f, 13f, 4f, 13f, 20f, 7f, 15f, 3f, 15f, 3f, 9f)
                    drawArc(9f, 5f, 23f, 19f, -60f, 120f, false, p)
                }
                "grid" ->
                    for (x in 3..14 step 11) for (y in 3..14 step 11) drawRoundRect(
                        x.toFloat(),
                        y.toFloat(),
                        x + 7f,
                        y + 7f,
                        1f,
                        1f,
                        p,
                    )
                "car" -> {
                    line(3f, 17f, 5f, 9f, 7f, 6f, 17f, 6f, 19f, 9f, 21f, 17f, 3f, 17f)
                    line(5f, 17f, 5f, 20f)
                    line(19f, 17f, 19f, 20f)
                }
            }
        }
        p.style = Paint.Style.STROKE
        canvas.restore()
    }
}

internal class LogoMark(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.blue }
    private val paths =
        listOf(12f, 23f, 18f).mapIndexed { i, h ->
            val x = i * 11f + 2
            Path().apply {
                moveTo(x, 27f)
                lineTo(x + 5, 27 - h)
                lineTo(x + 12, 27 - h)
                lineTo(x + 7, 27f)
                close()
            }
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(width / 36f, height / 30f)
        paths.forEach { canvas.drawPath(it, paint) }
        canvas.restore()
    }
}
