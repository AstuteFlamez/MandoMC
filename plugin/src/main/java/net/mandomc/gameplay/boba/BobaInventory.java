package net.mandomc.gameplay.boba;

import org.bukkit.Material;
import org.bukkit.inventory.PlayerInventory;

/** Native fuel is intentionally exact-material and hotbar-only; flame ammo follows storage convention. */
public final class BobaInventory {
    private BobaInventory() { }
    public static int coal(PlayerInventory inventory) {
        int count=0;
        for (int slot=0;slot<9;slot++) {
            var stack=inventory.getItem(slot);
            if (stack!=null&&stack.getType()==Material.COAL) count+=stack.getAmount();
        }
        return count;
    }
    public static boolean coalUnit(PlayerInventory inventory) { return take(inventory,Material.COAL,9); }
    public static boolean fireCharge(PlayerInventory inventory) { return take(inventory,Material.FIRE_CHARGE,36); }
    private static boolean take(PlayerInventory inventory,Material type,int end) {
        for (int slot=0;slot<end;slot++) {
            var stack=inventory.getItem(slot);
            if (stack==null||stack.getType()!=type||stack.getAmount()<1) continue;
            if (stack.getAmount()==1) inventory.setItem(slot,null);
            else { stack.setAmount(stack.getAmount()-1); inventory.setItem(slot,stack); }
            return true;
        }
        return false;
    }
}
