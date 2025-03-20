package com.noasaba.chatsyncer;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Discord -> Minecraft のチャット同期
 * notificationsキーのDiscordチャンネルからは同期しない
 */
public class DiscordListener extends ListenerAdapter {
    private final ChatSyncer plugin;

    public DiscordListener(ChatSyncer plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (plugin.isShuttingDown() || event.getAuthor().isBot()) return;

        // channel-mapping を取得
        ConfigurationSection mapping = plugin.getConfig().getConfigurationSection("channel-mapping");
        if (mapping == null) return;

        String channelId = event.getChannel().getId();

        // notifications 以外のキーを探す
        boolean matched = false;
        String matchedKey = null;
        for (String key : mapping.getKeys(false)) {
            if (key.equalsIgnoreCase("notifications")) {
                continue; // 同期しない
            }
            String mappedId = mapping.getString(key);
            if (mappedId != null && mappedId.equals(channelId)) {
                matched = true;
                matchedKey = key; // "test", "staff" etc
                break;
            }
        }
        if (!matched) return; // 同期対象外

        // コマンドかどうか
        String content = event.getMessage().getContentRaw();
        // 例: "!p"
        String playerListCmd = plugin.getConfig().getString("playerlist.command", "!p");
        if (content.equalsIgnoreCase(playerListCmd)) {
            handlePlayerListCommand(event);
            return;
        }

        forwardToMinecraft(event, matchedKey);
    }

    private void handlePlayerListCommand(MessageReceivedEvent event) {
        // Bukkitのメインスレッドでオンラインプレイヤー取得
        Bukkit.getScheduler().runTask(plugin, () -> {
            int online = Bukkit.getOnlinePlayers().size();
            int max = Bukkit.getMaxPlayers();

            if (online == 0) {
                String emptyMsg = plugin.getConfig().getString("playerlist.empty", "サーバーにプレイヤーはいません");
                event.getChannel().sendMessage(emptyMsg).queue();
                return;
            }

            StringBuilder names = new StringBuilder();
            Bukkit.getOnlinePlayers().forEach(p -> names.append(p.getName()).append("\n"));

            String format = plugin.getConfig().getString("playerlist.format",
                    "&6オンラインプレイヤー数 ({online}/{max}):\n&7{players}");
            format = format.replace("{online}", String.valueOf(online))
                    .replace("{max}", String.valueOf(max))
                    .replace("{players}", names.toString().trim());

            event.getChannel().sendMessage("```ansi\n" + format + "\n```").queue();
        });
    }

    private void forwardToMinecraft(MessageReceivedEvent event, String key) {
        // "!..." で始まるコマンドは同期しない
        if (event.getMessage().getContentRaw().startsWith("!")) return;

        // discord.message-format に {key}, {user}, {message} を埋め込む
        String format = plugin.getConfig().getString("discord.message-format", "&9[Discord-{key}] {user}&f: {message}");
        format = format
                .replace("{key}", key)
                .replace("{user}", event.getAuthor().getName())
                .replace("{message}", event.getMessage().getContentRaw());

        final String broadcastMessage = format;
        // Bukkitのメインスレッドでチャット表示
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcastMessage))
        );
    }
}
