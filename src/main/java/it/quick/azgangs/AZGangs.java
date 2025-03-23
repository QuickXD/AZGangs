package it.quick.azgangs;

import it.quick.azgangs.commands.GangCommand;
import it.quick.azgangs.database.DatabaseManager;
import it.quick.azgangs.listeners.DamageListener;
import it.quick.azgangs.managers.GangManager;
import it.quick.azgangs.managers.InviteManager;
import it.quick.azgangs.placeholder.GangPlaceholder;
import it.quick.azgangs.utils.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public class AZGangs extends JavaPlugin {

    private static AZGangs instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private GangManager gangManager;
    private InviteManager inviteManager;

    private final Set<Player> disabledMessages = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        configManager = new ConfigManager(this);

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        gangManager = new GangManager(this);
        inviteManager = new InviteManager(this);

        getCommand("gang").setExecutor(new GangCommand(this));

        getServer().getPluginManager().registerEvents(new DamageListener(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GangPlaceholder(this).register();
            getLogger().info("Successfully hooked into PlaceholderAPI!");
        } else {
            getLogger().warning("PlaceholderAPI not found! Placeholders will not work.");
        }

        getLogger().info("AZGangs has been enabled!");

        startAnnouncementTask();
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        getLogger().info("AZGangs has been disabled!");
    }

    private void startAnnouncementTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!disabledMessages.contains(player)) {
                    player.sendMessage("§e[AZGangs] §fTo add your gang name in the TAB, follow this guide: §bhttps://pastebin.com/tuTKHXUk");
                    player.sendMessage("§7To disable this message, use §c/gang off");
                }
            }
        }, 3600L, 36000L);
    }

    public void disableGangMessage(Player player) {
        disabledMessages.add(player);
    }

    public static AZGangs getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public GangManager getGangManager() {
        return gangManager;
    }

    public InviteManager getInviteManager() {
        return inviteManager;
    }
}
