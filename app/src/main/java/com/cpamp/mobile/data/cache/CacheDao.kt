package com.cpamp.mobile.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheDao {
    @Query("SELECT * FROM response_cache WHERE profileId = :profileId AND kind = :kind LIMIT 1")
    suspend fun get(profileId: String, kind: String): CacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CacheEntity)

    @Query("DELETE FROM response_cache WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Query("DELETE FROM response_cache WHERE kind = :kind")
    suspend fun deleteKind(kind: String)

    @Query("DELETE FROM response_cache")
    suspend fun clear()
}

