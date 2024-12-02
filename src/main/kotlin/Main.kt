package io.github.flyingpig525

import de.articdive.jnoise.generators.noise_parameters.simplex_variants.Simplex2DVariant
import de.articdive.jnoise.generators.noisegen.opensimplex.SuperSimplexNoiseGenerator
import de.articdive.jnoise.modules.octavation.fractal_functions.FractalFunction
import de.articdive.jnoise.pipeline.JNoise
import io.github.flyingpig525.console.Command
import io.github.flyingpig525.console.ConfigCommand
import io.github.flyingpig525.console.SaveCommand
import io.github.flyingpig525.data.Config
import io.github.flyingpig525.data.PlayerData
import io.github.flyingpig525.data.PlayerData.Companion.toBlockSortedList
import io.github.flyingpig525.item.*
import io.github.flyingpig525.wall.blockIsWall
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.bladehunt.kotstom.GlobalEventHandler
import net.bladehunt.kotstom.InstanceManager
import net.bladehunt.kotstom.SchedulerManager
import net.bladehunt.kotstom.dsl.item.amount
import net.bladehunt.kotstom.dsl.item.item
import net.bladehunt.kotstom.dsl.item.itemName
import net.bladehunt.kotstom.dsl.kbar
import net.bladehunt.kotstom.dsl.line
import net.bladehunt.kotstom.dsl.listen
import net.bladehunt.kotstom.dsl.particle
import net.bladehunt.kotstom.extension.adventure.asMini
import net.bladehunt.kotstom.extension.roundToBlock
import net.bladehunt.kotstom.extension.set
import net.kyori.adventure.resource.ResourcePackInfo
import net.minestom.server.MinecraftServer
import net.minestom.server.collision.ShapeImpl
import net.minestom.server.color.Color
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.*
import net.minestom.server.extras.MojangAuth
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.anvil.AnvilLoader
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.PlayerInventory
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.SendablePacket
import net.minestom.server.particle.Particle
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.time.Cooldown
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackReader
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import team.unnamed.creative.server.ResourcePackServer
import java.io.File
import java.io.PrintStream
import java.net.URI
import java.sql.Time
import java.time.Instant
import java.util.*

const val POWER_SYMBOL = "✘"
const val ATTACK_SYMBOL = "\uD83D\uDDE1"
const val MATTER_SYMBOL = "\uD83C\uDF0A"
const val MECHANICAL_SYMBOL = "☵"
const val CLAIM_SYMBOL = "◎"
const val COLONY_SYMBOL = "⚐"
const val BUILDING_SYMBOL = "⧈"
const val RESOURCE_SYMBOL = "⌘"
const val WALL_SYMBOL = "\uD83E\uDE93"
const val PICKAXE_SYMBOL = "⛏"

const val DASH_BANNER = "----------------------------------------------"

var tick: ULong = 0uL

var runConsoleLoop = true
val logStream = PrintStream("log.log")
@OptIn(ExperimentalSerializationApi::class)
val json = Json { prettyPrint = true; encodeDefaults = true; allowComments = true;}

lateinit var instance: InstanceContainer private set

lateinit var players: MutableMap<String, PlayerData>

lateinit var config: Config

lateinit var powerTask: Task
lateinit var matterTask: Task
lateinit var mechanicalTask: Task

