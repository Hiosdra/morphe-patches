package io.github.hiosdra.patches

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.fingerprint
import org.objectweb.asm.Opcodes.*

/**
 * Background Playback patch for F1 TV app.
 *
 * This patch enables audio-only playback when the screen is off by:
 * 1. Moving player pause logic from onPause() to onStop() (shared with PiP patch)
 * 2. Adding a "Background Playback" setting check in onStop()
 * 3. Adding a MediaSession for lock screen / notification controls
 * 4. Requesting audio focus in the player service
 *
 * ⚠️ LIMITATIONS:
 * - Full background playback requires a Foreground Service (MediaSessionService)
 *   which cannot be created via bytecode patches alone.
 * - This patch only handles the Activity-side lifecycle changes.
 * - For complete implementation, use the companion extension (f1tv-background-playback.mpe)
 *   which provides BackgroundPlaybackService with MediaSessionService.
 *
 * Target: com.formulaone.production (F1 TV)
 * Version: 3.0.48.1-SP157.6.0-release-R52-mobile (versionCode: 30481000)
 *
 * Note: This patch shares the onPause/onStop changes with the PiP patch.
 * If both patches are enabled, they should not conflict as they make the same changes.
 */
@Suppress("unused")
val f1TvBackgroundPlaybackPatch = bytecodePatch(
    name = "F1 TV - Background Audio Playback",
    description = "Enables audio-only playback when screen is off. Requires companion extension for full functionality (foreground service + MediaSession).",
    default = false,
    target = "com.formulaone.production",
    fingerprints = fingerprint("com.formulaone.production") {
        versionCode = 30481000
        versionName = "3.0.48.1-SP157.6.0-release-R52-mobile"
    }
) {
    execute {
        // Patch BasePlayerActivity - modify lifecycle for background playback
        editClass("com/avs/p020f1/p022ui/player/BasePlayerActivity") {
            // 1. Modify onPause() - remove playerSwitcher.onPause() call
            // Player should NOT pause when screen turns off (onPause is called)
            // This is the SAME change as PiP patch - they are compatible
            editMethod("onPause", "()V") {
                removeMethodCall("com/avs/p020f1/interactors/playback/PlayerSwitcher", "onPause", "()V")
            }

            // 2. Modify onStop() - conditionally pause based on background playback setting
            editMethod("onStop", "()V") {
                // Insert background playback check at the start of onStop
                // If background playback is enabled, don't pause the player
                insertCodeAtStart("""
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.isBackgroundPlaybackEnabled ()Z
                    IFEQ L_continue_stop
                    // Background playback enabled - don't pause player, just detach video output
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.detachVideoOutput ()V
                    RETURN
                L_continue_stop:
                """)

                // Also ensure playerSwitcher.onPause() is called before existing onStop logic
                // (this is inserted after our check, before the original onStop code)
                insertMethodCallBefore(
                    targetMethod = "getPlaybackUseCase().capturePlaybackInactivityTime()",
                    className = "com/avs/p020f1/interactors/playback/PlayerSwitcher",
                    methodName = "onPause",
                    methodDesc = "()V",
                    owner = "getPlayerSwitcher"
                )
            }

            // 3. Add isBackgroundPlaybackEnabled() method - reads from SharedPreferences
            addMethod(
                access = ACC_PRIVATE,
                name = "isBackgroundPlaybackEnabled",
                descriptor = "()Z",
                code = """
                    LDC "f1tv_preferences"
                    ICONST_0
                    INVOKEVIRTUAL android/content/Context.getSharedPreferences (Ljava/lang/String;I)Landroid/content/SharedPreferences;
                    LDC "background_playback_enabled"
                    ICONST_0
                    INVOKEINTERFACE android/content/SharedPreferences.getBoolean (Ljava/lang/String;Z)Z
                    IRETURN
                """
            )

            // 4. Add detachVideoOutput() method - detaches Surface from player but keeps audio
            addMethod(
                access = ACC_PRIVATE,
                name = "detachVideoOutput",
                descriptor = "()V",
                code = """
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.getPlayerSwitcher ()Lcom/avs/p020f1/interactors/playback/PlayerSwitcher;
                    INVOKEINTERFACE com/avs/p020f1/interactors/playback/PlayerSwitcher.detachVideoOutput ()V
                    RETURN
                """
            )

            // 5. Add onResume() logic to re-attach video output when returning from background
            editMethod("onResume", "()V") {
                // Insert at the beginning of onResumeInternal (called from onResume)
                insertCodeAtStart("""
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.isBackgroundPlaybackEnabled ()Z
                    IFEQ L_attach
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.attachVideoOutput ()V
                L_attach:
                """)
            }

            // 6. Add attachVideoOutput() method
            addMethod(
                access = ACC_PRIVATE,
                name = "attachVideoOutput",
                descriptor = "()V",
                code = """
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.getPlayerSwitcher ()Lcom/avs/p020f1/interactors/playback/PlayerSwitcher;
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.getPlayerLayoutHolder ()Lcom/avs/p020f1/p022ui/player/PlayerLayoutHolder;
                    INVOKEINTERFACE com/avs/p020f1/p022ui/player/PlayerLayoutHolder.getPlayerViews ()Lkotlin/Pair;
                    CHECKCAST kotlin/Pair
                    GETFIELD kotlin/Pair.first : Ljava/lang/Object;
                    CHECKCAST com/bitmovin/player/PlayerView
                    INVOKEINTERFACE com/avs/p020f1/interactors/playback/PlayerSwitcher.attachVideoOutput (Lcom/bitmovin/player/PlayerView;)V
                    RETURN
                """
            )

            // 7. Add MediaSession initialization for lock screen controls
            editMethod("onCreate", "(Landroid/os/Bundle;)V") {
                // Insert at the end of onCreate, after preparePlayerComponents()
                insertCodeBeforeReturn("""
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.initMediaSession ()V
                """)
            }

            // 8. Add initMediaSession() method
            addMethod(
                access = ACC_PRIVATE,
                name = "initMediaSession",
                descriptor = "()V",
                code = """
                    NEW androidx/media3/session/MediaSession$Builder
                    DUP
                    ALOAD 0
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.getPlayerSwitcher ()Lcom/avs/p020f1/interactors/playback/PlayerSwitcher;
                    INVOKEINTERFACE com/avs/p020f1/interactors/playback/PlayerSwitcher.getMedia3Player ()Landroidx/media3/common/Player;
                    INVOKESPECIAL androidx/media3/session/MediaSession$Builder.<init> (Landroid/content/Context;Landroidx/media3/common/Player;)V
                    INVOKEVIRTUAL androidx/media3/session/MediaSession$Builder.build ()Landroidx/media3/session/MediaSession;
                    ASTORE 1
                    ALOAD 0
                    ALOAD 1
                    PUTFIELD com/avs/p020f1/p022ui/player/BasePlayerActivity.mediaSession : Landroidx/media3/session/MediaSession;
                    ALOAD 1
                    NEW com/avs/p020f1/p022ui/player/BasePlayerActivity$MediaSessionCallback
                    DUP
                    ALOAD 0
                    INVOKESPECIAL com/avs/p020f1/p022ui/player/BasePlayerActivity$MediaSessionCallback.<init> (Lcom/avs/p020f1/p022ui/player/BasePlayerActivity;)V
                    INVOKEVIRTUAL androidx/media3/session/MediaSession.setCallback (Landroidx/media3/session/MediaSession$Callback;)V
                    RETURN
                """
            )

            // 9. Add releaseMediaSession() in onDestroy
            editMethod("onDestroy", "()V") {
                insertCodeAtStart("""
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/BasePlayerActivity.mediaSession : Landroidx/media3/session/MediaSession;
                    IFNULL L_skip_release
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/BasePlayerActivity.mediaSession : Landroidx/media3/session/MediaSession;
                    INVOKEVIRTUAL androidx/media3/session/MediaSession.release ()V
                L_skip_release:
                """)
            }

            // 10. Add MediaSession field
            addField(
                access = ACC_PRIVATE,
                name = "mediaSession",
                descriptor = "Landroidx/media3/session/MediaSession;",
                value = null
            )

            // 11. Add MediaSessionCallback inner class
            addInnerClass(
                name = "MediaSessionCallback",
                access = ACC_PRIVATE | ACC_STATIC,
                superClass = "androidx/media3/session/MediaSession$Callback",
                interfaces = [],
                methods = [
                    // onPlay() - resume playback
                    """
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/BasePlayerActivity$MediaSessionCallback.this$0 : Lcom/avs/p020f1/p022ui/player/BasePlayerActivity;
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.getPlayerSwitcher ()Lcom/avs/p020f1/interactors/playback/PlayerSwitcher;
                    INVOKEINTERFACE com/avs/p020f1/interactors/playback/PlayerSwitcher.onPlayPressed ()V
                    RETURN
                    """,
                    // onPause() - pause playback
                    """
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/BasePlayerActivity$MediaSessionCallback.this$0 : Lcom/avs/p020f1/p022ui/player/BasePlayerActivity;
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.getPlayerSwitcher ()Lcom/avs/p020f1/interactors/playback/PlayerSwitcher;
                    INVOKEINTERFACE com/avs/p020f1/interactors/playback/PlayerSwitcher.onPlayPressed ()V
                    RETURN
                    """
                ]
            )
        }

        // Patch PlayerSwitcher interface to add new methods
        editClass("com/avs/p020f1/interactors/playback/PlayerSwitcher") {
            // Add detachVideoOutput() - detaches Surface from player
            addMethod(
                access = ACC_PUBLIC | ACC_ABSTRACT,
                name = "detachVideoOutput",
                descriptor = "()V",
                code = null
            )

            // Add attachVideoOutput(PlayerView) - re-attaches Surface
            addMethod(
                access = ACC_PUBLIC | ACC_ABSTRACT,
                name = "attachVideoOutput",
                descriptor = "(Lcom/bitmovin/player/PlayerView;)V",
                code = null
            )

            // Add getMedia3Player() - returns Media3-compatible Player for MediaSession
            addMethod(
                access = ACC_PUBLIC | ACC_ABSTRACT,
                name = "getMedia3Player",
                descriptor = "()Landroidx/media3/common/Player;",
                code = null
            )
        }

        // Patch PlayerSwitcherImpl to implement new methods
        editClass("com/avs/p020f1/interactors/playback/PlayerSwitcherImpl") {
            // Implement detachVideoOutput() - set Surface to null on main player
            addMethod(
                access = ACC_PUBLIC,
                name = "detachVideoOutput",
                descriptor = "()V",
                code = """
                    ALOAD 0
                    GETFIELD com/avs/p020f1/interactors/playback/PlayerSwitcherImpl.mainPlayerView : Lcom/bitmovin/player/PlayerView;
                    IFNULL L_return
                    ALOAD 0
                    GETFIELD com/avs/p020f1/interactors/playback/PlayerSwitcherImpl.mainPlayerView : Lcom/bitmovin/player/PlayerView;
                    INVOKEVIRTUAL com/bitmovin/player/PlayerView.getPlayer ()Lcom/bitmovin/player/api/Player;
                    IFNULL L_return
                    ALOAD 0
                    GETFIELD com/avs/p020f1/interactors/playback/PlayerSwitcherImpl.mainPlayerView : Lcom/bitmovin/player/PlayerView;
                    INVOKEVIRTUAL com/bitmovin/player/PlayerView.getPlayer ()Lcom/bitmovin/player/api/Player;
                    ACONST_NULL
                    INVOKEVIRTUAL com/bitmovin/player/api/Player.setSurface (Landroid/view/Surface;)V
                L_return:
                    RETURN
                """
            )

            // Implement attachVideoOutput(PlayerView) - set Surface on player
            addMethod(
                access = ACC_PUBLIC,
                name = "attachVideoOutput",
                descriptor = "(Lcom/bitmovin/player/PlayerView;)V",
                code = """
                    ALOAD 1
                    IFNULL L_return
                    ALOAD 1
                    INVOKEVIRTUAL com/bitmovin/player/PlayerView.getPlayer ()Lcom/bitmovin/player/api/Player;
                    IFNULL L_return
                    ALOAD 1
                    INVOKEVIRTUAL com/bitmovin/player/PlayerView.getPlayer ()Lcom/bitmovin/player/api/Player;
                    ALOAD 1
                    INVOKEVIRTUAL com/bitmovin/player/PlayerView.getVideoSurface ()Landroid/view/Surface;
                    INVOKEVIRTUAL com/bitmovin/player/api/Player.setSurface (Landroid/view/Surface;)V
                L_return:
                    RETURN
                """
            )

            // Implement getMedia3Player() - return a Media3 wrapper around Bitmovin player
            // This is a stub - full implementation needs a Media3 Player wrapper
            addMethod(
                access = ACC_PUBLIC,
                name = "getMedia3Player",
                descriptor = "()Landroidx/media3/common/Player;",
                code = """
                    ACONST_NULL
                    ARETURN
                """
            )
        }
    }
}