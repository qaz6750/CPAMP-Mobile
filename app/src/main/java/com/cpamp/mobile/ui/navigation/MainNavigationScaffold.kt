package com.cpamp.mobile.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.ui.components.BrandMark

@Composable
fun MainNavigationScaffold(
    currentDestination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        if (expanded) {
            NavigationRail {
                BrandMark(modifier = Modifier.padding(vertical = 20.dp))
                AppDestination.entries.forEach { destination ->
                    NavigationRailItem(
                        selected = destination == currentDestination,
                        onClick = { onNavigate(destination) },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.label),
                            )
                        },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
            Box(modifier = Modifier.padding(start = 80.dp)) { content(PaddingValues()) }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar {
                        AppDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = destination == currentDestination,
                                onClick = { onNavigate(destination) },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = stringResource(destination.label),
                                    )
                                },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }
                },
            ) { padding -> content(padding) }
        }
    }
}

