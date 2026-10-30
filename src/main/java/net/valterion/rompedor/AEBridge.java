package net.valterion.rompedor;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Logger;

/** Acceso a AEAPI por reflexión: compila sin el jar de AdvancedEnchantments. */
final class AEBridge {

    private static Method method;

    private AEBridge() {
    }

    static boolean init(Logger log) {
        try {
            Class<?> c = Class.forName("net.advancedplugins.ae.api.AEAPI");
            method = c.getMethod("getEnchantmentsOnItem", ItemStack.class);
            return true;
        } catch (Throwable t) {
            log.severe("No se encontró AEAPI#getEnchantmentsOnItem en tu versión de AdvancedEnchantments: " + t);
            return false;
        }
    }

    /** Devuelve null si falla; quien llama debe abortar sin consumir el ítem. */
    @SuppressWarnings("unchecked")
    static Map<String, Integer> enchants(ItemStack item) {
        try {
            return (Map<String, Integer>) method.invoke(null, item);
        } catch (Throwable t) {
            return null;
        }
    }
}
