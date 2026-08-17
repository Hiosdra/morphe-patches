package io.github.hiosdra.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val VESSEL_FINDER_ADVERT_PLUGIN = "Lcom/izosa/advert/AdvertPlugin;"
private const val CAPACITOR_PLUGIN_CALL = "Lcom/getcapacitor/PluginCall;"

/** Stops native Capacitor advertisement plugin before creating AdView or initializing SDKs. */
@Suppress("unused")
val vesselFinderDisableAdvertisementsPatch = bytecodePatch(
    name = "VesselFinder - Disable advertisements",
    description = "Prevents the VesselFinder advertisement plugin from creating or showing banner ads.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_VESSEL_FINDER)

    execute {
        val advertPlugin = mutableClassDefBy(VESSEL_FINDER_ADVERT_PLUGIN)
        val showMethod = advertPlugin.methods.firstOrNull {
            it.name == "show" &&
                it.returnType == "V" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes.single().toString() == CAPACITOR_PLUGIN_CALL
        } ?: error("VesselFinder AdvertPlugin.show(PluginCall) was not found")

        showMethod.removeInstructions(0)
        showMethod.addInstructions(0, "return-void")
    }
}
