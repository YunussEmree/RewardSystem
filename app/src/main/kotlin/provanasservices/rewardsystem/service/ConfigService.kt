package provanasservices.rewardsystem.service

import net.md_5.bungee.api.ChatColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin
import provanasservices.rewardsystem.Main
import provanasservices.rewardsystem.model.RewardMob
import provanasservices.rewardsystem.service.LoggingService
import provanasservices.rewardsystem.util.ColorUtils
import java.io.File
import java.util.*
import kotlin.collections.HashMap

/**
 * Service class for handling configuration loading and management.
 * Provides methods to load reward configurations from config files.
 */
object ConfigService {
    /**
     * Loads all reward configurations from config.yml.
     */
    fun loadRewardsFromConfig(plugin: Main): MutableMap<String, RewardMob> {
        val rewards = HashMap<String, RewardMob>()
        val configFile = File(plugin.dataFolder, "config.yml")
        if (!configFile.exists()) plugin.saveDefaultConfig()
        val config = plugin.config

        // Global minimum damage
        Main.minimumDamageRequirement = config.getDouble("Settings.minimum_damage", 0.0)
        LoggingService.debug("Global minimum damage requirement: ${Main.minimumDamageRequirement}")

        val rewardsSection = config.getConfigurationSection("Rewards")
        if (rewardsSection == null) {
            LoggingService.warning("No Rewards section found in config.yml")
            return rewards
        }

        for (rewardId in rewardsSection.getKeys(false)) {
            try {
                val reward = parseRewardConfig(rewardId, rewardsSection.getConfigurationSection(rewardId))
                if (reward != null) {
                    rewards[rewardId] = reward
                    LoggingService.debug("Loaded reward configuration: $rewardId with name: ${'$'}{reward.name}")
                } else {
                    LoggingService.warning("Failed to parse reward configuration for ID: $rewardId")
                }
            } catch (e: Exception) {
                LoggingService.severe("Error loading reward configuration for ID $rewardId: ${'$'}{e.message}")
            }
        }
        LoggingService.info("Loaded ${'$'}{rewards.size} reward configurations")
        return rewards
    }

    /**
     * Builds a RewardMob from its ConfigurationSection.
     */
    private fun createRewardFromConfig(id: String, config: ConfigurationSection): RewardMob {
        val reward = RewardMob()
        reward.id = config.getString("id") ?: id
        reward.type = config.getString("type")?.takeIf { it.isNotEmpty() }
        reward.name = config.getString("name")?.takeIf { it.isNotEmpty() }
        reward.caseSensitiveName = config.getBoolean("caseSensitiveName", false)
        reward.caseSensitiveType = config.getBoolean("caseSensitiveType", true)
        reward.minimumDamage = config.getDouble("minimumDamage", 0.0)
        reward.minimumDamagePercent = config.getDouble("minimumDamagePercent", 0.0)
        reward.radius = config.getInt("radius", -1)
        reward.enabledWorld = config.getString("enabledWorld")?.takeIf { it.isNotEmpty() }
        val regionVal = config.getString("enabledRegion")
        reward.enabledRegion = if (regionVal.isNullOrBlank()) null else regionVal
        LoggingService.debug("CONFIG: Reward ${'$'}{reward.id} - Region: '${'$'}regionVal' -> '${'$'}{reward.enabledRegion}'")

        reward.cooldown = config.getInt("cooldown", 0)
        val cdType = config.getString("cooldownType", "SECONDS")?.uppercase()
        reward.cooldownType = try {
            RewardMob.CooldownType.valueOf(cdType ?: "SECONDS")
        } catch (e: IllegalArgumentException) {
            LoggingService.warning("Invalid cooldown type for $id: $cdType, using SECONDS")
            RewardMob.CooldownType.SECONDS
        }
        reward.cooldownMessage = config.getString("cooldownMessage", "") ?: ""
        reward.rewardMessages = config.getStringList("rewardMessages")

        // Parse allRewards with chances
        val rawAll = config.getStringList("allRewards")
        if (rawAll.isNotEmpty()) {
            val (norm, chance) = parseChanceCommands(rawAll)
            reward.allRewards = norm
            reward.allChanceRewards.addAll(chance)
        }

        // Parse position-specific rewards
        val section = config.getConfigurationSection("rewards")
        if (section != null) {
            for (posKey in section.getKeys(false)) {
                val pos = posKey.toIntOrNull() ?: continue
                val list = section.getStringList(posKey).filter { it != "none" }
                if (list.isEmpty()) continue
                val (norm, chance) = parseChanceCommands(list)
                if (norm.isNotEmpty()) reward.rewards[pos] = norm
                if (chance.isNotEmpty()) reward.chanceRewards[pos] = chance.toMutableList()
            }
        }

        reward.lastHitRewards = config.getStringList("lastHitRewards")
        LoggingService.debug("Reward $id summary: all=${'$'}{reward.allRewards.size}, pos=${'$'}{reward.rewards.size}, last=${'$'}{reward.lastHitRewards.size}")
        return reward
    }

/**
 * Splits commands into guaranteed and chance-based rewards.
 */
    /**
     * Splits commands into guaranteed and chance-based rewards.
     */
    fun parseChanceCommands(commands: List<String>): Pair<MutableList<String>, MutableList<RewardMob.ChanceReward>> {
        val normal = mutableListOf<String>()
        val chances = mutableListOf<RewardMob.ChanceReward>()
        LoggingService.debug("Parsing ${'$'}{commands.size} chance commands...")

        val percentRx = Regex("^(.+?)\\s+(\\d+(?:\\.\\d+)?)%$")
        val mathRx    = Regex("^(.+?)\\s+\\{math:(.+?)\\}%$")

        for (cmd in commands) {
            val t = cmd.trim()
            when {
                mathRx.matches(t) -> {
                    val (base, expr) = mathRx.find(t)!!.destructured
                    LoggingService.debug("Math chance: '$base' -> expr=$expr")
                    chances.add(RewardMob.ChanceReward(null, base, expr))
                }
                percentRx.matches(t) -> {
                    val (base, pct) = percentRx.find(t)!!.destructured
                    LoggingService.debug("Percent chance: '$base' -> $pct%")
                    chances.add(RewardMob.ChanceReward(pct.toDouble(), base, null))
                }
                else -> {
                    LoggingService.debug("Normal command: '$cmd'")
                    normal.add(cmd)
                }
            }
        }
        LoggingService.debug("Parsed: ${'$'}{normal.size} normal, ${'$'}{chances.size} chance")
        return normal to chances
    }

    private fun parseRewardConfig(id: String, config: ConfigurationSection?): RewardMob? {
        if (config == null) {
            LoggingService.warning("Config section for $id is null")
            return null
        }
        return try {
            createRewardFromConfig(id, config)
        } catch (e: Exception) {
            LoggingService.warning("Error parsing reward $id: ${'$'}{e.message}")
            null
        }
    }
}
