package it.quick.azgangs.utils;

import it.quick.azgangs.AZGangs;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final AZGangs plugin;
    private final FileConfiguration config;

    public ConfigManager(AZGangs plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public String getDatabaseType() {
        return config.getString("database.type", "mysql");
    }

    public String getDatabaseHost() {
        return config.getString("database.host", "localhost");
    }

    public int getDatabasePort() {
        return config.getInt("database.port", 3306);
    }

    public String getDatabaseName() {
        return config.getString("database.database", "azgangs");
    }

    public String getDatabaseUsername() {
        return config.getString("database.username", "root");
    }

    public String getDatabasePassword() {
        return config.getString("database.password", "password");
    }

    public String getDatabasePrefix() {
        return config.getString("database.table-prefix", "azgangs_");
    }

    public int getMaxMembersPerGang() {
        return config.getInt("gang-settings.max-members-per-gang", 10);
    }

    public boolean isPvpBetweenMembersEnabled() {
        return config.getBoolean("gang-settings.enable-pvp-between-members", false);
    }

    public int getMaxNameLength() {
        return config.getInt("gang-settings.max-name-length", 16);
    }

    public int getMinNameLength() {
        return config.getInt("gang-settings.min-name-length", 3);
    }

    public String getMessage(String path) {
        String prefix = config.getString("messages.prefix", "&8[&6AZGangs&8] ");
        String message = config.getString("messages." + path, "&cErrore: " + path);
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    public String getMessageNoPrefix(String path) {
        String message = config.getString("messages." + path, "&cErrore: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getHelpMessage(String command) {
        return ChatColor.translateAlternateColorCodes('&', config.getString("help." + command, "&cHelp not found: " + command));
    }

    public String getGangChatFormat() {
        return config.getString("messages.gang-chat-format", "&8[&eGang Chat&8] &7%playerName%&f: %message%");
    }
    public String getPermission(String permission) {
        return config.getString("permissions." + permission, "azgangs." + permission);
    }
}