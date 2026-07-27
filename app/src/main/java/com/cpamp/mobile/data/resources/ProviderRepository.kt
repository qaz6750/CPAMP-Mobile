package com.cpamp.mobile.data.resources

import com.cpamp.mobile.data.remote.SessionApiClientFactory
import com.cpamp.mobile.data.remote.RemoteFailure
import com.cpamp.mobile.data.remote.remoteCall
import com.cpamp.mobile.domain.model.AuthenticatedSession
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class ProviderSection(
    val wireName: String,
    val title: String,
    val openAiCompatible: Boolean = false,
) {
    Gemini("gemini-api-key", "Gemini"),
    Interactions("interactions-api-key", "Interactions"),
    Codex("codex-api-key", "Codex"),
    Xai("xai-api-key", "xAI"),
    Claude("claude-api-key", "Claude"),
    Vertex("vertex-api-key", "Vertex"),
    OpenAiCompatible("openai-compatibility", "OpenAI compatible", openAiCompatible = true),
}

data class ProviderRecord(
    val section: ProviderSection,
    val identity: String,
    val title: String,
    val baseUrl: String,
    val prefix: String,
    val priority: Int?,
    val disabled: Boolean,
    val hasSecret: Boolean,
    val raw: JsonObject,
)

data class ProviderDraft(
    val name: String = "",
    val apiKey: String = "",
    val baseUrl: String = "",
    val prefix: String = "",
    val priority: Int? = null,
    val disabled: Boolean = false,
)

