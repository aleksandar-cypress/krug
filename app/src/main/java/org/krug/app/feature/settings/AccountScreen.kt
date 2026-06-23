package org.krug.app.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.krug.app.R

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignedOut()
    }

    SettingsSubScaffold(
        title = stringResource(R.string.account_title),
        onBack = onBack,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(photoUrl = state.photoUrl)
            Spacer(Modifier.size(16.dp))
            Text(
                text = state.displayName.ifBlank { state.email.ifBlank { stringResource(R.string.account_anonymous) } },
                style = MaterialTheme.typography.titleLarge,
            )
            if (state.displayName.isNotBlank() && state.email.isNotBlank()) {
                Text(
                    text = state.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(24.dp))
            OutlinedTextField(
                value = state.nameInput,
                onValueChange = viewModel::setNameInput,
                label = { Text(stringResource(R.string.account_display_name_label)) },
                placeholder = { Text(stringResource(R.string.account_display_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(12.dp))
            Button(
                onClick = viewModel::saveName,
                enabled = !state.saving && state.nameInput.trim().isNotEmpty()
                    && state.nameInput.trim() != state.displayName,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(12.dp))
                }
                Text(
                    text = if (state.justSaved) {
                        stringResource(R.string.account_display_name_saved)
                    } else {
                        stringResource(R.string.account_display_name_save)
                    },
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { showSignOutConfirm = true },
                enabled = !state.signingOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.signingOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.size(12.dp))
                }
                Text(stringResource(R.string.account_sign_out))
            }
            // Roditeljska kontrola — dete ne sme da obriše nalog samostalno.
            if (!state.isChildAnywhere) {
                Spacer(Modifier.size(8.dp))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !state.deleting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.deleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.account_delete_progress),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.account_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(stringResource(R.string.account_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.account_sign_out_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        viewModel.signOut(context)
                    },
                ) { Text(stringResource(R.string.account_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.account_delete_confirm_title)) },
            text = { Text(stringResource(R.string.account_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteAccount(context)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.account_delete_confirm_cta),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (state.deleteNeedsReauth) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteReauth() },
            title = { Text(stringResource(R.string.account_delete_reauth_title)) },
            text = { Text(stringResource(R.string.account_delete_reauth_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDeleteReauth() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun Avatar(photoUrl: String?) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        } else {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}
