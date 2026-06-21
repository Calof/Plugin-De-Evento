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

public class FimEventoTask extends BukkitRunnable {

    private final EventoPvP plugin;
    private final EventoManager manager;
    private int tempoRestante;
    private final int tempoEspera;
    private final int avisoFinal;

    public FimEventoTask(EventoPvP plugin, EventoManager manager, int tempoEspera) {
        this.plugin = plugin;
        this.manager = manager;
        this.tempoRestante = tempoEspera;
        this.tempoEspera = tempoEspera;
        this.avisoFinal = plugin.getConfig().getInt("cronometro.segundos-aviso-final", 10);
    }

    @Override
    public void run() {
        if (tempoRestante <= 0) {
            manager.encerrarEvento();
            this.cancel();
            return;
        }

        String cor = manager.determinarCorTempo(tempoRestante, tempoEspera, avisoFinal);
        String msgActionBar = cor + "Teletransporte de volta em: " + tempoRestante + "s";
        Component compActionBar = LegacyComponentSerializer.legacyAmpersand().deserialize(msgActionBar);

        for (UUID uuid : manager.participantes) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendActionBar(compActionBar);
                if (tempoRestante <= avisoFinal) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.5F);
                }
            }
        }
        tempoRestante--;
    }
}