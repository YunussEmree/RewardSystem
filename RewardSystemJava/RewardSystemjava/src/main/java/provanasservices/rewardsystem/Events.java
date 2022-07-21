package provanasservices.rewardsystem;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static provanasservices.rewardsystem.Main.*;

public class Events implements Listener {
    Main plugin;
    public Events(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        Entity damager = event.getDamager();
        if ((damager instanceof Player) && (entity instanceof Mob)) {
            Mob mob = (Mob) entity;
            Player player = (Player) damager;
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();


            for (int i = 0; i < rewardsFromConfig.size(); i++) {

                RewardMob reward = rewardsFromConfig.get(i);
                if (reward.nameEquals(entity.getName()) && reward.typeEquals(entity.getType().name()) && reward.worldEquals(entity.getWorld().getName())) {
                    if (reward.getEnabledWorld() != null) {
                        World world = Bukkit.getWorld(reward.getEnabledWorld());
                        assert world != null;
                        if (reward.getEnabledRegion() != null) {
                            RegionManager regions = container.get(BukkitAdapter.adapt(world));
                            ProtectedRegion region = regions.getRegion(reward.getEnabledRegion());
                            if (region.contains(BukkitAdapter.asBlockVector(entity.getLocation()))) {
                                addToDamageMap(event, player, i);
                            }
                        } else {
                            addToDamageMap(event, player, i);
                        }
                    } else {

                        addToDamageMap(event, player, i);
                    }

                }
            }


            if (mob.getHealth() - event.getFinalDamage() <= 0) {
                for (int i = 0; i < rewardsFromConfig.size(); i++) {

                    RewardMob reward = rewardsFromConfig.get(i);
                    if (reward.nameEquals(entity.getName()) && reward.typeEquals(entity.getType().name()) && reward.worldEquals(entity.getWorld().getName())) {

                        if (reward.getEnabledWorld() != null) {
                            World world = Bukkit.getWorld(reward.getEnabledWorld());
                            assert world != null;
                            if (reward.getEnabledRegion() != null) {

                                RegionManager regions = container.get(BukkitAdapter.adapt(world));
                                ProtectedRegion region = regions.getRegion(reward.getEnabledRegion());
                                if (region.contains(BukkitAdapter.asBlockVector(entity.getLocation()))) {

                                    giveRewards(i, reward);
                                }

                            } else {

                                giveRewards(i, reward);
                            }
                        } else {

                            giveRewards(i, reward);
                        }
                        HashMap<String, Double> selectedMap = damageMap.get(i + 1);
                        if (reward.getRadius() == -1) {
                            reward.getRewardMessages().forEach(message -> {
                                Bukkit.getOnlinePlayers().forEach(onlinePlayer -> onlinePlayer.sendMessage(translateColors(replacePlaceholders(message, player.getName(), selectedMap))));
                            });
                        } else {
                            entity.getNearbyEntities(reward.getRadius(), reward.getRadius(), reward.getRadius()).forEach(e -> {
                                if (e instanceof Player) {
                                    Player p = (Player) e;
                                    reward.getRewardMessages().forEach(message -> {
                                        p.sendMessage(translateColors(replacePlaceholders(message, player.getName(), selectedMap)));
                                    });
                                }
                            });
                        }
                        damageMap.get(i + 1).clear();
                    }
                }
            }
        }

        else if(damager instanceof Arrow){
            Projectile proj = (Projectile) event.getDamager();
            if(!(proj.getShooter() instanceof Player)) return;
            if(!(entity instanceof Mob)) return;
            Player player = (Player) proj.getShooter();

            Mob mob = (Mob) entity;



            for (int i = 0; i < rewardsFromConfig.size(); i++) {

                RewardMob reward = rewardsFromConfig.get(i);
                if (reward.nameEquals(entity.getName()) && reward.typeEquals(entity.getType().name()) && reward.worldEquals(entity.getWorld().getName())) {

                    if (reward.getEnabledWorld() != null) {
                        World world = Bukkit.getWorld(reward.getEnabledWorld());
                        assert world != null;
                        if (reward.getEnabledRegion() != null) {
                            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                            RegionManager regions = container.get(BukkitAdapter.adapt(world));
                            ProtectedRegion region = regions.getRegion(reward.getEnabledRegion());
                            if (region.contains(BukkitAdapter.asBlockVector(entity.getLocation()))) {

                                addToDamageMap(event, player, i);
                            }
                        } else {
                            ;
                            addToDamageMap(event, player, i);
                        }
                    } else {

                        addToDamageMap(event, player, i);
                    }

                }
            }


            if (mob.getHealth() - event.getFinalDamage() <= 0) {
                for (int i = 0; i < rewardsFromConfig.size(); i++) {

                    RewardMob reward = rewardsFromConfig.get(i);
                    if (reward.nameEquals(entity.getName()) && reward.typeEquals(entity.getType().name()) && reward.worldEquals(entity.getWorld().getName())) {

                        if (reward.getEnabledWorld() != null) {
                            World world = Bukkit.getWorld(reward.getEnabledWorld());
                            assert world != null;
                            if (reward.getEnabledRegion() != null) {
                                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                                RegionManager regions = container.get(BukkitAdapter.adapt(world));
                                ProtectedRegion region = regions.getRegion(reward.getEnabledRegion());
                                if (region.contains(BukkitAdapter.asBlockVector(entity.getLocation()))) {

                                    giveRewards(i, reward);
                                }

                            } else {

                                giveRewards(i, reward);
                            }
                        } else {
                            giveRewards(i, reward);
                        }
                        HashMap<String, Double> selectedMap = damageMap.get(i + 1);
                        if (reward.getRadius() == -1) {
                            reward.getRewardMessages().forEach(message -> {
                                Bukkit.getOnlinePlayers().forEach(onlinePlayer -> onlinePlayer.sendMessage(translateColors(replacePlaceholders(message, player.getName(), selectedMap))));
                            });
                        } else {
                            entity.getNearbyEntities(reward.getRadius(), reward.getRadius(), reward.getRadius()).forEach(e -> {
                                if (e instanceof Player) {
                                    Player p = (Player) e;
                                    reward.getRewardMessages().forEach(message -> {
                                        p.sendMessage(translateColors(replacePlaceholders(message, player.getName(), selectedMap)));
                                    });
                                }
                            });
                        }
                        damageMap.get(i + 1).clear();
                    }
                }
            }
        } else {
            return;
        }
    }

