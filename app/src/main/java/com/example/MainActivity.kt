package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.navigation.Routes
import com.example.ui.screens.BatchTasksScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OcrDebugScreen
import com.example.ui.screens.OcrPreviewScreen
import com.example.ui.screens.OcrReviewScreen
import com.example.viewmodel.OcrPreviewViewModel
import com.example.ui.screens.PaddleOcrTestScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.BatchTasksViewModel
import com.example.viewmodel.HomeViewModel
import com.example.viewmodel.OcrDebugViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val devMode by com.example.data.SettingsRepository(context).devModeFlow.collectAsStateWithLifecycle(initialValue = false)

            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME
                    ) {
                        composable(Routes.HOME) { backStackEntry ->
                            val viewModel: HomeViewModel = viewModel()
                            
                            val guidanceAction = backStackEntry.savedStateHandle.remove<String>("guidance_proceed_action")
                            
                            HomeScreen(
                                viewModel = viewModel,
                                guidanceAction = guidanceAction,
                                onNavigateToBatchTasks = { navController.navigate(Routes.BATCH_TASKS) },
                                onNavigateToHistory = { navController.navigate(Routes.HISTORY_BILLS) },
                                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                                onNavigateToCameraGuidance = { action -> navController.navigate(Routes.createCameraGuidanceRoute(action)) },
                                onNavigateToPaddleTest = { if (devMode) navController.navigate(Routes.PADDLE_TEST) }
                            )
                        }

                        composable(Routes.SETTINGS) {
                            com.example.ui.screens.SettingsScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Routes.CAMERA_GUIDANCE,
                            arguments = listOf(navArgument("action") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val action = backStackEntry.arguments?.getString("action") ?: "camera"
                            com.example.ui.screens.CameraGuidanceScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onProceed = { 
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("guidance_proceed_action", action)
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Routes.HISTORY_BILLS) {
                            com.example.ui.screens.HistoryBillsScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Routes.BATCH_TASKS) {
                            val viewModel: BatchTasksViewModel = viewModel()
                            BatchTasksScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToDebug = { taskId ->
                                    navController.navigate(Routes.createOcrDebugRoute(taskId))
                                },
                                onNavigateToPreview = { taskId ->
                                    navController.navigate(Routes.createOcrPreviewRoute(taskId))
                                },
                                onNavigateToReview = { taskId ->
                                    navController.navigate(Routes.createOcrReviewRoute(taskId))
                                }
                            )
                        }

                        composable(
                            route = Routes.OCR_DEBUG,
                            arguments = listOf(navArgument("taskId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            if (devMode) {
                                val taskId = backStackEntry.arguments?.getInt("taskId") ?: -1
                                val viewModel: OcrDebugViewModel = viewModel()
                                OcrDebugScreen(
                                    taskId = taskId,
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            } else {
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    navController.popBackStack()
                                }
                            }
                        }

                        composable(
                            route = Routes.OCR_PREVIEW,
                            arguments = listOf(navArgument("taskId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val taskId = backStackEntry.arguments?.getInt("taskId") ?: -1
                            val viewModel: OcrPreviewViewModel = viewModel()
                            OcrPreviewScreen(
                                taskId = taskId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToReview = { navController.navigate(Routes.createOcrReviewRoute(taskId)) }
                            )
                        }
                        
                        composable(
                            route = Routes.OCR_REVIEW,
                            arguments = listOf(navArgument("taskId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val taskId = backStackEntry.arguments?.getInt("taskId") ?: -1
                            val viewModel: OcrPreviewViewModel = viewModel()
                            OcrReviewScreen(
                                taskId = taskId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToOverview = { navController.popBackStack(Routes.BATCH_TASKS, inclusive = false) }
                            )
                        }

                        composable(Routes.PADDLE_TEST) {
                            if (devMode) {
                                val viewModel: com.example.viewmodel.PaddleOcrTestViewModel = viewModel()
                                PaddleOcrTestScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            } else {
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    navController.popBackStack()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
