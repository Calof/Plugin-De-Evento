package com.seuprojeto.eventopvp.manager;

import com.seuprojeto.eventopvp.EventoPvP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KitManager {

    private final EventoPvP plugin;
    private File kitFile;
    private FileConfiguration kitConfig;

    public KitManager(EventoPvP plugin) {
        this.plugin = plugin;
        carregarConfig();
    }

    public void carregarConfig() {
        kitFile = new File(plugin.getDataFolder(), "kit.yml");
        if (!kitFile.exists()) {
            kitFile.getParentFile().mkdirs();
            try {
                kitFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Não foi possível criar o arquivo kit.yml!");
                e.printStackTrace();
            }
        }
        kitConfig = YamlConfiguration.loadConfiguration(kitFile);
    }

    public FileConfiguration getConfig() {
        return kitConfig;
    }

    public void salvarConfig() {
        try {
            kitConfig.save(kitFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar o arquivo kit.yml!");
            e.printStackTrace();
        }
    }

    // Centraliza a lógica de DEFINIR o kit sem duplicar itens
    public void definirKitDoJogador(Player p) {
        // getStorageContents() pega APENAS os 36 slots principais (sem armadura e sem off-hand)
        kitConfig.set("inventario", Arrays.asList(p.getInventory().getStorageContents()));
        kitConfig.set("armadura", Arrays.asList(p.getInventory().getArmorContents()));
        // Salva separadamente o item da segunda mão (off-hand)
        kitConfig.set("offhand", p.getInventory().getItemInOffHand());
        salvarConfig();
    }

    // Centraliza a lógica de APLICAR o kit de forma segura
    public void aplicarKitNoJogador(Player p) {
        // Carrega o inventário principal
        List<?> itensRaw = kitConfig.getList("inventario");
        List<ItemStack> itensKit = new ArrayList<>();
        if (itensRaw != null) {
            for (Object obj : itensRaw) {
                if (obj instanceof ItemStack) itensKit.add((ItemStack) obj);
            }
        }

        // Carrega a armadura
        List<?> armaduraRaw = kitConfig.getList("armadura");
        List<ItemStack> armaduraKit = new ArrayList<>();
        if (armaduraRaw != null) {
            for (Object obj : armaduraRaw) {
                if (obj instanceof ItemStack) armaduraKit.add((ItemStack) obj);
            }
        }

        // Carrega a segunda mão
        ItemStack offHandItem = kitConfig.getItemStack("offhand");

        // Limpa e aplica com segurança
        p.getInventory().clear();

        if (!itensKit.isEmpty()) {
            // setStorageContents define apenas o inventário principal, sem mexer no resto
            p.getInventory().setStorageContents(itensKit.toArray(new ItemStack[0]));
        }
        if (!armaduraKit.isEmpty()) {
            p.getInventory().setArmorContents(armaduraKit.toArray(new ItemStack[0]));
        }
        if (offHandItem != null) {
            p.getInventory().setItemInOffHand(offHandItem);
        }

        p.updateInventory();
    }
}