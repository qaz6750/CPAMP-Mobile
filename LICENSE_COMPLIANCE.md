# License Compliance Record

Reviewed: 2026-07-27

This file records engineering evidence and distribution decisions. It is not legal advice.

## CPAMP Mobile

- The project root `LICENSE` is the MIT License, Copyright (c) 2026 CPAMP Mobile contributors.
- The project's own copyright notice remains separate from upstream notices.
- Source and binary distributions preserve the notices listed in `THIRD_PARTY_NOTICES.md`.

## CPA-Manager-Plus

- Upstream: <https://github.com/seakee/CPA-Manager-Plus>
- Upstream license checked on 2026-07-27: MIT License, Copyright (c) 2026 Seakee.
- CPAMP Mobile is an independent, unofficial Android client. It does not bundle the CPA-Manager-Plus server or web application.
- The Android DTOs and Retrofit declarations implement public JSON and HTTP interoperability contracts such as `/v0/management/dashboard/summary` and `/v0/management/monitoring/analytics`. They do not copy the upstream React UI implementation.
- The complete upstream MIT text is preserved in `THIRD_PARTY_NOTICES.md` and `app/src/main/res/raw/cpa_manager_plus_license.txt`. The latter is available offline from Settings > About and updates > Open-source licenses.
- README and in-app copy do not claim affiliation, endorsement, or ownership of CPA-Manager-Plus.

## Source Review

- Repository source was searched for `Copyright`, `Seakee`, `CPA-Manager-Plus`, and upstream package paths.
- No upstream copyright header was found in Android Kotlin source.
- Public endpoint names, serialized field names, response examples, and compatible data structures are treated as interoperability facts. No upstream web component, stylesheet, generated bundle, screenshot, or logo is included.
- If a future change copies a substantial upstream implementation, its file and origin must be recorded here and the complete upstream MIT notice must remain distributed.

## Provider Marks

- Provider marks identify the vendor associated with a model and are not used as the CPAMP Mobile application icon or product name.
- OpenAI uses an unmodified path from the official OpenAI brand asset and follows the OpenAI brand guideline's direct-service identification condition.
- Anthropic, Google Gemini, DeepSeek, and Qwen paths come from Simple Icons. Simple Icons is distributed under CC0, while its disclaimer states that individual icon license data and third-party trademark rights require separate consideration.
- xAI uses the accurate `xAI` text mark because the official logo archive could not be retrieved in this environment and the Simple Icons `x` asset represents the unrelated X social platform.
- Sources, retrieval date, local filenames, and trademark qualifications are listed in `THIRD_PARTY_NOTICES.md`.

## Dependency and Release Controls

- Direct dependency families and licenses are listed in `THIRD_PARTY_NOTICES.md`; exact resolved transitive licenses should be reviewed for each release.
- Pull requests run GitHub Dependency Review for newly introduced high-severity vulnerabilities.
- A GitHub Release requires a signed APK and publishes a SHA-256 file. The app verifies both SHA-256 and signing-certificate continuity before invoking the Android installer.

## Conclusion

Based on the repository evidence reviewed above, CPAMP Mobile preserves the CPA-Manager-Plus MIT copyright and permission notice and does not represent upstream work as its own. The current distribution design meets the MIT notice-preservation condition. Provider trademarks remain governed by their owners' separate terms and are not covered by the upstream MIT license.