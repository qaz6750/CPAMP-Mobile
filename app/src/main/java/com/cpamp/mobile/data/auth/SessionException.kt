package com.cpamp.mobile.data.auth

internal class SessionException(
    val reason: Reason,
) : Exception(reason.name) {
    enum class Reason {
        NoActiveProfile,
        ProfileNotFound,
        SavedKeyUnavailable,
        MissingAdminKey,
        CleartextConfirmationRequired,
    }
}
