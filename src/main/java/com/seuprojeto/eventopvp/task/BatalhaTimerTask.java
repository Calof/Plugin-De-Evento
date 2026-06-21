package com.seuprojeto.eventopvp.task;

import com.seuprojeto.eventopvp.EventoPvP;
import com.seuprojeto.eventopvp.manager.EventoManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class BatalhaTimerTask extends BukkitRunnable {

    private final EventoPvP plugin;
    private final EventoManager manager;
    private int tempoRestante;
    private final int duracaoMaxima;
    private final int avisoFinal;
    private final boolean apenasNoFinal;

    public BatalhaTimerTask(EventoPvP plugin, EventoManager manager, int duracaoMaxima) {
        this.plugin = plugin;
        this.manager = manager;
        this.tempoRestante = duracaoMaxima;
        this.duracaoMaxima = duracaoMaxima;
        this.avisoFinal = plugin.getConfig().getInt("cronometro.segundos-aviso-final", 10);
        this.apenasNoFinal = plugin.getConfig().getBoolean("cronometro.exibir-apenas-no-final", false);
    }

    @Override
    public void run() {
        if (!manager.iniciado || manager.vivos.size() <= 1) {
            this.cancel();
            return;
        }

        if (tempoRestante <= 0) {
            String msgEmpate = plugin.getConfig().getString("broadcasts.empate-tempo", "")
                    .replace("%quantidade%", String.valueOf(manager.vivos.size()));
            Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msgEmpate));
            
            manager.processarPremioEmpate();
            
            manager.iniciado = false;
            manager.pvpLiberado = false;
            manager.iniciarAgendadorFimDoEvento();
            this.cancel();
            return;
        }

        String cor = manager.determinarCorTempo(tempoRestante, duracaoMaxima, avisoFinal);
        String msgActionBar = cor + "A batalha termina em: " + tempoRestante + "s";
        Component compActionBar = LegacyComponentSerializer.legacyAmpersand().deserialize(msgActionBar);

        boolean deveExibir = !apenasNoFinal || (tempoRestante <= avisoFinal);

        for (UUID uuid : manager.participantes) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                if (deveExibir) p.sendActionBar(compActionBar);
                if (tempoRestante <= avisoFinal) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1.2F);
                }
            }
        }
        tempoRestante--;
    }
}