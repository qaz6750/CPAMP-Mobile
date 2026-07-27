package com.cpamp.mobile.data.resources

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.model.AuthFileDeleteResultDto
import com.cpamp.mobile.data.remote.model.AuthFileDto
import com.cpamp.mobile.data.remote.model.AuthFileStatusPatchDto
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import java.io.IOException
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class AuthFilesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clientFactory: SessionApiClientFactory,
    private val json: Json,
) {
    suspend fun list(session: AuthenticatedSession): List<AuthFileDto> =
        remoteCall { clientFactory.api(session).authFiles().files }

    suspend fun setDisabled(session: AuthenticatedSession, name: String, disabled: Boolean) {
        remoteCall {
            clientFactory.api(session).patchAuthFile(AuthFileStatusPatchDto(name, disabled))
        }
    }

    suspend fun delete(session: AuthenticatedSession, name: String): AuthFileDeleteResultDto =
        remoteCall { clientFactory.api(session).deleteAuthFile(name) }

    suspend fun loadText(session: AuthenticatedSession, name: String): String {
        val body = remoteCall { clientFactory.api(session).downloadAuthFile(name) }
        val length = body.contentLength()
        if (length > MAX_AUTH_FILE_BYTES) {
            body.close()
            throw IOException("AUTH_FILE_TOO_LARGE")
        }
        return body.use { responseBody ->
            responseBody.byteStream().use { input ->
                readBoundedText(input)
            }
        }
    }

    suspend fun readLocalImport(uri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex) && cursor.getLong(sizeIndex) > MAX_AUTH_FILE_BYTES) {
                throw IOException("AUTH_FILE_TOO_LARGE")
            }
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "credential.json"

        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("AUTH_FILE_READ_FAILED")
        val text = input.use(::readBoundedText)
        validateName(name)
        val parsed = runCatching { json.parseToJsonElement(text) }
            .getOrElse { throw IllegalArgumentException("AUTH_FILE_JSON_INVALID") }
        require(parsed is kotlinx.serialization.json.JsonObject || parsed is kotlinx.serialization.json.JsonArray) {
            "AUTH_FILE_JSON_INVALID"
        }
        name to text
    }

    suspend fun saveText(session: AuthenticatedSession, name: String, text: String) {
        validateName(name)
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_AUTH_FILE_BYTES) { "AUTH_FILE_TOO_LARGE" }
        val parsed = runCatching { json.parseToJsonElement(text) }
            .getOrElse { throw IllegalArgumentException("AUTH_FILE_JSON_INVALID") }
        require(parsed is kotlinx.serialization.json.JsonObject || parsed is kotlinx.serialization.json.JsonArray) {
            "AUTH_FILE_JSON_INVALID"
        }
        val body = bytes.toRequestBody(JSON_MEDIA_TYPE)
        val part = MultipartBody.Part.createFormData("file", name.trim(), body)
        remoteCall { clientFactory.api(session).uploadAuthFile(part) }
    }

    private fun readBoundedText(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_AUTH_FILE_BYTES) throw IOException("AUTH_FILE_TOO_LARGE")
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun validateName(rawName: String) {
        val name = rawName.trim()
        require(name.length in 1..180 && name.endsWith(".json", ignoreCase = true)) {
            "AUTH_FILE_NAME_INVALID"
        }
        require(name.none { it == '/' || it == '\\' || it == '\u0000' || it == '\r' || it == '\n' || it == '"' }) {
            "AUTH_FILE_NAME_INVALID"
        }
    }

    private companion object {
        const val MAX_AUTH_FILE_BYTES = 1024 * 1024
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
