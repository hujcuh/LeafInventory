
package me.LeafPixel.LeafInventory.config;

/**
 * Lock strategy while a shulker UI is open.
 *
 * SLOT_ONLY:
 * - Only lock the original shulker slot and block dangerous hotkeys/actions.
 * - Better gameplay experience, usually safe enough with token+commit checks.
 *
 * FULL_INVENTORY:
 * - Cancel most inventory clicks/drags while the shulker UI is open.
 * - Maximum safety, but more restrictive.
 */
public enum ShulkerLockMode {
    SLOT_ONLY,
    FULL_INVENTORY;

    /**
     * Parse from config string. Defaults to SLOT_ONLY for a better UX.
     */
    public static ShulkerLockMode fromString(String raw) {
        if (raw == null) return SLOT_ONLY;
        try {
            return ShulkerLockMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SLOT_ONLY;
        }
    }
}
