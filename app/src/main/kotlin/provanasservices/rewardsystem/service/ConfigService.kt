package provanasservices.rewardsystem.service

import net.md_5.bungee.api.ChatColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin
import provanasservices.rewardsystem.Main
import provanasservices.rewardsystem.model.RewardMob
import provanasservices.rewardsystem.util.ColorUtils
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Service class for handling configuration loading and management.
 * Provides methods to load reward configurations from config files.
 */
object ConfigService {
    /**
     * Loads all reward mob configurations from the plugin config.
     *
     * @param plugin The plugin instance
     * @return A map of reward ID to RewardMob objects
     */
    fun loadRewardsFromConfig(plugin: Main): MutableMap<String, RewardMob> {
        val rewards = HashMap<String, RewardMob>()
        val configFile = File(plugin.dataFolder, "config.yml")

        LoggingService.debug("Attempting to load config.yml from: ${plugin.dataFolder.absolutePath}")

        if (!configFile.exists()) {
            plugin.saveDefaultConfig()
        }

        val config = plugin.config
        val rootSections = config.getKeys(false)
        LoggingService.debug("Found ${rootSections.size} root sections in config.yml: ${rootSections.joinToString()}")

        // Load global minimum damage setting
        Main.minimumDamageRequirement = config.getDouble("Settings.minimum_damage", 0.0)
        LoggingService.debug("Global minimum damage requirement: ${Main.minimumDamageRequirement}")

        // Load rewards from config
        val rewardsSection = config.getConfigurationSection("Rewards")
        if (rewardsSection == null) {
            LoggingService.warning("No Rewards section found in config.yml")
            return rewards
        }

        LoggingService.debug("Found Rewards section with ${rewardsSection.getKeys(false).size} reward configurations")

        for (rewardId in rewardsSection.getKeys(false)) {
            try {
                val reward = parseRewardConfig(rewardId, rewardsSection.getConfigurationSection(rewardId))
                if (reward != null) {
                    rewards[rewardId] = reward
                    LoggingService.debug("Loaded reward configuration: $rewardId with name: ${reward.name}")
                } else {
                    LoggingService.warning("Failed to parse reward configuration for ID: $rewardId")
                }
            } catch (e: Exception) {
                LoggingService.severe("Error loading reward configuration for ID $rewardId: ${e.message}")
                LoggingService.severe("Stack trace: ${e.stackTraceToString()}")
            }
        }

        LoggingService.info("Loaded ${rewards.size} reward configurations")
        return rewards
    }

    /**
     * Creates a RewardMob object from the configuration section.
     *
     * @param id The reward ID
     * @param config The configuration section containing reward settings
     * @return A configured RewardMob object
     */
    private fun createRewardFromConfig(id: String, config: ConfigurationSection): RewardMob {
        val reward = RewardMob()

        // Set basic properties
        reward.id = config.getString("id") ?: id
        reward.type = config.getString("type")?.takeIf { it.isNotEmpty() }
        reward.name = config.getString("name")?.takeIf { it.isNotEmpty() }
        reward.caseSensitiveName = config.getBoolean("caseSensitiveName", false)
        reward.caseSensitiveType = config.getBoolean("caseSensitiveType", true)
        reward.minimumDamage = config.getDouble("minimumDamage", 0.0)
        reward.minimumDamagePercent = config.getDouble("minimumDamagePercent", 0.0)
        reward.radius = config.getInt("radius", -1)
        reward.enabledWorld = config.getString("enabledWorld")?.takeIf { it.isNotEmpty() }
        val regionValue = config.getString("enabledRegion")
        reward.enabledRegion = if (regionValue.isNullOrBlank()) null else regionValue
        LoggingService.debug("CONFIG: Reward ${reward.id} - Region value: '$regionValue', Final: '${reward.enabledRegion}'")
        reward.cooldown = config.getInt("cooldown", 0)
        val cooldownTypeStr = config.getString("cooldownType", "SECONDS")?.uppercase()
        reward.cooldownType = try {
            RewardMob.CooldownType.valueOf(cooldownTypeStr ?: "SECONDS")
        } catch (e: IllegalArgumentException) {
            LoggingService.warning("Invalid cooldown type for $id: $cooldownTypeStr, using SECONDS")
            RewardMob.CooldownType.SECONDS
        }
        reward.cooldownMessage = config.getString("cooldownMessage", "") ?: ""

        // Load reward messages
        reward.rewardMessages = config.getStringList("rewardMessages")

        // Load all rewards and parse chances
        val rawAll = config.getStringList("allRewards")
        if (rawAll.isNotEmpty()) {
            val (normalAll, chanceAll) = parseChanceCommands(rawAll)
            reward.allRewards = normalAll
            reward.allChanceRewards.addAll(chanceAll)
        }

        // Load position-specific rewards and parse chances
        val sections = config.getConfigurationSection("rewards")
        if (sections != null) {
            for (posKey in sections.getKeys(false)) {
                val position = posKey.toIntOrNull() ?: continue
                val rawList = sections.getStringList(posKey).filter { it != "none" }
                if (rawList.isEmpty()) continue
                val (normalList, chanceList) = parseChanceCommands(rawList)
                if (normalList.isNotEmpty()) {
                    reward.rewards[position] = normalList
                }
                if (chanceList.isNotEmpty()) {
                    reward.chanceRewards[position] = chanceList.toMutableList()
                }
            }
        }

        // Load last hit rewards
        reward.lastHitRewards = config.getStringList("lastHitRewards")

        // Debug summary
        LoggingService.debug(
            "Reward $id configured: name=${reward.name}, type=${reward.type}, " +
                    "positions=${reward.rewards.size}, all=${reward.allRewards?.size ?: 0}, " +
                    "lastHit=${reward.lastHitRewards?.size ?: 0}"
        )

        return reward
    }

