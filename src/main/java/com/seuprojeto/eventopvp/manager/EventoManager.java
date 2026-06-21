package com.seuprojeto.eventopvp.manager;

import com.seuprojeto.eventopvp.EventoPvP;
import com.seuprojeto.eventopvp.task.BatalhaTimerTask;
import com.seuprojeto.eventopvp.task.FimEventoTask;
import com.seuprojeto.eventopvp.task.PreparacaoTask;
import com.seuprojeto.eventopvp.util.EfeitosVisuais;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class EventoManager {

    private final EventoPvP plugin;

    public boolean aberto = false;
    public boolean iniciado = false;
    public boolean pvpLiberado = false; 

    public final HashSet<UUID> participantes = new HashSet<>();
    public final HashSet<UUID> vivos = new HashSet<>();
    public final HashSet<UUID> jaEntraram = new HashSet<>();
    public final HashMap<UUID, Location> localAnterior = new HashMap<>();
    public final HashMap<UUID, Integer> killstreak = new HashMap<>();
    
    public UUID penultimoUUID = null;

    private BukkitTask taskCronometroBatalha = null;
    private BukkitTask taskCronometroFim = null;

    public EventoManager(EventoPvP plugin) {
        this.plugin = plugin;
    }

    public void encerrarEvento() {
        cancelarTasks();
        for (UUID uuid : participantes) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.getInventory().clear();
                p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
                Location loc = localAnterior.get(uuid);
                if (loc != null) p.teleport(loc);
                plugin.getConfigManager().getJogadoresConfig().set(uuid.toString(), null);
            }
        }
        plugin.getConfigManager().saveJogadoresConfig();
        
        participantes.clear();
        vivos.clear();
        jaEntraram.clear();
        localAnterior.clear();
        killstreak.clear();
        penultimoUUID = null;
        aberto = false;
        iniciado = false;
        pvpLiberado = false;
    }

    public void iniciarContagemPreparacao() {
        cancelarTasks();
        pvpLiberado = false;
        int tempoPrep = plugin.getConfig().getInt("preparacao.tempo-preparacao", 5);
        new PreparacaoTask(plugin, this, tempoPrep).runTaskTimer(plugin, 0L, 20L);
    }

    public void iniciarAgendadoresBatalha() {
        if (taskCronometroBatalha != null) taskCronometroBatalha.cancel();
        if (!plugin.getConfig().getBoolean("cronometro.batalha-com-tempo", true)) return;

        int duracaoMaxima = plugin.getConfig().getInt("cronometro.duracao-maxima-batalha", 600);
        taskCronometroBatalha = new BatalhaTimerTask(plugin, this, duracaoMaxima).runTaskTimer(plugin, 0L, 20L);
    }

    public void processarPremioEmpate() {
        if (vivos.isEmpty()) return;
        
        double premioTotal = plugin.getConfig().getDouble("premio", 15000.0);
        double parteDoPremio = premioTotal / vivos.size();
        
        List<String> nomesVencedores = vivos.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .collect(Collectors.toList());

        for (String nome : nomesVencedores) {
            String cmd = "eco give " + nome + " " + parteDoPremio;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        String msgRec = plugin.getConfig().getString("broadcasts.empate-recompensa", "")
                .replace("%quantia%", String.format("%.2f", parteDoPremio));
        Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msgRec));
        
        salvarLogNoHistorico(nomesVencedores, "EMPATE");
    }

    public void iniciarAgendadorFimDoEvento() {
        if (taskCronometroFim != null) taskCronometroFim.cancel();
        int tempoEspera = plugin.getConfig().getInt("tempo-espera-vencedor", 30);
        taskCronometroFim = new FimEventoTask(plugin, this, tempoEspera).runTaskTimer(plugin, 0L, 20L);
    }

    public String determinarCorTempo(int original, int maximo, int avisoFinal) {
        if (original <= 3) return "&4&l";
        if (original <= avisoFinal) return "&c";
        if (original <= (maximo / 2)) return "&e";
        return "&7";
    }

    public void cancelarTasks() {
        if (taskCronometroBatalha != null) { taskCronmetoBatalhaCancel(); }
        if (taskCronometroFim != null) { taskCronmetoFimCancel(); }
    }

    private void taskCronmetoBatalhaCancel() {
        taskCronometroBatalha.cancel();
        taskCronometroBatalha = null;
    }

    private void taskCronmetoFimCancel() {
        taskCronometroFim.cancel();
        taskCronometroFim = null;
    }

    public void verificarVencedor() {
        if (!iniciado) return;

        if (this.vivos.size() == 1) {
            UUID vencedorUUID = this.vivos.iterator().next();
            Player vencedor = Bukkit.getPlayer(vencedorUUID);

            if (vencedor != null) {
                String msgVencedor = plugin.getConfig().getString("broadcasts.vencedor", "").replace("%player%", vencedor.getName());
                Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msgVencedor));

                double valorPremio = plugin.getConfig().getDouble("premio", 15000.0);
                String cmdPremio = plugin.getConfig().getString("comandos.dar-premio", "")
                        .replace("%player%", vencedor.getName())
                        .replace("%premio%", String.valueOf(valorPremio));
                if (!cmdPremio.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdPremio);
                
                vencedor.setHealth(vencedor.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                vencedor.setFoodLevel(20);
                vencedor.setFireTicks(0);
                vencedor.getActivePotionEffects().forEach(effect -> vencedor.removePotionEffect(effect.getType()));

                EfeitosVisuais.spawnEfeitosEspeciaisVencedor(plugin, this, vencedor);
                
                salvarLogNoHistorico(List.of(vencedor.getName()), "VITORIA");
            }

            if (penultimoUUID != null) {
                 Player penultimo = Bukkit.getPlayer(penultimoUUID);
                 if (penultimo != null) {
                    String msgPenultimo = plugin.getConfig().getString("broadcasts.penultimo", "").replace("%player%", penultimo.getName());
                    Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msgPenultimo));

                    double valorPenultimo = plugin.getConfig().getDouble("premio-penultimo", 1000.0);
                    String cmdPenultimo = plugin.getConfig().getString("comandos.dar-premio-penultimo", "")
                            .replace("%player%", penultimo.getName())
                            .replace("%premio%", String.valueOf(valorPenultimo));
        
                    if (!cmdPenultimo.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdPenultimo);
                }
            }

            this.iniciado = false; 
            pvpLiberado = false;
            iniciarAgendadorFimDoEvento();
            
        } else if (this.vivos.isEmpty()) {
            salvarLogNoHistorico(List.of("Nenhum"), "SEM_VENCEDORES");
            encerrarEvento();
        }
    }

    public void salvarLogNoHistorico(List<String> vencedores, String status) {
        String idEdicao = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String path = "edicoes." + idEdicao;

        List<String> todosParticipantes = jaEntraram.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .collect(Collectors.toList());

        plugin.getConfigManager().getHistoricoConfig().set(path + ".status", status);
        plugin.getConfigManager().getHistoricoConfig().set(path + ".vencedores", vencedores);
        plugin.getConfigManager().getHistoricoConfig().set(path + ".participantes", todosParticipantes);
        plugin.getConfigManager().saveHistoricoConfig();
    }

    public void aplicarEfeitosArena(Player p) {
        List<String> listaEfeitos = plugin.getConfig().getStringList("efeitos-arena");
        for (String staticLine : listaEfeitos) {
            try {
                String[] partes = staticLine.split(":");
                PotionEffectType tipo = PotionEffectType.getByName(partes[0].toUpperCase());
                int amplifier = partes.length > 1 ? Integer.parseInt(partes[1]) : 0;
                if (tipo != null) {
                    p.addPotionEffect(new PotionEffect(tipo, 72000, amplifier, false, true, true));
                }
            } catch (Exception ignored) {}
        }
    }
}