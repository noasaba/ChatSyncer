package com.noasaba.chatsyncer;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * メインプラグインクラス
 * - config.yml をロード
 * - Botのステータス (PLAYING / WATCHING / LISTENING / STREAMING) を設定
 * - notificationsキーで定義されたDiscordチャンネルIDにサーバー通知を送信
 * - test, staff などは LunaChatListener / DiscordListener を使って同期
 */
public class ChatSyncer extends JavaPlugin {
    private JDA jda;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onEnable() {
        // config.yml がない場合はデフォルト配置
        saveDefaultConfig();
        reloadConfig();

        // Discordトークンなどの設定確認
        if (!validateConfig()) {
            getLogger().severe("設定が不正なため、ChatSyncerを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            // JDA(Bot)初期化
            initializeJDA();
            // イベントリスナー登録
            registerListeners();
            // リロード用コマンド設定
            getCommand("chatsyncer").setExecutor(new ReloadCommand(this));

            // サーバー起動通知
            // firstjoinなどのキーがあるため、"startup"キーを利用
            sendNotification("startup", false, null);

            getLogger().info("ChatSyncerプラグインが正常に起動しました。");
        } catch (Exception e) {
            getLogger().severe("ChatSyncer起動失敗: " + e.getMessage());
            safeShutdown();
        }
    }

    @Override
    public void onDisable() {
        isShuttingDown.set(true);
        // シャットダウン通知を2秒以内にDiscordへ送信
        CompletableFuture.runAsync(() -> sendNotification("shutdown", false, null))
                .orTimeout(2, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    getLogger().warning("シャットダウン通知に失敗: " + ex.getMessage());
                    return null;
                })
                .thenRun(this::safeShutdown);
    }

    private boolean validateConfig() {
        String token = getConfig().getString("discord.token");
        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN")) {
            getLogger().severe("discord.token が未設定です！");
            return false;
        }
        return true;
    }

    private void initializeJDA() throws InterruptedException {
        String token = getConfig().getString("discord.token");
        jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .build();
        jda.awaitReady();

        // Botステータス設定
        String statusType = getConfig().getString("discord.status.type", "PLAYING");
        String statusText = getConfig().getString("discord.status.text", "Minecraft");
        Activity activity = switch (statusType.toUpperCase()) {
            case "WATCHING" -> Activity.watching(statusText);
            case "LISTENING" -> Activity.listening(statusText);
            case "STREAMING" -> Activity.streaming(statusText, "https://www.twitch.tv/");
            default -> Activity.playing(statusText);
        };
        jda.getPresence().setActivity(activity);
    }

    private void registerListeners() {
        // 入退出 & 初参加の通知
        getServer().getPluginManager().registerEvents(new ServerEventListener(this), this);
        // LunaChat --> Discord
        getServer().getPluginManager().registerEvents(new LunaChatListener(this), this);
        // Discord --> Minecraft
        jda.addEventListener(new DiscordListener(this));
    }

    /**
     * サーバー停止、リソース解放
     */
    private void safeShutdown() {
        try {
            if (jda != null) {
                jda.shutdown();
                if (!jda.awaitShutdown(Duration.ofSeconds(1))) {
                    jda.shutdownNow();
                }
            }
            jda = null;
            executor.shutdown();
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            getServer().getScheduler().cancelTasks(this);
        }
    }

    /**
     * 入退出/初参加/サーバー起動/サーバー停止等の通知用メソッド。
     * @param type config.yml notifications内のキー (join, quit, firstjoin, startup, shutdown)
     * @param fromMinecraft trueならMinecraftのみ通知(システムタグ付与)、falseならDiscord & Minecraft両方
     * @param playerName {player}置換に使う（join, quit, firstjoinなど）
     */
    public void sendNotification(String type, boolean fromMinecraft, String playerName) {
        if (isShuttingDown.get()) return;

        String template = getConfig().getString("notifications." + type);
        if (template == null) return;

        String formatted = template;
        if (playerName != null) {
            formatted = formatted.replace("{player}", playerName);
        }
        // カラーコード
        formatted = ChatColor.translateAlternateColorCodes('&', formatted);

        if (fromMinecraft) {
            // システムタグ付与
            formatted += " " + getSystemTag();
        }
        // Minecraft
        getServer().broadcastMessage(formatted);

        // Discord
        if (!fromMinecraft && jda != null) {
            // notificationsキーのDiscordチャンネル
            String channelId = getConfig().getString("channel-mapping.notifications");
            if (channelId == null) return;

            final String msgForDiscord = ChatColor.stripColor(formatted);
            executor.execute(() -> {
                try {
                    TextChannel channel = jda.getTextChannelById(Long.parseLong(channelId));
                    if (channel != null) {
                        channel.sendMessage(msgForDiscord).queue();
                    }
                } catch (Exception e) {
                    getLogger().warning("Discord通知失敗: " + e.getMessage());
                }
            });
        }
    }

    /**
     * LunaChat --> Discord の通常チャット転送用
     */
    public void sendDiscordMessage(String channelId, String content) {
        if (isShuttingDown.get() || jda == null) return;
        final String msg = ChatColor.stripColor(content);
        executor.execute(() -> {
            try {
                TextChannel channel = jda.getTextChannelById(Long.parseLong(channelId));
                if (channel != null) {
                    channel.sendMessage(msg).queue();
                } else {
                    getLogger().warning("TextChannelがnull: " + channelId);
                }
            } catch (Exception e) {
                getLogger().warning("Discord送信エラー: " + e.getMessage());
            }
        });
    }

    public String getSystemTag() {
        return ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("system-tag", "§d[System]"));
    }

    public JDA getJDA() {
        return jda;
    }

    public boolean isShuttingDown() {
        return isShuttingDown.get();
    }
}
