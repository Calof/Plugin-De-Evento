package com.seuprojeto.eventopvp;

import com.seuprojeto.eventopvp.manager.EventoManager;
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
import org.bukkit.event.entity.EntityResurrectEvent; // Importado
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class EventoListeners implements Listener {

    private final EventoPvP plugin;

    public EventoListeners(EventoPvP plugin) {
        this.plugin = plugin;
    }

    // NOVO: Evento para garantir que o Totem funcione e não cause erros no evento de dano
    @EventHandler
    public void onTotemPop(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        EventoManager manager = plugin.getEventoManager();

        // Se o jogador está no evento e o totem foi acionado, deixa o Minecraft processar o totem normalmente
        if (manager.vivos.contains(player.getUniqueId())) {
            // Se o evento for cancelado por algum outro motivo, forçamos a ativação se ele tiver o totem
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player vitima = (Player) event.getEntity();
        EventoManager manager = plugin.getEventoManager();

        if (!manager.vivos.contains(vitima.getUniqueId())) return;

        if (!manager.pvpLiberado) {
            event.setCancelled(true);
            return;
        }

        if (vitima.getHealth() - event.getFinalDamage() <= 0) {
            
            // CORREÇÃO: Se o jogador estiver segurando um Totem da Imortalidade, NÃO elimina ele.
            // O EntityResurrectEvent vai cuidar de dar os efeitos do totem e revivê-lo.
            if (vitima.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING || 
                vitima.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
                return; // Ignora o resto do código de morte e deixa o Minecraft agir
            }

            event.setCancelled(true);

            Player killer = null;
            if (vitima.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent edbe = (EntityDamageByEntityEvent) vitima.getLastDamageCause();
                if (edbe.getDamager() instanceof Player) {
                    killer = (Player) edbe.getDamager();
                } else if (edbe.getDamager() instanceof AbstractArrow) {
                    AbstractArrow arrow = (AbstractArrow) edbe.getDamager();
                    if (arrow.getShooter() instanceof Player) {
                        killer = (Player) arrow.getShooter();
                    }
                }
            }

            if (killer != null && manager.vivos.contains(killer.getUniqueId())) {
                String msgAbate = plugin.getConfig().getString("broadcasts.abate-simples", "")
                        .replace("%vitima%", vitima.getName())
                        .replace("%atacante%", killer.getName());
                Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msgAbate));

                int streakAtual = manager.killstreak.getOrDefault(killer.getUniqueId(), 0) + 1;
                manager.killstreak.put(killer.getUniqueId(), streakAtual);

                if (streakAtual % 3 == 0) {
                    String msgStreak = plugin.getConfig().getString("broadcasts.killstreak", "")
                            .replace("%player%", killer.getName())
                            .replace("%kills%", String.valueOf(streakAtual));
                    Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msgStreak));
                }
            }

            manager.killstreak.remove(vitima.getUniqueId());

            if (vitima.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                vitima.setHealth(vitima.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            }
            vitima.setFoodLevel(20);
            vitima.setFireTicks(0);

            for (PotionEffect effect : vitima.getActivePotionEffects()) {
                vitima.removePotionEffect(effect.getType());
            }

            if (manager.vivos.size() == 2) {
                manager.penultimoUUID = vitima.getUniqueId();
            }

            manager.vivos.remove(vitima.getUniqueId());
            vitima.getInventory().clear();
            vitima.sendMessage(plugin.getMsg("mensagens.eliminado"));

            String cmdSpecRaw = plugin.getConfig().getString("comandos.spec", "");
            if (!cmdSpecRaw.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdSpecRaw.replace("%player%", vitima.getName()));
            }

            manager.verificarVencedor();
        }
    }

    @EventHandler
    public void onPvPHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player vitima = (Player) event.getEntity();
        EventoManager manager = plugin.getEventoManager();

        if (!manager.vivos.contains(vitima.getUniqueId())) return;

        if (!manager.pvpLiberado) {
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
        EventoManager manager = plugin.getEventoManager();
        if (manager.iniciado && !manager.pvpLiberado && manager.vivos.contains(p.getUniqueId())) {
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
        EventoManager manager = plugin.getEventoManager();
       
        if (manager.participantes.contains(p.getUniqueId())) {
            manager.participantes.remove(p.getUniqueId());
            manager.killstreak.remove(p.getUniqueId());
            
            if (manager.vivos.size() == 2 && manager.vivos.contains(p.getUniqueId())) {
                manager.penultimoUUID = p.getUniqueId();
            }
            
            boolean estavaVivo = manager.vivos.remove(p.getUniqueId());
            p.getInventory().clear();
            manager.localAnterior.remove(p.getUniqueId());

            if (manager.iniciado && estavaVivo) {
                manager.verificarVencedor();
            }
        }
    }
}