fun main() = runBlocking { try {
    // Initialize the servers
    val minecraftServer = MinecraftServer.init()
    MojangAuth.init()
    MinecraftServer.setBrandName("MCOvertake")
    MinecraftServer.getExceptionManager().setExceptionHandler {
        it.printStackTrace()
        it.printStackTrace(logStream)
    }

    val configFile = File("config.json")
    if (!configFile.exists()) {
        withContext(Dispatchers.IO) {
            configFile.createNewFile()
        }
        configFile.writeText(json.encodeToString(Config()))
    }
    config = json.decodeFromString<Config>(configFile.readText())
    configFile.writeText(json.encodeToString(config))
    log("Config imported...")

    val resourcePack = MinecraftResourcePackReader.minecraft().readFromZipFile(File("res/pack.zip"))
    val builtResourcePack = MinecraftResourcePackWriter.minecraft().build(resourcePack)
    val packServer = ResourcePackServer.server()
        .address(config.serverAddress, config.packServerPort)
        .pack(builtResourcePack)
        .build()
    log("Resource pack loaded...")

    val noise = JNoise.newBuilder()
        .superSimplex(SuperSimplexNoiseGenerator.newBuilder().setSeed(config.noiseSeed).setVariant2D(Simplex2DVariant.CLASSIC))
        .octavate(2, 0.1, 1.0, FractalFunction.TURBULENCE, true)
        .scale(config.noiseScale)
        .clamp(-1.0, 1.0)
        .build()
    instance = InstanceManager.createInstanceContainer().apply {
        chunkLoader = AnvilLoader("world/world")

        setGenerator { unit ->
            unit.modifier().setAll { x, y, z ->
                if (x in 0..config.mapSize && y in 38..39 && z in 0..config.mapSize) {
                    val eval = noise.evaluateNoise(x.toDouble(), z.toDouble())
                    if (eval > config.noiseThreshold) return@setAll Block.GRASS_BLOCK
                    return@setAll if (y == 39) Block.WATER else Block.SAND
                }
                if (x in -1..config.mapSize+1 && z in -1..config.mapSize+1 && y < 40) {
                    return@setAll Block.DIAMOND_BLOCK
                }
                Block.AIR
            }
        }
        setChunkSupplier(::LightingChunk)
    }
    log("Created instance...")
    for (x in 0..config.mapSize) {
        for (z in 0..config.mapSize) {
            instance.loadChunk(Vec(x.toDouble(), 39.0, z.toDouble())).thenRunAsync {
                val playerBlock = instance.getBlock(x, 38, z)
                if (instance.getBlock(x, 39, z) == Block.WATER && instance.getBlock(x, 38, z) != Block.SAND) {
                    ClaimWaterItem.spawnPlayerRaft(playerBlock, Vec(x.toDouble(), 40.0, z.toDouble()))
                }
            }
        }
    }
    log("Spawned building rafts...")
    log("World loaded...")


    players = Json.decodeFromString<MutableMap<String, PlayerData>>(
        if (File("./player-data.json").exists())
            File("./player-data.json").readText()
        else "{}"
    )
    log("Player data imported...")
    log("Filesystem actions complete...")

    initItems()
    log("Items initialized...")
    GlobalEventHandler.listen<AsyncPlayerConfigurationEvent> { event ->
        event.spawningInstance = instance
        val player = event.player
        if (config.whitelisted.isNotEmpty() && player.username !in config.whitelisted) {
            player.kick(
                config.notWhitelistedMessage.asMini()
            )
        }
        player.respawnPoint = Pos(5.0, 41.0, 5.0)
        player.sendResourcePacks(ResourcePackInfo.resourcePackInfo()
            .hash(builtResourcePack.hash())
            .uri(URI("http://${config.serverAddress}:${config.packServerPort}/${builtResourcePack.hash()}.zip"))
            .build()
        )
    }


    GlobalEventHandler.listen<PlayerSpawnEvent> { e ->
        e.player.gameMode = GameMode.ADVENTURE
        e.player.flyingSpeed = 0.5f
        e.player.isAllowFlying = true
        e.player.addEffect(Potion(PotionEffect.NIGHT_VISION, 1, -1))
        val data = players[e.player.uuid.toString()]
        if (data == null) {
            SelectBlockItem.setAllSlots(e.player)
        } else {
            data.setupPlayer(e.player)
            if (e.isFirstSpawn) {
                data.sendCooldowns(e.player)
            }
        }
    }
    log("Player spawning setup...")

    var scoreboardTitleProgress = -1.0
    // Every tick
    SchedulerManager.scheduleTask({
        tick++

        scoreboardTitleProgress += 0.02
        if (scoreboardTitleProgress >= 1.0) {
            scoreboardTitleProgress = -1.0
        }
        val scoreboard = kbar("<gradient:green:gold:$scoreboardTitleProgress><bold>MCOvertake - v0.1".asMini()) {
            for ((i, player) in players.toBlockSortedList().withIndex()) {
                if (player.playerDisplayName == null) player.playerDisplayName =
                    instance.getPlayerByUuid(player.uuid.toUUID())?.username ?: continue
                if (player.blocks == 0) continue
                line("<dark_green><bold>${player.playerDisplayName}".asMini()) {
                    isVisible = true
                    line = player.blocks
                    id = player.playerDisplayName + "$i"
                }
            }
        }
        for (player in instance.players) {
            scoreboard.addViewer(player)
        }
    }, TaskSchedule.tick(1), TaskSchedule.tick(1))


    // General player tick/extractor tick
    matterTask = SchedulerManager.scheduleTask({
        for (uuid in players.keys) {
            val data = players[uuid]!!
            data.playerTick(instance)
            data.matterExtractors.tick(data)
        }
    }, TaskSchedule.tick(30), TaskSchedule.tick(30))

    // Camp tick
    powerTask = SchedulerManager.scheduleTask({
        for (uuid in players.keys) {
            val data = players[uuid]!!
            data.powerTick()
            data.updateBossBars()
        }
    }, TaskSchedule.tick(70), TaskSchedule.tick(70))

    // Mechanical part tick
    mechanicalTask = SchedulerManager.scheduleTask({
        for (uuid in players.keys) {
            val data = players[uuid]!!
            data.mechanicalTick()
        }
    }, TaskSchedule.tick(400), TaskSchedule.tick(400))

    // Save loop
    SchedulerManager.scheduleTask({
        val file = File("./player-data.json")
        if (!file.exists()) {
            file.createNewFile()
        }
        file.writeText(Json.encodeToString(players))
        instance.saveChunksToStorage()
        if (config.printSaveMessages) {
            log("Game data saved")
        }
    }, TaskSchedule.minutes(1), TaskSchedule.minutes(1))
    log("Game loops scheduled...")

    GlobalEventHandler.listen<PlayerMoveEvent> { e ->
        with(e) {
            if (newPosition.x !in 0.0..config.mapSize + 1.0 || newPosition.z !in 0.0..config.mapSize + 1.0) {
                newPosition = Pos(
                    newPosition.x.coerceIn(0.0..config.mapSize + 1.0),
                    newPosition.y,
                    newPosition.z.coerceIn(0.0..config.mapSize + 1.0),
                    newPosition.yaw,
                    newPosition.pitch
                )
            }
        }
    }

    GlobalEventHandler.listen<PlayerUseItemEvent> { e ->
        val item = e.player.getItemInHand(e.hand)
        e.isCancelled = true
        for (actionable in Actionable.registry) {
            if (item.getTag(Tag.String("identifier")) == actionable.identifier) {
                e.isCancelled = try {
                    actionable.onInteract(e, instance)
                } catch(e: Exception) {
                    e.printStackTrace()
                    e.printStackTrace(logStream)
                    true
                }
                break
            }
        }
    }

    GlobalEventHandler.listen<PlayerBlockInteractEvent> { e ->
        val item = e.player.getItemInHand(e.hand)
        GlobalEventHandler.call(PlayerUseItemEvent(e.player, e.hand, item, 0))
    }
    log("Item use event registered")

    GlobalEventHandler.listen<PlayerSwapItemEvent> {
        it.isCancelled = true
    }

    GlobalEventHandler.listen<PlayerTickEvent> { e ->
        val playerData = players[e.player.uuid.toString()] ?: return@listen

        val target = e.player.getTrueTarget(20)
        if (target != null) {
            val buildingPoint = target.buildingPosition
            val playerPoint = target.playerPosition
            val targetBlock = instance.getBlock(target)
            val buildingBlock = instance.getBlock(buildingPoint)
            when (val playerBlock = instance.getBlock(playerPoint)) {
                Block.GRASS_BLOCK -> {
                    val canAccess = anyAdjacentBlocksMatch(playerPoint, playerData.block)
                    if (canAccess) {
                        ClaimItem.setItemSlot(e.player)
                    } else {
                        ColonyItem.setItemSlot(e.player)
                    }
                }

                Block.DIAMOND_BLOCK -> {
                    e.player.inventory.idle()
                }

                Block.SAND -> {
                    val canAccess = anyAdjacentBlocksMatch(playerPoint, playerData.block)
                    if (canAccess) {
                            ClaimWaterItem.setItemSlot(e.player)
                    } else {
                        e.player.inventory.idle()
                    }
                }

                else -> {
                    if (playerBlock == playerData.block) {
                        OwnedBlockItem.setItemSlot(e.player)
                        if (blockIsWall(targetBlock)) {
                            UpgradeWallItem.setItemSlot(e.player)
                        }
                    } else if (
                        playerBlock != playerData.block
                        && anyAdjacentBlocksMatch(playerPoint, playerData.block)
                    ) {
                        AttackItem.setItemSlot(e.player)
                    } else {
                        e.player.inventory.idle()
                    }
                }
            }
            val y40 = target.buildingPosition
            val targetParticles = mutableListOf<SendablePacket>()
            for (i in 1..5) {
                val dustParticle = Particle.DUST.withColor(Color(25, 25, 25)).withScale(0.6f)
                targetParticles += particle {
                    particle = dustParticle
                    position = y40.add(0.2 * i, 0.0, 0.0)
                    count = 1
                    offset = Vec(0.0, 0.0, 0.0)
                }
                targetParticles += particle {
                    particle = dustParticle
                    position = y40.add(0.2 * i, 0.0, 1.0)
                    count = 1
                    offset = Vec(0.0, 0.0, 0.0)
                }
                targetParticles += particle {
                    particle = dustParticle
                    position = y40.add(0.0, 0.0, 0.2 * i)
                    count = 1
                    offset = Vec(0.0, 0.0, 0.0)
                }
                targetParticles += particle {
                    particle = dustParticle
                    position = y40.add(1.0, 0.0, 0.2 * i)
                    count = 1
                    offset = Vec(0.0, 0.0, 0.0)
                }
            }
            e.player.sendPackets(targetParticles)
        } else {
            e.player.inventory.idle()
        }
    }
    log("Player loop started...")

    GlobalEventHandler.listen<PlayerDisconnectEvent> { e ->
        val file = File("./player-data.json")
        if (!file.exists()) {
            file.createNewFile()
        }
        file.writeText(Json.encodeToString(players))
        instance.saveChunksToStorage()
    }

    minecraftServer.start(config.serverAddress, config.serverPort)
    log("GameServer online!")
    packServer.start()
    log("PackServer online!")

    initConsoleCommands()
    log("Console commands initialized...")
    launch { runBlocking {
        val scanner = Scanner(System.`in`)
        while (runConsoleLoop) {
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                var last = ""
                val args = line.split(' ').mapNotNull {
                    if (it.startsWith('"')) {
                        if (it.endsWith('"')) return@mapNotNull it.drop(1).dropLast(1)
                        last = it.drop(1)
                        return@mapNotNull null
                    }
                    if (it.endsWith('"')) {
                        val ret = "$last ${it.dropLast(1)}"
                        last = ""
                        return@mapNotNull ret
                    }
                    if (last != "") {
                        last += " $it"
                        return@mapNotNull null
                    }
                    it
                }
                for (entry in Command.registry) {
                    if (entry.validate(args)) {
                        logStream.println(line)
                        entry.execute(args)
                    }
                }
            }
            delay(config.consolePollingDelay)
        }
        log("Console loop failed!")
    }}
    log("Console loop running!")
} catch (e: Exception) {
    e.printStackTrace()
    e.printStackTrace(logStream)
}}

