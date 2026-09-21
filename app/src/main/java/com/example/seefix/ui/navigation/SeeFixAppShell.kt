package com.example.seefix.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.seefix.domain.model.SafetyStatus
import com.example.seefix.ui.components.SeeFixBottomBar
import com.example.seefix.ui.components.SeeFixTopBar
import com.example.seefix.ui.debug.LocalAITestScreen
import com.example.seefix.ui.diagnosis.DiagnosisScreen
import com.example.seefix.ui.diagnosis.TroubleshootingViewModel
import com.example.seefix.ui.history.HistoryScreen
import com.example.seefix.ui.home.HomeScreen
import com.example.seefix.ui.settings.AISettingsScreen
import com.example.seefix.ui.settings.SettingsScreen
import com.example.seefix.ui.stores.StoresScreen

@Composable
fun SeeFixAppShell(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: TroubleshootingViewModel = viewModel(
        factory = TroubleshootingViewModel.provideFactory(context.applicationContext)
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    Scaffold(
        topBar = {
            SeeFixTopBar(
                title = currentScreen.title,
                safetyStatus = if (currentScreen == Screen.Diagnosis) {
                    uiState.session.safetyStatus
                } else {
                    SafetyStatus.SAFE_DEFAULT
                }
            )
        },
        bottomBar = {
            SeeFixBottomBar(
                currentScreen = currentScreen,
                onScreenSelected = { screen ->
                    currentScreen = screen
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding)
        when (currentScreen) {
            Screen.Home -> HomeScreen(
                onStartDiagnosis = {
                    viewModel.initializeDemoSession("Smart Washing Machine (iQOO Demo Appliance)")
                    currentScreen = Screen.Diagnosis
                },
                onSelectEquipmentPreset = { preset ->
                    viewModel.initializeDemoSession(preset.name)
                    currentScreen = Screen.Diagnosis
                },
                onNavigateToStores = { currentScreen = Screen.Stores },
                onNavigateToHistory = { currentScreen = Screen.History },
                onNavigateToLocalAITest = { currentScreen = Screen.LocalAITest },
                onNavigateToAISettings = { currentScreen = Screen.AISettings },
                viewModel = viewModel,
                modifier = screenModifier
            )
            Screen.Diagnosis -> DiagnosisScreen(
                equipmentName = uiState.session?.device?.name ?: "Smart Washing Machine (iQOO Demo Appliance)",
                initialSafetyMessage = uiState.session?.safetyStatus?.message ?: "SAFE: Low Voltage Demo Appliance",
                viewModel = viewModel,
                onNavigateToStores = { currentScreen = Screen.Stores },
                modifier = screenModifier
            )
            Screen.Stores -> StoresScreen(
                viewModel = viewModel,
                modifier = screenModifier
            )
            Screen.History -> HistoryScreen(
                viewModel = viewModel,
                onSelectSession = { session ->
                    viewModel.initializeDemoSession(session.equipmentName)
                    currentScreen = Screen.Diagnosis
                },
                modifier = screenModifier
            )
            Screen.Settings -> SettingsScreen(
                onNavigateToLocalAITest = { currentScreen = Screen.LocalAITest },
                onNavigateToAISettings = { currentScreen = Screen.AISettings },
                modifier = screenModifier
            )
            Screen.LocalAITest -> LocalAITestScreen(
                onNavigateBack = { currentScreen = Screen.Settings },
                modifier = screenModifier
            )
            Screen.AISettings -> AISettingsScreen(
                onNavigateBack = { currentScreen = Screen.Settings },
                modifier = screenModifier
            )
        }
    }
}
