package provanasservices.rewardsystem;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import static provanasservices.rewardsystem.Main.getRewardsFromConfig;
import static provanasservices.rewardsystem.Main.rewardsFromConfig;

public class CommandHandler implements CommandExecutor {
    private JavaPlugin plugin;
    public CommandHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if(args.length == 0){
            sender.sendMessage("§a§l-------[REWARD SYSTEM INFORMATION]-------");
            sender.sendMessage(ChatColor.AQUA + " ");
            sender.sendMessage(ChatColor.AQUA + "/rewardsystem reload §6-> §eReload the plugin");
            sender.sendMessage(ChatColor.AQUA + " ");
            sender.sendMessage("§a§l------------------------------------------");

        }

        if(args[0].equalsIgnoreCase("reload") && sender.hasPermission("rewardsystem.reload")){
            plugin.reloadConfig();
            plugin.saveDefaultConfig();
            rewardsFromConfig = getRewardsFromConfig(plugin);
            sender.sendMessage(ChatColor.AQUA + "Reward System plugin successfully reloaded");
        }
        return true;
    }
}
