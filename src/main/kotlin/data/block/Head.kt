package io.github.flyingpig525.data.block

import net.minestom.server.entity.PlayerSkin
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.HeadProfile
import java.util.*


class Head(
    /**
     * The "Minecraft URL" section on minecraft-heads
     */
    val mcURL: String,
) {

    /**
     * Gets an ItemStack of this head
     *
     * @return The ItemStack
     */
    val itemStack: ItemStack
        get() {
            val playerSkin = PlayerSkin(
                Base64.getEncoder()
                    .encodeToString(("{textures:{SKIN:{url:\"https://textures.minecraft.net/texture/$mcURL\"}}}").toByteArray()),
                ""
            )
            return ItemStack.of(Material.PLAYER_HEAD).with(ItemComponent.PROFILE, HeadProfile(playerSkin))
        }
    companion object {
        fun ItemStack.withHead(mcURL: String): ItemStack {
            val playerSkin = PlayerSkin(
                Base64.getEncoder()
                    .encodeToString(("{textures:{SKIN:{url:\"https://textures.minecraft.net/texture/$mcURL\"}}}").toByteArray()),
                ""
            )
            return with(ItemComponent.PROFILE, HeadProfile(playerSkin))
        }
        val QUESTION_MARK = Head("da99b05b9a1db4d29b5e673d77ae54a77eab66818586035c8a2005aeb810602a") // https://minecraft-heads.com/custom-heads/head/64805
    }
}