package com.cpamp.mobile.data.system

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Response

data class ObservedServerVersions(
    val cpaVersion: String? = null,
    val cpampVersion: String? = null,
)

@Singleton
class ServerVersionObserver @Inject constructor() {
    private val mutableVersions = MutableStateFlow<Map<String, ObservedServerVersions>>(emptyMap())
    val versions: StateFlow<Map<String, ObservedServerVersions>> = mutableVersions.asStateFlow()

    fun observe(profileId: String, response: Response) {
        val cpa = response.header("x-cpa-version")?.cleanVersion()
            ?: response.header("x-server-version")?.cleanVersion()?.takeIf { response.isExplicitCpaResponse() }
        val cpamp = response.header("x-cpamp-version")?.cleanVersion()
            ?: response.header("x-manager-version")?.cleanVersion()
        if (cpa == null && cpamp == null) return
        mutableVersions.update { current ->
            val old = current[profileId] ?: ObservedServerVersions()
            current + (profileId to old.copy(
                cpaVersion = cpa ?: old.cpaVersion,
                cpampVersion = cpamp ?: old.cpampVersion,
            ))
        }
    }

    fun snapshot(profileId: String): ObservedServerVersions =
        mutableVersions.value[profileId] ?: ObservedServerVersions()

    private fun Response.isExplicitCpaResponse(): Boolean {
        val identity = sequenceOf(
            header("x-server-name"),
            header("x-server-product"),
            header("x-service-name"),
        ).filterNotNull().joinToString(" ").lowercase()
        return identity.contains("cpa") && !identity.contains("manager") && !identity.contains("cpamp")
    }

    private fun String.cleanVersion(): String? = trim().takeIf { it.isNotEmpty() }
}
