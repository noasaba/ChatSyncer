package com.noasaba.chatsyncer;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /chatsyncer reload
 */
public class ReloadCommand implements CommandExecutor {
    private final ChatSyncer plugin;

    public ReloadCommand(ChatSyncer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("chatsyncer.reload")) {
            sender.sendMessage(ChatColor.RED + "権限がありません");
            return true;
        }
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "ChatSyncerの設定をリロードしました");
        return true;
    }
}
