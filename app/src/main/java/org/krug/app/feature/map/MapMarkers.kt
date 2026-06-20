package org.krug.app.feature.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Paint.Cap
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.toColorInt

object MapMarkers {
    private const val CACHE_MAX_ENTRIES = 32

    // LRU cache — ograničen broj entry-ja sa eviction-om najstarijeg.
    // Bez ovoga svaka promena baterije pravila je novu bitmapu i čuvala je zauvek.
    private val cache: MutableMap<String, Bitmap> =
        object : LinkedHashMap<String, Bitmap>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap>?): Boolean =
                size > CACHE_MAX_ENTRIES
        }

    // Stabilna paleta — svaki član dobija svoju boju na osnovu hash-a uid-a.
    // Logo boje (pink/teal/orange) prve da bi najčešći hash-evi padali na brand boje;
    // ostatak ostaje za diversity preko 3-4 člana.
    val palette = listOf(
        "#E56B8F", // logo pink
        "#48B09B", // logo teal
        "#F3B250", // logo orange
        "#8B5CF6", // violet (akcent)
        "#EC4899", // hot pink (akcent)
        "#06B6D4", // cyan (akcent)
        "#F97316", // orange (akcent)
        "#3B82F6", // blue (akcent)
    )

    fun colorForUid(uid: String): String =
        palette[(uid.hashCode().toLong().and(0xFFFFFFFFL) % palette.size).toInt()]

    fun computeInitials(displayName: String): String {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2)
            else -> "${parts[0].first()}${parts[1].first()}"
        }.uppercase()
    }

    /**
     * Life360-style pin: krug sa uskim pointer-om dole, beli prsten oko, color fill,
     * unutra foto (ako ima) ili 1-2 slova inicijala.
     */
    fun pinMarker(
        context: Context,
        hex: String,
        photo: Bitmap? = null,
        initials: String? = null,
        batteryPct: Int? = null,
    ): Bitmap {
        // Bucket batteryPct na korake od 10% (0, 10, 20…100) da cache key ne menja
        // na svaku 1% promenu baterije — to je bio leak izvor.
        val battBucket = batteryPct?.let { ((it + 5) / 10) * 10 }?.coerceIn(0, 100) ?: -1
        val cacheKey = "$hex|${photo?.hashCode() ?: 0}|${initials.orEmpty()}|$battBucket"
        cache[cacheKey]?.let { return it }

        val density = context.resources.displayMetrics.density
        val w = (density * 60).toInt()
        val h = (density * 74).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cx = w / 2f
        val bubbleR = density * 22f
        val cy = bubbleR + density * 6

        val color = runCatching { hex.toColorInt() }.getOrDefault("#818CF8".toColorInt())
        val ringW = density * 3f
        val tailDrop = density * 12f
        val batteryStroke = density * 3f
        val batteryRadius = bubbleR + ringW + batteryStroke / 2 + density * 1f

        // Drop shadow.
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(70, 0, 0, 0)
            maskFilter = BlurMaskFilter(density * 5, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawPath(buildPinPath(cx, cy + density * 3, bubbleR, tailDrop), shadowPaint)

        // Beli spoljni prsten.
        canvas.drawPath(
            buildPinPath(cx, cy, bubbleR + ringW, tailDrop + density * 2),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE },
        )

        // Color fill.
        canvas.drawPath(
            buildPinPath(cx, cy, bubbleR, tailDrop),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
        )

        // Sadržaj unutar bubble-a.
        when {
            photo != null -> drawCircularPhoto(canvas, photo, cx, cy, bubbleR - density * 2)
            !initials.isNullOrBlank() -> drawInitials(canvas, initials, cx, cy, density)
        }

        // Battery ring oko cele pin glave — luk dužine batteryPct%, počinje na vrhu.
        if (batteryPct != null && batteryPct in 0..100) {
            drawBatteryRing(canvas, cx, cy, batteryRadius, batteryStroke, batteryPct)
        }

        cache[cacheKey] = bmp
        return bmp
    }

    private fun buildPinPath(
        cx: Float,
        cy: Float,
        radius: Float,
        tailDrop: Float,
    ): Path = Path().apply {
        addCircle(cx, cy, radius, Path.Direction.CW)
        // Uzak pointer — vrhovi su skoro na dnu kruga (sin25°≈0.42, cos25°≈0.91),
        // pa izgleda kao prirodna teardrop, a ne pehar.
        val sin = 0.423f
        val cos = 0.906f
        moveTo(cx - radius * sin, cy + radius * cos)
        lineTo(cx, cy + radius + tailDrop)
        lineTo(cx + radius * sin, cy + radius * cos)
        close()
    }

    private fun drawCircularPhoto(canvas: Canvas, photo: Bitmap, cx: Float, cy: Float, radius: Float) {
        val saved = canvas.saveLayer(RectF(cx - radius, cy - radius, cx + radius, cy + radius), null)
        // Circle mask
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(
            photo,
            null,
            RectF(cx - radius, cy - radius, cx + radius, cy + radius),
            maskPaint,
        )
        canvas.restoreToCount(saved)
    }

    private fun drawBatteryRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        stroke: Float,
        pct: Int,
    ) {
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        // Pozadinski track (svetlo sivkast).
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Cap.ROUND
        }
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        // Battery fill — logo teal / logo orange / kritično crvena.
        val batteryColor = when {
            pct >= 50 -> "#48B09B".toColorInt() // logo teal
            pct >= 20 -> "#F3B250".toColorInt() // logo orange
            else -> "#EF4444".toColorInt()      // kritično crvena
        }
        val sweep = (pct / 100f) * 360f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = batteryColor
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Cap.ROUND
        }
        // Počni od -90° (vrh), idi clockwise.
        canvas.drawArc(rect, -90f, sweep, false, fillPaint)
    }

    private fun drawInitials(canvas: Canvas, text: String, cx: Float, cy: Float, density: Float) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = density * if (text.length >= 2) 17 else 22
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val metrics = textPaint.fontMetrics
        val textY = cy - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, cx, textY, textPaint)
    }
}
