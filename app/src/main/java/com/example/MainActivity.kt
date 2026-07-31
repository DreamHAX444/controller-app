package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.*
import com.example.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val navController = rememberNavController()
                
                Scaffold(
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route ?: "fleet"
                        
                        // Hide bottom bar on specific screens
                        if (currentRoute in listOf("fleet", "live", "history", "alerts")) {
                            BottomNavBar(currentRoute) { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "fleet",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("fleet") { 
                            FleetScreen(
                                onDeviceClick = { id, type ->
                                    navController.navigate("connect/$id/$type")
                                }
                            ) 
                        }
                        composable("live") { LiveFeedScreen() }
                        composable("history") { AudioTelemetryScreen() }
                        composable("alerts") { 
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Alerts Screen")
                            }
                        }
                        composable("connect/{id}/{type}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: ""
                            val type = backStackEntry.arguments?.getString("type") ?: ""
                            ConnectionRequestScreen(
                                deviceId = id,
                                deviceType = type,
                                onConnectClick = { navController.navigate("loading") },
                                onCancelClick = { navController.popBackStack() }
                            )
                        }
                        composable("loading") {
                            LoadingScreen(
                                onLoadingComplete = {
                                    navController.navigate("live") {
                                        popUpTo("fleet")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(currentRoute: String, onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem("fleet", "Fleet", Icons.Default.Map, currentRoute, onNavigate)
                NavItem("live", "Live", Icons.Default.Videocam, currentRoute, onNavigate)
                NavItem("history", "History", Icons.Default.Analytics, currentRoute, onNavigate)
                NavItem("alerts", "Alerts", Icons.Default.NotificationsActive, currentRoute, onNavigate)
            }
        }
    }
}

@Composable
fun NavItem(route: String, label: String, icon: ImageVector, currentRoute: String, onNavigate: (String) -> Unit) {
    val selected = currentRoute == route
    val iconColor = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary
    val alpha = if (selected) 1f else 0.6f
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .alpha(alpha)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onNavigate(route) }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp, 32.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = iconColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label, 
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = fontWeight,
                fontSize = 11.sp,
                letterSpacing = (-0.01).sp
            ), 
            color = textColor
        )
    }
}