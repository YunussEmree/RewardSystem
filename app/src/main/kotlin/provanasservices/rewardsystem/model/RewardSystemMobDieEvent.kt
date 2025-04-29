package provanasservices.rewardsystem.model

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.Cancellable
import provanasservices.rewardsystem.model.RewardMob
import java.util.*

/**
 * Custom event fired when a mob is killed and rewards are about to be distributed.
 * This event can be listened to by other plugins to integrate with the reward system.
 * 
 * @property entity The entity that was killed
 * @property reward The reward configuration being applied
 * @property lastToucher The name of the player who last hit the entity (may be null)
 */
class RewardSystemMobDieEvent(
    val entity: LivingEntity,
    val reward: RewardMob,
    val lastToucher: String?
) : Event(), Cancellable {
    
    // For backward compatibility
    val damageMap: HashMap<String, Double> = HashMap()
    val rewardId: String = reward.id
    
    private var cancelled = false
    
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
    
    override fun isCancelled(): Boolean {
        return cancelled
    }
    
    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }
} 