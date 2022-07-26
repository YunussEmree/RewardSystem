package provanasservices.rewardsystem

class RewardMob {
    var radius = -1
    var rewardMessages: ArrayList<String>? = null
    var name: String? = null
    var type: String? = null
    var enabledWorld: String? = null
    var enabledRegion: String? = null
    var allRewards: ArrayList<String>? = null
    var rewards = HashMap<Int, ArrayList<String>>()
    fun nameEquals(name: String): Boolean {
        return if (this.name == null) true else this.name == name
    }

    fun typeEquals(type: String): Boolean {
        return if (this.type == null) true else this.type == type
    }

    fun worldEquals(world: String): Boolean {
        return if (enabledWorld == null) true else enabledWorld == world
    }

    fun regionEquals(region: String): Boolean {
        return if (enabledRegion == null) true else enabledRegion == region
    }
}