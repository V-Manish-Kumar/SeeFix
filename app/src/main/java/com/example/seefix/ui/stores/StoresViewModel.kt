package com.example.seefix.ui.stores

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.seefix.ai.LocationInfo
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.location.EnrichedStoreLocation
import com.example.seefix.location.LocationManager
import com.example.seefix.location.StoreFinderRepository
import com.example.seefix.location.StoreSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StoreViewMode {
    MAP,
    LIST,
    SPLIT
}

data class StoresUiState(
    val userLocation: LocationInfo? = null,
    val stores: List<EnrichedStoreLocation> = emptyList(),
    val filteredStores: List<EnrichedStoreLocation> = emptyList(),
    val selectedStore: EnrichedStoreLocation? = null,
    val viewMode: StoreViewMode = StoreViewMode.SPLIT,
    val searchQuery: String = "",
    val selectedCategory: String = "All Stores",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class StoresViewModel(
    private val storeFinderRepository: StoreFinderRepository = StoreFinderRepository(),
    private val locationManager: LocationManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoresUiState())
    val uiState: StateFlow<StoresUiState> = _uiState.asStateFlow()

    init {
        observeLocation()
        refreshStores()
    }

    private fun observeLocation() {
        locationManager?.let { lm ->
            viewModelScope.launch {
                lm.userLocation.collect { location ->
                    _uiState.update { it.copy(userLocation = location) }
                    refreshStores()
                }
            }
        }
    }

    fun refreshStores(missingTools: List<ToolItem> = emptyList()) {
        val loc = _uiState.value.userLocation
        val userLat = loc?.latitude ?: 37.7749
        val userLng = loc?.longitude ?: -122.4194

        val result = storeFinderRepository.findNearbyStores(
            userLat = userLat,
            userLng = userLng,
            query = _uiState.value.searchQuery,
            missingTools = missingTools
        )

        val storesList = when (result) {
            is StoreSearchResult.Success -> result.stores
            is StoreSearchResult.Error -> emptyList()
        }

        val filtered = filterStoresList(storesList, _uiState.value.searchQuery, _uiState.value.selectedCategory)
        val defaultSelected = _uiState.value.selectedStore?.let { sel ->
            filtered.find { it.store.id == sel.store.id }
        } ?: filtered.firstOrNull() ?: storesList.firstOrNull()

        _uiState.update {
            it.copy(
                stores = storesList,
                filteredStores = filtered,
                selectedStore = defaultSelected
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = filterStoresList(current.stores, query, current.selectedCategory)
            val updatedSelected = if (current.selectedStore != null && filtered.any { it.store.id == current.selectedStore.store.id }) {
                current.selectedStore
            } else {
                filtered.firstOrNull()
            }
            current.copy(
                searchQuery = query,
                filteredStores = filtered,
                selectedStore = updatedSelected
            )
        }
        refreshStores()
    }

    fun onCategorySelected(category: String) {
        _uiState.update { current ->
            val filtered = filterStoresList(current.stores, current.searchQuery, category)
            val updatedSelected = if (current.selectedStore != null && filtered.any { it.store.id == current.selectedStore.store.id }) {
                current.selectedStore
            } else {
                filtered.firstOrNull()
            }
            current.copy(
                selectedCategory = category,
                filteredStores = filtered,
                selectedStore = updatedSelected
            )
        }
    }

    fun onStoreSelected(store: EnrichedStoreLocation?) {
        _uiState.update { it.copy(selectedStore = store) }
    }

    fun setViewMode(mode: StoreViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    private fun filterStoresList(
        allStores: List<EnrichedStoreLocation>,
        query: String,
        category: String
    ): List<EnrichedStoreLocation> {
        return allStores.filter { enriched ->
            val matchesCategory = category == "All Stores" || enriched.category == category
            val matchesQuery = query.isEmpty() ||
                    enriched.store.name.contains(query, ignoreCase = true) ||
                    enriched.store.address.contains(query, ignoreCase = true) ||
                    enriched.stockItems.any { it.partName.contains(query, ignoreCase = true) }
            matchesCategory && matchesQuery
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StoresViewModel(
                        storeFinderRepository = StoreFinderRepository(),
                        locationManager = LocationManager(context.applicationContext)
                    ) as T
                }
            }
    }
}
