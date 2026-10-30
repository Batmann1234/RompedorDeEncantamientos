package net.valterion.rompedor;

import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RompedorListener implements Listener {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_\\-.]+");

    private final RompedorPlugin plugin;
    private final RompedorMenu menu;
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, Long> last = new HashMap<>();

    public RompedorListener(RompedorPlugin plugin, RompedorMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    private static boolean empty(ItemStack i) {
        return i == null || i.getType().isAir();
    }

    private static boolean isOurs(Inventory inv) {
        return inv != null && inv.getHolder() instanceof RompedorMenu.Holder;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        busy.remove(e.getPlayer().getUniqueId());
        last.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (isOurs(e.getView().getTopInventory())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!isOurs(top)) return;

        // Nada se mueve dentro del menú jamás: el ítem nunca "vive" en el menú,
        // así que no hay nada que devolver al cerrar ni nada que duplicar.
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        ItemStack source;
        Runnable consume;

        if (clicked == top) {
            if (e.getSlot() != menu.inputSlot()) return;
            ClickType t = e.getClick();
            if (t == ClickType.LEFT || t == ClickType.RIGHT) {
                ItemStack cursor = p.getItemOnCursor();
                if (empty(cursor)) return;
                source = cursor;
                consume = () -> p.setItemOnCursor(null);
            } else if (t == ClickType.NUMBER_KEY) {
                int b = e.getHotbarButton();
                if (b < 0 || b > 8) return;
                ItemStack it = p.getInventory().getItem(b);
                if (empty(it)) return;
                source = it;
                consume = () -> p.getInventory().setItem(b, null);
            } else {
                return;
            }
        } else {
            if (!e.isShiftClick()) return;
            int slot = e.getSlot();
            if (slot < 0 || slot > 35) return; // solo inventario principal + hotbar
            ItemStack it = clicked.getItem(slot);
            if (empty(it)) return;
            source = it;
            consume = () -> clicked.setItem(slot, null);
        }

        process(p, source, consume);
    }

    private void process(Player p, ItemStack item, Runnable consume) {
        UUID id = p.getUniqueId();
        if (!busy.add(id)) return;
        try {
            long now = System.currentTimeMillis();
            long cd = plugin.getConfig().getLong("cooldown-ms", 500);
            Long prev = last.get(id);
            if (prev != null && now - prev < cd) {
                plugin.send(p, "cooldown");
                return;
            }

            if (item.getAmount() != 1) {
                plugin.send(p, "cantidad");
                return;
            }

            // 1) Identidad por NBT de MythicMobs (no por nombre ni lore: eso se falsifica)
            MythicBukkit mythic = MythicBukkit.inst();
            String type = mythic.getItemManager().getMythicTypeFromItem(item);
            if (type == null || !SAFE.matcher(type).matches()) {
                plugin.send(p, "no-mythic");
                return;
            }
            List<String> allowed = plugin.getConfig().getStringList("items-permitidos");
            if (!allowed.isEmpty() && allowed.stream().noneMatch(s -> s.equalsIgnoreCase(type))) {
                plugin.send(p, "no-permitido");
                return;
            }

            // 2) Encantamientos reales de AE (leídos del NBT por la API, no del lore)
            Map<String, Integer> rawItem = AEBridge.enchants(item);
            if (rawItem == null) {
                plugin.send(p, "error");
                return;
            }
            Map<String, Integer> onItem = lower(rawItem);

            // 3) Base: lo que ya trae la versión limpia de fábrica no se paga como runa
            Map<String, Integer> base;
            try {
                ItemStack clean = mythic.getItemManager().getItemStack(type);
                Map<String, Integer> rawBase = empty(clean) ? null : AEBridge.enchants(clean);
                if (rawBase == null) {
                    plugin.send(p, "error");
                    return;
                }
                base = lower(rawBase);
            } catch (Exception ex) {
                plugin.send(p, "error");
                return;
            }

            Map<String, Integer> toGive = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> en : onItem.entrySet()) {
                int lvl = en.getValue() == null ? 0 : en.getValue();
                if (lvl <= 0 || !SAFE.matcher(en.getKey()).matches()) continue;
                if (lvl > base.getOrDefault(en.getKey(), 0)) toGive.put(en.getKey(), lvl);
            }
            if (toGive.isEmpty()) {
                plugin.send(p, "sin-encantamientos");
                return;
            }

            // 4) Espacio ANTES de consumir nada
            int needed = 1 + toGive.size();
            int free = 0;
            for (ItemStack s : p.getInventory().getStorageContents()) if (empty(s)) free++;
            if (free < needed) {
                plugin.send(p, "sin-espacio", "<n>", String.valueOf(needed));
                return;
            }

            // 5) Consumir primero, entregar después. Todo síncrono en el hilo principal.
            consume.run();
            last.put(id, now);

            String audit = "jugador=" + p.getName() + " item=" + type + " runas=" + toGive;
            log(audit);
            try {
                String cmdItem = plugin.getConfig().getString("comandos.item-limpio")
                        .replace("<jugador>", p.getName()).replace("<item>", type);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdItem);

                String cmdRuna = plugin.getConfig().getString("comandos.runa");
                for (Map.Entry<String, Integer> en : toGive.entrySet()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdRuna
                            .replace("<jugador>", p.getName())
                            .replace("<encantamiento>", en.getKey())
                            .replace("<nivel>", String.valueOf(en.getValue())));
                }
            } catch (Exception ex) {
                // El ítem ya se consumió: queda todo en el registro para reponerlo a mano.
                plugin.getLogger().severe("FALLO ENTREGANDO -> " + audit + " : " + ex.getMessage());
                log("FALLO " + audit);
                plugin.send(p, "error");
                return;
            }

            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
            plugin.send(p, "roto", "<n>", String.valueOf(toGive.size()));
        } finally {
            busy.remove(id);
            Bukkit.getScheduler().runTask(plugin, p::updateInventory);
        }
    }

    private static Map<String, Integer> lower(Map<String, Integer> in) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (in == null) return out;
        for (Map.Entry<String, Integer> en : in.entrySet()) {
            if (en.getKey() != null) out.put(en.getKey().toLowerCase(Locale.ROOT), en.getValue());
        }
        return out;
    }

    private void log(String line) {
        try {
            File f = new File(plugin.getDataFolder(), "registro.log");
            plugin.getDataFolder().mkdirs();
            try (PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
                w.println("[" + LocalDateTime.now() + "] " + line);
            }
        } catch (Exception ignored) {
        }
    }
}
