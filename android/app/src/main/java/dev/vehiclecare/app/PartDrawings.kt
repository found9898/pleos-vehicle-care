package dev.vehiclecare.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A detail drawing on a fixed design grid, scaled to fit the space it is given (never past 1:1) and centred there.
 * Every part is a concept drawing, so [note] says what on it is to scale.
 */
abstract class DrawingView(context: Context, dark: Boolean, private val gridW: Int, protected val gridH: Int, private val note: String) : View(context) {
    protected val palette = Palette(dark)
    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    protected val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Fonts.of(700) }
    protected val box = RectF()

    protected abstract fun paint(canvas: Canvas)

    override fun onDraw(canvas: Canvas) {
        val s = min(1f, min(width.toFloat() / gridW, height.toFloat() / gridH))
        canvas.save(); canvas.translate((width - gridW * s) / 2, (height - gridH * s) / 2); canvas.scale(s, s)
        paint(canvas)
        label.textAlign = Paint.Align.LEFT; label.typeface = Fonts.of(400); label.textSize = 20f; label.color = palette.sub
        canvas.drawText(note, 36f, gridH - 6f, label)
        label.textAlign = Paint.Align.CENTER; label.typeface = Fonts.of(700)
        canvas.restore()
    }

    protected fun alpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    protected fun glow(canvas: Canvas, x: Float, y: Float, rx: Float, ry: Float, strength: Int, color: Int = palette.soon) {
        canvas.save(); canvas.translate(x, y); canvas.scale(1f, ry / rx)
        fill.shader = RadialGradient(0f, 0f, rx, alpha(color, strength), alpha(color, 0), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, rx, fill); fill.shader = null
        canvas.restore()
    }

    protected fun art(canvas: Canvas, res: Int, cx: Float, cy: Float, size: Float) {
        context.getDrawable(res)!!.apply { setBounds((cx - size / 2).toInt(), (cy - size / 2).toInt(), (cx + size / 2).toInt(), (cy + size / 2).toInt()); draw(canvas) }
    }

    /** A small pill naming a value on the drawing; [x] is its left, right or centre by [align]. */
    protected fun tag(canvas: Canvas, text: String, x: Float, y: Float, align: Paint.Align, strong: Boolean = true) {
        label.textSize = 24f; label.typeface = Fonts.of(if (strong) 700 else 400); label.color = palette.title
        val w = label.measureText(text) + 32
        val left = when (align) { Paint.Align.LEFT -> x; Paint.Align.RIGHT -> x - w; else -> x - w / 2 }.coerceIn(4f, 926f - w)
        box.set(left, y, left + w, y + 42)
        fill.color = palette.card; canvas.drawRoundRect(box, 21f, 21f, fill)
        line.color = palette.divider; line.strokeWidth = 1f; line.pathEffect = null; canvas.drawRoundRect(box, 21f, 21f, line)
        canvas.drawText(text, left + w / 2, y + 30, label)
        label.typeface = Fonts.of(700)
    }
}

/**
 * The part inside a ring that shows the interval used: [used] from the reading, [added] on top in orange for what the
 * car's data counted. [top] prints the distance left inside the ring; [usedTag], [addedTag] sit beside their arcs.
 */
