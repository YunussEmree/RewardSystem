package provanasservices.rewardsystem.service

import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import provanasservices.rewardsystem.Main
import provanasservices.rewardsystem.util.LoggingUtility
import java.util.*

/**
 * Handles the plugin's command execution.
 * Processes and responds to all '/rewardsystem' commands.
 */
class CommandHandler(private val plugin: JavaPlugin) : CommandExecutor {
    /**
     * Processes and executes commands for the RewardSystem plugin.
     * 
     * @param sender The command sender
     * @param command The command being executed
     * @param label The command label
     * @param args Command arguments
     * @return true if the command was handled, false otherwise
     */
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        // Show help message if no arguments provided
        if (args.isEmpty()) {
            showHelpMessage(sender)
            return true
        }
        
        // Process different command types
        when (args[0].lowercase()) {
            "reload" -> handleReloadCommand(sender, args)
            "cooldown" -> handleCooldownCommand(sender, args)
            else -> showHelpMessage(sender)
        }
        
        return true
    }
    
    /**
     * Displays the plugin's help message to the sender.
     * 
     * @param sender The command sender
     */
    private fun showHelpMessage(sender: CommandSender) {
        sender.sendMessage("§a§l-------[REWARD SYSTEM INFORMATION]-------")
        sender.sendMessage(ChatColor.AQUA.toString() + " ")
        sender.sendMessage(ChatColor.AQUA.toString() + "/rewardsystem reload §6-> §eReload the plugin")
        sender.sendMessage(ChatColor.AQUA.toString() + "/rewardsystem cooldown reset <player> <mobID> §6-> §eReset cooldown for a player for a specific mob")
        sender.sendMessage(ChatColor.AQUA.toString() + " ")
        sender.sendMessage("§a§l------------------------------------------")
    }
    
    /**
     * Handles the reload command to refresh plugin configuration.
     * 
     * @param sender The command sender
     * @param args Command arguments
     */
    private fun handleReloadCommand(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission("rewardsystem.reload")) {
            sender.sendMessage(ChatColor.RED.toString() + "You don't have permission to use this command!")
            return
        }
        
        sender.sendMessage(ChatColor.YELLOW.toString() + "Reloading RewardSystem plugin configuration...")
        LoggingService.info("Manual reload initiated by ${sender.name}")
        
        try {
            val main = plugin as Main
            
            // Clear existing data
            Main.damageMap.clear()
            Main.lastToucherMap.clear()
            Main.uuidMap.clear()
            
            // Save current cooldowns to database before reload
            LoggingService.info("Saving existing cooldowns to database before reload...")
            main.saveCooldownsToDatabase()
            
            // Reload plugin configuration
            plugin.reloadConfig()
            
            // Check if config.yml exists, and save default if not
            val configFile = java.io.File(plugin.dataFolder, "config.yml")
            if (!configFile.exists()) {
                LoggingService.info("Config file not found, creating default config.yml")
                plugin.saveDefaultConfig()
                plugin.reloadConfig() // Load the newly created config
            }
            
            // Log loaded config sections for debugging
            val rootSections = plugin.config.getKeys(false)
            LoggingService.info("Loaded config with sections: ${rootSections.joinToString()}")
            
            // Update logging settings from config
            LoggingService.updateDebugState()
            
            // Reconfigure global logging with new settings
            main.configureGlobalLogging()
            
            // Initialize LoggingUtility
            LoggingUtility.initialize(true)
            
            // Reload reward configurations
            Main.rewardsFromConfig = ConfigService.loadRewardsFromConfig(main)
            
            // Reload cooldowns from database
            LoggingService.info("Loading cooldowns from database after reload...")
            main.loadCooldownsFromDatabase()
            
            LoggingService.info("Plugin successfully reloaded with ${Main.rewardsFromConfig?.size ?: 0} reward configurations")
            sender.sendMessage(ChatColor.GREEN.toString() + "Reward System plugin successfully reloaded with ${Main.rewardsFromConfig?.size ?: 0} reward configurations")
        } catch (e: Exception) {
            LoggingService.severe("Error during reload: ${e.message}")
            e.printStackTrace()
            sender.sendMessage(ChatColor.RED.toString() + "Error reloading plugin: ${e.message}")
        }
    }
    
    /**
     * Handles cooldown-related commands, such as resetting a player's cooldown.
     * 
     * @param sender The command sender
     * @param args Command arguments
     */
    private fun handleCooldownCommand(sender: CommandSender, args: Array<String>) {
        // Check for subcommand, permissions, and required arguments
        if (args.size < 4 || !args[1].equals("reset", ignoreCase = true) || 
            !sender.hasPermission("rewardsystem.admin.resetcooldown")) {
            showHelpMessage(sender)
            return
        }
        
        val playerName = args[2]
        val mobId = args[3]
        
        // Find the target player
        val player = plugin.server.getPlayer(playerName)
        if (player == null) {
            sender.sendMessage(ChatColor.RED.toString() + "Player not found")
            return
        }
        
        // Find the reward configuration for the specified mob
        val reward = Main.rewardsFromConfig?.get(mobId)
        if (reward == null) {
            sender.sendMessage(ChatColor.RED.toString() + "Mob with id $mobId is not found")
            return
        }
        
        // Reset the player's cooldown
        reward.cooldowns.remove(player.uniqueId)
        sender.sendMessage(ChatColor.AQUA.toString() + "Player's cooldown successfully reset")
    }
} 