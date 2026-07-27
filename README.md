# CPAMP Mobile

CPAMP Mobile is a native Android administration client for a configured CPA-Manager-Plus Manager Server. It is built with Kotlin, Jetpack Compose, Material 3, Hilt, Retrofit/OkHttp, Kotlin Serialization, Room, DataStore, and Android Keystore.

> [!IMPORTANT]
> CPAMP Mobile is an independent, unofficial client. It is not affiliated with, endorsed by, or maintained by seakee or the CPA-Manager-Plus project. CPA-Manager-Plus names and upstream project references identify interoperability only.

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
| Overview | Health, daily requests, success rate, tokens, estimated cost, trends, and failures |
| Monitoring | Lifecycle-aware request polling, time/status/search filters, details, and cached recent data |
| Providers | List and common add/edit/delete operations with unknown-field preservation |
| Auth Files | List, enable/disable, import, edit, overwrite, and delete JSON files |
| Quotas | View and refresh active quota cooldowns |
| Client API Keys | Add, mask, and delete gateway client keys |
| System | Manager/collector status, filtered paged logs, log clearing, and server management |
| Security | Keystore AES-GCM, optional biometric/device-credential app lock, blocked screenshots, no backup |
| Appearance | System/light/dark themes, dynamic color, Simplified Chinese and English |

Destructive changes show the affected object and require confirmation. Switching servers cancels requests from the previous server and rebuilds screen state so cached data cannot cross profiles.

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

## Security and privacy

- Admin Keys are encrypted separately from server metadata with Android Keystore AES-GCM.
- Enabling app lock migrates ciphertext to a user-authenticated Keystore key; disabling it migrates back and removes the obsolete key.
- Admin Keys are not written to Room, DataStore, saved-state Bundles, logs, crash uploads, or backups.
- Monitoring cache is isolated by server profile and removes request identifiers, account labels, paths, and failure summaries.
- The app includes no telemetry or third-party crash reporting.

See [SECURITY.md](SECURITY.md) for the reporting process and security boundaries.

## First-release boundaries

The first release does not support lightweight mode, OAuth login flows, plugin stores, raw YAML editing, model price management, inspection automation, backup migration, usage import/export, or Manager Server initial setup. “Account management” refers to Providers, Auth Files, quotas, and their server-reported status; the app does not invent a separate user system.

## License and attribution

CPAMP Mobile source code is licensed under the [MIT License](LICENSE). Third-party dependency attribution is listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

No source code or protected visual assets are copied from reference mobile clients. Public architecture and mobile information-design ideas are implemented independently.