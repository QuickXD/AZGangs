package it.quick.azgangs.commands;

import it.quick.azgangs.AZGangs;
import it.quick.azgangs.models.Gang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GangCommand implements CommandExecutor, TabCompleter {

    private final AZGangs plugin;
    private final Set<UUID> gangChatToggle = new HashSet<>();
    private final List<String> subCommands = Arrays.asList(
            "create", "disband", "rename", "invite", "join", "leave", "info", "list", "help", "chat"
    );

    public GangCommand(AZGangs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Questo comando lo possono usare solo i players, non la console.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                handleCreate(player, args);
                break;
            case "disband":
                handleDisband(player);
                break;
            case "rename":
                handleRename(player, args);
                break;
            case "invite":
                handleInvite(player, args);
                break;
            case "join":
                handleJoin(player);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "info":
                handleInfo(player, args);
                break;
            case "chat":
                handleGangChat(player);
                break;
            case "list":
                handleList(player);
                break;
            case "help":
                showHelp(player);
                break;
            case "off":
                AZGangs.getInstance().disableGangMessage(player);
                player.sendMessage("§e[AZGangs] § Ok. Messaggio disabilitato.");
                break;
            default:
                showHelp(player);
                break;
        }

        return true;
    }


    private void handleCreate(Player player, String[] args) {
        if (!player.hasPermission(plugin.getConfigManager().getPermission("create"))) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("missing-gang-name"));
            return;
        }

        String gangName = args[1];
        plugin.getGangManager().createGang(player, gangName);
    }

    private void handleDisband(Player player) {
        if (!player.hasPermission(plugin.getConfigManager().getPermission("disband"))) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        plugin.getGangManager().disbandGang(player);
    }

    private void handleRename(Player player, String[] args) {
        if (!player.hasPermission(plugin.getConfigManager().getPermission("rename"))) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("missing-gang-name"));
            return;
        }

        String newName = args[1];
        plugin.getGangManager().renameGang(player, newName);
    }

    private void handleInvite(Player player, String[] args) {
        if (!player.hasPermission(plugin.getConfigManager().getPermission("invite"))) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("missing-player-name"));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
            return;
        }

        plugin.getInviteManager().sendInvite(player, target);
    }

    private void handleGangChat(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!plugin.getGangManager().isPlayerInGang(playerUUID)) {
            player.sendMessage(plugin.getConfigManager().getMessage("not-in-gang"));
            return;
        }

        if (gangChatToggle.contains(playerUUID)) {
            gangChatToggle.remove(playerUUID);
            player.sendMessage(plugin.getConfigManager().getMessage("gang-chat-disabled"));
        } else {
            gangChatToggle.add(playerUUID);
            player.sendMessage(plugin.getConfigManager().getMessage("gang-chat-enabled"));
        }
    }

    public boolean handleGangChatMessage(Player player, String message) {
        UUID playerUUID = player.getUniqueId();

        if (!gangChatToggle.contains(playerUUID)) {
            return false;
        }

        Gang playerGang = plugin.getGangManager().getPlayerGang(playerUUID);

        if (playerGang == null) {
            gangChatToggle.remove(playerUUID);
            return false;
        }

        for (UUID memberUUID : playerGang.getMembers()) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && member.isOnline()) {
                member.sendMessage(plugin.getConfigManager().getMessage("gang-chat-format")
                        .replace("%playerName%", player.getName())
                        .replace("%message%", message));
            }
        }

        return true;
    }

    private void handleJoin(Player player) {
        if (!player.hasPermission(plugin.getConfigManager().getPermission("join"))) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        plugin.getInviteManager().acceptInvite(player);
    }

    public boolean isGangChatEnabled(UUID playerUUID) {
        return gangChatToggle.contains(playerUUID);
    }

    private void handleLeave(Player player) {
        if (!player.hasPermission(plugin.getConfigManager().getPermission("leave"))) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        plugin.getGangManager().leaveGang(player);
    }

    private void handleInfo(Player player, String[] args) {
        if (!player.hasPermission(plugin.getConfigManager().getPermission("info"))) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        Gang gang;

        if (args.length > 1) {
            String gangName = args[1];
            gang = plugin.getGangManager().getGangByName(gangName);

            if (gang == null) {
                player.sendMessage(plugin.getConfigManager().getMessage("gang-not-found"));
                return;
            }
        } else {
            gang = plugin.getGangManager().getPlayerGang(player.getUniqueId());

            if (gang == null) {
                player.sendMessage(plugin.getConfigManager().getMessage("not-in-gang"));
                return;
            }
        }

        player.sendMessage(plugin.getConfigManager().getMessageNoPrefix("gang-info-header")
                .replace("%gangName%", gang.getName()));
        player.sendMessage(plugin.getConfigManager().getMessageNoPrefix("gang-info-owner")
                .replace("%owner%", gang.getOwnerName()));
        player.sendMessage(plugin.getConfigManager().getMessageNoPrefix("gang-info-members")
                .replace("%members%", String.join(", ", gang.getMemberNames())));
        player.sendMessage(plugin.getConfigManager().getMessageNoPrefix("gang-info-footer"));
    }

    private void handleList(Player player) {
        if (!player.hasPermission(plugin.getConfigManager().getPermission("list"))) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }

        List<Gang> gangs = plugin.getGangManager().getAllGangs();

        if (gangs.isEmpty()) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-gangs"));
            return;
        }

        player.sendMessage(plugin.getConfigManager().getMessageNoPrefix("gang-list-header"));

        for (Gang gang : gangs) {
            player.sendMessage(plugin.getConfigManager().getMessageNoPrefix("gang-list-entry")
                    .replace("%gangName%", gang.getName())
                    .replace("%owner%", gang.getOwnerName())
                    .replace("%memberCount%", String.valueOf(gang.getMemberCount())));
        }

        player.sendMessage(plugin.getConfigManager().getMessageNoPrefix("gang-list-footer"));
    }

    private void showHelp(Player player) {
        player.sendMessage(plugin.getConfigManager().getMessageNoPrefix("help-header"));

        player.sendMessage(plugin.getConfigManager().getHelpMessage("create"));
        player.sendMessage(plugin.getConfigManager().getHelpMessage("disband"));
        player.sendMessage(plugin.getConfigManager().getHelpMessage("rename"));
        player.sendMessage(plugin.getConfigManager().getHelpMessage("invite"));
        player.sendMessage(plugin.getConfigManager().getHelpMessage("join"));
        player.sendMessage(plugin.getConfigManager().getHelpMessage("leave"));
        player.sendMessage(plugin.getConfigManager().getHelpMessage("info"));
        player.sendMessage(plugin.getConfigManager().getHelpMessage("list"));
        player.sendMessage(plugin.getConfigManager().getHelpMessage("help"));

        player.sendMessage(plugin.getConfigManager().getMessageNoPrefix("help-footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            switch (subCommand) {
                case "invite":
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .filter(p -> p.getName().toLowerCase().startsWith(input))
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                    break;
                case "info":
                    completions.addAll(plugin.getGangManager().getAllGangs().stream()
                            .map(Gang::getName)
                            .filter(name -> name.toLowerCase().startsWith(input))
                            .collect(Collectors.toList()));
                    break;
            }
        }

        return completions;
    }
}