@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.valid7996.narcicgeteway.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.valid7996.narcicgeteway.domain.model.NavBarStyle
import dev.valid7996.narcicgeteway.ui.theme.Personalization
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun NrgNavBar(
    style: NavBarStyle,
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    personalization: Personalization = Personalization.Default,
) {
    // Hidden destinations stay in the enum and out of the bar; NavConfig brings them back.
    val destinations = TopDestination.entries.filter { destination ->
        NavConfig.SHOW_SERVERS_TAB || destination != TopDestination.Servers
    }
    when (style) {
        NavBarStyle.IosInteractive, NavBarStyle.Minimal, NavBarStyle.FloatingPill ->
            IosInteractiveTabBar(destinations, current, onSelect, personalization)
        NavBarStyle.CompactDock -> FloatingPillBar(destinations, current, onSelect, compact = true)
        NavBarStyle.ExpressivePill -> ExpressivePillBar(destinations, current, onSelect)
        NavBarStyle.FullBar -> MaterialBar(destinations, current, onSelect, underline = false)
        NavBarStyle.Underline -> MaterialBar(destinations, current, onSelect, underline = true)
    }
}

/**
 * Interactive iOS 26 inspired floating dock with fluid sliding capsule indicator,
 * dual-tone glassmorphism highlight rim, tactile spring feedback, and full pointer
 * capture to completely eliminate click bleed-through to underlying cards.
 */
@Composable
private fun IosInteractiveTabBar(
    destinations: List<TopDestination>,
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    personalization: Personalization,
) {
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Theme adaptive active accent color
    val activeAccent = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary
        personalization.serverActiveColor != null -> personalization.serverActiveColor
        personalization.themeProfileId == "azkaban" -> Color(0xFF38BDF8)
        personalization.themeProfileId == "noir" -> Color(0xFFE4E4E7)
        personalization.themeProfileId == "blossom" -> Color(0xFFF472B6)
        else -> MaterialTheme.colorScheme.primary
    }

    // Theme adaptive outer dock background & frosted glass rim highlight
    val dockBg = when {
        personalization.amoledBlack -> Color(0xF204060A)
        personalization.themeProfileId == "azkaban" -> Color(0xEE091322)
        personalization.themeProfileId == "noir" -> Color(0xEE121214)
        personalization.themeProfileId == "blossom" -> Color(0xEE1A0A14)
        isDark -> Color(0xEA0D1624)
        else -> Color(0xF2F6F8FC)
    }

    val dockBorderBrush = Brush.verticalGradient(
        listOf(
            when {
                personalization.themeProfileId == "azkaban" -> Color(0x4038BDF8)
                personalization.themeProfileId == "blossom" -> Color(0x40F472B6)
                isDark -> Color(0x35FFFFFF)
                else -> Color(0x66FFFFFF)
            },
            when {
                isDark -> Color(0x0CFFFFFF)
                else -> Color(0x18000000)
            },
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 10.dp)
            .zIndex(100f),
        contentAlignment = Alignment.Center,
    ) {
        // Outer Pill Dock
        Box(
            modifier = Modifier
                .widthIn(max = 350.dp)
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = if (isDark) 16.dp else 10.dp,
                    shape = CircleShape,
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.12f),
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.20f),
                )
                .clip(CircleShape)
                .background(dockBg)
                .border(
                    width = 1.2.dp,
                    brush = dockBorderBrush,
                    shape = CircleShape,
                )
                // Consume all clicks inside the dock so NO click passes through to underlying cards!
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalWidth = maxWidth
                val tabCount = destinations.size.coerceAtLeast(1)
                val tabWidth = totalWidth / tabCount
                val selectedIndex = destinations.indexOf(current).coerceAtLeast(0)

                // Smooth fluid sliding animation with spring physics
                val animatedSlot by animateFloatAsState(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = spring(
                        dampingRatio = 0.76f,
                        stiffness = 380f,
                    ),
                    label = "ios-tab-slide",
                )

                // Elastic rubber-band stretch during movement (inspired by iOS 26 tab menu in video)
                val diff = kotlin.math.abs(animatedSlot - selectedIndex)
                val stretchX = 1f + (diff * 0.18f).coerceAtMost(0.24f)

                val indicatorX = (tabWidth * animatedSlot) + 5.dp
                val indicatorW = (tabWidth - 10.dp).coerceAtLeast(10.dp)

                // Sliding Active Indicator Capsule (Glass Pill with rim light & glow)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(indicatorX.roundToPx(), 6.dp.roundToPx()) }
                        .width(indicatorW)
                        .height(52.dp)
                        .graphicsLayer {
                            scaleX = stretchX
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    activeAccent.copy(alpha = if (isDark) 0.30f else 0.20f),
                                    activeAccent.copy(alpha = if (isDark) 0.14f else 0.08f),
                                ),
                            ),
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    activeAccent.copy(alpha = if (isDark) 0.75f else 0.60f),
                                    activeAccent.copy(alpha = if (isDark) 0.20f else 0.12f),
                                ),
                            ),
                            shape = RoundedCornerShape(26.dp),
                        ),
                ) {
                    // Top specular reflection line
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (isDark) 0.22f else 0.40f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }

                // Interactive Tab Items
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEach { dest ->
                        val isSelected = current == dest

                        // Scale animation for icon
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = 420f,
                            ),
                            label = "tab-scale-${dest.name}",
                        )

                        // Rotation animation (Settings gear rotates slightly on select)
                        val iconRotation by animateFloatAsState(
                            targetValue = if (isSelected && dest == TopDestination.Settings) 25f else 0f,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 320f),
                            label = "tab-rot-${dest.name}",
                        )

                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) activeAccent
                            else if (isDark) Color(0xFF8E9BAE) else Color(0xFF64748B),
                            label = "tab-text-${dest.name}",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (current != dest) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelect(dest)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            ) {
                                IosTabIcon(
                                    destination = dest,
                                    selected = isSelected,
                                    activeAccent = activeAccent,
                                    isDark = isDark,
                                    rotation = iconRotation,
                                )

                                Spacer(Modifier.height(3.dp))

                                Text(
                                    text = stringResource(dest.labelRes),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IosTabIcon(
    destination: TopDestination,
    selected: Boolean,
    activeAccent: Color,
    isDark: Boolean,
    rotation: Float,
) {
    val inactiveTint = if (isDark) Color(0xFFA0B0C4) else Color(0xFF64748B)
    val iconTint by animateColorAsState(
        targetValue = if (selected) activeAccent else inactiveTint,
        label = "tab-icon-tint",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(24.dp),
    ) {
        // Glowing halo behind active icon
        if (selected) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(activeAccent.copy(alpha = 0.22f)),
            )
        }

        Icon(
            painter = painterResource(destination.icon),
            contentDescription = stringResource(destination.labelRes),
            tint = iconTint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    rotationZ = rotation
                },
        )
    }
}

