package com.cpamp.mobile.ui.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.R
import com.cpamp.mobile.ui.components.AppBackground
import com.cpamp.mobile.ui.components.AppCard
import com.cpamp.mobile.ui.components.PageHeader

const val OPEN_SOURCE_LICENSES_ROUTE = "open-source-licenses"

@Composable
fun OpenSourceLicensesScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    AppBackground {
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxHeight()
                .widthIn(max = 900.dp).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding() + 18.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PageHeader(
                    eyebrow = stringResource(R.string.about_updates),
                    title = stringResource(R.string.open_source_licenses),
                    subtitle = stringResource(R.string.open_source_licenses_subtitle),
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
                ExpandableLicenseCard(
                    title = "CPAMP Mobile",
                    owner = stringResource(R.string.license_cpamp_owner),
                    license = "MIT License",
                    description = stringResource(R.string.license_cpamp_description),
                    licenseResource = R.raw.cpamp_mobile_license,
                )
            }
            item {
                LicenseGroupCard(
                    title = stringResource(R.string.license_runtime_dependencies),
                    subtitle = stringResource(R.string.license_runtime_dependencies_summary),
                    items = runtimeLicenseItems(),
                )
            }
            item {
                LicenseGroupCard(
                    title = stringResource(R.string.license_development_dependencies),
                    subtitle = stringResource(R.string.license_development_dependencies_summary),
                    items = developmentLicenseItems(),
                )
            }
            item {
                ExpandableLicenseCard(
                    title = "CPA-Manager-Plus",
                    owner = "Seakee",
                    license = "MIT License",
                    description = stringResource(R.string.license_upstream_description),
                    licenseResource = R.raw.cpa_manager_plus_license,
                )
            }
            item {
                LicenseGroupCard(
                    title = stringResource(R.string.license_provider_marks),
                    subtitle = stringResource(R.string.license_provider_marks_summary),
                    items = providerMarkItems(),
                )
            }
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)) {
                    Text(
                        stringResource(R.string.license_distribution_notice),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandableLicenseCard(
    title: String,
    owner: String,
    license: String,
    description: String,
    @RawRes licenseResource: Int,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val licenseText = remember(context, licenseResource) {
        context.resources.openRawResource(licenseResource).bufferedReader().use { it.readText() }
    }
    AppCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LicenseItemHeader(title = title, owner = owner, license = license)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
                Text(
                    stringResource(
                        if (expanded) R.string.license_hide_full_text else R.string.license_show_full_text,
                    ),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (expanded) {
                Text(
                    licenseText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LicenseGroupCard(
    title: String,
    subtitle: String,
    items: List<LicenseItem>,
) {
    AppCard {
        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items.forEachIndexed { index, item ->
                if (index > 0) SettingsDivider()
                LicenseItemRow(item)
            }
        }
    }
}

@Composable
private fun LicenseItemRow(item: LicenseItem) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        LicenseItemHeader(item.name, item.owner, item.license)
        item.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LicenseItemHeader(title: String, owner: String, license: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(owner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            license,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class LicenseItem(
    val name: String,
    val owner: String,
    val license: String,
    val note: String? = null,
)

@Composable
private fun runtimeLicenseItems() = listOf(
    LicenseItem("AndroidX, Jetpack Compose, Material 3", "Android Open Source Project", "Apache-2.0"),
    LicenseItem("Kotlin, coroutines, serialization", "JetBrains", "Apache-2.0"),
    LicenseItem("Dagger and Hilt", "Google", "Apache-2.0"),
    LicenseItem("Retrofit, OkHttp", "Square", "Apache-2.0"),
    LicenseItem("Backdrop / AndroidLiquidGlass", "Kyant", "Apache-2.0"),
)

@Composable
private fun developmentLicenseItems() = listOf(
    LicenseItem("AndroidX Test and Espresso", "Android Open Source Project", "Apache-2.0"),
    LicenseItem("MockWebServer", "Square", "Apache-2.0"),
    LicenseItem("JUnit 4", "JUnit contributors", "EPL-1.0"),
)

@Composable
private fun providerMarkItems() = listOf(
    LicenseItem("Simple Icons", "Simple Icons contributors", "CC0-1.0", stringResource(R.string.license_simple_icons_note)),
    LicenseItem("OpenAI, Anthropic, Gemini, DeepSeek, Qwen, xAI", stringResource(R.string.license_mark_owners), stringResource(R.string.license_trademark_terms)),
)