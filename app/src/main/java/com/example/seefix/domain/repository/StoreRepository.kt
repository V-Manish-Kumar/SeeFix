package com.example.seefix.domain.repository

import com.example.seefix.domain.model.StoreLocation

interface StoreRepository {
    suspend fun getNearbyStores(query: String, lat: Double, lng: Double): List<StoreLocation>
}
