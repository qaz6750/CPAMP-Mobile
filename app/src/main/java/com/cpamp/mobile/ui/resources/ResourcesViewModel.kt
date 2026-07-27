package com.cpamp.mobile.ui.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.cpamp.mobile.data.auth.SessionRepository
import com.cpamp.mobile.data.remote.model.AuthFileDto
import com.cpamp.mobile.data.resources.AuthFilesRepository
import com.cpamp.mobile.data.resources.ProviderDraft
import com.cpamp.mobile.data.resources.ProviderRecord
import com.cpamp.mobile.data.resources.ProviderRepository
import com.cpamp.mobile.data.resources.ProviderSection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ResourceTab { Providers, AuthFiles }

data class ProviderEditorState(
    val section: ProviderSection,
    val original: ProviderRecord? = null,
    val draft: ProviderDraft = ProviderDraft(),
)

data class AuthFileEditorState(
    val name: String,
    val content: String = "",
    val loading: Boolean = true,
    val originalName: String? = null,
)

data class ResourcesUiState(
    val tab: ResourceTab = ResourceTab.Providers,
    val providers: Map<ProviderSection, List<ProviderRecord>> = emptyMap(),
    val authFiles: List<AuthFileDto> = emptyList(),
    val loading: Boolean = true,
    val mutating: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val providerEditor: ProviderEditorState? = null,
    val authFileEditor: AuthFileEditorState? = null,
)

@HiltViewModel
class ResourcesViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val providerRepository: ProviderRepository,
    private val authFilesRepository: AuthFilesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ResourcesUiState())
    val state: StateFlow<ResourcesUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun selectTab(tab: ResourceTab) {
        mutableState.value = mutableState.value.copy(tab = tab)
    }

    fun refresh() {
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            val providers = async { runCatching { providerRepository.loadAll(session) } }
            val authFiles = async { runCatching { authFilesRepository.list(session) } }
            val providerResult = providers.await()
            val authResult = authFiles.await()
            mutableState.value = mutableState.value.copy(
                providers = providerResult.getOrElse { mutableState.value.providers },
                authFiles = authResult.getOrElse { mutableState.value.authFiles },
                loading = false,
                error = listOfNotNull(providerResult.exceptionOrNull(), authResult.exceptionOrNull())
                    .firstOrNull()?.safeMessage(),
            )
        }
    }

    fun openProviderEditor(section: ProviderSection, record: ProviderRecord? = null) {
        mutableState.value = mutableState.value.copy(
            providerEditor = ProviderEditorState(
                section = section,
                original = record,
                draft = record?.let { existing ->
                    ProviderDraft(
                        name = if (section.openAiCompatible) existing.title else "",
                        baseUrl = existing.baseUrl,
                        prefix = existing.prefix,
                        priority = existing.priority,
                        disabled = existing.disabled,
                    )
                } ?: ProviderDraft(),
            ),
            error = null,
        )
    }

    fun updateProviderDraft(draft: ProviderDraft) {
        mutableState.value.providerEditor?.let { editor ->
            mutableState.value = mutableState.value.copy(providerEditor = editor.copy(draft = draft))
        }
    }

    fun closeProviderEditor() {
        mutableState.value = mutableState.value.copy(providerEditor = null)
    }

    fun saveProvider() {
        val editor = mutableState.value.providerEditor ?: return
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutate {
                providerRepository.save(
                    session = session,
                    section = editor.section,
                    originalIdentity = editor.original?.identity,
                    draft = editor.draft,
                )
                mutableState.value = mutableState.value.copy(providerEditor = null, message = "PROVIDER_SAVED")
            }
        }
    }

    fun deleteProvider(record: ProviderRecord) {
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutate {
                providerRepository.delete(session, record.section, record.identity)
                mutableState.value = mutableState.value.copy(message = "PROVIDER_DELETED")
            }
        }
    }

    fun setAuthFileDisabled(file: AuthFileDto, disabled: Boolean) {
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutate {
                authFilesRepository.setDisabled(session, file.name, disabled)
                mutableState.value = mutableState.value.copy(message = "AUTH_FILE_UPDATED")
            }
        }
    }

    fun deleteAuthFile(file: AuthFileDto) {
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutate {
                authFilesRepository.delete(session, file.name)
                mutableState.value = mutableState.value.copy(message = "AUTH_FILE_DELETED")
            }
        }
    }

    fun newAuthFile() {
        mutableState.value = mutableState.value.copy(
            authFileEditor = AuthFileEditorState(name = "credential.json", content = "{\n  \n}", loading = false),
            error = null,
        )
    }

    fun editAuthFile(file: AuthFileDto) {
        mutableState.value = mutableState.value.copy(
            authFileEditor = AuthFileEditorState(name = file.name),
            error = null,
        )
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            runCatching { authFilesRepository.loadText(session, file.name) }
                .onSuccess { content ->
                    mutableState.value = mutableState.value.copy(
                        authFileEditor = AuthFileEditorState(
                            name = file.name,
                            content = content,
                            loading = false,
                            originalName = file.name,
                        ),
                    )
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        authFileEditor = null,
                        error = error.safeMessage(),
                    )
                }
        }
    }

    fun updateAuthFileEditor(name: String, content: String) {
        val current = mutableState.value.authFileEditor ?: return
        mutableState.value = mutableState.value.copy(
            authFileEditor = current.copy(
                name = name.take(180),
                content = content.take(MAX_EDITOR_CHARS),
                loading = false,
            ),
        )
    }

    fun importAuthFile(uri: Uri) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(mutating = true, error = null)
            runCatching { authFilesRepository.readLocalImport(uri) }
                .onSuccess { (name, content) ->
                    mutableState.value = mutableState.value.copy(
                        mutating = false,
                        authFileEditor = AuthFileEditorState(
                            name = name,
                            content = content,
                            loading = false,
                        ),
                    )
                }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        mutating = false,
                        error = error.safeMessage(),
                    )
                }
        }
    }

    fun closeAuthFileEditor() {
        mutableState.value = mutableState.value.copy(authFileEditor = null)
    }

    fun saveAuthFile() {
        val editor = mutableState.value.authFileEditor ?: return
        viewModelScope.launch {
            val session = sessionRepository.session.value ?: return@launch
            mutate {
                authFilesRepository.saveText(session, editor.name, editor.content)
                mutableState.value = mutableState.value.copy(authFileEditor = null, message = "AUTH_FILE_SAVED")
            }
        }
    }

    fun clearNotice() {
        mutableState.value = mutableState.value.copy(error = null, message = null)
    }

    private suspend fun mutate(block: suspend () -> Unit) {
        mutableState.value = mutableState.value.copy(mutating = true, error = null, message = null)
        runCatching { block() }
            .onFailure { error ->
                mutableState.value = mutableState.value.copy(error = error.safeMessage())
            }
        mutableState.value = mutableState.value.copy(mutating = false)
        refresh()
    }

    private fun Throwable.safeMessage(): String = when (message) {
        "PROVIDER_CHANGED" -> "PROVIDER_CHANGED"
        "PROVIDER_NAME_REQUIRED", "PROVIDER_URL_REQUIRED", "PROVIDER_KEY_REQUIRED" -> message.orEmpty()
        "AUTH_FILE_TOO_LARGE", "AUTH_FILE_NAME_INVALID", "AUTH_FILE_JSON_INVALID",
        "AUTH_FILE_READ_FAILED" -> message.orEmpty()
        else -> "RESOURCE_REQUEST_FAILED"
    }

    private companion object {
        const val MAX_EDITOR_CHARS = 1024 * 1024
    }
}
