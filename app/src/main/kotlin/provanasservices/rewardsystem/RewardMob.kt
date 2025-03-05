package provanasservices.rewardsystem

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import net.md_5.bungee.api.ChatColor

class RewardMob {
    var id: String = ""
    var radius = -1
    var rewardMessages: List<String>? = null
    var name: String? = null
    var type: String? = null
    var enabledWorld: String? = null
    var enabledRegion: String? = null
    var allRewards: List<String>? = null
    var rewards = HashMap<Int, List<String>>()
    val chanceRewards = HashMap<Int, List<ChanceReward>>()
    var allChanceRewards = ArrayList<ChanceReward>()
    var minimumDamage = 0.0
    var cooldown = 0
    var cooldowns: MutableMap<UUID, Long> = Collections.synchronizedMap(HashMap())
    var cooldownMessage = ""
    fun nameEquals(name: String): Boolean {
        return if (this.name == null) true else {
            val strippedInputName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name))
            val strippedConfigName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', this.name!!))

            val plugin = Main.getInstance()
            if (plugin.config.getBoolean("Debug.enabled")) {
                plugin.logger.info("[REWARDSYSTEM DEBUG] Name comparison - Input: '$name', Config: '${this.name}'")
                plugin.logger.info("[REWARDSYSTEM DEBUG] Stripped - Input: '$strippedInputName', Config: '$strippedConfigName'")
            }

            this.name == name || strippedConfigName == strippedInputName
        }
    }

    fun typeEquals(type: String): Boolean {
        return if (this.type == null) true else this.type == type
    }

    fun worldEquals(world: String): Boolean {
        return if (enabledWorld == null) true else enabledWorld == world
    }

    data class ChanceReward(val chance: Double?, val commands: String, val chancePlaceholder: String? = null)
}