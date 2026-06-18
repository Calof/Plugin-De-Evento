package com.seuprojeto.eventopvp;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class EventoComando implements CommandExecutor {

    private final EventoPvP plugin;

    public EventoComando(EventoPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
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

        String sub = args[0].toLowerCase();

        if (sub.equals("start")) {
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
            List<?> itensKit = plugin.getKitConfig().getList("inventario");
            List<?> armaduraKit = plugin.getKitConfig().getList("armadura");

            for (java.util.UUID uuid : plugin.participantes) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    plugin.vivos.add(uuid);
                    p.getInventory().clear();

                    if (!cmdArenaRaw.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdArenaRaw.replace("%player%", p.getName()));

                    if (itensKit != null) {
                        p.getInventory().setContents(itensKit.toArray(new ItemStack[0]));
                    }
                    if (armaduraKit != null) {
                        p.getInventory().setArmorContents(armaduraKit.toArray(new ItemStack[0]));
                    }
                    p.updateInventory();
                }
            }
            Bukkit.broadcast(plugin.getMsg("broadcasts.batalha-comecou"));
            return true;
        }

        if (sub.equals("stop")) {
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

            plugin.getKitConfig().set("inventario", p.getInventory().getContents());
            plugin.getKitConfig().set("armadura", p.getInventory().getArmorContents());
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

        return false;
    }
}