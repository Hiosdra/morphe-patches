# Hiosdra Patches

Personal, community-maintained patches compatible with Morphe.

## About

This repository is an independent project and is not authored by or affiliated
with the Morphe project. It publishes source code and Morphe patch bundles, not
modified APK files.

Use these patches only with applications you own or are authorized to modify.

The `dev` patch list contains five F1 TV patches: three enabled by default and
two optional standalone patches adapted from Morphe's universal patches. It
also contains Movie Paradise patches and an optional VesselFinder patch that
suppresses banner advertisements. The F1 TV patch sources now target version
`3.0.49.4-SP166.4.1-release-R54.2-mobile` and compile successfully on this
checkout; application of the `dev` bundle to that APK has not been verified.

## Add to Morphe

[Add Hiosdra Patches to Morphe](https://morphe.software/add-source?github=Hiosdra%2Fmorphe-patches)

You can also add the following GitHub URL manually in Morphe's patch source
manager:

```text
https://github.com/Hiosdra/morphe-patches
```

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.4.0-dev.4](https://github.com/Hiosdra/morphe-patches/releases/tag/v1.4.0-dev.4)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;9 patches total
<details open>
<summary>📦 F1 TV&nbsp;&nbsp;•&nbsp;&nbsp;5 patches</summary>
<br>

**🎯 Supported versions:**

| 3.0.49.4-SP166.4.1-release-R54.2-mobile |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [F1 TV - Background playback](#f1-tv-background-playback) | Keeps the F1 TV player alive when the activity goes to the background or the screen turns off. |  |
| [F1 TV - Change package name](#f1-tv-change-package-name) | Changes the F1 TV package name to allow installing a separate patched instance. By default ".morphe" is appended to the package name. | • Package name<br>• Update permissions<br>• Update providers |
| [F1 TV - Disable Play Store updates](#f1-tv-disable-play-store-updates) | Disables Play Store updates for the F1 TV package by setting its version code to the maximum allowed. |  |
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
<summary>📦 VesselFinder&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 6.6.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [VesselFinder - Disable advertisements](#vesselfinder-disable-advertisements) | Prevents the VesselFinder advertisement plugin from creating or showing banner ads. |  |

</details>

<!-- PATCHES_END -->

#### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=Hiosdra%2Fmorphe-patches

Or manually add this repository URL as a patch source in Morphe: https://github.com/Hiosdra/morphe-patches

### 🛠️ Building

To build Hiosdra Patches, follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

The current F1 TV target is `com.formulaone.production` version
`3.0.49.4-SP166.4.1-release-R54.2-mobile` (versionCode `30494002`).

## 📜 License

Hiosdra Patches is licensed under the [GNU General Public License v3.0](LICENSE).