class RingDrawing(context: Context, dark: Boolean, private val artRes: Int, private val artSize: Float, private val used: Float, private val added: Float,
                  private val top: String?, private val usedTag: String?, private val addedTag: String?, private val under: String?, description: String, tall: Boolean = false)
    : DrawingView(context, dark, 930, if (tall) 660 else 510, "개념도") {
    private val cx = 465f; private val cy = if (tall) 320f else 246f; private val r = if (tall) 278f else 212f
    private val artScale = r / 212f

    init { contentDescription = description }

    private fun arc(canvas: Canvas, share: Float, color: Int) {
        if (share <= 0) return
        line.color = color; line.strokeWidth = 22f
        box.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(box, -90f, 360 * share.coerceAtMost(1f), false, line)
    }

    private fun beside(canvas: Canvas, text: String, share: Float) {
        val a = 2 * Math.PI * share
        val s = sin(a).toFloat(); val c = cos(a).toFloat()
        val y = (cy - (r + 26) * c - 21).coerceIn(4f, gridH - 90f)
        tag(canvas, text, cx + (r + 26) * s, y, if (s >= 0) Paint.Align.LEFT else Paint.Align.RIGHT)
    }

    override fun paint(canvas: Canvas) {
        if (added > 0) glow(canvas, cx, cy, r + 18, r + 18, 30)
        line.pathEffect = null; line.color = palette.track; line.strokeWidth = 22f; canvas.drawCircle(cx, cy, r, line)
        arc(canvas, used + added, palette.soon)
        arc(canvas, used, palette.primary)
        art(canvas, artRes, cx, cy + if (top != null) 22f else 0f, artSize * artScale)
        if (top != null) {
            line.color = palette.title; line.strokeWidth = 4f; canvas.drawLine(cx, cy - r - 18, cx, cy - r + 18, line)
            label.textSize = 24f; label.color = palette.title; canvas.drawText(top, cx, cy - r + 60, label)
        }
        usedTag?.let { beside(canvas, it, min(used, 1f) / 2) }
        if (added > .02f) addedTag?.let { beside(canvas, it, min(used, 1f) + min(added, 1 - min(used, 1f)) / 2) }
        under?.let { tag(canvas, it, cx, cy + r - 2, Paint.Align.CENTER) }
    }
}

/** The blades between two circles: dashed for the sound of the new blade, orange for now, drawn at the true amplitude ratio. */
class WiperDrawing(context: Context, dark: Boolean, db: Double, private val tall: Boolean = false) : DrawingView(context, dark, 930, if (tall) 660 else 510, "개념도 · 두 원의 크기 비는 소리 크기 비") {
    private val ratio = 10.0.pow(db / 20).toFloat()
    private val text = String.format(java.util.Locale.KOREA, "%.2f", ratio)
    private val art = if (dark) R.drawable.part_wiper_night else R.drawable.part_wiper_day

    init { contentDescription = "작동 소리가 새 블레이드 때의 ${text}배" }

    override fun paint(canvas: Canvas) {
        val cx = 465f; val cy = if (tall) 320f else 248f
        val outer = min(if (tall) 288f else 226f, (if (tall) 190f else 148f) * ratio); val inner = maxOf(80f, outer / ratio)
        glow(canvas, cx, cy, outer + 24, outer + 24, 30)
        line.pathEffect = null; line.color = palette.soon; line.strokeWidth = 10f; canvas.drawCircle(cx, cy, outer, line)
        line.color = palette.sub; line.strokeWidth = 4f; line.pathEffect = DashPathEffect(floatArrayOf(8f, 7f), 0f)
        canvas.drawCircle(cx, cy, inner, line); line.pathEffect = null
        art(canvas, art, cx, cy, if (tall) 500f else 390f)
        tag(canvas, "지금 ${text}배", cx + outer * .71f + 8, cy - outer * .71f - 34, Paint.Align.LEFT)
        tag(canvas, "새 블레이드 때", cx - inner * .71f - 54, cy + inner * .71f + 16, Paint.Align.RIGHT, false)
    }
}

