package com.cpamp.mobile.data.cache

import android.content.Context
import com.cpamp.mobile.common.runSuspendCatching
import com.cpamp.mobile.data.update.AppUpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CacheCleanupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheDao: CacheDao,
    private val updateRepository: AppUpdateRepository,
) {
    suspend fun clearRegenerableCache() {
        val failures = mutableListOf<Throwable>()
        runSuspendCatching { cacheDao.clear() }.onFailure(failures::add)
        runSuspendCatching { clearSharedReports() }.onFailure(failures::add)
        runSuspendCatching { updateRepository.clearRegenerableCache() }.onFailure(failures::add)
        if (failures.isNotEmpty()) throw CacheCleanupException(failures)
    }

    private suspend fun clearSharedReports() = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, SHARED_REPORTS_DIRECTORY)
        directory.listFiles()?.forEach { file ->
            check(file.deleteRecursively()) { "SHARED_REPORT_DELETE_FAILED" }
        }
    }

    private companion object {
        const val SHARED_REPORTS_DIRECTORY = "shared-reports"
    }
}

private class CacheCleanupException(causes: List<Throwable>) :
    Exception("CACHE_CLEANUP_FAILED: ${causes.size}", causes.firstOrNull())
