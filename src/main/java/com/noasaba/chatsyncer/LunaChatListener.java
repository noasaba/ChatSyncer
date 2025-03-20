package com.noasaba.chatsyncer;

import com.github.ucchyocean.lc.event.LunaChatChannelMessageEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * LunaChatのメッセージをDiscordへ転送する
 * notifications という LunaChatチャンネルは想定外
 */
public class LunaChatListener implements Listener {
    private final ChatSyncer plugin;

    public LunaChatListener(ChatSyncer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLunaChatMessage(LunaChatChannelMessageEvent event) {
        if (plugin.isShuttingDown()) return;

        // システムタグ付きは転送しない
        if (event.getMessage().contains(plugin.getSystemTag())) return;

        String lunaChannel = event.getChannelName();

        // config.yml の channel-mapping に "test", "staff" などがあればそれをキーにDiscordチャンネルIDを取得
        // notifications チャンネルは同期しない
        if (lunaChannel.equalsIgnoreCase("notifications")) {
            return;
        }
        String channelId = plugin.getConfig().getString("channel-mapping." + lunaChannel);
        if (channelId == null) {
            plugin.getLogger().warning("DiscordチャンネルIDが見つかりません: " + lunaChannel);
            return;
        }

        // suffix除去
        String message = event.getMessage();
        if (plugin.getConfig().getBoolean("lunachat.remove-suffix", false)) {
            String suffix = plugin.getConfig().getString("lunachat.suffix", "");
            if (!suffix.isEmpty() && message.endsWith(suffix)) {
                message = message.substring(0, message.length() - suffix.length()).trim();
            }
        }

        // プレイヤー名: メッセージ
        String formatted = event.getPlayer().getName() + ": " + message;
        plugin.sendDiscordMessage(channelId, formatted);
    }
}
