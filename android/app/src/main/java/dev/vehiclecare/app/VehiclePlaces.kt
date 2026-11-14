package dev.vehiclecare.app

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper

/**
 * The car's position (FusedLocation SDK) for ordering nearby services, and route requests to a chosen shop (NaviHelper
 * SDK). Both are asked for only when the owner opens nearby services; nothing here runs in the background.
 */
object VehiclePlaces {
    const val LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    const val ROUTE_PERMISSION = "pleos.car.permission.NAVI_ROUTE_SEARCH"
    private val handler = Handler(Looper.getMainLooper())
    var location: Pair<Double, Double>? = null
        private set
    private var fused: FusedLocation? = null
    private var navi: NaviHelper? = null

    fun granted(context: Context, permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** The last known position, once; [done] gets null when the car has none or access is blocked. */
    fun locate(context: Context, done: (Pair<Double, Double>?) -> Unit) {
        if (!granted(context, LOCATION_PERMISSION)) { done(null); return }
        runCatching {
            val f = fused ?: FusedLocation(context.applicationContext).also { it.initialize(); fused = it }
            if (!f.isSupported()) { done(null); return }
            f.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(data: LocationData?) { handler.post { if (data != null) location = data.latitude to data.longitude; done(data?.let { location }) } }
                override fun onError(e: Exception) { handler.post { done(null) } }
            })
        }.onFailure { done(null) }
    }

    /** Sends [offer]'s shop to the car's navigation as the destination; [result] gets a line for the owner. */
    fun navigate(context: Context, offer: Offer, result: (String) -> Unit) {
        val lat = offer.lat; val lon = offer.lon
        if (lat == null || lon == null) { result("방문 서비스라 길안내가 없어요"); return }
        runCatching {
            val n = navi ?: NaviHelper(context.applicationContext).also { navi = it }
            n.addListener(object : NaviHelperEventListener {
                override fun onRoutingCompleted() { handler.post { result("${offer.provider}로 길안내를 시작했어요") }; n.removeListener(this) }
                override fun onError(errorCode: NaviErrorCode) { handler.post { result("길안내를 시작하지 못했어요 ($errorCode)") }; n.removeListener(this) }
            })
            n.initialize({
                n.requestRoute(RouteInfo(latitude = lat, longitude = lon, displayLatitude = null, displayLongitude = null,
                    poiName = offer.provider, poiId = offer.id, address = offer.address ?: "", poiSubId = ""))
            }, { e -> handler.post { result("내비게이션에 연결하지 못했어요: ${e.message ?: e.javaClass.simpleName}") } })
        }.onFailure { result("내비게이션에 연결하지 못했어요: ${it.message ?: it.javaClass.simpleName}") }
    }
}
