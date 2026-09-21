package com.example.seefix

import com.example.seefix.location.StoreFinderRepository
import com.example.seefix.ui.stores.StoreViewMode
import com.example.seefix.ui.stores.StoresViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StoresViewModelTest {

    private lateinit var storeFinderRepository: StoreFinderRepository
    private lateinit var viewModel: StoresViewModel

    @Before
    fun setUp() {
        storeFinderRepository = StoreFinderRepository()
        viewModel = StoresViewModel(storeFinderRepository = storeFinderRepository)
    }

    @Test
    fun storesViewModel_initializesWithDefaultStoresAndSplitView() {
        val state = viewModel.uiState.value
        assertTrue(state.stores.isNotEmpty())
        assertTrue(state.filteredStores.isNotEmpty())
        assertEquals(StoreViewMode.SPLIT, state.viewMode)
        assertNotNull(state.selectedStore)
    }

    @Test
    fun storesViewModel_filtersBySearchQuery() {
        viewModel.onSearchQueryChanged("Capacitor")
        val state = viewModel.uiState.value

        assertTrue(state.filteredStores.isNotEmpty())
        assertTrue(state.filteredStores.all { store ->
            store.store.name.contains("Capacitor", ignoreCase = true) ||
                    store.stockItems.any { it.partName.contains("Capacitor", ignoreCase = true) }
        })
    }

    @Test
    fun storesViewModel_filtersByCategory() {
        viewModel.onCategorySelected("Electrical")
        val state = viewModel.uiState.value

        assertTrue(state.filteredStores.isNotEmpty())
        assertTrue(state.filteredStores.all { it.category == "Electrical" })
    }

    @Test
    fun storesViewModel_switchesViewModes() {
        viewModel.setViewMode(StoreViewMode.MAP)
        assertEquals(StoreViewMode.MAP, viewModel.uiState.value.viewMode)

        viewModel.setViewMode(StoreViewMode.LIST)
        assertEquals(StoreViewMode.LIST, viewModel.uiState.value.viewMode)

        viewModel.setViewMode(StoreViewMode.SPLIT)
        assertEquals(StoreViewMode.SPLIT, viewModel.uiState.value.viewMode)
    }

    @Test
    fun storesViewModel_selectsStore() {
        val initialStores = viewModel.uiState.value.filteredStores
        assertTrue(initialStores.size > 1)

        val targetStore = initialStores[1]
        viewModel.onStoreSelected(targetStore)

        assertEquals(targetStore.store.id, viewModel.uiState.value.selectedStore?.store?.id)
    }
}
