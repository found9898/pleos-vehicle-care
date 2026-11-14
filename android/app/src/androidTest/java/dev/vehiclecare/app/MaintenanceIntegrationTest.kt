package dev.vehiclecare.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MaintenanceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val today = LocalDate.now()
    private val testName = "phase1-isolated-test"

    @Test fun recordsAndDraftsSurviveAStoreRecreation() {
        val store = CareStore(context, testName); store.reset()
        try {
            val record = ServiceRecord("first", Part.TIRE, today.minusMonths(6), 10_000, ServiceAction.INSPECT)
            store.records += record
            store.plans[Part.TIRE] = MaintenancePlan(Part.TIRE, record.id, 12, 20_000, ServiceAction.INSPECT, "정비소 안내")
            store.mileage = Mileage(15_000, today); store.vehicleName = "테스트 차량"; store.save()
            store.saveDraft(Part.WIPER, JSONObject().put("step", 3).put("months", "12"))
            val reopened = CareStore(context, testName)
            assertEquals(store.records, reopened.records); assertEquals(store.plans, reopened.plans)
            assertEquals(store.mileage, reopened.mileage); assertEquals("테스트 차량", reopened.vehicleName)
            assertEquals(3, reopened.draft(Part.WIPER)!!.getInt("step"))
            assertEquals(record, reopened.activeRecord(Part.TIRE))
        } finally { store.reset() }
    }

    @Test fun corruptOrNewerSchemaIsPreservedAndCannotBeOverwritten() {
        val prefs = context.getSharedPreferences(testName, Context.MODE_PRIVATE)
        listOf("not JSON", "{\"version\":99}").forEach { bytes ->
            prefs.edit().putString("state", bytes).commit()
            val store = CareStore(context, testName)
            assertTrue(store.loadFailed)
            try { store.save(); fail("Must not overwrite unreadable state") } catch (_: IllegalStateException) { }
            assertEquals(bytes, prefs.getString("state", null))
            store.reset()
        }
    }

    @Test fun deletingOrReplacingBaselinePreservesHistoryAndRecalculates() {
        val store = CareStore(context, testName); store.reset()
        try {
            val first = ServiceRecord("old", Part.CABIN_FILTER, today.minusYears(1), 10_000, ServiceAction.REPLACE)
            val latest = first.copy(id = "new", date = today, odometerKm = 30_000)
            store.records += listOf(first, latest)
            store.plans[first.part] = MaintenancePlan(first.part, latest.id, 12, 10_000, first.action, "직접 정한 주기")
            store.mileage = Mileage(30_000, today); store.save()
            val reopened = CareStore(context, testName)
            assertEquals(latest, reopened.activeRecord(first.part))
            assertEquals(40_000L, ScheduleCalculator.calculate(reopened.plans[first.part], reopened.activeRecord(first.part), reopened.mileage, today).dueKm)
            reopened.records.removeAll { it.id == latest.id }; reopened.save()
            assertNull(CareStore(context, testName).activeRecord(first.part))
            assertEquals(1, CareStore(context, testName).records.size)
        } finally { store.reset() }
    }

    @Test fun recommendedFlowOnlyAsksForHistoryAndPreservesDraft() = withProductionBackup {
        CareStore(context).reset()
        launch(); click("와이퍼"); waitText("마지막 교체 날짜를")
        click("마지막 날짜 넣기"); waitText("최근 교체일만")
        val visible = allNodes(instrumentation.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }.joinToString(" | ")
        assertFalse(visible.contains("알림 간격")); assertFalse(visible.contains("거리 기준")); assertFalse(visible.contains("주행거리"))
        click("오늘"); click("닫기"); click("마지막 날짜 넣기"); click("기록 저장")
        declineReminders()
        waitText("교체까지")
        val saved = CareStore(context)
        assertEquals(12, saved.plans[Part.WIPER]?.intervalMonths)
        assertNull(saved.plans[Part.WIPER]?.intervalKm)
        assertEquals(today, saved.activeRecord(Part.WIPER)?.date)
        assertNull(saved.draft(Part.WIPER))
        click("더보기"); click("알림 일시정지")
        click("더보기"); click("이전 이력 입력·수정"); click("기록 저장")
        assertTrue(CareStore(context).plans[Part.WIPER]!!.paused)
    }

    @Test fun replacementIsSmallAndRequiresAnActualRecordAction() = withProductionBackup {
        CareStore(context).reset()
        val activity = launch(); click("와이퍼"); waitText("마지막 교체 날짜를")
        captureWindow(activity, "care-recommended-empty.png")
        clickUntil("교체했어요", "오늘 교체하셨나요?")
        // Merely opening a part or its confirmation must not create a service event.
        assertTrue(CareStore(context).records.isEmpty())
        click("기록하기"); declineReminders(); waitText("교체까지")
        assertEquals(1, CareStore(context).records.size)
        captureWindow(activity, "care-recommended-wiper.png")
        click("닫기"); waitText("설정")
        captureWindow(activity, "care-recommended-home.png")
    }

    @Test fun recommendedMigrationKeepsHistoryAndPauseWithoutInventingBaselines() = withProductionBackup {
        val store = CareStore(context); store.reset()
        val record = ServiceRecord("legacy-wiper", Part.WIPER, today.minusDays(5), 40_000, ServiceAction.REPLACE)
        store.records += record
        store.plans[Part.WIPER] = MaintenancePlan(Part.WIPER, record.id, 5, 2_000, record.action, "직접 정한 주기", true)
        store.save(); store.applyRecommendations()
        val saved = CareStore(context)
        assertEquals(record, saved.activeRecord(Part.WIPER)); assertTrue(saved.plans[Part.WIPER]!!.paused)
        assertEquals(12, saved.plans[Part.WIPER]?.intervalMonths); assertNull(saved.plans[Part.WIPER]?.intervalKm)
        assertEquals(6, saved.plans.size); assertEquals(1, saved.records.size)
        assertNull(saved.activeRecord(Part.ENGINE_OIL))
        saved.plans.remove(Part.TIRE); saved.save()
        val reopened = CareStore(context); reopened.applyRecommendations(); assertNull(reopened.plans[Part.TIRE])
    }

    @Test fun inspectionCanBeRecordedWithoutClaimingAReplacement() = withProductionBackup {
        CareStore(context).reset()
        launch(); waitText(" km · "); click("브레이크 패드")
        // Without a record the first button asks for the date; the inspection waits under "다른 정비 기록".
        clickUntil("다른 정비 기록", "어떤 정비를"); clickUntil("점검 받았어요", "오늘 점검하셨나요?"); click("기록")
        declineReminders(); waitText("점검까지")
        val saved = CareStore(context)
        assertEquals(ServiceAction.INSPECT, saved.activeRecord(Part.BRAKE_PAD)?.action)
        assertEquals(ServiceAction.INSPECT, saved.plans[Part.BRAKE_PAD]?.action)
        assertNotNull(saved.activeRecord(Part.BRAKE_PAD)?.odometerKm)
    }

    @Test fun vehicleOdometerIsReadFromRealPleosSdkAndPersistedAutomatically() = withProductionBackup {
        CareStore(context).reset()
        launch()
        val deadline = SystemClock.uptimeMillis() + 25000
        while (SystemClock.uptimeMillis() < deadline && CareStore(context).mileage?.source != "vehicle") SystemClock.sleep(200)
        val mileage = CareStore(context).mileage ?: throw AssertionError("Pleos odometer callback not received")
        assertEquals("vehicle", mileage.source); assertTrue(mileage.km >= 0)
        assertEquals(today, mileage.updatedOn); assertTrue(mileage.observedAt > 0)
        waitText(" km · ")
        val visible = allNodes(instrumentation.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }.joinToString(" | ")
        assertFalse(visible.contains("주행거리 미입력")); assertFalse(visible.contains("거리 갱신"))
    }

    @Test fun notificationsAreDeduplicatedAndPausedItemsCancel() = withProductionBackup {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        Reminders.clear(context); Reminders.schedule(context)
        val store = CareStore(context); store.reset()
        val record = ServiceRecord("notify", Part.WIPER, today.minusMonths(13), null, ServiceAction.REPLACE)
        store.records += record
        val plan = MaintenancePlan(record.part, record.id, 12, null, record.action, "직접 정한 주기")
        store.plans[record.part] = plan; store.save()
        Reminders.evaluate(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        SystemClock.sleep(300)
        assertEquals(1, manager.activeNotifications.count { it.id == 500 + record.part.ordinal })
        val prefs = context.getSharedPreferences("vehicle-care-reminders", Context.MODE_PRIVATE)
        val fingerprint = prefs.getString(record.part.name, null)
        assertNotNull(fingerprint)
        Reminders.evaluate(context)
        assertEquals(fingerprint, prefs.getString(record.part.name, null))
        assertEquals(1, manager.activeNotifications.count { it.id == 500 + record.part.ordinal })
        store.plans[record.part] = plan.copy(paused = true); store.save(); Reminders.evaluate(context)
        SystemClock.sleep(300)
        assertFalse(manager.activeNotifications.any { it.id == 500 + record.part.ordinal })
    }

    @Test fun ringsShowRemainingCyclesAndUnknownHistorySeparately() = withProductionBackup {
        val store = CareStore(context); store.reset(); store.applyRecommendations()
        val record = ServiceRecord("visual-wiper", Part.WIPER, today.minusMonths(6), null, ServiceAction.REPLACE)
        store.records += record; store.plans[Part.WIPER] = RecommendedCare.plan(Part.WIPER, record.id); store.save()
        val activity = launch()
        node { it.contentDescription?.toString()?.startsWith("와이퍼,") == true && it.contentDescription.toString().contains("교체까지") && !it.contentDescription.toString().contains("%") }
        node { it.contentDescription?.toString()?.startsWith("브레이크 패드, 이력 대기") == true }
        click("와이퍼"); waitText("판단 근거"); waitText("교체까지")
        captureWindow(activity, "care-recommended-half.png")
    }

    private fun launch(): android.app.Activity {
        // The tests start past the first-run permission screen; it has its own test.
        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE).edit().putBoolean("done", true).commit()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val activity = monitor.waitForActivityWithTimeout(5_000) ?: throw AssertionError("Activity did not launch")
        instrumentation.removeMonitor(monitor)
        waitText("설정")
        val until = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < until && allNodes(instrumentation.uiAutomation.rootInActiveWindow).any { it.text?.contains("차량 사용 상태를 확인") == true }) SystemClock.sleep(100)
        return activity
    }
    private fun captureWindow(activity: android.app.Activity, filename: String) {
        val latch = java.util.concurrent.CountDownLatch(1)
        var result = -1
        val bitmap = android.graphics.Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, android.graphics.Bitmap.Config.ARGB_8888)
        android.view.PixelCopy.request(activity.window, bitmap, { result = it; latch.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
        assertTrue("Window capture timed out", latch.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(android.view.PixelCopy.SUCCESS, result)
        val bytes = java.io.ByteArrayOutputStream().apply { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        require(filename.matches(Regex("[a-z0-9-]+\\.png")))
        val descriptors = instrumentation.uiAutomation.executeShellCommandRw("dd of=/data/local/tmp/$filename")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { it.write(bytes) }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).use { it.readBytes() }
    }
    private fun allNodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (node == null) return emptyList()
        return listOf(node) + (0 until node.childCount).flatMap { allNodes(node.getChild(it)) }
    }
    private fun node(match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val until = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < until) {
            allNodes(instrumentation.uiAutomation.rootInActiveWindow).find(match)?.let { return it }
            SystemClock.sleep(150)
        }
        throw AssertionError("UI element not found. Visible: " + allNodes(instrumentation.uiAutomation.rootInActiveWindow).mapNotNull { it.text?.toString() }.joinToString(" | "))
    }
    private fun click(text: String) {
        var target = node { it.text?.toString() == text || it.contentDescription?.toString() == text || it.contentDescription?.toString()?.startsWith("$text,") == true }
        while (!target.isClickable && target.parent != null) target = target.parent
        assertTrue("click $text", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync(); SystemClock.sleep(220)
    }
    private fun enter(description: String, value: String) {
        val target = node { it.contentDescription?.toString() == description }
        assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        instrumentation.waitForIdleSync()
    }
    /**
     * Taps until [expect] appears. A vehicle signal can repaint the screen between finding a button and tapping it,
     * and the tap then lands on a detached view; it is retried only while the expected text is still absent.
     */
    private fun clickUntil(text: String, expect: String) {
        repeat(3) {
            click(text)
            val until = SystemClock.uptimeMillis() + 3_000
            while (SystemClock.uptimeMillis() < until) {
                if (allNodes(instrumentation.uiAutomation.rootInActiveWindow).any { it.text?.toString()?.contains(expect) == true }) return
                SystemClock.sleep(150)
            }
        }
        waitText(expect)
    }
    /** The first record offers reminders once, unless the permission is already granted; the tests decline it. */
    private fun declineReminders() {
        val until = SystemClock.uptimeMillis() + 2_500
        while (SystemClock.uptimeMillis() < until) {
            if (allNodes(instrumentation.uiAutomation.rootInActiveWindow).any { it.text?.toString() == "나중에" }) { click("나중에"); return }
            SystemClock.sleep(150)
        }
    }
    private fun waitText(text: String) { node { it.text?.toString()?.contains(text) == true }; instrumentation.waitForIdleSync() }
    private fun withProductionBackup(block: () -> Unit) {
        check(context.packageName == "dev.vehiclecare.verification") { "Use -PcareIntegration=true to protect the real app's records" }
        val names = listOf("vehicle-care-v1", "vehicle-care-reminders")
        val backups = names.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        try { block() }
        finally {
            Reminders.clear(context)
            names.forEach { name ->
                val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                backups.getValue(name).forEach { (key, value) -> when (value) {
                    is String -> edit.putString(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                } }
                edit.commit()
            }
            // This is an isolated APK. Revoking a permission here would kill the instrumentation process.
            Reminders.schedule(context)
        }
    }
}
