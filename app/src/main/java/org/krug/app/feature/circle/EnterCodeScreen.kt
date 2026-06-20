package org.krug.app.feature.circle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.krug.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterCodeScreen(
    prefilledCode: String? = null,
    onBack: () -> Unit,
    onJoined: (circleId: String) -> Unit,
    viewModel: EnterCodeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(prefilledCode) {
        if (prefilledCode != null) viewModel.setCode(prefilledCode)
    }
    LaunchedEffect(state.joinedCircleId) {
        state.joinedCircleId?.let(onJoined)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.enter_code_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.enter_code_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(24.dp))

            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::setCode,
                label = { Text(stringResource(R.string.enter_code_input_label)) },
                singleLine = true,
                isError = state.errorRes != null,
                supportingText = state.errorRes?.let { res -> { Text(stringResource(res)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    textAlign = TextAlign.Center,
                    letterSpacing = 8.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            // Pridruži se dugme — odmah ispod inputa (nema weight(1f) na dnu jer ga
            // tastatura prekriva). imePadding() na Column-u uvlači sve iznad tastature.
            Spacer(Modifier.size(24.dp))
            Button(
                onClick = viewModel::submit,
                enabled = state.code.length == EnterCodeViewModel.CODE_LENGTH && !state.joining,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.joining) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(12.dp))
                }
                Text(stringResource(R.string.enter_code_join_cta))
            }
        }
    }
}
