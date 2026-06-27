package ym.timeRewards.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ym.timeRewards.data.PlayerDataService
import ym.timeRewards.service.TimeTrackingService

class PlayerSessionListener(
    private val playerDataService: PlayerDataService,
    private val trackingService: TimeTrackingService,
) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        playerDataService.load(event.player.uniqueId, event.player.name)
        trackingService.handleJoin(event.player.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        if (trackingService.handleQuit(event.player.uniqueId)) {
            playerDataService.unload(event.player.uniqueId)
        }
    }
}
