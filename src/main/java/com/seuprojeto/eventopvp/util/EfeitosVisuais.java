package com.seuprojeto.eventopvp.util;

import com.seuprojeto.eventopvp.EventoPvP;
import com.seuprojeto.eventopvp.manager.EventoManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class EfeitosVisuais {

    public static void spawnEfeitosEspeciaisVencedor(EventoPvP plugin, EventoManager manager, Player p) {
        Location loc = p.getLocation();
        int tempoEsperaSegundos = plugin.getConfig().getInt("tempo-espera-vencedor", 30);
        int maxTicksDeDuracao = tempoEsperaSegundos * 20;

        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    Firework fw = (Firework) p.getLocation().getWorld().spawnEntity(p.getLocation(), EntityType.FIREWORK_ROCKET);
                    FireworkMeta meta = fw.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .withColor(Color.ORANGE, Color.YELLOW, Color.GREEN)
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .flicker(true)
                            .trail(true)
                            .build());
                    meta.setPower(1);
                    fw.setFireworkMeta(meta);
                }
            }, i * 15L);
        }

        new BukkitRunnable() {
            int ticksExecutados = 0;
            double girarCoroa = 0;

            @Override
            public void run() {
                if (!p.isOnline() || ticksExecutados > maxTicksDeDuracao || !p.getWorld().equals(loc.getWorld())) {
                    this.cancel();
                    return;
                }

                Location pLoc = p.getLocation();
                double raioCoroa = 0.35;
                girarCoroa += Math.PI / 16;
                for (int i = 0; i < 6; i++) {
                    double angulo = (i * Math.PI / 3) + girarCoroa;
                    double x = raioCoroa * Math.cos(angulo);
                    double z = raioCoroa * Math.sin(angulo);
                    Location pontoCoroa = pLoc.clone().add(x, 2.2, z);
                    p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pontoCoroa, 1, 0, 0, 0, 0);
                    if (i % 2 == 0) {
                        p.getWorld().spawnParticle(Particle.WAX_OFF, pontoCoroa.add(0, 0.15, 0), 1, 0, 0, 0, 0);
                    }
                }

                double[][] formatoAsa = {
                    {0.2, 1.6, -0.2}, {0.4, 1.7, -0.25}, {0.6, 1.8, -0.3}, {0.8, 1.9, -0.35}, {1.0, 1.8, -0.4},
                    {1.1, 1.6, -0.42}, {0.9, 1.4, -0.4}, {0.7, 1.2, -0.35}, {0.5, 1.0, -0.3}, {0.3, 0.8, -0.25},
                    {0.4, 1.4, -0.3}, {0.6, 1.5, -0.35}, {0.8, 1.6, -0.4}, {0.9, 1.3, -0.38}, {0.7, 1.1, -0.32}
                };

                double direcaoRad = Math.toRadians(pLoc.getYaw());
                for (double[] ponto : formatoAsa) {
                    renderizarPontoAsa(p, pLoc, ponto[0], ponto[1], ponto[2], direcaoRad, false);
                    renderizarPontoAsa(p, pLoc, ponto[0], ponto[1], ponto[2], direcaoRad, true);
                }
                ticksExecutados += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private static void renderizarPontoAsa(Player p, Location orig, double x, double y, double z, double yawRad, boolean espelhar) {
        if (espelhar) x = -x;
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double rotX = x * cos - z * sin;
        double rotZ = x * sin + z * cos;
        Location locParticula = orig.clone().add(rotX, y, rotZ);
        p.getWorld().spawnParticle(Particle.GLOW, locParticula, 1, 0, 0, 0, 0);
    }
}