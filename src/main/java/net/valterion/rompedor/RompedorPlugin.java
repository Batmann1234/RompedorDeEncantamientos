package net.valterion.rompedor;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RompedorPlugin extends JavaPlugin {

    private RompedorMenu menu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!AEBridge.init(getLogger())) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.menu = new RompedorMenu(this);
        getServer().getPluginManager().registerEvents(new RompedorListener(this, menu), this);
    }

    public String raw(String key) {
        return getConfig().getString("mensajes." + key, key);
    }

    public void send(Player p, String key, String... replace) {
        String text = raw("prefix") + raw(key);
        for (int i = 0; i + 1 < replace.length; i += 2) {
            text = text.replace(replace[i], replace[i + 1]);
        }
        p.sendMessage(MiniMessage.miniMessage().deserialize(text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rompedor.admin")) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize(raw("prefix") + raw("sin-permiso")));
                return true;
            }
            reloadConfig();
            sender.sendMessage(MiniMessage.miniMessage().deserialize(raw("prefix") + raw("recargado")));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(raw("prefix") + raw("solo-jugadores")));
            return true;
        }
        if (!p.hasPermission("rompedor.usar")) {
            send(p, "sin-permiso");
            return true;
        }
        menu.open(p);
        return true;
    }
}
