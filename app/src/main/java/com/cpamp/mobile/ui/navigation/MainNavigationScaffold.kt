package com.cpamp.mobile.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.ui.components.BrandMark
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
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
    val renderEffectsSupported = isRenderEffectSupported()
    val navigationBarHeight = 58.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.2f)
    val navigationBarShape = RoundedCornerShape(percent = 50)
    val glassSurface = Brush.verticalGradient(
        colors = if (renderEffectsSupported) {
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
            )
        } else {
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            )
        },
    )
    Box(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = 40.dp, end = 40.dp, top = 6.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { navigationBarShape },
                    effects = {
                        blur(6.dp.toPx())
                    },
                    highlight = { Highlight.Plain.copy(alpha = 0.52f) },
                    shadow = {
                        Shadow(
                            radius = 12.dp,
                            color = Color.Black.copy(alpha = 0.10f),
                        )
                    },
                    onDrawSurface = { drawRect(glassSurface) },
                )
                .height(navigationBarHeight)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            val destinations = AppDestination.entries
            val interactionSources = remember {
                destinations.associateWith { MutableInteractionSource() }
            }
            val pressedStates = destinations.map { destination ->
                interactionSources.getValue(destination).collectIsPressedAsState().value
            }
            val itemWidth = maxWidth / destinations.size.toFloat()
            val selectedIndex = destinations.indexOf(currentDestination).coerceAtLeast(0)
            val pressedIndex = pressedStates.indexOfFirst { it }
            val indicatorIndex = pressedIndex.takeIf { it >= 0 } ?: selectedIndex
            val indicatorPressed = pressedIndex >= 0
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * indicatorIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = 380f,
                ),
                label = "navigationIndicatorOffset",
            )
            val indicatorStretch = remember { Animatable(0f) }
            val pressScale by animateFloatAsState(
                targetValue = if (indicatorPressed) 0.96f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "navigationIndicatorPressScale",
            )
            val indicatorSurface = Brush.verticalGradient(
                colors = if (renderEffectsSupported) {
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                    )
                } else {
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                    )
                },
            )
            val indicatorShape = RoundedCornerShape(percent = 50)
            LaunchedEffect(selectedIndex) {
                indicatorStretch.snapTo(0.48f)
                indicatorStretch.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.74f,
                        stiffness = 300f,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = indicatorOffset - 4.dp)
                    .width(itemWidth + 8.dp)
                    .height(navigationBarHeight - 18.dp)
                    .graphicsLayer {
                        scaleX = 1f + indicatorStretch.value * 0.06f
                        scaleY = pressScale - indicatorStretch.value * 0.02f
                    }
                    .background(indicatorSurface, indicatorShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        shape = indicatorShape,
                    ),
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    FloatingNavigationItem(
                        destination = destination,
                        selected = destination == currentDestination,
                        pressed = pressedStates[destinations.indexOf(destination)],
                        onClick = { onNavigate(destination) },
                        interactionSource = interactionSources.getValue(destination),
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
    pressed: Boolean,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
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
    val contentScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.94f
            selected -> 1.02f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "${destination.route}NavigationItemScale",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 3.dp)
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                },
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
