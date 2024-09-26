package me.calrl.playtimechat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PlaytimeChatCommand implements TabExecutor {
    private PlaytimeChat plugin;
    public PlaytimeChatCommand(PlaytimeChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if(!sender.hasPermission("playtimechat.admin")) return true;

        if(args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Expected 2 args, got " + args.length);
            return true;
        }
        String state = args[0];
        String playerName = args[1];

        Player player = Bukkit.getPlayer(playerName);

        switch(state) {
            case "delete" -> {
             Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " permission remove chat.allow");

            }
            case "add" -> {

            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        return List.of();
    }
}
