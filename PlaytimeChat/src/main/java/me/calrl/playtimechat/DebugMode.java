package me.calrl.playtimechat;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DebugMode {

    private final FileConfiguration config;
    private PlaytimeChat plugin;
    private final Logger logger;

    public DebugMode(PlaytimeChat plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.logger = plugin.getLogger();
    }

    public void info(String message) {
        if(config.getBoolean("debug")) {
            logger.log(Level.INFO, "[DEBUG] " + message);
        }
    }
}
