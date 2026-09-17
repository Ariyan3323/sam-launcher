# Sam Launcher — Canonical Project Context

This file is the canonical handoff for future development sessions. Always inspect it and the working tree before changing Sam Launcher. Work in `/home/ubuntu/sam-launcher`; do not clone or reset the repository over existing work.

## Product direction

Sam is a Persian-first Android assistant and optional launcher. It should combine direct conversation, multilingual installed-app resolution, local device tools, optional Gemini/OpenAI providers, opt-in web knowledge, encrypted local memory, and explicit permission gates for sensitive data. Do not claim that a deterministic fallback is a full language model.

## Current build

Build the installable OSS debug APK with:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/ubuntu/Android/Sdk
./gradlew :app:testOssDebugUnitTest :app:assembleOssDebug
```

Artifact:

```text
app/build/outputs/apk/oss/debug/app-oss-debug.apk
```

Canonical application ID:

```text
com.fgmembers.samlauncher
```

## Implemented areas

- Encrypted runtime provider keys in `agent/SecureAiSettings.kt`.
- Local/Web/Cloud mode selection and Gemini/OpenAI provider selection.
- Provider changes and key entry activate Cloud mode and restart settings state.
- OpenAI and Gemini error messages are surfaced instead of being silently replaced by local answers.
- `agent/ConversationCore.kt` provides a deterministic offline conversational fallback in Persian and English.
- `agent/OfflineKnowledgeStore.kt` stores approved general Q&A locally with AES-GCM/Android Keystore, bounded retention, and sensitive-topic exclusion in `AssistantActivity`.
- `agent/IntentRouter.kt` and `agent/SemanticAppSearch.kt` support multilingual and fuzzy app commands.
- Natural commands such as `برنامه مدیا پلیر رو باز کن` and `موزیک پخش کن` are routed through app/action intents.
- SMS and notification reading are consent-gated; sensitive content is not sent to cloud providers by default.
- Privacy/guest modes and regression tests exist.
- `SIGNING.md` documents application identity and signing requirements.

## Important behavior and limitations

- Saving a provider key does not prove that the provider is reachable; model name, quota, network, and provider policy can still fail.
- Gemini/OpenAI keys must never be pasted into chat, source files, Git, screenshots, or logs. Revoke any key exposed in chat.
- Local knowledge caching is retrieval memory, not training or changing provider model weights.
- Never hide a Cloud provider failure behind a misleading local response.
- Never read Gmail, SMS, Telegram, or notifications without explicit user consent and platform-appropriate access.
- Do not put private signing keystores in the repository. A signing mismatch cannot be fixed by code; the old app must be uninstalled once or the original signing key must be used.
- Do not publish, upload, or perform external live actions without explicit user approval.

## Change protocol for future sessions

1. Read this file and run `git status --short`.
2. Inspect the existing implementation before editing; preserve uncommitted work.
3. Make one focused change at a time.
4. Add or update tests for behavior changes.
5. Run the Gradle test/build command above.
6. Verify the APK path and report the exact result.
7. Update this file when a material capability or build detail changes.
