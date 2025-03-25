package it.quick.azgangs;

import it.quick.azgangs.commands.GangCommand;
import it.quick.azgangs.database.DatabaseManager;
import it.quick.azgangs.listeners.DamageListener;
import it.quick.azgangs.listeners.GangChatListener;
import it.quick.azgangs.managers.GangManager;
import it.quick.azgangs.managers.InviteManager;
import it.quick.azgangs.placeholder.GangPlaceholder;
import it.quick.azgangs.utils.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AZGangs extends JavaPlugin {

    private static AZGangs instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private GangManager gangManager;
    private InviteManager inviteManager;
    private GangCommand gangCommand;

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

        gangCommand = new GangCommand(this);
        getCommand("gang").setExecutor(gangCommand);

        getServer().getPluginManager().registerEvents(new DamageListener(this), this);
        getServer().getPluginManager().registerEvents(new GangChatListener(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GangPlaceholder(this).register();
            getLogger().info("Correttamente hooked in PlaceholderAPI!");
        } else {
            getLogger().warning("PlaceholderAPI non trovato, i Placeholders non funzionerà.");
        }

        getLogger().info("AZGangs abilitato!");

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
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!disabledMessages.contains(player)) {
                    player.sendMessage("§e[AZGangs] §fPer aggiungere il nome della tua gang nel TAB, segui questa guida: §bhttps://pastebin.com/tuTKHXUk");
                    player.sendMessage("§7Per disabilitare questo messaggio, usa §c/gang off");
                }
            }
        }, 2400L);
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

    public GangCommand getGangCommand() {
        return gangCommand;
    }
}