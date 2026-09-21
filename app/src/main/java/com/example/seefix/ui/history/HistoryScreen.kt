package com.example.seefix.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.seefix.data.repository.DemoScenarios
import com.example.seefix.domain.model.TroubleshootingSession
import com.example.seefix.ui.diagnosis.TroubleshootingViewModel
import com.example.seefix.ui.theme.SeeFixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionLogItem(
    val id: String,
    val equipmentName: String,
    val equipmentCategory: String,
    val issueDescription: String,
    val confidenceScore: Float,
    val totalSteps: Int,
    val completedSteps: Int,
    val status: String, // "Resolved", "In Progress"
    val dateText: String,
    val toolsUsed: String = "Insulated Multimeter, Screwdriver",
    val durationMinutes: Long = 5
)

fun TroubleshootingSession.toSessionLogItem(): SessionLogItem {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(timestamp))
    val status = if (isResolved) "Resolved" else "In Progress"
    val toolsText = if (availableTools.isNotEmpty()) availableTools.joinToString { it.name } else "Insulated Toolbag"

    return SessionLogItem(
        id = id,
        equipmentName = device?.name ?: "Hardware Unit",
        equipmentCategory = device?.category ?: "Appliance",
        issueDescription = detectedProblem,
        confidenceScore = confidence,
        totalSteps = steps.size,
        completedSteps = completedSteps.size,
        status = status,
        dateText = formattedDate,
        toolsUsed = toolsText,
        durationMinutes = (durationSeconds / 60).coerceAtLeast(3)
    )
}

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: TroubleshootingViewModel? = null,
    onSelectSession: (SessionLogItem) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val filterOptions = listOf("All", "Resolved", "In Progress")

    val persistentSessions = viewModel?.uiState?.collectAsStateWithLifecycle()?.value?.historySessions ?: emptyList()
    val sessionList = if (persistentSessions.isNotEmpty()) {
        persistentSessions.map { it.toSessionLogItem() }
    } else {
        DemoScenarios.getPreloadedHistory().map { it.toSessionLogItem() }
    }

    val filteredSessions = sessionList.filter { session ->
        val matchesFilter = when (selectedFilter) {
            "Resolved" -> session.status == "Resolved"
            "In Progress" -> session.status == "In Progress"
            else -> true
        }
        val matchesQuery = searchQuery.isEmpty() ||
                session.equipmentName.contains(searchQuery, ignoreCase = true) ||
                session.issueDescription.contains(searchQuery, ignoreCase = true)
        matchesFilter && matchesQuery
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search session history or issue...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search"
                )
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterOptions) { filter ->
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Session Logs List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredSessions) { session ->
                HistoryItemCardDetailed(
                    session = session,
                    onClick = { onSelectSession(session) }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun HistoryItemCardDetailed(
    session: SessionLogItem,
    onClick: () -> Unit
) {
    val isResolved = session.status == "Resolved"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.equipmentName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isResolved) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isResolved) Icons.Rounded.CheckCircle else Icons.Rounded.PendingActions,
                            contentDescription = null,
                            tint = if (isResolved) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = session.status,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isResolved) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                    }
                }
            }

            Text(
                text = "Issue: ${session.issueDescription}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Tools: ${session.toolsUsed}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${(session.confidenceScore * 100).toInt()}% Confidence",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${session.dateText} • ${session.durationMinutes} mins • ${session.completedSteps}/${session.totalSteps} Steps",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isResolved) "View Log" else "Resume Diagnosis",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    SeeFixTheme {
        HistoryScreen()
    }
}
