package com.example.mylibrary.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.mylibrary.ui.components.noRippleClickable
import com.example.mylibrary.ui.components.AppThemeSurface
import com.example.mylibrary.ui.theme.AppTheme
import com.example.mylibrary.ui.theme.FloatingActionSize
import com.example.mylibrary.ui.theme.FloatingActionIconSize
import com.example.mylibrary.ui.theme.FloatingNavigationBottomPadding
import com.example.mylibrary.ui.theme.FloatingNavigationActionGap
import com.example.mylibrary.ui.theme.FloatingNavigationCornerRadius
import com.example.mylibrary.ui.theme.FloatingNavigationHeight
import com.example.mylibrary.ui.theme.FloatingNavigationHorizontalPadding
import com.example.mylibrary.ui.theme.FloatingNavigationIconSize
import com.example.mylibrary.ui.theme.FloatingNavigationItemSpacing
import com.example.mylibrary.ui.theme.FloatingNavigationItemSize
import com.example.mylibrary.ui.theme.FloatingNavigationShadowElevation
import com.example.mylibrary.ui.theme.SurfaceRole

@Composable
fun LibraryBottomBar(
    selectedTab: Int,
    onNavigate: (Int) -> Unit,
    onAddItem: () -> Unit,
    iconResolver: NavigationIconResolver,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = FloatingNavigationBottomPadding),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FloatingNavigationActionGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppThemeSurface(
                role = SurfaceRole.CARD,
                modifier = Modifier.size(
                    width = FloatingNavigationHorizontalPadding * 2 +
                        FloatingNavigationItemSize * bottomDestinations.size +
                        FloatingNavigationItemSpacing * (bottomDestinations.size - 1),
                    height = FloatingNavigationHeight
                ),
                shape = RoundedCornerShape(FloatingNavigationCornerRadius),
                shadowElevation = FloatingNavigationShadowElevation,
                tonalElevation = 0.dp,
                drawImageSurface = false,
                forceOpaqueFallback = true
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = FloatingNavigationHorizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FloatingNavigationItemSpacing)
                ) {
                    bottomDestinations.forEachIndexed { index, destination ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .size(FloatingNavigationItemSize)
                                .background(
                                    color = if (isSelected) {
                                        colors.accent
                                    } else {
                                        colors.accent.copy(alpha = 0f)
                                    },
                                    shape = CircleShape
                                )
                                .semantics {
                                    selected = isSelected
                                    role = Role.Tab
                                }
                                .noRippleClickable(enabled = !isSelected) {
                                    onNavigate(index)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AppNavigationIcon(
                                slot = destination.iconSlot,
                                selected = isSelected,
                                iconResolver = iconResolver,
                                contentDescription = destination.label,
                                tint = if (isSelected) {
                                    colors.onAccent
                                } else {
                                    colors.textSecondary
                                },
                                modifier = Modifier.size(FloatingNavigationIconSize)
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.size(FloatingActionSize),
                shape = CircleShape,
                color = colors.accent,
                shadowElevation = FloatingNavigationShadowElevation,
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .testTag("add_item_fab")
                        .noRippleClickable(onClick = onAddItem),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "新增作品",
                        tint = colors.onAccent,
                        modifier = Modifier.size(FloatingActionIconSize)
                    )
                }
            }
        }
    }
}
