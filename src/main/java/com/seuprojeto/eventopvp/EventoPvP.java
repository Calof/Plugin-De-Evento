package com.seuprojeto.eventopvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class EventoPvP extends JavaPlugin {

    public boolean aberto = false;
    public boolean iniciado = false;
    public boolean pvpLiberado = false; 

    public final HashSet<UUID> participantes = new HashSet<>();
    public final HashSet<UUID> vivos = new HashSet<>();
    public final HashSet<UUID> jaEntraram = new HashSet<>();
    public final HashMap<UUID, Location> localAnterior = new HashMap<>();
    
    public UUID penultimoUUID = null;

    private File kitFile, jogadoresFile, spawnsFile;
    private FileConfiguration kitConfig, jogadoresConfig, spawnsConfig;

    private BukkitTask taskCronometroBatalha = null;
    private BukkitTask taskCronometroFim = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        criarKitConfig();
        criarJogadoresConfig();
        criarSpawnsConfig();

        EventoComando cmdExecutor = new EventoComando(this);
        getCommand("evento").setExecutor(cmdExecutor);
        getCommand("evento").setTabCompleter(cmdExecutor);

        getServer().getPluginManager().registerEvents(new EventoListeners(this), this);

        getLogger().info("Plugin EventoPvP Inicializado com Spawns Coordenados!");
    }

    @Override
    public void onDisable() {
        encerrarEvento();
    }

    public Component getMsg(String path) {
        String texto = getConfig().getString(path, "Mensagem ausente: " + path);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(texto);
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
                getJogadoresConfig().set(uuid.toString(), null);
            }
        }
        saveJogadoresConfig();
        
        participantes.clear();
        vivos.clear();
        jaEntraram.clear();
        localAnterior.clear();
        penultimoUUID = null;
        aberto = false;
        iniciado = false;
        pvpLiberado = false;
    }

    public void iniciarContagemPreparacao() {
        cancelarTasks();
        pvpLiberado = false;

        int tempoPrep = getConfig().getInt("preparacao.tempo-preparacao", 5);

        new BukkitRunnable() {
            int restante = tempoPrep;

            @Override
            public void run() {
                if (!iniciado) {
                    this.cancel();
                    return;
                }

                if (restante <= 0) {
                    pvpLiberado = true;
                    Bukkit.broadcast(getMsg("broadcasts.batalha-comecou"));
                    
                    for (UUID uuid : participantes) {
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

                    iniciarAgendadoresBatalha();
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

                for (UUID uuid : participantes) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.showTitle(titleRegressivo);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F, 1.0F);
                    }
                }

                restante--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    public void iniciarAgendadoresBatalha() {
        if (taskCronometroBatalha != null) taskCronometroBatalha.cancel();
        if (!getConfig().getBoolean("cronometro.batalha-com-tempo", true)) return;

        int duracaoMaxima = getConfig().getInt("cronometro.duracao-maxima-batalha", 600);
        int avisoFinal = getConfig().getInt("cronometro.segundos-aviso-final", 10);
        boolean apenasNoFinal = getConfig().getBoolean("cronometro.exibir-apenas-no-final", false);

        taskCronometroBatalha = new BukkitRunnable() {
            int tempoRestante = duracaoMaxima;

            @Override
            public void run() {
                if (!iniciado || vivos.size() <= 1) {
                    this.cancel();
                    return;
                }

                if (tempoRestante <= 0) {
                    Bukkit.broadcast(getMsg("broadcasts.empate-tempo"));
                    iniciado = false;
                    pvpLiberado = false;
                    iniciarAgendadorFimDoEvento();
                    this.cancel();
                    return;
                }

                String cor = determinarCorTempo(tempoRestante, duracaoMaxima, avisoFinal);
                String msgActionBar = cor + "A batalha termina em: " + tempoRestante + "s";
                Component compActionBar = LegacyComponentSerializer.legacyAmpersand().deserialize(msgActionBar);

                boolean deveExibir = !apenasNoFinal || (tempoRestante <= avisoFinal);

                for (UUID uuid : participantes) {
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
        }.runTaskTimer(this, 0L, 20L);
    }

    public void iniciarAgendadorFimDoEvento() {
        if (taskCronometroFim != null) taskCronometroFim.cancel();
        int tempoEspera = getConfig().getInt("tempo-espera-vencedor", 30);
        int avisoFinal = getConfig().getInt("cronometro.segundos-aviso-final", 10);

        taskCronometroFim = new BukkitRunnable() {
            int tempoRestante = tempoEspera;

            @Override
            public void run() {
                if (tempoRestante <= 0) {
                    encerrarEvento();
                    this.cancel();
                    return;
                }

                String cor = determinarCorTempo(tempoRestante, tempoEspera, avisoFinal);
                String msgActionBar = cor + "Teletransporte de volta em: " + tempoRestante + "s";
                Component compActionBar = LegacyComponentSerializer.legacyAmpersand().deserialize(msgActionBar);

                for (UUID uuid : participantes) {
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
        }.runTaskTimer(this, 0L, 20L);
    }

    private String determinarCorTempo(int original, int maximo, int avisoFinal) {
        if (original <= 3) return "&4&l";
        if (original <= avisoFinal) return "&c";
        if (original <= (maximo / 2)) return "&e";
        return "&7";
    }

    private void cancelarTasks() {
        if (taskCronometroBatalha != null) { taskCronometroBatalha.cancel(); taskCronometroBatalha = null; }
        if (taskCronometroFim != null) { taskCronometroFim.cancel(); taskCronometroFim = null; }
    }

    public void verificarVencedor() {
        if (!iniciado) return;

        if (this.vivos.size() == 1) {
            UUID vencedorUUID = this.vivos.iterator().next();
            Player vencedor = Bukkit.getPlayer(vencedorUUID);

            if (vencedor != null) {
                String msgVencedor = getConfig().getString("broadcasts.vencedor", "").replace("%player%", vencedor.getName());
                Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msgVencedor));

                int valorPremio = getConfig().getInt("premio", 50000);
                String cmdPremio = getConfig().getString("comandos.dar-premio", "")
                        .replace("%player%", vencedor.getName())
                        .replace("%premio%", String.valueOf(valorPremio));
                if (!cmdPremio.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdPremio);
                
                vencedor.setHealth(vencedor.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                vencedor.setFoodLevel(20);
                vencedor.setFireTicks(0);
                vencedor.getActivePotionEffects().forEach(effect -> vencedor.removePotionEffect(effect.getType()));

                spawnEfeitosEspeciaisVencedor(vencedor);
            }

            if (penultimoUUID != null) {
                 Player penultimo = Bukkit.getPlayer(penultimoUUID);
                 if (penultimo != null) {
                    String msgPenultimo = getConfig().getString("broadcasts.penultimo", "").replace("%player%", penultimo.getName());
                    Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msgPenultimo));

                    int valorPenultimo = getConfig().getInt("premio-penultimo", 15000);
                    String cmdPenultimo = getConfig().getString("comandos.dar-premio-penultimo", "")
                    .replace("%player%", penultimo.getName())
                    .replace("%premio%", String.valueOf(valorPenultimo));
        
        // CORRIGIDO: Agora usa cmdPenultimo corretamente
        if (!cmdPenultimo.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdPenultimo);
    }
}

            this.iniciado = false; 
            pvpLiberado = false;
            iniciarAgendadorFimDoEvento();
            
        } else if (this.vivos.isEmpty()) {
            encerrarEvento();
        }
    }

    private void spawnEfeitosEspeciaisVencedor(Player p) {
        Location loc = p.getLocation();
        int tempoEsperaSegundos = getConfig().getInt("tempo-espera-vencedor", 30);
        int maxTicksDeDuracao = tempoEsperaSegundos * 20; // Converte o tempo do config.yml em Ticks de servidores (20 tks = 1s)

        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
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
                // Modificado para respeitar dinamicamente o tempo estipulado na config.yml
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
        }.runTaskTimer(this, 0L, 2L);
    }

    private void renderizarPontoAsa(Player p, Location orig, double x, double y, double z, double yawRad, boolean espelhar) {
        if (espelhar) x = -x;
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double rotX = x * cos - z * sin;
        double rotZ = x * sin + z * cos;
        Location locParticula = orig.clone().add(rotX, y, rotZ);
        p.getWorld().spawnParticle(Particle.GLOW, locParticula, 1, 0, 0, 0, 0);
    }

    public void aplicarEfeitosArena(Player p) {
        List<String> listaEfeitos = getConfig().getStringList("efeitos-arena");
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

    private void criarKitConfig() {
        kitFile = new File(getDataFolder(), "kit.yml");
        if (!kitFile.exists()) {
            kitFile.getParentFile().mkdirs();
            try { kitFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        kitConfig = YamlConfiguration.loadConfiguration(kitFile);
    }

    public FileConfiguration getKitConfig() { return kitConfig; }
    public void saveKitConfig() { try { kitConfig.save(kitFile); } catch (IOException e) { e.printStackTrace(); } }

    private void criarJogadoresConfig() {
        jogadoresFile = new File(getDataFolder(), "jogadores.yml");
        if (!jogadoresFile.exists()) {
            jogadoresFile.getParentFile().mkdirs();
            try { jogadoresFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        jogadoresConfig = YamlConfiguration.loadConfiguration(jogadoresFile);
    }

    public FileConfiguration getJogadoresConfig() { return jogadoresConfig; }
    public void saveJogadoresConfig() { try { jogadoresConfig.save(jogadoresFile); } catch (IOException e) { e.printStackTrace(); } }

    private void criarSpawnsConfig() {
        spawnsFile = new File(getDataFolder(), "spawns.yml");
        if (!spawnsFile.exists()) {
            spawnsFile.getParentFile().mkdirs();
            try { spawnsFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);
    }

    public FileConfiguration getSpawnsConfig() { return spawnsConfig; }
    public void saveSpawnsConfig() { try { spawnsConfig.save(spawnsFile); } catch (IOException e) { e.printStackTrace(); } }
}