package com.seuprojeto.eventopvp;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class EventoComando implements CommandExecutor, TabCompleter {

    private final EventoPvP plugin;

    public EventoComando(EventoPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("pvp") || args[1].equalsIgnoreCase("ajuda") || args[1].equalsIgnoreCase("help")) {
            exibirMenuAjuda(sender);
            return true;
        }

        String sub = args[1].toLowerCase();

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

            plugin.participantes.add(p.getUniqueId());
            plugin.jaEntraram.add(p.getUniqueId());
            plugin.localAnterior.put(p.getUniqueId(), p.getLocation());
            plugin.getJogadoresConfig().set(p.getUniqueId().toString(), true);
            plugin.saveJogadoresConfig();

            p.sendMessage(plugin.getMsg("mensagens.entrou-no-evento"));

            // Broadcast global de entrada adicionado aqui:
            String msgEntrada = plugin.getConfig().getString("broadcasts.jogador-entrou-evento", "").replace("%player%", p.getName());
            if (!msgEntrada.isEmpty()) {
                Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msgEntrada));
            }

            String cmdLobbyRaw = plugin.getConfig().getString("comandos.lobby", "");
            if (!cmdLobbyRaw.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdLobbyRaw.replace("%player%", p.getName()));
            }
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
            p.getActivePotionEffects().forEach(effect -> p.removePotionEffect(effect.getType()));

            org.bukkit.Location backLoc = plugin.localAnterior.remove(p.getUniqueId());
            if (backLoc != null) p.teleport(backLoc);

            plugin.getJogadoresConfig().set(p.getUniqueId().toString(), null);
            plugin.saveJogadoresConfig();

            p.sendMessage(plugin.getMsg("mensagens.saiu-do-evento"));

            // Broadcast global de saída adicionado aqui:
            String msgSaida = plugin.getConfig().getString("broadcasts.jogador-saiu-evento", "").replace("%player%", p.getName());
            if (!msgSaida.isEmpty()) {
                Bukkit.broadcast(LegacyComponentSerializer.legacyAmpersand().deserialize(msgSaida));
            }

            if (plugin.iniciado && estavaVivo) {
                plugin.verificarVencedor();
            }
            return true;
        }

        // --- COMANDOS ADMINISTRATIVOS ---
        if (!sender.hasPermission("eventopvp.admin")) {
            sender.sendMessage(plugin.getMsg("mensagens.sem-permissao"));
            return true;
        }

        if (sub.equals("abrir")) {
            plugin.aberto = true;
            plugin.iniciado = false;
            plugin.participantes.clear();
            plugin.vivos.clear();
            plugin.jaEntraram.clear();
            plugin.localAnterior.clear();
            plugin.penultimoUUID = null;
            plugin.pvpLiberado = false;

            Bukkit.broadcast(plugin.getMsg("broadcasts.evento-aberto"));
            return true;
        }

        if (sub.equals("setspawn")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMsg("mensagens.apenas-jogadores"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUse: /evento pvp setspawn <numero do spawn>"));
                return true;
            }

            Player p = (Player) sender;
            String numeroSpawn = args[2];

            try {
                Integer.parseInt(numeroSpawn);
            } catch (NumberFormatException e) {
                p.sendMessage(plugin.getMsg("mensagens.spawn-erro-numero"));
                return true;
            }

            Location loc = p.getLocation();
            String path = "spawns." + numeroSpawn;
            plugin.getSpawnsConfig().set(path + ".world", loc.getWorld().getName());
            plugin.getSpawnsConfig().set(path + ".x", loc.getX());
            plugin.getSpawnsConfig().set(path + ".y", loc.getY());
            plugin.getSpawnsConfig().set(path + ".z", loc.getZ());
            plugin.getSpawnsConfig().set(path + ".yaw", loc.getYaw());
            plugin.getSpawnsConfig().set(path + ".pitch", loc.getPitch());
            plugin.saveSpawnsConfig();

            p.sendMessage(plugin.getMsg("mensagens.spawn-setado").replaceText(b -> b.matchLiteral("%num%").replacement(numeroSpawn)));
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
                    if (obj instanceof ItemStack) itensKit.add((ItemStack) obj);
                }
            }

            List<?> armaduraRaw = plugin.getKitConfig().getList("armadura");
            List<ItemStack> armaduraKit = new ArrayList<>();
            if (armaduraRaw != null) {
                for (Object obj : armaduraRaw) {
                    if (obj instanceof ItemStack) armaduraKit.add((ItemStack) obj);
                }
            }

            List<Location> listaSpawnsCustomizados = new ArrayList<>();
            if (plugin.getSpawnsConfig().getConfigurationSection("spawns") != null) {
                for (String key : plugin.getSpawnsConfig().getConfigurationSection("spawns").getKeys(false)) {
                    String wName = plugin.getSpawnsConfig().getString("spawns." + key + ".world");
                    if (wName != null && Bukkit.getWorld(wName) != null) {
                        double x = plugin.getSpawnsConfig().getDouble("spawns." + key + ".x");
                        double y = plugin.getSpawnsConfig().getDouble("spawns." + key + ".y");
                        double z = plugin.getSpawnsConfig().getDouble("spawns." + key + ".z");
                        float yaw = (float) plugin.getSpawnsConfig().getDouble("spawns." + key + ".yaw");
                        float pitch = (float) plugin.getSpawnsConfig().getDouble("spawns." + key + ".pitch");
                        listaSpawnsCustomizados.add(new Location(Bukkit.getWorld(wName), x, y, z, yaw, pitch));
                    }
                }
            }

            int indexSpawn = 0;
            for (UUID uuid : plugin.participantes) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    plugin.vivos.add(uuid);
                    p.getInventory().clear();

                    if (!listaSpawnsCustomizados.isEmpty()) {
                        Location destino = listaSpawnsCustomizados.get(indexSpawn % listaSpawnsCustomizados.size());
                        p.teleport(destino);
                        indexSpawn++;
                    } else {
                        if (!cmdArenaRaw.isEmpty()) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdArenaRaw.replace("%player%", p.getName()));
                        }
                        Location locAtual = p.getLocation();
                        double randX = (Math.random() - 0.5) * 3.0;
                        double randZ = (Math.random() - 0.5) * 3.0;
                        p.teleport(locAtual.add(randX, 0, randZ));
                    }

                    if (!itensKit.isEmpty()) p.getInventory().setContents(itensKit.toArray(new ItemStack[0]));
                    if (!armaduraKit.isEmpty()) p.getInventory().setArmorContents(armaduraKit.toArray(new ItemStack[0]));
                    
                    plugin.aplicarEfeitosArena(p);
                    p.updateInventory();
                }
            }
            
            if (plugin.getConfig().getBoolean("preparacao.utilizar-preparacao", true)) {
                plugin.iniciarContagemPreparacao();
            } else {
                plugin.pvpLiberado = true;
                Bukkit.broadcast(plugin.getMsg("broadcasts.batalha-comecou"));
                plugin.iniciarAgendadoresBatalha();
            }
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
            plugin.getKitConfig().set("inventario", Arrays.asList(p.getInventory().getContents()));
            plugin.getKitConfig().set("armadura", Arrays.asList(p.getInventory().getArmorContents()));
            plugin.saveKitConfig();
            p.sendMessage(plugin.getMsg("mensagens.kit-definido"));
            return true;
        }

        if (sub.equals("recarregar")) {
            plugin.reloadConfig();
            sender.sendMessage(plugin.getMsg("mensagens.plugin-recarregado"));
            return true;
        }

        if (sub.equals("efeito")) {
            if (args.length < 3) {
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUse: /evento pvp efeito <adicionar/remover/limpar/lista> [efeito:nivel]"));
                return true;
            }
            String acao = args[2].toLowerCase();

            if (acao.equals("lista")) {
                List<String> efeitos = plugin.getConfig().getStringList("efeitos-arena");
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aEfeitos atuais na arena: &e" + String.join(", ", efeitos)));
                return true;
            }
            if (acao.equals("limpar")) {
                plugin.getConfig().set("efeitos-arena", new ArrayList<>());
                plugin.saveConfig();
                sender.sendMessage(plugin.getMsg("mensagens.efeitos-limpos"));
                return true;
            }

            if (args.length < 4) {
                sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cEspecifique o efeito. Ex: SPEED:0"));
                return true;
            }

            String efeitoInput = args[3].toUpperCase();
            String nomeEfeito = efeitoInput.contains(":") ? efeitoInput.split(":")[0] : efeitoInput;

            if (PotionEffectType.getByName(nomeEfeito) == null) {
                sender.sendMessage(plugin.getMsg("mensagens.efeito-invalido"));
                return true;
            }

            List<String> listaEfeitos = plugin.getConfig().getStringList("efeitos-arena");

            if (acao.equals("adicionar")) {
                String amp = efeitoInput.contains(":") ? legacyGetAmp(efeitoInput) : "0";
                String entradaCompleta = nomeEfeito + ":" + amp;
                listaEfeitos.removeIf(s -> s.startsWith(nomeEfeito + ":"));
                listaEfeitos.add(entradaCompleta);
                plugin.getConfig().set("efeitos-arena", listaEfeitos);
                plugin.saveConfig();
                sender.sendMessage(plugin.getMsg("mensagens.efeito-adicionado").replaceText(b -> b.matchLiteral("%effect%").replacement(entradaCompleta)));
            } else if (acao.equals("remover")) {
                boolean removido = listaEfeitos.removeIf(s -> s.startsWith(nomeEfeito + ":"));
                if (removido) {
                    plugin.getConfig().set("efeitos-arena", listaEfeitos);
                    plugin.saveConfig();
                    sender.sendMessage(plugin.getMsg("mensagens.efeito-recorrente-removido").replaceText(b -> b.matchLiteral("%effect%").replacement(nomeEfeito)));
                } else {
                    sender.sendMessage(plugin.getMsg("mensagens.efeito-nao-encontrado"));
                }
            }
            return true;
        }

        sender.sendMessage(plugin.getMsg("mensagens.comando-invalido"));
        return true;
    }

    private String legacyGetAmp(String input) {
        String[] split = input.split(":");
        return split.length > 1 ? split[1] : "0";
    }

    private void exibirMenuAjuda(CommandSender sender) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&r"));
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&6============= &e&lMENU DE AJUDA: EVENTO PVP &6============="));
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/evento pvp entrar &7- Participar do evento aberto."));
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/evento pvp sair &7- Sair do evento e voltar ao ponto anterior."));
        if (sender.hasPermission("eventopvp.admin")) {
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp abrir &7- Abre as inscrições do evento pvp."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp iniciar &7- Teleporta e inicia os contadores de luta."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp fechar &7- Força a finalização imediata do evento."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp setspawn <id> &7- Cria um ponto de nascimento na sua posição."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp definirkit &7- Salva seu inventário atual como o kit de luta."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp efeito <add/remove/limpar/lista> &7- Gerencia poções."));
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&c/evento pvp recarregar &7- Atualiza as variáveis da config.yml."));
        }
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&6===================================================="));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("pvp").stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pvp")) {
            List<String> subComandos = new ArrayList<>(Arrays.asList("entrar", "sair", "ajuda", "help"));
            if (sender.hasPermission("eventopvp.admin")) {
                subComandos.addAll(Arrays.asList("abrir", "iniciar", "fechar", "definirkit", "recarregar", "efeito", "setspawn"));
            }
            return subComandos.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("setspawn") && sender.hasPermission("eventopvp.admin")) {
            return Arrays.asList("1", "2", "3", "4", "5");
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("efeito") && sender.hasPermission("eventopvp.admin")) {
            return Arrays.asList("adicionar", "remover", "limpar", "lista").stream().filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("efeito") && (args[2].equalsIgnoreCase("adicionar") || args[2].equalsIgnoreCase("remover")) && sender.hasPermission("eventopvp.admin")) {
            return Arrays.stream(PotionEffectType.values()).map(PotionEffectType::getName).map(String::toLowerCase).filter(name -> name.startsWith(args[3].toLowerCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}