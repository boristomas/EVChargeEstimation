package com.chupacabra.evchargeestimation

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chupacabra.evchargeestimation.ui.ChargeViewModel
import com.chupacabra.evchargeestimation.ui.components.FuturisticBackground
import com.chupacabra.evchargeestimation.ui.components.glowBorderBrush
import com.chupacabra.evchargeestimation.ui.components.panelFill
import com.chupacabra.evchargeestimation.ui.screens.CalculatorScreen
import com.chupacabra.evchargeestimation.ui.screens.CameraOcrScreen
import com.chupacabra.evchargeestimation.ui.screens.HistoryScreen
import com.chupacabra.evchargeestimation.ui.theme.EVChargeEstimationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Main UI is portrait-only; scan screen unlocks sensor orientation temporarily.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        setContent {
            EVChargeEstimationTheme {
                EvChargeApp()
            }
        }
    }
}

private object Routes {
    const val CALCULATOR = "calculator"
    const val HISTORY = "history"
    const val CAMERA = "camera"
}

@Composable
fun EvChargeApp(viewModel: ChargeViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.ui.collectAsStateWithLifecycle()
    val updateState by viewModel.update.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isCamera = currentDestination?.route == Routes.CAMERA
    val scheme = MaterialTheme.colorScheme
    val activity = LocalContext.current as? ComponentActivity

    // Quiet update check when the app opens (needs network; fails silently offline).
    LaunchedEffect(Unit) {
        viewModel.checkForUpdatesOnLaunch()
    }

    // Keep portrait whenever we leave the camera route.
    DisposableEffect(isCamera) {
        if (!isCamera) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose { }
    }

    val content: @Composable () -> Unit = {
        Scaffold(
            containerColor = if (isCamera) Color.Black else Color.Transparent,
            contentWindowInsets = if (isCamera) {
                WindowInsets(0, 0, 0, 0)
            } else {
                WindowInsets(0, 0, 0, 0) // edge-to-edge; screens handle their own padding via Scaffold
            },
            bottomBar = {
                if (!isCamera) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(glowBorderBrush())
                        )
                        NavigationBar(
                            containerColor = panelFill(),
                            tonalElevation = 0.dp,
                            contentColor = scheme.onSurface
                        ) {
                            NavigationBarItem(
                                selected = currentDestination?.hierarchy?.any {
                                    it.route == Routes.CALCULATOR
                                } == true,
                                onClick = {
                                    navController.navigate(Routes.CALCULATOR) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(Icons.Default.Calculate, contentDescription = null)
                                },
                                label = { Text("Home") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = scheme.primary,
                                    selectedTextColor = scheme.primary,
                                    indicatorColor = scheme.primary.copy(alpha = 0.16f),
                                    unselectedIconColor = scheme.onSurfaceVariant,
                                    unselectedTextColor = scheme.onSurfaceVariant
                                )
                            )
                            NavigationBarItem(
                                selected = currentDestination?.hierarchy?.any {
                                    it.route == Routes.HISTORY
                                } == true,
                                onClick = {
                                    navController.navigate(Routes.HISTORY) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(Icons.Default.History, contentDescription = null)
                                },
                                label = { Text("History") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = scheme.primary,
                                    selectedTextColor = scheme.primary,
                                    indicatorColor = scheme.primary.copy(alpha = 0.16f),
                                    unselectedIconColor = scheme.onSurfaceVariant,
                                    unselectedTextColor = scheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val hostPadding = if (isCamera) PaddingValues(0.dp) else innerPadding
            NavHost(
                navController = navController,
                startDestination = Routes.CALCULATOR,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(hostPadding)
            ) {
                composable(Routes.CALCULATOR) {
                    CalculatorScreen(
                        state = uiState,
                        onCurrentChange = viewModel::onCurrentPercentChange,
                        onHoursToFullChange = viewModel::onHoursToFullChange,
                        onMinutesPartToFullChange = viewModel::onMinutesPartToFullChange,
                        onDesiredChange = viewModel::onDesiredPercentChange,
                        onClear = viewModel::clearInputs,
                        onOpenCamera = { navController.navigate(Routes.CAMERA) },
                        updateState = updateState,
                        onCheckUpdate = { viewModel.checkForUpdates(silent = false) },
                        onDownloadUpdate = viewModel::downloadUpdate,
                        onInstallUpdate = { viewModel.installDownloadedUpdate() },
                        onDismissUpdate = viewModel::dismissUpdateBanner,
                        onOpenReleasePage = viewModel::openReleasePage,
                        onClearUpdateMessage = viewModel::clearUpdateMessage
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        entries = history,
                        onDelete = viewModel::deleteHistoryEntry,
                        onClearAll = viewModel::clearHistory
                    )
                }
                composable(Routes.CAMERA) {
                    CameraOcrScreen(
                        onResult = { parsed ->
                            viewModel.applyOcrResult(parsed)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    if (isCamera) {
        // No futuristic chrome behind the camera — pure preview + side panel.
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            content()
        }
    } else {
        FuturisticBackground {
            content()
        }
    }
}
