package com.noasaba.chatsyncer;

import com.github.ucchyocean.lc.event.LunaChatChannelMessageEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LunaChatListener implements Listener {
    private final ChatSyncer plugin;

    public LunaChatListener(ChatSyncer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLunaChatMessage(LunaChatChannelMessageEvent event) {
        if (!plugin.isActive()) return;

        // Luna chat のチャンネル名（例: "test", "staff" など）を取得
        String lunaChannel = event.getChannelName();
        plugin.getLogger().info("LunaChatChannelMessageEvent受信。チャネル: " + lunaChannel);

        // 設定ファイルから該当する Discord チャンネルID を取得
        String channelId = plugin.getConfig().getString("channel-mapping." + lunaChannel);
        if (channelId == null || plugin.getJDA() == null) {
            plugin.getLogger().warning("DiscordチャンネルIDが見つかりません。キー: " + lunaChannel);
            return;
        }

        String message = String.format("%s: %s",
                event.getPlayer().getName(),
                event.getMessage()
        );

        try {
            plugin.getJDA().getTextChannelById(Long.parseLong(channelId))
                    .sendMessage(message).queue();
            plugin.getLogger().info("Luna chatメッセージをDiscordに送信: " + message);
        } catch (Exception e) {
            plugin.getLogger().warning("Minecraft→Discord送信エラー: " + e.getMessage());
        }
    }
}
