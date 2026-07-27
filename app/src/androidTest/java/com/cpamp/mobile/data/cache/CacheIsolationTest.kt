package com.cpamp.mobile.data.cache

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheIsolationTest {
    private lateinit var database: CPAMPCacheDatabase
    private lateinit var dao: CacheDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CPAMPCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun cacheKeysAreIsolatedAndProfileDeletionIsTargeted() = runBlocking {
        dao.upsert(CacheEntity("server-a", "dashboard.v1", "a", 1))
        dao.upsert(CacheEntity("server-b", "dashboard.v1", "b", 2))

        assertEquals("a", dao.get("server-a", "dashboard.v1")?.payload)
        assertEquals("b", dao.get("server-b", "dashboard.v1")?.payload)

        dao.deleteProfile("server-a")

        assertNull(dao.get("server-a", "dashboard.v1"))
        assertEquals("b", dao.get("server-b", "dashboard.v1")?.payload)
    }
}