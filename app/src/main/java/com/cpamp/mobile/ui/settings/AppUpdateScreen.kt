package com.cpamp.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpamp.mobile.BuildConfig
import com.cpamp.mobile.R
import com.cpamp.mobile.data.update.UpdateStatus
import com.cpamp.mobile.data.update.displayBody
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.PageHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun AppUpdateScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refreshDownloadStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.status) {
        while (isActive && state.status == UpdateStatus.Downloading) {
            delay(750)
            viewModel.refreshDownloadStatus()
        }
    }

    AppBackground {
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxHeight()
                .widthIn(max = 900.dp).fillMaxWidth(),
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
                    eyebrow = stringResource(R.string.settings_title),
                    title = stringResource(R.string.about_updates),
                    subtitle = stringResource(R.string.update_page_subtitle),
                    leading = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                )
            }
            item {
                SettingsCard(stringResource(R.string.update_version_section)) {
                    Text(
                        stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.release?.let { release ->
                        Text(
                            stringResource(R.string.latest_version, release.tagName.removePrefix("v")),
                            fontWeight = FontWeight.SemiBold,
                        )
                        release.publishedAt?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        updateStatusText(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = updateStatusColor(state),
                    )
                    if (state.status in setOf(UpdateStatus.Downloading, UpdateStatus.Verifying)) {
                        state.progressPercent?.let { progress ->
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    when (state.status) {
                        UpdateStatus.Available -> Button(
                            onClick = viewModel::downloadUpdate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Text(
                                stringResource(R.string.download_update),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        UpdateStatus.ReadyToInstall -> Button(
                            onClick = { state.installUri?.let(context::installUpdate) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.InstallMobile, contentDescription = null)
                            Text(
                                stringResource(R.string.install_update),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        else -> Button(
                            onClick = viewModel::checkForUpdates,
                            enabled = state.status !in setOf(
                                UpdateStatus.Checking,
                                UpdateStatus.Downloading,
                                UpdateStatus.Verifying,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.status == UpdateStatus.Checking) {
                                CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                            }
                            Text(
                                stringResource(R.string.check_for_updates),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
            state.release?.displayBody(language)?.let { body ->
                item {
                    SettingsCard(stringResource(R.string.update_notes_section)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.padding(11.dp),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                state.release?.let { release ->
                                    Text(
                                        stringResource(
                                            R.string.update_details_title,
                                            release.tagName.removePrefix("v"),
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        Text(
                            body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                SettingsCard(stringResource(R.string.update_security_section)) {
                    Text(
                        stringResource(R.string.update_security_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