/** The filter with dust over the pleats: exactly [share] of the pleat area, in a fine grain that does not change between frames. */
class FilterDrawing(context: Context, private val isDark: Boolean, private val share: Double?, tagText: String?, tall: Boolean = false)
    : DrawingView(context, isDark, 930, if (tall) 660 else 510, "개념도 · 먼지 면적은 추정 비율") {
    private val size = if (tall) 640f else 500f; private val cx = 465f; private val cy = if (tall) 318f else 248f
    private val text = tagText
    private val points: FloatArray
    private val count: Int

    init {
        contentDescription = tagText ?: "실내 공기 필터"
        val k = size / 836; val x0 = cx - size / 2; val y0 = cy - size / 2
        val poly = listOf(44f to 322f, 590f to 146f, 800f to 424f, 214f to 612f).map { (x, y) -> x0 + x * k to y0 + y * k }
        val path = Path().apply { moveTo(poly[0].first, poly[0].second); poly.drop(1).forEach { lineTo(it.first, it.second) }; close() }
        val bounds = RectF().also { path.computeBounds(it, true) }
        val inside = android.graphics.Region().apply { setPath(path, android.graphics.Region(bounds.left.toInt() - 1, bounds.top.toInt() - 1, bounds.right.toInt() + 1, bounds.bottom.toInt() + 1)) }
        val rng = java.util.Random(18)
        val cells = mutableListOf<Triple<Float, Float, Float>>()
        var y = bounds.top
        while (y < bounds.bottom) {
            var x = bounds.left
            while (x < bounds.right) {
                if (inside.contains(x.toInt(), y.toInt())) cells += Triple(rng.nextFloat() + .22f * (sin(x * .019f + 1f) + sin(y * .031f + 2f) + sin((x - y) * .011f)) / 3, x + rng.nextFloat() * CELL, y + rng.nextFloat() * CELL)
                x += CELL
            }
            y += CELL
        }
        cells.sortBy { it.first }
        count = (cells.size * (share ?: 0.0).coerceIn(0.0, 1.0)).toInt()
        points = FloatArray(count * 2).also { p -> for (i in 0 until count) { p[i * 2] = cells[i].second; p[i * 2 + 1] = cells[i].third } }
    }

    override fun paint(canvas: Canvas) {
        art(canvas, if (isDark) R.drawable.part_filter_night else R.drawable.part_filter_day, cx, cy, size)
        line.color = alpha(palette.sub, 210); line.strokeWidth = CELL + .2f; line.pathEffect = null
        canvas.drawPoints(points, 0, count * 2, line)
        text?.let { tag(canvas, it, cx + size * .3f, cy + size * .37f, Paint.Align.LEFT) }
    }

    private companion object { const val CELL = 2f }
}

/**
 * Front and rear tread in two lenses, to scale, over the car. The app tracks one figure per axle, so both wheels of an axle
 * count together. [compact] sets the lenses beside the car for the screen that also lists shops; [car] false leaves the
 * lenses alone for a model with no drawing. A null reading draws the lens with its lines and no ribs.
 */
