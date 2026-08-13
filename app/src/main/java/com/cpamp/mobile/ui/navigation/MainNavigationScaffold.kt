package com.cpamp.mobile.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.ui.components.BrandMark
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.Shadow

@Composable
fun MainNavigationScaffold(
    currentDestination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        if (expanded) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
            ) { padding ->
                Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        BrandMark(modifier = Modifier.padding(vertical = 20.dp))
                        AppDestination.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = destination == currentDestination,
                                onClick = { onNavigate(destination) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) { content(PaddingValues()) }
                }
            }
        } else {
            val contentBackdrop = rememberLayerBackdrop()
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    FloatingNavigationBar(
                        currentDestination = currentDestination,
                        onNavigate = onNavigate,
                        backdrop = contentBackdrop,
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
                    content(padding)
                }
            }
        }
    }
}

@Composable
private fun FloatingNavigationBar(
    currentDestination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    backdrop: Backdrop,
) {
    val navigationBarHeight = 58.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.2f)
    val navigationBarShape = MaterialTheme.shapes.extraLarge
    val glassSurface = MaterialTheme.colorScheme.surface.copy(
        alpha = if (isRenderEffectSupported()) 0.42f else 0.92f,
    )
    Box(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 6.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { navigationBarShape },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        lens(
                            refractionHeight = 10.dp.toPx(),
                            refractionAmount = 16.dp.toPx(),
                            depthEffect = true,
                        )
                    },
                    highlight = { Highlight.Plain.copy(alpha = 0.78f) },
                    shadow = {
                        Shadow(
                            radius = 10.dp,
                            color = Color.Black.copy(alpha = 0.12f),
                        )
                    },
                    onDrawSurface = { drawRect(glassSurface) },
                )
                .height(navigationBarHeight)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            val destinations = AppDestination.entries
            val itemWidth = maxWidth / destinations.size.toFloat()
            val selectedIndex = destinations.indexOf(currentDestination).coerceAtLeast(0)
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "navigationIndicatorOffset",
            )
            Surface(
                modifier = Modifier
                    .offset(x = indicatorOffset + 2.dp)
                    .width(itemWidth - 4.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.medium,
            ) {}
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    FloatingNavigationItem(
                        destination = destination,
                        selected = destination == currentDestination,
                        onClick = { onNavigate(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavigationItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 180),
        label = "${destination.route}NavigationItemColor",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight().padding(horizontal = 2.dp),
        color = Color.Transparent,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(destination.label),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
