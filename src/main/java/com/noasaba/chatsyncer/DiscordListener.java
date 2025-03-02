package com.noasaba.chatsyncer;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import java.util.Set;

public class DiscordListener extends ListenerAdapter {
    private final ChatSyncer plugin;

    public DiscordListener(ChatSyncer plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!plugin.isActive() || event.getAuthor().isBot()) return;

        // channel-mapping セクション内のいずれかの値と一致するかチェック
        Set<String> keys = plugin.getConfig().getConfigurationSection("channel-mapping").getKeys(false);
        boolean validChannel = false;
        for (String key : keys) {
            String discordChannelId = plugin.getConfig().getString("channel-mapping." + key);
            if (discordChannelId != null && event.getChannel().getId().equals(discordChannelId)) {
                validChannel = true;
                break;
            }
        }
        if (!validChannel) return;

        handlePlayerListCommand(event);
        forwardToMinecraft(event);
    }

    private void handlePlayerListCommand(MessageReceivedEvent event) {
        String command = plugin.getConfig().getString("playerlist.command");
        if (!event.getMessage().getContentRaw().equalsIgnoreCase(command)) return;

        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        StringBuilder players = new StringBuilder();
        Bukkit.getOnlinePlayers().forEach(p -> players.append(p.getName()).append("\n"));

        // config.yml に記述された書式そのままを使用（囲みの指定もユーザー側で可能）
        String response = plugin.getConfig().getString("playerlist.format")
                .replace("{online}", String.valueOf(online))
                .replace("{max}", String.valueOf(max))
                .replace("{players}", players.toString().trim());

        event.getChannel().sendMessage(response).queue();
    }

    private void forwardToMinecraft(MessageReceivedEvent event) {
        String format = plugin.getConfig().getString("discord.message-format")
                .replace("{user}", event.getAuthor().getName())
                .replace("{message}", event.getMessage().getContentRaw());

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', format));
    }
}
