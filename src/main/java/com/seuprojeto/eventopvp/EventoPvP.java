package com.seuprojeto.eventopvp;

// IMPORTANTE: Use exatamente o mesmo início que já funcionava antes!
import com.seuprojeto.eventopvp.manager.ConfigManager;
import com.seuprojeto.eventopvp.manager.EventoManager;
import com.seuprojeto.eventopvp.manager.KitManager; // <--- Verifique se a pasta é esta
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

public class EventoPvP extends JavaPlugin {

    private ConfigManager configManager;
    private EventoManager eventoManager;
    private KitManager kitManager; 

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        this.configManager = new ConfigManager(this);
        this.eventoManager = new EventoManager(this);
        this.kitManager = new KitManager(this); // <--- Inicialização

        EventoComando cmdExecutor = new EventoComando(this);
        getCommand("evento").setExecutor(cmdExecutor);
        getCommand("evento").setTabCompleter(cmdExecutor);

        getServer().getPluginManager().registerEvents(new EventoListeners(this), this);

        getLogger().info("Plugin EventoPvP Inicializado com Sucesso!");
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

    // O comando vai precisar deste método para encontrar o KitManager
    public KitManager getKitManager() {
        return kitManager;
    }
}