    private void giveRewards(int i, RewardMob reward) {
        HashMap<String, Double> selectedDamageMap = damageMap.get(i+1);
        ArrayList<Map.Entry<String, Double>> entrySet = new ArrayList<>(selectedDamageMap.entrySet());
        entrySet.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        for(int index = 0; index < selectedDamageMap.size(); index++){
            int rewardIndex = index + 1;
            Map.Entry<String, Double> entry = entrySet.get(index);
            reward.getAllRewards().forEach(allReward -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), allReward.replace("%player%", entry.getKey()).replace("%damage%", entry.getValue().toString())));
            reward.getRewards().get(rewardIndex).forEach(rewardString -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rewardString.replace("%player%", entry.getKey()).replace("%damage%", String.valueOf(entry.getValue())));
            });
        }
    }

    private void addToDamageMap(EntityDamageByEntityEvent event, Player player, int i) {
        HashMap<String, Double> selectedDamageMap = damageMap.get(i+1);
        Double selectedDamage = selectedDamageMap.get(player.getName());
        if(selectedDamage == null) selectedDamage = 0.0;
        selectedDamage += event.getDamage();
        selectedDamageMap.put(player.getName(), selectedDamage);
        selectedDamageMap.put("Deneme", 5.0);
    }

    private static String replacePlaceholders(String string, String playerName,  HashMap<String, Double> map){
        ArrayList<Map.Entry<String, Double>> entries =new ArrayList<>(map.entrySet());
        entries.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        Pattern pattern = Pattern.compile("%top_name_(\\d+)%");
        Matcher matcher = pattern.matcher(string);
        String newStr = string;
        while(matcher.find()){
            System.out.println(matcher.group(1));
            int index = Integer.parseInt(matcher.group(1))-1;
            if(index < entries.size()){
                newStr = newStr.replace(matcher.group(), entries.get(index).getKey());
            }
        }
        pattern = Pattern.compile("%top_damage_(\\d+)%");
        matcher = pattern.matcher(newStr);
        while(matcher.find()){
            System.out.println(matcher.group(1));
            int index = Integer.parseInt(matcher.group(1))-1;
            if(index < entries.size()){
                newStr = newStr.replace(matcher.group(), entries.get(index).getValue().toString());
            }
        }


        newStr = newStr.replace("%personal_damage%", String.valueOf(map.get(playerName)) != null ? String.valueOf(Math.round(map.get(playerName))) : "0");
        return newStr;
    }

    @EventHandler
    public void debug(EntityDamageByEntityEvent event){

        Entity entity = event.getEntity();
        Entity damager = event.getDamager();
        if (damager instanceof Player) {
            Player player = (Player) damager;
            if(player.isOp()){
                if(plugin.getConfig().getBoolean("Debug.enabled")) {
                    String world = entity.getLocation().getWorld().getName();
                    String type = entity.getType().name();
                    String name = entity.getName();

                    player.sendMessage("§a§l-------[REWARD SYSTEM DEBUG MESSAGE]-------");
                    player.sendMessage(ChatColor.AQUA + "if you wont see this message, you should set debug: false in config.yml");
                    player.sendMessage(ChatColor.AQUA + " ");
                    player.sendMessage(ChatColor.BLUE + "Mob world: " + world);
                    player.sendMessage(ChatColor.BLUE + "Mob type: " + type);
                    player.sendMessage(ChatColor.BLUE + "Mob name: " + name);
                    player.sendMessage("§a§l------------------------------------------");
                }

            }
        }
    }
}

