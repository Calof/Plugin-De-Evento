package com.seuprojeto.eventopvp;

import com.seuprojeto.eventopvp.manager.ConfigManager;
import com.seuprojeto.eventopvp.manager.EventoManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

public class EventoPvP extends JavaPlugin {

    private ConfigManager configManager;
    private EventoManager eventoManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        this.configManager = new ConfigManager(this);
        this.eventoManager = new EventoManager(this);

        EventoComando cmdExecutor = new EventoComando(this);
        getCommand("evento").setExecutor(cmdExecutor);
        getCommand("evento").setTabCompleter(cmdExecutor);

        getServer().getPluginManager().registerEvents(new EventoListeners(this), this);

        getLogger().info("Plugin EventoPvP Inicializado e Modularizado com Sucesso!");
    }

    @Override
    public void onDisable() {
        if (eventoManager != null) {
            eventoManager.encerrarEvento();
        }
    }

    public Component getMsg(String path) {
        String texto = getConfig().getString(path, "Mensagem ausente: " + path);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(texto);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EventoManager getEventoManager() {
        return eventoManager;
    }
}