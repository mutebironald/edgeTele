package com.dimaggi.edgetele.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dimaggi.edgetele.ui.screens.home.HomeScreen
import com.dimaggi.edgetele.ui.screens.incident.NewIncidentScreen
import com.dimaggi.edgetele.ui.screens.incident.ResultScreen
import com.dimaggi.edgetele.ui.screens.log.IncidentLogScreen

object Routes {
    const val HOME = "home"
    const val NEW_INCIDENT = "new_incident"
    const val RESULT = "result/{incidentId}"
    const val INCIDENT_LOG = "incident_log"

    fun result(incidentId: String) = "result/$incidentId"
}

@Composable
fun EdgeTeleNavGraph(
    responderID: String,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                responderID = responderID,
                onNewIncident = { navController.navigate(Routes.NEW_INCIDENT) },
                onViewLog = { navController.navigate(Routes.INCIDENT_LOG) }
            )
        }

        composable(Routes.INCIDENT_LOG) {
            IncidentLogScreen(
                onBack = { navController.popBackStack() },
                onOpenIncident = { incidentId ->
                    navController.navigate(Routes.result(incidentId))
                }
            )
        }

        composable(Routes.NEW_INCIDENT) {
            NewIncidentScreen(
                responderID = responderID,
                onBack = { navController.popBackStack() },
                onClassified = { incidentId ->
                    navController.navigate(Routes.result(incidentId)) {
                        // Pop new incident screen off the back stack
                        popUpTo(Routes.NEW_INCIDENT) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.RESULT,
            arguments = listOf(
                navArgument("incidentId") { type = NavType.StringType }
            )
        ) {
            ResultScreen(
                onBack = { navController.popBackStack() },
                onHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                }
            )
        }
    }
}
