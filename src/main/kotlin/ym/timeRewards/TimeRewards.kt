package ym.timeRewards

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import org.bukkit.plugin.java.JavaPlugin
import ym.easygui.EasyGuiBootstrap
import ym.easygui.core.GuiManager
import ym.timeRewards.command.TimeRewardsCommand
import ym.timeRewards.config.ConfigManager
import ym.timeRewards.data.PlayerDataService
import ym.timeRewards.listener.PlayerSessionListener
import ym.timeRewards.placeholder.TimeRewardsPlaceholderExpansion
import ym.timeRewards.service.RewardGuiService
import ym.timeRewards.service.RewardService
import ym.timeRewards.service.TimeTrackingService

class TimeRewards : JavaPlugin() {
    lateinit var configManager: ConfigManager
        private set
    lateinit var playerDataService: PlayerDataService
        private set
    lateinit var trackingService: TimeTrackingService
        private set
    lateinit var rewardService: RewardService
        private set
    lateinit var guiManager: GuiManager
        private set
    lateinit var rewardGuiService: RewardGuiService
        private set

    private var placeholderExpansion: TimeRewardsPlaceholderExpansion? = null
    private var autoClaimTask: BukkitTask? = null

    override fun onEnable() {
        saveDefaultConfig()
        saveResource("messages.yml", false)
        saveDefaultRewardResources()

        configManager = ConfigManager(this)
        configManager.reload()

        playerDataService = PlayerDataService(this)
        trackingService = TimeTrackingService(this, playerDataService)
        rewardService = RewardService(this, configManager, playerDataService, trackingService)
        guiManager = EasyGuiBootstrap.create(this)
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            guiManager.registerPlaceholderApiStage()
        }
        rewardGuiService = RewardGuiService(configManager, playerDataService, trackingService, rewardService, guiManager)

        server.pluginManager.registerEvents(PlayerSessionListener(playerDataService, trackingService), this)

        val command = TimeRewardsCommand(this)
        getCommand("timerewards")?.apply {
            setExecutor(command)
            tabCompleter = command
        }

        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            placeholderExpansion = TimeRewardsPlaceholderExpansion(this)
            placeholderExpansion?.register()
        }
        startAutoClaimTask()

        Bukkit.getOnlinePlayers().forEach { player ->
            playerDataService.load(player.uniqueId, player.name)
            trackingService.handleJoin(player.uniqueId)
        }
    }

    override fun onDisable() {
        autoClaimTask?.cancel()
        autoClaimTask = null
        placeholderExpansion?.unregister()
        trackingService.flushOnline()
        playerDataService.saveAll()
        playerDataService.close()
        if (::guiManager.isInitialized) {
            guiManager.closeAll()
        }
    }

    fun reloadPlugin() {
        trackingService.flushOnline()
        reloadConfig()
        configManager.reload()
        playerDataService.reloadStorage()
        Bukkit.getOnlinePlayers().forEach { player ->
            playerDataService.load(player.uniqueId, player.name)
            trackingService.handleJoin(player.uniqueId)
            guiManager.refresh(player)
        }
    }

    private fun saveDefaultRewardResources() {
        listOf(
            "Rewards/day.yml",
            "Rewards/week.yml",
            "Rewards/month.yml",
            "Rewards/year.yml",
            "Rewards/total.yml",
        ).forEach { resourcePath ->
            saveResource(resourcePath, false)
        }
    }

    private fun startAutoClaimTask() {
        autoClaimTask?.cancel()
        autoClaimTask = server.scheduler.runTaskTimer(this, Runnable {
            Bukkit.getOnlinePlayers().forEach { player ->
                if (playerDataService.isAutoClaimEnabled(player.uniqueId)) {
                    rewardService.autoClaimAvailable(player)
                }
            }
        }, 20L * 60L, 20L * 60L)
    }
}
