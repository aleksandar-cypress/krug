package org.krug.app.feature.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.krug.app.core.util.confirmHaptic
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.krug.app.R
import org.krug.app.ui.brand.KrugLogo
import org.krug.app.ui.brand.pressScaleClickable
import org.krug.app.ui.theme.LogoBlue
import org.krug.app.ui.theme.LogoBlueLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterCodeScreen(
    prefilledCode: String? = null,
    onBack: () -> Unit,
    onJoined: (circleId: String) -> Unit,
    viewModel: EnterCodeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    LaunchedEffect(prefilledCode) {
        if (prefilledCode != null) viewModel.setCode(prefilledCode)
    }
    LaunchedEffect(state.joinedCircleId) {
        state.joinedCircleId?.let {
            // Success haptic pre nav-a — korisnik oseti da je join uspeo.
            view.confirmHaptic()
            onJoined(it)
        }
    }
    // Auto-focus na input + open keyboard čim ekran otvori — user ne mora dodatno
    // da tap-uje na input box. Magic moment "pridruži se" trebao bi da bude bez trenja.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.enter_code_title)) },
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
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.size(8.dp))

            // Brand hero — "magic moment" pri pridruživanju krugu.
            KrugLogo(modifier = Modifier.size(96.dp))

            Spacer(Modifier.size(20.dp))

            Text(
                text = stringResource(R.string.enter_code_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(28.dp))

            // 6-box keypad input. BasicTextField je nevidljiv (alpha 0), ali zadržava
            // fokus i hendluje IME numeric keyboard. Vizuelno: Row od 6 box-ova, svaki
            // pokazuje cifru iz state.code (ili prazno). Active box (idx == code.length)
            // ima brand border + glow halo.
            CodeKeypad(
                code = state.code,
                length = EnterCodeViewModel.CODE_LENGTH,
                hasError = state.errorRes != null,
                focusRequester = focusRequester,
                onValueChange = viewModel::setCode,
            )

            Spacer(Modifier.size(12.dp))

            // Status text (cooldown countdown ili error message)
            val cooldownSec = state.cooldownRemainingSec
            val errorRes = state.errorRes
            when {
                cooldownSec > 0 -> Text(
                    text = stringResource(R.string.enter_code_cooldown_countdown, cooldownSec),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                errorRes != null -> Text(
                    text = stringResource(errorRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                else -> Spacer(Modifier.size(20.dp))
            }

            Spacer(Modifier.size(24.dp))

            // Join CTA — brand gradient pill (konzistentno sa CreateCircleFab i ShowInvite
            // Share button-om). Disabled dok kod nije popunjen ili je cooldown aktivan.
            val cooldownActive = state.cooldownRemainingSec > 0
            val enabled = state.code.length == EnterCodeViewModel.CODE_LENGTH &&
                !state.joining && !cooldownActive
            JoinGradientButton(
                enabled = enabled,
                loading = state.joining,
                cooldownSec = if (cooldownActive) state.cooldownRemainingSec else 0,
                onClick = viewModel::submit,
            )
        }

        // Splash-style overlay tokom joining-a — rotirajući logo + status text. Pokriva
        // sadržaj da user ne vidi statičan ekran dok čekamo Firestore commit + listener
        // sync. Kad pop-back-stack na Map nakon ovog, Map ima već non-empty circles
        // pa empty-state CTA ("Imam pozivnicu") više ne flickeruje.
        if (state.joining) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KrugLogo(
                        modifier = Modifier.size(140.dp),
                        continuousSpin = true,
                    )
                    Spacer(Modifier.size(24.dp))
                    Text(
                        text = stringResource(R.string.enter_code_joining_status),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
      }
    }
}

@Composable
private fun CodeKeypad(
    code: String,
    length: Int,
    hasError: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Nevidljiv BasicTextField — hendluje IME + caret + paste. alpha 0 = nije
        // vidljiv, ali fokusabilan. Postavljen iznad Row-a box-ova tako da tap na
        // bilo koji box otvara tastaturu (preko clickable koji požuruje fokus).
        BasicTextField(
            value = code,
            onValueChange = { new ->
                if (new.length <= length && new.all { it.isDigit() }) onValueChange(new)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .focusRequester(focusRequester),
        )
        // Vizuelni layer — 6 box-ova preko nevidljivog text field-a.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { focusRequester.requestFocus() },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until length) {
                CodeBox(
                    char = code.getOrNull(i),
                    isActive = i == code.length,
                    hasError = hasError,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CodeBox(
    char: Char?,
    isActive: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        hasError -> MaterialTheme.colorScheme.error
        isActive -> LogoBlue
        char != null -> LogoBlueLight
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isActive || hasError) 2.dp else 1.dp
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = char?.toString() ?: "",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun JoinGradientButton(
    enabled: Boolean,
    loading: Boolean,
    cooldownSec: Int,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        LogoBlue.copy(alpha = alpha),
                        LogoBlueLight.copy(alpha = alpha),
                    ),
                ),
            )
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
            Spacer(Modifier.size(12.dp))
        }
        Text(
            text = if (cooldownSec > 0) {
                stringResource(R.string.enter_code_cooldown_button, cooldownSec)
            } else {
                stringResource(R.string.enter_code_join_cta)
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}
