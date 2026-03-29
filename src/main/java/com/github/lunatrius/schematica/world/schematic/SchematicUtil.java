// Based on Schematica by Lunatrius (https://github.com/Lunatrius/Schematica)
// Licensed under the MIT License. See LICENSE file for details.
package com.github.lunatrius.schematica.world.schematic;

import com.github.lunatrius.schematica.reference.Reference;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.Block;
import net.minecraft.ItemStack;
import net.minecraft.CompressedStreamTools;
import net.minecraft.NBTTagCompound;
import net.minecraft.Item;

public final class SchematicUtil {
    private static final ItemStack DEFAULT_ICON = new ItemStack((Block)Block.stone);

    public static NBTTagCompound readTagCompoundFromFile(File file) throws IOException {
        try {
            return CompressedStreamTools.readCompressed((InputStream)new FileInputStream(file));
        }
        catch (Exception ex) {
            Reference.logger.warn("Failed compressed read, trying normal read...", (Throwable)ex);
            return CompressedStreamTools.read((File)file);
        }
    }

    public static ItemStack getIconFromName(String iconName) {
        ItemStack icon = null;
        String name = "";
        int damage = 0;
        String[] parts = iconName.split(",");
        if (parts.length >= 1) {
            name = parts[0];
            if (parts.length >= 2) {
                try {
                    damage = Integer.parseInt(parts[1]);
                }
                catch (NumberFormatException ignored) {
                    // empty catch block
                }
            }
        }
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        try {
            int id = Integer.parseInt(name);
            Block block = Block.getBlock(id);
            if (block != null) {
                icon = new ItemStack(block, 1, damage);
            } else {
                Item item = Item.getItem(id);
                if (item != null) {
                    icon = new ItemStack(item, 1, damage);
                }
            }
        }
        catch (NumberFormatException ignored) {
            // Keep default when no numeric id is provided.
        }
        return icon != null ? icon : DEFAULT_ICON.copy();
    }

    public static ItemStack getIconFromNBT(NBTTagCompound tagCompound) {
        ItemStack icon = DEFAULT_ICON.copy();
        if (tagCompound != null && tagCompound.hasKey("Icon")) {
            ItemStack loaded = ItemStack.loadItemStackFromNBT(tagCompound.getCompoundTag("Icon"));
            if (loaded != null && loaded.getItem() != null) {
                icon = loaded;
            }
        }
        return icon;
    }

    public static ItemStack getIconFromFile(File file) {
        try {
            return SchematicUtil.getIconFromNBT(SchematicUtil.readTagCompoundFromFile(file));
        }
        catch (Exception e) {
            Reference.logger.error("Failed to read schematic icon!", (Throwable)e);
            return DEFAULT_ICON.copy();
        }
    }
}
