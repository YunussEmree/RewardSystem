package provanasservices.rewardsystem

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.managers.RegionManager
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import com.sk89q.worldguard.protection.regions.RegionContainer
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import provanasservices.rewardsystem.Main.Companion.translateColors
import java.util.*
import java.util.function.Consumer
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.roundToInt
import kotlin.random.Random

class Events(private var plugin: Main) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val entity: Entity = event.entity
        val damager: Entity = event.damager
        if (damager is Player && entity is Mob) {
            val player: Player = damager
            val container: RegionContainer = WorldGuard.getInstance().platform.regionContainer
            for (i in Main.rewardsFromConfig!!.indices) {
                val reward = Main.rewardsFromConfig!![i]
                if (reward.nameEquals(entity.name) && reward.typeEquals(entity.type.name) && reward.worldEquals(entity.world.name)) {
                    if (reward.enabledWorld != null) {
                        val world: World = Bukkit.getWorld(reward.enabledWorld!!)!!
                        if (reward.enabledRegion != null) {
                            val regions: RegionManager? = container.get(BukkitAdapter.adapt(world))
                            val region: ProtectedRegion? = regions?.getRegion(reward.enabledRegion)
                            if (region!!.contains(BukkitAdapter.asBlockVector(entity.location))) {
                                addToDamageMap(event, player, i)
                            }
                        } else {
                            addToDamageMap(event, player, i)
                        }
                    } else {
                        addToDamageMap(event, player, i)
                    }
                }
            }
        } else if (damager is Arrow) {
            val proj: Projectile = event.damager as Projectile
            if (proj.shooter !is Player) return
            if (entity !is Mob) return
            val player: Player = proj.shooter as Player
            for (i in Main.rewardsFromConfig!!.indices) {
                val reward = Main.rewardsFromConfig!![i]
                if (reward.nameEquals(entity.name) && reward.typeEquals(entity.type.name) && reward.worldEquals(entity.world.name)) {
                    if (reward.enabledWorld != null) {
                        val world: World = Bukkit.getWorld(reward.enabledWorld!!)!!
                        if (reward.enabledRegion != null) {
                            val container: RegionContainer = WorldGuard.getInstance().platform.regionContainer
                            val regions: RegionManager? = container.get(BukkitAdapter.adapt(world))
                            val region: ProtectedRegion? = regions!!.getRegion(reward.enabledRegion)
                            if (region!!.contains(BukkitAdapter.asBlockVector(entity.location))) {
                                addToDamageMap(event, player, i)
                            }
                        } else {
                            addToDamageMap(event, player, i)
                        }
                    } else {
                        addToDamageMap(event, player, i)
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
        val player: Player? = event.entity.killer
        for (i in Main.rewardsFromConfig!!.indices) {
            val reward = Main.rewardsFromConfig!![i]
            if (reward.nameEquals(entity.name) && reward.typeEquals(entity.type.name) && reward.worldEquals(entity.world.name)) {
                if (reward.enabledWorld != null) {
                    val world: World = Bukkit.getWorld(reward.enabledWorld!!)!!
                    if (reward.enabledRegion != null) {
                        val container: RegionContainer = WorldGuard.getInstance().platform.regionContainer
                        val regions: RegionManager? = container.get(BukkitAdapter.adapt(world))
                        val region: ProtectedRegion? = regions?.getRegion(reward.enabledRegion)
                        if (region!!.contains(BukkitAdapter.asBlockVector(entity.location))) {
                            giveRewards(i, reward)
                        }
                    } else {
                        giveRewards(i, reward)
                    }
                } else {
                    giveRewards(i, reward)
                }
                val selectedMap = Main.damageMap[i + 1]!!
                if(selectedMap.isEmpty()){
                    plugin.logger.info(translateColors("&cNot giving rewards because no players have damaged the ${entity.name}."))
                }
                if (reward.radius == -1 && selectedMap.isNotEmpty()) {
                    if(selectedMap.isEmpty()) return
                    reward.rewardMessages!!.forEach { message: String ->
                        Bukkit.getOnlinePlayers().forEach { onlinePlayer: Player ->
                            onlinePlayer.sendMessage(
                                translateColors(replacePlaceholders(message, player!!.name, selectedMap))
                            )
                        }
                    }
                } else {
                    val nearPlayers = entity.getNearbyEntities(reward.radius.toDouble(), reward.radius.toDouble(), reward.radius.toDouble()).filterIsInstance<Player>()
                    if(nearPlayers.isNotEmpty() && selectedMap.isNotEmpty()) {
                        reward.rewardMessages!!.forEach { message: String ->
                            nearPlayers.forEach { onlinePlayer: Player ->

                                onlinePlayer.sendMessage(
                                    translateColors(replacePlaceholders(message, player!!.name, selectedMap))
                                )
                            }
                        }
                    }
                }
                Main.damageMap[i + 1]!!.clear()
            }
        }
    }

    private fun giveRewards(i: Int, reward: RewardMob) {
        val selectedDamageMap = Main.damageMap[i + 1]!!
        val entrySet = ArrayList<Map.Entry<String, Double>>(selectedDamageMap.entries)
        entrySet.sortWith { (_, value): Map.Entry<String?, Double>, (_, value1): Map.Entry<String?, Double> ->
            value1.compareTo(
                value
            )
        }
        for (index in 0 until selectedDamageMap.size) {
            val rewardIndex = index + 1
            val (key, value) = entrySet[index]

            reward.allRewards!!.forEach(Consumer { allReward: String ->
                Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    allReward.replace("%player%", key).replace("%damage%", value.toString())
                )
            })
            reward.rewards[rewardIndex]!!.forEach(Consumer { rewardString: String ->
                Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    rewardString.replace("%player%", key).replace("%damage%", value.toString())
                )
            })
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
        }
    }

    private fun addToDamageMap(event: EntityDamageByEntityEvent, player: Player, i: Int) {
        val selectedDamageMap = Main.damageMap[i + 1]!!
        var selectedDamage = selectedDamageMap[player.name]
        if (selectedDamage == null) selectedDamage = 0.0
        selectedDamage += event.finalDamage
        selectedDamageMap[player.name] = selectedDamage
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
        private fun replacePlaceholders(string: String, playerName: String, map: HashMap<String, Double>): String {
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
            newStr = newStr.replace("%top_name_(\\d+)%".toRegex(), "No one")
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
                "%personal_damage%", if (map[playerName]?.toString() != null) (map[playerName]!!).roundToInt().toString() else "0"
            )
            return newStr
        }
    }
}