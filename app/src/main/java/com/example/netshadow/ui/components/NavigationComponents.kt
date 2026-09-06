package com.example.netshadow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netshadow.ui.navigation.Screen
import com.example.netshadow.ui.navigation.bottomNavItems
import com.example.netshadow.ui.theme.Black
import com.example.netshadow.ui.theme.BorderColor
import com.example.netshadow.ui.theme.NeonGreen
import com.example.netshadow.ui.theme.SurfaceCard

@Composable
fun NetShadowTopAppBar(
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp), // Increased from 56dp
        color = Black
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.CenterStart) {
                leadingIcon?.invoke()
            }
            
            Text(
                text = "NETSHADOW_OS",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) {
                trailingIcon?.invoke()
            }
        }
    }
}

@Composable
fun NetShadowBottomNavigation(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = SurfaceCard
    ) {
        Column {
            // 1px Outline per DESIGN.md
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp), // Increased padding for non-3-button nav
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    val tint = if (isSelected) NeonGreen else Color.Gray

                    Column(
                        modifier = Modifier
                            .clickable { onNavigate(screen) }
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.label,
                            tint = tint,
                            modifier = Modifier.size(26.dp) // Slightly larger icons
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = screen.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = tint,
                                fontSize = 11.sp, // Slightly larger text
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}
