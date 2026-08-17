package io.github.hiosdra.patches

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal const val VESSEL_FINDER_PACKAGE = "com.astrapaging.vff"
internal const val VESSEL_FINDER_VERSION = "6.6.0"
internal const val VESSEL_FINDER_VERSION_CODE = 660

internal val COMPATIBILITY_VESSEL_FINDER = Compatibility(
    name = "VesselFinder",
    packageName = VESSEL_FINDER_PACKAGE,
    description = "VesselFinder mobile APK",
    apkFileType = ApkFileType.APK,
    targets = listOf(
        AppTarget(
            version = VESSEL_FINDER_VERSION,
            minSdk = 29,
            description = "versionCode $VESSEL_FINDER_VERSION_CODE",
        ),
    ),
)
