package com.cpamp.mobile.ui.resources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.R
import com.cpamp.mobile.data.remote.model.AuthFileDto
import com.cpamp.mobile.data.resources.ProviderDraft
import com.cpamp.mobile.data.resources.ProviderRecord
import com.cpamp.mobile.data.resources.ProviderSection
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.ConnectionPill
import com.cpamp.mobile.ui.components.PageHeader

private sealed interface PendingResourceAction {
    data class DeleteProvider(val record: ProviderRecord) : PendingResourceAction
    data class SetAuthFileDisabled(val file: AuthFileDto, val disabled: Boolean) : PendingResourceAction
    data class DeleteAuthFile(val file: AuthFileDto) : PendingResourceAction
    data object OverwriteAuthFile : PendingResourceAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcesScreen(
    contentPadding: PaddingValues,
    viewModel: ResourcesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingAction by remember { mutableStateOf<PendingResourceAction?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importAuthFile)
    }

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 24.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PageHeader(
                    eyebrow = stringResource(R.string.nav_resources),
                    title = stringResource(R.string.resources_title),
                    subtitle = stringResource(R.string.resources_subtitle),
                    trailing = {
                        IconButton(onClick = viewModel::refresh, enabled = !state.loading && !state.mutating) {
                            if (state.loading || state.mutating) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                            }
                        }
                    },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.tab == ResourceTab.Providers,
                        onClick = { viewModel.selectTab(ResourceTab.Providers) },
                        label = { Text(stringResource(R.string.resource_providers)) },
                    )
                    FilterChip(
                        selected = state.tab == ResourceTab.AuthFiles,
                        onClick = { viewModel.selectTab(ResourceTab.AuthFiles) },
                        label = { Text(stringResource(R.string.resource_auth_files)) },
                    )
                }
            }
            if (state.error != null || state.message != null) {
                item { ResourceNotice(state, viewModel::clearNotice) }
            }
            if (state.loading && state.providers.isEmpty() && state.authFiles.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.tab == ResourceTab.Providers) {
                ProviderSection.entries.forEach { section ->
                    item(key = "header:${section.wireName}") {
                        SectionHeader(
                            title = section.title,
                            count = state.providers[section].orEmpty().size,
                            actionLabel = stringResource(R.string.add_provider),
                            onAction = { viewModel.openProviderEditor(section) },
                            enabled = !state.mutating,
                        )
                    }
                    val records = state.providers[section].orEmpty()
                    if (records.isEmpty()) {
                        item(key = "empty:${section.wireName}") {
                            EmptyResourceCard(stringResource(R.string.no_provider_configured, section.title))
                        }
                    } else {
                        items(records, key = ProviderRecord::identity) { record ->
                            ProviderCard(
                                record = record,
                                enabled = !state.mutating,
                                onEdit = { viewModel.openProviderEditor(section, record) },
                                onDelete = { pendingAction = PendingResourceAction.DeleteProvider(record) },
                            )
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = viewModel::newAuthFile,
                            enabled = !state.mutating,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text(stringResource(R.string.new_auth_file), modifier = Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/json")) },
                            enabled = !state.mutating,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                            Text(stringResource(R.string.import_json), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (state.authFiles.isEmpty()) {
                    item { EmptyResourceCard(stringResource(R.string.no_auth_files)) }
                } else {
                    items(state.authFiles, key = AuthFileDto::name) { file ->
                        AuthFileCard(
                            file = file,
                            enabled = !state.mutating,
                            onEdit = { viewModel.editAuthFile(file) },
                            onToggle = { disabled ->
                                pendingAction = PendingResourceAction.SetAuthFileDisabled(file, disabled)
                            },
                            onDelete = { pendingAction = PendingResourceAction.DeleteAuthFile(file) },
                        )
                    }
                }
            }
        }
    }

    state.providerEditor?.let { editor ->
        ProviderEditorSheet(
            editor = editor,
            saving = state.mutating,
            onDraftChange = viewModel::updateProviderDraft,
            onSave = viewModel::saveProvider,
            onDismiss = viewModel::closeProviderEditor,
        )
    }

    state.authFileEditor?.let { editor ->
        AuthFileEditorSheet(
            editor = editor,
            saving = state.mutating,
            onChange = viewModel::updateAuthFileEditor,
            onSave = {
                val sameNameExists = state.authFiles.any { it.name == editor.name }
                if (editor.originalName != null || sameNameExists) {
                    pendingAction = PendingResourceAction.OverwriteAuthFile
                } else {
                    viewModel.saveAuthFile()
                }
            },
            onDismiss = viewModel::closeAuthFileEditor,
        )
    }

    pendingAction?.let { action ->
        val title: String
        val message: String
        val destructive: Boolean
        when (action) {
            is PendingResourceAction.DeleteProvider -> {
                title = stringResource(R.string.delete_provider_title)
                message = stringResource(R.string.delete_provider_body, action.record.title)
                destructive = true
            }
            is PendingResourceAction.SetAuthFileDisabled -> {
                title = stringResource(
                    if (action.disabled) R.string.disable_auth_file_title else R.string.enable_auth_file_title,
                )
                message = stringResource(
                    if (action.disabled) R.string.disable_auth_file_body else R.string.enable_auth_file_body,
                    action.file.name,
                )
                destructive = action.disabled
            }
            is PendingResourceAction.DeleteAuthFile -> {
                title = stringResource(R.string.delete_auth_file_title)
                message = stringResource(R.string.delete_auth_file_body, action.file.name)
                destructive = true
            }
            PendingResourceAction.OverwriteAuthFile -> {
                title = stringResource(R.string.overwrite_auth_file_title)
                message = stringResource(
                    R.string.overwrite_auth_file_body,
                    state.authFileEditor?.name.orEmpty(),
                )
                destructive = true
            }
        }
        ConfirmationDialog(
            title = title,
            message = message,
            destructive = destructive,
            onDismiss = { pendingAction = null },
            onConfirm = {
                when (action) {
                    is PendingResourceAction.DeleteProvider -> viewModel.deleteProvider(action.record)
                    is PendingResourceAction.SetAuthFileDisabled -> {
                        viewModel.setAuthFileDisabled(action.file, action.disabled)
                    }
                    is PendingResourceAction.DeleteAuthFile -> viewModel.deleteAuthFile(action.file)
                    PendingResourceAction.OverwriteAuthFile -> viewModel.saveAuthFile()
                }
                pendingAction = null
            },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    actionLabel: String,
    onAction: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Surface(
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text("$count", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(onClick = onAction, enabled = enabled) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(actionLabel, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun ProviderCard(
    record: ProviderRecord,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    Icons.Outlined.Key,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (record.disabled) {
                        ConnectionPill(
                            label = stringResource(R.string.disabled),
                            secure = false,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                val details = listOfNotNull(
                    record.baseUrl.takeIf(String::isNotBlank),
                    record.prefix.takeIf(String::isNotBlank)?.let { stringResource(R.string.prefix_value, it) },
                    record.priority?.let { stringResource(R.string.priority_value, it) },
                ).joinToString(" · ")
                Text(
                    details.ifBlank { stringResource(R.string.provider_secret_configured) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit, enabled = enabled) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit))
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun AuthFileCard(
    file: AuthFileDto,
    enabled: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(file.provider.ifBlank { file.type }, file.status).filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ConnectionPill(
                    label = stringResource(if (file.disabled) R.string.disabled else R.string.enabled),
                    secure = !file.disabled && !file.unavailable,
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit, enabled = enabled) { Text(stringResource(R.string.edit_json)) }
                TextButton(onClick = { onToggle(!file.disabled) }, enabled = enabled) {
                    Text(stringResource(if (file.disabled) R.string.enable else R.string.disable))
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditorSheet(
    editor: ProviderEditorState,
    saving: Boolean,
    onDraftChange: (ProviderDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var keyVisible by rememberSaveable(editor.section.wireName, editor.original?.identity) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    stringResource(
                        if (editor.original == null) R.string.add_provider_title else R.string.edit_provider_title,
                        editor.section.title,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.provider_editor_help),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (editor.section.openAiCompatible) {
                item {
                    OutlinedTextField(
                        value = editor.draft.name,
                        onValueChange = { onDraftChange(editor.draft.copy(name = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.provider_name)) },
                        singleLine = true,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = editor.draft.apiKey,
                    onValueChange = { onDraftChange(editor.draft.copy(apiKey = it.take(4096))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.provider_api_key)) },
                    supportingText = {
                        Text(
                            stringResource(
                                if (editor.original == null) R.string.provider_api_key_required else R.string.provider_api_key_keep,
                            ),
                        )
                    },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(if (keyVisible) R.string.hide_key else R.string.show_key),
                            )
                        }
                    },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = editor.draft.baseUrl,
                    onValueChange = { onDraftChange(editor.draft.copy(baseUrl = it.take(2048))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.provider_base_url)) },
                    supportingText = { Text(stringResource(R.string.provider_base_url_help)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = editor.draft.prefix,
                    onValueChange = { onDraftChange(editor.draft.copy(prefix = it.take(128))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.provider_prefix)) },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = editor.draft.priority?.toString().orEmpty(),
                    onValueChange = { raw ->
                        onDraftChange(editor.draft.copy(priority = raw.filter(Char::isDigit).take(9).toIntOrNull()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.provider_priority)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
            if (editor.section.openAiCompatible) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.provider_disabled), fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.provider_disabled_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = editor.draft.disabled,
                            onCheckedChange = { onDraftChange(editor.draft.copy(disabled = it)) },
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(onClick = onSave, enabled = !saving, modifier = Modifier.weight(1f)) {
                        if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.save), modifier = Modifier.padding(start = if (saving) 8.dp else 0.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthFileEditorSheet(
    editor: AuthFileEditorState,
    saving: Boolean,
    onChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(if (editor.originalName == null) R.string.new_auth_file else R.string.edit_auth_file),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (editor.loading) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { onChange(it, editor.content) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.file_name)) },
                    supportingText = { Text(stringResource(R.string.auth_file_name_help)) },
                    singleLine = true,
                    enabled = editor.originalName == null,
                )
                OutlinedTextField(
                    value = editor.content,
                    onValueChange = { onChange(editor.name, it) },
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    label = { Text(stringResource(R.string.json_content)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    supportingText = { Text(stringResource(R.string.auth_file_size, editor.content.length)) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(onClick = onSave, enabled = !saving, modifier = Modifier.weight(1f)) {
                        if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.save), modifier = Modifier.padding(start = if (saving) 8.dp else 0.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceNotice(state: ResourcesUiState, onDismiss: () -> Unit) {
    val isError = state.error != null
    val text = state.error?.resourceMessage() ?: state.message?.resourceMessage().orEmpty()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

@Composable
private fun EmptyResourceCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(if (destructive) R.string.confirm_change else R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun String.resourceMessage(): String = when (this) {
    "PROVIDER_CHANGED" -> stringResource(R.string.error_provider_changed)
    "PROVIDER_NAME_REQUIRED" -> stringResource(R.string.error_provider_name)
    "PROVIDER_URL_REQUIRED" -> stringResource(R.string.error_provider_url)
    "PROVIDER_KEY_REQUIRED" -> stringResource(R.string.error_provider_key)
    "AUTH_FILE_TOO_LARGE" -> stringResource(R.string.error_auth_file_too_large)
    "AUTH_FILE_NAME_INVALID" -> stringResource(R.string.error_auth_file_name)
    "AUTH_FILE_JSON_INVALID" -> stringResource(R.string.error_auth_file_json)
    "AUTH_FILE_READ_FAILED" -> stringResource(R.string.error_auth_file_read)
    "PROVIDER_SAVED" -> stringResource(R.string.provider_saved)
    "PROVIDER_DELETED" -> stringResource(R.string.provider_deleted)
    "AUTH_FILE_UPDATED" -> stringResource(R.string.auth_file_updated)
    "AUTH_FILE_DELETED" -> stringResource(R.string.auth_file_deleted)
    "AUTH_FILE_SAVED" -> stringResource(R.string.auth_file_saved)
    else -> stringResource(R.string.resource_request_failed)
}

