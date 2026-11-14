package dev.vehiclecare.app

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.*
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

/** Home orbit order, clockwise from the top; the detail screen lines the other parts up in the same order. */
private val ORBIT = listOf(Part.ENGINE_OIL, Part.HVAC, Part.CABIN_FILTER, Part.TIRE, Part.BRAKE_PAD, Part.WIPER)

/** Where each part sits on the car picture, as a share of the picture. */
private val ANCHORS = mapOf(Part.ENGINE_OIL to (.35f to .40f), Part.HVAC to (.63f to .25f), Part.CABIN_FILTER to (.65f to .45f),
    Part.TIRE to (.91f to .78f), Part.BRAKE_PAD to (.69f to .82f), Part.WIPER to (.47f to .31f))

/** Recommended maintenance cycles with real vehicle mileage and explicit service history. */
class MainActivity : Activity() {
    private lateinit var store: CareStore
    private lateinit var root: FrameLayout
    private var route = "home"
    private var part = Part.WIPER
    private var draft = JSONObject()
    private var restricted = true
    private var restrictionReady = false
    private var car: Car? = null
    private var ux: CarUxRestrictionsManager? = null
    private val dialogs = mutableListOf<Dialog>()
    private var renderedDate = LocalDate.now()
    // Part images on the current screen, and where they sat on the previous one, so every part can fly to its new place.
    private val partArts = mutableMapOf<Part, View>()
    private var transitionOrigins: Map<Part, Rect> = emptyMap()
    // The parts that are not moving; they fade in where they belong instead of flying.
    private val fadeIns = mutableListOf<View>()
    private var stage: View? = null
    private var partTransition: ValueAnimator? = null
    private var mileageFeed: VehicleMileageFeed? = null
    private var mileageState = "차량 연결 중"
    private var mileageLive = false
    private val dark get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    // How far every popup darkens the screen behind it, the system permission prompt included.
    private val dim get() = if (dark) 0.75f else 0.45f
    private val scrim = android.graphics.drawable.ColorDrawable(Color.BLACK)
    private val bg get() = Palette(dark).canvas
    private val surface get() = Palette(dark).card
    private val ink get() = Palette(dark).title
    private val muted get() = Palette(dark).sub
    private val accent get() = Palette(dark).primary
    private val soft get() = Palette(dark).fill
    private val warning get() = Palette(dark).soon

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = CareStore(this)
        // The car names itself (make, model, powertrain, size); a known name picks the model's schedules and part sizes.
        VehicleIdentity.read(this)?.let { store.identify(it) }
        store.applyRecommendations()
        route = state?.getString("route") ?: when {
            intent.hasExtra("part") -> "detail"
            // The first launch asks for everything the car's readings need on one screen, before the owner meets a prompt mid-task.
            !onboarding.getBoolean("done", false) && (missingPermissions().isNotEmpty() || store.records.isEmpty()) -> "onboarding"
            else -> "home"
        }
        part = runCatching { Part.valueOf(state?.getString("part") ?: intent.getStringExtra("part") ?: "WIPER") }.getOrDefault(Part.WIPER)
        draft = state?.getString("draft")?.let(::JSONObject) ?: JSONObject()
        VehicleSignals.start(this); VehicleSignals.subscribe(signalsChanged)
        render(); connectRestrictions(); Reminders.schedule(this)
    }
    private val signalsChanged: () -> Unit = { if (!isDestroyed && route != "wizard" && dialogs.none { it.isShowing }) render() }

    private fun connectRestrictions() {
        try {
            car = Car.createCar(this, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (!isDestroyed) {
                    if (!ready) setRestricted(true, false)
                    else try {
                        ux = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        ux!!.registerListener { setRestricted(it.isRequiresDistractionOptimization, true) }
                        setRestricted(ux!!.currentCarUxRestrictions.isRequiresDistractionOptimization, true)
                    } catch (_: Exception) { setRestricted(true, false) }
                }
            }
        } catch (_: Exception) { setRestricted(true, false) }
    }
    private fun setRestricted(value: Boolean, ready: Boolean) = runOnUiThread {
        if (restricted != value || restrictionReady != ready) {
            restricted = value; restrictionReady = ready
            if (value) {
                persistDraft(); dialogs.forEach { it.dismiss() }; dialogs.clear(); hideKeyboard()
            }
            render()
        }
    }
    override fun onResume() {
        super.onResume()
        // A prompt whose answer never arrives (the app restored after being killed) must not leave the screen dark.
        shade(false)
        mileageFeed = VehicleMileageFeed(this) { km, state ->
            if (isDestroyed) return@VehicleMileageFeed
            val old = store.mileage
            val valid = km != null && (old?.source != "vehicle" || km >= old.km)
            val nextState = if (km != null && !valid) "차량 주행거리 확인 필요" else state
            val changed = mileageLive != valid || mileageState != nextState || valid && old?.km != km
            mileageLive = valid; mileageState = nextState
            if (valid && !store.loadFailed) {
                change { store.mileage = Mileage(km!!, LocalDate.now(), "vehicle", System.currentTimeMillis()) }
                Reminders.evaluate(this)
            }
            if (changed && route != "wizard" && dialogs.none { it.isShowing }) render()
        }.also { it.start() }
        if (::store.isInitialized) {
            if (renderedDate != LocalDate.now()) render()
            Reminders.evaluate(this)
        }
    }
    override fun onStart() {
        super.onStart()
        // The background service may have moved the odometer while the screen was away.
        if (::store.isInitialized && !store.loadFailed) store.reload()
        VehicleSignals.hold(this, VehicleSignals.SCREEN); SignalService.start(this, true)
    }
    override fun onStop() { VehicleSignals.release(VehicleSignals.SCREEN); super.onStop() }
    override fun onPause() { persistDraft(); mileageFeed?.stop(); mileageFeed = null; super.onPause() }
    override fun onDestroy() {
        partTransition?.cancel(); VehicleSignals.unsubscribe(signalsChanged)
        dialogs.forEach { it.dismiss() }
        runCatching { ux?.unregisterListener() }; runCatching { car?.disconnect() }
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("route", route); outState.putString("part", part.name)
        outState.putString("draft", draft.toString()); super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        runCatching { Part.valueOf(intent.getStringExtra("part") ?: "") }.getOrNull()?.let {
            persistDraft(); part = it; route = "detail"; shopsOpen = false; render()
        }
    }
    @Deprecated("Native Activity back handling")
    override fun onBackPressed() {
        if (restricted) { super.onBackPressed(); return }
        when (route) {
            "wizard" -> go(if (store.plans[part] == null) "home" else "detail")
            "history" -> go("detail")
            "detail" -> go("home", true)
            "settings" -> go("home")
            else -> super.onBackPressed()
        }
    }
    private fun go(next: String, animate: Boolean = false, settle: Boolean = false) {
        if (restricted && next != "home") return
        // Only the chosen part travels; the rest appear in place.
        transitionOrigins = if (animate) partArts.filterKeys { it == part }.mapValues { (_, v) -> screenRect(v) }.filterValues { !it.isEmpty } else emptyMap()
        shopsOpen = false
        persistDraft(); route = next; render()
        if (animate) moveParts()
        // Switching parts from the strip: the page settles in place, nothing travels.
        if (settle && ValueAnimator.areAnimatorsEnabled()) stage?.apply {
            alpha = 0f; translationY = dp(10).toFloat()
            animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }
    private fun screenRect(v: View): Rect { val l = IntArray(2); v.getLocationOnScreen(l); return Rect(l[0], l[1], l[0] + v.width, l[1] + v.height) }

    /** Flies each part image from where it was on the previous screen to its place on this one. */
    private fun moveParts() {
        val origins = transitionOrigins.also { transitionOrigins = emptyMap() }
        if (origins.isEmpty() || !ValueAnimator.areAnimatorsEnabled()) return
        fadeIns.forEach { v -> v.alpha = 0f; v.animate().alpha(1f).setStartDelay(120).setDuration(320).start() }
        val host = root
        host.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                host.viewTreeObserver.removeOnPreDrawListener(this)
                val base = IntArray(2); host.getLocationOnScreen(base)
                val flights = partArts.mapNotNull { (p, dest) ->
                    val from = origins[p] ?: return@mapNotNull null
                    if (dest.width == 0 || dest.height == 0) return@mapNotNull null
                    val to = screenRect(dest).apply { offset(-base[0], -base[1]) }
                    val start = Rect(from).apply { offset(-base[0], -base[1]) }
                    val drawable = getDrawable(PartArt.resource(p))!!.constantState!!.newDrawable(resources).mutate()
                    drawable.bounds = start; host.overlay.add(drawable); dest.alpha = 0f
                    Triple(drawable, start to to, dest)
                }
                if (flights.isEmpty()) return true
                partTransition?.cancel()
                partTransition = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 380; interpolator = android.view.animation.DecelerateInterpolator(1.6f)
                    addUpdateListener { animation ->
                        val f = animation.animatedValue as Float
                        flights.forEach { (d, path, _) ->
                            val (a, b) = path
                            d.setBounds((a.left + (b.left - a.left) * f).toInt(), (a.top + (b.top - a.top) * f).toInt(),
                                (a.right + (b.right - a.right) * f).toInt(), (a.bottom + (b.bottom - a.bottom) * f).toInt())
                        }
                        host.getChildAt(0)?.alpha = .65f + .35f * f
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            flights.forEach { (d, _, dest) -> host.overlay.remove(d); dest.alpha = 1f }
                            host.getChildAt(0)?.alpha = 1f
                        }
                    })
                    start()
                }
                return true
            }
        })
    }
    private fun render() {
        partArts.clear(); fadeIns.clear(); stage = null
        assessments = emptyMap(); assessments = store.spec.parts.associateWith { judge(it) }
        if (part !in store.spec.parts) part = orbitParts.first()
        renderedDate = LocalDate.now()
        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        window.statusBarColor = bg; window.navigationBarColor = bg
        root.addView(when (route) { "detail" -> detail(); "wizard" -> wizard(); "history" -> history(); "settings" -> settings(); "onboarding" -> onboarding(); else -> home() }, FrameLayout.LayoutParams(-1, -1))
        if (restricted) root.addView(col().apply {
            gravity = Gravity.CENTER; setBackgroundColor(bg); pad(40); isClickable = true
            addView(text(if (restrictionReady) "정차 후 이어서 살펴봐요" else "차량 사용 상태를 확인하고 있어요", TypeScale.TITLE, true))
            // Only a record being written keeps its answers (the draft); popups close and lose theirs, so the line says so only then.
            val kept = route == "wizard" && draft.optBoolean("touched")
            addView(text(if (restrictionReady) "P단으로 바꾸면 이어서 볼 수 있어요.${if (kept) " 쓰던 기록은 그대로 남겨 뒀어요." else ""}" else "차량의 화면 사용 제한을 확인한 뒤 관리 기능을 열어요.", TypeScale.BODY, tint = muted))
        }, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun home(): View {
        val page = col().apply { setPadding(dp(32), dp(14), dp(32), dp(8)) }
        // The most pressing part first: due before soon, then the furthest along.
        // A paused part keeps its state on the ring but is not counted: the owner asked not to be reminded of it.
        val urgent = store.spec.parts.filter { store.plans[it]?.paused != true }.map { it to status(it) }.filter { it.second.status in listOf(DueStatus.SOON, DueStatus.DUE) }
            .sortedWith(compareBy({ if (it.second.status == DueStatus.DUE) 0 else 1 }, { -(it.second.fraction ?: 0f) }))
        // A first run has nothing to judge; "nothing to do" would be false, so the screen asks for the first date instead.
        val empty = store.records.isEmpty() && !store.loadFailed
        val first = store.spec.parts.first()
        page.addView(row().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(col().apply {
                val reading = store.mileage?.takeIf { it.source == "vehicle" }
                addView(text(when {
                    empty -> "먼저 마지막 정비 날짜를 알려 주세요"
                    urgent.isEmpty() -> "지금은 챙길 게 없어요"
                    else -> "지금 챙길 게 ${urgent.size}개 있어요"
                }, TypeScale.TITLE_LARGE, true).apply { setPadding(0, 0, 0, 0) })
                // The model the schedules come from, decided from what the car reports; the owner is never asked.
                addView(text(listOfNotNull(store.spec.title, reading?.let { "${fmt(it.km)} km" } ?: mileageState,
                    if (store.demo) "예시 데이터" else null, if (empty) "날짜를 넣으면 차가 센 운행으로 다음 시점을 계산해요" else null).joinToString(" · "),
                    TypeScale.META, tint = p.sub).apply { setPadding(0, dp(6), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            // With nothing recorded yet, the first part to fill in is the one button that matters.
            if (empty) addView(actionButton("${PartArt.title(first)} 기록하기", true) { this@MainActivity.part = first; go("detail", true) }
                .apply { minWidth = dp(260); textSize = TypeScale.META.toFloat() }, LinearLayout.LayoutParams(-2, dp(76)))
            // The most pressing part, one tap away: a tile like the provider tiles, with the state's dot.
            urgent.firstOrNull()?.let { (item, result) ->
                addView(row().apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(dp(28), 0, dp(16), 0); minimumHeight = dp(76)
                    background = round(p.fill, Radius.SURFACE); isClickable = true
                    contentDescription = "${PartArt.title(item)}, ${stateWord(item, result)}"
                    addView(dot(tone(item, result), 12), LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(14) })
                    addView(text(PartArt.title(item), TypeScale.DETAIL, true).apply { setPadding(0, 0, 0, 0) })
                    addView(text(stateWord(item, result), TypeScale.META, false, p.sub).apply { setPadding(dp(14), 0, 0, 0) })
                    addView(chevron(p.sub, 32), LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(8) })
                    setOnClickListener { this@MainActivity.part = item; go("detail", true) }
                }, LinearLayout.LayoutParams(-2, dp(76)))
            }
            addView(actionButton("설정", false) { go("settings") }.apply { minWidth = dp(140); textSize = TypeScale.META.toFloat() },
                LinearLayout.LayoutParams(-2, dp(76)).apply { marginStart = dp(16) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        if (store.loadFailed) page.addView(text("저장 정보를 읽지 못했어요. 설정에서 확인해 주세요.", 16, tint = warning))
        page.addView(VehicleMap(), LinearLayout.LayoutParams(-1, dp(maxOf(760, resources.configuration.screenHeightDp - 150))))
        return scroll(page)
    }

    private fun orbitNode(item: Part, ringSize: Int): PartOrbitNode {
        val result = status(item)
        return PartOrbitNode(this, item, result.status, result.fraction,
            summary(item, result), store.plans[item] == null && store.draft(item) != null, ringSize, label = ringLabel(item, result), tone = tone(item, result)).apply {
            partArts[item] = art
            if (item != this@MainActivity.part) fadeIns += this
            setOnClickListener { this@MainActivity.part = item; go("detail", true) }
        }
    }

    private inner class VehicleMap : FrameLayout(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val image = ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.vehicle_tucson); scaleType = ImageView.ScaleType.FIT_CENTER
            // The photo is a Tucson; any other car is drawn as its plain outline, so the picture never names the wrong model.
            if (store.spec.name != "투싼") { setColorFilter(p.sub, android.graphics.PorterDuff.Mode.SRC_IN); imageAlpha = 70 }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // Clockwise from the top, evenly spaced however many parts the car has. The vehicle remains the center.
        private val parts = orbitParts
        private val ringDp = when { resources.configuration.screenWidthDp >= 1200 -> 168; resources.configuration.screenWidthDp >= 850 -> 144; else -> 120 }
        private val nodes = parts.map { orbitNode(it, ringDp) }
        private val centers = Array(parts.size) { PointF() }
        private val anchors = parts.map { ANCHORS.getValue(it) }
        private val orbit = RectF()
        private val carBounds = RectF()
        init { setWillNotDraw(false); addView(image); nodes.forEach { addView(it) } }
        override fun onMeasure(w: Int, h: Int) {
            val width = MeasureSpec.getSize(w); val height = MeasureSpec.getSize(h); setMeasuredDimension(width, height)
            val carWidth = (width * (if (width < dp(750)) .46f else .49f)).toInt()
            image.measure(MeasureSpec.makeMeasureSpec(carWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((carWidth * .62f).toInt(), MeasureSpec.EXACTLY))
            nodes.forEach { it.measure(MeasureSpec.makeMeasureSpec(dp(ringDp + 56), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(ringDp + 84), MeasureSpec.EXACTLY)) }
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val cx = width / 2f; val cy = height * .46f
            val rx = (width - nodes.first().measuredWidth) / 2f - dp(18)
            val ry = minOf(cy - dp(ringDp / 2 + 16), height - cy - dp(ringDp / 2 + 90))
            orbit.set(cx - rx, cy - ry, cx + rx, cy + ry)
            val left = (width - image.measuredWidth) / 2; val top = (cy + dp(30) - image.measuredHeight / 2).toInt()
            image.layout(left, top, left + image.measuredWidth, top + image.measuredHeight)
            carBounds.set(image.left.toFloat(), image.top.toFloat(), image.right.toFloat(), image.bottom.toFloat())
            nodes.forEachIndexed { index, node ->
                val angle = Math.toRadians(-90.0 + index * 360.0 / parts.size)
                val x = cx + rx * kotlin.math.cos(angle).toFloat(); val y = cy + ry * kotlin.math.sin(angle).toFloat()
                centers[index].set(x, y)
                val nx = (x - node.measuredWidth / 2).toInt(); val ny = (y - node.ringSize / 2).toInt()
                node.layout(nx, ny, nx + node.measuredWidth, ny + node.measuredHeight)
            }
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1).toFloat(); paint.color = muted; paint.alpha = 25
            canvas.drawOval(orbit, paint)
            anchors.forEachIndexed { index, point ->
                val startX = carBounds.left + carBounds.width() * point.first
                val startY = carBounds.top + carBounds.height() * point.second
                val center = centers[index]; val dx = center.x - startX; val dy = center.y - startY
                val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val radius = nodes[index].ringSize / 2f
                val endX = center.x - dx / length * radius; val endY = center.y - dy / length * radius
                // Lines only tie a part to its place on the car; the ring carries the state's colour.
                paint.color = p.track; paint.alpha = 255; paint.strokeWidth = resources.displayMetrics.density * 1.5f
                canvas.drawPath(Path().apply {
                    moveTo(startX, startY)
                    cubicTo(startX + dx * .28f, startY + dy * .12f, endX - dx * .15f, endY - dy * .12f, endX, endY)
                }, paint)
            }
            paint.alpha = 255
        }
        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            anchors.forEachIndexed { index, point ->
                val x = carBounds.left + carBounds.width() * point.first; val y = carBounds.top + carBounds.height() * point.second
                paint.style = Paint.Style.FILL; paint.color = p.sub; paint.alpha = 200
                canvas.drawCircle(x, y, dp(3).toFloat(), paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density; paint.alpha = 90
                canvas.drawCircle(x, y, dp(7).toFloat(), paint)
            }
            paint.alpha = 255
        }
    }

    private fun detail(): View = partScreen(false)
    private fun wizard(): View = partScreen(true)

    private fun prepareEditor(newRecord: Boolean = false, recordId: String? = null) {
        val record = if (recordId != null) store.records.find { it.id == recordId && it.part == part } else if (newRecord) null else store.activeRecord(part)
        val pending = if (!newRecord && recordId == null) store.draft(part) else null
        draft = pending ?: JSONObject().put("new", newRecord || record == null).put("recordId", record?.id ?: UUID.randomUUID().toString())
            .put("date", record?.date?.toString()).put("km", record?.odometerKm?.toString() ?: "")
            .put("action", (record?.action ?: QuickSetup.action(part)).name)
        draft.put("part", part.name)
        if (newRecord) draft.put("date", LocalDate.now().toString()).put("km", "")

    }
    private fun startWizard(newRecord: Boolean, recordId: String? = null) {
        if (restricted || store.loadFailed) return
        prepareEditor(newRecord, recordId); go("wizard")
    }

    private fun partScreen(editing: Boolean): View {
        val plan = store.plans[part]
        if (editing && draft.value("part") != part.name) prepareEditor()
        if (!editing && plan != null) drawingScreen(plan)?.let { return it }
        val page = col().apply { setPadding(dp(30), dp(20), dp(30), dp(24)) }
        page.addView(row().apply {
            gravity = Gravity.TOP
            // The other parts line up here; while editing they stay hidden so a half-written record is not left behind.
            if (editing) addView(text(getString(R.string.app_name), 16, tint = muted), LinearLayout.LayoutParams(0, -2, 1f))
            else addView(partStrip(), LinearLayout.LayoutParams(0, -2, 1f))
            if (!editing && plan != null) addView(toolIcon(R.drawable.ic_more_horiz, "더보기") { showPartMenu(it) }, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(toolIcon(android.R.drawable.ic_menu_close_clear_cancel, "닫기") {
                if (route == "wizard" && plan != null) go("detail") else go("home", route == "detail")
            }, LinearLayout.LayoutParams(dp(52), dp(52)))
        })
        val compact = resources.configuration.screenWidthDp < 850
        val result = status(part)
        val hero = col().apply { gravity = Gravity.CENTER_HORIZONTAL }
        val ring = PartOrbitNode(this, part, result.status, result.fraction, "", false,
            if (compact) 156 else 212, showLabels = false, tone = tone(part, result))
        ring.isClickable = false; ring.isFocusable = false; ring.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        if (!editing) partArts[part] = ring.art
        hero.addView(ring, LinearLayout.LayoutParams(dp(if (compact) 210 else 286), dp(if (compact) 166 else 224)))
        hero.addView(text(PartArt.title(part), TypeScale.BODY, true).apply { gravity = Gravity.CENTER_HORIZONTAL })
        // The ring is the combined gauge (schedule or vehicle estimate, whichever is further); under it, the job and how far.
        if (!editing) ringLabel(part, result)?.let { label ->
            hero.addView(text(label, TypeScale.LABEL, false, if (result.status == DueStatus.DUE) tone(part, result) else p.sub).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 0, 0, 0) })
        }
        val body = if (editing) compactEditor() else compactSummary(plan ?: RecommendedCare.plan(part), result)
        val actions = if (editing) emptyList() else careActions(plan ?: RecommendedCare.plan(part), result)
        val bar = if (actions.isEmpty()) 0 else ACTION_BAR
        val stage = col().apply {
            gravity = Gravity.CENTER_VERTICAL
            // The part strip makes the header taller than the editor's plain title row; the action bar takes the bottom.
            minimumHeight = dp(maxOf(560, resources.configuration.screenHeightDp - bar - if (editing) 150 else 150 + stripRingDp() + 64 - 52 + 48))
        }
        if (compact) {
            stage.addView(hero, margin(12)); stage.addView(body, margin(22))
        } else {
            stage.addView(row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(hero, LinearLayout.LayoutParams(dp(if (editing) 330 else 280), -2).apply { marginEnd = dp(if (editing) 66 else 36) })
                addView(body, LinearLayout.LayoutParams(0, -2, 1f))
            })
        }
        // Air between the part strip and the sentence; the stage gives the same height back below.
        page.addView(stage, margin(if (editing) 0 else 48))
        this.stage = stage
        val width = if (editing) 1040 else 1280
        if (actions.isEmpty()) return scroll(page, width)
        // What the owner did goes in the bottom-right corner, where the guide's popups also put their actions.
        page.setPadding(page.paddingLeft, page.paddingTop, page.paddingRight, dp(bar))
        return FrameLayout(this).apply {
            addView(scroll(page, width), FrameLayout.LayoutParams(-1, -1))
            addView(col().apply {
                setPadding(dp(30), 0, dp(30), dp(24)); setBackgroundColor(p.canvas)
                addView(View(this@MainActivity).apply { setBackgroundColor(p.divider) }, LinearLayout.LayoutParams(-1, dp(1)))
                addView(row().apply {
                    actions.forEachIndexed { i, (label, action) ->
                        addView(actionButton(label, i == 0, action).apply { minWidth = dp(280); textSize = TypeScale.DETAIL.toFloat() }, LinearLayout.LayoutParams(-2, dp(80)).apply { if (i > 0) marginStart = dp(16) })
                    }
                }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = dp(20) })
            }, FrameLayout.LayoutParams(if (resources.configuration.screenWidthDp > width) dp(width) else -1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }
    }

    /**
     * A detail built around its drawing, on a wide window once the part has a date on record; anything else keeps the
     * general detail. One panel: what to do and the drawing on the left, why on the right, the shops below.
     */
    private fun drawingScreen(plan: MaintenancePlan): View? {
        val config = resources.configuration
        if (config.screenWidthDp < 1500 || config.screenHeightDp < 1000) return null
        val record = store.activeRecord(part) ?: return null
        val signals = VehicleSignals.snapshot()
        val assessment = status(part)
        val tone = tone(part, assessment)
        val data = partData(record)
        val guide = CareGuide.build(part, plan, record, assessment, signals, store.records, store.mileage, store.report(part), LocalDate.now())
        val flagged = assessment.status == DueStatus.DUE || assessment.status == DueStatus.SOON
        // With nothing to do the shops fold into a link, and the booking stays visible once there is one.
        val active = flagged || store.bookings[part] != null || shopsOpen
        val (drawing, rows, reason) = partSheet(plan, record, assessment, guide, signals, data, active)

        val page = col().apply { setPadding(dp(48), dp(16), dp(48), dp(24)) }
        page.addView(row().apply {
            addView(detailStrip(), LinearLayout.LayoutParams(0, dp(96), 1f))
            addView(row().apply {
                minimumHeight = dp(76); setPadding(dp(16), 0, dp(8), 0); isClickable = true; contentDescription = "더보기"
                addView(text("더보기", TypeScale.META, false, p.body).apply { setPadding(0, 0, 0, 0) })
                setOnClickListener { if (!restricted) showPartMenu(it) }
            })
            addView(toolIcon(R.drawable.ic_close, "닫기") { go("home", true) }.apply { imageTintList = android.content.res.ColorStateList.valueOf(p.title); setPadding(dp(22), dp(22), dp(22), dp(22)) }, LinearLayout.LayoutParams(dp(76), dp(76)))
        })

        val panel = col().apply { background = round(p.card, Radius.SURFACE).apply { setStroke(dp(1), p.divider) }; setPadding(0, dp(24), 0, dp(26)) }
        val top = row().apply { gravity = Gravity.TOP }
        top.addView(col().apply {
            addView(row().apply {
                setPadding(dp(36), 0, dp(36), 0)
                if (flagged) addView(dot(tone, 16), LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(14) })
                addView(text(headline(plan, assessment, guide.headline), TypeScale.HEADLINE_DETAIL, true).apply { typeface = Fonts.of(700); setPadding(0, 0, 0, 0); fontFeatureSettings = "tnum" }, LinearLayout.LayoutParams(0, -2, 1f))
            })
            val why = reason ?: Regex("^(.+?[.!?])\\s").find(guide.advice)?.groupValues?.get(1) ?: guide.advice
            addView(text(why.replace(Regex("([\\d만천]) (km|mm|dB|%|일|개월)"), "$1 $2"), TypeScale.DETAIL, tint = p.body).apply {
                setPadding(0, 0, 0, 0); setLineSpacing(dp(4).toFloat(), 1f); breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); marginStart = dp(if (flagged) 66 else 36); marginEnd = dp(36) })
            addView(drawing, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        }, LinearLayout.LayoutParams(dp(930), -1))
        top.addView(View(this).apply { setBackgroundColor(p.divider) }, LinearLayout.LayoutParams(dp(1), -1))
        top.addView(col().apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(36), 0, dp(36), 0)
            addView(sectionHeader("판단 근거", data?.let { d -> textLink("측정값 보기") { showMeasurements(d) } }))
            rows.forEachIndexed { i, v ->
                if (i > 0) addView(View(this@MainActivity).apply { setBackgroundColor(p.divider) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(if (rows.size > 2) 28 else 34); bottomMargin = dp(if (rows.size > 2) 28 else 34) })
                addView(v)
            }
            val notes = listOfNotNull(if (plan.paused) "알림 쉬는 중" else null) + (data?.warnings ?: emptyList())
            notes.forEach { addView(text(it, TypeScale.META, false, p.body), margin(12)) }
            data?.prompt?.let { (note, button) ->
                addView(row().apply {
                    setPadding(dp(24), dp(8), dp(8), dp(8)); background = round(p.fill, Radius.SURFACE)
                    addView(text(note, TypeScale.META, tint = p.body), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(button, LinearLayout.LayoutParams(-2, dp(76)).apply { marginStart = dp(16) })
                }, margin(16))
            }
        }, LinearLayout.LayoutParams(0, -1, 1f))
        panel.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))
        panel.addView(View(this).apply { setBackgroundColor(p.divider) }, LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(36); marginEnd = dp(36) })
        if (active) panel.addView(serviceSection(plan, assessment, wide = true), LinearLayout.LayoutParams(-1, -2).apply { marginStart = dp(36); marginEnd = dp(36); topMargin = dp(12) })
        panel.addView(row().apply {
            careActions(plan, assessment).forEachIndexed { i, (label, action) ->
                val first = label == "닦았어요"
                addView(actionButton(label, first, action).apply { if (!first) background = round(p.fill, Radius.SURFACE); minWidth = dp(300) }, LinearLayout.LayoutParams(-2, dp(84)).apply { if (i > 0) marginStart = dp(12) })
            }
            if (!active) {
                addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 0, 1f))
                addView(textLink("정비소 보기") { shopsOpen = true; render() })
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { marginStart = dp(36); marginEnd = dp(36); topMargin = dp(if (active) 24 else 16) })
        page.addView(panel, LinearLayout.LayoutParams(-1, dp(maxOf(960, config.screenHeightDp - 154))).apply { topMargin = dp(16) })
        stage = panel
        return scroll(page, 1680)
    }

    /** A bare "22일 남았어요" on a full-width headline says what is left of what. */
    private fun headline(plan: MaintenancePlan, assessment: Assessment, text: String): String {
        if (!Regex("^[\\d,년 개월일km]+ 남았어요$").matches(text)) return text
        val who = when (part) { Part.ENGINE_OIL -> "엔진오일"; Part.BRAKE_PAD -> "브레이크"; Part.TIRE -> "타이어"; Part.WIPER -> "와이퍼"; Part.HVAC -> "냉난방"; Part.CABIN_FILTER -> "필터" }
        return "$who ${(assessment.task ?: plan.action).label}까지 $text"
    }

    /** One figure on the right of the drawing: a bar with the car's own share on top in orange when it moved the status. */
    private fun evidence(f: Factor, ticks: List<Float> = listOfNotNull(f.marker), labels: List<String> = emptyList()): View {
        val used = f.fraction ?: return evidenceRow(f.title, f.value, f.now.ifEmpty { f.note }, f.limit, null, f.alert)
        // Once the plain part alone fills the bar, the alert colour would hide under it; colour the whole bar instead.
        val shares = if (f.extra > 0 && f.alert && used >= 1f) listOf(1f to p.soon)
            else if (f.extra > 0) listOf(used + f.extra to (if (f.alert) p.soon else p.rib), used to p.primary)
            else listOf(used to if (f.alert) p.soon else p.primary)
        return evidenceRow(f.title, f.value, f.now.ifEmpty { f.note }, f.limit, EvidenceBar(this, shares, p.track, ticks, p.title, labels, p.sub), barHeight = if (labels.isEmpty()) 22 else 50)
    }

    /** The drawing and evidence rows of the current part, and a reason sentence when the guide's own will not do. */
    private fun partSheet(plan: MaintenancePlan, record: ServiceRecord, assessment: Assessment, guide: Guide, signals: SignalSnapshot, data: PartData?, active: Boolean): Triple<View, List<View>, String?> {
        val tucson = store.spec.name == "투싼"
        val shared = guide.factors.firstOrNull { it.fraction != null }
        return when (part) {
            Part.BRAKE_PAD -> {
                val pad = BrakeWearModel.params()
                val left = CompositeJudge.padLeft(store.records, signals)
                val sinceKm = record.odometerKm?.let { at -> store.mileage?.km?.takeIf { it >= at }?.let { (it - at).toDouble() } }
                val equivalent = CompositeJudge.brakeEquivalentKm(record, sinceKm, signals)
                val interval = plan.intervalKm?.toDouble()
                val km = { v: Double -> String.format(java.util.Locale.KOREA, "%,.0f", v) }
                val alert = assessment.drivenBy?.startsWith("제동 에너지") == true
                val rows = mutableListOf<View>()
                if (equivalent != null && interval != null) {
                    val real = sinceKm ?: 0.0
                    val added = (equivalent - real).coerceAtLeast(0.0)
                    val scale = maxOf(interval * 1.1, equivalent * 1.06)
                    rows += evidenceRow("제동 에너지 환산", "${km(equivalent)} km", if (added >= 1) "실제 ${km(real)} km + 제동 습관 ${km(added)} km" else "실제 ${km(real)} km", "권장 ${km(interval)} km",
                        EvidenceBar(this, listOf((equivalent / scale).toFloat() to (if (alert) p.soon else p.rib), (real / scale).toFloat() to p.primary), p.track, listOf((interval / scale).toFloat()), p.title))
                } else guide.factors.firstOrNull { it.title == "주행거리" }?.let { rows += evidence(it) }
                guide.factors.firstOrNull { it.title == "제동 습관" }?.let { f -> rows += evidence(f.copy(now = f.limit.replace("가운데 선이", "눈금이"), limit = "", alert = false)) }
                val over = alert && equivalent != null && interval != null && left != null
                val reason = if (over) "패드는 약 ${String.format(java.util.Locale.KOREA, "%.1f", left!!.coerceAtLeast(0.0))} mm 남은 것으로 추정되지만, 제동량이 권장 거리${if (equivalent!! >= interval!!) "를 넘어 눈으로 확인할 때예요." else "에 가까워졌어요."}" else null
                Triple(BrakeXrayView(this, dark, left, pad.padNewMm, BrakeWearModel.REPLACE_MM, tucson), rows, reason)
            }
            Part.ENGINE_OIL -> {
                val d = guide.factors.firstOrNull { it.title == "주행거리" }
                val parts = d?.now?.split(" + ") ?: emptyList()
                val rows = guide.factors.filter { it !== d }.map { f ->
                    if (f.title != "연료 희석 추정") evidence(f) else {
                        // The bar runs a little past the change point so both lines keep their labels.
                        val end = (OilModel.CHANGE_PCT * 1.14).toFloat(); val pct = (f.fraction ?: 0f) * OilModel.CHANGE_PCT.toFloat()
                        evidence(f.copy(fraction = pct / end, limit = ""), listOf(OilModel.CAUTION_PCT.toFloat() / end, OilModel.CHANGE_PCT.toFloat() / end),
                            listOf("주의 ${OilModel.CAUTION_PCT.toInt()}%", "교체 ${OilModel.CHANGE_PCT.toInt()}%"))
                    } }
                val used = d?.fraction ?: shared?.fraction ?: 0f
                Triple(RingDrawing(this, dark, if (dark) R.drawable.part_oil_night else R.drawable.part_oil_day, 300f, used, d?.extra ?: 0f, d?.value ?: shared?.value,
                    parts.firstOrNull { it.startsWith("실제") }, parts.firstOrNull { it.startsWith("운행 조건") }?.let { "+$it" }, null, listOfNotNull(guide.headline, d?.now).joinToString(". "), !active), rows, null)
            }
            Part.HVAC -> {
                val rows = guide.factors.map { f ->
                    if (f.title in listOf("냉방", "난방") && f.limit.endsWith("바로 점검")) evidence(f.copy(fraction = null, now = f.limit, limit = "")) else evidence(f) }
                val lost = data?.gauges?.firstOrNull()?.takeIf { it.value != "-" }?.caption?.substringBefore(" ·")?.let { "냉매 $it 감소 추정" }
                val days = guide.factors.firstOrNull()
                Triple(RingDrawing(this, dark, if (dark) R.drawable.part_hvac_night else R.drawable.part_hvac_day, 330f, days?.fraction ?: 0f, 0f, null, null, null, lost, listOfNotNull(guide.headline, lost).joinToString(". "), !active), rows, null)
            }
            Part.WIPER -> {
                val shift = signals.sound.shift(record.id)
                val change = signals.sound.change(record.id).first
                val db = shift?.db ?: change?.takeIf { it >= .5 }
                val ordered = guide.factors.sortedBy { it.title != "작동 소리" }
                val rows = ordered.map { f -> when {
                    f.title == "작동 소리" && f.now.startsWith("최근 ${WiperSound.RECENT}회") -> evidenceRow(f.title, f.value, f.now, "", row().apply {
                        gravity = Gravity.CENTER_VERTICAL
                        val louder = Math.round((f.fraction ?: 0f) * WiperSound.RECENT)
                        repeat(WiperSound.RECENT) { i -> addView(dot(if (i < louder) p.soon else p.track, 16), LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(10) }) }
                    })
                    f.title == "닦은 횟수" && " · 보통보다" in f.now -> evidence(f.copy(value = f.now.substringBefore(" · "), now = f.now.substringAfter(" · ")))
                    else -> evidence(f)
                } }
                val days = guide.factors.firstOrNull { it.title == "기간" }
                val drawing = if (db != null) WiperDrawing(this, dark, db, !active)
                    else RingDrawing(this, dark, if (dark) R.drawable.part_wiper_night else R.drawable.part_wiper_day, 340f, days?.fraction ?: 0f, days?.extra ?: 0f, null, null, null, null, guide.headline, !active)
                Triple(drawing, rows, null)
            }
            Part.CABIN_FILTER -> {
                val dust = signals.filter.since(record.id)?.takeIf { it > 0 }?.let { it / FilterModel.END_OF_SERVICE_HOURS }
                val air = guide.factors.firstOrNull { it.title == "먼지 적재 추정" }
                val where = data?.gauges?.firstOrNull()?.caption?.substringAfter(" · ", "") ?: ""
                val rows = guide.factors.filter { it !== air && it.title != "주행거리" }.map { evidence(it) }.toMutableList()
                air?.let { rows += evidenceRow("외기 송풍", it.now.removePrefix("외기 송풍 "), where, it.limit, null, it.alert) }
                data?.gauges?.getOrNull(1)?.takeIf { it.value != "-" }?.let { g ->
                    rows += evidenceRow(g.label, "${g.value}${g.unit}", g.caption, "", EvidenceBar(this, listOf((g.low ?: 0f) to p.primary), p.track)) }
                Triple(FilterDrawing(this, dark, dust, dust?.let { air?.value?.let { v -> "먼지 $v" } }, !active), rows.take(3), null)
            }
            Part.TIRE -> {
                val tread = TireTread.of(store.records, store.mileage, signals.tire)
                val tp = TireParams.of(CarModel.current)
                val alert = assessment.drivenBy?.startsWith("홈 깊이") == true
                Triple(TireDrawing(this, dark, tread?.front, tread?.rear, tp.newMm, TireModel.MOTORWAY_TREAD_MM, alert, active, tucson), guide.factors.map { evidence(it) }, null)
            }
        }
    }

    /** All the car's parts in home order, each with its small ring and state; the current one sits on a card. */
    private fun detailStrip(): View = row().also { fadeIns += it }.apply {
        orbitParts.forEach { item ->
            val result = status(item)
            addView(row().apply {
                setPadding(dp(8), 0, dp(4), 0)
                if (item == part) background = round(p.card, Radius.SURFACE).apply { setStroke(dp(1), p.divider) }
                else { isClickable = true; setOnClickListener { this@MainActivity.part = item; go("detail", settle = true) } }
                contentDescription = "${item.title}, ${ringLabel(item, result) ?: ""}"
                val ring = PartOrbitNode(this@MainActivity, item, result.status, result.fraction, "", false, 64, showLabels = false, tone = tone(item, result))
                ring.isClickable = false; ring.isFocusable = false; ring.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                partArts[item] = ring.art
                addView(ring, LinearLayout.LayoutParams(dp(68), dp(68)))
                addView(col().apply {
                    addView(text(PartArt.title(item), TypeScale.LABEL_SMALL, true).apply { setPadding(0, 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    val line = (ringLabel(item, result) ?: "").replace(Regex("^.+까지 (.+ km)$"), "$1 남음")
                    addView(text(line, TypeScale.LABEL_SMALL, false, if (item == part) p.title else p.sub).apply { setPadding(0, 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
            }, LinearLayout.LayoutParams(0, dp(92), 1f).apply { marginEnd = dp(6) })
        }
    }

    /** One line of the judgment: title and value, the bar, then what it counts and its limit. */
    private fun evidenceRow(title: String, value: String, now: String, limit: String, bar: View?, alert: Boolean = false, barHeight: Int = 22) = col().apply {
        addView(row().apply {
            addView(text(title, TypeScale.DETAIL, false, p.body).apply { setPadding(0, 0, 0, 0); maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(value, TypeScale.TITLE, true, if (alert && bar == null) p.soon else p.title).apply { setPadding(0, 0, 0, 0); fontFeatureSettings = "tnum"; maxLines = 1 })
        })
        bar?.let { addView(it, LinearLayout.LayoutParams(-1, dp(barHeight)).apply { topMargin = dp(6) }) }
        if (now.isNotEmpty() || limit.isNotEmpty()) addView(row().apply {
            addView(text(now, TypeScale.META, false, p.sub).apply { setPadding(0, 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; fontFeatureSettings = "tnum" }, LinearLayout.LayoutParams(0, -2, 1f))
            if (limit.isNotEmpty()) addView(text(limit, TypeScale.META, false, p.sub).apply { setPadding(dp(16), 0, 0, 0); maxLines = 1; fontFeatureSettings = "tnum" })
        }, margin(4))
    }

    /**
     * The services the owner can log from the detail, most relevant first. Two at most: the asked-for one and the next;
     * a third job folds into "다른 기록".
     */
    private fun careActions(plan: MaintenancePlan, assessment: Assessment): List<Pair<String, () -> Unit>> {
        val record = store.activeRecord(part)
        // A louder blade is first a cue to clean; the manual inspects only what stays loud afterwards.
        val shift = if (part == Part.WIPER) VehicleSignals.wiperSound().shift(record?.id) else null
        val clean = record != null && shift != null && !shift.confirmed && assessment.status != DueStatus.DUE &&
            VehicleSignals.wiperSound().sinceCleaning(record.id) == null
        val jobs = when (part) {
            Part.TIRE -> listOf("점검했어요" to ServiceAction.INSPECT, "위치 교환했어요" to ServiceAction.ROTATE, "타이어 교체했어요" to ServiceAction.REPLACE)
            Part.BRAKE_PAD -> listOf("점검 받았어요" to ServiceAction.INSPECT, "패드 교체했어요" to ServiceAction.REPLACE)
            else -> listOf((if (plan.action == ServiceAction.INSPECT) "점검 받았어요" else "교체했어요") to plan.action)
        }.sortedBy { it.second != (assessment.task ?: plan.action) }
        val buttons = mutableListOf<Pair<String, () -> Unit>>()
        if (record == null) buttons += "마지막 날짜 넣기" to { startWizard(false) }
        if (clean) buttons += "닦았어요" to {
            confirm("유리와 블레이드를 닦으셨나요?", "닦은 뒤 같은 속도로 ${WiperSound.CONFIRM}번 작동할 때 소리를 다시 비교해요. 그때도 크면 교체를 권해요.", "닦았어요", "아직이에요") {
                VehicleSignals.markCleaned(record!!.id, shift!!.level); render(); toast("닦은 뒤 소리를 다시 비교할게요")
            }
        }
        jobs.forEach { (label, action) -> buttons += label to { recordToday(plan, action) } }
        return if (buttons.size > 2) buttons.take(1) + ("다른 정비 기록" to {
            val rest = buttons.drop(1)
            choose("어떤 정비를 하셨나요?", rest.map { it.first }) { i -> rest[i].second() }
        }) else buttons
    }

    private fun stripRingDp() = if (resources.configuration.screenWidthDp < 850) 64 else 84
    /** The other five parts in home order, small enough to sit in one row; tapping one opens its detail. */
    private fun partStrip(): View = row().also { fadeIns += it }.apply {
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        val ringDp = stripRingDp()
        orbitParts.filter { it != part }.forEach { item ->
            val result = status(item)
            addView(PartOrbitNode(this@MainActivity, item, result.status, result.fraction,
                summary(item, result), store.plans[item] == null && store.draft(item) != null, ringDp, tone = tone(item, result),
                label = if (result.status == DueStatus.DUE || result.status == DueStatus.SOON) stateWord(item, result) else null).apply {
                partArts[item] = art
                setOnClickListener { this@MainActivity.part = item; go("detail", settle = true) }
            }, LinearLayout.LayoutParams(0, dp(ringDp + 64), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        }
    }

    private fun compactEditor(): View {
        val content = col()
        val action = ServiceAction.valueOf(draft.value("action").ifEmpty { part.defaultAction.name })
        content.addView(text("최근 ${action.label}일만 알려주세요", 24, true))
        content.addView(row().apply {
            addView(quietButton(draft.value("date").takeIf { it.isNotEmpty() }?.replace('-', '.') ?: "날짜 선택") { pickServiceDate() }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL; textSize = 20f; setTextColor(ink)
            }, LinearLayout.LayoutParams(0, dp(54), 1f))
            addView(quietButton("오늘") { draft.put("dateUnknown", false); updateDraft("date", LocalDate.now().toString()); render() }, LinearLayout.LayoutParams(dp(68), dp(54)))
        }, margin(20))
        var baseline: EditText? = null
        // Tires keep a distance baseline for rotation even though their plan is a monthly check.
        if ((RecommendedCare.plan(part).intervalKm != null || part == Part.TIRE) && draft.value("date") != LocalDate.now().toString()) {
            content.addView(text("당시 주행거리 · 선택", 14, tint = muted), margin(12))
            baseline = smallField("모르면 비워 두세요", draft.value("km"), "정비 당시 주행거리 (km)") { updateDraft("km", it) }
            content.addView(baseline)
        }
        content.addView(text("추천 기준  ·  ${policyText(recommended(part))}", 14, tint = muted), margin(18))
        content.addView(quietButton("기록 저장") {
            if (draft.value("date").isEmpty()) { toast("최근 정비 날짜를 선택해 주세요"); return@quietButton }
            if (baseline != null && !valid(baseline, optional = true)) return@quietButton
            finishWizard()
        }.apply { background = round(soft, 22) }, LinearLayout.LayoutParams(dp(136), dp(48)).apply { topMargin = dp(24) })
        return content
    }

    private fun compactSummary(plan: MaintenancePlan, assessment: Assessment): View {
        val content = col()
        val record = store.activeRecord(part)
        val data = partData(record)
        val guide = CareGuide.build(part, plan, record, assessment, VehicleSignals.snapshot(), store.records, store.mileage, store.report(part), LocalDate.now())
        val tone = tone(part, assessment)

        // 1. What to do: one sentence led by the state's dot, then why. The buttons sit in the bar at the bottom right.
        content.addView(row().apply {
            gravity = Gravity.CENTER_VERTICAL
            if (assessment.status == DueStatus.DUE || assessment.status == DueStatus.SOON) addView(dot(tone, 14), LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(16) })
            addView(text(guide.headline, TypeScale.HEADLINE_DETAIL, true).apply { setLineSpacing(0f, 1.05f); setPadding(0, 0, 0, 0); fontFeatureSettings = "tnum" },
                LinearLayout.LayoutParams(0, -2, 1f))
        })
        // A number and its unit stay on one line.
        // One sentence of why: the rows below carry the rest, so the screen reads in two lines, not five.
        val why = Regex("^(.+?[.!?])\\s").find(guide.advice)?.groupValues?.get(1) ?: guide.advice
        content.addView(text(why.replace(Regex("([\\d만천]) (km|mm|dB|%|일|개월)"), "$1\u00A0$2"), TypeScale.DETAIL, tint = p.body).apply {
            setLineSpacing(dp(6).toFloat(), 1f); setPadding(0, 0, 0, 0)
            // Two even lines rather than one long line and a stray word.
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED
        }, margin(12))
        val notes = listOfNotNull(if (plan.paused) "알림 쉬는 중" else null) + (data?.warnings ?: emptyList())
        if (notes.isNotEmpty()) content.addView(row().apply {
            notes.forEachIndexed { i, n -> addView(row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(dot(p.soon, 10), LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(10) })
                addView(text(n, TypeScale.META, false, p.body).apply { setPadding(0, 0, 0, 0) })
            }, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = dp(32) }) }
        }, margin(16))

        // 2. What the judgment rests on: a plain list under a section title, no card and no bars; the figure that moved
        // the status carries the state's dot. The raw measurements stay one small link away, in a popup.
        val more = data?.let { d -> textLink("측정값 보기") { showMeasurements(d) } }
        if (guide.factors.isNotEmpty()) {
            content.addView(sectionHeader("판단 근거", more), margin(40))
            guide.factors.forEach { f ->
                content.addView(View(this).apply { setBackgroundColor(p.divider) }, LinearLayout.LayoutParams(-1, dp(1)))
                content.addView(factorView(f, tone))
            }
        } else more?.let { content.addView(it, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = dp(16) }) }
        data?.prompt?.let { (note, button) ->
            content.addView(row().apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(8), dp(8), dp(8)); background = round(p.fill, Radius.SURFACE)
                addView(text(note, TypeScale.META, tint = p.body), LinearLayout.LayoutParams(0, -2, 1f))
                addView(button, LinearLayout.LayoutParams(-2, dp(76)).apply { marginStart = dp(16) })
            }, margin(16))
        }

        // 3. Where to get it done: grey tiles, so it reads apart from the list above.
        content.addView(serviceSection(plan, assessment), margin(44))
        return content
    }

    /** A section title with an optional trailing control on the same line. */
    private fun sectionHeader(title: String, trailing: View?) = row().apply {
        gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(76)
        addView(text(title, TypeScale.SECTION, true, p.sub).apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(-2, -2))
        addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 0, 1f))
        trailing?.let { addView(it) }
    }

    /** The guide's text button: a label and a drawn chevron, 76 dp tall for an in-car touch target. */
    private fun textLink(label: String, color: Int = p.sub, action: () -> Unit) = row().apply {
        gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(76); setPadding(dp(16), 0, 0, 0); isClickable = true; contentDescription = label
        addView(text(label, TypeScale.META, false, color).apply { setPadding(0, 0, 0, 0) })
        addView(chevron(color, 28), LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(2) })
        setOnClickListener { if (!restricted) action() }
    }

    private fun chevron(color: Int, size: Int) = ImageView(this).apply {
        setImageResource(R.drawable.ic_chevron_right); imageTintList = android.content.res.ColorStateList.valueOf(color)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO; layoutParams = ViewGroup.LayoutParams(dp(size), dp(size))
    }

    private fun dot(color: Int, size: Int) = View(this).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = ViewGroup.LayoutParams(dp(size), dp(size))
    }

    private var serviceModes = mutableMapOf<Part, ServiceMode>()
    /** The owner opened the shops on a detail that has nothing to do; back to folded on leaving it. */
    private var shopsOpen = false
    /** The parts this car has, in home order. */
    private val orbitParts get() = ORBIT.filter { it in store.spec.parts }
    /** Height kept free under the detail for the action bar: an 80 dp button and its margins. */
    private val ACTION_BAR = 1 + 20 + 80 + 24 + 16
    private val p get() = Palette(dark)

    /** A white card on the grey canvas: no border, generous padding. */
    private fun card() = col().apply { setPadding(dp(32), dp(24), dp(32), dp(28)); background = round(surface, 24) }

    /** A tappable list line: title, optional value on the right, and a chevron. */
    private fun listRow(title: String, value: String?, trail: String = "›", action: (() -> Unit)?) = row().apply {
        gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(88)
        addView(text(title, TypeScale.BODY, false, p.title), LinearLayout.LayoutParams(0, -2, 1f))
        value?.let { addView(text(it, TypeScale.BODY, true, p.title)) }
        if (trail.isNotEmpty()) addView(chevron(p.sub, 28), LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(12) })
        if (action != null) { isClickable = true; setOnClickListener { if (!restricted) action() } }
    }

    /**
     * Where the job can be done: a mobile service, a nearby shop or the parts to do it yourself. The chosen way is the
     * title with the tabs beside it, and each provider is a card that says only what it does and where; the price waits
     * in the booking sheet. Payment waits for the Pleos Payment SDK.
     */
    private fun serviceSection(plan: MaintenancePlan, assessment: Assessment, wide: Boolean = false): View = col().apply {
        val task = assessment.task ?: plan.action
        val booking = store.bookings[part]
        val modes = ServiceCatalog.modes(part, task)
        val mode = booking?.mode ?: serviceModes[part]?.takeIf { it in modes } ?: modes.first()
        val located = VehiclePlaces.location
        val offers = if (booking != null) emptyList()
            else ServiceCatalog.offers(part, task, mode, located, java.time.LocalDateTime.now()).sortedBy { it.distanceKm ?: 0.0 }
        // The ways of doing the job are the section's title: Line tabs on the left, the full list on the right.
        addView(row().apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(64)
            if (booking != null || modes.size == 1) addView(text(if (booking != null || wide) "${mode.label} 예약" else mode.label, TypeScale.SECTION, true, if (wide) p.title else p.sub).apply { setPadding(0, 0, 0, 0) })
            else addView(segmented(modes.map { it.label }, modes.indexOf(mode), dp(64)) { serviceModes[part] = modes[it]; render() })
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 0, 1f))
            if (booking == null && mode == ServiceMode.SHOP && located == null) addView(textLink(if (VehiclePlaces.granted(this@MainActivity, VehiclePlaces.LOCATION_PERMISSION)) "내 위치 사용" else "위치 허용", p.title) {
                if (VehiclePlaces.granted(this@MainActivity, VehiclePlaces.LOCATION_PERMISSION)) VehiclePlaces.locate(this@MainActivity) { render(); if (it == null) toast("차량 위치를 받지 못했어요") }
                else ask(VehiclePlaces.LOCATION_PERMISSION, 103)
            }.apply { setPadding(dp(12), 0, dp(24), 0) })
            // Providers and places are samples until partners sign; the link says so without a sentence.
            if (booking == null) addView(textLink("예시 ${offers.size}곳 전체 보기") { if (!restricted) providerSheet(mode, offers, task, located != null) })
        })
        if (booking != null) {
            addView(row().apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(32), dp(20), dp(20), dp(20)); background = round(p.fill, Radius.SURFACE)
                addView(col().apply {
                    addView(text("${when2(booking.at)} · ${booking.provider}", TypeScale.DETAIL, true).apply { setPadding(0, 0, 0, 0) })
                    addView(text("${ServiceCatalog.job(part, task)} · ${where(booking.mode, booking.offer, VehiclePlaces.location != null)} · 시연용 예시", TypeScale.META, tint = p.sub).apply { setPadding(0, dp(4), 0, 0) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                if (booking.lat != null) addView(actionButton("길안내", false) { route(booking.offer) }.apply { background = round(p.card, Radius.SURFACE) }, LinearLayout.LayoutParams(-2, dp(76)))
                addView(actionButton("예약 취소", false) {
                    confirm("예약을 취소할까요?", "${booking.provider} ${when2(booking.at)} 예약을 지워요.", "예약 취소") { if (change { store.bookings.remove(part) }) render() }
                }.apply { background = round(p.card, Radius.SURFACE) }, LinearLayout.LayoutParams(-2, dp(76)).apply { marginStart = dp(16) })
            }, margin(12))
            return@apply
        }
        // Four in a row, nearest first; the rest wait in the full list the header link opens.
        val shown = offers.take(SLOTS)
        addView(row().apply {
            gravity = Gravity.TOP
            val gap = if (wide) 16 else 20
            shown.forEachIndexed { i, offer -> addView(if (wide) offerLine(offer, task, located != null) else offerCard(offer, task, located != null), LinearLayout.LayoutParams(0, if (wide) dp(124) else -2, 1f).apply { if (i > 0) marginStart = dp(gap) }) }
            repeat(SLOTS - shown.size) { addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(gap) }) }
        }, margin(if (wide) 4 else 16))
    }

    /** Where the job happens, in a few words: at the car, at the shop with its distance, or at home. */
    private fun where(mode: ServiceMode, offer: Offer, located: Boolean) = when (mode) {
        ServiceMode.VISIT -> "집·회사로 방문"
        ServiceMode.SHOP -> offer.distanceKm?.takeIf { located }?.let { String.format(java.util.Locale.KOREA, "정비소 · %.1f km", it) } ?: "예시 정비소"
        ServiceMode.SELF -> "집으로 배송"
    }

    /**
     * One provider as a card: room for the shop's photo, its name under it and one quiet line (rating, and distance for a
     * shop). The job is the same for every card, so it waits in the booking popup the whole card opens.
     */
    private fun offerCard(offer: Offer, task: ServiceAction, located: Boolean, onPick: (() -> Unit)? = null) = col().apply {
        isClickable = true
        foreground = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(androidx_alpha(p.title, 20)), null, round(Color.WHITE, Radius.SURFACE))
        contentDescription = "${offer.provider}, 별점 ${offer.rating}, ${where(offer.mode, offer, located)}, ${if (offer.mode == ServiceMode.SELF) "주문" else "예약"}"
        setOnClickListener { if (!restricted) { onPick?.invoke(); book(offer) } }
        // A text card on a filled surface: no photo slot waiting for a picture that does not exist yet.
        background = round(p.fill, Radius.SURFACE); setPadding(dp(20), dp(20), dp(20), dp(20))
        addView(text(offer.provider, TypeScale.LABEL, true).apply { setPadding(0, 0, 0, 0); maxLines = 2; minLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        val place = when (offer.mode) { ServiceMode.SHOP -> offer.distanceKm?.takeIf { located }?.let { String.format(java.util.Locale.KOREA, "%.1f km", it) }
            ServiceMode.SELF -> offer.tags.firstOrNull(); else -> offer.tags.firstOrNull() }
        addView(text(listOfNotNull("★ ${offer.rating}", place).joinToString("  ·  "), TypeScale.LABEL_SMALL, tint = p.sub).apply {
            setPadding(0, 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, margin(4))
        addView(text(won(offer.total), TypeScale.LABEL_SMALL, false, p.body).apply { setPadding(0, 0, 0, 0); fontFeatureSettings = "tnum" }, margin(14))
    }

    /** The same provider in the brake detail's lower band: name and chevron, then rating and what the job costs there. */
    private fun offerLine(offer: Offer, task: ServiceAction, located: Boolean) = col().apply {
        gravity = Gravity.CENTER_VERTICAL; isClickable = true
        foreground = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(androidx_alpha(p.title, 20)), null, round(Color.WHITE, Radius.SURFACE))
        contentDescription = "${offer.provider}, 별점 ${offer.rating}, ${where(offer.mode, offer, located)}, ${if (offer.mode == ServiceMode.SELF) "주문" else "예약"}"
        setOnClickListener { if (!restricted) book(offer) }
        background = round(p.fill, Radius.SURFACE); setPadding(dp(20), dp(12), dp(16), dp(12))
        addView(row().apply {
            addView(text(offer.provider, TypeScale.LABEL, true).apply { setPadding(0, 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(chevron(p.sub, 28), LinearLayout.LayoutParams(dp(28), dp(28)))
        })
        addView(row().apply {
            val place = offer.distanceKm?.takeIf { located && offer.mode == ServiceMode.SHOP }?.let { String.format(java.util.Locale.KOREA, "  ·  %.1f km", it) } ?: ""
            addView(text("★ ${offer.rating}$place", TypeScale.LABEL, tint = p.sub).apply { setPadding(0, 0, 0, 0); maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text("${task.label} ${won(offer.total)}", TypeScale.LABEL, true).apply { setPadding(0, 0, 0, 0); fontFeatureSettings = "tnum"; maxLines = 1 })
        }, margin(12))
    }

    /**
     * Every provider for this way of doing the job, in a popup with a search box and a sort, as a grid of the same cards.
     * Typing waits for a stop: the search box is an input like any other.
     */
    private fun providerSheet(mode: ServiceMode, offers: List<Offer>, task: ServiceAction, located: Boolean) {
        var dialog: Dialog? = null
        val sorts = if (mode == ServiceMode.SHOP && located) listOf("가까운 순", "별점 순", "리뷰 많은 순") else listOf("별점 순", "리뷰 많은 순")
        var sort = 0; var query = ""
        val grid = col()
        val count = text("", TypeScale.LABEL, tint = p.sub).apply { setPadding(0, 0, 0, 0) }
        fun fill() {
            grid.removeAllViews()
            val list = offers.filter { o -> query.isBlank() || o.provider.contains(query.trim(), true) || o.tags.any { it.contains(query.trim(), true) } }
                .sortedWith(when (sorts[sort]) { "가까운 순" -> compareBy { it.distanceKm ?: 0.0 }; "별점 순" -> compareByDescending { it.rating }; else -> compareByDescending<Offer> { it.reviews } })
            count.text = "${list.size}곳 · 예시"
            if (list.isEmpty()) grid.addView(text("'${query.trim()}'에 맞는 업체가 없어요", TypeScale.BODY, tint = p.sub).apply { gravity = Gravity.CENTER; setPadding(0, dp(48), 0, dp(48)) }, margin())
            list.chunked(3).forEachIndexed { r, chunk ->
                grid.addView(row().apply {
                    gravity = Gravity.TOP
                    chunk.forEachIndexed { i, o -> addView(offerCard(o, task, located) { dialog?.dismiss() }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = dp(20) }) }
                    repeat(3 - chunk.size) { addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(20) }) }
                }, margin(if (r == 0) 0 else 28))
            }
        }
        lateinit var sortRow: LinearLayout
        fun sortTabs(): View = segmented(sorts, sort, dp(64)) { sort = it; sortRow.removeAllViews(); sortRow.addView(sortTabs()); fill() }
        val body = col().apply {
            addView(row().apply {
                addView(text("${mode.label} 업체", TypeScale.TITLE, true).apply { setPadding(0, 0, 0, 0) })
                addView(count.apply { setPadding(dp(16), dp(6), 0, 0) })
            })
            addView(EditText(this@MainActivity).apply {
                hint = "업체 이름이나 특징으로 찾기"; contentDescription = "업체 검색"; textSize = TypeScale.BODY.toFloat(); setTextColor(ink); setHintTextColor(muted)
                setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT; imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
                minHeight = dp(76); setPadding(dp(28), 0, dp(28), 0); background = round(p.fill, Radius.SURFACE); filters = arrayOf(InputFilter.LengthFilter(20))
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); fill() }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }, margin(24))
            sortRow = row()
            sortRow.addView(sortTabs())
            addView(sortRow, margin(8))
            addView(ScrollView(this@MainActivity).apply { isVerticalScrollBarEnabled = false; addView(grid) },
                LinearLayout.LayoutParams(-1, dp(minOf(560, resources.configuration.screenHeightDp - 420))).apply { topMargin = dp(8) })
        }
        fill()
        dialog = sheet(body, emptyList(), width = 1040)
    }

    /** The guide's Line tabs: labels in a row, the chosen one darker with a short bar under it. */
    private fun segmented(labels: List<String>, selected: Int, height: Int = dp(76), pick: (Int) -> Unit) = row().apply {
        labels.forEachIndexed { i, l ->
            addView(text(l, TypeScale.META, false, if (i == selected) p.title else p.sub).apply {
                typeface = Fonts.of(if (i == selected) 700 else 400); gravity = Gravity.CENTER
                setPadding(dp(4), 0, dp(4), 0); isClickable = true
                contentDescription = if (i == selected) "$l, 선택됨" else l
                // The bar is a bottom layer of the label's own background, so it is exactly as wide as the label.
                if (i == selected) background = android.graphics.drawable.LayerDrawable(arrayOf(GradientDrawable().apply { setColor(p.title) })).apply {
                    setLayerGravity(0, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL); setLayerHeight(0, dp(3))
                }
                setOnClickListener { if (i != selected && !restricted) pick(i) }
            }, LinearLayout.LayoutParams(-2, height).apply { if (i > 0) marginStart = dp(32) })
        }
    }

    private fun route(offer: Offer) {
        if (!VehiclePlaces.granted(this, VehiclePlaces.ROUTE_PERMISSION)) { pendingRoute = offer; ask(VehiclePlaces.ROUTE_PERMISSION, 104); return }
        VehiclePlaces.navigate(this, offer) { toast(it) }
    }
    private var pendingRoute: Offer? = null

    private fun assessmentTask() = status(part).task ?: (store.plans[part] ?: RecommendedCare.plan(part)).action

    /** The booking sheet: when, what it costs line by line, how it is paid, then book or route. */
    private fun book(offer: Offer) {
        var slot = offer.slots.first()
        val body = col()
        body.addView(text(offer.provider, TypeScale.TITLE, true).apply { setPadding(0, 0, 0, 0) })
        // Every provider and price is a sample until partners sign; the popup that takes the booking says so first.
        body.addView(text(ServiceCatalog.SAMPLE, TypeScale.META, true, p.body).apply { setPadding(0, dp(6), 0, 0) })
        body.addView(text(listOf(ServiceCatalog.job(part, assessmentTask()), where(offer.mode, offer, VehiclePlaces.location != null), "★ ${offer.rating}").joinToString("  ·  "), TypeScale.LABEL, tint = p.sub))
        body.addView(text(if (offer.mode == ServiceMode.SELF) "도착 예정" else "방문 시간", TypeScale.LABEL, true, p.body), margin(20))
        val slots = row()
        fun paintSlots() {
            slots.removeAllViews()
            offer.slots.forEachIndexed { i, t ->
                slots.addView(text(when2(t), TypeScale.LABEL, t == slot, if (t == slot) p.onPrimary else p.title).apply {
                    gravity = Gravity.CENTER; setPadding(dp(28), 0, dp(28), 0); background = round(if (t == slot) p.primary else p.fill, Radius.SURFACE)
                    setOnClickListener { slot = t; paintSlots() }
                }, LinearLayout.LayoutParams(-2, dp(76)).apply { if (i > 0) marginStart = dp(12) })
            }
        }
        paintSlots()
        body.addView(slots, margin(8))
        body.addView(View(this).apply { setBackgroundColor(p.divider) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(24) })
        offer.lines.forEach { l -> body.addView(row().apply {
            addView(text(l.name, TypeScale.LABEL, tint = p.body), LinearLayout.LayoutParams(0, -2, 1f)); addView(text(won(l.won), TypeScale.LABEL, false, p.title))
        }, margin(if (l == offer.lines.first()) 14 else 0)) }
        body.addView(row().apply {
            addView(text("합계", TypeScale.BODY, true), LinearLayout.LayoutParams(0, -2, 1f)); addView(text(won(offer.total), TypeScale.TITLE, true))
        }, margin(8))
        body.addView(text(if (offer.mode == ServiceMode.SELF) "결제는 주문할 때 해요" else "결제는 현장에서 해요", TypeScale.META, false, p.sub), margin(12))
        sheet(body, listOfNotNull(
            if (offer.lat != null) "길안내" to { route(offer) } else null,
            (if (offer.mode == ServiceMode.SELF) "주문하기" else "예약하기") to {
                if (change { store.bookings[part] = Booking.of(offer, slot) }) { render(); toast("${when2(slot)} ${offer.provider} ${if (offer.mode == ServiceMode.SELF) "주문" else "예약"}을 저장했어요(예시)") }
            }))
    }

    /**
     * The guide's system popup in place of the platform dialog: Medium 760 dp or Large 1,200 dp wide, 48 dp corners,
     * content, then 100 dp buttons in a row with the filled confirm action first and cancel last. Every button closes
     * the popup before acting.
     */
    private fun sheet(body: View, buttons: List<Pair<String, () -> Unit>>, cancel: String? = "닫기", width: Int = 760): Dialog {
        val dialog = Dialog(this)
        val confirm = buttons.lastOrNull()
        val all = listOfNotNull(confirm) + buttons.dropLast(1) + listOfNotNull(cancel?.let { it to {} })
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(col().apply {
            setPadding(dp(48), dp(44), dp(48), dp(48)); background = round(surface, Radius.POPUP)
            addView(body)
            addView(row().apply {
                all.forEachIndexed { i, (label, action) ->
                    // Closing is never the filled action: a popup with nothing to confirm gets a plain close button.
                    addView(actionButton(label, i == 0 && confirm != null) { dialog.dismiss(); action() }.apply { textSize = TypeScale.BODY.toFloat() },
                        LinearLayout.LayoutParams(0, dp(88), 1f).apply { if (i > 0) marginStart = dp(16) })
                }
            }, margin(36))
        })
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        present(dialog)
        dialog.window?.setLayout(dp(minOf(width, resources.configuration.screenWidthDp - 48)), ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    /** One choice from a short list, as rows in a sheet. */
    private fun choose(title: String, options: List<String>, pick: (Int) -> Unit) {
        var dialog: Dialog? = null
        val body = col().apply {
            addView(text(title, TypeScale.TITLE, true).apply { setPadding(0, 0, 0, dp(8)) })
            options.forEachIndexed { i, o ->
                if (i > 0) addView(View(this@MainActivity).apply { setBackgroundColor(p.divider) }, LinearLayout.LayoutParams(-1, 1))
                addView(listRow(o, null) { dialog?.dismiss(); pick(i) })
            }
        }
        dialog = sheet(body, emptyList())
    }

    /** A reading popup: the title, the text in a box that scrolls once it is long, and the source as a link under it. */
    private fun explain(title: String, body: String, link: Pair<String, String>? = null) {
        val most = dp(minOf(620, resources.configuration.screenHeightDp - 360))
        sheet(col().apply {
            addView(text(title, TypeScale.TITLE, true).apply { setPadding(0, 0, 0, 0) })
            addView(object : ScrollView(this@MainActivity) {
                override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(most, View.MeasureSpec.AT_MOST))
            }.apply { addView(text(body, TypeScale.BODY, tint = p.body).apply { setPadding(0, 0, dp(24), 0); setLineSpacing(dp(6).toFloat(), 1f) }) }, margin(20))
            link?.let { (label, url) -> addView(source(label, url), margin(8)) }
        }, emptyList(), width = 1200)
    }

    /**
     * A source as text first: the address is always printed, because a car may have no browser, or one that refuses to
     * start over a driving screen without telling the app. The link to open it is offered only where something can.
     */
    private fun source(label: String, url: String): View = col().apply {
        val view = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        val opens = packageManager.queryIntentActivities(view, 0).isNotEmpty()
        addView(text("출처 " + url.removePrefix("https://"), TypeScale.META, tint = p.sub).apply { setPadding(0, 0, 0, 0); setTextIsSelectable(true) })
        if (opens) addView(textLink(label, p.title) { runCatching { startActivity(view) }.onFailure { toast("이 차에서는 주소를 열 수 없어요. 위 주소를 휴대폰에서 열어 주세요") } }
            .apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
    }

    private fun quiet(label: String, action: () -> Unit) = actionButton(label, false, action).apply { textSize = TypeScale.LABEL.toFloat() }

    private fun won(v: Int) = if (v == 0) "무료" else "${fmt(v.toLong())}원"
    private fun when2(t: java.time.LocalDateTime): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), t.toLocalDate())
        val day = when (days) { 0L -> "오늘"; 1L -> "내일"; 2L -> "모레"; else -> "${t.monthValue}월 ${t.dayOfMonth}일" }
        return String.format(java.util.Locale.KOREA, "%s %d:%02d", day, t.hour, t.minute)
    }
    private val divider get() = p.divider
    private companion object { const val SLOTS = 4 }

    /**
     * One figure as the driver needs to read it: what it is and what is left on the first line; then, when there is a
     * limit, a bar of how much of it is used with the reading on the left end and the limit on the right.
     */
    private fun factorView(f: Factor, tone: Int) = col().apply {
        setPadding(0, dp(10), 0, dp(10))
        addView(row().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(f.title, TypeScale.DETAIL, false, p.title).apply { setPadding(0, 0, 0, 0); maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            if (f.alert) addView(dot(tone, 12), LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(12) })
            addView(text(f.value, TypeScale.DETAIL, true, p.title).apply { setPadding(0, 0, 0, 0); fontFeatureSettings = "tnum"; maxLines = 1 })
        })
        val bar = f.fraction
        if (bar != null) {
            addView(MeterBar(this@MainActivity, bar, if (f.alert) tone else p.primary, p.track, f.marker, f.extra), LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(10) })
            if (f.now.isNotEmpty() || f.limit.isNotEmpty()) addView(row().apply {
                addView(text(f.now, TypeScale.META, false, p.sub).apply { setPadding(0, 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; fontFeatureSettings = "tnum" },
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(text(f.limit, TypeScale.META, false, p.sub).apply { setPadding(dp(16), 0, 0, 0); maxLines = 1; fontFeatureSettings = "tnum" })
            }, margin(6))
        } else {
            // A row without a bar keeps the bar's room, so every row is the same height and the list never shifts when a
            // reading appears or goes; its note sits where the other rows' reading and limit sit.
            addView(View(this@MainActivity), LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(10) })
            addView(text(f.note.ifEmpty { " " }, TypeScale.META, false, p.sub).apply { setPadding(0, 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, margin(6))
        }
    }

    private class Gauge(val label: String, val value: String, val unit: String, val low: Float? = null, val high: Float? = null,
        val caption: String = "", val bandLow: Float? = null, val bandHigh: Float? = null)
    private class PartData(val gauges: List<Gauge>, val rows: List<Pair<String, String>>, val state: String, val footer: String,
        val warnings: List<String> = emptyList(), val prompt: Pair<String, View>? = null)

    /** A change against the post-service reference; until the reference exists the arc shows how much of it is collected. */
    private fun baselineOr(label: String, value: String?, unit: String, sessions: Int, needed: Int, caption: String) =
        if (value != null) Gauge(label, value, unit, caption = caption)
        else minOf(sessions, needed).let { got -> Gauge(label, "$got/$needed", "기준 수집 중", got.toFloat() / needed, caption = caption) }

    /** The guide's basic button: filled basic_600 for the main action, the pale alpha fill for the rest. */
    private fun actionButton(label: String, primary: Boolean, action: () -> Unit) = button(label, primary, action).apply {
        textSize = TypeScale.BODY.toFloat(); minHeight = 0; minimumHeight = 0; minWidth = dp(200); setPadding(dp(36), 0, dp(36), 0); typeface = Fonts.of(700)
        if (primary) { background = round(p.primary, Radius.SURFACE); setTextColor(p.onPrimary) } else { background = round(p.primaryTint, Radius.SURFACE); setTextColor(p.title) }
    }

    private fun androidx_alpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /** The measurements behind the guidance, in a Large popup: the research figures as tiles, then what the car counted. */
    private fun showMeasurements(data: PartData) {
        val body = col()
        body.addView(row().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("측정값", TypeScale.TITLE, true).apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(data.state, TypeScale.META, false, p.sub))
        })
        if (data.gauges.isEmpty() && data.rows.isEmpty()) body.addView(text("첫 운행 뒤부터 채워져요. 차에서 잰 값이 생기면 여기에 보여 드려요.", TypeScale.BODY, tint = p.body), margin(20))
        if (data.gauges.isNotEmpty()) body.addView(row().apply {
            gravity = Gravity.TOP
            data.gauges.forEachIndexed { i, g ->
                addView(col().apply {
                    setPadding(dp(28), dp(22), dp(28), dp(24)); background = round(p.fill, Radius.SURFACE)
                    addView(text(g.label, TypeScale.LABEL, tint = p.body).apply { setPadding(0, 0, 0, 0) })
                    addView(row().apply {
                        gravity = Gravity.BOTTOM
                        addView(text(g.value, 40, true).apply { setPadding(0, 0, 0, 0) })
                        if (g.unit.isNotEmpty()) addView(text(g.unit, TypeScale.LABEL, tint = p.sub).apply { setPadding(dp(8), 0, 0, dp(6)) })
                    }, margin(6))
                    g.low?.let { lo -> addView(MeterBar(this@MainActivity, lo, p.ok, p.card, null), LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(12) }) }
                    if (g.caption.isNotEmpty()) addView(text(g.caption, TypeScale.LABEL_SMALL, tint = p.sub).apply { setPadding(0, dp(10), 0, 0) })
                }, LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) marginStart = dp(16) })
            }
        }, margin(24))
        if (data.rows.isNotEmpty()) {
            body.addView(text("차에서 잰 값", TypeScale.LABEL, true, p.body), margin(24))
            data.rows.chunked(2).forEach { pair ->
                body.addView(row().apply {
                    pair.forEachIndexed { i, (label, value) ->
                        addView(col().apply {
                            addView(text(label, TypeScale.LABEL_SMALL, tint = p.sub).apply { setPadding(0, 0, 0, 0) })
                            addView(text(value, TypeScale.BODY, true).apply { setPadding(0, dp(2), 0, 0) })
                        }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = dp(32) })
                    }
                    if (pair.size == 1) addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = dp(32) })
                }, margin(12))
            }
        }
        body.addView(text(data.footer, TypeScale.LABEL_SMALL, tint = p.sub), margin(16))
        sheet(body, emptyList(), width = 1200)
    }

    /** What the car reported for this part. Shown beside, never instead of, the schedule. */
    private fun partData(record: ServiceRecord?): PartData? {
        val since = if (record != null) "최근 ${record.action.label} 이후" else "기록 시작 이후"
        val k = java.util.Locale.KOREA
        val connect = { note: String, label: String, permission: String, code: Int ->
            note to quietButton(label) { ask(permission, code) }.apply { background = round(surface, 20) } }
        if (part == Part.ENGINE_OIL) {
            val odo = record?.odometerKm?.let { at -> store.mileage?.km?.let { (it - at).coerceAtLeast(0).toDouble() } }
            val sum = VehicleSignals.oilTrips().summary(record?.id, odo)
            val km = { v: Double -> String.format(k, "%,.0f", v) }
            val unseen = if (sum.unseenKm >= 1) "  ·  앱 밖 ${km(sum.unseenKm)} km는 통상 조건" else ""
            return PartData(
                listOf(Gauge("통상 ${km(OilModel.normalKm())} km 대비", span(pct(sum.load.first * 100), pct(sum.load.second * 100)), "%",
                        sum.load.first.toFloat(), sum.load.second.toFloat(), "환산 ${span(km(sum.effectiveKm.first), km(sum.effectiveKm.second))} km · 예열 9~13분 가정"),
                    Gauge("연료 희석 추정", sum.dilution?.let { String.format(k, "%.1f", it) } ?: "-", "%",
                        sum.dilution?.let { (it / OilModel.CHANGE_PCT).toFloat() }, caption = "주의 ${OilModel.CAUTION_PCT.toInt()}% · 교체 ${OilModel.CHANGE_PCT.toInt()}%")),
                listOf("앱이 본 운행" to "${sum.trips}회 · ${km(sum.observedKm)} km", "짧은 운행" to "${sum.short + sum.shortCold}회 (영하 ${sum.shortCold}회)",
                    "최근 오일 온도 추정" to (VehicleSignals.oilTrips().trips.lastOrNull()?.peakOilC?.let { "최고 ${Math.round(it)}°C" } ?: "-"), "공회전" to "${Math.round(sum.idleMinutes)}분",
                    "공회전 비율" to (if (sum.driveMinutes < 1) "-" else "${Math.round(sum.idleMinutes * 100 / sum.driveMinutes)}%"),
                    "엔진 오일량" to (VehicleSignals.oilLevel() ?: "미제공")),
                VehicleSignals.oilState(), "$since$unseen",
                warnings = listOfNotNull(VehicleSignals.oilLevel()?.takeIf { it != "정상" }?.let { "엔진 오일량 $it" },
                    if (store.severe.isNotEmpty()) "가혹 조건 ${store.severe.size}개 선택" else null),
                prompt = if (VehicleSignals.speedGranted(this)) null
                    else connect("차속을 연결하면 운행 길이를 재요 · 허용 창에는 위치로 표시돼요", "차속 연결", VehicleSignals.SPEED_PERMISSION, 101))
        }
        if (part == Part.TIRE) return tireData(connect)
        val usage = Usage.of(part) ?: return null
        val (amount, _) = VehicleSignals.usage(usage).since(record?.id)
        val usageText = if (usage.counts) "${fmt(amount)}회" else durationParts(amount).let { (v, u) -> "$v$u" }
        val footer = "$since  ·  ${if (SignalService.enabled(this)) "앱을 닫아도 기록" else "앱 화면 표시 중 기록"}"
        return when (part) {
            Part.BRAKE_PAD -> {
                val bw = VehicleSignals.brakeWork()
                val (work, distance, _) = bw.since(record?.id)
                val (low, high) = bw.padWornMm(record?.id)
                val perKm = if (distance >= 1000) work / distance else null
                val ref = CompositeJudge.brakeReferenceKjPerKm()
                val scale = maxOf(300.0, ref * 2.5)
                val bp = BrakeWearModel.params()
                if (!VehicleSignals.speedGranted(this)) PartData(emptyList(), listOf(usage.label to usageText), VehicleSignals.state(usage), footer,
                    prompt = connect("차속을 연결하면 패드 마모를 추정해요 · 허용 창에는 위치로 표시돼요", "차속 연결", VehicleSignals.SPEED_PERMISSION, 101))
                else PartData(
                    listOf(Gauge("앞 패드 마모 추정", wearRange(low, high), "mm", caption = "마찰 제동 ${Math.round(bp.frictionShare * 100)}% · 디스크 온도 반영"),
                        Gauge("1 km당 제동 에너지", perKm?.let { "${Math.round(it)}" } ?: "-", "kJ/km", perKm?.let { (it / scale).toFloat() },
                            caption = "이 차 보통 ${Math.round(ref)} (WLTP 제동 시험)", bandLow = (ref * 0.7 / scale).toFloat(), bandHigh = (ref * 1.3 / scale).toFloat())),
                    listOf(usage.label to usageText, "제동 에너지" to energy(work).let { (v, u) -> "$v $u" },
                        "주행" to String.format(k, "%,.1f km", distance / 1000),
                        "앞 디스크 온도 추정" to (bw.discC?.let { "${Math.round(it)}°C" } ?: "-"),
                        VehicleSignals.brakeWork().holdsSince(record?.id).let { h ->
                            "속도 유지 제동" to (if (h.count == 0) "없음" else String.format(k, "%d회 · %.1f분 · 상한 추정에만 반영", h.count, h.seconds / 60)) },
                        "브레이크액" to (VehicleSignals.brakeFluid() ?: "차량 미제공"), sportRow()),
                    VehicleSignals.state(usage), footer,
                    warnings = listOfNotNull(if (SevereUse.checks(part, store.severe)) "가혹 조건 · 수시 점검" else null))
            }
            Part.WIPER -> {
                val (low, high, _) = VehicleSignals.wiperCycles().since(record?.id)
                val budget = WiperModel.CYCLE_BUDGET
                val mic = VehicleSignals.micGranted(this)
                val (change, sessions) = VehicleSignals.wiperSound().change(record?.id)
                val shift = VehicleSignals.wiperSound().shift(record?.id)
                val cleaning = VehicleSignals.wiperSound().sinceCleaning(record?.id)
                val today = LocalDate.now()
                val heavy = record?.let { CompositeJudge.bladeWipeDays(it, today, VehicleSignals.wiperCycles()) } ?: 0.0
                val days = record?.date?.let { from ->
                    val seen = VehicleSignals.outsideDays()
                    Triple(RubberAging.equivalentDays(from, today, seen, RubberAging.EA_LOW) + heavy, RubberAging.equivalentDays(from, today, seen, RubberAging.EA_HIGH) + heavy,
                        java.time.temporal.ChronoUnit.DAYS.between(from, today))
                }
                val level = mapOf("LOW" to "저속", "HIGH" to "고속", "INTERMITTENT" to "간헐", "AUTO" to "자동")
                // One count, the lower bound that ages the blade, against what a typical driver wipes in the same days.
                val onCar = days?.third ?: 0L
                val typical = WiperModel.TYPICAL_PER_YEAR * onCar / 365
                PartData(
                    listOfNotNull(if (onCar >= 30) Gauge("닦은 횟수", fmt(Math.round(low / 100) * 100), "회", (low / typical / 2).toFloat(),
                            caption = "같은 기간 보통 운전자 ${fmt(Math.round(typical / 100) * 100)}회 · ${Math.round((low / typical - 1) * 100).let { if (it >= 0) "$it% 많음" else "${-it}% 적음" }}")
                        else Gauge("닦은 횟수", fmt(Math.round(low)), "회", (onCar / 30f), caption = "30일 뒤 보통과 비교"),
                        if (!mic) null
                        else if (shift?.confirmed == true) Gauge("닦은 뒤 작동 소리", String.format(k, "%+.1f", shift.db), "dB", caption = "${level[shift.level] ?: "같은 단"} · 닦은 뒤 ${WiperSound.CONFIRM}회 모두 교체 직후보다 큼")
                        else if (cleaning != null) Gauge("닦은 뒤 소리 확인", "$cleaning/${WiperSound.CONFIRM}", "회", cleaning.toFloat() / WiperSound.CONFIRM, caption = "같은 속도로 작동할 때 비교")
                        else if (shift != null) Gauge("작동 소리 증가", String.format(k, "%+.1f", shift.db), "dB", caption = "${level[shift.level] ?: "같은 단"} · 최근 ${WiperSound.RECENT}회 모두 교체 직후보다 큼")
                        else baselineOr("작동 소리 변화", change?.let { String.format(k, "%+.1f", it) }, "dB", sessions, WiperSound.BASELINE, "1~10 kHz · 교체 직후 대비")),
                    listOfNotNull(usage.label to usageText, "마이크" to VehicleSignals.micState(),
                        days?.let { (lo, hi, real) -> "블레이드 노화 환산" to "${span(fmt(Math.round(lo)), fmt(Math.round(hi)))}일 · 실제 ${real}일" },
                        "햇볕 주차" to "${VehicleSignals.outsideDays().days.values.count { it.soakC != null }}일",
                        "기온 기록" to "${VehicleSignals.outsideDays().days.size}일"),
                    VehicleSignals.state(usage), footer,
                    warnings = listOfNotNull(if (VehicleSignals.washer()?.on == true) "워셔액 부족" else null),
                    prompt = if (mic) null else connect("와이퍼가 움직일 때만 소리를 분석해요 · 소리는 저장하지 않아요", "마이크 연결", android.Manifest.permission.RECORD_AUDIO, 102))
            }
            Part.CABIN_FILTER -> {
                val fresh = VehicleSignals.usage(Usage.FRESH).since(record?.id).first
                val recirc = VehicleSignals.usage(Usage.RECIRC).since(record?.id).first
                val share = (VehicleSignals.filterDust().since(record?.id) ?: 0.0) / FilterModel.END_OF_SERVICE_HOURS
                val recircShare = if (fresh + recirc == 0L) null else recirc.toDouble() / (fresh + recirc)
                PartData(
                    listOf(Gauge("먼지 적재 추정", pct(share * 100), "%", share.toFloat(), caption = "외기 송풍 ${durationParts(fresh).let { (v, u) -> "$v$u" }} · ${FilterModel.province(VehiclePlaces.location)?.first?.let { "$it 미세먼지" } ?: "전국 평균 미세먼지"}"),
                        Gauge("내기 순환 비율", recircShare?.let { "${Math.round(it * 100)}" } ?: "-", "%", recircShare?.toFloat(), caption = "송풍 시간 기준")),
                    listOf(usage.label to usageText, "흡입 상태" to when (VehicleSignals.climate().recirc) { true -> "내기 순환"; false -> "외기 유입"; null -> "미확인" }),
                    VehicleSignals.state(usage), "$footer  ·  Wei 외(2020) 상하이 500시간 먼지량 기준",
                    warnings = listOfNotNull(if (SevereUse.checks(part, store.severe)) "먼지 많은 지역 · 수시 점검" else null))
            }
            Part.HVAC -> {
                val lost = record?.date?.let { RefrigerantModel.lost(it, LocalDate.now()) }
                val (latest, _, _) = VehicleSignals.cooldown().summary(record?.id)
                val temp = { v: Double? -> v?.let { String.format(k, "%.1f°C", it) } ?: "-" }
                // Cooling and heating are timed alike: share of the start gap closed in the probe minutes, against the sessions after the check.
                val recoveryGauge = { t: ClimateTracker, label: String ->
                    val (recovery, n) = t.recovery(record?.id)
                    val caption = "점검 직후 같은 조건 대비"
                    if (recovery != null) Gauge(label, "${Math.round(recovery * 100)}", "%", recovery.toFloat().coerceIn(0f, 1f), caption = caption)
                    else if (n > ClimateTracker.BASELINE) Gauge(label, "-", "%", caption = "같은 조건의 세션 대기")
                    else baselineOr(label, null, "%", n, ClimateTracker.BASELINE, caption)
                }
                PartData(
                    listOf(Gauge("냉매 감소 추정", lost?.let { (a, b) -> span(pct(a * 100), pct(b * 100)) } ?: "-", "%",
                            caption = lost?.let { (a, b) -> RefrigerantModel.chargeG(CarModel.current).let { g -> "${g} g 중 ${span(fmt(Math.round(a * g)), fmt(Math.round(b * g)))} g · 사고 손실 제외" } } ?: "점검 기록 후 계산"),
                        recoveryGauge(VehicleSignals.cooldown(), "${ClimateTracker.PROBE_MINUTES.toInt()}분 냉방 회복"),
                        recoveryGauge(VehicleSignals.heatup(), "${ClimateTracker.HEAT_PROBE_MINUTES.toInt()}분 난방 회복")),
                    listOf(usage.label to usageText, "최근 냉방 속도" to (latest?.let { String.format(k, "%.1f°C/분", it) } ?: "측정 대기"),
                        "실내" to temp(VehicleSignals.climate().cabinC), "외기" to temp(VehicleSignals.outsideC())),
                    VehicleSignals.state(usage), footer)
            }
            else -> null
        }
    }

    /** Rotation distance and driving dynamics; tread loss only once a new set was recorded with its odometer. */
    private fun tireData(connect: (String, String, String, Int) -> Pair<String, View>): PartData {
        val k = java.util.Locale.KOREA
        val km = { v: Double -> String.format(k, "%,.0f", v) }
        val sd = { v: Double? -> v?.let { String.format(k, "%.2f m/s²", it) } }
        val rotation = Rotation.of(store.records, store.mileage, VehicleSignals.tireLoad())
        val base = Rotation.baseline(store.records)
        val stats = rotation?.stats ?: VehicleSignals.tireLoad().since(base?.id)
        val rotationGauge = if (rotation == null) Gauge("위치 교환 1만 km 대비", "-", "%", caption = if (base == null) "위치 교환 기록 후 계산" else "주행거리 대기")
            else Gauge("위치 교환 1만 km 대비", pct(rotation.share * 100), "%", rotation.share.toFloat(),
                caption = if (rotation.broughtForward) "환산 ${km(rotation.equivalentKm)} km · 실제 ${km(rotation.sinceKm)} km" else "${rotation.baseline.action.label} 후 ${km(rotation.sinceKm)} km")
        val lateral = stats.lateralSd
        val scale = 2.0
        val dynamics = when {
            stats.distanceM < TireModel.MIN_OBSERVED_M -> (stats.distanceM / TireModel.MIN_OBSERVED_M).let { got ->
                Gauge("마모 가중", "${km(stats.distanceM / 1000)}/${km(TireModel.MIN_OBSERVED_M / 1000)}", "km 수집 중", got.toFloat(), caption = "50 km부터 계산") }
            else -> stats.factor!!.let { f ->
                Gauge("마모 가중", String.format(k, "%.2f", f), "배", (f / scale).toFloat(), caption = if (lateral == null) "좌우는 UN 규정 117 기준값으로 가정" else "UN 규정 117 보통 주행 = 1",
                    bandLow = (0.9 / scale).toFloat(), bandHigh = (1.1 / scale).toFloat()) }
        }
        val tread = TireTread.of(store.records, store.mileage, VehicleSignals.tireLoad())
            ?.let { t -> String.format(k, "앞 %.1f · 뒤 %.1f mm", t.front, t.rear) } ?: "교체 기록 후 계산"
        val since = base?.let { "최근 ${it.action.label} 이후" } ?: "기록 시작 이후"
        val granted = VehicleSignals.speedGranted(this)
        return PartData(
            listOf(rotationGauge, dynamics),
            listOf("앞뒤 가감속" to (sd(stats.longitudinal.sd.takeIf { stats.distanceM > 0 }) ?: "-"),
                "좌우 가속" to (sd(lateral) ?: if (stats.lateral.distanceM > 0) "수집 중" else "측정 안 됨"),
                "앱이 본 주행" to "${km(stats.distanceM / 1000)} km",
                "최근 위치 교환" to (base?.let { "${it.date.toString().replace('-', '.')} · ${fmt(it.odometerKm!!)} km" } ?: "기록 없음"),
                "홈 깊이 추정" to tread, sportRow(), "주행 중 평균 기온" to (VehicleSignals.tireLoad().warmthSince(base?.id)?.let { "${Math.round(it)}°C" } ?: "-"),
                "공기압" to "일반 앱 미제공"),
            if (granted) VehicleSignals.wheelState() else VehicleSignals.speedState(),
            "$since  ·  Giechaskiel 외(2024), UN 규정 117",
            prompt = if (granted) null else connect("차속을 연결하면 가감속과 회전으로 타이어 마모 가중을 재요 · 허용 창에는 위치로 표시돼요", "차속 연결", VehicleSignals.SPEED_PERMISSION, 101))
    }

    private fun span(a: String, b: String) = if (a == b) a else "$a~$b"
    /** Share of distance in the car's sport mode, for context beside braking and tire load; it does not change a judgment. */
    private fun sportRow() = "스포츠 모드 주행" to (VehicleSignals.driveModes().sportShare?.let { "${Math.round(it * 100)}%" } ?: "주행 모드 대기")
    /** Enough decimals that a small share does not print as zero. */
    private fun pct(v: Double) = String.format(java.util.Locale.KOREA, if (v >= 10) "%.0f" else if (v >= 1) "%.1f" else "%.2f", v)
    private fun durationParts(seconds: Long) = when {
        seconds < 60 -> "$seconds" to "초"
        seconds < 3600 -> "${seconds / 60}" to "분"
        else -> "${seconds / 3600}시간 ${seconds % 3600 / 60}" to "분"
    }
    private fun energy(joules: Double) = when {
        joules >= 1e9 -> String.format(java.util.Locale.KOREA, "%.2f", joules / 1e9) to "GJ"
        joules >= 1e6 -> String.format(java.util.Locale.KOREA, "%.2f", joules / 1e6) to "MJ"
        else -> fmt(Math.round(joules / 1e3)) to "kJ"
    }
    /** Three decimals while wear is still in micrometres, so early figures are not all "0.00". */
    private fun wearRange(low: Double, high: Double): String {
        if (high < 0.0005) return "<0.001"
        val f = if (high < 0.1) "%.3f" else "%.2f"
        return String.format(java.util.Locale.KOREA, f, low) + "~" + String.format(java.util.Locale.KOREA, f, high)
    }

    /** The service date as a month of days in the app's popup. Days after today cannot be picked. */
    private fun pickServiceDate() {
        val today = LocalDate.now()
        var chosen = runCatching { LocalDate.parse(draft.value("date")) }.getOrDefault(today).coerceAtMost(today)
        var month = chosen.withDayOfMonth(1)
        val title = text("", TypeScale.TITLE, true).apply { setPadding(0, 0, 0, 0) }
        val grid = col()
        fun arrow(back: Boolean, step: () -> Unit) = android.widget.FrameLayout(this).apply {
            contentDescription = if (back) "이전 달" else "다음 달"; isClickable = true; setOnClickListener { if (isEnabled) step() }
            addView(chevron(p.title, 32).apply { rotation = if (back) 180f else 0f }, android.widget.FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
        }
        lateinit var later: View
        fun paint() {
            title.text = "${month.year}년 ${month.monthValue}월"
            val last = month >= today.withDayOfMonth(1)
            later.isEnabled = !last; later.alpha = if (last) 0.25f else 1f
            grid.removeAllViews()
            grid.addView(row().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                    addView(text(d, TypeScale.LABEL_SMALL, false, p.sub).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, 0) },
                        LinearLayout.LayoutParams(dp(72), dp(48)).apply { if (i > 0) marginStart = dp(16) })
                }
            })
            // Sunday first, as the Korean calendar is printed.
            val lead = month.dayOfWeek.value % 7
            val days = month.lengthOfMonth()
            for (w in 0 until (lead + days + 6) / 7) grid.addView(row().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                for (c in 0..6) {
                    val d = w * 7 + c - lead + 1
                    val lp = LinearLayout.LayoutParams(dp(72), dp(72)).apply { if (c > 0) marginStart = dp(16); topMargin = dp(4) }
                    if (d < 1 || d > days) { addView(View(this@MainActivity), lp); continue }
                    val date = month.withDayOfMonth(d)
                    val picked = date == chosen; val future = date > today
                    addView(text("$d", TypeScale.LABEL, picked || date == today, if (picked) p.onPrimary else p.title).apply {
                        gravity = Gravity.CENTER; setPadding(0, 0, 0, 0); fontFeatureSettings = "tnum"
                        contentDescription = "${month.monthValue}월 ${d}일${if (picked) ", 선택됨" else ""}"
                        if (picked) background = round(p.primary, 36)
                        if (future) alpha = 0.25f else setOnClickListener { chosen = date; paint() }
                    }, lp)
                }
            })
        }
        val body = col().apply {
            addView(row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(title, LinearLayout.LayoutParams(0, -2, 1f))
                addView(arrow(true) { month = month.minusMonths(1); paint() }, LinearLayout.LayoutParams(dp(76), dp(76)))
                later = arrow(false) { month = month.plusMonths(1); paint() }
                addView(later, LinearLayout.LayoutParams(dp(76), dp(76)).apply { marginStart = dp(8) })
            })
            addView(grid, margin(16))
        }
        paint()
        sheet(body, listOf("확인" to { draft.put("dateUnknown", false); updateDraft("date", chosen.toString()); render() }), cancel = "취소")
    }
    private fun touched() { draft.put("touched", true); persistDraft() }
    private fun updateDraft(key: String, value: String) { draft.put(key, value); touched() }
    private fun recordToday(plan: MaintenancePlan, action: ServiceAction = plan.action) {
        // A tire or brake check may come with a gauge reading; it is optional and starts the estimate over from what was read.
        if (action == ServiceAction.INSPECT && (part == Part.TIRE || part == Part.BRAKE_PAD)) { recordCheck(plan); return }
        val title = if (part == Part.TIRE && action == ServiceAction.REPLACE) "오늘 타이어를 교체하셨나요?" else "오늘 ${action.label}하셨나요?"
        confirm(title, if (vehicleMileage() != null) "오늘 날짜와 차량에서 읽은 주행거리로 새 주기를 시작해요." else "오늘 날짜로 기록해요. 차량 연결 전에는 거리 기준을 계산할 수 없어요.", "기록하기", "취소") {
            if (!saveToday(part, plan, action)) return@confirm
            store.clearDraft(part); draft = JSONObject(); render(); toast("${action.label} 기록을 저장했어요")
            // The manual has the brakes looked at whenever tires are changed or rotated; offer to log that in the same visit.
            if (part != Part.TIRE || action == ServiceAction.INSPECT) { if (store.records.size == 1) offerNotifications() }
            else store.plans[Part.BRAKE_PAD]?.let { brake ->
                confirm("브레이크도 함께 점검하셨나요?", "취급설명서는 타이어를 교체하거나 위치를 교환할 때 브레이크 디스크와 패드를 함께 점검하라고 해요. 점검했다면 브레이크 점검도 오늘 날짜로 기록할게요.", "함께 기록", "타이어만") {
                    if (saveToday(Part.BRAKE_PAD, brake, ServiceAction.INSPECT)) { render(); toast("브레이크 점검도 기록했어요") }
                }
            }
        }
    }

    /** A tire or brake check today, with the depth or thickness the shop read if the owner has it. */
    private fun recordCheck(plan: MaintenancePlan) {
        if (restricted) return
        val tire = part == Part.TIRE
        var mm = ""
        val body = col().apply {
            addView(text("오늘 ${ServiceAction.INSPECT.label}하셨나요?", TypeScale.TITLE, true).apply { setPadding(0, 0, 0, 0) })
            addView(text(if (tire) "정비소에서 잰 홈 깊이가 있으면 넣어 주세요. 가장 많이 닳은 바퀴 값이면 돼요. 없으면 비워 두셔도 돼요."
                else "정비소에서 잰 앞 패드 두께가 있으면 넣어 주세요. 없으면 비워 두셔도 돼요.", TypeScale.BODY, tint = p.body).apply { setLineSpacing(dp(4).toFloat(), 1f) }, margin(12))
            addView(field(if (tire) "홈 깊이 mm (예: 4.5)" else "패드 두께 mm (예: 7)", "", if (tire) "잰 홈 깊이" else "잰 패드 두께") { mm = it }.apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; filters = arrayOf(InputFilter.LengthFilter(4))
                background = round(p.fill, Radius.SURFACE); setPadding(dp(28), 0, dp(28), 0) }, margin(20))
        }
        sheet(body, listOf("기록" to {
            val reading = mm.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..(if (tire) 12.0 else 15.0) }
            if (!restricted && saveToday(part, plan, ServiceAction.INSPECT, reading)) {
                store.clearDraft(part); draft = JSONObject(); render()
                toast(if (reading != null) "${ServiceAction.INSPECT.label} 기록과 잰 값 ${reading} mm를 저장했어요" else "${ServiceAction.INSPECT.label} 기록을 저장했어요")
                if (store.records.size == 1) offerNotifications()
            }
        }), cancel = "취소")
    }

    /** Stores a service done today on [target] and restarts what that service resets. */
    private fun saveToday(target: Part, plan: MaintenancePlan, action: ServiceAction, measuredMm: Double? = null): Boolean {
        val record = ServiceRecord(UUID.randomUUID().toString(), target, LocalDate.now(),
            store.mileage?.takeIf { mileageLive && it.source == "vehicle" && it.updatedOn == LocalDate.now() }?.km, action, measuredMm)
        if (!change { store.records += record; store.plans[target] = plan.copy(recordId = record.id); store.bookings.remove(target) }) return false
        // An inspection restarts HVAC and brake use (pads are judged at inspection); for others only a replacement does.
        // A tire inspection does not restart the rotation interval; a rotation or a new set does.
        val restarts = if (target == Part.TIRE) action != ServiceAction.INSPECT else action == ServiceAction.REPLACE || target == Part.HVAC || target == Part.BRAKE_PAD
        if (restarts) VehicleSignals.markBaseline(target, record.id)
        return true
    }
    private fun showPartMenu(anchor: View) {
        val plan = store.plans[part] ?: RecommendedCare.plan(part)
        PopupMenu(this, anchor).apply {
            // Inspected parts get their inspection as the main button; a replacement is the rarer record and lives here.
            if (part == Part.TIRE) {
                menu.add("이전 위치 교환 입력").setOnMenuItemClickListener {
                    startWizard(true); draft.put("action", ServiceAction.ROTATE.name); render(); true
                }
            }
            menu.add("이전 이력 입력·수정").setOnMenuItemClickListener { startWizard(false); true }
            menu.add("추천 기준").setOnMenuItemClickListener {
                explain("${PartArt.title(part)} 추천 기준", "${policyText(recommended(part))}\n${recommended(part).source}\n\n차량 사양·가혹 조건에 따라 정비 기준이 달라질 수 있어요. 상태 점검 결과를 우선해 주세요.", "출처 보기" to RecommendedCare.sourceUrl(part))
                true
            }
            if (part == Part.WIPER) menu.add("계산 근거").setOnMenuItemClickListener {
                val (lo, hi) = WiperModel.cpm(WiperModel.Level.LOW); val (ho, hh) = WiperModel.cpm(WiperModel.Level.HIGH)
                val (io, ih) = WiperModel.cpm(WiperModel.Level.INTERMITTENT); val (ao, ah) = WiperModel.cpm(WiperModel.Level.AUTO)
                explain("와이퍼 계산 근거", "교체 시점은 블레이드 나이로 정해요. 부품사(Bosch, DENSO, Valeo)가 권하는 1년을 기준으로, 기상청 평년의 하루를 하루로 셉니다. 현대 국내 설명서에는 교체 주기가 없고, 미국판 쏘나타 설명서는 6개월마다 점검하라고 해요.\n\n" +
                        "더위: 주행 중 잰 외기온도가 그 시각의 평년값보다 높은 날은 아레니우스 가중치만큼 더 셉니다. 공기 중 고무 노화의 활성화 에너지는 클로로프렌 74~90, 천연고무 약 90 kJ/mol이고, EPDM은 100°C 아래에서 51까지 내려가요. 판정에는 변화가 가장 작은 50을 써요.\n" +
                        "햇볕 주차: 30분 넘게 세워 둔 차가 실내가 외기보다 ${RubberAging.SOAK_GAP.toInt()}°C 이상 더운 채 출발하면 햇볕에 세워 둔 날로 보고 ${RubberAging.SUN_DAY}일로 셉니다. 그늘에 세우는 차의 블레이드가 2~3배 오래 간다는 부품사 설명과, 오존·햇빛 균열이 현장 고장의 주된 원인이라는 특허들을 따른 가정이에요.\n\n" +
                        "닦은 횟수: 스위치 단별 작동 시간 × 분당 왕복 수(저속 ${lo.toInt()}~${hi.toInt()}, 고속 ${ho.toInt()}~${hh.toInt()}, 간헐 ${io.toInt()}~${ih.toInt()}, 자동 ${ao.toInt()}~${ah.toInt()}회). 영하에서는 얼음을 닦을 수 있어 범위 위쪽만 2배로 셉니다. " +
                        "한국소비자원 시험의 왕복 20만 회 뒤에도 10종 중 8종이 양호했고, 공항 강수 기록과 하루 주행으로 계산한 보통 운전자는 1년에 약 5.2만 회를 닦아요. 그래서 이 속도를 넘은 만큼만, 20만 회를 1년으로 쳐서 날짜에 더해요.\n\n" +
                        "작동 소리: 와이퍼 작동 중 1~10 kHz에서 닦는 소리가 바닥 소음보다 얼마나 큰지 재요. 블레이드의 끽 소리는 1 kHz와 2~2.5 kHz 근처에서 나요(Reddyhoff 외, 2015). 새 블레이드는 먼지 때문에 처음 소리가 클 수 있어 첫 ${WiperSound.WARMUP}회는 빼고, " +
                        "같은 단수끼리 그다음 ${WiperSound.BASELINE}회와 최근 ${WiperSound.RECENT}회를 비교해요. 최근 것이 모두 더 크면 먼저 유리와 블레이드를 닦아 보시라고 안내하고, 닦은 뒤 ${WiperSound.CONFIRM}회도 모두 크면 교체를 권해요. 소리 자체는 저장하지 않아요.")
                true
            }
            if (part == Part.CABIN_FILTER) store.activeRecord(part)?.let { rec ->
                OwnerReport.entries.forEach { r ->
                    val label = if (r == OwnerReport.WEAK_AIRFLOW) "풍량이 약해졌어요" else "냄새가 나요"
                    if (store.reports[rec.id] != r) menu.add(label).setOnMenuItemClickListener {
                        val body = if (r == OwnerReport.WEAK_AIRFLOW) "취급설명서는 필터가 막히면 풍량이 약해지고 냉난방 성능이 떨어진다고 안내해요. 필터 교체를 \"지금 할 때\"로 올릴게요."
                            else "설명서는 필터를 제때 바꾸지 않으면 악취가 날 수 있다고 해요. 송풍구의 에어컨 냄새는 고장이 아니며, 에어컨을 끈 뒤 송풍만 5분 켜서 말리면 줄어든다고 안내해요. 필터 교체를 \"곧 할 때\"로 올릴게요."
                        confirm(label, body, "알리기", "취소") { if (change { store.reports[rec.id] = r }) render() }
                        true
                    }
                }
                if (store.reports[rec.id] != null) menu.add("알린 증상 지우기").setOnMenuItemClickListener {
                    if (change { store.reports.remove(rec.id) }) render(); true
                }
            }
            if (part == Part.CABIN_FILTER || part == Part.HVAC) menu.add("계산 근거").setOnMenuItemClickListener {
                val body = if (part == Part.CABIN_FILTER)
                    "먼지 적재 = 송풍 시간마다 (필터를 지난 바람 ÷ 167 m³/h) × (그달 한국 PM2.5 ÷ 42 µg/m³)를 더한 값을 500시간과 비교\n" +
                    "500시간은 상하이 외기(PM2.5 42 µg/m³)에서 저속 송풍(약 167 m³/h)으로 필터를 노화시킨 시험이에요(Wei 외 2020). 이때 쌓이는 먼지 4~6 g은 실제 도로 먼지로 필터 차압이 100 Pa 오른 양(3~4 g, Johansson 1999)과 같아요. " +
                    "한국 공기(2024 전국 평균 PM2.5 16, 겨울·봄 높고 여름 낮음)로는 저속 송풍 약 1,300시간에 같은 양이 쌓여요.\n" +
                    "바람: 송풍 단계에 따라 시간당 80~450 m³. 내기 순환은 바깥 먼지의 약 10%만 싣고, 송풍을 꺼도 시속 10 km 넘게 외기로 달리면 주행풍 70 m³가 지나가요.\n\n" +
                    "교체는 취급설명서의 12개월이 먼저예요. 정전 필터는 먼지와 상관없이 약 1년에 걸쳐 걸러냄이 떨어지고(1 µm 94%에서 58%), 냄새·곰팡이도 사용 기간과 여름을 따라 늘어서, 먼지가 적다고 늦추지 않아요. 먼지 적재는 하루 몇 시간씩 외기로 달리는 차를 12개월보다 먼저 알리는 몫이에요.\n\n" +
                    "위치를 허용하면 가장 가까운 시도의 2024 연평균(환경부, 제주·전남 12.3 ~ 인천 18.9 µg/m³)을 써요. 실시간 값은 아직 쓰지 않아요. 흡입 상태를 모르면 외기로 셈해 실제보다 빨리 찰 수 있어요."
                else
                    "냉매 감소 = 1 − e^(−연 누설률 × 경과 연수), 최근 점검일부터. ${CarModel.current.title} 충전량 ${RefrigerantModel.chargeG(CarModel.current)} g(국내 취급설명서)\n" +
                    "폐차 때 잰 국내 차량은 8.7년에 65.4%, 12.4년에 55.6%가 남아 연 5.3%였고, EU 실측은 첫해 10.5%, 4~6년차 약 5%였어요. 그래서 범위를 연 ${String.format(java.util.Locale.KOREA, "%.1f~%.1f", RefrigerantModel.LOW * 100, RefrigerantModel.HIGH * 100)}%로 두고, 첫해는 위쪽을 쓰고 이후 중앙 ${String.format(java.util.Locale.KOREA, "%.1f", RefrigerantModel.CENTRE * 100)}%의 두 배까지 봐요. " +
                    "사고나 돌로 배관이 뚫리는 경우(연 2.2%의 차)는 예측할 수 없어 이 값에 들어 있지 않아요.\n\n" +
                    "${ClimateTracker.PROBE_MINUTES.toInt()}분 냉방 회복 = 에어컨이 켜질 때 실내가 설정보다 ${ClimateTracker.START_GAP.toInt()}°C 이상 높으면, 처음 ${ClimateTracker.PROBE_MINUTES.toInt()}분 동안 설정 온도까지의 격차를 몇 % 좁혔는지. " +
                    "점검 직후 ${ClimateTracker.BASELINE}회와 외기 ±${ClimateTracker.SIMILAR_OUTSIDE.toInt()}°C 안의 최근 값을, 내·외기가 같고 송풍 단계 차이가 ${Math.round(ClimateTracker.FAN_MATCH * 100)}% 이내인 회차끼리 비교해요. " +
                    "세워 둔 채(시속 ${ClimateTracker.PARKED_KPH.toInt()} km 미만) 켠 냉방은 빼요. 응축기에 바람이 들지 않아 같은 차도 50 km/h보다 약 1.5배 오래 걸리기 때문이에요(자동차부품연구원 2011).\n" +
                    "· 최근 ${ClimateTracker.RECENT}회가 모두 점검 직후의 가장 느린 회차보다 느리면 '곧 할 때'\n" +
                    "· 최근 2회가 모두 점검 직후 중앙값의 ${Math.round(ClimateTracker.LOST_SHARE * 100)}% 미만이면 '지금 할 때'(냉매가 거의 빠진 상태)\n\n" +
                    "차량 에어컨은 리시버가 여분 냉매를 품고 있어 한동안 냉방이 그대로예요. 냉매 14% 부족에 능력 12%, 29% 부족에 21%가 줄었고(두원공조 1998), 절반 가까이 빠질 때까지 큰 저하가 없다는 보고도 있어요. 100~200 g만 남으면 압축기가 멈춰요. " +
                    "독일 정비소가 연 시스템의 38%는 냉매가 다 빠져 있었어요. 그래서 '곧 할 때'는 원인을 하나로 정하지 않고, '지금 할 때'는 냉매가 거의 없는 상태로 봐요.\n\n" +
                    "실내 열 모의 실험(ORNL 실차 냉각 시간에 맞춤, 송풍 습관·차속 포함): 변화 없는 시스템을 두 여름 동안 잘못 알린 비율 0.3~0.6%, 냉방 능력 절반에서 97% 감지, 냉매를 다 잃으면 거의 모두 1~2회 안에 '지금 할 때'로 판정. " +
                    "재충전 없이 5년을 돌린 차들 중 냉방 능력이 90% 넘게 남은 차를 잘못 알린 비율은 2.7%였어요.\n\n" +
                    "${ClimateTracker.HEAT_PROBE_MINUTES.toInt()}분 난방 회복 = ${VehicleSignals.COLD_START_HOURS}시간 이상 세워 둔 차가 출발 2분 안에 송풍을 켰고 실내가 설정보다 ${ClimateTracker.START_GAP.toInt()}°C 이상 낮으면, 처음 ${ClimateTracker.HEAT_PROBE_MINUTES.toInt()}분 동안 설정 온도까지의 격차를 몇 % 좁혔는지. 비교 방식은 냉방과 같고, 차속 차이가 ${ClimateTracker.SPEED_MATCH.toInt()} km/h 이내인 회차끼리, 평균 시속 ${ClimateTracker.SLOW_KPH.toInt()} km 미만인 회차는 빼고 비교해요. " +
                    "서모스탯은 차의 자기진단(OBD II)이 시동마다 보고 경고등으로 먼저 알려요. 이 판정은 냉각수 양, 히터 코어, 온도 조절 문처럼 히터 쪽을 봐요. 하이브리드는 엔진이 언제 돌지 몰라 흩어짐이 크고, 전기차는 처음부터 전기로 데우므로 둘 다 '거의 안 데움'(지금 할 때)만 알려요. 전기차는 세워 둔 시간 조건 없이 처음 ${ClimateTracker.EV_HEAT_PROBE_MINUTES.toInt()}분을 봐요.\n" +
                    "엔진·히터 열 모의 실험: 변화 없는 차를 두 겨울 동안 잘못 알린 비율 1%, 히터 능력 절반에서 66% 감지, 히터가 작동하지 않으면 96%를 '지금 할 때'로 판정. 3~16시간의 실제 주차 길이를 섞어도 결과가 같았고, 주차 시간대별로 나눠 비교하면 기준이 모자라 히터 고장 감지가 60%대로 떨어져 나누지 않았어요."
                explain("${PartArt.title(part)} 계산 근거", body)
                true
            }
            if (part == Part.ENGINE_OIL) menu.add("가혹 조건 선택").setOnMenuItemClickListener { chooseSevere(plan); true }
            if (part == Part.ENGINE_OIL) menu.add("계산 근거").setOnMenuItemClickListener {
                explain("엔진오일 계산 근거", "차가 엔진 회전수와 오일 온도를 일반 앱에 주지 않아, 차속과 외기 온도로 오일 온도를 추정해요.\n\n" +
                        "오일 온도: 달리는 동안 목표 온도(일반 100°C, 하이브리드 80°C)로 따라 오르고, 세워 두면 외기 쪽으로 식어요. " +
                        "따라 오르는 속도는 시내 주행 9분, 정체 20분 시정수예요. Ford 1.8 L 시내 주행 실측(외기 31°C에서 10분, 영하 2°C에서 14~16분에 80°C)과 1~2분 안에서 맞고, Honda 오일 수명 시스템이 같은 식을 써요. 식는 속도는 현대 특허의 예(0°C에서 90°C가 2시간 뒤 약 30°C)로 110분 시정수예요.\n\n" +
                        "오일 부담: 엔진 회전 수 × 오일 온도 가중치. 가중치는 GM 오일 수명 특허(US 4,742,476)의 회전수 기준 값으로, 0°C 미만 4, 38°C 미만 3, 82°C 미만 2, 그 이상 1이에요. 회전 수는 차속으로 근사해 저속 시내 주행이 km당 더 무겁게 잡혀요.\n\n" +
                        "척도: 현대 가혹 조건 주기가 통상의 절반이고 미국판 설명서가 단거리를 영상 8 km 미만으로 정의해, 20°C에서 5 km 운행을 반복하면 정확히 2배가 되게 맞췄어요. " +
                        "이 척도에서 영하 10°C 4 km 반복은 오일 수명 3,800 km로, GM이 실차로 잰 냉간 단거리 수명 2,400~3,680 km와 가까워요. 장거리 위주로 달려도 설명서 주기보다 늘리지는 않아요.\n\n" +
                        "연료 희석: 오일이 60°C가 되기 전에는 연료가 섞이고, 데워지면 가벼운 성분부터 빠져요. 장거리 정상 운행은 1~2%(Shayler), 여름 단거리 반복은 약 3%, 겨울은 약 9%(Bergstra 실측 3~5%, 8~11%)가 되도록 맞췄어요. 3%면 주의, 5%면 교체를 권해요. 5%가 섞이면 0W-20 오일의 40°C 점도가 약 5분의 1 떨어져요.\n\n" +
                        "모래·먼지, 험로, 견인처럼 앱이 알 수 없는 조건은 메뉴의 '가혹 조건 선택'에서 고르면 설명서 가혹 조건 주기로 바뀌어요. 이때도 운행 부담은 통상 주기 기준으로 계산해 두 번 세지 않아요.")
                true
            }
            if (part == Part.TIRE) menu.add("계산 근거").setOnMenuItemClickListener {
                val tp = TireParams.of(CarModel.current)
                explain("타이어 계산 근거", "위치 교환: 취급설명서 정기 점검표의 1만 km마다. 편마모를 막기 위한 항목이에요. 최근 위치 교환이나 타이어 교체 기록의 주행거리부터 셉니다.\n\n" +
                        "마모 가중 = 0.1 + 0.9 × (앞뒤 가속² 평균 + 좌우 가속² 평균) ÷ 1.067\n" +
                        "타이어 마모는 미끄러지며 쓰는 마찰 에너지에 비례하고, 그 에너지는 힘(가속도)의 제곱으로 커져요. 1.067은 UN 규정 117 마모 시험의 보통 주행(앞뒤 0.45, 좌우 0.93 m/s², 2025년 통합본)이에요.\n" +
                        "앞뒤: 차속을 1초 간격으로 미분. 좌우: 조향각으로 계산(차속² × 조향각 ÷ 조향비 ${tp.steeringRatio} ÷ (축거 ${tp.wheelbaseM} m + 언더스티어 × 차속²)). 조향각이 없으면 뒷바퀴 좌우 회전 차이로 계산하지만, 바퀴 반경이 1 mm만 달라도 시속 100 km에서 1.3 m/s²가 잘못 잡혀 조향각을 우선해요. " +
                        "언더스티어는 3°/g로 시작해, 조향각과 바퀴 회전이 함께 들어오는 시속 30 km 이상 선회에서 이 차의 값을 배워요. 지금 ${String.format(java.util.Locale.KOREA, "%.1f", VehicleSignals.tireLoad().understeer() * 9.81 * 180 / Math.PI)}°/g. 왼쪽·오른쪽 선회를 따로 모아 평균해서 바퀴 반경 차이를 상쇄해요.\n\n" +
                        "홈 깊이: 새 타이어 ${String.format(java.util.Locale.KOREA, "%.1f", tp.newMm)} mm(순정 타이어 사양)에서, 보통 주행 기준 1만 km당 앞 ${tp.frontPer10k} mm, 뒤 ${tp.rearPer10k} mm를 빼요. 15개 차종을 규정 그대로 몬 2025년 시험의 앞 1.47·뒤 0.64 mm를 차량 하중으로 옮긴 값이에요. 여기에 마모 가중과 기온(1°C에 2.7%, 18.5°C 기준)을 곱하고, 위치 교환 때마다 앞뒤를 맞바꿔요. 1만 5천 km 넘게 위치 교환 기록이 없으면 중앙값은 앞뒤 평균으로, 상단은 교환하지 않은 앞바퀴 기준으로 셉니다.\n" +
                        "같은 운전에도 타이어마다 0.6~1.36배 차이가 나서, 추정만으로 교체를 권하지 않아요. 상단(1.36배)이 3 mm에 닿으면 곧, 중앙값이 2.4 mm(현대 설명서의 고속도로 기준)에 닿으면 지금 깊이를 확인해 달라고 해요. 교체 없이 점검을 기록하면 그때 홈이 3 mm 이상이었다고 보고, 닳는 속도의 상한을 그에 맞춰 낮춰요. 그다음에는 2.4 mm에서 다시 물어요. 점검 때 잰 홈 깊이를 넣으면 그 값에서 다시 세요.\n\n" +
                        "공기압: 일반 앱은 바퀴별 공기압을 읽을 수 없어요(시스템 전용). 공기압은 한 달에 약 7 kPa씩, 기온 1°C에 1.16 kPa씩 빠지고, 20% 부족하면 수명이 약 20% 줄어서 매월 점검을 권해요.\n\n" +
                        "Giechaskiel 외, Lubricants 12 (2024) 338 · Vehicles 7 (2025) 91 · UN 규정 117 부속서 10", "논문 보기" to "https://doi.org/10.3390/lubricants12100338")
                true
            }
            if (part == Part.BRAKE_PAD) menu.add("계산 근거").setOnMenuItemClickListener {
                val bp = BrakeWearModel.params(); val ref = CompositeJudge.brakeReferenceKjPerKm()
                explain("브레이크 패드 계산 근거", "마모 = 계수 × 마찰 제동 일 × 온도 배율\n" +
                        "제동 일: 페달을 밟은 동안 줄어든 운동에너지 − 주행저항. Green NCAP 절차와 GM 특허(US 9,416,835)의 방식이에요.\n" +
                        "차량: ${CarModel.current.title} · 공차 ${fmt(bp.curbKg.toLong())} kg + 운전자 75 kg, 회전 관성 3% · 주행저항은 미국 EPA 인증 값${if (bp.roadLoadGuessed) "이 없어 차체 크기로 추정" else ""}\n" +
                        "마찰 제동 비율: ${Math.round(bp.frictionShare * 100)}%${if (bp.frictionShare < 1) " (상한 ${Math.round(bp.frictionShareHigh * 100)}%). 나머지는 회생제동이 맡아요. 하이브리드 0.40·전기차 0.13은 UN 브레이크 배출 규정(GTR 24) 값과 실차 측정으로 정했어요" else ". 엔진브레이크는 빼지 못해 실제보다 크게 나올 수 있어요"}\n" +
                        "계수: ${BrakeWearModel.K_LOW * 1e9}~${BrakeWearModel.K_HIGH * 1e9} × 10⁻⁹ mm/J (실차 2대 실측, Jensen 외)\n" +
                        "보통 제동량: 이 차 ${Math.round(ref)} kJ/km. 국제 브레이크 시험 주행(WLTP-Brake)의 79 J/(kg·km) × 차량 질량이에요. 이 정도면 1만 km에 패드가 1~2 mm 닳아요.\n\n" +
                        "디스크 온도: 앞 디스크 9 kg, 제동 열의 93%가 디스크로, 앞축이 70%를 맡아요. 식는 속도는 GM 특허 식을 쓰고, 상한은 더 느리게 식는 대류 계산으로 잡았어요. 200°C까지는 마모가 그대로이고 400°C면 4배, 500°C면 5배로 계산해요. 보통 주행은 디스크 최고 130~180°C라(실측) 거의 영향이 없고 긴 내리막에서 커져요.\n\n" +
                        "패드 잔량: 패드를 교체했다고 기록한 뒤부터 새 패드 ${fmt(bp.padNewMm.toLong())} mm에서 빼요. ${fmt(BrakeWearModel.REPLACE_MM.toLong())} mm 남으면 교체, ${fmt(BrakeWearModel.SOON_MM.toLong())} mm면 곧으로 알려요(현대 특허의 경고 시점 2 mm보다 여유). 점검 때 잰 패드 두께를 넣으면 그 값에서 다시 빼요. 차량이 브레이크액 부족을 알리면 바로 '지금 할 때'예요.\n\n" +
                        "속도 유지 제동: 페달을 ${BrakeHolds.HOLD_SECONDS.toInt()}초 넘게 밟았는데 속도가 ${BrakeHolds.HOLD_DROP_MPS.toInt()} m/s도 줄지 않은 구간(시속 ${(BrakeHolds.HOLD_MIN_MPS * 3.6).toInt()} km 이상). 내리막에서 속도를 붙잡은 경우예요. 경사를 모르므로 5% 내리막으로 가정해 상한 추정에만 더해요.\n\n" +
                        "Jensen, Santos, Corstens. Wear 552-553 (2024) 205433\nJensen, Santos, Corstens. Wear 538-539 (2024) 205220\nMamakos 외(2021), Storch 외(2023) WLTP-Brake 측정", "논문 보기" to "https://doi.org/10.1016/j.wear.2024.205433")
                true
            }
            menu.add("정비 이력").setOnMenuItemClickListener { go("history"); true }
            menu.add(if (plan.paused) "알림 다시 시작" else "알림 일시정지").setOnMenuItemClickListener {
                change { store.plans[part] = plan.copy(paused = !plan.paused) }; render(); true
            }
            menu.add("알림 해제").setOnMenuItemClickListener {
                confirm("알림을 해제할까요?", "정비 이력은 남겨둘게요.", "알림 해제") {
                    change { store.plans.remove(part); store.clearDraft(part) }; draft = JSONObject(); render()
                }; true
            }
            show()
        }
    }
    private fun draftModels(): Pair<MaintenancePlan, ServiceRecord> {
        val id = draft.value("recordId").ifEmpty { UUID.randomUUID().toString() }
        val action = ServiceAction.valueOf(draft.value("action").ifEmpty { part.defaultAction.name })
        val record = ServiceRecord(id, part, draft.value("date").takeIf(String::isNotEmpty)?.let(LocalDate::parse), draft.value("km").toLongOrNull(), action)
        val autoKm = if (record.date == LocalDate.now() && store.records.none { it.id == record.id }) vehicleMileage()?.km else record.odometerKm
        return RecommendedCare.plan(part, id, store.plans[part]?.paused ?: false, store.severe.isNotEmpty()) to record.copy(odometerKm = autoKm)

    }
    private fun finishWizard() {
        if (restricted) return
        val (plan, record) = draftModels(); val old = store.plans[part]
        if (plan.intervalMonths == null && plan.intervalKm == null) { toast("알림 간격을 선택해 주세요"); return }
        if (change {
            val index = store.records.indexOfFirst { it.id == record.id }
            if (index >= 0) store.records[index] = record else store.records.add(record)
            if (old == null || draft.optBoolean("new") || old.recordId == record.id) store.plans[part] = plan
        }) {
            store.clearDraft(part); draft = JSONObject(); route = "detail"; hideKeyboard(); render()
            if (store.records.size == 1) offerNotifications()
        }
    }
    /** After the first record, when a reminder first has something to say: one line of why, then the system prompt. */
    private fun offerNotifications() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        confirm("정비할 때가 되면 알려 드릴까요?", "기록한 날짜와 차가 센 운행으로 시점이 오면 알림을 보내요. 화면을 열지 않아도 돼요.", "알림 받기", "나중에") {
            ask(Manifest.permission.POST_NOTIFICATIONS, 100)
        }
    }
    private fun persistDraft() {
        if (::store.isInitialized && (route == "wizard" || route == "detail" && store.plans[part] == null) && draft.value("part") == part.name && draft.optBoolean("touched")) store.saveDraft(part, draft)
    }
    private fun quietButton(label: String, action: () -> Unit) = button(label, action = action).apply {
        background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT); minHeight = dp(48); minimumHeight = dp(48)
        textSize = 16f; setPadding(dp(8), 0, dp(8), 0)
    }
    private fun smallField(hint: String, value: String, description: String, onChange: (String) -> Unit) = field(hint, value, description, onChange).apply {
        textSize = 21f; minHeight = dp(54); minimumHeight = dp(54); setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    private fun toolIcon(resource: Int, label: String, action: (View) -> Unit) = ImageButton(this).apply {
        setImageResource(resource); imageTintList = android.content.res.ColorStateList.valueOf(muted)
        background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT); contentDescription = label
        setPadding(dp(14), dp(14), dp(14), dp(14)); setOnClickListener { if (!restricted) action(it) }
    }

    private fun history(): View {
        val page = page(); page.addView(header("${part.title} 정비 이력", "detail"))
        val records = store.records.filter { it.part == part }.reversed()
        if (records.isEmpty()) page.addView(text("아직 기록한 정비가 없어요", 26, true))
        records.forEach { record ->
            val entry = col().apply {
                pad(22); background = round(surface, 18)
                addView(text("${record.date ?: "날짜 미확인"} · ${record.action.label}", 22, true))
                addView(text(record.odometerKm?.let { fmt(it) + " km" } ?: "주행거리 미확인", 18, tint = muted))
                if (record.id == store.plans[part]?.recordId) addView(text("현재 주기의 기준 기록", 16, true, accent))
                addView(button("수정") { startWizard(false, record.id) })
                addView(button("삭제") {
                    confirm("이 기록을 삭제할까요?", "현재 주기 기준인 기록을 삭제하면 새 기준을 입력할 때까지 해당 주기를 계산할 수 없어요.", "기록 삭제") {
                        change { store.records.removeAll { it.id == record.id } }; render()
                    }
                })
            }
            page.addView(entry, margin(14))
        }
        return scroll(page, 940)
    }
    private fun settings(): View {
        val page = page(); page.addView(header("설정", "home")); page.addView(text("내 차에 맞게 관리해요", 32, true))
        val spec = store.spec
        val told = store.identity?.let { "${it.label.ifEmpty { "이름 없음" }} · ${it.powertrain.label}" }
        val reported = when {
            // The demo fixes a Tucson; the car's own report is shown apart so the two never read as one car.
            store.demo -> "예시 데이터 사용 중${told?.let { "(차가 알려 준 정보: $it)" } ?: ""}"
            told != null && store.identity?.powertrain != Powertrain.UNKNOWN && store.identity?.powertrain != spec.powertrain -> "차가 알려 준 동력원과 달라요: $told"
            told != null -> "차가 알려 준 정보: $told"
            else -> "차에서 차종 정보를 받지 못했어요"
        }
        page.addView(box("내 차", "${spec.title}${if (spec.year > 0) " (${spec.year})" else ""}\n$reported\n${store.mileage?.takeIf { it.source == "vehicle" }?.let { fmt(it.km) + " km · 차량 수신" } ?: mileageState}"), margin(20))
        val nm = getSystemService(NotificationManager::class.java)
        val enabled = nm.areNotificationsEnabled() && nm.getNotificationChannel(Reminders.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        page.addView(box("주기 알림", "${if (enabled) "알림 사용 가능" else "시스템 알림이 꺼져 있어요"}\n정비 시점 30일 또는 1,000 km 전과 시점이 됐을 때 알려 드려요. 기기의 실행·절전 상태에 따라 알림이 늦어질 수 있어요."), margin(20))
        page.addView(button("시스템 알림 설정") {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ask(Manifest.permission.POST_NOTIFICATIONS, 100)
            else startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
        })
        val collecting = SignalService.enabled(this)
        page.addView(box("백그라운드 수집", if (collecting) "켜짐\n앱을 닫아도 운행·제동·공조·와이퍼 신호를 기록해 추정에 써요. 차량을 제어하지 않고, 와이퍼 소리는 앱을 한 번 연 뒤부터 분석해요."
            else "꺼짐\n앱 화면이 떠 있는 동안만 신호를 기록해요. 차량 데이터 추정이 실제보다 적게 잡혀요."), margin(20))
        page.addView(button(if (collecting) "백그라운드 수집 끄기" else "백그라운드 수집 켜기") { SignalService.setEnabled(this, !collecting); render() })
        val voice = VoiceBrief.enabled(this)
        page.addView(box("출발 음성 안내", if (voice) "켜짐\n지금 할 정비가 있으면 출발할 때 한 문장으로 알려 드려요. 같은 내용은 하루에 한 번만 말해요." else "꺼짐"))
        page.addView(button(if (voice) "음성 안내 끄기" else "음성 안내 켜기") { VoiceBrief.setEnabled(this, !voice); render() })
        page.addView(box("차량에 맞는 주기", if (spec.scheduleUrl != null) "${spec.title} 취급설명서의 정기 점검표를 따르고, 와이퍼·실내 필터 교체와 타이어 월 점검은 부품사 안내를 적용해요. 부품 상세의 더보기에서 출처를 확인할 수 있어요."
            else "차가 알려 준 차종의 정비 자료가 아직 없어 일반 권장 주기로 계산해요.${if (spec.powertrain == Powertrain.ELECTRIC) " 전기차라 엔진오일은 관리하지 않아요." else ""}"), margin(20))
        page.addView(source("현대차 설명서 열기", spec.scheduleUrl ?: "https://ownersmanual.hyundai.com"), margin(12))
        page.addView(box("예시 데이터", if (store.demo) "투싼 가솔린의 열 달 기록을 예시로 보여 주고 있어요. 실제 차량 기록이 아니에요."
            else "기록이 쌓이기 전에 화면을 미리 볼 수 있게 투싼 가솔린의 열 달 기록을 예시로 넣어요. 지금 기록은 지워져요."), margin(20))
        page.addView(button(if (store.demo) "예시 데이터 지우기" else "예시 데이터로 둘러보기") {
            if (store.demo) confirm("예시 데이터를 지울까요?", "예시 기록과 차량 신호를 모두 지우고 처음 상태로 돌아가요.", "예시 지우기") { wipe() }
            else confirm("예시 데이터로 바꿀까요?", "지금 정비 기록과 차량 신호를 지우고 예시 기록을 넣어요. 되돌릴 수 없어요.", "예시로 바꾸기") {
                DemoData.apply(this, DemoData.build(LocalDate.now(), store.mileage?.takeIf { it.source == "vehicle" }?.km ?: 0))
                Reminders.clear(this); store.reload(); VehicleSignals.reloadStored(); draft = JSONObject(); route = "home"; render()
            }
        })
        page.addView(text("차량 이름·거리·정비 기록·작성 중 답변은 이 차량에만 저장해요. 차량 양도·사용자 변경 시 초기화해 주세요.", 17, tint = muted), margin(20))
        if (store.loadFailed) page.addView(text("저장 형식을 읽지 못했어요. 기존 파일을 유지했어요. 초기화하면 복구할 수 없어요.", 18, tint = warning))
        page.addView(button("차량 관리 데이터 전체 삭제") {
            confirm("모든 관리 데이터를 삭제할까요?", "정비 이력, 주기, 주행거리, 작성 중인 답변을 모두 지워요. 되돌릴 수 없어요.", "전체 삭제") { wipe() }
        }, margin(18))
        return scroll(page, 940)
    }
    private fun wipe() {
        store.reset(); VehicleIdentity.read(this)?.let { store.identify(it) }; store.applyRecommendations()
        VehicleSignals.reset(); Reminders.clear(this); draft = JSONObject()
        // A clean slate starts with the first-run setup again.
        onboarding.edit().putBoolean("done", false).apply(); obSteps = emptyList(); obIndex = 0; obReturn = null; obDate.clear(); obKm.clear(); obRecord.clear()
        route = "onboarding"; render()
    }
    /** Shows a popup over the screen dimmed to the same depth as every other popup. */
    private fun present(dialog: Dialog) {
        dialogs += dialog; dialog.show()
        dialog.window?.apply { addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); setDimAmount(dim) }
    }

    /**
     * The system permission prompt is a full-width band that leaves the screen behind it undimmed, so the app darkens
     * its own window for as long as the prompt is up, and the prompt reads as a popup like the app's own.
     */
    private fun ask(permission: String, code: Int) { shade(true); requestPermissions(arrayOf(permission), code) }

    private val onboarding get() = getSharedPreferences("onboarding", MODE_PRIVATE)
    /** The runtime grants the readings need; the route search to navigation is granted at install. */
    private fun allPermissions() = listOfNotNull(VehicleSignals.SPEED_PERMISSION, Manifest.permission.RECORD_AUDIO, VehiclePlaces.LOCATION_PERMISSION,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null)
    private fun missingPermissions() = allPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    // First-run setup: the steps, where the owner is, and what each part step answered (and the record it made).
    private var obSteps: List<String> = emptyList()
    private var obIndex = 0
    /** The furthest step reached, while the owner has stepped back to change an answer; answering returns there. */
    private var obReturn: Int? = null
    private val obDate = mutableMapOf<Part, QuickSetup.Choice>()
    private val obKm = mutableMapOf<Part, QuickSetup.KmChoice>()
    private val obRecord = mutableMapOf<Part, String>()
    private fun onboardingSteps() = listOfNotNull(if (missingPermissions().isNotEmpty()) "permissions" else null) +
        store.spec.parts.filter { store.activeRecord(it) == null }.map { it.name } + "finish"

    /**
     * First run as one question at a time in a centred column: the car readings to allow, then each part's last service
     * picked from rough choices, then done. The whole order stays listed under the question, so the owner sees how far
     * along it is and can step back to any answer.
     */
    private fun onboarding(): View {
        if (obSteps.isEmpty()) obSteps = onboardingSteps()
        obIndex = obIndex.coerceIn(0, obSteps.lastIndex)
        val step = obSteps[obIndex]
        val column = col()
        // The header keeps its height on every step, so the step bar under it never moves.
        column.addView(row().apply {
            minimumHeight = dp(76)
            addView(text("${obIndex + 1} / ${obSteps.size}", TypeScale.META, true, p.sub).apply { setPadding(0, 0, 0, 0); fontFeatureSettings = "tnum" }, LinearLayout.LayoutParams(0, -2, 1f))
            if (step != "finish") addView(textLink("나중에 할게요") { finishOnboarding() })
        })
        column.addView(stepper(), margin(16))
        column.addView(when (step) { "permissions" -> permissionStep(); "finish" -> finishStep(); else -> partStep(Part.valueOf(step)) }.also { card ->
            if (ValueAnimator.areAnimatorsEnabled()) { card.alpha = 0f; card.translationY = dp(12).toFloat(); card.animate().alpha(1f).translationY(0f).setDuration(220).start() }
        }, margin(48))
        val page = FrameLayout(this).apply { setPadding(0, dp(32), 0, dp(48)) }
        page.addView(column, FrameLayout.LayoutParams(dp(minOf(980, resources.configuration.screenWidthDp - 160)), -2, Gravity.CENTER_HORIZONTAL))
        return scroll(page)
    }
    private fun stepTitle(step: String) = when (step) { "permissions" -> "차량 정보 허용"; "finish" -> "완료"; else -> PartArt.title(Part.valueOf(step)) }
    private fun nextStep(delay: Long = 0) {
        val go = { if (route == "onboarding") { obIndex = (obReturn ?: (obIndex + 1)).coerceAtMost(obSteps.lastIndex); obReturn = null; render() } }
        if (delay > 0) window.decorView.postDelayed(go, delay) else go()
    }

    /** Opens an earlier answer without losing the place: the later questions are not walked again. */
    private fun reopen(step: Int) {
        val reached = obReturn ?: obIndex
        obReturn = reached.takeIf { it != step }; obIndex = step; render()
    }

    private fun bigButton(label: String, action: () -> Unit) = actionButton(label, true, action).apply { textSize = TypeScale.DETAIL.toFloat() }

    private fun permissionStep(): View = col().apply {
        addView(text("차에서 받을 정보를 허용해 주세요", TypeScale.HEADLINE, true).apply { setPadding(0, 0, 0, 0) })
        addView(text("운행 기록이 쌓일수록 정비 시점이 정확해져요. 차량을 제어하지 않고, 기록은 계정 없이 이 차에만 저장해요.", TypeScale.BODY, tint = p.body).apply {
            setPadding(0, 0, 0, 0); setLineSpacing(dp(6).toFloat(), 1f) }, margin(16))
        val asks = listOfNotNull(
            Triple(VehicleSignals.SPEED_PERMISSION, "주행 속도", "제동, 급가속, 짧은 운행을 기록해 ${if (Part.ENGINE_OIL in store.spec.parts) "엔진오일·" else ""}브레이크·타이어 시점을 앞당겨요"),
            Triple(Manifest.permission.RECORD_AUDIO, "와이퍼 작동 소리", "와이퍼가 움직일 때 소리 크기만 재요. 소리는 저장하지 않아요"),
            Triple(VehiclePlaces.LOCATION_PERMISSION, "차량 위치", "정비소를 찾을 때 읽고, 그 지역 미세먼지로 필터에 쌓인 먼지를 추정해요"),
            if (Build.VERSION.SDK_INT >= 33) Triple(Manifest.permission.POST_NOTIFICATIONS, "정비 알림", "시점이 가까워지면 화면을 열지 않아도 알려 드려요") else null,
        )
        addView(col().apply {
            background = round(p.fill, Radius.SURFACE); setPadding(dp(32), dp(12), dp(32), dp(12))
            asks.forEachIndexed { i, (perm, title, why) ->
                if (i > 0) addView(View(this@MainActivity).apply { setBackgroundColor(p.divider) }, LinearLayout.LayoutParams(-1, dp(1)))
                addView(row().apply {
                    setPadding(0, dp(18), 0, dp(18))
                    addView(col().apply {
                        addView(text(title, TypeScale.DETAIL, true).apply { setPadding(0, 0, 0, 0) })
                        addView(text(why, TypeScale.LABEL, tint = p.body).apply { setPadding(0, dp(4), 0, 0) })
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) addView(text("허용됨", TypeScale.LABEL, false, p.sub).apply { setPadding(dp(24), 0, 0, 0) })
                })
            }
        }, margin(32))
        addView(bigButton("한 번에 허용하기") {
            val missing = missingPermissions()
            if (missing.isEmpty()) nextStep() else { shade(true); requestPermissions(missing.toTypedArray(), 105) }
        }, LinearLayout.LayoutParams(-1, dp(88)).apply { topMargin = dp(32) })
        addView(row().apply { gravity = Gravity.CENTER; addView(textLink("이번엔 건너뛰기") { nextStep() }) }, margin(8))
    }

    private fun partStep(part: Part): View = col().apply {
        addView(row().apply {
            addView(ImageView(this@MainActivity).apply { setImageResource(PartArt.resource(part)); scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(112), dp(112)).apply { marginEnd = dp(28) })
            addView(col().apply {
                addView(text(QuickSetup.question(part), TypeScale.HEADLINE, true).apply { setPadding(0, 0, 0, 0) })
                addView(text(QuickSetup.hint(part), TypeScale.BODY, tint = p.body).apply { setPadding(0, dp(10), 0, 0); setLineSpacing(dp(4).toFloat(), 1f) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        val both = QuickSetup.asksDate(part) && QuickSetup.asksKm(part)
        if (QuickSetup.asksDate(part)) {
            if (both) addView(text(QuickSetup.dateLabel(part), TypeScale.LABEL, true, p.sub).apply { setPadding(0, 0, 0, 0) }, margin(36))
            addView(chips(QuickSetup.dates(part).map { it.label }, QuickSetup.dates(part).indexOfFirst { it.label == obDate[part]?.label }) { i ->
                obDate[part] = QuickSetup.dates(part)[i]; answered(part) }, margin(if (both) 12 else 36))
        }
        if (QuickSetup.asksKm(part)) {
            if (both) addView(text(QuickSetup.kmLabel(part), TypeScale.LABEL, true, p.sub).apply { setPadding(0, 0, 0, 0) }, margin(32))
            val distances = QuickSetup.distances(part)
            addView(chips(distances.map { it.label }, distances.indexOfFirst { it.label == obKm[part]?.label }) { i ->
                obKm[part] = distances[i]; answered(part) }, margin(if (both) 12 else 36))
        }
        if (obReturn != null) addView(row().apply { gravity = Gravity.CENTER; addView(textLink("돌아가기") { nextStep() }) }, margin(16))
    }

    /** Choices in rows of three, large enough to hit at arm's length; the picked one is filled. */
    private fun chips(labels: List<String>, picked: Int, pick: (Int) -> Unit): View = col().apply {
        labels.chunked(3).forEachIndexed { r, line ->
            addView(row().apply {
                line.forEachIndexed { c, label ->
                    val i = r * 3 + c
                    addView(text(label, TypeScale.DETAIL, i == picked, if (i == picked) p.onPrimary else p.title).apply {
                        gravity = Gravity.CENTER; setPadding(dp(12), 0, dp(12), 0); isClickable = true; contentDescription = label
                        background = round(if (i == picked) p.primary else p.fill, Radius.SURFACE)
                        foreground = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(androidx_alpha(p.title, 20)), null, round(Color.WHITE, Radius.SURFACE))
                        setOnClickListener { if (!restricted) pick(i) }
                    }, LinearLayout.LayoutParams(0, dp(88), 1f).apply { if (c > 0) marginStart = dp(14) })
                }
                repeat(3 - line.size) { addView(View(this@MainActivity), LinearLayout.LayoutParams(0, dp(88), 1f).apply { marginStart = dp(14) }) }
            }, LinearLayout.LayoutParams(-1, -2).apply { if (r > 0) topMargin = dp(14) })
        }
    }

    /** Saves the part's answer as its record once every question on the step has one, then moves on. */
    private fun answered(part: Part) {
        val complete = (!QuickSetup.asksDate(part) || obDate[part] != null) && (!QuickSetup.asksKm(part) || obKm[part] != null)
        if (!complete) { render(); return }
        val record = QuickSetup.record("setup-${part.name}-${UUID.randomUUID()}", part, LocalDate.now(),
            store.mileage?.takeIf { it.source == "vehicle" }?.km, obDate[part], obKm[part])
        val old = obRecord.remove(part)
        change {
            if (old != null) store.records.removeAll { it.id == old }
            if (record != null) { store.records += record; store.plans[part] = RecommendedCare.plan(part, record.id, false, store.severe.isNotEmpty()) }
            else store.plans[part] = RecommendedCare.plan(part, severe = store.severe.isNotEmpty())
        }
        record?.let { obRecord[part] = it.id }
        render()
        // Back on a two-question step, the other answer may be next; it stays open until the owner goes back.
        if (obReturn == null || !(QuickSetup.asksDate(part) && QuickSetup.asksKm(part))) nextStep(260)
    }

    private fun finishStep(): View = col().apply {
        val parts = store.spec.parts
        val ready = parts.count { store.activeRecord(it) != null }
        addView(text("준비됐어요", TypeScale.HEADLINE, true).apply { setPadding(0, 0, 0, 0) })
        addView(text(buildString {
            append(if (ready > 0) "${parts.size}개 중 ${ready}개는 바로 다음 정비 시점을 계산해요." else "아직 넣은 기록이 없어요.")
            if (ready < parts.size) append(" 나머지는 홈에서 부품을 누르면 넣을 수 있어요.")
        }, TypeScale.BODY, tint = p.body).apply { setPadding(0, 0, 0, 0); setLineSpacing(dp(6).toFloat(), 1f) }, margin(16))
        // What each answer set up, so the owner can check it before starting; a row reopens its question.
        addView(col().apply {
            background = round(p.fill, Radius.SURFACE); setPadding(dp(32), dp(8), dp(32), dp(8))
            parts.forEachIndexed { i, part ->
                if (i > 0) addView(View(this@MainActivity).apply { setBackgroundColor(p.divider) }, LinearLayout.LayoutParams(-1, dp(1)))
                val record = store.activeRecord(part)
                addView(row().apply {
                    minimumHeight = dp(76)
                    addView(ImageView(this@MainActivity).apply { setImageResource(PartArt.resource(part)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                        LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(20) })
                    addView(text(PartArt.title(part), TypeScale.DETAIL, true).apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(text(if (record == null) "나중에 넣기" else QuickSetup.summary(obDate[part], obKm[part]).ifEmpty { "기록 있음" },
                        TypeScale.LABEL, false, if (record == null) p.sub else p.body).apply { setPadding(0, 0, 0, 0) })
                    val at = obSteps.indexOf(part.name)
                    if (at >= 0) { isClickable = true; contentDescription = "${PartArt.title(part)} 다시 답하기"; setOnClickListener { if (!restricted) reopen(at) } }
                })
            }
        }, margin(32))
        addView(bigButton("시작하기") { finishOnboarding() }, LinearLayout.LayoutParams(-1, dp(88)).apply { topMargin = dp(32) })
    }

    private fun stepShort(step: String) = when (step) {
        "permissions" -> "권한"; "finish" -> "완료"
        else -> when (Part.valueOf(step)) { Part.WIPER -> "와이퍼"; Part.ENGINE_OIL -> "엔진오일"; Part.BRAKE_PAD -> "브레이크"; Part.TIRE -> "타이어"
            Part.HVAC -> "냉난방"; Part.CABIN_FILTER -> "실내 필터" }
    }
    /**
     * The whole order at a glance: one segment per step, filled up to the current one, each with its name under it.
     * An answered step can be tapped to change its answer.
     */
    private fun stepper(): View = row().apply {
        gravity = Gravity.TOP
        val reached = obReturn ?: obIndex
        obSteps.forEachIndexed { i, step ->
            val now = i == obIndex; val done = i <= reached && !now
            addView(col().apply {
                addView(View(this@MainActivity).apply { background = round(if (done || now) p.primary else p.track, 3) }, LinearLayout.LayoutParams(-1, dp(6)))
                addView(text(stepShort(step), TypeScale.LABEL_SMALL, now, if (now) p.title else p.sub).apply { setPadding(0, dp(10), 0, 0); maxLines = 1 })
                if (done) { isClickable = true; contentDescription = "${stepTitle(step)} 다시 답하기"; setOnClickListener { if (!restricted) reopen(i) } }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = dp(8) })
        }
    }
    private fun finishOnboarding() { onboarding.edit().putBoolean("done", true).apply(); obSteps = emptyList(); obIndex = 0; obReturn = null; route = "home"; render() }
    private fun shade(on: Boolean) {
        val decor = window.decorView
        decor.overlay.remove(scrim)
        if (on) { scrim.alpha = (dim * 255).toInt(); scrim.setBounds(0, 0, decor.width, decor.height); decor.overlay.add(scrim) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        shade(false)
        if (requestCode == 105) {
            val granted = permissions.filterIndexed { i, _ -> results.getOrNull(i) == PackageManager.PERMISSION_GRANTED }.toSet()
            if (VehicleSignals.SPEED_PERMISSION in granted) VehicleSignals.connectCar(this)
            if (Manifest.permission.RECORD_AUDIO in granted) { VehicleSignals.micChanged(); SignalService.start(this, true) }
            // The location is still read only when the owner looks for a shop.
            if (Manifest.permission.POST_NOTIFICATIONS in granted) Reminders.evaluate(this)
            nextStep()
            if (granted.size < permissions.size) toast("허용하지 않은 정보는 쓰는 화면에서 다시 켤 수 있어요")
            return
        }
        if (requestCode == 101) { VehicleSignals.connectCar(this); render(); return }
        if (requestCode == 103) { VehiclePlaces.locate(this) { render(); if (it == null) toast("차량 위치를 받지 못했어요") }; return }
        if (requestCode == 104) { pendingRoute?.let { o -> if (VehiclePlaces.granted(this, VehiclePlaces.ROUTE_PERMISSION)) VehiclePlaces.navigate(this, o) { toast(it) } }; pendingRoute = null; return }
        if (requestCode == 102) { VehicleSignals.micChanged(); SignalService.start(this, true); render(); return }
        if (requestCode == 100) { Reminders.evaluate(this); render(); toast(if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) "주기 알림을 사용할 수 있어요" else "앱 안에서는 계속 주기를 확인할 수 있어요") }
    }
    private fun editName() {
        val field = EditText(this).apply {
            setText(store.vehicleName); setSingleLine(); hint = "차량 이름"; contentDescription = "차량 이름"; filters = arrayOf(InputFilter.LengthFilter(30))
            textSize = TypeScale.BODY.toFloat(); setTextColor(ink); setHintTextColor(muted); minHeight = dp(76); setPadding(dp(28), 0, dp(28), 0)
            background = round(p.fill, Radius.SURFACE); setSelection(text.length)
        }
        sheet(col().apply {
            addView(text("차량 이름", TypeScale.TITLE, true).apply { setPadding(0, 0, 0, 0) })
            addView(field, margin(24))
        }, listOf("저장" to {
            hideKeyboard()
            val name = field.text.toString().trim()
            if (name.isEmpty()) toast("이름을 입력해 주세요") else if (change { store.vehicleName = name }) render()
        }), cancel = "취소")
    }
    /**
     * The manual's severe conditions, as the owner sees their own driving. Any one moves engine oil to the manual's
     * severe interval; the app's own trip measurement keeps running beside it.
     */
    private fun chooseSevere(plan: MaintenancePlan) {
        val checked = BooleanArray(SevereUse.CONDITIONS.size) { (it + 1) in store.severe }
        val body = col().apply {
            addView(text("자주 해당하는 운행 조건", TypeScale.TITLE, true).apply { setPadding(0, 0, 0, dp(8)) })
            // Twelve conditions in two columns, in the manual's order down each column.
            val half = (SevereUse.CONDITIONS.size + 1) / 2
            addView(row().apply {
                for (c in 0..1) addView(col().apply {
                    for (i in c * half until minOf(SevereUse.CONDITIONS.size, (c + 1) * half)) addView(CheckBox(this@MainActivity).apply {
                        text = SevereUse.CONDITIONS[i]; textSize = TypeScale.LABEL.toFloat(); setTextColor(ink); isChecked = checked[i]
                        buttonTintList = android.content.res.ColorStateList.valueOf(p.primary); minHeight = dp(76); setPadding(dp(12), 0, 0, 0)
                        setOnCheckedChangeListener { _, on -> checked[i] = on }
                    }, margin())
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (c > 0) marginStart = dp(24) })
            }, margin(8))
        }
        sheet(body, listOf("저장" to {
            val chosen = checked.indices.filter { checked[it] }.map { it + 1 }.toSortedSet()
            if (change {
                store.severe = chosen
                store.plans[Part.ENGINE_OIL] = RecommendedCare.plan(Part.ENGINE_OIL, plan.recordId, plan.paused, chosen.isNotEmpty())
            }) { render(); toast(if (chosen.isEmpty()) "통상 조건 주기로 돌아갔어요" else "가혹 조건 주기(5,000 km 또는 6개월)로 바꿨어요") }
        }), cancel = "취소", width = 1200)
    }

    private fun change(block: () -> Unit): Boolean {
        if (restricted || store.loadFailed) { toast("지금은 저장할 수 없어요"); return false }
        return try { block(); store.save(); Reminders.evaluate(this); true }
        catch (_: Exception) { store.reload(); toast("저장하지 못했어요. 저장 공간을 확인해 주세요."); false }
    }

    // Judging reads the last stored odometer, as the reminders do, so the screen does not change once the live feed answers.
    // A new record takes only a reading the feed confirmed today.
    private fun vehicleMileage() = store.mileage?.takeIf { mileageLive && it.source == "vehicle" && it.updatedOn == LocalDate.now() }
    // Judged once per render: the vehicle map asks for every part's state on each draw.
    private var assessments: Map<Part, Assessment> = emptyMap()
    private fun status(item: Part) = assessments[item] ?: judge(item)
    private fun judge(item: Part) = CompositeJudge.assess(store.plans[item], store.activeRecord(item), store.mileage, LocalDate.now(), VehicleSignals.snapshot(), store.records, store.report(item))
    private fun summary(item: Part, a: Assessment): String =
        (a.drivenBy?.let { "$it · ${a.status.label}" } ?: summary(item, a.schedule)) + if (store.plans[item]?.paused == true) " · 알림 쉬는 중" else ""
    private fun summary(item: Part, result: DueResult): String = when (result.status) {
        DueStatus.UNREGISTERED -> "기록과 주기를 등록해 주세요"
        DueStatus.PAUSED -> "주기 알림을 잠시 쉬고 있어요"
        DueStatus.NEEDS_INFO -> result.missing.firstOrNull() ?: "기준 기록을 확인해 주세요"
        DueStatus.DUE -> "${store.plans[item]?.action?.label ?: "정비"} 기준에 도달했어요"
        else -> listOfNotNull(result.remainingDays?.let { "${it}일 남음" }, result.remainingKm?.let { "${fmt(it)} km 남음" }).joinToString(" · ")
    }
    private fun dueText(result: DueResult): String = buildList {
        result.dueDate?.let { add("$it" + (result.remainingDays?.let { d -> if (d < 0) " · ${abs(d)}일 지남" else if (d == 0L) " · 오늘" else " · ${d}일 남음" } ?: "")) }
        result.dueKm?.let { add("누적 ${fmt(it)} km" + (result.remainingKm?.let { k -> if (k < 0) " · ${fmt(abs(k))} km 지남" else if (k == 0L) " · 도달" else " · ${fmt(k)} km 남음" } ?: " · 현재 거리 미확인")) }
        if (isEmpty()) add("최근 정비 기록을 보완하면 다음 시점을 계산해요")
        if (result.dueDate != null && result.dueKm != null) add("둘 중 먼저 오는 기준")
    }.joinToString("\n")
    private fun recommended(part: Part) = RecommendedCare.plan(part, severe = store.severe.isNotEmpty())
    private fun policyText(p: MaintenancePlan) = listOfNotNull(p.intervalMonths?.let { "${it}개월" }, p.intervalKm?.let { "${fmt(it)} km" }).joinToString(" 또는 ") + "마다 ${p.action.label}"
    /**
     * Red is kept for a replacement that is due and for what the car or a reading says outright (pad or tread at its line,
     * low fluid or oil, a system that barely works). An inspection that has merely come round asks in orange.
     */
    /** Orange for upkeep that is due (an inspection, a tire rotation); red only for a replacement or a reading. */
    private fun tone(item: Part, a: Assessment): Int =
        if (inspectionDue(item, a) || a.status == DueStatus.DUE && a.task == ServiceAction.ROTATE) p.soon else statusColor(a.status)
    private fun inspectionDue(item: Part, a: Assessment): Boolean {
        val by = a.drivenBy.orEmpty()
        val reading = listOf("패드 잔량", "홈 깊이", "브레이크액", "엔진 오일량").any { by.startsWith(it) } || by.contains("거의 안")
        return a.status == DueStatus.DUE && (a.task ?: store.plans[item]?.action) == ServiceAction.INSPECT && !reading
    }
    private fun stateWord(item: Part, a: Assessment) = when {
        inspectionDue(item, a) -> "점검할 때"
        a.status == DueStatus.DUE -> "지금 할 때"
        else -> "곧 할 때"
    }
    /** The line under a ring: the job and how far, never a share. Null before there is a record to count from. */
    private fun ringLabel(item: Part, a: Assessment): String? {
        val plan = store.plans[item] ?: return null
        val record = store.activeRecord(item) ?: return null
        val guide = CareGuide.build(item, plan, record, a, VehicleSignals.snapshot(), store.records, store.mileage, store.report(item), LocalDate.now())
        return CareGuide.label(plan, a, guide.factors)
    }
    private fun statusColor(s: DueStatus) = when (s) { DueStatus.DUE -> p.due; DueStatus.SOON -> p.soon; DueStatus.UPCOMING -> p.ok; else -> p.sub }
    private fun icon(p: Part, size: Int) = ImageView(this).apply {
        setImageResource(when (p) { Part.WIPER -> R.drawable.part_wiper; Part.ENGINE_OIL -> R.drawable.part_oil; Part.BRAKE_PAD -> R.drawable.part_brake; Part.TIRE -> R.drawable.part_tire; Part.HVAC -> R.drawable.part_hvac; Part.CABIN_FILTER -> R.drawable.part_filter })
        imageTintList = android.content.res.ColorStateList.valueOf(accent); layoutParams = ViewGroup.LayoutParams(dp(size), dp(size)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun page() = col().apply { setPadding(dp(32), dp(20), dp(32), dp(30)) }
    private fun col() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun scroll(page: View, maxWidth: Int = 0): View = ScrollView(this).apply {
        isFillViewport = true
        if (maxWidth > 0) addView(FrameLayout(this@MainActivity).apply {
            addView(page, FrameLayout.LayoutParams(if (resources.configuration.screenWidthDp > maxWidth) dp(maxWidth) else -1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        }) else addView(page)
    }
    private fun text(value: String, size: Int, bold: Boolean = false, tint: Int = ink) = TextView(this).apply {
        // The guide asks for 18~20 px of real height at least; Label Small (20) is the floor everywhere.
        text = value; textSize = maxOf(size, TypeScale.LABEL_SMALL).toFloat(); setTextColor(tint); typeface = Fonts.of(if (bold) 700 else 400)
        setPadding(0, dp(6), 0, dp(6)); setLineSpacing(dp(3).toFloat(), 1f)
        // Korean wraps between any two syllables by default; break between phrases so no word is split across lines.
        if (android.os.Build.VERSION.SDK_INT >= 33) lineBreakWordStyle = android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
    }
    private fun button(value: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = value; textSize = TypeScale.LABEL.toFloat(); isAllCaps = false; minHeight = dp(64); setPadding(dp(28), dp(8), dp(28), dp(8)); typeface = Fonts.of(700)
        setTextColor(if (primary) Palette(dark).onPrimary else Palette(dark).title); background = round(if (primary) Palette(dark).primary else Palette(dark).primaryTint, Radius.SURFACE); stateListAnimator = null
        setOnClickListener { if (!restricted) action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(6), 0, dp(6)) }
    }
    private fun choice(value: String, selected: Boolean, action: () -> Unit) = button(if (selected) "✓  $value" else value, selected, action)
    private fun header(title: String, target: String) = row().apply {
        addView(button("닫기") { go(target) }, LinearLayout.LayoutParams(dp(100), dp(58)))
        addView(text(title, 19, true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(18) })
    }
    private fun box(title: String, body: String) = col().apply {
        setPadding(0, dp(14), 0, dp(14))
        addView(text(title, 17, true, muted)); addView(text(body, 20))
    }
    private fun field(hintText: String, value: String, description: String, changed: (String) -> Unit) = EditText(this).apply {
        hint = hintText; contentDescription = description; textSize = TypeScale.BODY.toFloat(); setTextColor(ink); setHintTextColor(muted)
        inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(); setText(value); filters = arrayOf(InputFilter.LengthFilter(10)); minHeight = dp(76)
        // The wide car screen would otherwise open a full-screen keyboard that hides the field's name and unit.
        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
        pad(18); background = round(surface, 16)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    private fun valid(field: EditText, optional: Boolean = false, max: Long = ScheduleCalculator.MAX_ODOMETER, min: Long = 0): Boolean {
        val value = field.text.toString(); if (optional && value.isEmpty()) return true
        if (value.toLongOrNull()?.let { it in min..max } != true) { field.error = "${fmt(min)}~${fmt(max)} 범위의 숫자를 입력해 주세요"; return false }
        return true
    }
    /** A yes/no popup whose buttons say what they do ("예약 취소" / "그대로 두기"), never a bare 확인/취소. */
    private fun confirm(title: String, body: String, yes: String, no: String = "그대로 두기", action: () -> Unit) {
        if (restricted) return
        sheet(col().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(text(title, TypeScale.TITLE, true).apply { setPadding(0, 0, 0, 0); gravity = Gravity.CENTER })
            addView(text(body, TypeScale.BODY, tint = p.body).apply { setLineSpacing(dp(4).toFloat(), 1f); gravity = Gravity.CENTER }, margin(12))
        }, listOf(yes to { if (!restricted) action() }), cancel = no)
    }
    private fun hideKeyboard() { if (::root.isInitialized) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0) }
    private fun margin(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun round(fill: Int, radius: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(radius).toFloat() }
    private fun View.pad(value: Int) = setPadding(dp(value), dp(value), dp(value), dp(value))
    private fun c(value: String) = Color.parseColor(value)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun fmt(value: Long) = String.format(java.util.Locale.KOREA, "%,d", value)
    private fun JSONObject.value(key: String) = optString(key).takeUnless { it == "null" } ?: ""
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}