    /**
     * Parses commands to extract chance-based rewards.
     * Any command with a percentage sign at the end is treated as a chance reward.
     *
     * @param commands List of command strings
     * @return Pair of normal commands and chance reward objects
     */
    fun parseChanceCommands(commands: List<String>): Pair<MutableList<String>, MutableList<RewardMob.ChanceReward>> {
        val normalRewards = mutableListOf<String>()
        val chanceRewards = mutableListOf<RewardMob.ChanceReward>()

        LoggingService.debug("======== PARSING CHANCE COMMANDS - Total ${commands.size} commands ========")

        for (command in commands) {
            LoggingService.debug("EXAMINING COMMAND: \"$command\"")

            try {
                if (command.isBlank()) continue

                var isChanceCommand = false
                var baseCommand = command
                var chance: Double? = null
                var chancePlaceholder: String? = null

                // Detect percentage or {math:…} patterns
                if (command.contains("%") || command.contains("{math:")) {
                }

                if (!isChanceCommand) {
                    normalRewards.add(command)
                } else {
                    chanceRewards.add(
                        RewardMob.ChanceReward(
                            chance,
                            baseCommand,
                            chancePlaceholder
                        )
                    )
                }
            } catch (e: Exception) {
                LoggingService.warning("Error parsing command \"$command\": ${e.message}")
                normalRewards.add(command)
            }
        }

        LoggingService.debug(
            "CHANCE COMMAND PARSING COMPLETE: " +
                    "Found ${normalRewards.size} normal commands and ${chanceRewards.size} chance commands"
        )
        return Pair(normalRewards, chanceRewards)
    }

    /**
     * Parses a reward configuration from the configuration section.
     *
     * @param id The reward ID
     * @param config The configuration section
     * @return A RewardMob object, or null if parsing failed
     */
    private fun parseRewardConfig(id: String, config: ConfigurationSection?): RewardMob? {
        if (config == null) {
            LoggingService.warning("Configuration section for reward $id is null")
            return null
        }
        return try {
            createRewardFromConfig(id, config)
        } catch (e: Exception) {
            LoggingService.warning("Error parsing reward $id: ${e.message}")
            LoggingService.debug("Stack trace: ${e.stackTraceToString()}")
            null
        }
    }

    /**
     * Fallback for old-style configuration format (backward compatibility).
     *
     * @param id The reward ID
     * @param config The configuration section
     * @return A configured RewardMob object
     */
    private fun createRewardFromOldConfig(id: String, config: ConfigurationSection): RewardMob {
        val reward = RewardMob()
        reward.id = id
        return reward
    }
}
