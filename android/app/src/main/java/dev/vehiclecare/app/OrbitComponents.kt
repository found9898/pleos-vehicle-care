package dev.vehiclecare.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object PartArt {
    fun resource(part: Part) = when (part) {
        Part.WIPER -> R.drawable.consumable_wiper
        Part.ENGINE_OIL -> R.drawable.consumable_oil
        Part.BRAKE_PAD -> R.drawable.consumable_brake
        Part.TIRE -> R.drawable.consumable_tire
        Part.HVAC -> R.drawable.consumable_hvac
        Part.CABIN_FILTER -> R.drawable.consumable_filter
    }
    fun title(part: Part) = when (part) {
        Part.WIPER -> "와이퍼"
        Part.ENGINE_OIL -> "엔진오일·필터"
        Part.BRAKE_PAD -> "브레이크 패드"
        Part.TIRE -> "타이어"
        Part.HVAC -> "냉방·난방"
        Part.CABIN_FILTER -> "실내 공기 필터"
    }
    /** Fraction of the configured interval used, never remaining physical part life. */
    fun fraction(plan: MaintenancePlan?, record: ServiceRecord?, mileage: Mileage?, today: LocalDate): Float? {
        if (plan == null || record == null) return null
        val values = mutableListOf<Float>()
        if (plan.intervalMonths != null && record.date != null && !record.date.isAfter(today)) {
            val total = ChronoUnit.DAYS.between(record.date, record.date.plusMonths(plan.intervalMonths.toLong()))
            if (total > 0) values += ChronoUnit.DAYS.between(record.date, today).toFloat() / total
        }
        if (plan.part.supportsDistance && plan.intervalKm != null && plan.intervalKm > 0 && record.odometerKm != null && mileage != null &&
            !mileage.updatedOn.isAfter(today) && mileage.km >= record.odometerKm) {
            values += (mileage.km - record.odometerKm).toFloat() / plan.intervalKm
        }
        return values.maxOrNull()?.coerceIn(0f, 1f)
    }
}

