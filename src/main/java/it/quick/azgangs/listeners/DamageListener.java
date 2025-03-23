package it.quick.azgangs.listeners;

import it.quick.azgangs.AZGangs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class DamageListener implements Listener {

    private final AZGangs plugin;

    public DamageListener(AZGangs plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (plugin.getConfigManager().isPvpBetweenMembersEnabled()) {
            return;
        }

        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        if (plugin.getGangManager().arePlayersInSameGang(victim.getUniqueId(), attacker.getUniqueId())) {
            event.setCancelled(true);

        }
    }
}