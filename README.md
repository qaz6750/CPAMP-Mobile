# CPAMP Mobile

CPAMP Mobile is a native Android administration client for a configured CPA-Manager-Plus Manager Server. It is built with Kotlin, Jetpack Compose, Material 3, Hilt, Retrofit/OkHttp, Kotlin Serialization, Room, DataStore, and Android Keystore.

Current version: **1.0.4**

> [!IMPORTANT]
> CPAMP Mobile is an independent, unofficial client. It is not affiliated with, endorsed by, or maintained by Seakee or the [CPA-Manager-Plus](https://github.com/seakee/CPA-Manager-Plus) project. CPA-Manager-Plus names and upstream project references identify interoperability only.

## Server requirements

The first release requires an already configured CPA-Manager-Plus **full-mode Manager Server**, normally exposed on port `18317`.

- Enter the Manager Server base address, such as `https://manager.example.com:18317`.
- Use the **CPAMP Admin Key** as the login credential. This is not a client API key issued to applications using the gateway.
- The app validates `/status` or `/usage-service/info`, checks the Admin Key, and reads available capabilities before saving a profile.
- CPA lightweight mode, commonly exposed on port `8317`, is detected and reported as unsupported.
- Initial Manager Server setup is not performed by the app.

HTTPS uses the Android system trust store and hostname verification. Certificate failures cannot be bypassed.

## HTTP warning

Arbitrary HTTP addresses are supported only as an explicit compatibility option. HTTP sends the Admin Key and management data without transport encryption. A network observer may read or modify that traffic.

Each HTTP server requires an explicit warning confirmation before first use. The login screen, saved server list, and connected system view continue to identify the connection as unencrypted. Local Keystore encryption cannot protect credentials while they travel over HTTP.

## Features

| Area | First release |
| --- | --- |
| Servers | Add, validate, delete, and quickly switch full-mode Manager Servers |
| Overview | Health, daily requests, success rate, tokens, estimated cost, interactive token/request trends, and real provider marks |
| Monitoring | Manual refresh, time/status filters, request details, and privacy-safe cached recent data |
| Usage | Interactive usage buckets, on-demand rankings, and privacy-safe Today/7-day/30-day share images |
| Operations | Manager/collector status, filtered paged logs, log clearing, and saved-server management |
| Settings | App lock, screenshot/address privacy, appearance, language, open-source notices, and signed in-app updates |
| Security | Keystore AES-GCM, optional biometric/device-credential app lock, configurable screenshot protection, no backup |
| Appearance | Blue-and-white light theme, navy dark theme, optional dynamic color, Simplified Chinese and English |

Destructive changes show the affected object and require confirmation. Switching servers cancels requests from the previous server and rebuilds screen state so cached data cannot cross profiles.

All Manager Server network screens use explicit manual refresh. Monitoring requests only the visible summary and event page; Usage computes only the selected ranking; Operations loads only the selected status or log section. Changing filters or categories never triggers a background request. A share image makes at most one explicit analytics request when the selected range cannot reuse loaded aggregate data.

The app does not manage providers, authentication files, quota cooldowns, or gateway client API keys. Use the CPA-Manager-Plus web interface for those administrative operations.

Update checks occur only when the user taps **Check for updates**. Releases are read from this repository's public GitHub Releases API with no embedded GitHub token and no Manager Admin Key. The system Download Manager downloads the APK, then the app verifies the published SHA-256 and requires the APK signing certificate to match the installed app before opening the Android installer.

## Build

Prerequisites:

- JDK 17
- Android SDK with API 36 platform and matching build tools
- Android Studio or a command-line Android SDK installation

Create `local.properties` with the SDK path when Gradle cannot discover it automatically:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

The APK is written under `app/build/outputs/apk/debug/`.

The complete local verification commands are:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Instrumentation tests require an API 26+ emulator or device:

```bash
./gradlew connectedDebugAndroidTest
```

## Release signing

Release signing is configured only through environment variables:

```text
CPAMP_KEYSTORE_PATH
CPAMP_KEYSTORE_PASSWORD
CPAMP_KEY_ALIAS
CPAMP_KEY_PASSWORD
```

When all four values are available, `assembleRelease` signs the APK. Without them, Gradle produces an unsigned release APK. Keystores, signing properties, and `local.properties` are ignored by Git.

GitHub Actions accepts the following repository secrets:

```text
CPAMP_KEYSTORE_BASE64
CPAMP_KEYSTORE_PASSWORD
CPAMP_KEY_ALIAS
CPAMP_KEY_PASSWORD
```

The workflow uploads debug and release APK artifacts. Its release artifact name explicitly includes `signed` or `unsigned`. Pull requests also run GitHub Dependency Review and reject newly introduced high-severity vulnerable dependencies.

Tags matching `v*` create a GitHub Release only when all signing secrets are present. The tag must match `versionName`; the release contains a deterministically named signed APK and its SHA-256 file. Missing or partial signing configuration blocks the release instead of publishing an unsigned installer.

## Security and privacy

- Admin Keys are encrypted separately from server metadata with Android Keystore AES-GCM.
- Enabling app lock migrates ciphertext to a user-authenticated Keystore key; disabling it migrates back and removes the obsolete key.
- Admin Keys are not written to Room, DataStore, saved-state Bundles, logs, crash uploads, or backups.
- Monitoring cache is isolated by server profile and removes request identifiers, account labels, paths, and failure summaries.
- Screenshots and recent-task previews are allowed by default and can be disabled from Settings.
- Shared usage images contain aggregate requests, success rate, tokens, cost, timeline and top-model data only. They exclude server names, addresses, keys, credentials and account labels.
- In-app updates accept only HTTPS assets with fixed release names, a matching SHA-256, and the installed application's signing identity. Android still requires explicit installer confirmation.
- The app includes no telemetry or third-party crash reporting.

See [SECURITY.md](SECURITY.md) for the reporting process and security boundaries.

## First-release boundaries

The app does not support lightweight mode, OAuth login flows, provider/auth-file/client-key management, plugin stores, raw YAML editing, model price management, inspection automation, backup migration, usage import/export, or Manager Server initial setup.

## License and attribution

CPAMP Mobile source code is licensed under the [MIT License](LICENSE). Third-party dependency attribution is listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

[CPA-Manager-Plus](https://github.com/seakee/CPA-Manager-Plus) is the upstream interoperable server project: "A self-hosted CPA / CLIProxyAPI management panel and AI gateway observability dashboard for requests, usage, cost, quota, failures, and account health." It is available under the MIT License, Copyright (c) 2026 Seakee. CPA-Manager-Plus is not bundled with this application.

Public Manager Server API contracts are implemented independently. The complete upstream MIT notice and provider-mark sources are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [LICENSE_COMPLIANCE.md](LICENSE_COMPLIANCE.md); the upstream notice is also available offline inside the app.