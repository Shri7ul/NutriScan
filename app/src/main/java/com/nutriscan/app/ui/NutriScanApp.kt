package com.nutriscan.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nutriscan.app.ui.details.DetailsScreen
import com.nutriscan.app.ui.home.HomeScreen
import com.nutriscan.app.ui.manual.ManualEntryScreen
import com.nutriscan.app.ui.scanner.ScannerScreen
import com.nutriscan.app.ui.search.ProductSearchScreen

private object Routes {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val SEARCH = "search"
    const val ARG_BARCODE = "barcode"
    const val DETAILS_PATTERN = "details/{$ARG_BARCODE}"
    const val MANUAL_PATTERN = "manual/{$ARG_BARCODE}"

    fun details(barcode: String): String = "details/$barcode"
    fun manual(barcode: String): String = "manual/$barcode"
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
                onSearchClick = {
                    navController.navigate(Routes.SEARCH) { launchSingleTop = true }
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

        composable(Routes.SEARCH) {
            ProductSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenProduct = { barcode -> navController.navigate(Routes.details(barcode)) }
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
                },
                onSearch = {
                    navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                },
                onAddInfo = { code -> navController.navigate(Routes.manual(code)) }
            )
        }

        composable(
            route = Routes.MANUAL_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_BARCODE) { type = NavType.StringType })
        ) { backStackEntry ->
            val barcode = backStackEntry.arguments?.getString(Routes.ARG_BARCODE).orEmpty()
            ManualEntryScreen(
                barcode = barcode,
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.navigate(Routes.details(barcode)) {
                        popUpTo(Routes.MANUAL_PATTERN) { inclusive = true }
                    }
                }
            )
        }
    }
}
