package provanasservices.rewardsystem;

import java.util.ArrayList;
import java.util.HashMap;

public class RewardMob {
    private int radius = -1;
    private ArrayList<String> rewardMessages;
    private String name;
    private String type;
    private String enabledWorld;
    private String enabledRegion;
    private ArrayList<String> allRewards;
    private HashMap<Integer, ArrayList<String>> rewards = new HashMap<>();

    public String getName() {
        return name;
    }

    public boolean nameEquals(String name) {
        if(this.name == null) return true;
        return this.name.equals(name);
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public boolean typeEquals(String type) {
        if(this.type == null) return true;
        return this.type.equals(type);
    }
    public void setType(String type) {
        this.type = type;
    }

    public String getEnabledWorld() {
        return enabledWorld;
    }

    public boolean worldEquals(String world) {
        if(this.enabledWorld == null) return true;
        return this.enabledWorld.equals(world);
    }
    public void setEnabledWorld(String enabledWorld) {
        this.enabledWorld = enabledWorld;
    }

    public String getEnabledRegion() {
        return enabledRegion;
    }

    public boolean regionEquals(String region) {
        if(this.enabledRegion == null) return true;
        return this.enabledRegion.equals(region);
    }
    public void setEnabledRegion(String enabledRegion) {
        this.enabledRegion = enabledRegion;
    }

    public ArrayList<String> getAllRewards() {
        return allRewards;
    }
    public void setAllRewards(ArrayList<String> allRewards) {
        this.allRewards = allRewards;
    }

    public HashMap<Integer, ArrayList<String>> getRewards() {
        return rewards;
    }

    public void setRewards(HashMap<Integer, ArrayList<String>> rewards) {
        this.rewards = rewards;
    }

    public ArrayList<String> getRewardMessages() {
        return rewardMessages;
    }

    public void setRewardMessages(ArrayList<String> rewardMessages) {
        this.rewardMessages = rewardMessages;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }
}
