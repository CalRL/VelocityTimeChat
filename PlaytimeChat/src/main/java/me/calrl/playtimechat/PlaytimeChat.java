package me.calrl.playtimechat;

import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.units.qual.C;

public class PlaytimeChat extends JavaPlugin {

    private DebugMode debugMode;
    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        // Plugin startup logic
        debugMode = new DebugMode(this);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "playtime:main", new ChatListener(this));

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