@Composable
private fun FloatingPillBar(
    destinations: List<TopDestination>,
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    compact: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        ) {
            destinations.forEach { dest ->
                NavPill(
                    destination = dest,
                    selected = current == dest,
                    compact = compact,
                    onClick = { onSelect(dest) },
                    modifier = Modifier,
                )
            }
        }
    }
}

@Composable
private fun ExpressivePillBar(destinations: List<TopDestination>, current: TopDestination, onSelect: (TopDestination) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 10.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { dest ->
                    val selected = current == dest
                    val bg by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        label = "exp-bg",
                    )
                    val fg by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "exp-fg",
                    )
                    Surface(
                        onClick = { onSelect(dest) },
                        shape = CircleShape,
                        color = bg,
                        contentColor = fg,
                        modifier = Modifier,
                    ) {
                        Row(
                            Modifier.padding(horizontal = if (selected) 14.dp else 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(dest.icon),
                                contentDescription = stringResource(dest.labelRes),
                                modifier = Modifier.size(22.dp),
                            )

                            AnimatedContent(selected, label = "exp-label") { show ->
                                if (show) {
                                    Row {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(dest.labelRes),
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(0.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialBar(
    destinations: List<TopDestination>,
    current: TopDestination,
    onSelect: (TopDestination) -> Unit,
    underline: Boolean,
) {
    NavigationBar {
        destinations.forEach { dest ->
            val selected = current == dest
            NavigationBarItem(
                modifier = Modifier,
                selected = selected,
                onClick = { onSelect(dest) },
                icon = {
                    if (underline) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .width(24.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else androidx.compose.ui.graphics.Color.Transparent,
                                    ),
                            )
                            Spacer(Modifier.height(6.dp))
                            Icon(painterResource(dest.icon), contentDescription = null)
                        }
                    } else {
                        Icon(painterResource(dest.icon), contentDescription = null)
                    }
                },
                label = { Text(stringResource(dest.labelRes), maxLines = 1) },
                colors = if (underline) {
                    androidx.compose.material3.NavigationBarItemDefaults.colors(
                        indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    )
                } else {
                    androidx.compose.material3.NavigationBarItemDefaults.colors()
                },
            )
        }
    }
}

@Composable
private fun NavPill(
    destination: TopDestination,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconSize by animateDpAsState(
        targetValue = when {
            compact -> if (selected) 21.dp else 19.dp
            selected -> 25.dp
            else -> 22.dp
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "nav-icon-size",
    )
    val icon = @Composable {
        Icon(
            painter = painterResource(destination.icon),
            contentDescription = stringResource(destination.labelRes),
            modifier = Modifier.size(iconSize),
        )
    }
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (scaleIn(initialScale = 0.85f) + fadeIn()) togetherWith
                (scaleOut(targetScale = 0.85f) + fadeOut())
        },
        label = "nav-pill",
        modifier = modifier,
    ) { isSelected ->
        val sizeMod = if (compact) Modifier.size(38.dp) else Modifier
        if (isSelected) {
            FilledIconButton(onClick = onClick, modifier = sizeMod) { icon() }
        } else {
            IconButton(onClick = onClick, modifier = sizeMod) { icon() }
        }
    }
}
