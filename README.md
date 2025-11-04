# Hide Google Voice Messaging

LSPosed module to hide the messaging functionality from Google Voice, keeping only calls and voicemail.

## Features

- ✅ Hides Messages tab from bottom navigation (no flickering)
- ✅ Blocks message notifications
- ✅ Blocks message toasts/banners
- ✅ Keeps Calls and Voicemail fully functional
- ✅ Multi-layer blocking for seamless experience

## Requirements

- Rooted Android device
- [LSPosed Framework](https://github.com/LSPosed/LSPosed) installed
- Google Voice app (official Play Store version)

## Installation

1. Download the latest APK from [Releases](../../releases)
2. Install the APK on your device
3. Open LSPosed Manager
4. Go to **Modules** tab
5. Enable **Hide Voice Messaging**
6. Tap the module and check **Google Voice** in scope
7. **Reboot** your device

## Verification

After reboot, open Google Voice. You should only see:
- Calls tab
- Voicemail tab
- Settings

The Messages tab will be completely hidden.

## Building from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/HideGoogleVoiceMessaging.git
cd HideGoogleVoiceMessaging

# Build the APK
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

## How It Works

The module uses multiple layers of protection:

1. **Pre-hiding** - Hides views before they attach to the window
2. **Measure blocking** - Prevents message views from being measured
3. **Draw blocking** - Prevents message views from being rendered
4. **View interception** - Blocks views with message-related content from being added
5. **Continuous monitoring** - Checks every 50ms to ensure views stay hidden
6. **Notification blocking** - Prevents messaging notifications

## Troubleshooting

**Messages tab still appears:**
- Ensure the module is enabled in LSPosed
- Verify Google Voice is selected in module scope
- Make sure you rebooted after enabling

**Check logs:**
```bash
adb logcat | grep HideVoiceMsg
```

You should see entries like:
```
HideVoiceMsg: Loaded into Google Voice
HideVoiceMsg: Hooked View.onAttachedToWindow
HideVoiceMsg: Pre-hid message view before attach: Messages
```

## License

MIT License - See [LICENSE](LICENSE) file for details

## Disclaimer

This module is for personal use only. Use at your own risk.
