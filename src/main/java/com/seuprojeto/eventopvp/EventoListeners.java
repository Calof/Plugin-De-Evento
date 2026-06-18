package com.seuprojeto.eventopvp;



import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;

import org.bukkit.Location;

import org.bukkit.attribute.Attribute;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;

import org.bukkit.event.Listener;

import org.bukkit.event.entity.EntityDamageEvent;

import org.bukkit.event.player.PlayerJoinEvent;

import org.bukkit.event.player.PlayerQuitEvent;



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



            vitima.setHealth(vitima.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());

            vitima.setFoodLevel(20);

            vitima.setFireTicks(0);

            vitima.getActivePotionEffects().forEach(effect -> vitima.removePotionEffect(effect.getType()));



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

            boolean estavaVivo = plugin.vivos.remove(p.getUniqueId());

           

            p.getInventory().clear();

           

            // Remove da lista de memória do teletransporte imediato,

            // pois agora o onJoin cuidará do teletransporte via arquivo físico.

            plugin.localAnterior.remove(p.getUniqueId());



            if (plugin.iniciado && estavaVivo) {

                plugin.verificarVencedor();

            }

        }

    }



    // NOVA CONDIÇÃO: Envia para o Lobby antes de devolver à localização original

    @EventHandler

    public void onJoin(PlayerJoinEvent event) {

        Player p = event.getPlayer();

        String uuidStr = p.getUniqueId().toString();



        if (plugin.getJogadoresConfig().contains(uuidStr)) {

            Location locOriginal = (Location) plugin.getJogadoresConfig().get(uuidStr);



            if (locOriginal != null) {

                // Segurança interna do plugin

                plugin.participantes.remove(p.getUniqueId());

                plugin.vivos.remove(p.getUniqueId());



                // Limpa o jogador imediatamente no login

                p.getInventory().clear();

                p.setFireTicks(0);

                p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));



                // PASSO 1: Envia o comando de Lobby cadastrado na config.yml

                String cmdLobby = plugin.getConfig().getString("comandos.lobby", "").replace("%player%", p.getName());

                if (!cmdLobby.isEmpty()) {

                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdLobby);

                }



                // PASSO 2: Cria um delay de 1 segundo (20 ticks) para o servidor respirar

                // e então envia o jogador para a sua coordenada de origem antiga com segurança.

                Bukkit.getScheduler().runTaskLater(plugin, () -> {

                    if (p.isOnline()) { // Confirma que o jogador não deslogou de novo nesse 1 segundo

                        p.teleport(locOriginal);

                        p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(

                            "&cVocê havia deslogado no evento! Passamos você pelo Lobby e o devolvemos ao seu local original de forma segura."

                        ));

                    }

                }, 20L);



                // Limpa o arquivo físico após agendar o teletransporte

                plugin.getJogadoresConfig().set(uuidStr, null);

                plugin.saveJogadoresConfig();

               

                if (plugin.iniciado) {

                    plugin.verificarVencedor();

                }

            }

        }

    }

}