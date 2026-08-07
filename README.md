# DuckMail Android

A native Android client for the DuckMail API.

## Features

- Material 3 Compose UI with Android 16-friendly dynamic theming
- Dynamic Android theme colors
- Create random DuckMail inboxes
- Choose from public DuckMail domains
- Login to an existing mailbox
- Inbox refresh
- Open and mark messages as read
- Delete messages
- Copy email/message text
- Local session persistence
- No WebView and no third-party HTTP library
- GitHub Actions APK build included

## API

Base URL:

https://api.duckmail.sbs

Official API docs:

https://www.duckmail.sbs/en/api-docs

The app follows the documented endpoints for domains, accounts, token authentication and messages.

## Build for free on GitHub

1. Create a new GitHub repository.
2. Upload this entire project.
3. Open the **Actions** tab.
4. Select **Build DuckMail APK**.
5. Click **Run workflow**.
6. When it finishes, open the workflow run.
7. Download the artifact named **DuckMail-debug-apk**.
8. Extract it and install `app-debug.apk` on Android.

The workflow uses the GitHub-hosted runner, JDK 17 and Gradle.

## Build on Android

Recommended options:

### AndroidIDE
Install AndroidIDE on the phone, import/open this project, allow Gradle sync, then run:

./gradlew assembleDebug

If the wrapper is not present, AndroidIDE can use its configured Gradle installation; alternatively run:

gradle assembleDebug

The APK will be:

app/build/outputs/apk/debug/app-debug.apk

### Termux
A full Android SDK + Gradle toolchain in Termux is possible but significantly heavier. For a phone-only workflow, GitHub Actions is the easier free build server.

## Important

This app is intended for testing and legitimate disposable-email use. Follow DuckMail's API rules and the laws/terms applicable to the services where the email is used.

## Notes

- Public account creation is used without an API key.
- The API supports optional `dk_...` API keys for private domains; this project does not hard-code a private key.
- Credentials are stored locally in Android SharedPreferences. For a production app, consider encrypted storage and a stronger credential lifecycle.
