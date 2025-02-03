package io.github.flyingpig525.item

import io.github.flyingpig525.GameInstance
import io.github.flyingpig525.building.MatterExtractor
import io.github.flyingpig525.data
import io.github.flyingpig525.data.player.BlockData
import io.github.flyingpig525.ksp.Item
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.*

@Item
object MatterExtractorItem : Actionable {
    override val identifier: String = "matter:generator"
    override val itemMaterial: Material = MatterExtractor.getItem(1, 1).material()

    override fun getItem(uuid: UUID, instance: GameInstance): ItemStack {
        val data = instance.dataResolver[uuid.toString()] ?: return ERROR_ITEM
        return MatterExtractor.getItem(data)
    }

    override fun onInteract(event: PlayerUseItemEvent): Boolean {
        return basicBuildingPlacementDouble(
            event,
            MatterExtractor,
            BlockData::organicMatter,
            "Organic Matter"
        )
    }

    override fun setItemSlot(player: Player) {
        val data = player.data!!
        data.buildings.matterExtractors.select(player)
    }
}