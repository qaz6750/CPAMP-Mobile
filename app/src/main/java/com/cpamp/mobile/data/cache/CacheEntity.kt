package com.cpamp.mobile.data.cache

import androidx.room.Entity

@Entity(
    tableName = "response_cache",
    primaryKeys = ["profileId", "kind"],
)
data class CacheEntity(
    val profileId: String,
    val kind: String,
    val payload: String,
    val updatedAt: Long,
)

