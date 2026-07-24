package com.example.corovaItems;

/**
 * Decouples corovaItems from corovaPermissions.
 *
 * corovaPermissions registers a concrete implementation at startup
 * (e.g. in its onEnable) via {@link #register(CurrencyHook)}.
 * corovaItems code (e.g. PaydayBook) calls the static helpers without
 * importing anything from corovaPermissions.
 */
public interface CurrencyHook {

    // ── Implementation contract ───────────────────────────────────────────────

    long  getBalance(org.bukkit.entity.Player player);
    void  addBalance(org.bukkit.entity.Player player, long amount);
    /** @return true if the deduction succeeded (sufficient funds). */
    boolean removeBalance(org.bukkit.entity.Player player, long amount);
    /** Formats an amount with the currency symbol, e.g. "💎 1,234". */
    String format(long amount);

    // ── Static registry ───────────────────────────────────────────────────────

    final class Registry {
        private static CurrencyHook instance;

        private Registry() {}

        public static void register(CurrencyHook hook) {
            instance = hook;
        }

        public static boolean isRegistered() {
            return instance != null;
        }

        // Convenience pass-through methods used by corovaItems classes

        public static long getBalance(org.bukkit.entity.Player player) {
            if (instance == null) return 0L;
            return instance.getBalance(player);
        }

        public static void addBalance(org.bukkit.entity.Player player, long amount) {
            if (instance == null) return;
            instance.addBalance(player, amount);
        }

        public static boolean removeBalance(org.bukkit.entity.Player player, long amount) {
            if (instance == null) return false;
            return instance.removeBalance(player, amount);
        }

        public static String format(long amount) {
            if (instance == null) return String.valueOf(amount);
            return instance.format(amount);
        }
    }
}