package com.cpamp.mobile.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CPAMPCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}

