package me.calrl.velocityonlinetime;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.h2.jdbcx.JdbcConnectionPool;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "velocityonlinetime",
        name = "VelocityOnlineTime",
        version = BuildConstants.VERSION,
        authors = {"Cal"}
)
public class VelocityOnlineTime {

    private final Logger logger;
    private final ProxyServer server;
    private Connection connection;
    private final Map<UUID, Instant> joinTimeMap = new HashMap<>();
    private static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from("playtime:main");
    private int time;
    private Path dataDirectory;
    @Inject
    public VelocityOnlineTime(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) throws IOException {
        initializeDb();
        try {
            server.getChannelRegistrar().register(IDENTIFIER);
            logger.info(IDENTIFIER + " registered.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        initializeFile();




    }

    private boolean sendPluginMessageWithRetry(RegisteredServer server, MinecraftChannelIdentifier identifier, byte[] data, int retries) {
        int attempts = 0;
        boolean success = false;
        while (attempts < retries && !success) {
            success = server.sendPluginMessage(identifier, data);
            if (!success) {
                logger.warn("Failed to send plugin message. Attempt {}/{}.", attempts + 1, retries);
                attempts++;
                try {
                    Thread.sleep(1000); // Wait 1 second before retrying
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return success;
    }

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (connection == null) {
            logger.error("Database connection is not available for player {}", player.getUsername());
            return;
        }

        try {
            PreparedStatement selectStatement = connection.prepareStatement("SELECT time FROM player_time WHERE uuid = ?");
            selectStatement.setString(1, uuid.toString());
            ResultSet resultSet = selectStatement.executeQuery();

            long currentTime = 0;
            if (resultSet.next()) {
                currentTime = resultSet.getLong("time");
            }
            resultSet.close();

            if (currentTime < time) {
                joinTimeMap.put(uuid, Instant.now());
                logger.info("Player {} has been added for time tracking.", player.getUsername());
            } else {
                //logger.info("Player {} has already exceeded 900 seconds and will not be tracked further.", player.getUsername());
            }
        } catch (SQLException e) {
            //logger.error("Failed to check player time in H2 database for player {}", player.getUsername(), e);
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        joinTimeMap.remove(uuid);
    }

    private void updateOnlineTime() {
        synchronized (joinTimeMap) {
            for (UUID uuid : new HashMap<>(joinTimeMap).keySet()) {
                try (PreparedStatement selectStatement = connection.prepareStatement("SELECT time FROM player_time WHERE uuid = ?")) {
                    selectStatement.setString(1, uuid.toString());
                    try (ResultSet resultSet = selectStatement.executeQuery()) {
                        long currentTime = 0;
                        if (resultSet.next()) {
                            currentTime = resultSet.getLong("time");
                        }

                        if (currentTime < time) {
                            // Increase time by 5 seconds
                            currentTime += 5;

                            // Update the database
                            try (PreparedStatement updateStatement = connection.prepareStatement(
                                    "MERGE INTO player_time (uuid, time) KEY (uuid) VALUES (?, ?)"
                            )) {
                                updateStatement.setString(1, uuid.toString());
                                updateStatement.setLong(2, currentTime);
                                updateStatement.executeUpdate();

                                //logger.info("Updated online time for player {}: {} seconds", uuid, currentTime);
                            }

                            if (currentTime >= time) {
                                joinTimeMap.remove(uuid);
                                logger.info("Player {} has reached " + time + " seconds and will no longer be tracked.", uuid);

                                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                                out.writeUTF("addPermission");
                                out.writeUTF("chat.allow");
                                out.writeUTF(uuid.toString());

                                Optional<Player> optionalPlayer = server.getPlayer(uuid);
                                if (optionalPlayer.isPresent()) {
                                    Optional<ServerConnection> currentServer = optionalPlayer.get().getCurrentServer();
                                    if (currentServer.isPresent()) {
                                        currentServer.get().sendPluginMessage(IDENTIFIER, out.toByteArray());
                                        logger.info("Sent plugin message for player {} to server.", uuid);
                                    } else {
                                        logger.warn("Player {} is not connected to any server.", uuid);
                                    }
                                } else {
                                    logger.warn("Player with UUID {} is not online.", uuid);
                                }
                            }
                        } else {
                            joinTimeMap.remove(uuid);
                            logger.info("Player {} has already reached or exceeded " + time + " seconds and will be removed from tracking.", uuid);
                        }
                    }
                } catch (SQLException e) {
                    logger.error("Failed to update player time in H2 database for player {}", uuid, e);
                } catch (Exception e) {
                    logger.error("Unexpected error occurred while updating online time for player {}", uuid, e);
                }
            }
        }
    }


    public void initializeDb() {
        try {
            // Specify the directory where the .h2.db file will be stored
            logger.info("Creating file...");
            File databaseDir = new File("plugins/VelocityOnlineTime");
            if (!databaseDir.exists()) {
                if (databaseDir.mkdirs()) {
                    System.out.println("Created directory for H2 database: " + databaseDir.getAbsolutePath());
                } else {
                    System.out.println("Failed to create directory for H2 database.");
                    return;
                }
            }

            // Define the H2 database connection URL
            String dbUrl = "jdbc:h2:" + databaseDir.getAbsolutePath() + "/onlinetime";

            // Initialize the H2 database connection
            connection = JdbcConnectionPool.create(dbUrl, "user", "password").getConnection();
            System.out.println("Successfully connected to H2 database.");

            // Create the player_time table if it doesn't exist
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_time (uuid VARCHAR(36) PRIMARY KEY, time BIGINT)");
                System.out.println("Ensured the player_time table exists.");
            }

        } catch (SQLException e) {
            System.out.println("Failed to connect to H2 database");
            System.out.print(e);
            connection = null; // Explicitly set connection to null on failure
            }

        if (connection != null) {
            // Schedule the task to run every 5 seconds
            server.getScheduler().buildTask(this, this::updateOnlineTime)
                    .repeat(5, TimeUnit.SECONDS)
                    .schedule();
        } else {
            logger.error("Database connection was not established, cannot start scheduled tasks.");
        }
    }
    public void initializeFile() throws IOException {
        final File config = new File(String.valueOf(dataDirectory), "config.yml");
        if(!config.exists()) {
            createDefaultConfig(config);
        }
        final YamlConfigurationLoader loader = YamlConfigurationLoader.builder().file(config).build();
        final CommentedConfigurationNode node = loader.load();
        final String timeString = node.node("time").getString();
        if(timeString != null && !timeString.isEmpty()) {
            time = Integer.parseInt(timeString);
            logger.info("Loaded time " + time);
        }

    }
    private void createDefaultConfig(File configFile) {
        try {
            // Ensure the directory exists
            File parentDir = configFile.getParentFile();
            if (!parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    logger.error("Failed to create config directory: " + parentDir.getAbsolutePath());
                    return;
                }
            }

            // Manually create the file and write default content
            if (configFile.createNewFile()) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                    writer.write("time: 900");
                    writer.newLine();
                }
                logger.info("Created default config file at: " + configFile.getAbsolutePath());
            } else {
                logger.error("Failed to create config file: " + configFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to create default config file", e);
        }
    }
}
