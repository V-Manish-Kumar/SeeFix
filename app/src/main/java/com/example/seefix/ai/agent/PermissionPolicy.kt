package com.example.seefix.ai.agent

class PermissionPolicy {
    fun requiresUserConfirmation(tool: SeeFixTool, arguments: Map<String, String>): Boolean {
        val action = arguments["action"]
        if (action == "open_navigation" || action == "dial_phone") {
            return true
        }

        if (tool.permission == ToolPermission.USER_CONFIRMATION_REQUIRED ||
            tool.permission == ToolPermission.RESTRICTED
        ) {
            return true
        }

        return false
    }
}
