package io.github.flyingpig525.data.player.config

import kotlinx.serialization.Serializable
import net.minestom.server.item.Material
import java.lang.reflect.Field

@Serializable
class PlayerConfig {
    // see comment in BlockConfig.kt
    @JvmField
    val claimParticles = ConfigElement(
        Material.GRASS_BLOCK,
        "Particles on Claim",
        true
    )

    fun map(): Map<String, Field> = this::class.java.declaredFields.associateBy { it.name }
}