/** A floating product cutout with a real schedule ring and accessible text. No card surface. */
class PartOrbitNode(
    context: Context,
    val part: Part,
    private val status: DueStatus,
    private val progress: Float?,
    private val caption: String,
    private val drafting: Boolean,
    private val ringDp: Int,
    private val showLabels: Boolean = true,
    /** The job and how far away it is ("교체까지 332 km"); the ring never prints a share. */
    private val label: String? = null,
    /** The state's colour when it is not the plain status colour (an inspection that is due asks in orange, not red). */
    private val tone: Int? = null,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val palette = Palette(dark)
    private val color = tone ?: when (status) {
        DueStatus.UPCOMING -> palette.ok
        DueStatus.SOON -> palette.soon
        DueStatus.DUE -> palette.due
        else -> palette.sub
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    val art = ImageView(context).apply {
        setImageResource(PartArt.resource(part))
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val name = TextView(context).apply {
        text = PartArt.title(part); textSize = if (ringDp >= 150) TypeScale.LABEL.toFloat() else TypeScale.LABEL_SMALL.toFloat()
        setTextColor(palette.title)
        typeface = Fonts.of(700)
        gravity = android.view.Gravity.CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val state = TextView(context).apply {
        text = if (drafting) "이어서 기록" else label ?: when (status) {
            DueStatus.UNREGISTERED -> "미등록"
            DueStatus.UPCOMING -> "주기 여유"
            DueStatus.NEEDS_INFO -> "이력 대기"
            else -> status.label
        }
        textSize = if (ringDp >= 150) TypeScale.LABEL_SMALL.toFloat() else 18f; typeface = Fonts.of(400)
        // The ring carries the state's colour; the label takes it only when the part is due.
        setTextColor(if (status == DueStatus.DUE) color else palette.sub); gravity = android.view.Gravity.CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val ringSize get() = dp(ringDp.toFloat()).toInt()
    init {
        setWillNotDraw(false)
        isClickable = true; isFocusable = true
        contentDescription = "${part.title}, ${state.text}, $caption"
        addView(art)
        if (showLabels) { addView(name); addView(state) }
    }
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w); val height = MeasureSpec.getSize(h)
        setMeasuredDimension(width, height)
        val imageSize = (ringSize * .83f).toInt()
        art.measure(MeasureSpec.makeMeasureSpec(imageSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(imageSize, MeasureSpec.EXACTLY))
        if (showLabels) {
            name.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(48f).toInt(), MeasureSpec.AT_MOST))
            state.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(38f).toInt(), MeasureSpec.AT_MOST))
        }
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val x = (width - art.measuredWidth) / 2; val y = (ringSize - art.measuredHeight) / 2
        art.layout(x, y, x + art.measuredWidth, y + art.measuredHeight)
        if (showLabels) {
            val nameTop = ringSize + dp(9f).toInt()
            name.layout(0, nameTop, width, nameTop + name.measuredHeight)
            state.layout(0, name.bottom + dp(3f).toInt(), width, name.bottom + dp(3f).toInt() + state.measuredHeight)
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = ringSize / 2f - dp(4f); val cx = width / 2f; val cy = ringSize / 2f
        paint.style = Paint.Style.FILL
        paint.color = palette.card
        paint.alpha = if (isPressed) 200 else 255
        if (palette.stageCenter != palette.stageEdge) paint.shader = RadialGradient(cx, cy, radius - dp(5f), palette.stageCenter, palette.stageEdge, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius - dp(5f), paint)
        paint.shader = null
        paint.alpha = 255; paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(4f)
        paint.color = palette.fill
        track.set(cx - radius, cy - radius, cx + radius, cy + radius)
        if (progress == null) {
            // No record yet: a dashed ring in the secondary text colour, visible on the canvas (the track grey was 1.09:1).
            paint.color = palette.sub; paint.alpha = 110
            paint.pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(7f)), 0f)
            canvas.drawCircle(cx, cy, radius, paint); paint.pathEffect = null; paint.alpha = 255
        } else {
            canvas.drawCircle(cx, cy, radius, paint)
            // The ring fills with what is used, like the bars on the detail screen: the fuller, the sooner.
            paint.color = color; paint.strokeWidth = dp(4f); paint.strokeCap = Paint.Cap.ROUND
            canvas.drawArc(track, -90f, maxOf(360f * progress!!, 2f), false, paint)
            paint.strokeCap = Paint.Cap.BUTT
        }
        if (drafting) {
            paint.style = Paint.Style.FILL; paint.color = palette.primary
            canvas.drawCircle(cx + radius * .70f, cy - radius * .70f, dp(6f), paint)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(.95f).scaleY(.95f).setDuration(100).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

/** Where a value sits against a reference band, on a 0..max scale with the value clamped to the end. */
/**
 * A 240° arc gauge for one key figure. [low]..[high] is the estimate's range on a 0..1 scale: the low end fills solid
 * and the span to the high end lighter, so a range never reads as a single precise value. [bandLow]..[bandHigh] marks a
 * published reference on the same scale. Without a fraction there is no scale to draw, so only the number shows.
 */
class ArcGauge(
    context: Context,
    private val low: Float?,
    private val high: Float?,
    private val value: String,
    private val unit: String,
    private val color: Int,
    private val dark: Boolean,
    private val bandLow: Float? = null,
    private val bandHigh: Float? = null,
) : View(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.of(700); textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.of(400); textAlign = Paint.Align.CENTER }
    private val arc = RectF()
    private val palette = Palette(dark)
    init { contentDescription = "$value $unit" }

    override fun onDraw(canvas: Canvas) {
        val stroke = dp(10f)
        val size = minOf(width, height).toFloat()
        val r = size / 2 - stroke
        val cx = width / 2f; val cy = height / 2f + dp(6f)
        arc.set(cx - r, cy - r, cx + r, cy + r)
        val start = 150f; val sweep = 240f
        paint.strokeWidth = stroke
        paint.color = palette.fill
        // An empty track would read as zero; a figure with no scale is drawn as a number only.
        if (low != null || bandLow != null) canvas.drawArc(arc, start, sweep, false, paint)
        if (bandLow != null && bandHigh != null) {
            paint.strokeCap = Paint.Cap.BUTT
            paint.color = palette.band
            canvas.drawArc(arc, start + sweep * bandLow.coerceIn(0f, 1f), sweep * (bandHigh - bandLow).coerceIn(0f, 1f), false, paint)
            paint.strokeCap = Paint.Cap.ROUND
        }
        if (low != null) {
            val lo = low.coerceIn(0f, 1f); val hi = (high ?: low).coerceIn(lo, 1f)
            paint.color = color; paint.alpha = 90
            if (hi > lo) canvas.drawArc(arc, start, sweep * hi, false, paint)
            paint.alpha = 255
            // A sliver still shows where the arc starts, so a tiny share does not look like no data.
            canvas.drawArc(arc, start, maxOf(sweep * lo, 1.5f), false, paint)
        }
        big.color = palette.title
        big.textSize = dp(if (value.length > 7) 23f else if (value.length > 4) 30f else 38f)
        canvas.drawText(value, cx, cy + big.textSize * .32f, big)
        small.color = palette.sub
        small.textSize = dp(14f)
        if (unit.isNotEmpty() && value != "-") canvas.drawText(unit, cx, cy + big.textSize * .32f + dp(22f), small)
    }
}

/**
 * A thin horizontal meter: [fraction] filled in [fill], then [extra] in a lighter [fill] right after it (what the car's
 * data counted on top of the plain reading), with an optional reference tick at [marker].
 */
class MeterBar(context: Context, private val fraction: Float, private val fill: Int, private val track: Int, private val marker: Float?,
               private val extra: Float = 0f) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat(); val w = width.toFloat(); val r = h / 2
        paint.color = track; canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
        val f = fraction.coerceIn(0f, 1f); val e = extra.coerceIn(0f, 1f - f)
        if (e > 0) { paint.color = Color.argb(110, Color.red(fill), Color.green(fill), Color.blue(fill)); canvas.drawRoundRect(0f, 0f, maxOf(h, w * (f + e)), h, r, r, paint) }
        if (f > 0) { paint.color = fill; canvas.drawRoundRect(0f, 0f, maxOf(h, w * f), h, r, r, paint) }
        marker?.let { m ->
            val x = w * m.coerceIn(0f, 1f)
            paint.color = Color.argb(160, Color.red(fill), Color.green(fill), Color.blue(fill)); canvas.drawRect(x - 1.5f, -h, x + 1.5f, h * 2, paint)
            // Where the fill already covers the line, it is cut into the fill in the track colour so it stays visible.
            if (x < w * (f + e)) { paint.color = track; canvas.drawRect(x - 1.5f, 0f, x + 1.5f, h, paint) }
        }
    }
}
