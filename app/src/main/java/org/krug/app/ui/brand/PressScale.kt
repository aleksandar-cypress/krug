package org.krug.app.ui.brand

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role

/**
 * Replikuje `Modifier.clickable` ali sa dodatkom press scale animacije — kad user
 * pritisne element, sve unutra se scale-uje na [pressedScale] (default 0.96), pa se
 * vraća sa spring-om kad pusti.
 *
 * Spring je tuninovan tako da daje "lickable" osećaj: damping 0.55 (lagani overshoot),
 * stiffness 700 (brza reakcija). Bez overshoot-a deluje stale; sa previše overshoot-a
 * deluje "wobbly".
 *
 * Koristi se na primary CTA-ima (gradient pill button-ima) gde Material Button visual
 * feedback nije dovoljan ili button koristi custom Modifier-baziran layout.
 *
 * TalkBack: default `role = Role.Button` da screen reader izgovori "Button" umesto
 * generičkog "clickable" — pomaže user-u sa TalkBack-om da razlikuje button od običnog
 * kliktavog reda. Prosleđuj `role = null` ako element nije semantički button.
 */
@Composable
fun Modifier.pressScaleClickable(
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press-scale",
    )
    return this
        .scale(currentScale)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/**
 * Varijanta sa long-press support-om. Koristi combinedClickable umesto plain clickable.
 * Ne extract-ovana kao default arg-om na `pressScaleClickable` jer `combinedClickable`
 * ima drugačiju semantiku (može da eat-uje ripple na long-press) i experimental API.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressScaleCombinedClickable(
    pressedScale: Float = 0.96f,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press-scale",
    )
    return this
        .scale(currentScale)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}
