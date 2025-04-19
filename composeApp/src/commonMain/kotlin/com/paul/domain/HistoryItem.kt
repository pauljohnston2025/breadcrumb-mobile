package com.paul.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class HistoryItem(
    val id: String,
    val routeId: String,
    val timestamp: Instant
)