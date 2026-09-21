package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkTask(
    val id: String,
    val title: String,
    val description: String,
    val domain: WorkDomain,
    val status: String = "IN_PROGRESS",
    val priority: String = "MEDIUM",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sessionId: String? = null
)
