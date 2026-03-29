// Based on Schematica by Lunatrius (https://github.com/Lunatrius/Schematica)
// Licensed under the MIT License. See LICENSE file for details.

package com.github.lunatrius.schematica.api;

import java.util.List;
import net.minecraft.Block;
import net.minecraft.Entity;
import net.minecraft.ItemStack;
import net.minecraft.TileEntity;

public interface ISchematic {
    public Block getBlock(int var1, int var2, int var3);

    public boolean setBlock(int var1, int var2, int var3, Block var4);

    public boolean setBlock(int var1, int var2, int var3, Block var4, int var5);

    public TileEntity getTileEntity(int var1, int var2, int var3);

    public List<TileEntity> getTileEntities();

    public void setTileEntity(int var1, int var2, int var3, TileEntity var4);

    public void removeTileEntity(int var1, int var2, int var3);

    public int getBlockMetadata(int var1, int var2, int var3);

    public boolean setBlockMetadata(int var1, int var2, int var3, int var4);

    public List<Entity> getEntities();

    public void addEntity(Entity var1);

    public void removeEntity(Entity var1);

    public ItemStack getIcon();

    public void setIcon(ItemStack var1);

    public int getWidth();

    public int getLength();

    public int getHeight();
}

