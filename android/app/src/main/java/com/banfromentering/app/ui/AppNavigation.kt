package com.banfromentering.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object Routes {
    const val LIST = "list"
    const val MAP = "map"
    const val SETTINGS = "settings"
    const val EDIT = "edit/{id}?lat={lat}&lng={lng}"

    /** [id] = 0 creates a new ban, optionally pre-positioned at [lat]/[lng]. */
    fun edit(id: Long = 0, lat: Double? = null, lng: Double? = null): String =
        "edit/$id?lat=${lat ?: ""}&lng=${lng ?: ""}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: BanViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            BanListScreen(
                viewModel = viewModel,
                onAdd = { navController.navigate(Routes.edit()) },
                onOpen = { navController.navigate(Routes.edit(it.id)) },
                onOpenMap = { navController.navigate(Routes.MAP) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.MAP) {
            MapScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpen = { navController.navigate(Routes.edit(it.id)) },
                onAddAt = { lat, lng -> navController.navigate(Routes.edit(lat = lat, lng = lng)) },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("lat") { type = NavType.StringType; defaultValue = "" },
                navArgument("lng") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val args = entry.arguments
            BanEditScreen(
                viewModel = viewModel,
                banId = args?.getLong("id") ?: 0,
                initialLatitude = args?.getString("lat")?.toDoubleOrNull(),
                initialLongitude = args?.getString("lng")?.toDoubleOrNull(),
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
