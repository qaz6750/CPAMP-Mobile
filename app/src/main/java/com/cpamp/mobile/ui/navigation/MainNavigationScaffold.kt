package com.cpamp.mobile.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cpamp.mobile.ui.components.BrandMark
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val density = LocalDensity.current
    val navigationBarHeight = 58.dp * density.fontScale.coerceIn(1f, 1.2f)
    val capsuleShape = RoundedCornerShape(percent = 50)
    val destinations = AppDestination.entries
    val selectedIndex = destinations.indexOf(currentDestination).coerceAtLeast(0)
    val interactionSources = remember {
        destinations.associateWith { MutableInteractionSource() }
    }
    val pressedStates = destinations.map { destination ->
        interactionSources.getValue(destination).collectIsPressedAsState().value
    }
    val pressedIndex = pressedStates.indexOfFirst { it }
    var isDragging by remember { mutableStateOf(false) }
    var interactionPosition by remember { mutableStateOf<Offset?>(null) }
    val indicatorPosition = remember { Animatable(selectedIndex.toFloat()) }
    val navigationScope = rememberCoroutineScope()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressedIndex >= 0 || isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
        label = "navigationGlassPressProgress",
    )
    val glassSurface = Brush.verticalGradient(
        colors = if (renderEffectsSupported) {
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.36f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.14f),
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
                    shape = { capsuleShape },
                    effects = {
                        if (renderEffectsSupported) {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(
                                refractionHeight = 20.dp.toPx(),
                                refractionAmount = 24.dp.toPx(),
                                depthEffect = true,
                            )
                        }
                    },
                    highlight = {
                        Highlight.Plain.copy(alpha = 0.56f + pressProgress * 0.18f)
                    },
                    shadow = {
                        Shadow(
                            radius = 14.dp,
                            color = Color.Black.copy(alpha = 0.10f + pressProgress * 0.04f),
                        )
                    },
                    layerBlock = {
                        val scale = 1f + pressProgress * 6.dp.toPx() / size.width
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(glassSurface) },
                )
                .height(navigationBarHeight)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            val itemWidth = maxWidth / destinations.size.toFloat()
            val itemWidthPx = constraints.maxWidth.toFloat() / destinations.size
            val indicatorInsetPx = with(density) { 4.dp.toPx() }
            val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
            val tabsBackdrop = rememberLayerBackdrop()
            val indicatorBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
            val indicatorSurface = Brush.verticalGradient(
                colors = if (renderEffectsSupported) {
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
                    )
                } else {
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                    )
                },
            )
            LaunchedEffect(selectedIndex, pressedIndex, isDragging) {
                if (!isDragging) {
                    val targetIndex = pressedIndex.takeIf { it >= 0 } ?: selectedIndex
                    indicatorPosition.animateTo(
                        targetValue = targetIndex.toFloat(),
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = 360f,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(tabsBackdrop)
                    .pointerInput(itemWidthPx, isLtr) {
                        detectHorizontalDragGestures(
                            onDragStart = { position ->
                                isDragging = true
                                interactionPosition = position
                            },
                            onDragEnd = {
                                val targetIndex = indicatorPosition.value.roundToInt()
                                    .coerceIn(destinations.indices)
                                onNavigate(destinations[targetIndex])
                                navigationScope.launch {
                                    indicatorPosition.animateTo(
                                        targetValue = targetIndex.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = 0.70f,
                                            stiffness = 320f,
                                        ),
                                    )
                                    isDragging = false
                                    interactionPosition = null
                                }
                            },
                            onDragCancel = {
                                navigationScope.launch {
                                    indicatorPosition.animateTo(
                                        targetValue = selectedIndex.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = 0.74f,
                                            stiffness = 340f,
                                        ),
                                    )
                                    isDragging = false
                                    interactionPosition = null
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                interactionPosition = change.position
                                val direction = if (isLtr) 1f else -1f
                                navigationScope.launch {
                                    indicatorPosition.snapTo(
                                        (indicatorPosition.value + dragAmount / itemWidthPx * direction)
                                            .coerceIn(0f, destinations.lastIndex.toFloat()),
                                    )
                                }
                            },
                        )
                    },
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
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(itemWidth + 8.dp)
                    .height(navigationBarHeight - 18.dp)
                    .graphicsLayer {
                        val visualPosition = if (isLtr) {
                            indicatorPosition.value
                        } else {
                            destinations.lastIndex - indicatorPosition.value
                        }
                        translationX = visualPosition * itemWidthPx - indicatorInsetPx
                    }
                    .drawBackdrop(
                        backdrop = indicatorBackdrop,
                        shape = { capsuleShape },
                        effects = {
                            if (renderEffectsSupported) {
                                vibrancy()
                                lens(
                                    refractionHeight = (8.dp + 6.dp * pressProgress).toPx(),
                                    refractionAmount = (10.dp + 8.dp * pressProgress).toPx(),
                                    depthEffect = true,
                                    chromaticAberration = true,
                                )
                            }
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = 0.58f + pressProgress * 0.30f)
                        },
                        shadow = {
                            Shadow(
                                radius = 10.dp,
                                color = Color.Black.copy(alpha = 0.10f + pressProgress * 0.08f),
                            )
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp,
                                alpha = 0.34f + pressProgress * 0.36f,
                            )
                        },
                        layerBlock = {
                            val velocity = (indicatorPosition.velocity / 12f).coerceIn(-0.18f, 0.18f)
                            scaleX = 1f + pressProgress * 0.10f + abs(velocity) * 0.12f
                            scaleY = 1f + pressProgress * 0.05f - abs(velocity) * 0.04f
                        },
                        onDrawSurface = {
                            drawRect(indicatorSurface)
                            val visualPosition = if (isLtr) {
                                indicatorPosition.value
                            } else {
                                destinations.lastIndex - indicatorPosition.value
                            }
                            val lensLeft = visualPosition * itemWidthPx - indicatorInsetPx
                            val highlightCenter = interactionPosition?.let { position ->
                                Offset(
                                    x = (position.x - lensLeft).coerceIn(0f, size.width),
                                    y = position.y.coerceIn(0f, size.height),
                                )
                            } ?: center
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.16f * pressProgress),
                                        Color.Transparent,
                                    ),
                                    center = highlightCenter,
                                    radius = size.maxDimension,
                                ),
                            )
                        },
                    ),
            )
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
