package provanasservices.rewardsystem

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import provanasservices.rewardsystem.Licence.Companion.evaluateLicence
import java.awt.Color
import java.util.UUID

class Main : JavaPlugin() {
    override fun onEnable() {
        logger.info(ChatColor.GREEN.toString() + "Plugin startup")
        if(!Licence.parseYAMLAndCheckLicenceCode(this)) {
            evaluateLicence(Color.RED, "Başarısız", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5f/Red_X.svg/1200px-Red_X.svg.png")
            logger.severe("PLUGIN LICENCE REJECTED!")
            logger.severe("Could You Contact With Plugin Developers? (Discord): 'blestit' 'metumortis'")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }
        else {
            evaluateLicence(Color.GREEN, "Başarılı", "https://kansersavas.com/wp-content/uploads/2018/05/t%C4%B1k.png")
            logger.info(ChatColor.GREEN.toString() + "PLUGIN LICENCE ACCEPTED!")
            logger.info(ChatColor.GREEN.toString() + "You Can Contact With Developers For Anything (Discord): 'blestit' 'metumortis'")
        }

        // Plugin startup logic
        PLACEHOLDERAPI_ENABLED = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
        logger.info(ChatColor.GREEN.toString() + "Plugin startup")
        server.pluginManager.registerEvents(Events(this), this)
        reloadConfig()
        saveDefaultConfig()
        rewardsFromConfig = getRewardsFromConfig(this)
        getCommand("rewardsystem")!!.setExecutor(CommandHandler(this))
    }

    override fun onDisable() {
        // Plugin shutdown logic
        logger.info(ChatColor.RED.toString() + "Plugin Shutdown")
    }

    companion object {
        var minimumDamageRequirement: Double = 0.0 // Default value
        var PLACEHOLDERAPI_ENABLED = false
        @JvmField
        var rewardsFromConfig: ArrayList<RewardMob>? = null
        @JvmField
        val lastToucherMap = HashMap<UUID, String>()
        @JvmField
        var damageMap = HashMap<UUID, HashMap<String, Double>>()
        @JvmField
        val uuidMap = HashMap<Int, HashSet<UUID>>()
        @JvmStatic
        fun translateColors(string: String?): String {
            if (string == null) return ""
            var parsedStr = string.replace("\\{(#[0-9A-f]{6})\\}".toRegex(), "&$1")
            if ("&#[0-9A-f]{6}".toRegex().containsMatchIn(parsedStr)) {
                for (x in "&(#[0-9A-f]{6})".toRegex().findAll(parsedStr)) {
                    parsedStr = parsedStr.replaceFirst(
                        x.value.toRegex(),
                        net.md_5.bungee.api.ChatColor.of(
                            x.value.slice(
                                1 until x.value.length
                            )
                        ).toString()
                    )
                }
            }
            return ChatColor.translateAlternateColorCodes('&', parsedStr)
        }
        
        @JvmStatic
        fun getRewardsFromConfig(plugin: Plugin): ArrayList<RewardMob> {
            val rewards = ArrayList<RewardMob>()
            for (s in plugin.config.getKeys(true).filter { el: String -> el.matches(Regex("^RewardSystem\\.[0-9]+$")) }.toTypedArray()){
                val reward = RewardMob()
                if (plugin.config.getBoolean("$s.NameCheck.enabled", false)) {
                    reward.name = translateColors(plugin.config.getString("$s.NameCheck.name", null))
                }
                if (plugin.config.getBoolean("$s.MobTypeCheck.enabled", false)) {
                    reward.type = plugin.config.getString("$s.MobTypeCheck.type", null)
                }
                if (plugin.config.getBoolean("$s.WorldCheck.enabled", false)) {
                    reward.enabledWorld = plugin.config.getString("$s.WorldCheck.worldName", null)
                }
                if (plugin.config.getBoolean("$s.RegionCheck.enabled", false)) {
                    reward.enabledRegion = plugin.config.getString("$s.RegionCheck.regionName", null)
                }
                reward.rewardMessages = ArrayList(plugin.config.getStringList("$s.RewardMessage.message"))
                reward.minimumDamage = plugin.config.getDouble("$s.MinimumDamageRequirement", 0.0)
                reward.radius = plugin.config.getInt("$s.RewardMessage.radius", -1)
                val rewardPaths = "$s.RewardCommands"
                val allChanceRewards = plugin.config.getStringList("$rewardPaths.all")
                val (filteredChanceRewardsToAll, definiteRewardsToAll) = allChanceRewards.partition { rewardString ->
                    val rewardArgs = rewardString.split(" ")
                    val lastRewardArg = rewardArgs.last()
                    if(lastRewardArg.endsWith("%")) {
                        val chance = lastRewardArg.replace("%", "").toDoubleOrNull()
                        return@partition chance != null
                    }
                    if(PLACEHOLDERAPI_ENABLED) return@partition lastRewardArg.matches(Regex("""\{\w+\}"""));
                    return@partition false
                }
                reward.allRewards = definiteRewardsToAll
                val mappedChanceRewardsToAll = filteredChanceRewardsToAll.map {
                    if(it.endsWith("%")) {
                        val chance = it.split(" ").last().replace("%", "").toDouble()
                        val command = it.replace(" $chance%", "")
                        RewardMob.ChanceReward(chance, command)
                    }
                    else {
                        val chanceString = it.split(" ").last()
                        val command = it.split(" ").dropLast(1).joinToString(" ")
                        RewardMob.ChanceReward(null, command, chanceString)
                    }
                }
                reward.allChanceRewards = ArrayList(mappedChanceRewardsToAll)
                var i = 1
                while (plugin.config.contains("$rewardPaths.$i")) {
                    val rewardPath = ArrayList(plugin.config.getStringList("$rewardPaths.$i"))

                    val (filteredChanceRewards, definiteRewards) = rewardPath.partition { rewardString ->
                        val rewardArgs = rewardString.split(" ")
                        val lastRewardArg = rewardArgs.last()
                        if(lastRewardArg.endsWith("%")) {
                            val chance = lastRewardArg.replace("%", "").toDoubleOrNull()
                            return@partition chance != null
                        }
                        if(PLACEHOLDERAPI_ENABLED) return@partition lastRewardArg.matches(Regex("""\{\w+\}"""));
                        return@partition false
                    }
                    reward.rewards[i] = definiteRewards

                    val mappedChanceRewards = filteredChanceRewards.map {
                        if(it.endsWith("%")) {
                            val chance = it.split(" ").last().replace("%", "").toDouble()
                            val command = it.replace(" $chance%", "")
                            RewardMob.ChanceReward(chance, command)
                        }
                        else {
                            val chanceString = it.split(" ").last()
                            val command = it.split(" ").dropLast(1).joinToString(" ")
                            RewardMob.ChanceReward(null, command, chanceString)
                        }
                    }
                    reward.chanceRewards[i] = mappedChanceRewards
                    i++
                }
                /*
                i = 1
                while (plugin.config.contains("$s.ChanceRewards.$i")){
                    val chance = plugin.config.getString("$s.ChanceRewards.$i.chance", "%100")
                    val rewardPath = ArrayList(plugin.config.getStringList("$s.ChanceRewards.$i.commands"))
                    reward.chanceRewards[rewardPath] = chance!!.replace("%", "").toInt()
                    i++
                }
                */
                uuidMap[s.replace("[^0-9]".toRegex(), "").toInt()] = HashSet()
                rewards.add(reward)
            }
            return rewards
        }
    }
}
