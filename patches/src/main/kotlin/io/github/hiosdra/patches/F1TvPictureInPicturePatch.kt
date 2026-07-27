package io.github.hiosdra.patches

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.fingerprint
import org.objectweb.asm.Opcodes.*

/**
 * Picture-in-Picture patch for F1 TV app.
 *
 * This patch enables PiP mode for the Bitmovin player (BasePlayerActivity) by:
 * 1. Moving player pause logic from onPause() to onStop() - critical for PiP since
 *    Android calls onPause() when entering PiP but the player should continue playing
 * 2. Adding onPictureInPictureModeChanged() to hide/show UI controls during PiP
 * 3. Adding onUserLeaveHint() for manual PiP entry on Android 10/11
 * 4. Adding PiP params building and updating methods
 *
 * Target: com.formulaone.production (F1 TV)
 * Version: 3.0.48.1-SP157.6.0-release-R52-mobile (versionCode: 30481000)
 *
 * Note: Manifest changes (android:supportsPictureInPicture="true") must be applied
 * via a resource patch or by modifying the APK manifest directly.
 */
@Suppress("unused")
val f1TvPictureInPicturePatch = bytecodePatch(
    name = "F1 TV - Picture-in-Picture Support",
    description = "Enables Picture-in-Picture mode for the Bitmovin player. Requires manifest modification to add android:supportsPictureInPicture=\"true\" to BasePlayerActivity.",
    default = false,
    target = "com.formulaone.production",
    fingerprints = fingerprint("com.formulaone.production") {
        versionCode = 30481000
        versionName = "3.0.48.1-SP157.6.0-release-R52-mobile"
    }
) {
    execute {
        // Patch BasePlayerActivity - move pause logic from onPause to onStop
        // and add PiP lifecycle handlers
        editClass("com/avs/p020f1/p022ui/player/BasePlayerActivity") {
            // 1. Modify onPause() - remove playerSwitcher.onPause() call
            // The player should NOT pause when entering PiP (onPause is called)
            editMethod("onPause", "()V") {
                // Find and remove the call to playerSwitcher.onPause()
                // Original: getPlayerSwitcher().onPause(); super.onPause(); ...
                // We want to keep super.onPause() and other logic but remove playerSwitcher.onPause()
                removeMethodCall("com/avs/p020f1/interactors/playback/PlayerSwitcher", "onPause", "()V")
            }

            // 2. Modify onStop() - add playerSwitcher.onPause() call before existing onStop()
            // Player should pause when Activity is actually stopping (not just entering PiP)
            editMethod("onStop", "()V") {
                // Insert playerSwitcher.onPause() at the beginning of onStop
                // before getPlaybackUseCase().capturePlaybackInactivityTime()
                insertMethodCallAtStart(
                    "com/avs/p020f1/interactors/playback/PlayerSwitcher",
                    "onPause",
                    "()V",
                    owner = "getPlayerSwitcher"
                )
            }

            // 3. Add onPictureInPictureModeChanged method
            // This hides/shows UI controls when entering/exiting PiP
            addMethod(
                access = ACC_PUBLIC,
                name = "onPictureInPictureModeChanged",
                descriptor = "(ZLandroid/content/res/Configuration;)V",
                code = """
                    ALOAD 0
                    ALOAD 1
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.getPlayerLayoutHolder ()Lcom/avs/p020f1/p022ui/player/PlayerLayoutHolder;
                    ILOAD 1
                    ICONST_1
                    IXOR
                    INVOKEINTERFACE com/avs/p020f1/p022ui/player/PlayerLayoutHolder.setPipUiVisible (Z)V
                    ALOAD 0
                    ILOAD 1
                    IFEQ L_exit_pip
                    RETURN
                L_exit_pip:
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.getPlayerLayoutHolder ()Lcom/avs/p020f1/p022ui/player/PlayerLayoutHolder;
                    INVOKEINTERFACE com/avs/p020f1/p022ui/player/PlayerLayoutHolder.updateUi ()V
                    RETURN
                """
            )

            // 4. Add onUserLeaveHint for Android 10/11 (API 29-30) manual PiP entry
            addMethod(
                access = ACC_PUBLIC,
                name = "onUserLeaveHint",
                descriptor = "()V",
                code = """
                    ALOAD 0
                    INVOKESPECIAL com/avs/p020f1/p022ui/BaseActivity.onUserLeaveHint ()V
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.canEnterPip ()Z
                    IFEQ L_return
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.buildPipParams ()Landroid/app/PictureInPictureParams;
                    ALOAD 0
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.buildPipParams ()Landroid/app/PictureInPictureParams;
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.enterPictureInPictureMode (Landroid/app/PictureInPictureParams;)Z
                    POP
                L_return:
                    RETURN
                """
            )

            // 5. Add canEnterPip() helper method
            addMethod(
                access = ACC_PRIVATE,
                name = "canEnterPip",
                descriptor = "()Z",
                code = """
                    ALOAD 0
                    INVOKEVIRTUAL android/content/Context.getPackageManager ()Landroid/content/pm/PackageManager;
                    GETSTATIC android/content/pm/PackageManager.FEATURE_PICTURE_IN_PICTURE : Ljava/lang/String;
                    INVOKEVIRTUAL android/content/pm/PackageManager.hasSystemFeature (Ljava/lang/String;)Z
                    IFEQ L_false
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.getPlayerSwitcher ()Lcom/avs/p020f1/interactors/playback/PlayerSwitcher;
                    INVOKEINTERFACE com/avs/p020f1/interactors/playback/PlayerSwitcher.isPlaying ()Z
                    IFEQ L_false
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.getPlayerSwitcher ()Lcom/avs/p020f1/interactors/playback/PlayerSwitcher;
                    INVOKEINTERFACE com/avs/p020f1/interactors/playback/PlayerSwitcher.isCasting ()Z
                    IFNE L_false
                    ICONST_1
                    IRETURN
                L_false:
                    ICONST_0
                    IRETURN
                """
            )

            // 6. Add buildPipParams() method
            addMethod(
                access = ACC_PRIVATE,
                name = "buildPipParams",
                descriptor = "()Landroid/app/PictureInPictureParams;",
                code = """
                    NEW android/graphics/Rect
                    DUP
                    INVOKESPECIAL android/graphics/Rect.<init> ()V
                    ASTORE 1
                    ALOAD 0
                    INVOKEVIRTUAL com/avs/p020f1/p022ui/player/BasePlayerActivity.getPlayerLayoutHolder ()Lcom/avs/p020f1/p022ui/player/PlayerLayoutHolder;
                    INVOKEINTERFACE com/avs/p020f1/p022ui/player/PlayerLayoutHolder.getPipSourceView ()Landroid/view/View;
                    ALOAD 1
                    INVOKEVIRTUAL android/view/View.getGlobalVisibleRect (Landroid/graphics/Rect;)Z
                    POP
                    NEW android/app/PictureInPictureParams$Builder
                    DUP
                    INVOKESPECIAL android/app/PictureInPictureParams$Builder.<init> ()V
                    NEW android/util/Rational
                    DUP
                    BIPUSH 16
                    BIPUSH 9
                    INVOKESPECIAL android/util/Rational.<init> (II)V
                    INVOKEVIRTUAL android/app/PictureInPictureParams$Builder.setAspectRatio (Landroid/util/Rational;)Landroid/app/PictureInPictureParams$Builder;
                    ALOAD 1
                    INVOKEVIRTUAL android/app/PictureInPictureParams$Builder.setSourceRectHint (Landroid/graphics/Rect;)Landroid/app/PictureInPictureParams$Builder;
                    INVOKESTATIC android/os/Build$VERSION.SDK_INT ()I
                    BIPUSH 31
                    IF_ICMPLT L_build
                    ICONST_1
                    INVOKEVIRTUAL android/app/PictureInPictureParams$Builder.setAutoEnterEnabled (Z)Landroid/app/PictureInPictureParams$Builder;
                    ICONST_1
                    INVOKEVIRTUAL android/app/PictureInPictureParams$Builder.setSeamlessResizeEnabled (Z)Landroid/app/PictureInPictureParams$Builder;
                L_build:
                    INVOKEVIRTUAL android/app/PictureInPictureParams$Builder.build ()Landroid/app/PictureInPictureParams;
                    ARETURN
                """
            )

            // 7. Add getPipSourceView() to PlayerLayoutHolder interface
            // This is needed for the source rect hint in PiP params
        }

        // Patch PlayerLayoutHolder interface to add getPipSourceView() and setPipUiVisible()
        editClass("com/avs/p020f1/p022ui/player/PlayerLayoutHolder") {
            // Add getPipSourceView() method (returns the main video container view)
            addMethod(
                access = ACC_PUBLIC | ACC_ABSTRACT,
                name = "getPipSourceView",
                descriptor = "()Landroid/view/View;",
                code = null // Abstract method - no implementation
            )

            // Add setPipUiVisible() method
            addMethod(
                access = ACC_PUBLIC | ACC_ABSTRACT,
                name = "setPipUiVisible",
                descriptor = "(Z)V",
                code = null // Abstract method - no implementation
            )
        }

        // Patch PlayerLayoutHolderImpl to implement the new methods
        editClass("com/avs/p020f1/p022ui/player/PlayerLayoutHolderImpl") {
            // Implement getPipSourceView() - returns the main player container
            addMethod(
                access = ACC_PUBLIC,
                name = "getPipSourceView",
                descriptor = "()Landroid/view/View;",
                code = """
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/PlayerLayoutHolderImpl.binding : Lcom/avs/p020f1/databinding/ActivityPlayerBitmovinBinding;
                    GETFIELD com/avs/p020f1/databinding/ActivityPlayerBitmovinBinding.mainPlayerContainer : Landroid/view/View;
                    ARETURN
                """
            )

            // Implement setPipUiVisible() - hide/show UI controls
            addMethod(
                access = ACC_PUBLIC,
                name = "setPipUiVisible",
                descriptor = "(Z)V",
                code = """
                    ILOAD 1
                    IFEQ L_gone
                    GETSTATIC android/view/View.VISIBLE : I
                    ISTORE 2
                    GOTO L_set
                L_gone:
                    GETSTATIC android/view/View.GONE : I
                    ISTORE 2
                L_set:
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/PlayerLayoutHolderImpl.binding : Lcom/avs/p020f1/databinding/ActivityPlayerBitmovinBinding;
                    GETFIELD com/avs/p020f1/databinding/ActivityPlayerBitmovinBinding.playerBackIcon : Landroid/widget/ImageView;
                    ILOAD 2
                    INVOKEVIRTUAL android/view/View.setVisibility (I)V
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/PlayerLayoutHolderImpl.binding : Lcom/avs/p020f1/databinding/ActivityPlayerBitmovinBinding;
                    GETFIELD com/avs/p020f1/databinding/ActivityPlayerBitmovinBinding.playerPlayButton : Landroid/widget/ImageView;
                    ILOAD 2
                    INVOKEVIRTUAL android/view/View.setVisibility (I)V
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/PlayerLayoutHolderImpl.binding : Lcom/avs/p020f1/databinding/ActivityPlayerBitmovinBinding;
                    GETFIELD com/avs/p020f1/databinding/ActivityPlayerBitmovinBinding.playerForwardButton : Landroid/widget/ImageView;
                    ILOAD 2
                    INVOKEVIRTUAL android/view/View.setVisibility (I)V
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/PlayerLayoutHolderImpl.binding : Lcom/avs/p020f1/databinding/ActivityPlayerBitmovinBinding;
                    GETFIELD com/avs/p020f1/databinding/ActivityPlayerBitmovinBinding.playerRewindButton : Landroid/widget/ImageView;
                    ILOAD 2
                    INVOKEVIRTUAL android/view/View.setVisibility (I)V
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/PlayerLayoutHolderImpl.binding : Lcom/avs/p020f1/databinding/ActivityPlayerBitmovinBinding;
                    GETFIELD com/avs/p020f1/databinding/ActivityPlayerBitmovinBinding.switchersLayout : Landroid/view/View;
                    ILOAD 2
                    INVOKEVIRTUAL android/view/View.setVisibility (I)V
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/PlayerLayoutHolderImpl.binding : Lcom/avs/p020f1/databinding/ActivityPlayerBitmovinBinding;
                    GETFIELD com/avs/p020f1/databinding/ActivityPlayerBitmovinBinding.liveNow : Landroid/view/View;
                    ILOAD 2
                    INVOKEVIRTUAL android/view/View.setVisibility (I)V
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/PlayerLayoutHolderImpl.binding : Lcom/avs/p020f1/databinding/ActivityPlayerBitmovinBinding;
                    GETFIELD com/avs/p020f1/databinding/ActivityPlayerBitmovinBinding.upNextTray : Landroid/view/View;
                    ILOAD 2
                    INVOKEVIRTUAL android/view/View.setVisibility (I)V
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/PlayerLayoutHolderImpl.binding : Lcom/avs/p020f1/databinding/ActivityPlayerBitmovinBinding;
                    GETFIELD com/avs/p020f1/databinding/ActivityPlayerBitmovinBinding.upNextEndScreen : Landroid/view/View;
                    ILOAD 2
                    INVOKEVIRTUAL android/view/View.setVisibility (I)V
                    ALOAD 0
                    GETFIELD com/avs/p020f1/p022ui/player/PlayerLayoutHolderImpl.binding : Lcom/avs/p020f1/databinding/ActivityPlayerBitmovinBinding;
                    GETFIELD com/avs/p020f1/databinding/ActivityPlayerBitmovinBinding.playerTouchOverlay : Landroid/view/View;
                    ILOAD 2
                    INVOKEVIRTUAL android/view/View.setVisibility (I)V
                    RETURN
                """
            )
        }
    }
}