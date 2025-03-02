package com.noasaba.chatsyncer;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatSyncer extends JavaPlugin {
    private JDA jda;
    private boolean active = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        if (!validateConfig()) {
            getLogger().severe("設定が不正なため、プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            initializeJDA();
            // Discord のステータス設定を反映する
            setDiscordStatus();
            registerListeners();
            getCommand("chatsyncer").setExecutor(this);
            active = true;
            // onEnable() で一度だけ起動通知を送信
            sendNotificationByKey("startup");
            getLogger().info("プラグインが正常に起動しました");
        } catch (Exception e) {
            getLogger().severe("起動失敗: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (active) {
            sendNotificationByKey("shutdown");
        }
        shutdownJDA();
    }

    // Discordトークンと channel-mapping セクションの存在を確認
    private boolean validateConfig() {
        String token = getConfig().getString("discord.token");
        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN")) {
            getLogger().severe("Discordトークンが未設定です！");
            return false;
        }
        if (getConfig().getConfigurationSection("channel-mapping") == null ||
                getConfig().getConfigurationSection("channel-mapping").getKeys(false).isEmpty()) {
            getLogger().severe("channel-mapping が設定されていません！");
            return false;
        }
        return true;
    }

    private void initializeJDA() throws InterruptedException {
        jda = JDABuilder.createDefault(getConfig().getString("discord.token"))
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .build();
        jda.awaitReady();
    }

    // Discord のステータス設定を行う
    private void setDiscordStatus() {
        String statusType = getConfig().getString("discord.status.type", "PLAYING").toUpperCase();
        String statusText = getConfig().getString("discord.status.text", "");
        if (statusText.isEmpty()) return;

        Activity activity;
        switch (statusType) {
            case "WATCHING":
                activity = Activity.watching(statusText);
                break;
            case "LISTENING":
                activity = Activity.listening(statusText);
                break;
            case "STREAMING":
                // STREAMING の場合、URL の指定が必須なので適当な URL を設定してください
                activity = Activity.streaming(statusText, "https://www.twitch.tv/");
                break;
            default:
                activity = Activity.playing(statusText);
                break;
        }
        jda.getPresence().setActivity(activity);
        getLogger().info("Discord ステータスを設定しました: " + statusType + " " + statusText);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new LunaChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ServerEventListener(this), this);
        jda.addEventListener(new DiscordListener(this));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("chatsyncer.reload")) {
            sender.sendMessage(ChatColor.RED + "権限がありません");
            return true;
        }
        reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "設定を再読み込みしました");
        return true;
    }

    /**
     * notifications セクションのキーからメッセージを取得し、送信する
     * （例："startup", "shutdown", "join", "quit" など）
     */
    public void sendNotificationByKey(String key) {
        String message = getConfig().getString("notifications." + key);
        if (message == null) {
            getLogger().warning("通知メッセージが見つかりません: notifications." + key);
            return;
        }
        sendNotificationRaw(message);
    }

    /**
     * 既にフォーマット済みのメッセージを、通知用 Discord チャンネルに送信する。
     * また、Minecraft のチャットにもブロードキャストします。
     */
    public void sendNotificationRaw(String message) {
        String formatted = ChatColor.translateAlternateColorCodes('&', message);
        getServer().broadcastMessage(formatted);

        // 通知用のチャンネルは専用キー "notifications" を使用
        String channelId = getConfig().getString("channel-mapping.notifications");
        if (channelId == null) {
            getLogger().warning("通知送信用の 'notifications' チャンネルID が設定されていません。");
            return;
        }
        try {
            TextChannel channel = jda.getTextChannelById(Long.parseLong(channelId));
            if (channel != null) {
                channel.sendMessage(ChatColor.stripColor(formatted)).queue();
                getLogger().info("通知メッセージを Discord に送信しました: " + formatted);
            } else {
                getLogger().warning("Discord チャンネルが見つかりません。ID: " + channelId);
            }
        } catch (Exception e) {
            getLogger().warning("Discordメッセージ送信失敗: " + e.getMessage());
        }
    }

    private void shutdownJDA() {
        if (jda != null) {
            jda.shutdown();
            jda = null;
        }
    }

    public JDA getJDA() {
        return jda;
    }

    public boolean isActive() {
        return active;
    }
}