fun log(msg: Any?) {
    val time = Time.from(Instant.now()).toString().dropLast(9).drop(11)
    println("[$time]: $msg")
    logStream.println("[$time]: $msg")
}

fun clearBlock(block: Block) {
    scheduleImmediately {
        log("clear block")
        for (x in 0..config.mapSize) {
            for (z in 0..config.mapSize) {
                instance.loadChunk(Vec(x.toDouble(), z.toDouble())).thenRun {
                    if (instance.getBlock(x, 38, z) == block) {
                        if (instance.getBlock(x, 39, z) == Block.WATER) {
                            ClaimWaterItem.destroyPlayerRaft(Vec(x.toDouble(), 39.0, z.toDouble()))
                        } else {
                            instance.setBlock(x, 39, z, Block.GRASS_BLOCK)
                        }
                        instance.setBlock(x, 40, z, Block.AIR)
                    }
                }
            }
        }
    }
}

fun Entity.getTrueTarget(maxDistance: Int, onRayStep: ((pos: Point, block: Block) -> Unit)? = null): Point? {
    val playerEyePos = position.add(0.0, eyeHeight, 0.0)
    val playerDirection = playerEyePos.direction().mul(0.5, 0.5, 0.5)
    var point = playerEyePos.asVec()
    for (i in 0..maxDistance * 2) {
        point = point.add(playerDirection)
        val block = instance.getBlock(point)
        if (onRayStep != null) onRayStep(point, block)
        if (block.defaultState() == Block.WATER) return point.roundToBlock()
        if (!block.isAir) {
            val blockShape = block.registry().collisionShape()
            if (blockShape is ShapeImpl) {
                for (box in blockShape.collisionBoundingBoxes()) {
                    if (box.boundingBoxRayIntersectionCheck(
                            point.sub(playerDirection),
                            playerDirection,
                            point.asPosition().roundToBlock()
                        )
                    ) return point.roundToBlock()
                }
            }
        }
    }
    return null
}

