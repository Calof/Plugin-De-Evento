package com.seuprojeto.eventopvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class EventoPvP extends JavaPlugin {

    public boolean aberto = false;
    public boolean iniciado = false;

    public final HashSet<UUID> participantes = new HashSet<>();
    public final HashSet<UUID> vivos = new HashSet<>();
    public final HashSet<UUID> jaEntraram = new HashSet<>();
    public final HashMap<UUID, Location> localAnterior = new HashMap<>();
    
    public UUID penultimoUUID = null;

    private File kitFile, jogadoresFile;
    private FileConfiguration kitConfig, jogadoresConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        criarKitConfig();
        criarJogadoresConfig();

        EventoComando cmdExecutor = new EventoComando(this);
        getCommand("evento").setExecutor(cmdExecutor);
        getCommand("evento").setTabCompleter(cmdExecutor);

        getServer().getPluginManager().registerEvents(new EventoListeners(this), this);

        getLogger().info("Plugin EventoPvP Atualizado com Auto-completar!");
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

                spawnFogosVencedor(vencedor);
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
                    if (!cmdPenultimo.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdPenultimo);
                }
            }

            this.iniciado = false; 
            int segundosEspera = getConfig().getInt("tempo-espera-vencedor", 5);
            Bukkit.getScheduler().runTaskLater(this, this::encerrarEvento, segundosEspera * 20L);
            
        } else if (this.vivos.isEmpty()) {
            encerrarEvento();
        }
    }

    private void spawnFogosVencedor(Player p) {
        Location loc = p.getLocation();
        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline()) {
                    Firework fw = (Firework) loc.getWorld().spawnEntity(p.getLocation(), EntityType.FIREWORK_ROCKET);
                    FireworkMeta meta = fw.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder()
                            .withColor(Color.ORANGE, Color.YELLOW, Color.GREEN) // Corrigido de Color.GOLD para Color.ORANGE
                            .with(FireworkEffect.Type.BALL_LARGE)
                            .flicker(true)
                            .trail(true)
                            .build());
                    meta.setPower(1);
                    fw.setFireworkMeta(meta);
                }
            }, i * 15L);
        }
    }

    public void aplicarEfeitosArena(Player p) {
        List<String> listaEfeitos = getConfig().getStringList("efeitos-arena");
        for (String staticLine : listaEfeitos) {
            try {
                String[] partes = staticLine.split(":");
                PotionEffectType tipo = PotionEffectType.getByName(partes[0].toUpperCase());
                int amplifier = partes.length > 1 ? Integer.parseInt(partes[1]) : 0;
                
                if (tipo != null) {
                    // Corrigido parâmetros do construtor: duration, amplifier, ambient, particles, icon
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
}