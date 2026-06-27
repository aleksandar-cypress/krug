package org.krug.app.feature.circle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.krug.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleDetailScreen(
    onBack: () -> Unit,
    onLeftOrDeleted: () -> Unit,
    onShowInvite: (circleId: String, circleName: String, code: String) -> Unit,
    viewModel: CircleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    val editSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state.leftOrDeleted) {
        if (state.leftOrDeleted) onLeftOrDeleted()
    }
    LaunchedEffect(state.pendingInviteCode) {
        val code = state.pendingInviteCode
        if (code != null) {
            onShowInvite(state.circleId, state.circleName, code)
            viewModel.consumeInviteCode()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.circleName.ifBlank { stringResource(R.string.circle_detail_title) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.isOwner) {
                        IconButton(onClick = { showEditSheet = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.circle_detail_edit_circle_cd))
                        }
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
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.size(8.dp))
            CircleHeader(
                name = state.circleName,
                colorHex = state.colorHex,
                iconKey = state.iconKey,
                memberCount = state.members.size,
            )
            Spacer(Modifier.size(24.dp))

            // Vlasnik dobija dva entry-ja: običan poziv i poziv za dete (preset isChild=true
            // čim novi član prihvati invite). Članovi (non-owner) ne vide ova dugmad.
            if (state.isOwner) {
                Button(
                    onClick = { viewModel.generateInvite(forChild = false) },
                    enabled = !state.generatingInvite,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.circle_detail_invite_cta))
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = { viewModel.generateInvite(forChild = true) },
                    enabled = !state.generatingInvite,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.ChildCare, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.circle_detail_invite_child))
                }
            } else {
                Button(
                    onClick = { viewModel.generateInvite(forChild = false) },
                    enabled = !state.generatingInvite,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.circle_detail_invite_cta))
                }
            }

            Spacer(Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.map_members_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.members, key = { it.uid }) { m ->
                    MemberRow(
                        m = m,
                        canManage = state.isOwner && !m.isSelf,
                        onToggleChild = { makeChild ->
                            viewModel.toggleChildStatus(m.uid, makeChild)
                        },
                    )
                }
            }

            // Self je dete u OVOM krugu — sakrij "Izađi iz kruga", pokaži banner.
            val selfIsChildHere = state.members.firstOrNull { it.isSelf }?.isChild == true
            if (selfIsChildHere && !state.isOwner) {
                Spacer(Modifier.size(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChildCare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = stringResource(R.string.circle_detail_child_locked_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            if (state.isOwner) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !state.deleting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.circle_detail_delete_cta))
                }
            } else {
                val selfIsChildHere = state.members.firstOrNull { it.isSelf }?.isChild == true
                if (!selfIsChildHere) {
                    OutlinedButton(
                        onClick = { showLeaveConfirm = true },
                        enabled = !state.leaving,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.circle_detail_leave_cta))
                    }
                }
            }
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.circle_detail_leave_confirm_title)) },
            text = { Text(stringResource(R.string.circle_detail_leave_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    viewModel.leave()
                }) { Text(stringResource(R.string.circle_detail_leave_cta)) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.circle_detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.circle_detail_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) {
                    Text(
                        text = stringResource(R.string.circle_detail_delete_cta),
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

    if (showEditSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            sheetState = editSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            var saving by remember { mutableStateOf(false) }
            var duplicateError by remember { mutableStateOf(false) }
            EditCircleSheet(
                initialName = state.circleName,
                initialColor = state.colorHex,
                initialIcon = state.iconKey,
                saving = saving,
                duplicateError = duplicateError,
                onSave = { name, color, icon ->
                    saving = true
                    duplicateError = false
                    coroutineScope.launch {
                        val ok = viewModel.updateDetails(name, color, icon)
                        saving = false
                        if (ok) showEditSheet = false
                        else duplicateError = true
                    }
                },
                onCancel = { showEditSheet = false },
            )
        }
    }
}

@Composable
private fun CircleHeader(name: String, colorHex: String, iconKey: String, memberCount: Int) {
    val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CircleIconAssets.forKey(iconKey),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.size(16.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = pluralStringResource(R.plurals.circles_members_count, memberCount, memberCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemberRow(
    m: CircleDetailMember,
    canManage: Boolean = false,
    onToggleChild: (Boolean) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val initial = m.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = m.displayName.ifBlank {
                        if (m.isSelf) stringResource(R.string.member_label_you)
                        else stringResource(R.string.circle_detail_role_member)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (m.isChild) {
                    Spacer(Modifier.size(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.ChildCare,
                        contentDescription = stringResource(R.string.member_child_cd),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = when {
                    m.isOwner -> stringResource(R.string.circle_detail_role_owner)
                    m.isChild -> "Dete"
                    else -> stringResource(R.string.circle_detail_role_member)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (m.isChild) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canManage) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Opcije člana")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (m.isChild) "Ukloni oznaku deteta"
                                else "Označi kao dete",
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.ChildCare, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onToggleChild(!m.isChild)
                        },
                    )
                }
            }
        }
    }
}

// region @Preview

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Member row, owner")
@Composable
private fun MemberRowOwnerPreview() {
    org.krug.app.ui.theme.KrugTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MemberRow(
                m = CircleDetailMember(
                    uid = "x",
                    displayName = "Marko Marković",
                    isOwner = true,
                    isSelf = true,
                    isChild = false,
                ),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Member row, child")
@Composable
private fun MemberRowChildPreview() {
    org.krug.app.ui.theme.KrugTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MemberRow(
                m = CircleDetailMember(
                    uid = "y",
                    displayName = "Ana",
                    isOwner = false,
                    isSelf = false,
                    isChild = true,
                ),
                canManage = true,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Circle header")
@Composable
private fun CircleHeaderPreview() {
    org.krug.app.ui.theme.KrugTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CircleHeader(
                name = "Porodica",
                colorHex = "#4F46E5",
                iconKey = "family",
                memberCount = 4,
            )
        }
    }
}

// endregion
