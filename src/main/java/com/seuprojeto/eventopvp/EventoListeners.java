package com.seuprojeto.eventopvp;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

public class EventoListeners implements Listener {

    private final EventoPvP plugin;

    public EventoListeners(EventoPvP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player vitima = (Player) event.getEntity();

        if (!plugin.vivos.contains(vitima.getUniqueId())) return;

        if (vitima.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);

            if (vitima.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                vitima.setHealth(vitima.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            }
            vitima.setFoodLevel(20);
            vitima.setFireTicks(0);

            for (PotionEffect effect : vitima.getActivePotionEffects()) {
                vitima.removePotionEffect(effect.getType());
            }

            // Intercepta quando restam apenas 2 jogadores para definir o segundo colocado
            if (plugin.vivos.size() == 2) {
                plugin.penultimoUUID = vitima.getUniqueId();
            }

            plugin.vivos.remove(vitima.getUniqueId());
            vitima.getInventory().clear();
            vitima.sendMessage(plugin.getMsg("mensagens.eliminado"));

            String cmdSpecRaw = plugin.getConfig().getString("comandos.spec", "");
            if (!cmdSpecRaw.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdSpecRaw.replace("%player%", vitima.getName()));
            }

            plugin.verificarVencedor();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
       
        if (plugin.participantes.contains(p.getUniqueId())) {
            plugin.participantes.remove(p.getUniqueId());
            
            if (plugin.vivos.size() == 2 && plugin.vivos.contains(p.getUniqueId())) {
                plugin.penultimoUUID = p.getUniqueId();
            }
            
            boolean estavaVivo = plugin.vivos.remove(p.getUniqueId());
            p.getInventory().clear();
            plugin.localAnterior.remove(p.getUniqueId());

            if (plugin.iniciado && estavaVivo) {
                plugin.verificarVencedor();
            }
        }
    }
}