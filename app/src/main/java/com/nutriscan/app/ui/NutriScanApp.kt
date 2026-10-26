package com.nutriscan.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nutriscan.app.ui.details.DetailsScreen
import com.nutriscan.app.ui.home.HomeScreen
import com.nutriscan.app.ui.scanner.ScannerScreen

private object Routes {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val ARG_BARCODE = "barcode"
    const val DETAILS_PATTERN = "details/{$ARG_BARCODE}"

    fun details(barcode: String): String = "details/$barcode"
}

@Composable
fun NutriScanApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onScanClick = {
                    navController.navigate(Routes.SCANNER) { launchSingleTop = true }
                },
                onBarcodeEntered = { barcode ->
                    navController.navigate(Routes.details(barcode))
                },
                onOpenRecent = { barcode ->
                    navController.navigate(Routes.details(barcode))
                }
            )
        }

        composable(Routes.SCANNER) {
            ScannerScreen(
                onBack = { navController.popBackStack() },
                onBarcodeDetected = { barcode ->
                    // The scanner is a one-shot screen: drop it so Back from details returns home.
                    navController.navigate(Routes.details(barcode)) {
                        popUpTo(Routes.SCANNER) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.DETAILS_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_BARCODE) { type = NavType.StringType })
        ) { backStackEntry ->
            val barcode = backStackEntry.arguments?.getString(Routes.ARG_BARCODE).orEmpty()
            DetailsScreen(
                barcode = barcode,
                onBack = { navController.popBackStack() },
                onRescan = {
                    navController.navigate(Routes.SCANNER) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
