package com.seuprojeto.eventopvp;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EventoComando implements CommandExecutor, TabCompleter {

    private final EventoPvP plugin;

    public EventoComando(EventoPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("pvp")) {
            sender.sendMessage(plugin.getMsg("mensagens.comando-invalido"));
            return true;
        }

        String sub = args[1].toLowerCase();

        // ================= COMANDOS JOGADORES =================
        if (sub.equals("ajuda") || sub.equals("help")) {
            enviarMenuAjuda(sender);
            return true;
        }

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
            
            if (plugin.vivos.size() == 2 && plugin.vivos.contains(p.getUniqueId())) {
                plugin.penultimoUUID = p.getUniqueId();
            }
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

        // ================= COMANDOS ADMINISTRADORES =================
        if (!sender.hasPermission("evento.admin")) {
            sender.sendMessage(plugin.getMsg("mensagens.sem-permissao"));
            return true;
        }

        if (sub.equals("abrir")) {
            plugin.aberto = true;
            plugin.jaEntraram.clear();
            plugin.penultimoUUID = null;
            Bukkit.broadcast(plugin.getMsg("broadcasts.evento-aberto"));
            return true;
        }

        if (sub.equals("iniciar")) {
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
            plugin.penultimoUUID = null;

            String cmdArenaRaw = plugin.getConfig().getString("comandos.arena", "");

            List<?> itensRaw = plugin.getKitConfig().getList("inventario");
            List<ItemStack> itensKit = new ArrayList<>();
            if (itensRaw != null) {
                for (Object obj : itensRaw) {
                    itensKit.add(obj instanceof ItemStack ? (ItemStack) obj : null);
                }
            }

            List<?> armaduraRaw = plugin.getKitConfig().getList("armadura");
            List<ItemStack> armaduraKit = new ArrayList<>();
            if (armaduraRaw != null) {
                for (Object obj : armaduraRaw) {
                    armaduraKit.add(obj instanceof ItemStack ? (ItemStack) obj : null);
                }
            }

            for (java.util.UUID uuid : plugin.participantes) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    plugin.vivos.add(uuid);
                    p.getInventory().clear();

                    if (!cmdArenaRaw.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdArenaRaw.replace("%player%", p.getName()));

                    if (!itensKit.isEmpty()) p.getInventory().setContents(itensKit.toArray(new ItemStack[0]));
                    if (!armaduraKit.isEmpty()) p.getInventory().setArmorContents(armaduraKit.toArray(new ItemStack[0]));
                    
                    plugin.aplicarEfeitosArena(p);
                    p.updateInventory();
                }
            }
            Bukkit.broadcast(plugin.getMsg("broadcasts.batalha-comecou"));
            return true;
        }

        if (sub.equals("fechar")) {
            Bukkit.broadcast(plugin.getMsg("broadcasts.evento-encerrado"));
            plugin.encerrarEvento();
            return true;
        }

        if (sub.equals("definirkit")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMsg("mensagens.apenas-jogadores"));
                return true;
            }
            Player p = (Player) sender;

            List<ItemStack> inventarioLista = new ArrayList<>();
            for (ItemStack item : p.getInventory().getContents()) inventarioLista.add(item);

            List<ItemStack> armaduraLista = new ArrayList<>();
            for (ItemStack item : p.getInventory().getArmorContents()) armaduraLista.add(item);

            plugin.getKitConfig().set("inventario", inventarioLista);
            plugin.getKitConfig().set("armadura", armaduraLista);
            plugin.saveKitConfig();

            p.sendMessage(plugin.getMsg("mensagens.kit-definido"));
            return true;
        }

        if (sub.equals("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(plugin.getMsg("mensagens.plugin-recarregado"));
            return true;
        }

        // ================= SISTEMA DINÂMICO DE EFEITOS =================
        if (sub.equals("efeito")) {
            if (args.length < 3) {
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUse: /evento pvp efeito <adicionar|remover|limpar|lista>"));
                return true;
            }
            String acaoEfeito = args[2].toLowerCase();
            List<String> listaAtual = plugin.getConfig().getStringList("efeitos-arena");

            if (acaoEfeito.equals("adicionar")) {
                if (args.length < 4) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUse: /evento pvp efeito adicionar <NOME_EFEITO> [nivel]"));
                    return true;
                }
                String nomeEfeito = args[3].toUpperCase();
                PotionEffectType tipo = PotionEffectType.getByName(nomeEfeito);
                if (tipo == null) {
                    sender.sendMessage(plugin.getMsg("mensagens.efeito-invalido"));
                    return true;
                }
                int nivel = 1;
                if (args.length >= 5) {
                    try { nivel = Integer.parseInt(args[4]); } catch (NumberFormatException e) { nivel = 1; }
                }
                int amplifier = nivel - 1 < 0 ? 0 : nivel - 1;

                listaAtual.removeIf(s -> s.toUpperCase().startsWith(nomeEfeito + ":") || s.toUpperCase().equals(nomeEfeito));
                listaAtual.add(nomeEfeito + ":" + amplifier);
                
                plugin.getConfig().set("efeitos-arena", listaAtual);
                plugin.saveConfig();

                String msg = plugin.getConfig().getString("mensagens.efeito-adicionado", "").replace("%effect%", nomeEfeito + " " + nivel);
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
                return true;
            }

            if (acaoEfeito.equals("remover")) {
                if (args.length < 4) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUse: /evento pvp efeito remover <NOME_EFEITO>"));
                    return true;
                }
                String nomeEfeito = args[3].toUpperCase();
                boolean removido = listaAtual.removeIf(s -> s.toUpperCase().startsWith(nomeEfeito + ":") || s.toUpperCase().equals(nomeEfeito));
                
                if (!removido) {
                    sender.sendMessage(plugin.getMsg("mensagens.efeito-nao-encontrado"));
                    return true;
                }

                plugin.getConfig().set("efeitos-arena", listaAtual);
                plugin.saveConfig();

                String msg = plugin.getConfig().getString("mensagens.efeito-removido", "").replace("%effect%", nomeEfeito);
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
                return true;
            }

            if (acaoEfeito.equals("limpar")) {
                listaAtual.clear();
                plugin.getConfig().set("efeitos-arena", listaAtual);
                plugin.saveConfig();
                sender.sendMessage(plugin.getMsg("mensagens.efeitos-limpos"));
                return true;
            }

            if (acaoEfeito.equals("lista")) {
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&6=== EFEITOS ATUAIS DA ARENA ==="));
                if (listaAtual.isEmpty()) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&7Nenhum efeito configurado."));
                } else {
                    for (String ef : listaAtual) {
                        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e- " + ef));
                    }
                }
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&6=============================="));
                return true;
            }
        }

        sender.sendMessage(plugin.getMsg("mensagens.comando-invalido"));
        return true;
    }

    // =========================================================================
    // LÓGICA DO AUTOCOMPLETAR (TAB COMPLETE)
    // =========================================================================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> listaProvisoria = new ArrayList<>();

        if (args.length == 1) {
            listaProvisoria.add("pvp");
            StringUtil.copyPartialMatches(args[0], listaProvisoria, completions);
            Collections.sort(completions);
            return completions;
        }

        if (!args[0].equalsIgnoreCase("pvp")) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            listaProvisoria.addAll(Arrays.asList("entrar", "sair", "ajuda"));
            if (sender.hasPermission("evento.admin")) {
                listaProvisoria.addAll(Arrays.asList("abrir", "iniciar", "fechar", "definirkit", "reload", "efeito"));
            }
            StringUtil.copyPartialMatches(args[1], listaProvisoria, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("efeito")) {
            if (sender.hasPermission("evento.admin")) {
                listaProvisoria.addAll(Arrays.asList("adicionar", "remover", "limpar", "lista"));
                StringUtil.copyPartialMatches(args[2], listaProvisoria, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("efeito")) {
            if (sender.hasPermission("evento.admin")) {
                String subAcao = args[2].toLowerCase();
                
                if (subAcao.equals("adicionar")) {
                    for (PotionEffectType type : PotionEffectType.values()) {
                        if (type != null && type.getName() != null) {
                            listaProvisoria.add(type.getName().toLowerCase());
                        }
                    }
                } else if (subAcao.equals("remover")) {
                    List<String> efeitosAtuais = plugin.getConfig().getStringList("efeitos-arena");
                    for (String linha : efeitosAtuais) {
                        listaProvisoria.add(linha.split(":")[0].toLowerCase());
                    }
                }

                StringUtil.copyPartialMatches(args[3], listaProvisoria, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        if (args.length == 5 && args[1].equalsIgnoreCase("efeito") && args[2].equalsIgnoreCase("adicionar")) {
            if (sender.hasPermission("evento.admin")) {
                listaProvisoria.addAll(Arrays.asList("1", "2", "3"));
                StringUtil.copyPartialMatches(args[4], listaProvisoria, completions);
                return completions;
            }
        }

        return Collections.emptyList();
    }

    private void enviarMenuAjuda(CommandSender sender) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&6====== COMANDOS DO EVENTO PVP ======"));
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/evento pvp entrar &7- Entra no evento se aberto."));
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/evento pvp sair &7- Desiste e sai do evento."));
        
        if (sender.hasPermission("evento.admin")) {
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c=== COMANDOS ADMINISTRATIVOS ==="));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp abrir &7- Abre inscrições."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp iniciar &7- Inicia a arena com kits e efeitos."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp fechar &7- Força o encerramento do evento."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp definirkit &7- Salva seu inventário como Kit."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp reload &7- Recarrega a config.yml."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp efeito <adicionar|remover|limpar|lista> &7- Gerencia poções."));
        }
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&6================================="));
    }
}