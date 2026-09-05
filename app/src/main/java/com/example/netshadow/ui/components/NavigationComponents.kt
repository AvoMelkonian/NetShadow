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
            .height(56.dp),
        color = Black,
        border = null // Top bar often doesn't have a bottom border in this design, but let's see
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
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

            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
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
    Column {
        // 1px Outline per DESIGN.md
        HorizontalDivider(color = BorderColor, thickness = 1.dp)
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .padding(vertical = 8.dp),
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
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = screen.label,
                        style = MaterialTheme.typography.labelSmall.copy( // labelSmall is Roboto Mono
                            color = tint,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}
