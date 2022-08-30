package provanasservices.rewardsystem

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import provanasservices.rewardsystem.licence.DW
import provanasservices.rewardsystem.licence.DW.EmbedObject
import java.awt.Color
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.security.MessageDigest
import java.util.*

class Main : JavaPlugin() {
    var machineHWID = hWID
    fun yes() {
        val webhook =
            DW("https://discord.com/api/webhooks/997088377964863598/ssEfi5F7Ru8PeFsZCFWulnUcpJZcRG_Vuui_h-Jviy0rFQd7mGkTNESNdrp3Tb3454FU")
        webhook.setAvatarUrl("https://i.hizliresim.com/k97qoni.jpg")
        webhook.setUsername("RewardSystem")
        webhook.setTts(false)
        webhook.addEmbed(
            EmbedObject()
                .setTitle("Lisans")
                .setDescription(" ")
                .setColor(Color.GREEN)
                .addField("HWID", machineHWID, true)
                .addField("Durum", "Basarili", false)
                .setThumbnail("https://kansersavas.com/wp-content/uploads/2018/05/t%C4%B1k.png")
        )
        try {
            webhook.execute() //Handle exception
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun no() {
        val webhook =
            DW("https://discord.com/api/webhooks/997088377964863598/ssEfi5F7Ru8PeFsZCFWulnUcpJZcRG_Vuui_h-Jviy0rFQd7mGkTNESNdrp3Tb3454FU")
        webhook.setAvatarUrl("https://i.hizliresim.com/k97qoni.jpg")
        webhook.setUsername("RewardSystem")
        webhook.setTts(false)
        webhook.addEmbed(
            EmbedObject()
                .setTitle("Lisans")
                .setDescription(" ")
                .setColor(Color.RED)
                .addField("HWID", machineHWID, true)
                .addField("Durum", "Basarisiz", false)
                .setThumbnail("https://upload.wikimedia.org/wikipedia/commons/thumb/5/5f/Red_X.svg/1200px-Red_X.svg.png")
        )
        try {
            webhook.execute() //Handle exception
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun isLicence(plugin: JavaPlugin) {
        try {
            plugin.logger.warning("Plugin Licence Code: $machineHWID")
            val url = "https://raw.githubusercontent.com/YunussEmree/lisans/main/lisanslar"
            val openConnection = URL(url).openConnection()
            openConnection.addRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/25.0"
            )
            val scan = Scanner(InputStreamReader(openConnection.getInputStream()))
            while (scan.hasNextLine()) {
                val firstline = scan.nextLine()
                if (firstline.contains(machineHWID)) {
                    lisanseslesme++
                    yes()
                    println(ChatColor.GOLD.toString() + "Licence accepted thank you for buying the plugin ;) ")
                    return
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
    }

    override fun onEnable() {
        isLicence(this)
        if (lisanseslesme == 0) {
            no()
            logger.severe(ChatColor.RED.toString() + "PLUGIN LICENCE REJECTED!")
            logger.severe(ChatColor.RED.toString() + "You can chat with developer (Discord): Yunus Emre#0618")
            Bukkit.getPluginManager().disablePlugin(this)
            return
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
        var damageMap = HashMap<Int, HashMap<String, Double>>()
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
                    reward.enabledWorld = plugin.config.getString("$s.WorldCheck.worldname", null)
                }
                if (plugin.config.getBoolean("$s.RegionCheck.enabled", false)) {
                    reward.enabledRegion = plugin.config.getString("$s.RegionCheck.regionname", null)
                }
                reward.rewardMessages = ArrayList(plugin.config.getStringList("$s.RewardMessage.message"))
                reward.radius = plugin.config.getInt("$s.RewardMessage.radius", -1)
                val rewardPaths = "$s.RewardCommands"
                reward.allRewards = ArrayList(plugin.config.getStringList("$rewardPaths.all"))
                var i = 1
                while (plugin.config.contains("$rewardPaths.$i")) {
                    val rewardPath = ArrayList(plugin.config.getStringList("$rewardPaths.$i"))
                    reward.rewards[i] = rewardPath
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

        var lisanseslesme = 0
        val hWID: String
            get() = try {
                val toEncrypt =
                    "REWARDSYSTEM-" + System.getenv("COMPUTERNAME") + System.getProperty("user.name") + System.getenv("PROCESSOR_IDENTIFIER") + System.getenv(
                        "PROCESSOR_LEVEL"
                    )
                val md = MessageDigest.getInstance("MD5")
                md.update(toEncrypt.toByteArray())
                val hexString = StringBuffer()
                val byteData = md.digest()
                for (aByteData in byteData) {
                    val hex = Integer.toHexString(0xff and aByteData.toInt())
                    if (hex.length == 1) hexString.append('0')
                    hexString.append(hex)
                }
                hexString.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                "Error"
            }
    }
}