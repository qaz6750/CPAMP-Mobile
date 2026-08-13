# Third-Party Notices

CPAMP Mobile uses open-source Android and JVM libraries. Dependency versions are pinned in `gradle/libs.versions.toml`; resolved transitive dependencies and their licenses should be reviewed for each release.

| Component | Project | License |
| --- | --- | --- |
| AndroidX Core, Activity, Lifecycle, Navigation, Room, DataStore, Biometric, AppCompat, Test | Android Open Source Project / AndroidX | Apache License 2.0 |
| Jetpack Compose and Material 3 | Android Open Source Project / AndroidX | Apache License 2.0 |
| Dagger and Hilt | Google | Apache License 2.0 |
| Kotlin and kotlinx.coroutines / serialization | JetBrains | Apache License 2.0 |
| Retrofit | Square | Apache License 2.0 |
| OkHttp and MockWebServer | Square | Apache License 2.0 |
| [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) and Shapes | Kyant | Apache License 2.0 |
| JUnit 4 | JUnit contributors | Eclipse Public License 1.0 |
| [CPA-Manager-Plus](https://github.com/seakee/CPA-Manager-Plus) | Seakee | MIT License |
| [Simple Icons](https://github.com/simple-icons/simple-icons) | Simple Icons contributors | CC0-1.0 project; individual icon and trademark terms still apply |

This notice is informational and does not replace the license text distributed by each dependency. Release distributors are responsible for preserving notices required by the exact resolved dependency set.

## CPA-Manager-Plus

[CPA-Manager-Plus](https://github.com/seakee/CPA-Manager-Plus) is "A self-hosted CPA / CLIProxyAPI management panel and AI gateway observability dashboard for requests, usage, cost, quota, failures, and account health."

CPA-Manager-Plus is licensed under the MIT License. The complete notice distributed with CPAMP Mobile is:

```text
MIT License

Copyright (c) 2026 Seakee

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

The complete upstream license is available in the [CPA-Manager-Plus repository](https://github.com/seakee/CPA-Manager-Plus/blob/main/LICENSE).

CPA-Manager-Plus is an upstream interoperable server project and is not bundled into this application. CPAMP Mobile is an independent, unofficial client and is not affiliated with or endorsed by Seakee or CPA-Manager-Plus.

## Provider marks

Provider marks are included only to identify the service associated with a model. They are not CPAMP Mobile branding and do not imply affiliation, sponsorship, or endorsement. All provider names and marks remain the property of their respective owners.

| Mark | Local resource | Source reviewed 2026-07-27 | Notes |
| --- | --- | --- | --- |
| OpenAI Blossom | `ic_provider_openai.xml` | [OpenAI Brand Guidelines](https://openai.com/brand/) and the official `Blossom_Light.svg` asset | Used only for OpenAI-related models, with the original path and proportions. OpenAI trademark terms apply. |
| Anthropic | `ic_provider_anthropic.xml` | [Simple Icons Anthropic](https://simpleicons.org/icons/anthropic.svg), sourced by Simple Icons from Anthropic | Simple Icons project CC0 notice applies; Anthropic trademark rights are not licensed by CC0. |
| Google Gemini | `ic_provider_gemini.xml` | [Simple Icons Google Gemini](https://simpleicons.org/icons/googlegemini.svg), sourced from Gemini | Google trademark and brand terms apply. |
| DeepSeek | `ic_provider_deepseek.xml` | [Simple Icons DeepSeek](https://simpleicons.org/icons/deepseek.svg), sourced from DeepSeek | DeepSeek trademark rights remain with DeepSeek. |
| Qwen | `ic_provider_qwen.xml` | [Simple Icons QWen](https://simpleicons.org/icons/qwen.svg), sourced from Qwen | Qwen trademark rights remain with its owner. |
| xAI | Text mark rendered as `xAI` | [xAI Brand Guidelines](https://x.ai/legal/brand-guidelines) | The official logo archive was unavailable to the automated retrieval environment; the accurate company name is used instead of the unrelated X social-platform logo. xAI trademark terms apply. |

Simple Icons' CC0 notice expressly does not waive or license third-party trademark rights, and the Simple Icons disclaimer notes that individual icon license data must be considered separately. CPAMP Mobile uses these marks nominatively in a compact model-provider list and will update or remove a mark if its owner requires it.