class TireDrawing(context: Context, dark: Boolean, private val frontMm: Double?, private val rearMm: Double?, private val newMm: Double, private val limitMm: Double,
                  private val alert: Boolean, private val compact: Boolean, private val car: Boolean)
    : DrawingView(context, dark, 930, if (compact) 510 else if (car) 780 else 460, "개념도 · 홈 깊이만 실제 비율") {
    private val drawing = context.getDrawable(if (dark) R.drawable.vehicle_tucson_xray_night else R.drawable.vehicle_tucson_xray_day)!!
    private val color = if (alert) palette.soon else palette.ok
    private val clip = Path()

    init {
        contentDescription = if (frontMm != null && rearMm != null) "타이어 홈 깊이 추정 앞 약 ${mm(frontMm)} mm, 뒤 약 ${mm(rearMm)} mm, ${mm(limitMm)} mm면 확인" else "타이어 앞뒤 축"
    }

    private fun mm(v: Double) = String.format(java.util.Locale.KOREA, "%.1f", v)

    private fun lens(canvas: Canvas, cx: Float, cy: Float, r: Float, depth: Double?, word: String) {
        val k = r * 1.03f / newMm.toFloat()
        val base = cy + r * .6f; val new = base - newMm.toFloat() * k; val limit = base - limitMm.toFloat() * k
        fill.color = palette.fill; canvas.drawCircle(cx, cy, r, fill)
        canvas.save(); clip.reset(); clip.addCircle(cx, cy, r, Path.Direction.CW); canvas.clipPath(clip)
        fill.color = palette.solid; canvas.drawRect(cx - r, base, cx + r, cy + r, fill)
        if (depth != null) {
            val top = base - depth.toFloat().coerceIn(0f, newMm.toFloat()) * k
            fill.color = palette.rib
            listOf(-1f to .59f, -.25f to .49f, .48f to .59f).forEach { (from, w) -> canvas.drawRect(cx + from * r, top, cx + (from + w) * r, base, fill) }
        }
        line.color = palette.sub; line.strokeWidth = 2f; line.pathEffect = DashPathEffect(floatArrayOf(7f, 6f), 0f)
        canvas.drawLine(cx - r, new, cx + r, new, line); line.pathEffect = null
        line.color = palette.title; line.strokeWidth = 3f; canvas.drawLine(cx - r, limit, cx + r, limit, line)
        canvas.restore()
        line.color = color; line.strokeWidth = 3f; canvas.drawCircle(cx, cy, r, line)
        label.textSize = 24f; label.color = palette.title
        canvas.drawText(depth?.let { "$word 약 ${mm(it)} mm" } ?: word, cx, cy - r - 12, label)
    }

    private fun wheel(canvas: Canvas, x: Float, y: Float, rx: Float, ry: Float, hidden: Boolean) {
        box.set(x - rx, y - ry, x + rx, y + ry); line.color = color; line.strokeWidth = 3f
        line.pathEffect = if (hidden) DashPathEffect(floatArrayOf(7f, 6f), 0f) else null
        canvas.drawOval(box, line); line.pathEffect = null
    }

    private fun axle(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        line.color = color; line.strokeWidth = 2f; line.pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
        canvas.drawLine(x1, y1, x2, y2, line); line.pathEffect = null
    }

    override fun paint(canvas: Canvas) {
        val legendY = gridH - 42f
        if (!car) {
            lens(canvas, 285f, 210f, 150f, frontMm, "앞"); lens(canvas, 645f, 210f, 150f, rearMm, "뒤")
        } else {
            val k = if (compact) .335f else .42f; val ox = if (compact) 6f else 92f; val oy = if (compact) 70f else 306f
            val sx = { x: Float -> ox + x * k }; val sy = { y: Float -> oy + y * k }
            val nx = sx(1108f); val ny = sy(708f); val fx = sx(262f); val fy = sy(748f); val rx = sx(1478f); val ry = sy(698f); val bx = sx(775f); val by = sy(796f)
            val w = k / .42f
            drawing.setBounds(ox.toInt(), oy.toInt(), (ox + 1674 * k).toInt(), (oy + 940 * k).toInt()); drawing.draw(canvas)
            glow(canvas, nx, ny, 105 * w, 120 * w, if (alert) 60 else 36, color); glow(canvas, rx, ry, 60 * w, 100 * w, if (alert) 50 else 30, color)
            val r = if (compact) 92f else 122f
            val (ax, ay) = if (compact) 720f to 128f else 330f to 170f
            val (cx, cy) = if (compact) 720f to 362f else 690f to 170f
            line.color = color; line.strokeWidth = 2f; line.pathEffect = null
            if (compact) { canvas.drawLine(ax - r * .9f, ay + r * .4f, nx, ny - 60 * w, line); canvas.drawLine(cx - r, cy, rx + 26 * w, ry - 20 * w, line) }
            else { canvas.drawLine(ax + 60, ay + r - 16, nx, ny - 76, line); canvas.drawLine(cx + 10, cy + r, rx, ry - 64, line) }
            axle(canvas, fx, fy, nx, ny); axle(canvas, bx, by, rx, ry)
            wheel(canvas, nx, ny, 56 * w, 77 * w, false); wheel(canvas, fx, fy, 40 * w, 60 * w, true)
            wheel(canvas, rx, ry, 32 * w, 64 * w, false); wheel(canvas, bx, by, 33 * w, 50 * w, true)
            lens(canvas, ax, ay, r, frontMm, "앞"); lens(canvas, cx, cy, r, rearMm, "뒤")
        }
        label.textAlign = Paint.Align.LEFT; label.typeface = Fonts.of(400); label.textSize = 22f; label.color = palette.sub
        line.color = palette.sub; line.strokeWidth = 2f; line.pathEffect = DashPathEffect(floatArrayOf(7f, 6f), 0f)
        canvas.drawLine(36f, legendY - 8, 84f, legendY - 8, line); line.pathEffect = null
        val first = "새 타이어 ${mm(newMm)} mm"
        canvas.drawText(first, 96f, legendY, label)
        val x = 96 + label.measureText(first) + 32
        line.color = palette.title; line.strokeWidth = 3f; canvas.drawLine(x, legendY - 8, x + 48, legendY - 8, line)
        canvas.drawText("${mm(limitMm)} mm면 확인", x + 60, legendY, label)
        label.textAlign = Paint.Align.CENTER; label.typeface = Fonts.of(700)
        if (frontMm == null && rearMm == null) { label.textSize = 22f; label.color = palette.body; canvas.drawText("타이어를 단 때와 주행거리를 넣으면 홈 깊이를 추정해요", 620f, legendY, label) }
    }
}
