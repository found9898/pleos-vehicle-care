package dev.vehiclecare.app

import android.graphics.Color
import android.graphics.Typeface

/**
 * Colours from the Pleos Connect design guide (docs-design/foundations/colors), light and dark. Grey scale basic_00..900,
 * text as static dark/light at 100/84/64 %, the filled button basic_600, the basic button alpha_dark_50, and the
 * informative and regulation colours for status. Names stay this app's own; the comment on each says which token it is.
 */
class Palette(private val dark: Boolean) {
    private fun c(light: String, night: String) = Color.parseColor(if (dark) night else light)
    /** background_secondary: behind every panel. */
    val canvas = c("#F7F8FA", "#131417")
    /** surface_basic: panels, cards and popups. */
    val card = c("#FFFFFF", "#1D1E21")
    /** surface_high: tiles, tracks and segmented controls. */
    val fill = c("#EDEEF2", "#313236")
    /** surface_accent: a track that sits on surface_high. */
    val track = c("#E0E1E6", "#44464E")
    /** The chosen tab of a segmented control (Box type): white on grey by day, a lighter grey at night. */
    val raised = c("#FFFFFF", "#44464E")
    val divider = c("#E4E6EA", "#313236")
    /** Three text colours, all at 4.5:1 or more on the panel: primary, secondary and meta. */
    val title = c("#131417", "#FFFFFF")
    val body = c("#3A3C42", "#D6FFFFFF")
    val sub = c("#5B5E66", "#A4A8AE")
    /** button_filled_enabled (basic_600) and the label on it. */
    val primary = c("#44464E", "#686A72")
    val onPrimary = c("#FFFFFF", "#FFFFFF")
    /** button_basic_enabled (alpha_dark_50); its label is text_primary. */
    val primaryTint = c("#0D131417", "#1FFFFFFF")
    /** On track carries no hue: colour is kept for what needs attention (soon, due). basic_600 by day, basic_500 at night. */
    val ok = c("#44464E", "#C7C9CE")
    /** regulation_orange and regulation_red: soon and due. */
    val soon = c("#FF8A00", "#FF8A00")
    /** Darker than regulation_red by day so red text clears 4.5:1 on the canvas (#F62E24 was 3.7:1). */
    val due = c("#D0201A", "#FF4339")
    /** Behind part photos: plain white by day; at night a soft light in the middle so black rubber and metal stay visible. */
    val stageCenter = c("#FFFFFF", "#4A4E58")
    val stageEdge = c("#FFFFFF", "#25272D")
    /** The brake section: pad left, the part past the replacement line, disc and piston, backing plate. */
    val rib = c("#8E919A", "#787C85")
    val padZone = c("#5B5E66", "#44464E")
    val solid = c("#C7C9CE", "#44464E")
    val plate = c("#44464E", "#C7C9CE")
    /** The normal range drawn on a gauge track. */
    val band = c("#C7DBFF", "#1F3F73")
}

/** Sizes from the guide's type hierarchy, one step down, in dp; the Connect display is 160 dpi, so 1 dp is 1 design px. Nothing a driver reads goes under 24 dp except card meta. */
object TypeScale {
    const val HEADLINE = 40
    /** The detail's one sentence: Headline Small. */
    const val HEADLINE_DETAIL = 38
    /** A section title inside a screen: a label above its rows, one step over them and quieter in colour. */
    const val SECTION = 24
    const val TITLE = 28
    /** The home's one line: Title Large. */
    const val TITLE_LARGE = 34
    const val BODY_LARGE = 26
    const val BODY = 24
    const val LABEL = 24
    const val LABEL_SMALL = 20
    /**
     * Running text on the detail and its rows. The guide's 26~30 read as oversized beside the cards on the 2560 px
     * Connect display, so rows sit at the 24 dp floor and hierarchy comes from weight and colour instead of size.
     */
    const val DETAIL = 24
    /** Notes, meta, tabs and links a driver reads: 24 dp, the Android for Cars floor for secondary text. */
    const val META = 24
}

/** One radius for surfaces (tiles, buttons, chips) and one for popups. */
object Radius {
    const val SURFACE = 20
    const val POPUP = 32
}

object Fonts {
    /**
     * Asta Sans, the system UI face the guide names. On the Pleos image the named family `pretendard` in
     * fonts_customization.xml points at the AstaSansOTF files weight by weight (600 Bold, 700 ExtraBold); `sans-serif`
     * points at the same files. At 600 (Bold) the cut reads barely heavier than Regular on screen, so bold text uses 700.
     */
    private val base: Typeface by lazy { Typeface.create("pretendard", Typeface.NORMAL) }
    /** [weight] 400 regular, 600 bold, 700 extra bold. */
    fun of(weight: Int): Typeface = Typeface.create(base, weight, false)
}
