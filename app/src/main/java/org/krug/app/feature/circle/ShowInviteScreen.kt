package org.krug.app.feature.circle

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.krug.app.R
import org.krug.app.ui.brand.KrugLogo
import org.krug.app.ui.brand.pressScaleClickable
import org.krug.app.ui.theme.LogoBlue
import org.krug.app.ui.theme.LogoBlueLight

@Composable
fun ShowInviteScreen(
    circleName: String,
    inviteCode: String,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val shareText = stringResource(R.string.invite_share_template, circleName, inviteCode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.size(24.dp))

        // Brand hero — krug se "širi" sa novim članom. Vizuelno povezuje invite akciju sa
        // brand-om umesto generic "evo ti kod" iskustva.
        KrugLogo(modifier = Modifier.size(96.dp))

        Spacer(Modifier.size(20.dp))

        Text(
            text = stringResource(R.string.invite_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.invite_body, circleName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(36.dp))

        CodeDisplay(code = inviteCode)

        Spacer(Modifier.size(16.dp))

        CopyButton(code = inviteCode, context = context)

        Spacer(Modifier.weight(1f))

        // Primary CTA — brand gradient pill (match CreateCircleFab pattern). Daje
        // ovom "magic moment" trenutku težinu — share je glavna akcija, ne sporedna.
        ShareGradientButton(
            onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, null))
            },
        )

        Spacer(Modifier.size(8.dp))
        TextButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.invite_done_cta))
        }
    }
}

@Composable
private fun CodeDisplay(code: String) {
    // Brand gradient okvir + bele box-ove sa cifrom. Veće, expresivnije nego primaryContainer
    // flat box. shadow daje "lifted" osećaj — kod je glavni objekat ekrana.
    Row(
        modifier = Modifier
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = LogoBlue,
                spotColor = LogoBlue,
            )
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors = listOf(LogoBlue, LogoBlueLight)))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        code.forEach { c ->
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = c.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = LogoBlue,
                )
            }
        }
    }
}

@Composable
private fun CopyButton(code: String, context: Context) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1_800)
            copied = false
        }
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Krug invite", code))
                copied = true
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = if (copied) LogoBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(
                if (copied) R.string.invite_copy_code_copied else R.string.invite_copy_code,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (copied) LogoBlue else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ShareGradientButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(colors = listOf(LogoBlue, LogoBlueLight)))
            .pressScaleClickable(onClick = onClick)
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.invite_share_cta),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}
