package io.github.flyingpig525.item

import net.bladehunt.kotstom.dsl.item.item
import net.bladehunt.kotstom.dsl.item.itemName
import net.bladehunt.kotstom.extension.adventure.asMini
import net.bladehunt.kotstom.extension.set
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.util.*

object OwnedBlockItem : Actionable {

    init {
        Actionable.registry += this
    }

    override val identifier: String = "block:owned"

    override fun getItem(uuid: UUID): ItemStack {
        return item(Material.LIME_DYE) {
            itemName = "<green><bold>Your Land".asMini()
            set(Tag.String("identifier"), identifier)
        }
    }

    override fun setItemSlot(player: Player) {
        player.inventory[0] = getItem(player.uuid)
    }
}