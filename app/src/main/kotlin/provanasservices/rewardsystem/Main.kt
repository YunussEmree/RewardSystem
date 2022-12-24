package provanasservices.rewardsystem

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import provanasservices.rewardsystem.Licence.Companion.evaluateLicence
import java.awt.Color

class Main : JavaPlugin() {
    override fun onEnable() {
        logger.info(ChatColor.GREEN.toString() + "Plugin startup")
        if(!Licence.parseYAMLAndCheckLicenceCode(this)) {
            evaluateLicence(Color.RED, "Başarısız", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5f/Red_X.svg/1200px-Red_X.svg.png")
            logger.severe("PLUGIN LICENCE REJECTED!")
            logger.severe("You can chat with developer (Discord): Yunus Emre#0618 MetuMortis#4431")
            Bukkit.getPluginManager().disablePlugin(this)
            return
        }
        else {
            evaluateLicence(Color.GREEN, "Başarılı", "https://kansersavas.com/wp-content/uploads/2018/05/t%C4%B1k.png")
            logger.info(ChatColor.GREEN.toString() + "PLUGIN LICENCE ACCEPTED!")
            logger.info(ChatColor.GREEN.toString() + "You can chat with developer (Discord): Yunus Emre#0618")
        }

        // Plugin startup logic
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
        @JvmField
        var rewardsFromConfig: ArrayList<RewardMob>? = null
        @JvmField
        val lastToucherMap = HashMap<Int, String>()
        @JvmField
        val damageMap = HashMap<Int, HashMap<String, Double>>()
        @JvmStatic
        fun translateColors(message: String?): String {
            return ChatColor.translateAlternateColorCodes('&', message!!)
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
                reward.radius = plugin.config.getInt("$s.RewardMessage.radius", -1)
                val rewardPaths = "$s.RewardCommands"
                reward.allRewards = ArrayList(plugin.config.getStringList("$rewardPaths.all"))
                var i = 1
                while (plugin.config.contains("$rewardPaths.$i")) {
                    val rewardPath = ArrayList(plugin.config.getStringList("$rewardPaths.$i"))

                    val (chanceRewards, definiteRewards) = rewardPath.partition { it.endsWith("%") }

                    reward.rewards[i] = definiteRewards

                    val filteredChanceRewards = chanceRewards.filter { it.split(" ").last().replace("%", "").toIntOrNull() != null }
                    val mappedChanceRewards = filteredChanceRewards.map {
                        val chance = it.split(" ").last().replace("%", "").toInt()
                        val command = it.replace(" $chance%", "")
                        RewardMob.ChanceReward(chance, command)
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
                damageMap[s.replace("[^0-9]".toRegex(), "").toInt()] = HashMap()
                rewards.add(reward)
            }
            return rewards
        }
    }
}