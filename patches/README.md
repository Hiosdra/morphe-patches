# F1 TV Morphe Patches

This repository contains Morphe patches for the F1 TV Android app (`com.formulaone.production`).

## 📋 Available Patches

### 1. F1 TV - Picture-in-Picture Support
**File:** `F1TvPictureInPicturePatch.kt`  
**Target:** `BasePlayerActivity` (Bitmovin player)

Enables Picture-in-Picture mode for the standard Bitmovin player by:
- Moving player pause logic from `onPause()` to `onStop()` - critical because Android calls `onPause()` when entering PiP but the player should continue playing
- Adding `onPictureInPictureModeChanged()` to hide/show UI controls during PiP
- Adding `onUserLeaveHint()` for manual PiP entry on Android 10/11 (API 29-30)
- Adding `buildPipParams()` to configure PiP aspect ratio (16:9) and source rect hint
- Adding `canEnterPip()` helper to check eligibility (playing, not casting, PiP supported)
- Adding `getPipSourceView()` and `setPipUiVisible()` to `PlayerLayoutHolder` interface/implementation

**⚠️ Manifest Changes Required:**
The following must be added to `BasePlayerActivity` in the APK manifest (requires resource patch or manual APK modification):
```xml
<activity
    android:name="com.avs.p020f1.p022ui.player.BasePlayerActivity"
    android:supportsPictureInPicture="true"
    android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|keyboardHidden"
    android:launchMode="singleTop"
    ... />
```

### 2. F1 TV - Background Audio Playback
**File:** `F1TvBackgroundPlaybackPatch.kt`  
**Target:** `BasePlayerActivity` + Extension (`f1tv-background-playback.mpe`)

Enables audio-only playback when screen is off by:
- Moving player pause logic from `onPause()` to `onStop()` (shared with PiP patch)
- Adding background playback setting check in `onStop()` - if enabled, detaches video output but keeps audio playing
- Adding `detachVideoOutput()` / `attachVideoOutput()` to `PlayerSwitcher` interface/implementation
- Adding MediaSession initialization for lock screen / notification controls
- Using companion extension for foreground service with MediaSessionService

**🔧 Companion Extension Required:**
Full background playback requires the `f1tv-background-playback.mpe` extension which provides:
- `BackgroundPlaybackService` - MediaSessionService running as foreground service (mediaPlayback type)
- `BitmovinPlaybackEngine` - Wraps Bitmovin Player as Media3 Player interface
- Audio focus handling, wake lock management, notification controls

**Settings:**
The patch reads `background_playback_enabled` from SharedPreferences (`f1tv_preferences`). Add a toggle in the app's settings UI.

## 🚀 Building

```bash
# Build patches (.mpp file)
./gradlew :patches:buildAndroid

# Build extension (.mpe file)
./gradlew :extensions:extension:buildExtension

# Build both
./gradlew build
```

Outputs:
- `patches/build/libs/patches-*.mpp` - Patch bundle
- `extensions/extension/build/outputs/mpe/f1tv-background-playback.mpe` - Extension

## 📱 Installation in Morphe

1. Add this repository as a patch source in Morphe:
   - URL: `https://github.com/hiosdra/hiosdra-patches`
   - Or use the one-click link: `https://morphe.software/add-source?github=hiosdra/hiosdra-patches`

2. Enable desired patches in Morphe's patch list

3. For background playback, also install the extension:
   - The extension will be automatically downloaded when the patch is enabled

## 🎯 Target App Details

- **Package:** `com.formulaone.production`
- **Version:** 3.0.48.1-SP157.6.0-release-R52-mobile
- **Version Code:** 30481000
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 35 (Android 15)

## ⚖️ Legal Notice

> **These patches are intended only for applications that you own or are authorized to modify.**

F1 TV is a commercial service by Formula One. Only use these patches if:
- You have a valid F1 TV subscription
- You are modifying your own installed app for personal use
- You comply with F1 TV's Terms of Service and applicable laws

The patches do not bypass DRM, authentication, or subscription checks. They only modify playback behavior (PiP, background audio) for content you're already authorized to access.

## 🔧 Technical Details

### Architecture

The F1 TV app uses two separate player implementations:
1. **Bitmovin Player** (`BasePlayerActivity`) - Standard live/VOD playback with dual PlayerView for seamless channel switching
2. **Tiledmedia/ClearVR** (`TiledPlayerActivity`) - Multiview (multiple onboard cameras)

These patches target only the Bitmovin player path (`BasePlayerActivity`).

### Key Classes Patched

| Class | Package | Purpose |
|-------|---------|---------|
| BasePlayerActivity | com.avs.p020f1.p022ui.player | Main player Activity |
| PlayerSwitcherImpl | com.avs.p020f1.interactors.playback | Manages dual PlayerView, DRM, channel switching |
| PlayerLayoutHolderImpl | com.avs.p020f1.p022ui.player | UI layout and controls |
| PlayerLayoutHolder | com.avs.p020f1.p022ui.player | Interface for layout holder |

### Bytecode Patching Strategy

Using Morphe's `bytecodePatch` DSL with ASM:
- `editMethod()` - Modify existing method bytecode
- `removeMethodCall()` - Remove specific method invocations
- `insertCodeAtStart()` / `insertCodeBeforeReturn()` - Inject new bytecode
- `addMethod()` - Add new methods
- `addField()` - Add new fields

## 📝 Version Compatibility

| F1 TV Version | Patch Version | Status |
|---------------|---------------|--------|
| 3.0.48.1 (30481000) | 1.0.0 | ✅ Tested |
| 3.0.x | 1.0.x | ⚠️ May need updates |

Patches use fingerprinting to target specific version codes. Update `fingerprint` block when F1 TV updates.

## 🐛 Known Limitations

1. **PiP Manifest Flag** - `android:supportsPictureInPicture="true"` must be added to manifest (requires resource patch or manual APK modification)

2. **Background Playback** - Requires extension (`.mpe`) for foreground service. Bytecode patches alone cannot create services.

3. **Multiview (Tiledmedia)** - Not supported. TiledPlayerActivity has different architecture with `suspendPlaybackOnPause()`.

4. **DRM/License** - Background playback uses ExoPlayer in extension for audio-only. Widevine license handling is complex; may need additional work for long sessions.

5. **Channel Switching in PiP** - Dual PlayerView switching may cause visual artifacts in PiP. Test thoroughly.

## 📚 References

- [Morphe Patcher Documentation](https://github.com/MorpheApp/morphe-patcher/tree/main/docs)
- [Android PiP Documentation](https://developer.android.com/develop/ui/views/picture-in-picture)
- [Media3 MediaSessionService](https://developer.android.com/media/media3/session/background-playback)
- [Bitmovin Player Android SDK](https://bitmovin.com/docs/player/sdks/android-sdk)

## 🤝 Contributing

1. Follow [Morphe development setup](https://github.com/MorpheApp/morphe-documentation/blob/main/docs/morphe-development/README.md)
2. Use semantic commit messages (`feat:`, `fix:`, `chore:`)
3. Test on target F1 TV version before submitting PR
4. Update fingerprint when F1 TV updates

## 📄 License

GPLv3 - See [LICENSE](../LICENSE)