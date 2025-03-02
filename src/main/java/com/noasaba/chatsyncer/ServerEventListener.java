package com.noasaba.chatsyncer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ServerEventListener implements Listener {
    private final ChatSyncer plugin;

    public ServerEventListener(ChatSyncer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getLogger().info("PlayerJoinEvent: " + event.getPlayer().getName());
        String message = plugin.getConfig().getString("notifications.join")
                .replace("{player}", event.getPlayer().getName());
        plugin.sendNotificationRaw(message);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getLogger().info("PlayerQuitEvent: " + event.getPlayer().getName());
        String message = plugin.getConfig().getString("notifications.quit")
                .replace("{player}", event.getPlayer().getName());
        plugin.sendNotificationRaw(message);
    }
}
