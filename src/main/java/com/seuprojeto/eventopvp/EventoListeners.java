package com.seuprojeto.eventopvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

public class EventoListeners implements Listener {

    private final EventoPvP plugin;

    public EventoListeners(EventoPvP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player vitima = (Player) event.getEntity();

        if (!plugin.vivos.contains(vitima.getUniqueId())) return;

        if (plugin.iniciado && !plugin.pvpLiberado) {
            event.setCancelled(true);
            return;
        }

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
    public void onPvPHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player vitima = (Player) event.getEntity();

        if (!plugin.vivos.contains(vitima.getUniqueId())) return;

        if (plugin.iniciado && !plugin.pvpLiberado) {
            event.setCancelled(true);
            return;
        }

        Player atacante = null;

        if (event.getDamager() instanceof Player) {
            atacante = (Player) event.getDamager();
        } else if (event.getDamager() instanceof AbstractArrow) {
            AbstractArrow flecha = (AbstractArrow) event.getDamager();
            if (flecha.getShooter() instanceof Player) {
                atacante = (Player) flecha.getShooter();
            }
        }

        if (atacante == null) return;

        Location locSangue = vitima.getLocation().add(0, 1.0, 0);

        boolean isCritico = false;
        if (event.getDamager() instanceof Player) {
            isCritico = atacante.getFallDistance() > 0.0F 
                    && !atacante.isOnGround() 
                    && !atacante.isClimbing() 
                    && !atacante.isInWater() 
                    && !atacante.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS) 
                    && atacante.getVehicle() == null;
        }

        if (isCritico) {
            Particle.DustOptions poeiraSangueEspessa = new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 0, 0), 1.5F);
            vitima.getWorld().spawnParticle(Particle.DUST, locSangue, 35, 0.2, 0.4, 0.2, 0.1, poeiraSangueEspessa);
            vitima.getWorld().spawnParticle(Particle.BLOCK, locSangue, 15, 0.1, 0.3, 0.1, 0.1, Material.REDSTONE_BLOCK.createBlockData());
            vitima.getWorld().playSound(locSangue, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.8F);
            vitima.getWorld().playSound(locSangue, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.4F, 1.5F);
        } else {
            Particle.DustOptions poeiraSangueNormal = new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 0), 1.0F);
            vitima.getWorld().spawnParticle(Particle.DUST, locSangue, 12, 0.1, 0.3, 0.1, 0.05, poeiraSangueNormal);
            vitima.getWorld().playSound(locSangue, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8F, 1.1F);
        }
    }

    @EventHandler
    public void onPlayerMoveBeforeStart(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (plugin.iniciado && !plugin.pvpLiberado && plugin.vivos.contains(p.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                Location novoTo = from.clone();
                novoTo.setYaw(to.getYaw());
                novoTo.setPitch(to.getPitch());
                event.setTo(novoTo);
            }
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