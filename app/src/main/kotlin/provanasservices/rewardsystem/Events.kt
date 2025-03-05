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
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.server.ServerCommandEvent
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
    // Custom CommandSender wrapper to log all dispatched commands
    private class LoggingCommandSender(private val original: ConsoleCommandSender, private val plugin: Main) : ConsoleCommandSender by original {
        override fun sendMessage(message: String) {
            original.sendMessage(message)
        }
    }
    
    init {
        // Hook into Bukkit.dispatchCommand to log all plugin commands
        setupCommandLogging()
    }
    
    private fun setupCommandLogging() {
        // Using reflection to create a hook for Bukkit.dispatchCommand would be ideal,
        // but for simplicity we'll just log within our plugin's dispatchCommand calls
        plugin.logger.info("Command logging for plugins initialized")
    }
    
    // Helper method to dispatch commands with logging
    fun dispatchCommandWithLogging(sender: CommandSender, command: String) {
        if (plugin.config.getBoolean("Debug.enabled")) {
            plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $command")
        }
        Bukkit.dispatchCommand(sender, command)
    }

    // Helper method to check permissions and dispatch commands with logging
    private fun dispatchCommandWithPermCheck(player: Player, commandStr: String): Boolean {
        // Extract permission if exists with format $permission.name$
        val permPattern = "\\$(.*?)\\$".toRegex()
        val permMatch = permPattern.find(commandStr)
        
        // If permission parameter exists, check it
        if (permMatch != null) {
            val permission = permMatch.groups[1]?.value
            
            // Remove permission part from command
            val cleanCommand = commandStr.replace("$$permission$", "").trim()
            
            // Check if player has permission
            if (permission != null && !player.hasPermission(permission)) {
                if (plugin.config.getBoolean("Debug.enabled")) {
                    plugin.logger.info("[REWARDSYSTEM DEBUG] Player ${player.name} doesn't have permission: $permission for command: $cleanCommand")
                }
                return false
            }
            
            // Process math expressions in the command
            val processedCommand = MathEvaluator.processCommand(
                cleanCommand,
                player,
                plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
            )
            
            // Execute command with processed math
            if (plugin.config.getBoolean("Debug.enabled")) {
                plugin.logger.info("[REWARDSYSTEM DEBUG] Player ${player.name} has permission: $permission, executing: $processedCommand")
            }
            
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
            return true
        } else {
            // No permission check needed, but still process math expressions
            val processedCommand = MathEvaluator.processCommand(
                commandStr,
                player,
                plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
            )
            
            // Execute command with processed math
            if (plugin.config.getBoolean("Debug.enabled")) {
                plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $processedCommand")
            }
            
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
            return true
        }
    }
    
    // Parse command to extract chance and permission info
    private fun parseCommand(command: String): Triple<String, String?, Double?> {
        // Extract permission if exists
        val permPattern = "\\$(.*?)\\$".toRegex()
        val permMatch = permPattern.find(command)
        val permission = permMatch?.groups?.get(1)?.value
        
        // Remove permission part for clean command
        val withoutPerm = if (permission != null) {
            command.replace("$$permission$", "").trim()
        } else {
            command
        }
        
        // Check for percentage at end
        val words = withoutPerm.split(" ")
        val lastWord = words.lastOrNull()
        
        if (lastWord != null && lastWord.endsWith("%")) {
            try {
                // First check if it's a math expression
                val mathPattern = "\\{math:(.*?)\\}%".toRegex()
                val mathMatch = mathPattern.find(lastWord)
                
                if (mathMatch != null) {
                    // Found a math expression as chance percentage
                    // We'll evaluate this later when we have player context
                    val mathExpression = mathMatch.groupValues[1]
                    val cleanCommand = withoutPerm.substring(0, withoutPerm.length - lastWord.length).trim()
                    
                    // Return Triple with mathExpression as placeholder
                    return Triple(cleanCommand, permission, null).also {
                        // Store the math expression for later evaluation
                        mathChanceExpressions[cleanCommand] = mathExpression
                    }
                }
                
                // Standard percentage chance
                val chance = lastWord.replace("%", "").toDouble()
                val cleanCommand = withoutPerm.substring(0, withoutPerm.length - lastWord.length).trim()
                return Triple(cleanCommand, permission, chance)
            } catch (e: NumberFormatException) {
                // Not a percentage
            }
        }
        
        return Triple(withoutPerm, permission, null)
    }
    
    // Storage for math expressions to be evaluated later as chance percentages
    private val mathChanceExpressions = HashMap<String, String>()
    
    // Helper method to evaluate math expressions for chance
    private fun evaluateChanceExpression(expression: String, player: Player): Double {
        val processedExpression = MathEvaluator.processCommand(
            expression,
            player,
            plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
        )
        
        return try {
            processedExpression.toDouble()
        } catch (e: NumberFormatException) {
            if (plugin.config.getBoolean("Debug.enabled")) {
                plugin.logger.info("[REWARDSYSTEM DEBUG] Failed to convert math result to chance: $processedExpression")
            }
            0.0
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        // Log player commands
        val command = event.message
        if (plugin.config.getBoolean("Debug.enabled")) {
            plugin.logger.info("[REWARDSYSTEM DEBUG] Player Command executed: $command")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onConsoleCommand(event: ServerCommandEvent) {
        // Log console commands
        val command = event.command
        if (plugin.config.getBoolean("Debug.enabled")) {
        plugin.logger.info("[REWARDSYSTEM DEBUG] Console Command executed: $command")
        }
    }


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
        try {
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
                    val finalDamager = lastToucherMap[event.entity.uniqueId] ?: plugin.config.getString("no_one")!!
                    if (selectedMap.isEmpty()) {
                        plugin.logger.info(translateColors("&cNot giving rewards because no players have damaged the ${entity.name}."))
                    }
                    if (reward.radius == -1 && selectedMap.isNotEmpty()) {
                        if (selectedMap.isEmpty()) return
                        reward.rewardMessages!!.forEach { message: String ->
                            Bukkit.getOnlinePlayers().forEach { onlinePlayer: Player ->
                                onlinePlayer.sendMessage(
                                    translateColors(
                                        replacePlaceholders(
                                            message,
                                            onlinePlayer.name,
                                            selectedMap,
                                            finalDamager,
                                            plugin.config.getString("no_one")!!
                                        )
                                    )
                                )
                            }
                        }
                    } else {
                        val nearPlayers = entity.getNearbyEntities(
                            reward.radius.toDouble(),
                            reward.radius.toDouble(),
                            reward.radius.toDouble()
                        ).filterIsInstance<Player>()
                        if (nearPlayers.isNotEmpty() && selectedMap.isNotEmpty()) {
                            reward.rewardMessages!!.forEach { message: String ->
                                nearPlayers.forEach { onlinePlayer: Player ->

                                    onlinePlayer.sendMessage(
                                        translateColors(
                                            replacePlaceholders(
                                                message,
                                                onlinePlayer.name,
                                                selectedMap,
                                                finalDamager,
                                                plugin.config.getString("no_one")!!
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }
                    Main.damageMap[event.entity.uniqueId]!!.clear()
                }
            }
        } catch (ignored: NullPointerException){

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

            if (value < reward.minimumDamage) {
                continue
            }


            val player: Player = Bukkit.getPlayerExact(key) ?: continue
            val uuid = player.getUniqueId()


            if (reward.cooldown != 0 && reward.cooldowns[uuid] != null) {
                if (reward.cooldowns[uuid]!! > System.currentTimeMillis()) {
                    if (!player.hasPermission("rewardsystem.cooldown.bypass") && !player.isOp) {
                        player.sendMessage(
                            translateColors(reward.cooldownMessage).replace(
                                "%time%",
                                ((reward.cooldowns[uuid]!! - System.currentTimeMillis()) / 1000).toString()
                            )
                        )
                        continue
                    }
                } else {
                    reward.cooldowns.remove(uuid)
                }
            }


            reward.cooldowns[uuid] = System.currentTimeMillis() + reward.cooldown * 1000

            // Process allRewards
            reward.allRewards?.forEach(Consumer { allReward: String ->
                if (allReward.isNotEmpty() && !allReward.equals("none", ignoreCase = true)) {
                    val player = Bukkit.getPlayer(key) ?: return@Consumer
                    
                    // Parse and check permission
                    val (parsedCommand, permission, _) = parseCommand(allReward)
                    
                    // Process math expressions
                    val placeholderReplaced = parsedCommand.replace("%player%", key).replace("%damage%", value.toString())
                    val processedCmd = PlaceholderAPI.setBracketPlaceholders(player, placeholderReplaced)
                    
                    // Process math expressions
                    val mathProcessed = MathEvaluator.processCommand(
                        processedCmd,
                        player,
                        plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
                    )
                    
                    if (permission != null) {
                        // Check permission
                        if (!player.hasPermission(permission)) {
                            if (plugin.config.getBoolean("Debug.enabled")) {
                                plugin.logger.info("[REWARDSYSTEM DEBUG] Player ${player.name} doesn't have permission: $permission for command: $mathProcessed")
                            }
                            return@Consumer
                        }
                    }
                    
                    if (plugin.config.getBoolean("Debug.enabled")) {
                        plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $mathProcessed")
                    }
                    Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        mathProcessed
                    )
                }
            })

            reward.allChanceRewards.forEach { (chance, allChanceReward, chancePlaceholder) ->
                val random = Random.nextDouble(100.0)
                if (chance != null) {
                    if (random <= chance) {
                        val player = Bukkit.getPlayer(key) ?: return@forEach

                        val (parsedCommand, permission, _) = parseCommand(allChanceReward)
                        
                        if (permission != null) {
                            if (!player.hasPermission(permission)) {
                                if (plugin.config.getBoolean("Debug.enabled")) {
                                    plugin.logger.info("[REWARDSYSTEM DEBUG] Player ${player.name} doesn't have permission: $permission for command")
                                }
                                return@forEach
                            }
                        }
                        
                        // Check if this command has a math expression for chance
                        val mathChanceExpression = mathChanceExpressions[parsedCommand]
                        if (mathChanceExpression != null) {
                            // Evaluate the chance with player context
                            val calculatedChance = evaluateChanceExpression(mathChanceExpression, player)
                            if (random > calculatedChance) {
                                // Chance failed
                                if (plugin.config.getBoolean("Debug.enabled")) {
                                    plugin.logger.info("[REWARDSYSTEM DEBUG] Math chance failed: $calculatedChance% < $random%")
                                }
                                return@forEach
                            }
                        }
                        
                        if (PLACEHOLDERAPI_ENABLED) {
                            val placeholderReplaced = parsedCommand.replace("%player%", key).replace("%damage%", value.toString())
                            val processedCmd = PlaceholderAPI.setBracketPlaceholders(player, placeholderReplaced)
                            
                            // Process math expressions
                            val mathProcessed = MathEvaluator.processCommand(
                                processedCmd,
                                player,
                                plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
                            )
                            
                            if (plugin.config.getBoolean("Debug.enabled")) {
                                plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $mathProcessed")
                            }
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                mathProcessed
                            )
                        } else {
                            val replaced = parsedCommand.replace("%player%", key).replace("%damage%", value.toString())
                            
                            // Process math expressions
                            val cmd = MathEvaluator.processCommand(
                                replaced,
                                player,
                                plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
                            )
                            
                            if (plugin.config.getBoolean("Debug.enabled")) {
                                plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $cmd")
                            }
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                cmd
                            )
                        }
                    }
                } else if (chancePlaceholder != null) {
                    val player = Bukkit.getPlayer(key) ?: return@forEach
                    val chanceExtracted =
                        PlaceholderAPI.setBracketPlaceholders(player, chancePlaceholder)
                    if (random <= chanceExtracted.toDouble()) {
                        val (parsedCommand, permission, _) = parseCommand(allChanceReward)
                        
                        if (permission != null) {
                            if (!player.hasPermission(permission)) {
                                if (plugin.config.getBoolean("Debug.enabled")) {
                                    plugin.logger.info("[REWARDSYSTEM DEBUG] Player ${player.name} doesn't have permission: $permission for command")
                                }
                                return@forEach
                            }
                        }
                        
                        // Check if this command has a math expression for chance
                        val mathChanceExpression = mathChanceExpressions[parsedCommand]
                        if (mathChanceExpression != null) {
                            // Evaluate the chance with player context
                            val calculatedChance = evaluateChanceExpression(mathChanceExpression, player)
                            if (random > calculatedChance) {
                                // Chance failed
                                if (plugin.config.getBoolean("Debug.enabled")) {
                                    plugin.logger.info("[REWARDSYSTEM DEBUG] Math chance failed: $calculatedChance% < $random%")
                                }
                                return@forEach
                            }
                        }
                        
                        if (PLACEHOLDERAPI_ENABLED) {
                            val placeholderReplaced = parsedCommand.replace("%player%", key).replace("%damage%", value.toString())
                            val processedCmd = PlaceholderAPI.setBracketPlaceholders(player, placeholderReplaced)
                            
                            val mathProcessed = MathEvaluator.processCommand(
                                processedCmd,
                                player,
                                plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
                            )
                            
                            if (plugin.config.getBoolean("Debug.enabled")) {
                                plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $mathProcessed")
                            }
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                mathProcessed
                            )
                        } else {
                            val replaced = parsedCommand.replace("%player%", key).replace("%damage%", value.toString())
                            
                            val cmd = MathEvaluator.processCommand(
                                replaced,
                                player,
                                plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
                            )
                            
                            if (plugin.config.getBoolean("Debug.enabled")) {
                                plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $cmd")
                            }
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                cmd
                            )
                        }
                    }
                }
            }

            reward.rewards[rewardIndex]?.forEach(Consumer { rewardString: String ->
                if (rewardString.isNotEmpty()) {
                    val player = Bukkit.getPlayer(key) ?: return@Consumer

                    val (parsedCommand, permission, _) = parseCommand(rewardString)
                    val cmd = parsedCommand.replace("%player%", key).replace("%damage%", value.toString())
                    
                    if (permission != null) {
                        if (!player.hasPermission(permission)) {
                            if (plugin.config.getBoolean("Debug.enabled")) {
                                plugin.logger.info("[REWARDSYSTEM DEBUG] Player ${player.name} doesn't have permission: $permission for command: $cmd")
                            }
                            return@Consumer
                        }
                    }
                    
                    if (plugin.config.getBoolean("Debug.enabled")) {
                        plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $cmd")
                    }
                    Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        cmd
                    )
                }
            })

            reward.chanceRewards[rewardIndex]?.forEach { (chance, rewardString, chancePlaceholder) ->
                val random = Random.nextDouble(100.0)
                if (chance != null) {
                    if (random <= chance) {
                        val player = Bukkit.getPlayer(key) ?: return@forEach

                        val (parsedCommand, permission, _) = parseCommand(rewardString)
                        
                        if (permission != null) {
                            if (!player.hasPermission(permission)) {
                                if (plugin.config.getBoolean("Debug.enabled")) {
                                    plugin.logger.info("[REWARDSYSTEM DEBUG] Player ${player.name} doesn't have permission: $permission for command")
                                }
                                return@forEach
                            }
                        }
                        
                        // Check if this command has a math expression for chance
                        val mathChanceExpression = mathChanceExpressions[parsedCommand]
                        if (mathChanceExpression != null) {
                            // Evaluate the chance with player context
                            val calculatedChance = evaluateChanceExpression(mathChanceExpression, player)
                            if (random > calculatedChance) {
                                // Chance failed
                                if (plugin.config.getBoolean("Debug.enabled")) {
                                    plugin.logger.info("[REWARDSYSTEM DEBUG] Math chance failed: $calculatedChance% < $random%")
                                }
                                return@forEach
                            }
                        }
                        
                        if (PLACEHOLDERAPI_ENABLED) {
                            val placeholderReplaced = parsedCommand.replace("%player%", key).replace("%damage%", value.toString())
                            val processedCmd = PlaceholderAPI.setBracketPlaceholders(player, placeholderReplaced)
                            
                            val mathProcessed = MathEvaluator.processCommand(
                                processedCmd,
                                player,
                                plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
                            )
                            
                            if (plugin.config.getBoolean("Debug.enabled")) {
                                plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $mathProcessed")
                            }
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                mathProcessed
                            )
                        } else {
                            val replaced = parsedCommand.replace("%player%", key).replace("%damage%", value.toString())
                            
                            val cmd = MathEvaluator.processCommand(
                                replaced,
                                player,
                                plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
                            )
                            
                            if (plugin.config.getBoolean("Debug.enabled")) {
                                plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $cmd")
                            }
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                cmd
                            )
                        }
                    }
                } else if (chancePlaceholder != null) {
                    val player = Bukkit.getPlayer(key) ?: return@forEach
                    val chanceExtracted =
                        PlaceholderAPI.setBracketPlaceholders(player, chancePlaceholder)
                    if (random <= chanceExtracted.toDouble()) {
                        val (parsedCommand, permission, _) = parseCommand(rewardString)
                        
                        if (permission != null) {
                            if (!player.hasPermission(permission)) {
                                if (plugin.config.getBoolean("Debug.enabled")) {
                                    plugin.logger.info("[REWARDSYSTEM DEBUG] Player ${player.name} doesn't have permission: $permission for command")
                                }
                                return@forEach
                            }
                        }
                        
                        // Check if this command has a math expression for chance
                        val mathChanceExpression = mathChanceExpressions[parsedCommand]
                        if (mathChanceExpression != null) {
                            // Evaluate the chance with player context
                            val calculatedChance = evaluateChanceExpression(mathChanceExpression, player)
                            if (random > calculatedChance) {
                                // Chance failed
                                if (plugin.config.getBoolean("Debug.enabled")) {
                                    plugin.logger.info("[REWARDSYSTEM DEBUG] Math chance failed: $calculatedChance% < $random%")
                                }
                                return@forEach
                            }
                        }
                        
                        if (PLACEHOLDERAPI_ENABLED) {
                            val placeholderReplaced = parsedCommand.replace("%player%", key).replace("%damage%", value.toString())
                            val processedCmd = PlaceholderAPI.setBracketPlaceholders(player, placeholderReplaced)
                            
                            val mathProcessed = MathEvaluator.processCommand(
                                processedCmd,
                                player,
                                plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
                            )
                            
                            if (plugin.config.getBoolean("Debug.enabled")) {
                                plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $mathProcessed")
                            }
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                mathProcessed
                            )
                        } else {
                            val replaced = parsedCommand.replace("%player%", key).replace("%damage%", value.toString())
                            
                            val cmd = MathEvaluator.processCommand(
                                replaced,
                                player,
                                plugin.config.getString("Math.rounding_mode", "floor") ?: "floor"
                            )
                            
                            if (plugin.config.getBoolean("Debug.enabled")) {
                                plugin.logger.info("[REWARDSYSTEM DEBUG] Plugin dispatched command: $cmd")
                            }
                            Bukkit.dispatchCommand(
                                Bukkit.getConsoleSender(),
                                cmd
                            )
                        }
                    }
                }
            }
        }
        println(reward.cooldowns)
    }

    private fun addToDamageMap(event: EntityDamageByEntityEvent, player: Player) {
        var damageMap = Main.damageMap[event.entity.uniqueId]
        if (damageMap == null) Main.damageMap[event.entity.uniqueId] = HashMap()
        damageMap = Main.damageMap[event.entity.uniqueId]!!
        var selectedDamage = damageMap.get(player.name)
        if (selectedDamage == null) selectedDamage = 0.0
        selectedDamage += event.finalDamage
        damageMap[player.name] = selectedDamage
        if (event.entity is Mob) {
            if ((event.entity as Mob).health - event.finalDamage < 0) lastToucherMap[event.entity.uniqueId] =
                player.name
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
            if (this == null) return ""
            var parsedStr: String = this
            parsedStr = this.replace("""\{(#[0-9A-f]{6})\}""".toRegex(), "&$1")
            if ("&#[0-9A-f]{6}".toRegex().containsMatchIn(parsedStr)) {
                for (x in "&(#[0-9A-f]{6})".toRegex().findAll(parsedStr)) {
                    parsedStr = parsedStr.replaceFirst(
                        x.value.toRegex(), ChatColor.of(
                            x.value.slice(
                                1 until x.value.length
                            )
                        ).toString()
                    )
                }
            }
            return ChatColor.translateAlternateColorCodes('&', parsedStr)
        }


        private fun replacePlaceholders(
            string: String,
            playerName: String,
            map: HashMap<String, Double>,
            finalDamager: String,
            noOne: String = "No one"
        ): String {
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