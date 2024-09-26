package me.calrl.playtimechat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.units.qual.C;

public class PlaytimeChat extends JavaPlugin {

    private DebugMode debugMode;
    private ProtocolManager protocolManager;
    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        // Plugin startup logic
        debugMode = new DebugMode(this);
        protocolManager = ProtocolLibrary.getProtocolManager();

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "playtime:main", new ChatListener(this));

        protocolManager.addPacketListener(new PacketAdapter(
            this, ListenerPriority.HIGHEST, PacketType.Play.Client.CHAT
        ) {
            public void onPacketReceiving(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                Player player = event.getPlayer();

                if(!player.hasPermission("chat.allow")) {
                    event.setCancelled(true);
                    player.sendMessage("You cannot chat yet!");
                    player.sendMessage("You need to play for a total of 15 minutes before chatting!");
                }
            }
        });
    }



    @Override
    public void onDisable() {
        this.saveConfig();
        // Plugin shutdown logic
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    public DebugMode getDebugMode() {
        return debugMode;
    }
}
