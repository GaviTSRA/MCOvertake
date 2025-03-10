package io.github.flyingpig525.item

import com.sun.jdi.InvalidTypeException
import io.github.flyingpig525.*
import io.github.flyingpig525.GameInstance.Companion.fromInstance
import io.github.flyingpig525.data.block.*
import io.github.flyingpig525.data.inventory.InventoryConditionArguments
import io.github.flyingpig525.data.player.BlockData
import io.github.flyingpig525.data.player.BlockData.Companion.toBlockList
import io.github.flyingpig525.ksp.Item
import net.bladehunt.kotstom.dsl.item.item
import net.bladehunt.kotstom.dsl.item.itemName
import net.bladehunt.kotstom.dsl.item.lore
import net.bladehunt.kotstom.extension.adventure.asMini
import net.bladehunt.kotstom.extension.adventure.noItalic
import net.bladehunt.kotstom.extension.set
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.inventory.click.ClickType
import net.minestom.server.inventory.condition.InventoryConditionResult
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.util.*
import kotlin.enums.EnumEntries

@Item(persistent = true)
object SelectBlockItem : Actionable {
    override val identifier: String = "item:select_block"
    override val itemMaterial: Material = Material.STRUCTURE_VOID
    val item = item(itemMaterial) {
        itemName = "<green>$COLONY_SYMBOL <bold>Select Block</bold> $COLONY_SYMBOL".asMini()
        lore {
            +"<red>Selecting a new block will clear your current block".asMini()
            +"<red>and you will start at the beginning".asMini()
        }
        set(Tag.String("identifier"), identifier)
    }

    override fun getItem(uuid: UUID, instance: GameInstance): ItemStack {
        return item
    }

    override fun onInteract(event: PlayerUseItemEvent): Boolean {
        val gameInstance = instances.fromInstance(event.instance) ?: return true
        val inventory = Inventory(InventoryType.CHEST_5_ROW, "Select Block")

        inventory[4, 0] = END_CATEGORY
        inventory[2, 2] = NATURAL_CATEGORY
        inventory[6, 2] = UNDERGROUND_CATEGORY
        inventory[4, 4] = NETHER_CATEGORY

        inventory.addInventoryCondition { player: Player, slot: Int, clickType: ClickType, res: InventoryConditionResult ->
            res.isCancel = true
            if (res.clickedItem.material() == Material.AIR) return@addInventoryCondition
            player.inventory.cursorItem = ItemStack.AIR
            val arguments = InventoryConditionArguments(player, slot, clickType, res)
            when(res.clickedItem) {
                NATURAL_CATEGORY -> openCategory(NaturalCategory.entries, arguments, gameInstance)
                UNDERGROUND_CATEGORY -> openCategory(UndergroundCategory.entries, arguments, gameInstance)
                NETHER_CATEGORY -> openCategory(NetherCategory.entries, arguments, gameInstance)
                END_CATEGORY -> openCategory(EndCategory.entries, arguments, gameInstance)
                else -> {}
            }
        }

        event.player.openInventory(inventory)
        return true
    }

    private fun <T : Enum<T>> openCategory(entries: EnumEntries<T>, e: InventoryConditionArguments, instance: GameInstance) {
        val inventory = Inventory(InventoryType.CHEST_6_ROW, "Select Block")


        for ((i, cBlock) in entries.withIndex()) {
            if (cBlock is CategoryBlock) {
                val item = item(cBlock.material) {
                    itemName = "<gray>- <gold><bold>${cBlock.name.replace('_', ' ')} <reset><gray>-".asMini()
                    lore {
                        if (cBlock.block !in instance.blockData.toBlockList()) {
                            +"<gray>-| <green><bold><i>Click to Select<reset> <gray>|-".asMini().noItalic()
                            +"<red>Selecting a new block will clear your current block".asMini()
                            +"<red>and you will start at the beginning".asMini()
                        } else {
                            +"<gray>-| <red><bold><i>Block already in use<reset> <gray>|-".asMini().noItalic()
                        }
                    }
                }
                inventory[i % 9, i / 9] = item
            } else throw InvalidTypeException("block is not type CategoryBlock")
        }

        inventory.addInventoryCondition { player: Player, slot: Int, clickType: ClickType, res: InventoryConditionResult ->
            res.isCancel = true
            val isOwnBlock = e.player.uuid.toString() == instance.uuidParents[e.player.uuid.toString()]
            if (res.clickedItem.material() == Material.AIR
                || res.clickedItem.material().block() in instance.blockData.toBlockList()) return@addInventoryCondition
            if (e.player.data != null && isOwnBlock) {
                val data = e.player.data!!
                instance.clearBlock(data.block)
                e.player.removeBossBars()
            }
            instance.uuidParents[e.player.uuid.toString()] = e.player.uuid.toString()
            instance.dataResolver[e.player.uuid.toString()] =
                BlockData(e.player.uuid.toString(), res.clickedItem.material().block()!!, e.player.username).apply {
                    if (instance.noOp) research.basicResearch.noOp = true
                }
            e.player.closeInventory()
            for (i in 0..8) {
                e.player.inventory[i] = ItemStack.AIR
            }
            instance.dataResolver[e.player.uuid.toString()]!!.gameInstance = instance
            instance.dataResolver[e.player.uuid.toString()]!!.setupPlayer(e.player)
            instance.outgoingCoopInvites[e.player.uuid] = mutableListOf()
        }
        e.player.openInventory(inventory)
    }

    override fun setItemSlot(player: Player) {
        val gameInstance = instances.fromInstance(player.instance) ?: return
        player.inventory[9] = getItem(player.uuid, gameInstance)
    }

    fun setAllSlots(player: Player) {
        val gameInstance = instances.fromInstance(player.instance) ?: return
        for (i in 0..8) {
            player.inventory[i] = getItem(player.uuid, gameInstance)
        }
    }
}