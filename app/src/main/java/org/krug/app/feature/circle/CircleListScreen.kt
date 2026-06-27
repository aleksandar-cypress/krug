package org.krug.app.feature.circle

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.krug.app.R
import org.krug.app.core.circle.CircleModel
import org.krug.app.ui.brand.KrugLogo
import org.krug.app.ui.brand.pressScaleClickable
import org.krug.app.ui.theme.LogoBlue
import org.krug.app.ui.theme.LogoBlueLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onCircleClick: (String) -> Unit,
    viewModel: CircleListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.circles_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (state.circles.isNotEmpty()) {
                CreateCircleFab(
                    text = stringResource(R.string.circles_create_cta),
                    onClick = onCreate,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> SkeletonList()
                state.circles.isEmpty() -> EmptyState(onCreate = onCreate, onJoin = onJoin)
                else -> CircleList(
                    circles = state.circles,
                    onCircleClick = onCircleClick,
                    onJoin = onJoin,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Brand logo umesto generic group ikone — empty state je prvi utisak novog user-a.
        KrugLogo(
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.size(24.dp))
        Text(
            text = stringResource(R.string.circles_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.circles_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(32.dp))
        CreateCircleButton(
            text = stringResource(R.string.circles_create_cta),
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(12.dp))
        OutlinedButton(
            onClick = onJoin,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.circles_join_cta))
        }
    }
}

/**
 * Skeleton placeholder lista — 4 shimmer kartice koje matchuju CircleRow oblik.
 * Pokazuje se dok Firestore prvi snapshot ne stigne. Bez ovog, user vidi blank screen
 * sa "+" FAB-om što deluje kao bug ("ima podatke ali ne učitava ih").
 */
@Composable
private fun SkeletonList() {
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmer)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(shimmerColor),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .height(16.dp)
                                .fillMaxWidth(fraction = 0.62f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerColor),
                        )
                        Spacer(Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .height(12.dp)
                                .fillMaxWidth(fraction = 0.38f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerColor),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium FAB za "Napravi krug" — gradient pill sa belim "+" badge-om.
 * Veći touch target (60dp) i jači shadow nego stock Material FAB.
 */
@Composable
private fun CreateCircleFab(text: String, onClick: () -> Unit) {
    val gradient = Brush.linearGradient(
        colors = listOf(LogoBlue, LogoBlueLight),
    )
    Row(
        modifier = Modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(30.dp),
                ambientColor = LogoBlue,
                spotColor = LogoBlue,
            )
            .clip(RoundedCornerShape(30.dp))
            .background(gradient)
            .pressScaleClickable(onClick = onClick)
            .padding(start = 12.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}

/**
 * Full-width verzija za empty state — isti gradient, bez shadow halo-a.
 */
@Composable
private fun CreateCircleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val gradient = Brush.linearGradient(
        colors = listOf(LogoBlue, LogoBlueLight),
    )
    Row(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .pressScaleClickable(onClick = onClick)
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

@Composable
private fun CircleList(
    circles: List<CircleModel>,
    onCircleClick: (String) -> Unit,
    onJoin: () -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(circles, key = { it.id }) { circle ->
            CircleRow(circle = circle, onClick = { onCircleClick(circle.id) })
        }
        item {
            Spacer(Modifier.size(8.dp))
            OutlinedButton(
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.circles_join_cta))
            }
        }
    }
}

@Composable
private fun CircleRow(circle: CircleModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .pressScaleClickable(pressedScale = 0.98f, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(parseColor(circle.colorHex)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CircleIconAssets.forKey(circle.iconKey),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(circle.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = pluralStringResource(R.plurals.circles_members_count, circle.memberIds.size, circle.memberIds.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun parseColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(Color(0xFF4F46E5))
