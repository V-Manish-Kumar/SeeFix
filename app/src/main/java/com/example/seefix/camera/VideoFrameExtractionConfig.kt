package com.example.seefix.camera

data class VideoFrameExtractionConfig(
    val maxFrames: Int = 12,
    val minFrames: Int = 5,
    val maxDimension: Int = 768,
    val jpegQuality: Int = 80
)
