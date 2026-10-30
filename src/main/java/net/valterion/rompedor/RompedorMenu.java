package net.valterion.rompedor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class RompedorMenu {

    /** Holder propio: así identificamos el menú sin depender del título. */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final RompedorPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public RompedorMenu(RompedorPlugin plugin) {
        this.plugin = plugin;
    }

    public int inputSlot() {
        return plugin.getConfig().getInt("menu.slot-item", 13);
    }

    public void open(Player p) {
        int rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("menu.filas", 3)));
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
                mm.deserialize(plugin.getConfig().getString("menu.titulo", "Rompedor")));
        holder.inventory = inv;

        Material fill = Material.matchMaterial(plugin.getConfig().getString("menu.relleno", "BLACK_STAINED_GLASS_PANE"));
        if (fill == null) fill = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack filler = new ItemStack(fill);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);

        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        inv.setItem(inputSlot(), null);

        int infoSlot = plugin.getConfig().getInt("menu.info.slot", -1);
        if (infoSlot >= 0 && infoSlot < inv.getSize() && infoSlot != inputSlot()) {
            Material im = Material.matchMaterial(plugin.getConfig().getString("menu.info.material", "PAPER"));
            if (im == null) im = Material.PAPER;
            ItemStack info = new ItemStack(im);
            ItemMeta meta = info.getItemMeta();
            meta.displayName(mm.deserialize(plugin.getConfig().getString("menu.info.nombre", "")));
            List<Component> lore = new ArrayList<>();
            for (String l : plugin.getConfig().getStringList("menu.info.lore")) lore.add(mm.deserialize(l));
            meta.lore(lore);
            info.setItemMeta(meta);
            inv.setItem(infoSlot, info);
        }
        p.openInventory(inv);
    }
}
