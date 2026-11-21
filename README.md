# Smart AI Auto Reply

A privacy-first Android application built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, Android Keystore, and Android `NotificationListenerService`.

## Features

- **Zero Login / 100% On-Device Storage**: Opens directly to the dashboard. Message logs, contact rules, categories, commands, smart actions, and AI Chat threads are stored locally in Room.
- **Multi-App Notification Auto Reply**: Detects incoming messaging notifications from WhatsApp, Facebook Messenger, IMO, Telegram, Instagram, SMS, Signal, Discord, and other compatible messaging apps using official Android `RemoteInput` notification reply actions. Apps without inline reply actions are clearly flagged.
- **Android Keystore-Encrypted AI Settings**: Supports Groq (`gsk_...`), OpenRouter, OpenAI, Together AI, Gemini OpenAI-compatible, and custom OpenAI-compatible endpoints. API keys are encrypted locally using `AndroidKeyStore` (`AES/GCM/NoPadding`) with live model discovery and a **Test API Connection** button.
- **Category & Contact Intelligence**: Includes 8 default categories plus custom categories (instructions, reply style, greeting, response language, enable/disable) and per-contact category/memory/instruction overrides.
- **Custom Command System**: Create exact or keyword triggers (e.g., `/hello`, `/location`, `/busy`) with optional priority over AI responses.
- **Smart Automatic Actions**: Time, Date, Busy Mode, Custom Actions, and consent-gated Approximate Location sharing (`ACCESS_COARSE_LOCATION`) that always requires explicit user confirmation before enabling.
- **AI Chat Assistant**: Persistent multi-session AI chat with configurable model & personality and an option to pin a session as active context for future automatic replies.
- **Message Management Dashboard**: Search contacts/messages, filter by app or status, edit AI drafts with 1-tap tone rewrite chips (`Default`, `Shorter`, `Polite`, `Casual`), mute auto-replies per contact, export/share conversation transcripts, send manual inline replies, or wipe all local history.
- **Smart Safeguards & Quiet Hours**: Configure per-contact reply cooldowns, group chat auto-reply filtering, and overnight/daytime Quiet Hours with a custom fallback auto-reply message.
- **Reply Pipeline Sandbox & Analytics**: Dry-run incoming messages safely against your Commands, Smart Actions, Quiet Hours, Group safeguards, and AI Category rules without modifying message logs, plus view live reply-source analytics on the Home Dashboard.

## Setup Instructions

1. Open the project in Android Studio or build via Gradle (`gradle assembleDebug`).
2. Launch **Smart AI Auto Reply** on Android (API 24+).
3. Go to **Home -> Access / Permissions Setup** and grant **Notification Listener Access**.
4. Open **Supported Apps** and toggle ON the messaging apps you want to automate.
5. Go to **Settings -> AI API Configuration**, choose a provider (e.g., Groq), paste your API key, and tap **Test API Connection**.
6. Toggle **Auto Reply Active** ON from the Home Dashboard.

## Testing

Run local JVM and Robolectric unit/UI tests with:

```bash
gradle :app:testDebugUnitTest
```

## Building APK via GitHub Actions

This repository includes a ready-to-run workflow in `.github/workflows/build-apk.yml`:

1. Push the project to a GitHub repository (`main` or `master` branch).
2. Open the **Actions** tab in GitHub (you can also trigger **Build Android APK** manually with **Run workflow**).
3. Once the workflow finishes, download **`smart-ai-auto-reply-debug-apk`** from the **Artifacts** section.
4. *(Optional Release APK)* Add `KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` (for key alias `upload`) under **Settings -> Secrets and variables -> Actions** to automatically produce a signed release APK (`smart-ai-auto-reply-release-apk`).

