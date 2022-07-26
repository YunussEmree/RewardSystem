package provanasservices.rewardsystem

import net.md_5.bungee.api.ChatColor
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
            sender.sendMessage(ChatColor.AQUA.toString() + " ")
            sender.sendMessage("§a§l------------------------------------------")
        } else if (args[0].equals("reload", ignoreCase = true) && sender.hasPermission("rewardsystem.reload")) {
            plugin.reloadConfig()
            plugin.saveDefaultConfig()
            Main.rewardsFromConfig = getRewardsFromConfig(
                plugin
            )
            sender.sendMessage(ChatColor.AQUA.toString() + "Reward System plugin successfully reloaded")
        }
        return true
    }
}