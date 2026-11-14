package dev.vehiclecare.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * The brake detail's drawing: the home Tucson as a line drawing with the front axle lit, and a lens from the near wheel
 * into a cross-section of the pad. Only the pad's thickness is to scale; disc, backing plate and piston are schematic.
 * The app estimates front pads alone and cannot tell left from right, so both front wheels light up and the rear stays dark.
 * Laid out on a 930 x 510 design grid. [leftMm] null (no pad change on record) draws the lens with the lines alone, and
 * [showCar] false leaves the lens alone for a model with no drawing.
 */
class BrakeXrayView(context: Context, dark: Boolean, private val leftMm: Double?, private val newMm: Double, private val limitMm: Double, private val showCar: Boolean = true)
    : DrawingView(context, dark, 930, 510, "개념도 · 패드 두께만 실제 비율") {
    private val car = context.getDrawable(if (dark) R.drawable.vehicle_tucson_xray_night else R.drawable.vehicle_tucson_xray_day)!!
    private val strength = if (dark) 107 else 76
    private val lens = Path()
    private val left = leftMm?.let { format(it) }

    init {
        contentDescription = "앞바퀴 브레이크 2곳. " + (left?.let { "앞 패드 약 $it mm 남은 것으로 추정, ${format(limitMm)} mm면 교체" } ?: "패드 교체 기록이 있으면 남은 두께를 계산해요")
    }

    private fun format(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.KOREA, "%.1f", v)

    private fun disc(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, slots: Int, dashed: Boolean) {
        line.pathEffect = if (dashed) DashPathEffect(floatArrayOf(6f, 5f), 0f) else null
        box.set(cx - rx, cy - ry, cx + rx, cy + ry)
        fill.color = alpha(palette.soon, 40); fill.style = Paint.Style.FILL; canvas.drawOval(box, fill)
        line.color = palette.soon; line.strokeWidth = 2.5f; canvas.drawOval(box, line)
        line.pathEffect = null; line.strokeWidth = 2f
        for (i in 0 until slots) {
            val t = i.toDouble() / slots * 2 * Math.PI
            canvas.drawLine(cx + rx * .5f * cos(t).toFloat(), cy + ry * .5f * sin(t).toFloat(),
                cx + rx * .82f * cos(t + .28).toFloat(), cy + ry * .82f * sin(t + .28).toFloat(), line)
        }
        box.set(cx - rx * .34f, cy - ry * .34f, cx + rx * .34f, cy + ry * .34f)
        fill.color = palette.card; canvas.drawOval(box, fill); canvas.drawOval(box, line)
    }

    override fun paint(canvas: Canvas) {
        val k = .335f; val ox = 6f; val oy = 82f
        val nx = ox + 1108 * k; val ny = oy + 708 * k
        val fx = ox + 262 * k; val fy = oy + 748 * k
        val lx = if (showCar) 708f else 465f; val ly = if (showCar) 242f else 228f; val lr = 188f

        if (showCar) {
            car.setBounds(ox.toInt(), oy.toInt(), (ox + 1674 * k).toInt(), (oy + 940 * k).toInt()); car.draw(canvas)
            glow(canvas, nx, ny, 90f, 105f, strength)
            glow(canvas, fx, fy, 55f, 85f, strength * 7 / 10)
            // The lens opens from the near wheel.
            val wedge = Path().apply { moveTo(nx, ny - 44); lineTo(lx - 40, ly - lr + 4); lineTo(lx - 40, ly + lr - 4); lineTo(nx, ny + 44); close() }
            fill.color = alpha(palette.soon, 18); canvas.drawPath(wedge, fill)
            line.color = palette.soon; line.strokeWidth = 2f; line.pathEffect = null
            canvas.drawLine(nx, ny - 44, lx - 40, ly - lr + 4, line); canvas.drawLine(nx, ny + 44, lx - 40, ly + lr - 4, line)
            line.strokeWidth = 2.5f; line.pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
            canvas.drawLine(fx, fy, nx, ny, line); line.pathEffect = null
            disc(canvas, fx, fy, 13f, 31f, 6, true)
            disc(canvas, nx, ny, 25f, 37f, 10, false)
            // The caliper on the near disc.
            box.set(nx - 25, ny - 37, nx + 25, ny + 37)
            line.strokeWidth = 10f; canvas.drawArc(box, -76f, 84f, false, line)
        }

        // The pad, to scale: disc face on the left, the worn part dashed on the right.
        fill.color = palette.fill; canvas.drawCircle(lx, ly, lr, fill)
        canvas.save()
        lens.reset(); lens.addCircle(lx, ly, lr, Path.Direction.CW); canvas.clipPath(lens)
        val x0 = lx - lr + 50; val mm = 198f / newMm.toFloat()
        val xLimit = x0 + limitMm.toFloat() * mm
        val thick = (leftMm ?: newMm).toFloat().coerceIn(0f, newMm.toFloat())
        val xLeft = x0 + thick * mm
        val xNew = x0 + newMm.toFloat() * mm
        fill.color = palette.solid; canvas.drawRect(lx - lr, ly - lr, x0, ly + lr, fill)
        if (leftMm != null) {
            fill.color = palette.rib; canvas.drawRect(x0, ly - 86, xLeft, ly + 86, fill)
            fill.color = palette.padZone; canvas.drawRect(x0, ly - 86, minOf(xLimit, xLeft), ly + 86, fill)
        }
        line.color = palette.sub; line.strokeWidth = 2f; line.pathEffect = DashPathEffect(floatArrayOf(7f, 6f), 0f)
        canvas.drawRect(if (leftMm != null) xLeft else x0, ly - 86, xNew, ly + 86, line); line.pathEffect = null
        fill.color = palette.solid; canvas.drawRect(xLeft + 22, ly - 62, xLeft + 222, ly + 62, fill)
        fill.color = palette.plate; canvas.drawRect(xLeft, ly - 112, xLeft + 22, ly + 112, fill)
        line.color = palette.title; line.strokeWidth = 3f; canvas.drawLine(xLimit, ly - 112, xLimit, ly + 112, line)
        canvas.restore()
        line.color = palette.soon; line.strokeWidth = 3f; canvas.drawCircle(lx, ly, lr, line)

        label.textSize = 20f; label.color = Color.WHITE
        // A thin pad has no room for its word; the sentence under the lens still says how much is left.
        fun inside(word: String, from: Float, to: Float) { if (label.measureText(word) + 8 <= to - from) canvas.drawText(word, (from + to) / 2, ly + 7, label) }
        if (leftMm != null) { inside("교체", x0, minOf(xLimit, xLeft)); inside("남은 패드", xLimit, xLeft) }
        label.textSize = 24f; label.color = palette.title
        canvas.drawText(left?.let { "약 $it mm 남음 · ${format(limitMm)} mm면 교체" } ?: "패드 교체 기록이 있으면 남은 두께를 계산해요", lx, ly + lr + 36, label)

        // The label under the axle.
        if (showCar) tag(canvas, "앞바퀴 2곳", ox + 330 * k + 40, ny + 74, Paint.Align.LEFT)
    }
}

