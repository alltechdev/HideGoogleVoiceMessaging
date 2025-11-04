# Hide Google Voice Messaging

LSPosed module to hide the messaging functionality from Google Voice, keeping only calls and voicemail.

## Features

- Hides Messages tab from bottom navigation (no flickering)
- Blocks message notifications
- Blocks message toasts/banners
- Keeps Calls and Voicemail fully functional
- Multi-layer blocking for seamless experience

## Requirements

- Rooted Android device
- [LSPosed Framework](https://github.com/LSPosed/LSPosed) installed
- Google Voice app (working on version 2025.10.19.826039865)

## Installation

1. Download the latest APK from [Releases](../../releases)
2. Install the APK on your device
3. Open LSPosed Manager
4. Go to **Modules** tab
5. Enable **Hide Voice Messaging**
6. Tap the module and check **Google Voice** in scope
7. **Reboot** your device

## Building from Source

```bash
# Clone the repository
git clone https://github.com/alltechdev/HideGoogleVoiceMessaging.git
cd HideGoogleVoiceMessaging

# Build the APK
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

## How It Works

- Hides views before they attach to the window

- Prevents message views from being measured

- Prevents message views from being rendered

- Blocks views with message-related content from being added

- Checks every 50ms to ensure views stay hidden

- Prevents messaging notifications
