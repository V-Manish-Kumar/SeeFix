package com.example.seefix.camera

sealed interface ExtractionUIState {
    object Idle : ExtractionUIState
    object Extracting : ExtractionUIState
    data class Extracted(val frameCount: Int) : ExtractionUIState
    data class Error(val message: String) : ExtractionUIState
}
