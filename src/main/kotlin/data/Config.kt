package io.github.flyingpig525.data

import io.github.flyingpig525.DASH_BANNER
import kotlinx.serialization.Serializable

// These comments will eventually be used for wiki stuff i guess
@Serializable
data class Config(
    // Server address for game server and pack server, set to your servers public ip
    val serverAddress: String = "0.0.0.0",
    // Server port for game server, should always stay as 25565
    val serverPort: Int = 25565,
    // Server port for resource pack server, recommended to stay 25566
    val packServerPort: Int = 25566,
    // List of usernames to be on the whitelist
    val whitelisted: Set<String> = emptySet(),
    // Message to show non-whitelisted players
    val notWhitelistedMessage: String = "<red><bold>Player not whitelisted\n</bold><grey>$DASH_BANNER\n<gold><bold>Please contact the server owner if you believe this is a mistake",
    // Operator usernames
    val opUsernames: MutableSet<String> = mutableSetOf(),
    // Operator UUIDs (autogenerated)
    val opUUID: MutableSet<String> = mutableSetOf(),
    // Whether to print a message on auto save
    val printSaveMessages: Boolean = false,
    // Delay in which the process check the console for commands in milliseconds (set >5000)
    val consolePollingDelay: Long = 5000,
    // Scale for generation noise
    val noiseScale: Double = 0.03,
    // Threshold for noise result to be considered grass, else water
    val noiseThreshold: Double = 0.35,
    // Seed for noise, randomly generated when config is created. Change when resetting map
    val noiseSeed: Long = (Long.MIN_VALUE..Long.MAX_VALUE).random(),
    // Length and width of map (will always be a square)
    val mapSize: Int = 1000
)