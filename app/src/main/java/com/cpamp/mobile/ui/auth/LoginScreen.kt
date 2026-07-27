package com.cpamp.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.domain.model.ServerProfile
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.BrandMark
import com.cpamp.mobile.ui.components.ConnectionPill

@Composable
fun SessionLoadingScreen() {
    AppBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark()
            CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            Text(
                text = stringResource(R.string.restoring_session),
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    state: SessionUiState,
    hideAddresses: Boolean,
    onLogin: (String, String, String, Boolean) -> Unit,
    onConnectSaved: (String) -> Unit,
    onDeleteSaved: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var adminKey by remember { mutableStateOf("") }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var cleartextConfirmation by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ServerProfile?>(null) }

    fun submit(allowCleartext: Boolean) {
        onDismissError()
        onLogin(name, address, adminKey, allowCleartext)
    }

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 42.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BrandMark()
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.login_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.new_connection), fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.server_name)) },
                            placeholder = { Text(stringResource(R.string.server_name_hint)) },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.server_address)) },
                            placeholder = { Text("https://192.168.1.10:18317") },
                            supportingText = { Text(stringResource(R.string.address_help)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = adminKey,
                            onValueChange = { adminKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.admin_key)) },
                            placeholder = { Text("cpamp_••••••••") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = stringResource(
                                            if (keyVisible) R.string.hide_key else R.string.show_key,
                                        ),
                                    )
                                }
                            },
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                        )
                        state.error?.let { error ->
                            Text(
                                text = stringResource(error.stringResource()),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Button(
                            onClick = {
                                if (address.trim().startsWith("http://", ignoreCase = true)) {
                                    cleartextConfirmation = true
                                } else {
                                    submit(false)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.submitting && address.isNotBlank() && adminKey.isNotBlank(),
                        ) {
                            if (state.submitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                            }
                            Text(stringResource(R.string.connect), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            if (state.profiles.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.saved_servers), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.saved_servers_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.profiles, key = ServerProfile::id) { profile ->
                    SavedProfileCard(
                        profile = profile,
                        hideAddress = hideAddresses,
                        busy = state.submitting,
                        onConnect = { onConnectSaved(profile.id) },
                        onDelete = { pendingDelete = profile },
                    )
                }
            }
        }
    }

    if (cleartextConfirmation) {
        AlertDialog(
            onDismissRequest = { cleartextConfirmation = false },
            icon = { Icon(Icons.Outlined.LockOpen, contentDescription = null) },
            title = { Text(stringResource(R.string.http_warning_title)) },
            text = { Text(stringResource(R.string.http_warning_body)) },
            confirmButton = {
                Button(onClick = { cleartextConfirmation = false; submit(true) }) {
                    Text(stringResource(R.string.continue_insecurely))
                }
            },
            dismissButton = {
                TextButton(onClick = { cleartextConfirmation = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_server_title)) },
            text = { Text(stringResource(R.string.delete_server_body, profile.name)) },
            confirmButton = {
                Button(onClick = { onDeleteSaved(profile.id); pendingDelete = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SavedProfileCard(
    profile: ServerProfile,
    hideAddress: Boolean,
    busy: Boolean,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(profile.name, fontWeight = FontWeight.SemiBold)
                if (!hideAddress) {
                    Text(
                        profile.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ConnectionPill(
                    label = stringResource(if (profile.usesCleartext) R.string.http_connection else R.string.https_connection),
                    secure = !profile.usesCleartext,
                )
            }
            OutlinedButton(onClick = onConnect, enabled = !busy) { Text(stringResource(R.string.connect)) }
            IconButton(onClick = onDelete, enabled = !busy) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.delete))
            }
        }
        HorizontalDivider()
    }
}

private fun AuthUiError.stringResource(): Int = when (this) {
    AuthUiError.InvalidAddress -> R.string.error_invalid_address
    AuthUiError.MissingKey -> R.string.error_missing_key
    AuthUiError.Unauthorized -> R.string.error_unauthorized
    AuthUiError.LightMode -> R.string.error_light_mode
    AuthUiError.NotManager -> R.string.error_not_manager
    AuthUiError.Redirect -> R.string.error_redirect
    AuthUiError.Tls -> R.string.error_tls
    AuthUiError.Timeout -> R.string.error_timeout
    AuthUiError.Network -> R.string.error_network
    AuthUiError.Server -> R.string.error_server
    AuthUiError.SavedKeyUnavailable -> R.string.error_saved_key
    AuthUiError.Unknown -> R.string.error_unknown
}

