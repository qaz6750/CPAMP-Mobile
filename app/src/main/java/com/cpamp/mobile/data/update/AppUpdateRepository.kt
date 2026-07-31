package com.cpamp.mobile.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cpamp.mobile.BuildConfig
import com.cpamp.mobile.common.runSuspendCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

private val Context.updateDataStore by preferencesDataStore(name = "app_update")

@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(false)
        .build()
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            scope.launch {
                val savedId = this@AppUpdateRepository.context.updateDataStore.data.first()[DOWNLOAD_ID]
                if (completedId == savedId) verifyDownloadedApk(completedId)
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch { restoreDownload() }
    }

    suspend fun checkForUpdates() {
        if (mutableState.value.status == UpdateStatus.Checking) return
        mutableState.value = mutableState.value.copy(status = UpdateStatus.Checking, error = null)
        val preferences = context.updateDataStore.data.first()
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "CPAMPMobile/${BuildConfig.VERSION_NAME} Android")
            .apply { preferences[RELEASE_ETAG]?.let { header("If-None-Match", it) } }
            .build()
        val result = runSuspendCatching {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> {
                            val body = response.body?.string().orEmpty()
                            val release = json.decodeFromString<GitHubReleaseDto>(body)
                            context.updateDataStore.edit { stored ->
                                response.header("ETag")?.let { stored[RELEASE_ETAG] = it }
                                stored[RELEASE_JSON] = body
                            }
                            release
                        }
                        304 -> preferences[RELEASE_JSON]?.let(json::decodeFromString)
                            ?: throw UpdateException(UpdateError.InvalidRelease)
                        404 -> null
                        403, 429 -> throw UpdateException(UpdateError.RateLimited)
                        else -> throw UpdateException(UpdateError.Network)
                    }
                }
            }
        }
        result.onSuccess { release ->
            mutableState.value = when {
                release == null -> AppUpdateState(status = UpdateStatus.NoRelease)
                release.releaseAssets() == null -> AppUpdateState(
                    status = UpdateStatus.Failed,
                    release = release,
                    error = UpdateError.InvalidRelease,
                )
                isNewerVersion(release.tagName, BuildConfig.VERSION_NAME) -> AppUpdateState(
                    status = UpdateStatus.Available,
                    release = release,
                )
                else -> AppUpdateState(status = UpdateStatus.UpToDate, release = release)
            }
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                status = UpdateStatus.Failed,
                error = (error as? UpdateException)?.reason ?: UpdateError.Network,
            )
        }
    }

    suspend fun downloadUpdate() {
        val release = mutableState.value.release ?: return
        val assets = release.releaseAssets() ?: return
        if (mutableState.value.status != UpdateStatus.Available) return
        mutableState.value = mutableState.value.copy(status = UpdateStatus.Downloading, progressPercent = 0, error = null)
        runSuspendCatching {
            val checksum = fetchChecksum(assets)
            val fileName = assets.apk.name
            val destination = resolveUpdateFile(fileName)
                ?: throw UpdateException(UpdateError.Download)
            check(!destination.exists() || destination.delete()) { "UPDATE_FILE_DELETE_FAILED" }
            val request = DownloadManager.Request(Uri.parse(assets.apk.downloadUrl))
                .setTitle(fileName)
                .setDescription(context.applicationInfo.loadLabel(context.packageManager).toString())
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            val downloadId = downloadManager.enqueue(request)
            try {
                context.updateDataStore.edit { stored ->
                    stored[DOWNLOAD_ID] = downloadId
                    stored[DOWNLOAD_FILE] = fileName
                    stored[DOWNLOAD_CHECKSUM] = checksum
                    stored[DOWNLOAD_VERSION] = assets.version
                    stored[RELEASE_JSON] = json.encodeToString(release)
                }
            } catch (error: Throwable) {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { downloadManager.remove(downloadId) }
                        .exceptionOrNull()
                        ?.let(error::addSuppressed)
                    runCatching {
                        check(!destination.exists() || destination.delete()) { "UPDATE_FILE_DELETE_FAILED" }
                    }.exceptionOrNull()?.let(error::addSuppressed)
                }
                throw error
            }
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                status = UpdateStatus.Failed,
                error = (error as? UpdateException)?.reason ?: UpdateError.Download,
            )
        }
    }

    suspend fun refreshDownloadStatus() {
        val id = context.updateDataStore.data.first()[DOWNLOAD_ID] ?: return
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id)) ?: return
        cursor.use {
            if (!it.moveToFirst()) return
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> verifyDownloadedApk(id)
                DownloadManager.STATUS_FAILED -> mutableState.value = mutableState.value.copy(
                    status = UpdateStatus.Failed,
                    error = UpdateError.Download,
                )
                else -> mutableState.value = mutableState.value.copy(
                    status = UpdateStatus.Downloading,
                    progressPercent = if (total > 0) (downloaded * 100 / total).toInt() else null,
                )
            }
        }
    }

    suspend fun clearRegenerableCache() {
        val preferences = context.updateDataStore.data.first()
        preferences[DOWNLOAD_ID]?.let { downloadManager.remove(it) }
        withContext(Dispatchers.IO) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?.listFiles()
                ?.forEach { file ->
                    check(file.deleteRecursively()) { "UPDATE_FILE_DELETE_FAILED" }
                }
        }
        context.updateDataStore.edit { it.clear() }
        mutableState.value = AppUpdateState()
    }

    private suspend fun restoreDownload() {
        val preferences = context.updateDataStore.data.first()
        val release = preferences[RELEASE_JSON]?.let { runCatching { json.decodeFromString<GitHubReleaseDto>(it) }.getOrNull() }
        val downloadedVersion = preferences[DOWNLOAD_VERSION]
        if (downloadedVersion != null && !isNewerVersion(downloadedVersion, BuildConfig.VERSION_NAME)) {
            preferences[DOWNLOAD_FILE]?.let { fileName ->
                resolveUpdateFile(fileName)?.delete()
            }
            clearDownloadState()
            mutableState.value = AppUpdateState(status = UpdateStatus.UpToDate, release = release)
        } else if (release != null && preferences[DOWNLOAD_ID] != null) {
            mutableState.value = AppUpdateState(status = UpdateStatus.Downloading, release = release)
            refreshDownloadStatus()
        }
    }

    private suspend fun fetchChecksum(assets: ReleaseAssets): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(assets.checksum.downloadUrl)
            .header("User-Agent", "CPAMPMobile/${BuildConfig.VERSION_NAME} Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UpdateException(UpdateError.Download)
            val body = response.body?.readLimitedUtf8(MAX_CHECKSUM_BYTES)
                ?: throw UpdateException(UpdateError.Checksum)
            parseSha256(body, assets.apk.name) ?: throw UpdateException(UpdateError.Checksum)
        }
    }

    private fun ResponseBody.readLimitedUtf8(maxBytes: Int): String {
        val buffer = ByteArray(maxBytes + 1)
        var total = 0
        byteStream().use { input ->
            while (total < buffer.size) {
                val count = input.read(buffer, total, buffer.size - total)
                if (count < 0) break
                total += count
            }
        }
        if (total > maxBytes) throw UpdateException(UpdateError.Checksum)
        return buffer.copyOf(total).toString(Charsets.UTF_8)
    }

    private suspend fun verifyDownloadedApk(downloadId: Long) {
        mutableState.value = mutableState.value.copy(status = UpdateStatus.Verifying, progressPercent = null)
        val preferences = context.updateDataStore.data.first()
        val fileName = preferences[DOWNLOAD_FILE] ?: return verificationFailed(UpdateError.Download)
        val expected = preferences[DOWNLOAD_CHECKSUM] ?: return verificationFailed(UpdateError.Checksum)
        val file = resolveUpdateFile(fileName) ?: return verificationFailed(UpdateError.Download)
        val error = when {
            !file.isFile -> UpdateError.Download
            file.sha256() != expected -> UpdateError.Checksum
            !hasCurrentAppSignature(file) -> UpdateError.Signature
            else -> null
        }
        if (error != null) {
            file.delete()
            verificationFailed(error)
            return
        }
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
            ?: return verificationFailed(UpdateError.Download)
        mutableState.value = mutableState.value.copy(status = UpdateStatus.ReadyToInstall, installUri = uri, error = null)
    }

    private suspend fun verificationFailed(error: UpdateError) {
        clearDownloadState()
        mutableState.value = mutableState.value.copy(status = UpdateStatus.Failed, installUri = null, error = error)
    }

    private suspend fun clearDownloadState() {
        context.updateDataStore.edit { stored ->
            stored.remove(DOWNLOAD_ID)
            stored.remove(DOWNLOAD_FILE)
            stored.remove(DOWNLOAD_CHECKSUM)
            stored.remove(DOWNLOAD_VERSION)
        }
    }

    private suspend fun resolveUpdateFile(fileName: String): File? = withContext(Dispatchers.IO) {
        if (!isExpectedUpdateFileName(fileName)) return@withContext null
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.canonicalFile
            ?: return@withContext null
        File(directory, fileName).canonicalFile.takeIf { it.parentFile == directory }
    }

    @Suppress("DEPRECATION")
    private fun hasCurrentAppSignature(apk: File): Boolean {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        return signatureDigests(archive).intersect(signatureDigests(installed)).isNotEmpty()
    }

    @Suppress("DEPRECATION")
    private fun signatureDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            info.signatures.orEmpty()
        }
        return signatures.map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex() }.toSet()
    }

    private fun File.sha256(): String = inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private class UpdateException(val reason: UpdateError) : Exception()

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/qaz6750/CPAMP-Mobile/releases/latest"
        const val MAX_CHECKSUM_BYTES = 512
        val RELEASE_ETAG = stringPreferencesKey("release_etag")
        val RELEASE_JSON = stringPreferencesKey("release_json")
        val DOWNLOAD_ID = longPreferencesKey("download_id")
        val DOWNLOAD_FILE = stringPreferencesKey("download_file")
        val DOWNLOAD_CHECKSUM = stringPreferencesKey("download_checksum")
        val DOWNLOAD_VERSION = stringPreferencesKey("download_version")
    }
}

internal fun parseSha256(body: String, expectedFileName: String): String? {
    if (!isExpectedUpdateFileName(expectedFileName)) return null
    val lines = body.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    val match = lines.singleOrNull()?.let(SHA256_ENTRY::matchEntire) ?: return null
    val fileName = match.groupValues[2].removePrefix("*")
    return match.groupValues[1].lowercase().takeIf { fileName == expectedFileName }
}

internal fun isExpectedUpdateFileName(fileName: String): Boolean =
    fileName.matches(Regex("^cpamp-mobile-v\\d+\\.\\d+\\.\\d+\\.apk$"))

private val SHA256_ENTRY = Regex("^([0-9a-fA-F]{64})\\s+([^\\s]+)$")
