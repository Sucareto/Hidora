package app.sucareto.hidora.profile

import android.content.Context
import app.sucareto.hidora.core.gadget.GadgetSpec
import app.sucareto.hidora.core.profile.GadgetProfile
import app.sucareto.hidora.core.profile.UsbIdAllocator
import app.sucareto.hidora.core.profile.UsbIdentity
import app.sucareto.hidora.core.profile.UsbIdentitySettings
import app.sucareto.hidora.profile.gamepad.GamepadProfile
import app.sucareto.hidora.profile.keyboardmouse.KeyboardMouseProfile

object ProfileRegistry {
    val profiles: List<GadgetProfile> = listOf(
        KeyboardMouseProfile,
        GamepadProfile
    )

    private val defaultIds = UsbIdAllocator.allocate(profiles.flatMap { p -> p.usbIdentities.map { p.id to it } })

    init {
        require(profiles.map { it.gadgetName }.toSet().size == profiles.size) { "duplicate gadget name" }
        require(profiles.map { it.id }.toSet().size == profiles.size) { "duplicate profile id" }
        profiles.forEach { require(GadgetSpec.isValidName(it.gadgetName)) { "invalid gadget name: ${it.gadgetName}" } }
    }

    fun byId(id: String): GadgetProfile? = profiles.firstOrNull { it.id == id }

    fun byGadgetName(name: String): GadgetProfile? = profiles.firstOrNull { it.gadgetName == name }

    fun usbIdentity(context: Context, profile: GadgetProfile, identity: UsbIdentity): UsbIdentitySettings.Entry =
        UsbIdentitySettings.get(context).entry(profile, identity, defaultIds.getValue("${profile.id}.${identity.key}"))
}

fun GadgetProfile.usbIdentity(context: Context, identity: UsbIdentity): UsbIdentitySettings.Entry =
    ProfileRegistry.usbIdentity(context, this, identity)