@Singleton
class ProviderRepository @Inject constructor(
    private val clientFactory: SessionApiClientFactory,
) {
    private val sectionLocks = ConcurrentHashMap<ProviderSection, Mutex>()

    suspend fun load(session: AuthenticatedSession, section: ProviderSection): List<ProviderRecord> {
        val payload = try {
            remoteCall { clientFactory.api(session).getDynamic(managementPath(section.wireName)) }
        } catch (_: RemoteFailure.NotFound) {
            JsonArray(emptyList())
        }
        return extractList(payload, section).mapNotNull { it as? JsonObject }
            .mapIndexed { index, raw -> raw.toProviderRecord(section, index) }
    }

    suspend fun save(
        session: AuthenticatedSession,
        section: ProviderSection,
        originalIdentity: String?,
        draft: ProviderDraft,
    ) {
        validateDraft(section, originalIdentity, draft)
        sectionLocks.getOrPut(section) { Mutex() }.withLock {
            val api = clientFactory.api(session)
            val rawConfig = runCatching {
                remoteCall { api.getDynamic(managementPath("config")) }
            }.getOrNull()
            val latest = rawConfig
                ?.let { extractList(it, section) }
                ?.takeIf { it.isNotEmpty() }
                ?: extractList(
                    remoteCall { api.getDynamic(managementPath(section.wireName)) },
                    section,
                )

            val records = latest.mapNotNull { it as? JsonObject }.toMutableList()
            val replacement = if (originalIdentity == null) {
                mergeDraft(JsonObject(emptyMap()), section, draft, keepExistingSecret = false)
            } else {
                val index = records.indexOfFirst { providerIdentity(section, it) == originalIdentity }
                check(index >= 0) { "PROVIDER_CHANGED" }
                mergeDraft(records[index], section, draft, keepExistingSecret = true).also { records[index] = it }
            }
            if (originalIdentity == null) records += replacement

            remoteCall {
                api.putDynamicUnit(
                    managementPath(section.wireName),
                    JsonArray(records),
                )
            }
        }
    }

    suspend fun delete(
        session: AuthenticatedSession,
        section: ProviderSection,
        identity: String,
    ) {
        sectionLocks.getOrPut(section) { Mutex() }.withLock {
            val api = clientFactory.api(session)
            val latest = extractList(
                remoteCall { api.getDynamic(managementPath(section.wireName)) },
                section,
            ).mapNotNull { it as? JsonObject }
            check(latest.any { providerIdentity(section, it) == identity }) { "PROVIDER_CHANGED" }
            val next = latest.filterNot { providerIdentity(section, it) == identity }
            remoteCall {
                api.putDynamicUnit(managementPath(section.wireName), JsonArray(next))
            }
        }
    }

    private fun validateDraft(
        section: ProviderSection,
        originalIdentity: String?,
        draft: ProviderDraft,
    ) {
        if (section.openAiCompatible) {
            require(draft.name.trim().isNotEmpty()) { "PROVIDER_NAME_REQUIRED" }
            require(draft.baseUrl.trim().startsWith("http://") || draft.baseUrl.trim().startsWith("https://")) {
                "PROVIDER_URL_REQUIRED"
            }
        }
        if (originalIdentity == null) {
            require(draft.apiKey.trim().isNotEmpty()) { "PROVIDER_KEY_REQUIRED" }
        }
    }

    private fun mergeDraft(
        original: JsonObject,
        section: ProviderSection,
        draft: ProviderDraft,
        keepExistingSecret: Boolean,
    ): JsonObject {
        val aliases = if (section.openAiCompatible) OPENAI_EDITABLE_FIELDS else KEY_EDITABLE_FIELDS
        val output = original.toMutableMap().apply { aliases.forEach(::remove) }

        if (section.openAiCompatible) {
            output["name"] = JsonPrimitive(draft.name.trim())
            output["base-url"] = JsonPrimitive(draft.baseUrl.trim())
            output["disabled"] = JsonPrimitive(draft.disabled)
            val oldEntries = original.firstArray(API_KEY_ENTRIES)
            output["api-key-entries"] = when {
                draft.apiKey.isNotBlank() && keepExistingSecret && oldEntries?.isNotEmpty() == true -> {
                    val first = oldEntries.first() as? JsonObject ?: JsonObject(emptyMap())
                    JsonArray(
                        listOf(JsonObject(first.toMutableMap().apply { put("api-key", JsonPrimitive(draft.apiKey.trim())) })) +
                            oldEntries.drop(1),
                    )
                }
                draft.apiKey.isNotBlank() -> JsonArray(
                    listOf(JsonObject(mapOf("api-key" to JsonPrimitive(draft.apiKey.trim())))),
                )
                keepExistingSecret && oldEntries != null -> oldEntries
                else -> JsonArray(emptyList())
            }
        } else {
            val existingKey = original.firstElement(API_KEY_FIELDS)
            when {
                draft.apiKey.isNotBlank() -> output["api-key"] = JsonPrimitive(draft.apiKey.trim())
                keepExistingSecret && existingKey != null -> output["api-key"] = existingKey
            }
            output["disabled"] = JsonPrimitive(draft.disabled)
        }

        draft.baseUrl.trim().takeIf(String::isNotEmpty)?.let { output["base-url"] = JsonPrimitive(it) }
        draft.prefix.trim().takeIf(String::isNotEmpty)?.let { output["prefix"] = JsonPrimitive(it) }
        draft.priority?.let { output["priority"] = JsonPrimitive(it) }
        return JsonObject(output.filterValues { it !is JsonNull })
    }

    private fun extractList(payload: JsonElement, section: ProviderSection): List<JsonElement> {
        if (payload is JsonArray) return payload
        if (payload !is JsonObject) return emptyList()
        val aliases = sectionAliases(section) + listOf("items", "data")
        for (alias in aliases) {
            val value = payload[alias]
            if (value is JsonArray) return value
        }
        return emptyList()
    }

    private fun JsonObject.toProviderRecord(section: ProviderSection, index: Int): ProviderRecord {
        val identity = providerIdentity(section, this).ifBlank { "${section.wireName}:$index" }
        val name = firstString(NAME_FIELDS)
        val baseUrl = firstString(BASE_URL_FIELDS)
        val prefix = firstString(listOf("prefix"))
        val authIndex = firstString(AUTH_INDEX_FIELDS)
        val title = when {
            name.isNotBlank() -> name
            authIndex.isNotBlank() -> authIndex
            prefix.isNotBlank() -> prefix
            baseUrl.isNotBlank() -> baseUrl
            else -> "${section.title} #${index + 1}"
        }
        return ProviderRecord(
            section = section,
            identity = identity,
            title = title,
            baseUrl = baseUrl,
            prefix = prefix,
            priority = firstInt(listOf("priority")),
            disabled = firstBoolean(listOf("disabled")) ?: false,
            hasSecret = firstString(API_KEY_FIELDS).isNotEmpty() ||
                firstArray(API_KEY_ENTRIES)?.isNotEmpty() == true || authIndex.isNotEmpty(),
            raw = this,
        )
    }

    private fun providerIdentity(section: ProviderSection, record: JsonObject): String {
        if (section.openAiCompatible) return record.firstString(NAME_FIELDS).trim().lowercase()
        val authIndex = record.firstString(AUTH_INDEX_FIELDS)
        if (authIndex.isNotBlank()) return "auth-index\u0000$authIndex"
        return record.firstString(API_KEY_FIELDS) + "\u0000" + record.firstString(BASE_URL_FIELDS)
    }

    private fun sectionAliases(section: ProviderSection): List<String> = when (section) {
        ProviderSection.Gemini -> listOf(section.wireName, "geminiApiKey", "geminiApiKeys")
        ProviderSection.Interactions -> listOf(section.wireName, "interactionsApiKey", "interactionsApiKeys")
        ProviderSection.Codex -> listOf(section.wireName, "codexApiKey", "codexApiKeys")
        ProviderSection.Xai -> listOf(section.wireName, "xaiApiKey", "xaiApiKeys")
        ProviderSection.Claude -> listOf(section.wireName, "claudeApiKey", "claudeApiKeys")
        ProviderSection.Vertex -> listOf(section.wireName, "vertexApiKey", "vertexApiKeys")
        ProviderSection.OpenAiCompatible -> listOf(section.wireName, "openaiCompatibility", "openAICompatibility")
    }

    private fun JsonObject.firstString(keys: List<String>): String =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull }?.trim().orEmpty()

    private fun JsonObject.firstInt(keys: List<String>): Int? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.intOrNull }

    private fun JsonObject.firstBoolean(keys: List<String>): Boolean? =
        keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.booleanOrNull }

    private fun JsonObject.firstElement(keys: List<String>): JsonElement? =
        keys.firstNotNullOfOrNull { key -> this[key] }

    private fun JsonObject.firstArray(keys: List<String>): JsonArray? =
        keys.firstNotNullOfOrNull { key -> this[key] as? JsonArray }

    private fun managementPath(suffix: String) = "v0/management/$suffix"

    private companion object {
        val API_KEY_FIELDS = listOf("api-key", "apiKey")
        val API_KEY_ENTRIES = listOf("api-key-entries", "apiKeyEntries", "api_key_entries", "api-keys", "apiKeys", "api_keys")
        val AUTH_INDEX_FIELDS = listOf("auth-index", "authIndex", "auth_index")
        val BASE_URL_FIELDS = listOf("base-url", "baseUrl", "base_url")
        val NAME_FIELDS = listOf("name", "id")
        val KEY_EDITABLE_FIELDS = (API_KEY_FIELDS + BASE_URL_FIELDS + listOf("prefix", "priority")).toSet()
        val OPENAI_EDITABLE_FIELDS = (
            NAME_FIELDS + BASE_URL_FIELDS + API_KEY_ENTRIES + listOf("prefix", "priority", "disabled")
        ).toSet()
    }
}
