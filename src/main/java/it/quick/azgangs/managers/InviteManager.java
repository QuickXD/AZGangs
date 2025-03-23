package it.quick.azgangs.managers;

import it.quick.azgangs.AZGangs;
import it.quick.azgangs.models.Gang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InviteManager {

    private final AZGangs plugin;
    private final Map<UUID, Map<Integer, Long>> invites;
    private static final long INVITE_EXPIRATION_TIME = 60 * 1000;

    public InviteManager(AZGangs plugin) {
        this.plugin = plugin;
        this.invites = new ConcurrentHashMap<>();

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredInvites, 6000, 6000);
    }

    public void sendInvite(Player sender, Player target) {
        UUID senderUUID = sender.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        Gang gang = plugin.getGangManager().getPlayerGang(senderUUID);
        if (gang == null) {
            sender.sendMessage(plugin.getConfigManager().getMessage("not-in-gang"));
            return;
        }

        if (!gang.isOwner(senderUUID) && !sender.hasPermission(plugin.getConfigManager().getPermission("admin"))) {
            sender.sendMessage(plugin.getConfigManager().getMessage("not-gang-owner"));
            return;
        }

        if (plugin.getGangManager().isPlayerInGang(targetUUID)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-already-in-gang"));
            return;
        }

        int maxMembers = plugin.getConfigManager().getMaxMembersPerGang();
        if (gang.getMemberCount() >= maxMembers) {
            sender.sendMessage(plugin.getConfigManager().getMessage("gang-full"));
            return;
        }

        invites.computeIfAbsent(targetUUID, k -> new HashMap<>());

        invites.get(targetUUID).put(gang.getId(), System.currentTimeMillis());

        sender.sendMessage(plugin.getConfigManager().getMessage("invite-sent")
                .replace("%playerName%", target.getName()));

        target.sendMessage(plugin.getConfigManager().getMessage("invite-received")
                .replace("%gangName%", gang.getName()));
    }

    public boolean acceptInvite(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!invites.containsKey(playerUUID) || invites.get(playerUUID).isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-pending-invites"));
            return false;
        }

        Map<Integer, Long> playerInvites = invites.get(playerUUID);
        Map.Entry<Integer, Long> mostRecentInvite = null;

        for (Map.Entry<Integer, Long> entry : playerInvites.entrySet()) {
            if (mostRecentInvite == null || entry.getValue() > mostRecentInvite.getValue()) {
                mostRecentInvite = entry;
            }
        }

        if (mostRecentInvite == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-pending-invites"));
            return false;
        }

        if (System.currentTimeMillis() - mostRecentInvite.getValue() > INVITE_EXPIRATION_TIME) {
            playerInvites.remove(mostRecentInvite.getKey());
            player.sendMessage(plugin.getConfigManager().getMessage("invite-expired"));
            return false;
        }

        Gang gang = plugin.getGangManager().getGangById(mostRecentInvite.getKey());
        if (gang == null) {
            playerInvites.remove(mostRecentInvite.getKey());
            player.sendMessage(plugin.getConfigManager().getMessage("gang-not-found"));
            return false;
        }

        boolean success = plugin.getGangManager().addMember(gang, playerUUID);

        if (success) {
            invites.remove(playerUUID);

            player.sendMessage(plugin.getConfigManager().getMessage("joined-gang")
                    .replace("%gangName%", gang.getName()));

            return true;
        }

        return false;
    }

    public void cleanupExpiredInvites() {
        long currentTime = System.currentTimeMillis();

        invites.forEach((playerUUID, gangInvites) -> {
            gangInvites.entrySet().removeIf(entry ->
                    currentTime - entry.getValue() > INVITE_EXPIRATION_TIME);

            if (gangInvites.isEmpty()) {
                invites.remove(playerUUID);
            }
        });
    }

    public void removeInvites(UUID playerUUID) {
        invites.remove(playerUUID);
    }

    public boolean hasInvite(UUID playerUUID) {
        return invites.containsKey(playerUUID) && !invites.get(playerUUID).isEmpty();
    }
}