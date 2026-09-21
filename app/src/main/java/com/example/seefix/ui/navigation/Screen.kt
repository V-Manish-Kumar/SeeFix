package com.example.seefix.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

sealed interface Screen {
    val routeName: String
    val title: String
    val icon: ImageVector

    data object Home : Screen {
        override val routeName: String = "home"
        override val title: String = "Home"
        override val icon: ImageVector = Icons.Rounded.Home
    }

    data object Diagnosis : Screen {
        override val routeName: String = "diagnosis"
        override val title: String = "Diagnose"
        override val icon: ImageVector = Icons.Rounded.Build
    }

    data object Stores : Screen {
        override val routeName: String = "stores"
        override val title: String = "Stores"
        override val icon: ImageVector = Icons.Rounded.Storefront
    }

    data object History : Screen {
        override val routeName: String = "history"
        override val title: String = "History"
        override val icon: ImageVector = Icons.Rounded.History
    }

    data object Settings : Screen {
        override val routeName: String = "settings"
        override val title: String = "Settings"
        override val icon: ImageVector = Icons.Rounded.Settings
    }

    data object LocalAITest : Screen {
        override val routeName: String = "debug_ai"
        override val title: String = "Local Gemma AI Test"
        override val icon: ImageVector = Icons.Rounded.BugReport
    }

    data object AISettings : Screen {
        override val routeName: String = "ai_settings"
        override val title: String = "AI Configuration"
        override val icon: ImageVector = Icons.Rounded.Tune
    }

    companion object {
        val bottomNavScreens = listOf(Home, Diagnosis, Stores, History, Settings)
    }
}
