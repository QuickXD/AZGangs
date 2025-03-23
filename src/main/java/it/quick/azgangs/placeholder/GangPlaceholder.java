package it.quick.azgangs.placeholder;

import it.quick.azgangs.AZGangs;
import it.quick.azgangs.models.Gang;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class GangPlaceholder extends PlaceholderExpansion {

    private final AZGangs plugin;

    public GangPlaceholder(AZGangs plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "azgangs";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        if (identifier.equals("gangname")) {
            Gang gang = plugin.getGangManager().getPlayerGang(player.getUniqueId());
            return gang != null ? gang.getName() : "";
        }

        if (identifier.equals("owner")) {
            Gang gang = plugin.getGangManager().getPlayerGang(player.getUniqueId());
            return gang != null ? gang.getOwnerName() : "";
        }

        if (identifier.equals("membercount")) {
            Gang gang = plugin.getGangManager().getPlayerGang(player.getUniqueId());
            return gang != null ? String.valueOf(gang.getMemberCount()) : "0";
        }

        if (identifier.equals("isingang")) {
            return String.valueOf(plugin.getGangManager().isPlayerInGang(player.getUniqueId()));
        }

        if (identifier.equals("isowner")) {
            Gang gang = plugin.getGangManager().getPlayerGang(player.getUniqueId());
            return gang != null && gang.isOwner(player.getUniqueId()) ? "true" : "false";
        }

        return null;
    }
}