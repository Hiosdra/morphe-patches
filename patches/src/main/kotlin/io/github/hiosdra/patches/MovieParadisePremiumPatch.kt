package io.github.hiosdra.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Forces RevenueCat `EntitlementInfo.isActive()` to always return `true`.
 *
 * IMPORTANT — this is an experiment, not a reliable unlock. The teardown
 * (doc 05) establishes that Movie Paradise reads its premium state from the
 * server-side `is_premium` field returned by its own `/me` endpoint, in the
 * Dart/AOT layer inside `libapp.so`. That layer is native ARM64 code and is
 * unreachable from a DEX bytecode patch. RevenueCat only drives the *purchase*
 * flow here, so forcing an entitlement active on the client is expected to
 * unlock little or nothing.
 *
 * It is kept as a buildable, installable test of that exact claim: if premium
 * were read from RevenueCat client-side, this would flip it; if (as predicted)
 * it stays locked, that empirically confirms the server-authoritative design.
 *
 * Requires [movieParadisePairipBypassPatch] for the build to launch at all.
 */
@Suppress("unused")
val movieParadisePremiumPatch = bytecodePatch(
    name = "Movie Paradise - Force RevenueCat entitlement (experimental)",
    description = "Forces RevenueCat entitlements active. Experimental: premium is server-authoritative, so this likely unlocks nothing.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_MOVIE_PARADISE)

    val isActive = Fingerprint(
        definingClass = RC_ENTITLEMENT_INFO,
        name = "isActive",
        returnType = "Z",
        parameters = emptyList(),
    )

    execute {
        isActive.matchOrNull()?.let { match ->
            match.method.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    return v0
                """,
            )
        }
    }
}
