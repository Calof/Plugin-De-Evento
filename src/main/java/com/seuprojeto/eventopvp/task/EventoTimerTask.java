package com.seuprojeto.eventopvp.task;

import com.seuprojeto.eventopvp.EventoPvP;
import com.seuprojeto.eventopvp.manager.EventoManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.UUID;

public class EventoTimerTask extends BukkitRunnable {

    public enum FaseEvento {
        PREPARACAO,
        BATALHA,
        FIM
    }

    private final EventoPvP plugin;
    private final EventoManager manager;
    private final FaseEvento faseAtual;
    
    private int tempoRestante;
    private final int tempoMaximoFase;
    private final int avisoFinal;
    private final boolean apenasNoFinal;

    public EventoTimerTask(EventoPvP plugin, EventoManager manager, FaseEvento faseAtual, int tempoInicial) {
        this.plugin = plugin;
        this.manager = manager;
        this.faseAtual = faseAtual;
        this.tempoRestante = tempoInicial;
        this.tempoMaximoFase = tempoInicial;
        
        this.avisoFinal = plugin.getConfig().getInt("cronometro.segundos-aviso-final", 10);
        this.apenasNoFinal = plugin.getConfig().getBoolean("cronometro.exibir-apenas-no-final", false);
    }

    @Override
    public void run() {
        if (faseAtual != FaseEvento.FIM && !manager.iniciado) {
            this.cancel();
            return;
        }

        switch (faseAtual) {
            case PREPARACAO:
                executarPreparacao();
                break;
            case BATALHA:
                executarBatalha();
                break;
            case FIM:
                executarFim();
                break;
        }
    }

    // CORRIGIDO: Escrito corretamente com X
    private void executarPreparacao() {
        if (tempoRestante <= 0) {
            manager.pvpLiberado = true;
            
            // CORRIGIDO: Bukkit.broadcast(Component) é suportado nas versões mais novas da Paper/Purpur.
            // Caso sua versão use a API clássica antiga, enviamos via loop ou pelo Adventure global do servidor.
            Bukkit.getServer().broadcast(plugin.getMsg("broadcasts.batalha-comecou"));
            
            Title titleGo = Title.title(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&a&lVALENDO!"),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&eQue vença o melhor!"),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1000), Duration.ofMillis(200))
            );
            
            for (UUID uuid : manager.participantes) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5F, 1.0F);
                    p.showTitle(titleGo);
                }
            }

            manager.iniciarAgendadoresBatalha();
            this.cancel();
            return;
        }

        String corTitle = "&f";
        if (tempoRestante == 3) corTitle = "&c";
        else if (tempoRestante == 2) corTitle = "&e";
        else if (tempoRestante == 1) corTitle = "&a";

        Component mainTitle = LegacyComponentSerializer.legacyAmpersand().deserialize(corTitle + tempoRestante);
        Component subTitle = LegacyComponentSerializer.legacyAmpersand().deserialize("&7Prepare-se para lutar!");
        Title titleRegressivo = Title.title(
                mainTitle, 
                subTitle, 
                Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(900), Duration.ofMillis(50))
        );

        for (UUID uuid : manager.participantes) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.showTitle(titleRegressivo);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F, 1.0F);
            }
        }

        tempoRestante--;
    }

    // CORRIGIDO: Escrito corretamente com X
    private void executarBatalha() {
        if (manager.vivos.size() <= 1) {
            this.cancel();
            return;
        }

        if (tempoRestante <= 0) {
            String msgEmpate = plugin.getConfig().getString("broadcasts.empate-tempo", "")
                    .replace("%quantidade%", String.valueOf(manager.vivos.size()));
            
            Bukkit.getServer().broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msgEmpate));
            
            manager.processarPremioEmpate();
            
            manager.iniciado = false;
            manager.pvpLiberado = false;
            manager.iniciarAgendadorFimDoEvento();
            this.cancel();
            return;
        }

        String cor = manager.determinarCorTempo(tempoRestante, tempoMaximoFase, avisoFinal);
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

    // CORRIGIDO: Escrito corretamente com X
    private void executarFim() {
        if (tempoRestante <= 0) {
            manager.encerrarEvento();
            this.cancel();
            return;
        }

        String cor = manager.determinarCorTempo(tempoRestante, tempoMaximoFase, avisoFinal);
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