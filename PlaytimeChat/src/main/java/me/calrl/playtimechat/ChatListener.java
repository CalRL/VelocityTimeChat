package me.calrl.playtimechat;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Logger;

public class ChatListener implements Listener, PluginMessageListener {
    private final PlaytimeChat plugin;
    private final Logger logger;
    private final DebugMode debugMode;

    public ChatListener(PlaytimeChat plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.debugMode = plugin.getDebugMode();
    }

    @EventHandler
    private void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if(!player.hasPermission("chat.allow") && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage("You cannot chat yet!");
            player.sendMessage("You need to play for a total of 15 minutes before chatting!");
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        FileConfiguration config = plugin.getConfig();
        debugMode.info("> Channel: " + channel);
        debugMode.info("> Player: " + player.getName());
        debugMode.info("> data: " + Arrays.toString(data));

        StringBuilder result = new StringBuilder();
        for (byte b : data) {
            if (b != 0) {  // Ignore NUL characters
                result.append((char) b);
            }
        }
        String text = result.toString();
        String[] texts = text.split("\\$");
        debugMode.info("Text Split");
        if (texts.length > 1) {
            debugMode.info("Texts Length > 1");
            //player.sendMessage(String.format(" '%s' ", texts[0]));
            if (texts[0].contains("chat.allow")) {
                debugMode.info("Adding permission...");
                UUID uuid = UUID.fromString(texts[1]);
                Player uuidPlayer = Bukkit.getPlayer(uuid);
                if (uuidPlayer != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + uuidPlayer.getName() + " permission set chat.allow true");
                    debugMode.info("Permission added!");
                } else {
                    //logger.warning("Player not found: " + texts[1]);
                }
            }
        } else {
            //logger.warning("Invalid plugin message format received.");
        }

    }
}
