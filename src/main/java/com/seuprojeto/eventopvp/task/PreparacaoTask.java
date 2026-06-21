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

public class PreparacaoTask extends BukkitRunnable {

    private final EventoPvP plugin;
    private final EventoManager manager;
    private int restante;

    public PreparacaoTask(EventoPvP plugin, EventoManager manager, int tempoPreparacao) {
        this.plugin = plugin;
        this.manager = manager;
        this.restante = tempoPreparacao;
    }

    @Override
    public void run() {
        if (!manager.iniciado) {
            this.cancel();
            return;
        }

        if (restante <= 0) {
            manager.pvpLiberado = true;
            Bukkit.broadcast(plugin.getMsg("broadcasts.batalha-comecou"));
            
            for (UUID uuid : manager.participantes) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5F, 1.0F);
                    Title titleGo = Title.title(
                            LegacyComponentSerializer.legacyAmpersand().deserialize("&a&lVALENDO!"),
                            LegacyComponentSerializer.legacyAmpersand().deserialize("&eQue vença o melhor!"),
                            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1000), Duration.ofMillis(200))
                    );
                    p.showTitle(titleGo);
                }
            }

            manager.iniciarAgendadoresBatalha();
            this.cancel();
            return;
        }

        String corTitle = "&f";
        if (restante == 3) corTitle = "&c";
        else if (restante == 2) corTitle = "&e";
        else if (restante == 1) corTitle = "&a";

        Component mainTitle = LegacyComponentSerializer.legacyAmpersand().deserialize(corTitle + restante);
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

        restante--;
    }
}