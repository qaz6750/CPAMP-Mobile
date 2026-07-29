package com.cpamp.mobile.data.update

import android.net.Uri

enum class UpdateStatus {
    Idle,
    Checking,
    NoRelease,
    UpToDate,
    Available,
    Downloading,
    Verifying,
    ReadyToInstall,
    Failed,
}

data class AppUpdateState(
    val status: UpdateStatus = UpdateStatus.Idle,
    val release: GitHubReleaseDto? = null,
    val progressPercent: Int? = null,
    val installUri: Uri? = null,
    val error: UpdateError? = null,
)

enum class UpdateError { Network, RateLimited, InvalidRelease, Download, Checksum, Signature }
