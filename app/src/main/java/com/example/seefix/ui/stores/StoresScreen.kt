package com.example.seefix.ui.stores

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.seefix.ai.LocationInfo
import com.example.seefix.location.EnrichedStoreLocation
import com.example.seefix.ui.diagnosis.TroubleshootingViewModel
import com.example.seefix.ui.theme.SeeFixTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StoresScreen(
    modifier: Modifier = Modifier,
    viewModel: TroubleshootingViewModel? = null,
    storesViewModel: StoresViewModel? = null
) {
    val context = LocalContext.current

    val effectiveTroubleshootingVm: TroubleshootingViewModel? = if (storesViewModel == null) {
        viewModel ?: viewModel(factory = TroubleshootingViewModel.provideFactory(context.applicationContext))
    } else {
        viewModel
    }

    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    DisposableEffect(Unit) {
        effectiveTroubleshootingVm?.startSensors()
        onDispose {
            effectiveTroubleshootingVm?.stopSensors()
        }
    }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        effectiveTroubleshootingVm?.refreshLocationAndStores()
    }

    val troubleshootingUiState = effectiveTroubleshootingVm?.uiState?.collectAsStateWithLifecycle()?.value

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All Stores") }
    var viewMode by remember { mutableStateOf(StoreViewMode.SPLIT) }

    val rawStores = troubleshootingUiState?.enrichedStores ?: emptyList()
    val userLocation: LocationInfo = troubleshootingUiState?.userLocation ?: LocationInfo(37.7749, -122.4194, "San Francisco Demo Fix")

    val categories = listOf("All Stores", "Electrical", "HVAC", "Tools & Hardware")

    val filteredStores = remember(rawStores, searchQuery, selectedCategory) {
        rawStores.filter { enriched ->
            val matchesCategory = selectedCategory == "All Stores" || enriched.category == selectedCategory
            val matchesQuery = searchQuery.isEmpty() ||
                    enriched.store.name.contains(searchQuery, ignoreCase = true) ||
                    enriched.store.address.contains(searchQuery, ignoreCase = true) ||
                    enriched.stockItems.any { it.partName.contains(searchQuery, ignoreCase = true) }
            matchesCategory && matchesQuery
        }
    }

    var selectedStore by remember(filteredStores) {
        mutableStateOf<EnrichedStoreLocation?>(filteredStores.firstOrNull())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(modifier = Modifier.height(2.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                effectiveTroubleshootingVm?.discoverNearbyStores(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search missing parts, capacitors, or store name...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search"
                )
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // View Mode Segmented Toggle ("Map View" / "List View" / "Split View")
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val modes = listOf(
                    StoreViewMode.SPLIT to "Split View",
                    StoreViewMode.MAP to "Map View",
                    StoreViewMode.LIST to "List View"
                )
                modes.forEach { (mode, label) ->
                    val isSelected = viewMode == mode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewMode = mode },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    StoreViewMode.MAP -> Icons.Rounded.Map
                                    StoreViewMode.LIST -> Icons.AutoMirrored.Rounded.FormatListBulleted
                                    StoreViewMode.SPLIT -> Icons.Rounded.ViewStream
                                },
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // Main Screen Content based on ViewMode
        when (viewMode) {
            StoreViewMode.MAP -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    InteractiveStoreMapView(
                        userLocation = userLocation,
                        stores = filteredStores,
                        selectedStore = selectedStore ?: filteredStores.firstOrNull(),
                        onStoreSelected = { store -> selectedStore = store },
                        modifier = Modifier.fillMaxSize()
                    )

                    selectedStore?.let { storeDetail ->
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            StoreDetailContent(
                                storeDetail = storeDetail,
                                onCallClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, "tel:${storeDetail.store.phone}".toUri())
                                    context.startActivity(intent)
                                },
                                onNavigateClick = {
                                    val store = storeDetail.store
                                    val googleNavUri = "google.navigation:q=${store.latitude},${store.longitude}".toUri()
                                    val mapIntent = Intent(Intent.ACTION_VIEW, googleNavUri)
                                    try {
                                        context.startActivity(mapIntent)
                                    } catch (_: Exception) {
                                        val geoUri = "geo:${store.latitude},${store.longitude}?q=${Uri.encode(store.name)}".toUri()
                                        val fallbackIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                        try {
                                            context.startActivity(fallbackIntent)
                                        } catch (_: Exception) {
                                            val webUri = "https://www.google.com/maps/search/?api=1&query=${store.latitude},${store.longitude}".toUri()
                                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            StoreViewMode.LIST -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredStores) { storeDetail ->
                        StoreItemCardDetailed(
                            storeDetail = storeDetail,
                            isSelected = selectedStore?.store?.id == storeDetail.store.id,
                            onStoreClick = { selectedStore = storeDetail },
                            onCallClick = {
                                val intent = Intent(Intent.ACTION_DIAL, "tel:${storeDetail.store.phone}".toUri())
                                context.startActivity(intent)
                            },
                            onNavigateClick = {
                                val store = storeDetail.store
                                val geoUri = "geo:${store.latitude},${store.longitude}?q=${Uri.encode(store.name)}".toUri()
                                val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                try {
                                    context.startActivity(mapIntent)
                                } catch (_: Exception) {
                                    val webUri = "https://www.google.com/maps/search/?api=1&query=${store.latitude},${store.longitude}".toUri()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                }
                            }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            StoreViewMode.SPLIT -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InteractiveStoreMapView(
                        userLocation = userLocation,
                        stores = filteredStores,
                        selectedStore = selectedStore ?: filteredStores.firstOrNull(),
                        onStoreSelected = { store -> selectedStore = store },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredStores) { storeDetail ->
                            StoreItemCardDetailed(
                                storeDetail = storeDetail,
                                isSelected = selectedStore?.store?.id == storeDetail.store.id,
                                onStoreClick = { selectedStore = storeDetail },
                                onCallClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, "tel:${storeDetail.store.phone}".toUri())
                                    context.startActivity(intent)
                                },
                                onNavigateClick = {
                                    val store = storeDetail.store
                                    val googleNavUri = "google.navigation:q=${store.latitude},${store.longitude}".toUri()
                                    val mapIntent = Intent(Intent.ACTION_VIEW, googleNavUri)
                                    try {
                                        context.startActivity(mapIntent)
                                    } catch (_: Exception) {
                                        val geoUri = "geo:${store.latitude},${store.longitude}?q=${Uri.encode(store.name)}".toUri()
                                        val fallbackIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                        try {
                                            context.startActivity(fallbackIntent)
                                        } catch (_: Exception) {
                                            val webUri = "https://www.google.com/maps/search/?api=1&query=${store.latitude},${store.longitude}".toUri()
                                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                        }
                                    }
                                }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreDetailContent(
    storeDetail: EnrichedStoreLocation,
    onCallClick: () -> Unit,
    onNavigateClick: () -> Unit
) {
    val store = storeDetail.store

    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Row: Store Name, Open/Close status & Distance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = store.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = store.address,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (store.isOpen) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFE57373).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (store.isOpen) "OPEN NOW" else "CLOSED",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (store.isOpen) Color(0xFF4CAF50) else Color(0xFFE57373)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${storeDetail.cardinalDirection} • ${store.distanceKm} km",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Part Stock Availability Badges
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Stock Availability:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                storeDetail.stockItems.forEach { stock ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (stock.isAvailable) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF3B1C1C),
                        border = BorderStroke(
                            1.dp,
                            if (stock.isAvailable) Color(0xFF4CAF50).copy(alpha = 0.4f) else Color(0xFFE57373).copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (stock.isAvailable) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = if (stock.isAvailable) Color(0xFF4CAF50) else Color(0xFFE57373),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${stock.partName}: ${stock.stockCountText}",
                                fontSize = 11.sp,
                                color = if (stock.isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFFFCDD2)
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCallClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Call Store", fontSize = 12.sp)
            }

            Button(
                onClick = onNavigateClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.NearMe,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Navigate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StoreItemCardDetailed(
    storeDetail: EnrichedStoreLocation,
    isSelected: Boolean = false,
    onStoreClick: () -> Unit = {},
    onCallClick: () -> Unit,
    onNavigateClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStoreClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        StoreDetailContent(
            storeDetail = storeDetail,
            onCallClick = onCallClick,
            onNavigateClick = onNavigateClick
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StoresScreenPreview() {
    SeeFixTheme {
        StoresScreen()
    }
}
