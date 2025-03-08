package provanasservices.rewardsystem.model

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.*

/**
 * Custom event fired when a mob is killed and rewards are about to be distributed.
 * This event can be listened to by other plugins to integrate with the reward system.
 * 
 * @property damageMap Map of player names to their damage contributions
 * @property rewardId ID of the reward configuration being applied
 */
class RewardSystemMobDieEvent(
    val damageMap: HashMap<String, Double>, 
    val rewardId: String
) : Event() {
    companion object {
        private val HANDLERS = HandlerList()
        
        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }
    
    override fun getHandlers(): HandlerList {
        return HANDLERS
    }
} 