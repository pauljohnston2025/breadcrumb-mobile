package com.paul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.* // M2 Imports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.paul.viewmodels.DeviceSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class) // For ListItem
@Composable
fun AppDrawerContent(
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    // Get current route to highlight the selected item
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Column(modifier = modifier.fillMaxSize()) {
        // --- Drawer Header (Optional) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp) // Adjust height
                .background(MaterialTheme.colors.primary), // Use theme color
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "Breadcrumb Nav", // Your App Name
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        // --- Navigation Items ---
        drawerScreens.forEach { screen ->

            // Use ListItem for M2 drawer items
            ListItem(
                modifier = Modifier
                    .clickable {
                        scope.launch { drawerState.close() } // Close drawer first
                        // Prevent navigating to the same screen
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.startDestinationId) {
                                    // we want to be able to press back and get to the original app page, do not keep any other part of the stack
                                    inclusive = false
                                    // do not save the state, when a user switches back to the device page they should not see 'device settings'
                                    saveState = false
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = false
                            }
                        }
                    }
                    .padding(horizontal = 16.dp), // Indent items slightly
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = if (currentRoute == screen.route) MaterialTheme.colors.primary else LocalContentColor.current.copy(
                            alpha = ContentAlpha.medium
                        )
                    )
                },

                text = {
                    Text(
                        text = screen.title,
                        fontWeight = if (currentRoute == screen.route) FontWeight.Bold else FontWeight.Normal,
                        color = if (currentRoute == screen.route) MaterialTheme.colors.primary else LocalContentColor.current
                    )
                },
                // Optional: Highlight background if selected
                // backgroundColor = if (currentRoute == screen.route) MaterialTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent
            )
            // Divider(modifier = Modifier.padding(horizontal = 16.dp)) // Optional divider
        }
        // Add other items like About, Logout etc. here if needed
    }
}