val Point.buildingPosition: Point get() {
    // TODO: WHEN ADDING DIFFERENT LEVELS ADD MORE CASES
    if (y() >= 39) {
        return withY(40.0)
    }
    return withY(40.0)
}

val Point.playerPosition: Point get() {
    if (y() >= 39) {
        return withY(38.0)
    }
    return withY(38.0)
}

fun scheduleImmediately(fn: () -> Unit) =
    SchedulerManager.scheduleTask(fn, TaskSchedule.immediate(), TaskSchedule.stop())

fun PlayerInventory.idle() {
    set(0, idleItem())
}

fun idleItem(): ItemStack = item(Material.GRAY_DYE) {
    itemName = "".asMini()
    amount = 1
}

fun String.toUUID(): UUID? = UUID.fromString(this)

fun anyAdjacentBlocksMatch(point: Point, block: Block): Boolean {
    var ret = false
    repeatAdjacent(point) {
        if (instance.getBlock(it) == block.defaultState()) ret = true
    }
    return ret
}
/**
 * Calls [fn] with each adjacent location relative to [point] starting from the north-west going clockwise
 */
inline fun repeatAdjacent(point: Point, fn: (point: Point) -> Unit) {
    fn(point.add(-1.0, 0.0, -1.0))
    fn(point.add(0.0, 0.0, -1.0))
    fn(point.add(1.0, 0.0, -1.0))
    fn(point.add(1.0, 0.0, 0.0))
    fn(point.add(1.0, 0.0, 1.0))
    fn(point.add(0.0, 0.0, 1.0))
    fn(point.add(-1.0, 0.0, 1.0))
    fn(point.add(-1.0, 0.0, 0.0))
}

var Entity.hasGravity: Boolean
    get() = !this.hasNoGravity()
    set(value) = this.setNoGravity(!value)

val Cooldown.ticks: Int get() = (duration.toMillis() / 50).toInt()

fun initItems() {
    BarracksItem
    ClaimItem
    ColonyItem
    MatterContainerItem
    MatterExtractorItem
    OwnedBlockItem
    SelectBlockItem
    SelectBuildingItem
    TrainingCampItem
    WallItem
    UpgradeWallItem
    BreakBuildingItem
    ClaimWaterItem
    MatterCompressorItem
}

fun initConsoleCommands() {
    ConfigCommand
    SaveCommand
}