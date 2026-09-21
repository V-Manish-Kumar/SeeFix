package com.example.seefix.ai.agent

enum class AgentStatus {
    IDLE,
    ANALYZING,
    WAITING_FOR_TOOL,
    WAITING_FOR_USER,
    ACTION_PROPOSED,
    COMPLETED,
    STOPPED,
    ERROR
}
