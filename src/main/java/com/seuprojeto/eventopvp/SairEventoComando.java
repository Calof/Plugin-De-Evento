package com.seuprojeto.eventopvp;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SairEventoComando implements CommandExecutor {

    private final EventoPvP plugin;

    public SairEventoComando(EventoPvP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
}