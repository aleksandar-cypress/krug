package org.krug.app.ui.brand

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.krug.app.ui.theme.LogoBlue
import org.krug.app.ui.theme.LogoOrange
import org.krug.app.ui.theme.LogoPink
import org.krug.app.ui.theme.LogoTeal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Krug-branded loading indicator. 4 figure (jedna za svaku brand boju) rotiraju oko
 * centra. Zamenjuje generic Material `CircularProgressIndicator` na ekranima gde
 * brand prisustvo poboljšava percepciju loading-a kao deo proizvoda, ne sistemskog
 * čekanja.
 *
 * Razlikuje se od [KrugLogo] po tome što ne render-uje pun logo (4 figure + luci) već
 * samo 4 tačke u kružnoj formaciji — diskretnije, manje "weight"-a, brže prepoznatljivo
 * kao "loading" a ne "brand mark".
 */
@Composable
fun KrugSpinner(
    modifier: Modifier = Modifier,
    durationMs: Int = 1_400,
    dotRadiusFraction: Float = 0.18f,
    orbitRadiusFraction: Float = 0.72f,
) {
    val transition = rememberInfiniteTransition(label = "krug-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "krug-spinner-angle",
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val half = minOf(cx, cy)
        val orbit = half * orbitRadiusFraction
        val dot = half * dotRadiusFraction
        for (i in 0..3) {
            val rad = ((angle + i * 90f) * PI / 180f).toFloat()
            val x = cx + cos(rad) * orbit
            val y = cy + sin(rad) * orbit
            drawCircle(
                color = DOT_COLORS[i],
                radius = dot,
                center = Offset(x, y),
            )
        }
    }
}

private val DOT_COLORS: List<Color> = listOf(LogoBlue, LogoPink, LogoTeal, LogoOrange)
