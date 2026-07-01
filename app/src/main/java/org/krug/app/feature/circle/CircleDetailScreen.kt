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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.krug.app.R
import org.krug.app.core.util.rejectHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleDetailScreen(
    onBack: () -> Unit,
    onLeftOrDeleted: () -> Unit,
    onShowInvite: (circleId: String, circleName: String, code: String) -> Unit,
    viewModel: CircleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    // Uid člana koji se trenutno preimenjuje (dijalog vidljiv). null = zatvoren.
    var renamingUid by remember { mutableStateOf<String?>(null) }
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
                    ButtonLeadingIconOrSpinner(
                        loading = state.generatingInvite,
                        icon = Icons.Outlined.PersonAdd,
                        spinnerColor = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.circle_detail_invite_cta))
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = { viewModel.generateInvite(forChild = true) },
                    enabled = !state.generatingInvite,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ButtonLeadingIconOrSpinner(
                        loading = state.generatingInvite,
                        icon = Icons.Outlined.ChildCare,
                        spinnerColor = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.circle_detail_invite_child))
                }
            } else {
                Button(
                    onClick = { viewModel.generateInvite(forChild = false) },
                    enabled = !state.generatingInvite,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ButtonLeadingIconOrSpinner(
                        loading = state.generatingInvite,
                        icon = Icons.Outlined.PersonAdd,
                        spinnerColor = MaterialTheme.colorScheme.onPrimary,
                    )
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
                        onRenameClicked = { renamingUid = m.uid },
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
                    ButtonLeadingIconOrSpinner(
                        loading = state.deleting,
                        icon = Icons.Outlined.Delete,
                        spinnerColor = MaterialTheme.colorScheme.error,
                    )
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
                        ButtonLeadingIconOrSpinner(
                            loading = state.leaving,
                            icon = Icons.AutoMirrored.Outlined.ExitToApp,
                            spinnerColor = MaterialTheme.colorScheme.error,
                        )
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
                    // Napustiti krug je destruktivno — jak haptik potvrđuje akciju
                    // (isti pattern kao SOS confirm i account delete).
                    view.rejectHaptic()
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
                    view.rejectHaptic()
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

    // Rename dialog — renderuje se samo kad je renamingUid postavljen. State (text field)
    // je unutar RenameMemberDialog composable-a, re-init-uje se za svaki different member.
    renamingUid?.let { uid ->
        val member = state.members.firstOrNull { it.uid == uid }
        if (member != null) {
            RenameMemberDialog(
                member = member,
                onDismiss = { renamingUid = null },
                onSave = { newName ->
                    viewModel.setMemberNickname(uid, newName)
                    renamingUid = null
                },
            )
        }
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

/**
 * Leading slot za button-e koji imaju async loading state — prikazuje inline
 * CircularProgressIndicator umesto ikone dok je `loading=true`. Drži se istih
 * dimenzija (18dp) pa Layout ne preskače.
 */
@Composable
private fun ButtonLeadingIconOrSpinner(
    loading: Boolean,
    icon: ImageVector,
    spinnerColor: Color,
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = spinnerColor,
        )
    } else {
        Icon(icon, contentDescription = null)
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
    onRenameClicked: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Prikazano ime: nickname (ako je postavljen u ovom krugu) > real display name > fallback.
    val displayed = m.nickname?.takeIf { it.isNotBlank() }
        ?: m.displayName.ifBlank {
            if (m.isSelf) stringResource(R.string.member_label_you)
            else stringResource(R.string.circle_detail_role_member)
        }
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
            // Inicijal iz PRIKAZANOG imena (nickname prvo) — konzistentno sa vizuelnim
            // identity-jem koji vidi user u istom redu.
            val initial = displayed.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
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
                    text = displayed,
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
            // Ako je nickname postavljen, pokaži real name kao suptilan pod-tekst (tako
            // owner uvek vidi ko je pravi user, i member nema iznenađenja "ko je Klinac?").
            // Ako nema nickname-a, prikaži rol (owner/child/member) kao pre.
            val subLine = when {
                m.nickname != null && m.displayName.isNotBlank() ->
                    stringResource(R.string.member_real_name_label, m.displayName)
                m.isOwner -> stringResource(R.string.circle_detail_role_owner)
                m.isChild -> stringResource(R.string.circle_detail_role_child)
                else -> stringResource(R.string.circle_detail_role_member)
            }
            Text(
                text = subLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (m.isChild) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canManage) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.member_options_cd),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (m.isChild) R.string.member_menu_unmark_child
                                    else R.string.member_menu_mark_child,
                                ),
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.member_menu_rename)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onRenameClicked()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameMemberDialog(
    member: CircleDetailMember,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var text by remember(member.uid) { mutableStateOf(member.nickname.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.member_rename_title)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 24) text = it },
                    placeholder = { Text(stringResource(R.string.member_rename_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.member_rename_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = text.trim()
                onSave(trimmed.ifBlank { null })
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            // Ako je nickname već postavljen, dugme za brzo brisanje. Bez ovog, user bi
            // morao da ručno obriše slova pa da klikne Save — više klikova nego treba.
            if (member.nickname != null) {
                TextButton(onClick = { onSave(null) }) {
                    Text(
                        text = stringResource(R.string.member_rename_clear_cta),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
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
