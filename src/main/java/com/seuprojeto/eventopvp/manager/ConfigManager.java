package com.seuprojeto.eventopvp.manager;

import com.seuprojeto.eventopvp.EventoPvP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {

    private final EventoPvP plugin;
    private File kitFile, jogadoresFile, spawnsFile, historicoFile;
    private FileConfiguration kitConfig, jogadoresConfig, spawnsConfig, historicoConfig;

    public ConfigManager(EventoPvP plugin) {
        this.plugin = plugin;
        criarConfigs();
    }

    private void criarConfigs() {
        kitFile = iniciarArquivo("kit.yml");
        kitConfig = YamlConfiguration.loadConfiguration(kitFile);

        jogadoresFile = iniciarArquivo("jogadores.yml");
        jogadoresConfig = YamlConfiguration.loadConfiguration(jogadoresFile);

        spawnsFile = iniciarArquivo("spawns.yml");
        spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);

        historicoFile = iniciarArquivo("historico.yml");
        historicoConfig = YamlConfiguration.loadConfiguration(historicoFile);
    }

    private File iniciarArquivo(String nome) {
        File arquivo = new File(plugin.getDataFolder(), nome);
        if (!arquivo.exists()) {
            arquivo.getParentFile().mkdirs();
            try {
                arquivo.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Não foi possível criar o arquivo: " + nome);
                e.printStackTrace();
            }
        }
        return arquivo;
    }

    public FileConfiguration getKitConfig() { return kitConfig; }
    public void saveKitConfig() { try { kitConfig.save(kitFile); } catch (IOException e) { e.printStackTrace(); } }

    public FileConfiguration getJogadoresConfig() { return jogadoresConfig; }
    public void saveJogadoresConfig() { try { jogadoresConfig.save(jogadoresFile); } catch (IOException e) { e.printStackTrace(); } }

    public FileConfiguration getSpawnsConfig() { return spawnsConfig; }
    public void saveSpawnsConfig() { try { spawnsConfig.save(spawnsFile); } catch (IOException e) { e.printStackTrace(); } }

    public FileConfiguration getHistoricoConfig() { return historicoConfig; }
    public void saveHistoricoConfig() { try { historicoConfig.save(historicoFile); } catch (IOException e) { e.printStackTrace(); } }
}