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

        // プレイヤーのメッセージを取得
        String message = event.getMessage();
        // config.yml の設定で、LunaChat側のsuffixを除去するかチェック
        if (plugin.getConfig().getBoolean("lunachat.remove-suffix", false)) {
            String suffix = plugin.getConfig().getString("lunachat.suffix", "");
            if (!suffix.isEmpty() && message.endsWith(suffix)) {
                message = message.substring(0, message.length() - suffix.length()).trim();
            }
        }

        // Discordに送信するメッセージの形式 (例: "プレイヤー名: メッセージ")
        String formatted = String.format("%s: %s", event.getPlayer().getName(), message);

        try {
            plugin.getJDA().getTextChannelById(Long.parseLong(channelId))
                    .sendMessage(formatted).queue();
            plugin.getLogger().info("Luna chatメッセージをDiscordに送信: " + formatted);
        } catch (Exception e) {
            plugin.getLogger().warning("Minecraft→Discord送信エラー: " + e.getMessage());
        }
    }
}
