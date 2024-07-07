package provanasservices.rewardsystem

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.managers.RegionManager
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import com.sk89q.worldguard.protection.regions.RegionContainer
import me.clip.placeholderapi.PlaceholderAPI
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import provanasservices.rewardsystem.Main.Companion.PLACEHOLDERAPI_ENABLED
import provanasservices.rewardsystem.Main.Companion.lastToucherMap
import provanasservices.rewardsystem.Main.Companion.translateColors
import provanasservices.rewardsystem.Main.Companion.uuidMap
import java.util.*
import java.util.function.Consumer
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.roundToInt
import kotlin.random.Random

class Events(private var plugin: Main) : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val entity: Entity = event.entity
        val damager: Entity = event.damager
        if (damager is Player && entity is Mob) {
            val player: Player = damager
            val container: RegionContainer = WorldGuard.getInstance().platform.regionContainer
            for (reward in Main.rewardsFromConfig!!.values) {
                if (reward.nameEquals(entity.name) && reward.typeEquals(entity.type.name) && reward.worldEquals(entity.world.name)) {
                    if (reward.enabledWorld != null) {
                        val world: World = Bukkit.getWorld(reward.enabledWorld!!)!!
                        if (reward.enabledRegion != null) {
                            val regions: RegionManager? = container.get(BukkitAdapter.adapt(world))
                            val region: ProtectedRegion? = regions?.getRegion(reward.enabledRegion)
                            if (region!!.contains(BukkitAdapter.asBlockVector(entity.location))) {
                                addToDamageMap(event, player)
                            }
                        } else {
                            addToDamageMap(event, player)
                        }
                    } else {
                        addToDamageMap(event, player)
                    }
                }
            }
        } else if (damager is Arrow) {
            val proj: Projectile = event.damager as Projectile
            if (proj.shooter !is Player) return
            if (entity !is Mob) return
            val player: Player = proj.shooter as Player
            for (reward in Main.rewardsFromConfig!!.values) {
                if (reward.nameEquals(entity.name) && reward.typeEquals(entity.type.name) && reward.worldEquals(entity.world.name)) {
                    if (reward.enabledWorld != null) {
                        val world: World = Bukkit.getWorld(reward.enabledWorld!!)!!
                        if (reward.enabledRegion != null) {
                            val container: RegionContainer = WorldGuard.getInstance().platform.regionContainer
                            val regions: RegionManager? = container.get(BukkitAdapter.adapt(world))
                            val region: ProtectedRegion? = regions!!.getRegion(reward.enabledRegion)
                            if (region!!.contains(BukkitAdapter.asBlockVector(entity.location))) {
                                uuidMap[reward.id]!!.add(event.entity.uniqueId)
                                addToDamageMap(event, player)
                            }
                        } else {
                            uuidMap[reward.id]!!.add(event.entity.uniqueId)
                            addToDamageMap(event, player)
                        }
                    } else {
                        uuidMap[reward.id]!!.add(event.entity.uniqueId)
                        addToDamageMap(event, player)
                    }
                }
            }
        } else {
            return
        }
    }

    @EventHandler
    fun onDeath(event: EntityDeathEvent) {
        val entity: Entity = event.entity
        //val player: Player? = event.entity.killer
        for (reward in Main.rewardsFromConfig!!.values) {
            if (reward.nameEquals(entity.name) && reward.typeEquals(entity.type.name) && reward.worldEquals(entity.world.name)) {
                if (reward.enabledWorld != null) {
                    val world: World = Bukkit.getWorld(reward.enabledWorld!!)!!
                    if (reward.enabledRegion != null) {
                        val container: RegionContainer = WorldGuard.getInstance().platform.regionContainer
                        val regions: RegionManager? = container.get(BukkitAdapter.adapt(world))
                        val region: ProtectedRegion? = regions?.getRegion(reward.enabledRegion)
                        if (region!!.contains(BukkitAdapter.asBlockVector(entity.location))) {
                            giveRewards(entity.uniqueId, reward)
                        }
                    } else {
                        giveRewards(entity.uniqueId, reward)
                    }
                } else {
                    giveRewards(entity.uniqueId, reward)
                }
                val selectedMap = Main.damageMap[event.entity.uniqueId]!!
                val finalDamager = Main.lastToucherMap[event.entity.uniqueId] ?: plugin.config.getString("no_one")!!
                if(selectedMap.isEmpty()){
                    plugin.logger.info(translateColors("&cNot giving rewards because no players have damaged the ${entity.name}."))
                }
                if (reward.radius == -1 && selectedMap.isNotEmpty()) {
                    if(selectedMap.isEmpty()) return
                    reward.rewardMessages!!.forEach { message: String ->
                        Bukkit.getOnlinePlayers().forEach { onlinePlayer: Player ->
                            onlinePlayer.sendMessage(
                                translateColors(replacePlaceholders(message, onlinePlayer.name, selectedMap, finalDamager, plugin.config.getString("no_one")!!))
                            )
                        }
                    }
                } else {
                    val nearPlayers = entity.getNearbyEntities(reward.radius.toDouble(), reward.radius.toDouble(), reward.radius.toDouble()).filterIsInstance<Player>()
                    if(nearPlayers.isNotEmpty() && selectedMap.isNotEmpty()) {
                        reward.rewardMessages!!.forEach { message: String ->
                            nearPlayers.forEach { onlinePlayer: Player ->

                                onlinePlayer.sendMessage(
                                    translateColors(replacePlaceholders(message, onlinePlayer.name, selectedMap, finalDamager, plugin.config.getString("no_one")!!))
                                )
                            }
                        }
                    }
                }
                Main.damageMap[event.entity.uniqueId]!!.clear()
            }
        }
    }

    private fun giveRewards(uuid: UUID, reward: RewardMob) {
        var selectedDamageMap = Main.damageMap[uuid] ?: return
        val mobType = Bukkit.getEntity(uuid)?.type ?: return
        var minimumdamage = plugin.config.getDouble("minimum_damage")
        Bukkit.getServer().pluginManager.callEvent(RewardSystemMobDieEvent(selectedDamageMap, reward.id))
        val entrySet = ArrayList<Map.Entry<String, Double>>(selectedDamageMap.entries)

        entrySet.sortWith { (_, value): Map.Entry<String?, Double>, (_, value1): Map.Entry<String?, Double> ->
            value1.compareTo(
                value
            )
        }

        for (index in 0 until selectedDamageMap.size) {
            val rewardIndex = index + 1
            val (key, value) = entrySet[index]

            if(value < reward.minimumDamage){
                continue
            }



            val player :Player = Bukkit.getPlayerExact(key) ?: continue
            println("Unique id of playeer is ${player.uniqueId}")
            val uuid = player.getUniqueId()


            if(reward.cooldown != 0 && reward.cooldowns[uuid] != null && reward.cooldowns[uuid]!! > System.currentTimeMillis()){
                 if (!player.hasPermission("rewardsystem.cooldown.bypass") && !player.isOp) {
                    player.sendMessage(translateColors(reward.cooldownMessage).replace("%time%", ((reward.cooldowns[uuid]!! - System.currentTimeMillis()) / 1000).toString()))
                    continue
                }
            }


            reward.cooldowns[uuid] = System.currentTimeMillis() + reward.cooldown * 1000

            reward.allRewards?.forEach(Consumer { allReward: String ->
                Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    allReward.replace("%player%", key).replace("%damage%", value.toString()),
                )
            })
            reward.allChanceRewards.forEach { (chance, allChanceReward, chancePlaceholder) ->
                val random = Random.nextDouble(100.0)
                if(chance != null) {
                    if(random <= chance){
                        if(PLACEHOLDERAPI_ENABLED) {
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                PlaceholderAPI.setBracketPlaceholders(Bukkit.getPlayer(key), allChanceReward.replace("%player%", key).replace("%damage%", value.toString()))
                            )
                        }
                        else {
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                allChanceReward.replace("%player%", key).replace("%damage%", value.toString())
                            )
                        }
                    }
                }
                else if(chancePlaceholder != null) {
                    val chanceExtracted = PlaceholderAPI.setBracketPlaceholders(Bukkit.getPlayer(key), chancePlaceholder)
                    if(random <= chanceExtracted.toDouble()){
                        if(PLACEHOLDERAPI_ENABLED) {
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                PlaceholderAPI.setBracketPlaceholders(Bukkit.getPlayer(key), allChanceReward.replace("%player%", key).replace("%damage%", value.toString()))
                            )
                        }
                        else {
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                allChanceReward.replace("%player%", key).replace("%damage%", value.toString())
                            )
                        }
                    }
                }
            }
            reward.rewards[rewardIndex]?.forEach(Consumer { rewardString: String ->
                Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    rewardString.replace("%player%", key).replace("%damage%", value.toString())
                )
            })
            reward.chanceRewards[rewardIndex]?.forEach { (chance, rewardString, chancePlaceholder) ->
                val random = Random.nextDouble(100.0)
                if(chance != null) {
                    if(random <= chance){
                        if(PLACEHOLDERAPI_ENABLED) {
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                PlaceholderAPI.setBracketPlaceholders(Bukkit.getPlayer(key), rewardString.replace("%player%", key).replace("%damage%", value.toString()))
                            )
                        }
                        else {
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                rewardString.replace("%player%", key).replace("%damage%", value.toString())
                            )
                        }
                    }
                }
                else if(chancePlaceholder != null) {
                    val chanceExtracted = PlaceholderAPI.setBracketPlaceholders(Bukkit.getPlayer(key), chancePlaceholder)
                    if(random <= chanceExtracted.toDouble()){
                        if(PLACEHOLDERAPI_ENABLED) {
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                PlaceholderAPI.setBracketPlaceholders(Bukkit.getPlayer(key), rewardString.replace("%player%", key).replace("%damage%", value.toString()))
                            )
                        }
                        else {
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                rewardString.replace("%player%", key).replace("%damage%", value.toString())
                            )
                        }
                    }
                }

            }
            /*
            reward.chanceRewards.entries.forEach { (chanceRewards, chance) ->
                val random = Random.nextInt(100)
                if(random <= chance){
                    chanceRewards.forEach { chanceReward: String ->
                        Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(),
                            chanceReward.replace("%player%", key).replace("%damage%", value.toString())
                        )
                    }
                }

            }
            TODO("Şansa bağlı ödüller test edilecek")
            */
        }
        println(reward.cooldowns)
    }

    private fun addToDamageMap(event: EntityDamageByEntityEvent, player: Player) {
        var damageMap = Main.damageMap[event.entity.uniqueId]
        if(damageMap == null) Main.damageMap[event.entity.uniqueId] = HashMap()
        damageMap = Main.damageMap[event.entity.uniqueId]!!
        var selectedDamage = damageMap.get(player.name)
        if (selectedDamage == null) selectedDamage = 0.0
        selectedDamage += event.finalDamage
        damageMap[player.name] = selectedDamage
        if(event.entity is Mob){
            if((event.entity as Mob).health - event.finalDamage < 0) lastToucherMap[event.entity.uniqueId] = player.name
        }
    }


    @EventHandler
    fun debug(event: EntityDamageByEntityEvent) {
        val entity: Entity = event.entity
        val damager: Entity = event.damager
        if (damager is Player) {
            val player: Player = damager
            if (player.isOp) {
                if (plugin.config.getBoolean("Debug.enabled")) {
                    val world = entity.location.world!!.name

                    val type = entity.type.name
                    val name = entity.name
                    player.sendMessage("§a§l-------[REWARD SYSTEM DEBUG MESSAGE]-------")
                    player.sendMessage(ChatColor.AQUA.toString() + "if you wont see this message, you should set debug: false in config.yml")
                    player.sendMessage(ChatColor.AQUA.toString() + " ")
                    player.sendMessage(ChatColor.BLUE.toString() + "Mob world: " + world)
                    player.sendMessage(ChatColor.BLUE.toString() + "Mob type: " + type)
                    player.sendMessage(ChatColor.BLUE.toString() + "Mob name: " + name)
                    player.sendMessage("§a§l------------------------------------------")
                }
            }
        }
    }

    companion object {
        fun String?.translateColors(): String {
            if(this == null) return ""
            var parsedStr: String = this
            parsedStr = this.replace("""\{(#[0-9A-f]{6})\}""".toRegex(), "&$1")
            if("&#[0-9A-f]{6}".toRegex().containsMatchIn(parsedStr)){
                for (x in "&(#[0-9A-f]{6})".toRegex().findAll(parsedStr)){
                    parsedStr = parsedStr.replaceFirst(x.value.toRegex(),ChatColor.of(x.value.slice(
                        1 until x.value.length
                    )).toString())
                }
            }
            return ChatColor.translateAlternateColorCodes('&', parsedStr)
        }


        private fun replacePlaceholders(string: String, playerName: String, map: HashMap<String, Double>, finalDamager: String, noOne: String = "No one"): String {
            val entries = ArrayList<Map.Entry<String, Double>>(map.entries)
            entries.sortWith { (_, value): Map.Entry<String?, Double>, (_, value1): Map.Entry<String?, Double> ->
                value1.compareTo(
                    value
                )
            }
            var pattern = Pattern.compile("%top_name_(\\d+)%")
            var matcher = pattern.matcher(string)
            var newStr = string
            while (matcher.find()) {
                val index = matcher.group(1).toInt() - 1
                if (index < entries.size) {
                    newStr = newStr.replace(matcher.group(), entries[index].key)
                }
            }
            newStr = newStr.replace("%top_name_(\\d+)%".toRegex(), noOne)
            pattern = Pattern.compile("%top_damage_(\\d+)%")
            matcher = pattern.matcher(newStr)
            while (matcher.find()) {
                val index = matcher.group(1).toInt() - 1
                if (index < entries.size) {
                    newStr = newStr.replace(matcher.group(), entries[index].value.roundToInt().toString())
                }
            }
            newStr = newStr.replace("%top_damage_(\\d+)%".toRegex(), "0")
            newStr = newStr.replace(
                "%personal_damage%", (map[playerName]?.roundToInt() ?: 0).toString()
            )
            newStr = newStr.replace("%final_damager%", finalDamager)
            return newStr
        }
    }
}