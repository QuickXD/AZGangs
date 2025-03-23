package it.quick.azgangs.models;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Gang {

    private final int id;
    private String name;
    private final UUID ownerUUID;
    private List<UUID> members;

    public Gang(int id, String name, UUID ownerUUID) {
        this.id = id;
        this.name = name;
        this.ownerUUID = ownerUUID;
        this.members = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        OfflinePlayer player = Bukkit.getOfflinePlayer(ownerUUID);
        return player.getName() != null ? player.getName() : "Unknown";
    }

    public List<UUID> getMembers() {
        return members;
    }

    public void setMembers(List<UUID> members) {
        this.members = members;
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isMember(UUID playerUUID) {
        return members.contains(playerUUID);
    }

    public boolean isOwner(UUID playerUUID) {
        return ownerUUID.equals(playerUUID);
    }

    public void addMember(UUID playerUUID) {
        if (!members.contains(playerUUID)) {
            members.add(playerUUID);
        }
    }

    public List<String> getMemberNames() {
        List<String> names = new ArrayList<>();
        for (UUID uuid : members) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            names.add(player.getName() != null ? player.getName() : uuid.toString().substring(0, 8));
        }
        return names;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Gang gang = (Gang) obj;
        return id == gang.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "Gang{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", ownerUUID=" + ownerUUID +
                ", memberCount=" + getMemberCount() +
                '}';
    }
}