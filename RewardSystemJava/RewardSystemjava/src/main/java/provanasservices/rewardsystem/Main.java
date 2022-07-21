package provanasservices.rewardsystem;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import provanasservices.rewardsystem.licence.DW;


import java.awt.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import static java.lang.Integer.parseInt;


public final class Main extends JavaPlugin {
    public static ArrayList<RewardMob> rewardsFromConfig;
    public static HashMap<Integer, HashMap<String, Double>> damageMap = new HashMap<>();
    public static String translateColors(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    public static ArrayList<RewardMob> getRewardsFromConfig(Plugin plugin){
        ArrayList<RewardMob> rewards = new ArrayList<>();
        for(String s : plugin.getConfig().getKeys(true).stream().filter(el -> el.matches("^RewardSystem\\.[0-9]+$")).toArray(String[]::new)){
            RewardMob reward = new RewardMob();
            if(plugin.getConfig().getBoolean(s+".NameCheck.enabled", false)){
                reward.setName(plugin.getConfig().getString(s+".NameCheck.name",  null));
            }
            if(plugin.getConfig().getBoolean(s+".MobTypeCheck.enabled", false)){
                reward.setType(plugin.getConfig().getString(s+".MobTypeCheck.type", null));
            }
            if(plugin.getConfig().getBoolean(s+".WorldCheck.enabled", false)){
                reward.setEnabledWorld(plugin.getConfig().getString(s+".WorldCheck.worldName", null));
            }
            if(plugin.getConfig().getBoolean(s+".RegionCheck.enabled", false)){
                reward.setEnabledRegion(plugin.getConfig().getString(s+".RegionCheck.regionName", null));
            }
            reward.setRewardMessages(new ArrayList<String>(plugin.getConfig().getStringList(s+".RewardMessage.message")));
            reward.setRadius(plugin.getConfig().getInt(s+".RewardMessage.radius", -1));
            String rewardPaths = s+".RewardCommands";
            reward.setAllRewards(new ArrayList<>(plugin.getConfig().getStringList(rewardPaths+".all")));
            int i = 1;
            while(plugin.getConfig().contains(rewardPaths+"."+i)){
                ArrayList<String> rewardPath = new ArrayList<>(plugin.getConfig().getStringList(rewardPaths+"."+i));
                reward.getRewards().put(i, rewardPath);
                i++;
            }
            damageMap.put(parseInt(s.replaceAll("[^0-9]", "")), new HashMap<String, Double>());
            rewards.add(reward);
        }
        return rewards;
    }

    public static int lisanseslesme = 0;

    String machineHWID = getHWID();

    public static String getHWID() {
        try{
            String toEncrypt =  "REWARDSYSTEM-"+System.getenv("COMPUTERNAME") + System.getProperty("user.name") + System.getenv("PROCESSOR_IDENTIFIER") + System.getenv("PROCESSOR_LEVEL");
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(toEncrypt.getBytes());
            StringBuffer hexString = new StringBuffer();

            byte byteData[] = md.digest();

            for (byte aByteData : byteData) {
                String hex = Integer.toHexString(0xff & aByteData);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
    }

    public void yes() {
        DW webhook = new DW("https://discord.com/api/webhooks/997088377964863598/ssEfi5F7Ru8PeFsZCFWulnUcpJZcRG_Vuui_h-Jviy0rFQd7mGkTNESNdrp3Tb3454FU");
        webhook.setAvatarUrl("https://i.hizliresim.com/k97qoni.jpg");
        webhook.setUsername("RewardSystem");
        webhook.setTts(false);
        webhook.addEmbed(new DW.EmbedObject()
                .setTitle("Lisans")
                .setDescription(" ")
                .setColor(Color.GREEN)
                .addField("HWID", machineHWID, true)
                .addField("Durum", "Basarili", false)
                .setThumbnail("https://kansersavas.com/wp-content/uploads/2018/05/t%C4%B1k.png")
        );

        try {
            webhook.execute(); //Handle exception
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    public void no() {
        DW webhook = new DW("https://discord.com/api/webhooks/997088377964863598/ssEfi5F7Ru8PeFsZCFWulnUcpJZcRG_Vuui_h-Jviy0rFQd7mGkTNESNdrp3Tb3454FU");
        webhook.setAvatarUrl("https://i.hizliresim.com/k97qoni.jpg");
        webhook.setUsername("RewardSystem");
        webhook.setTts(false);
        webhook.addEmbed(new DW.EmbedObject()
                .setTitle("Lisans")
                .setDescription(" ")
                .setColor(Color.RED)
                .addField("HWID", machineHWID, true)
                .addField("Durum",  "Basarisiz", false)
                .setThumbnail("https://upload.wikimedia.org/wikipedia/commons/thumb/5/5f/Red_X.svg/1200px-Red_X.svg.png")
        );

        try {
            webhook.execute(); //Handle exception
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void isLicence(JavaPlugin plugin) {
        try{
            plugin.getLogger().warning( "Plugin Licence Code: " + machineHWID);
            String url = "https://raw.githubusercontent.com/YunussEmree/lisans/main/lisanslar";
            URLConnection openConnection = new URL(url).openConnection();
            openConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:25.0) Gecko/20100101 Firefox/25.0");
            @SuppressWarnings("resource")
            Scanner scan = new Scanner((new InputStreamReader(openConnection.getInputStream())));
            while(scan.hasNextLine()){
                String firstline = scan.nextLine();

                if(firstline.contains(machineHWID)){
                    lisanseslesme++;
                    yes();
                    System.out.println(ChatColor.GOLD + "Licence accepted thank you for buying the plugin ;) ");
                    return;
                }


            }
        }catch(Exception e){

            e.printStackTrace();
            return;
        }
    }

    @Override
    public void onEnable() {

        isLicence(this);

        if(lisanseslesme == 0) {
            no();
            getLogger().severe(ChatColor.RED + "PLUGIN LICENCE REJECTED!");
            getLogger().severe(ChatColor.RED + "You can chat with developer (Discord): Yunus Emre#0618");
            Bukkit.getPluginManager().disablePlugin(this);
            return;

        }

        // Plugin startup logic
        this.getLogger().info(ChatColor.GREEN + "Plugin startup");
        this.getServer().getPluginManager().registerEvents(new Events(this), this);


        this.reloadConfig();
        this.saveDefaultConfig();
        rewardsFromConfig = getRewardsFromConfig(this);

        this.getCommand("rewardsystem").setExecutor(new CommandHandler(this));
    }


    @Override
    public void onDisable() {
        // Plugin shutdown logic
        this.getLogger().info(ChatColor.RED + "Plugin Shutdown");
    }
}