# Hiosdra Patches

Personal, community-maintained patches compatible with Morphe.

## About

This repository is an independent project and is not authored by or affiliated
with the Morphe project. It publishes source code and Morphe patch bundles, not
modified APK files.

Use these patches only with applications you own or are authorized to modify.

The published bundle contains three F1 TV patches enabled by default. They have
been applied and rebuilt successfully against the supported APK version listed
below. The no-op build check remains available for repository smoke testing.

## Add to Morphe

[Add Hiosdra Patches to Morphe](https://morphe.software/add-source?github=Hiosdra%2Fmorphe-patches)

You can also add the following GitHub URL manually in Morphe's patch source
manager:

```text
https://github.com/Hiosdra/morphe-patches
```

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.4.0-dev.2](https://github.com/Hiosdra/morphe-patches/releases/tag/v1.4.0-dev.2)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;7 patches total
<details open>
<summary>📦 F1 TV&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 3.0.48.1-SP157.6.0-release-R52-mobile |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [F1 TV - Background playback](#f1-tv-background-playback) | Keeps the F1 TV player alive when the activity goes to the background or the screen turns off. |  |
| [F1 TV - Foreground playback service](#f1-tv-foreground-playback-service) | Keeps background F1 TV playback alive with an Android media playback notification and playback/PiP controls. |  |
| [F1 TV - Picture-in-Picture](#f1-tv-picture-in-picture) | Keeps F1 TV playback alive while entering Android Picture-in-Picture mode. |  |

</details>

<details open>
<summary>📦 Movie Paradise&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 5.2.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Movie Paradise - Force RevenueCat entitlement (experimental)](#movie-paradise-force-revenuecat-entitlement-experimental) | Forces RevenueCat entitlements active. Experimental: premium is server-authoritative, so this likely unlocks nothing. |  |
| [Movie Paradise - GmsCore support (microG login)](#movie-paradise-gmscore-support-microg-login) | Routes Google Play Services through microG (MicroG-RE) so Google sign-in works without stock Play Services. |  |
| [Movie Paradise - PairIP license bypass](#movie-paradise-pairip-license-bypass) | Neutralises Google Play integrity/license checks (PairIP) so a repackaged build launches. |  |

</details>

<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Compile-only example](#compile-only-example) | Provides a no-op patch for validating the project build. |  |

</details>

<!-- PATCHES_END -->

#### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=Hiosdra%2Fmorphe-patches

Or manually add this repository URL as a patch source in Morphe: https://github.com/Hiosdra/morphe-patches

### 🛠️ Building

To build Hiosdra Patches, follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

The current F1 TV target is `com.formulaone.production` version
`3.0.48.1-SP157.6.0-release-R52-mobile` (versionCode `30481000`).

## 📜 License

Hiosdra Patches is licensed under the [GNU General Public License v3.0](LICENSE).
