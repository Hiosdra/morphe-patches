package io.github.hiosdra.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val FOREGROUND_SERVICE =
    "Lio/github/hiosdra/patches/extension/backgroundplayback/BackgroundPlaybackService;"

private const val FOREGROUND_SERVICE_NAME =
    "io.github.hiosdra.patches.extension.backgroundplayback.BackgroundPlaybackService"

private val f1TvForegroundServiceResourcePatch = resourcePatch(
    name = "F1 TV - Foreground service manifest",
    description = "Declares the media playback foreground service and required Android permissions.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_F1_TV)

    execute {
        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement
            listOf(
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
                "android.permission.POST_NOTIFICATIONS",
            ).forEach { permission ->
                val exists = (0 until root.childNodes.length)
                    .map { root.childNodes.item(it) }
                    .filterIsInstance<Element>()
                    .any { it.tagName == "uses-permission" && it.getAttribute("android:name") == permission }
                if (!exists) {
                    manifest.createElement("uses-permission").also {
                        it.setAttribute("android:name", permission)
                        root.appendChild(it)
                    }
                }
            }

            val application = (0 until root.childNodes.length)
                .map { root.childNodes.item(it) }
                .filterIsInstance<Element>()
                .firstOrNull { it.tagName == "application" }
                ?: error("F1 TV application node was not found in AndroidManifest.xml")

            val serviceExists = (0 until application.childNodes.length)
                .map { application.childNodes.item(it) }
                .filterIsInstance<Element>()
                .any { it.tagName == "service" && it.getAttribute("android:name") == FOREGROUND_SERVICE_NAME }
            if (!serviceExists) {
                manifest.createElement("service").also {
                    it.setAttribute("android:name", FOREGROUND_SERVICE_NAME)
                    it.setAttribute("android:exported", "false")
                    it.setAttribute("android:foregroundServiceType", "mediaPlayback")
                    application.appendChild(it)
                }
            }
        }
    }
}

/**
 * Optional companion to the lifecycle patch. It promotes the host process to
 * a mediaPlayback foreground service while the player activity is resumed.
 */
@Suppress("unused")
val f1TvForegroundServicePatch = bytecodePatch(
    name = "F1 TV - Foreground playback service",
    description = "Keeps background F1 TV playback alive with an Android media playback notification.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_F1_TV)
    dependsOn(f1TvBackgroundPlaybackPatch)
    dependsOn(f1TvForegroundServiceResourcePatch)
    extendWith("extensions/extension.mpe")

    execute {
        val onPause = mutableClassDefBy(BASE_PLAYER_ACTIVITY).methods.firstOrNull {
            it.name == "onPause" && it.parameterTypes.isEmpty()
        } ?: error("F1 TV BasePlayerActivity.onPause() was not found")

        // onResume can run before the player has reported PLAYING. Start the
        // service again at the lifecycle boundary where the user leaves the
        // player, so Home/PiP does not rely on a later resume callback.
        onPause.addInstructions(
            0,
            """
                invoke-virtual {p0}, $BASE_PLAYER_ACTIVITY->getPlayerSwitcher()$PLAYER_SWITCHER
                move-result-object v0
                invoke-interface {v0}, $PLAYER_SWITCHER->isPlaying()Z
                move-result v1
                if-eqz v1, :skip_service
                new-instance v1, Landroid/content/Intent;
                const-class v0, $FOREGROUND_SERVICE
                invoke-direct {v1, p0, v0}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
                invoke-virtual {p0, v1}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;
                move-result-object v1
                :skip_service
            """,
        )

        val onResume = mutableClassDefBy(BASE_PLAYER_ACTIVITY).methods.firstOrNull {
            it.name == "onResume" && it.parameterTypes.isEmpty()
        } ?: error("F1 TV BasePlayerActivity.onResume() was not found")

        onResume.addInstructions(
            0,
            """
                invoke-virtual {p0}, $BASE_PLAYER_ACTIVITY->getPlayerSwitcher()$PLAYER_SWITCHER
                move-result-object v0
                invoke-interface {v0}, $PLAYER_SWITCHER->isPlaying()Z
                move-result v1
                if-eqz v1, :skip_service
                new-instance v1, Landroid/content/Intent;
                const-class v2, $FOREGROUND_SERVICE
                invoke-direct {v1, p0, v2}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
                invoke-virtual {p0, v1}, Landroid/content/Context;->startService(Landroid/content/Intent;)Landroid/content/ComponentName;
                move-result-object v1
                :skip_service
            """,
        )
    }
}
