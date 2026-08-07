# DuckMailPerfect

A polished native Android client for the DuckMail API.

## Features

- Material 3 UI with dynamic Android colors
- Multiple saved mailboxes
- One-tap mailbox switching
- Create random disposable mailboxes
- Optional API key for private domains
- Mailbox expiry: 24h / 3d / 7d / never
- Auto-refresh: 10 / 15 / 30 / 60 seconds
- Pull-to-refresh style refresh action
- Unread counters
- Search sender/subject
- Open messages and mark as read
- Delete messages
- Delete the remote mailbox
- Copy address/password/message
- Attachment list with browser opening
- Session persistence
- GitHub Actions APK build
- No Retrofit/OkHttp dependency; HTTPS API calls use the Android platform

## GitHub build

Upload the repository as-is. GitHub Actions is already configured.

1. GitHub -> Actions
2. Select `Build DuckMail APK`
3. Run workflow
4. Download `DuckMail-debug-apk` from Artifacts

The workflow uses the GitHub-hosted Ubuntu runner, JDK 17, the installed Gradle toolchain,
and uploads the generated APK as an artifact.

The app uses the official DuckMail API base:
https://api.duckmail.sbs

API reference:
https://www.duckmail.sbs/en/api-docs
