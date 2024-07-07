package provanasservices.rewardsystem

import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import provanasservices.rewardsystem.Main.Companion.getRewardsFromConfig

class CommandHandler(private val plugin: JavaPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§a§l-------[REWARD SYSTEM INFORMATION]-------")
            sender.sendMessage(ChatColor.AQUA.toString() + " ")
            sender.sendMessage(ChatColor.AQUA.toString() + "/rewardsystem reload §6-> §eReload the plugin")
            sender.sendMessage(ChatColor.AQUA.toString() + "/rewardsystem cooldown reset <nick> <mobID> §6-> §eReset cooldown for a player for a specific mob")
            sender.sendMessage(ChatColor.AQUA.toString() + " ")
            sender.sendMessage("§a§l------------------------------------------")
        } else if (args[0].equals("reload", ignoreCase = true) && sender.hasPermission("rewardsystem.reload")) {
            plugin.reloadConfig()
            plugin.saveDefaultConfig()
            Main.rewardsFromConfig = getRewardsFromConfig(
                plugin
            )
            sender.sendMessage(ChatColor.AQUA.toString() + "Reward System plugin successfully reloaded")
        } else if (args[0].equals("cooldown", ignoreCase = true) && args[1].equals("reset", ignoreCase = true) && sender.hasPermission("rewardsystem.admin.resetcooldown")) {
            var nick = args[2]
            var id = args[3]

            val player = plugin.server.getPlayer(nick)
            if (player != null) {
                val uuid = player.uniqueId
                val reward = Main.rewardsFromConfig?.get(id) ?: run {
                    sender.sendMessage(ChatColor.RED.toString() + "Mob with id $id is not found")
                    return true
                }
                reward.cooldowns.remove(uuid)
                sender.sendMessage(ChatColor.AQUA.toString() + "Player's cooldown successfully reset")
            } else {
                sender.sendMessage(ChatColor.RED.toString() + "Player not found")
            }
            sender.sendMessage(ChatColor.AQUA.toString() + "Reward System plugin successfully reloaded")
        }

        else {
            sender.sendMessage("§a§l-------[REWARD SYSTEM INFORMATION]-------")
            sender.sendMessage(ChatColor.AQUA.toString() + " ")
            sender.sendMessage(ChatColor.AQUA.toString() + "/rewardsystem reload §6-> §eReload the plugin")
            sender.sendMessage(ChatColor.AQUA.toString() + "/rewardsystem cooldown reset <nick> <mobID> §6-> §eReset cooldown for a player for a specific mob")
            sender.sendMessage(ChatColor.AQUA.toString() + " ")
            sender.sendMessage("§a§l------------------------------------------")
        }
        return true
    }
}