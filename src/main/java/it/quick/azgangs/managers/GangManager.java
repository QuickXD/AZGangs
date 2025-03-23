package it.quick.azgangs.managers;

import it.quick.azgangs.AZGangs;
import it.quick.azgangs.models.Gang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class GangManager {

    private final AZGangs plugin;
    private final Map<UUID, Gang> playerGangs;
    private final Map<String, Gang> gangsByName;
    private final Map<Integer, Gang> gangsById;

    public GangManager(AZGangs plugin) {
        this.plugin = plugin;
        this.playerGangs = new HashMap<>();
        this.gangsByName = new HashMap<>();
        this.gangsById = new HashMap<>();
        loadGangs();
    }

    private void loadGangs() {
        playerGangs.clear();
        gangsByName.clear();
        gangsById.clear();

        List<Gang> gangs = plugin.getDatabaseManager().getAllGangs();

        for (Gang gang : gangs) {
            gangsById.put(gang.getId(), gang);
            gangsByName.put(gang.getName().toLowerCase(), gang);

            for (UUID memberUUID : gang.getMembers()) {
                playerGangs.put(memberUUID, gang);
            }
        }

        plugin.getLogger().info("Loaded " + gangs.size() + " gangs with " + playerGangs.size() + " members.");
    }

    public boolean createGang(Player player, String name) {
        UUID playerUUID = player.getUniqueId();

        if (isPlayerInGang(playerUUID)) {
            player.sendMessage(plugin.getConfigManager().getMessage("already-in-gang"));
            return false;
        }

        if (getGangByName(name) != null) {
            player.sendMessage(plugin.getConfigManager().getMessage("gang-name-taken"));
            return false;
        }

        int minLength = plugin.getConfigManager().getMinNameLength();
        int maxLength = plugin.getConfigManager().getMaxNameLength();

        if (name.length() < minLength || name.length() > maxLength || !name.matches("[a-zA-Z0-9]+")) {
            player.sendMessage(plugin.getConfigManager().getMessage("gang-name-invalid")
                    .replace("%min%", String.valueOf(minLength))
                    .replace("%max%", String.valueOf(maxLength)));
            return false;
        }

        boolean success = plugin.getDatabaseManager().createGang(name, playerUUID);

        if (success) {
            loadGangs();

            player.sendMessage(plugin.getConfigManager().getMessage("gang-created")
                    .replace("%gangName%", name));

            return true;
        }

        return false;
    }

    public boolean disbandGang(Player player) {
        UUID playerUUID = player.getUniqueId();
        Gang gang = getPlayerGang(playerUUID);

        if (gang == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-in-gang"));
            return false;
        }

        if (!gang.isOwner(playerUUID)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-gang-owner"));
            return false;
        }

        boolean success = plugin.getDatabaseManager().disbandGang(gang.getId());

        if (success) {
            for (UUID memberUUID : gang.getMembers()) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    member.sendMessage(plugin.getConfigManager().getMessage("gang-disbanded")
                            .replace("%gangName%", gang.getName()));
                }
            }

            loadGangs();

            return true;
        }

        return false;
    }

    public boolean renameGang(Player player, String newName) {
        UUID playerUUID = player.getUniqueId();
        Gang gang = getPlayerGang(playerUUID);

        if (gang == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-in-gang"));
            return false;
        }

        if (!gang.isOwner(playerUUID)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-gang-owner"));
            return false;
        }

        if (getGangByName(newName) != null) {
            player.sendMessage(plugin.getConfigManager().getMessage("gang-name-taken"));
            return false;
        }

        int minLength = plugin.getConfigManager().getMinNameLength();
        int maxLength = plugin.getConfigManager().getMaxNameLength();

        if (newName.length() < minLength || newName.length() > maxLength || !newName.matches("[a-zA-Z0-9]+")) {
            player.sendMessage(plugin.getConfigManager().getMessage("gang-name-invalid")
                    .replace("%min%", String.valueOf(minLength))
                    .replace("%max%", String.valueOf(maxLength)));
            return false;
        }

        String oldName = gang.getName();
        boolean success = plugin.getDatabaseManager().renameGang(gang.getId(), newName);

        if (success) {
            for (UUID memberUUID : gang.getMembers()) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && member.isOnline()) {
                    member.sendMessage(plugin.getConfigManager().getMessage("gang-renamed")
                            .replace("%newName%", newName));
                }
            }

            gang.setName(newName);
            gangsByName.remove(oldName.toLowerCase());
            gangsByName.put(newName.toLowerCase(), gang);

            return true;
        }

        return false;
    }

    public boolean leaveGang(Player player) {
        UUID playerUUID = player.getUniqueId();
        Gang gang = getPlayerGang(playerUUID);

        if (gang == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-in-gang"));
            return false;
        }

        if (gang.isOwner(playerUUID)) {
            return disbandGang(player);
        }

        boolean success = plugin.getDatabaseManager().removeMember(playerUUID);

        if (success) {
            player.sendMessage(plugin.getConfigManager().getMessage("gang-left")
                    .replace("%gangName%", gang.getName()));

            for (UUID memberUUID : gang.getMembers()) {
                if (!memberUUID.equals(playerUUID)) {
                    Player member = Bukkit.getPlayer(memberUUID);
                    if (member != null && member.isOnline()) {
                        member.sendMessage(plugin.getConfigManager().getMessageNoPrefix("player-left-gang")
                                .replace("%playerName%", player.getName())
                                .replace("%gangName%", gang.getName()));
                    }
                }
            }

            loadGangs();

            return true;
        }

        return false;
    }

    public boolean addMember(Gang gang, UUID playerUUID) {
        int maxMembers = plugin.getConfigManager().getMaxMembersPerGang();
        if (gang.getMemberCount() >= maxMembers) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.sendMessage(plugin.getConfigManager().getMessage("gang-full"));
            }
            return false;
        }

        Gang currentGang = getPlayerGang(playerUUID);
        if (currentGang != null) {
            if (currentGang.getId() == gang.getId()) {
                return true;
            }

            boolean removed = plugin.getDatabaseManager().removeMember(playerUUID);
            if (!removed) {
                plugin.getLogger().warning("Errore nella rimozione di " + playerUUID + " dalla gang " +
                        currentGang.getId() + " prima di aggiungerlo ad una nuova gang");
                return false;
            }
        }

        boolean success = plugin.getDatabaseManager().addMember(gang.getId(), playerUUID);

        if (success) {
            gang.addMember(playerUUID);
            playerGangs.put(playerUUID, gang);

            Player newMember = Bukkit.getPlayer(playerUUID);
            if (newMember != null && newMember.isOnline()) {
                for (UUID memberUUID : gang.getMembers()) {
                    Player member = Bukkit.getPlayer(memberUUID);
                    if (member != null && member.isOnline() && !memberUUID.equals(playerUUID)) {
                        member.sendMessage(plugin.getConfigManager().getMessage("invite-accepted")
                                .replace("%playerName%", newMember.getName()));
                    }
                }
            }

            return true;
        }

        return false;
    }

    public Gang getPlayerGang(UUID playerUUID) {
        return playerGangs.get(playerUUID);
    }

    public Gang getGangByName(String name) {
        return gangsByName.get(name.toLowerCase());
    }

    public Gang getGangById(int id) {
        return gangsById.get(id);
    }

    public List<Gang> getAllGangs() {
        return new ArrayList<>(gangsById.values());
    }

    public boolean isPlayerInGang(UUID playerUUID) {
        return playerGangs.containsKey(playerUUID);
    }

    public boolean arePlayersInSameGang(UUID player1UUID, UUID player2UUID) {
        Gang gang1 = getPlayerGang(player1UUID);
        Gang gang2 = getPlayerGang(player2UUID);

        return gang1 != null && gang2 != null && gang1.equals(gang2);
    }
}