/** The evidence bar: stacked shares from the left, widest first, and thin ticks that stand past the bar. */
class EvidenceBar(context: Context, private val shares: List<Pair<Float, Int>>, private val track: Int, private val ticks: List<Float> = emptyList(), private val tick: Int = track,
                  private val tickLabels: List<String> = emptyList(), private val labelColor: Int = tick) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat(); val bar = 14 * d; val roof = if (tickLabels.isEmpty()) 0f else 26 * d; val top = roof + (height - roof - bar) / 2; val r = bar / 2
        paint.color = track; canvas.drawRoundRect(0f, top, w, top + bar, r, r, paint)
        shares.sortedByDescending { it.first }.forEach { (share, color) ->
            if (share > 0) { paint.color = color; canvas.drawRoundRect(0f, top, maxOf(bar, w * share.coerceIn(0f, 1f)), top + bar, r, r, paint) }
        }
        paint.color = tick
        val half = 2 * resources.displayMetrics.density
        ticks.forEach { t -> val x = w * t.coerceIn(0f, 1f); canvas.drawRect(x - half, roof, x + half, height.toFloat(), paint) }
        paint.color = labelColor; paint.textSize = 20 * d; paint.typeface = Fonts.of(400); paint.textAlign = Paint.Align.CENTER
        tickLabels.forEachIndexed { i, label -> canvas.drawText(label, w * ticks[i].coerceIn(0f, 1f), roof - 4 * d, paint) }
    }
}
