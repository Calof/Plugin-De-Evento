package com.seuprojeto.eventopvp;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class EventoComando implements CommandExecutor {

    private final EventoPvP plugin;

    public EventoComando(EventoPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("ajuda")) {
            enviarMenuAjuda(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // ================= COMMANDS JOGADORES =================
        if (sub.equals("entrar")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMsg("mensagens.apenas-jogadores"));
                return true;
            }
            Player p = (Player) sender;

            if (!plugin.aberto) {
                p.sendMessage(plugin.getMsg("mensagens.evento-fechado"));
                return true;
            }
            if (plugin.iniciado) {
                p.sendMessage(plugin.getMsg("mensagens.evento-ja-iniciado"));
                return true;
            }
            if (plugin.participantes.contains(p.getUniqueId())) {
                p.sendMessage(plugin.getMsg("mensagens.ja-esta-no-evento"));
                return true;
            }
            if (plugin.jaEntraram.contains(p.getUniqueId())) {
                p.sendMessage(plugin.getMsg("mensagens.ja-participou"));
                return true;
            }

            plugin.localAnterior.put(p.getUniqueId(), p.getLocation());
            plugin.participantes.add(p.getUniqueId());
            plugin.jaEntraram.add(p.getUniqueId());

            plugin.getJogadoresConfig().set(p.getUniqueId().toString(), p.getLocation());
            plugin.saveJogadoresConfig();

            String cmdLobby = plugin.getConfig().getString("comandos.lobby", "").replace("%player%", p.getName());
            if (!cmdLobby.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdLobby);

            p.sendMessage(plugin.getMsg("mensagens.entrou-no-evento"));
            return true;
        }

        if (sub.equals("sair")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMsg("mensagens.apenas-jogadores"));
                return true;
            }
            Player p = (Player) sender;

            if (!plugin.participantes.contains(p.getUniqueId())) {
                p.sendMessage(plugin.getMsg("mensagens.na-lista-de-evento"));
                return true;
            }

            plugin.participantes.remove(p.getUniqueId());
            boolean estavaVivo = plugin.vivos.remove(p.getUniqueId());

            p.getInventory().clear();
            p.setFireTicks(0);
            p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));
            if (p.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            }

            Location loc = plugin.localAnterior.remove(p.getUniqueId());
            if (loc != null) p.teleport(loc);

            plugin.getJogadoresConfig().set(p.getUniqueId().toString(), null);
            plugin.saveJogadoresConfig();

            p.sendMessage(plugin.getMsg("mensagens.saiu-do-evento"));

            if (plugin.iniciado && estavaVivo) {
                plugin.verificarVencedor();
            }
            return true;
        }

        // ================= COMMANDS ADMINISTRADORES =================
        if (sub.equals("abrir")) {
            if (!sender.hasPermission("evento.admin")) {
                sender.sendMessage(plugin.getMsg("mensagens.sem-permissao"));
                return true;
            }
            plugin.aberto = true;
            plugin.jaEntraram.clear();
            Bukkit.broadcast(plugin.getMsg("broadcasts.evento-aberto"));
            return true;
        }

        if (sub.equals("iniciar")) {
            if (!sender.hasPermission("evento.admin")) {
                sender.sendMessage(plugin.getMsg("mensagens.sem-permissao"));
                return true;
            }

            int minJogadores = plugin.getConfig().getInt("minimo-jogadores", 2);
            if (plugin.participantes.size() < minJogadores) {
                String msgErro = plugin.getConfig().getString("mensagens.jogadores-insuficientes", "")
                        .replace("%min%", String.valueOf(minJogadores))
                        .replace("%atual%", String.valueOf(plugin.participantes.size()));
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msgErro));
                return true;
            }

            plugin.iniciado = true;
            plugin.vivos.clear();

            String cmdArenaRaw = plugin.getConfig().getString("comandos.arena", "");

            List<?> itensRaw = plugin.getKitConfig().getList("inventario");
            List<ItemStack> itensKit = new ArrayList<>();
            if (itensRaw != null) {
                for (Object obj : itensRaw) {
                    if (obj instanceof ItemStack) {
                        itensKit.add((ItemStack) obj);
                    } else {
                        itensKit.add(null);
                    }
                }
            }

            List<?> armaduraRaw = plugin.getKitConfig().getList("armadura");
            List<ItemStack> armaduraKit = new ArrayList<>();
            if (armaduraRaw != null) {
                for (Object obj : armaduraRaw) {
                    if (obj instanceof ItemStack) {
                        armaduraKit.add((ItemStack) obj);
                    } else {
                        armaduraKit.add(null);
                    }
                }
            }

            for (java.util.UUID uuid : plugin.participantes) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    plugin.vivos.add(uuid);
                    p.getInventory().clear();

                    if (!cmdArenaRaw.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdArenaRaw.replace("%player%", p.getName()));

                    if (!itensKit.isEmpty()) {
                        p.getInventory().setContents(itensKit.toArray(new ItemStack[0]));
                    }
                    if (!armaduraKit.isEmpty()) {
                        p.getInventory().setArmorContents(armaduraKit.toArray(new ItemStack[0]));
                    }
                    
                    p.updateInventory();
                }
            }
            Bukkit.broadcast(plugin.getMsg("broadcasts.batalha-comecou"));
            return true;
        }

        if (sub.equals("fechar")) {
            if (!sender.hasPermission("evento.admin")) {
                sender.sendMessage(plugin.getMsg("mensagens.sem-permissao"));
                return true;
            }
            Bukkit.broadcast(plugin.getMsg("broadcasts.evento-encerrado"));
            plugin.encerrarEvento();
            return true;
        }

        if (sub.equals("definirkit")) {
            if (!sender.hasPermission("evento.admin")) {
                sender.sendMessage(plugin.getMsg("mensagens.sem-permissao"));
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMsg("mensagens.apenas-jogadores"));
                return true;
            }
            Player p = (Player) sender;

            List<ItemStack> inventarioLista = new ArrayList<>();
            for (ItemStack item : p.getInventory().getContents()) {
                inventarioLista.add(item);
            }

            List<ItemStack> armaduraLista = new ArrayList<>();
            for (ItemStack item : p.getInventory().getArmorContents()) {
                armaduraLista.add(item);
            }

            plugin.getKitConfig().set("inventario", inventarioLista);
            plugin.getKitConfig().set("armadura", armaduraLista);
            plugin.saveKitConfig();

            p.sendMessage(plugin.getMsg("mensagens.kit-definido"));
            return true;
        }

        if (sub.equals("reload")) {
            if (!sender.hasPermission("evento.admin")) {
                sender.sendMessage(plugin.getMsg("mensagens.sem-permissao"));
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(plugin.getMsg("mensagens.plugin-recarregado"));
            return true;
        }

        enviarMenuAjuda(sender);
        return true;
    }

    private void enviarMenuAjuda(CommandSender sender) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&6====== COMANDOS DO EVENTOPVP ======"));
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/evento entrar &7- Entra no evento se estiver aberto."));
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/evento sair &7- Desiste e sai do evento atual."));
        
        if (sender.hasPermission("evento.admin")) {
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c=== COMANDOS ADMINISTRATIVOS ==="));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento abrir &7- Permite a entrada de jogadores."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento iniciar &7- Envia os jogadores para a Arena com o Kit."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento fechar &7- Cancela e encerra o evento imediatamente."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento definirkit &7- Salva o seu inventário atual como o Kit Oficial."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento reload &7- Atualiza as mensagens da config.yml."));
        }
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&6================================="));
    }
}