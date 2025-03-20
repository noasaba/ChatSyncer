package com.noasaba.chatsyncer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 入退出および初参加時の通知
 */
public class ServerEventListener implements Listener {
    private final ChatSyncer plugin;

    public ServerEventListener(ChatSyncer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.isShuttingDown()) return;

        // 初参加チェック
        if (!event.getPlayer().hasPlayedBefore()) {
            // 初参加 (firstjoin)
            plugin.sendNotification("firstjoin", false, event.getPlayer().getName());
        } else {
            // 通常参加 (join)
            plugin.sendNotification("join", false, event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.isShuttingDown()) return;
        plugin.sendNotification("quit", false, event.getPlayer().getName());
    }
}
