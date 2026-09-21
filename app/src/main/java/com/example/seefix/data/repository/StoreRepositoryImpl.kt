package com.example.seefix.data.repository

import com.example.seefix.domain.model.StoreLocation
import com.example.seefix.domain.repository.StoreRepository

class StoreRepositoryImpl : StoreRepository {
    override suspend fun getNearbyStores(
        query: String,
        lat: Double,
        lng: Double
    ): List<StoreLocation> {
        return listOf(
            StoreLocation(
                id = "store_1",
                name = "Hardware Express Supply",
                address = "101 Industrial Pkwy, Sector 4",
                distanceKm = 1.2,
                isOpen = true,
                phone = "(555) 019-2831",
                latitude = lat + 0.005,
                longitude = lng + 0.005
            ),
            StoreLocation(
                id = "store_2",
                name = "Pro Parts & Electrical Depot",
                address = "450 Metro Tech Blvd",
                distanceKm = 3.5,
                isOpen = true,
                phone = "(555) 014-9922",
                latitude = lat - 0.012,
                longitude = lng + 0.008
            )
        )
    }
}
