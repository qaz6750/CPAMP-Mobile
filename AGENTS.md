# Project Guidelines

## Language and Scope

- Write source code, documentation, code comments, pull request text, and Git commit messages in English.
- Use another language only for localized resources or when the user explicitly requests it.
- Keep changes focused on the requested behavior. Do not refactor unrelated code or reformat untouched files.

## Architecture

- This is a native Android application using Kotlin, Jetpack Compose, Material 3, Hilt, Retrofit/OkHttp, Room, and DataStore.
- Preserve the existing flow from Compose screens to Hilt ViewModels, repositories, and data sources. UI code must not call remote or persistence APIs directly.
- Keep cached data isolated by server profile. Never persist account labels, credential file names, authentication indexes, raw provider responses, or request-event details.
- Account management is read-only. Do not add provider, credential, quota cooldown, or gateway key mutation without an explicit product requirement.

## Kotlin and Compose

- Target Java 17 and follow the Kotlin style already used in the surrounding file.
- Prefer existing theme tokens and shared components over hard-coded styling or new abstractions.
- Keep composables small and state-hoisted. Business logic belongs in ViewModels, repositories, or domain helpers rather than UI functions.
- Design for compact mobile screens, larger font scales, light and dark themes, and stable layouts with no clipped or overlapping content.
- Use Material or existing project icons instead of custom vector drawings when an appropriate icon exists.

## Localization and Accessibility

- Put user-facing text in string resources; do not hard-code it in Kotlin.
- Keep English and Simplified Chinese resources synchronized when adding or changing user-facing text.
- Provide content descriptions for actionable icons. Decorative icons should use a null content description.
- Preserve touch target sizes, readable contrast, and meaningful loading, empty, error, and disabled states.

## Security and Privacy

- Never log, cache, expose, or commit Admin Keys, credentials, signing secrets, server addresses marked private, or raw sensitive responses.
- Preserve Android Keystore protection, HTTPS certificate and hostname verification, screenshot privacy controls, and the no-telemetry policy.
- Do not weaken HTTP warnings, update signature/hash verification, profile isolation, or destructive-action confirmations.
- Do not commit `local.properties`, keystores, generated APKs, or build output.

## Build and Verification

- Use the Gradle wrapper from the repository root.
- Prefer the narrowest relevant check first, then broaden verification according to risk.
- Unit tests: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Debug APK: `./gradlew assembleDebug`
- Full local verification: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`
- Instrumentation tests require an API 26 or newer emulator/device: `./gradlew connectedDebugAndroidTest`
- Report checks that were not run and the reason they were skipped.

## Git

- Use Conventional Commits with an English subject: `type(scope): subject`.
- Choose the narrowest relevant type and scope, for example `style(accounts): refine account detail cards`.
- Keep the subject concise, lowercase, and imperative. Do not end it with a period.
- Commit only files that belong to the current task. Do not include unrelated worktree changes.
- Do not rewrite, discard, or overwrite user changes, and do not push unless explicitly requested.
