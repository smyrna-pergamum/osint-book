package io.github.smyrna_pergamum.osintbook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.smyrna_pergamum.osintbook.OsintApp
import io.github.smyrna_pergamum.osintbook.data.AppSettings

@Composable
fun AppNav(app: OsintApp, settings: AppSettings) {
    val nav = rememberNavController()
    // Fixed at first composition so finishing onboarding does not rebuild the graph.
    val start = remember { if (settings.onboarded) "home" else "onboarding" }
    NavHost(nav, startDestination = start) {
        composable("onboarding") {
            OnboardingScreen(app, settings) {
                nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
            }
        }
        composable("home") {
            HomeScreen(
                app,
                open = { nav.navigate("investigation/$it") },
                openSettings = { nav.navigate("settings") },
            )
        }
        composable("investigation/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
            InvestigationScreen(
                app, e.arguments!!.getString("id")!!,
                back = { nav.popBackStack() },
                openQuery = { nav.navigate("query/$it") },
                openFinding = { nav.navigate("finding/$it") },
                openReport = { nav.navigate("report/$it") },
                openMap = { nav.navigate("map/$it") },
                openSettings = { nav.navigate("settings") },
            )
        }
        composable("query/{targetId}", listOf(navArgument("targetId") { type = NavType.StringType })) { e ->
            QueryScreen(app, e.arguments!!.getString("targetId")!!, back = { nav.popBackStack() }, openSettings = { nav.navigate("settings") })
        }
        composable("finding/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
            FindingScreen(app, e.arguments!!.getString("id")!!, back = { nav.popBackStack() },
                openMap = { inv, f -> nav.navigate("map/$inv?finding=$f") })
        }
        composable("report/{id}", listOf(navArgument("id") { type = NavType.StringType })) { e ->
            ReportScreen(app, e.arguments!!.getString("id")!!, back = { nav.popBackStack() })
        }
        composable(
            "map/{inv}?finding={finding}",
            listOf(navArgument("inv") { type = NavType.StringType }, navArgument("finding") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { e ->
            MapScreen(app, e.arguments!!.getString("inv")!!, e.arguments?.getString("finding"),
                back = { nav.popBackStack() }, openFinding = { nav.navigate("finding/$it") })
        }
        composable("settings") {
            SettingsScreen(app, settings, back = { nav.popBackStack() }, openInvestigation = { nav.navigate("investigation/$it") })
        }
    }
}
