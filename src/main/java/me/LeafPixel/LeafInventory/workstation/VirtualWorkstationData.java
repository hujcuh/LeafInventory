package me.LeafPixel.LeafInventory.workstation;

import org.bukkit.inventory.ItemStack;

public final class VirtualWorkstationData {
    private ItemStack input;
    private ItemStack fuel;
    private ItemStack output;

    private int burnTimeRemaining;
    private int burnTimeTotal;
    private int cookTime;
    private int cookTimeTotal = 200; // default for MVP

    private long lastAccessTime = System.currentTimeMillis();

    public ItemStack getInput() {
        return input;
    }

    public void setInput(ItemStack input) {
        this.input = cloneOrNull(input);
    }

    public ItemStack getFuel() {
        return fuel;
    }

    public void setFuel(ItemStack fuel) {
        this.fuel = cloneOrNull(fuel);
    }

    public ItemStack getOutput() {
        return output;
    }

    public void setOutput(ItemStack output) {
        this.output = cloneOrNull(output);
    }

    public int getBurnTimeRemaining() {
        return burnTimeRemaining;
    }

    public void setBurnTimeRemaining(int burnTimeRemaining) {
        this.burnTimeRemaining = burnTimeRemaining;
    }

    public int getBurnTimeTotal() {
        return burnTimeTotal;
    }

    public void setBurnTimeTotal(int burnTimeTotal) {
        this.burnTimeTotal = burnTimeTotal;
    }

    public int getCookTime() {
        return cookTime;
    }

    public void setCookTime(int cookTime) {
        this.cookTime = cookTime;
    }

    public int getCookTimeTotal() {
        return cookTimeTotal;
    }

    public void setCookTimeTotal(int cookTimeTotal) {
        this.cookTimeTotal = cookTimeTotal;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void touch() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    public VirtualWorkstationData copy() {
        VirtualWorkstationData d = new VirtualWorkstationData();
        d.setInput(input);
        d.setFuel(fuel);
        d.setOutput(output);
        d.setBurnTimeRemaining(burnTimeRemaining);
        d.setBurnTimeTotal(burnTimeTotal);
        d.setCookTime(cookTime);
        d.setCookTimeTotal(cookTimeTotal);
        d.lastAccessTime = this.lastAccessTime;
        return d;
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}
