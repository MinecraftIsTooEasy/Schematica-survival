// Based on Schematica by Lunatrius (https://github.com/Lunatrius/Schematica)
// Licensed under the MIT License. See LICENSE file for details.
package com.github.lunatrius.schematica.world.storage;

import com.github.lunatrius.schematica.api.ISchematic;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.Block;
import net.minecraft.Entity;
import net.minecraft.EntityPlayer;
import net.minecraft.ItemStack;
import net.minecraft.NBTTagCompound;
import net.minecraft.TileEntity;

public class Schematic
implements ISchematic {
    private static final ItemStack DEFAULT_ICON = new ItemStack((Block)Block.stone);
    private ItemStack icon;
    private final short[][][] blocks;
    private final byte[][][] metadata;
    private final List<TileEntity> tileEntities = new ArrayList<TileEntity>();
    private final List<Entity> entities = new ArrayList<Entity>();
    private final List<NBTTagCompound> entityTags = new ArrayList<NBTTagCompound>();
    private final int width;
    private final int height;
    private final int length;

    public Schematic(ItemStack icon, int width, int height, int length) {
        this.icon = icon;
        this.blocks = new short[width][height][length];
        this.metadata = new byte[width][height][length];
        this.width = width;
        this.height = height;
        this.length = length;
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        if (!this.isValid(x, y, z)) {
            return null;
        }
        return Block.getBlock((int)this.blocks[x][y][z]);
    }

    @Override
    public boolean setBlock(int x, int y, int z, Block block) {
        return this.setBlock(x, y, z, block, 0);
    }

    @Override
    public boolean setBlock(int x, int y, int z, Block block, int metadata) {
        if (!this.isValid(x, y, z)) {
            return false;
        }
        this.blocks[x][y][z] = (short)(block != null ? block.blockID : 0);
        this.setBlockMetadata(x, y, z, metadata);
        return true;
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        for (TileEntity tileEntity : this.tileEntities) {
            if (tileEntity.xCoord != x || tileEntity.yCoord != y || tileEntity.zCoord != z) continue;
            return tileEntity;
        }
        return null;
    }

    @Override
    public List<TileEntity> getTileEntities() {
        return this.tileEntities;
    }

    @Override
    public void setTileEntity(int x, int y, int z, TileEntity tileEntity) {
        if (!this.isValid(x, y, z)) {
            return;
        }
        this.removeTileEntity(x, y, z);
        if (tileEntity != null) {
            this.tileEntities.add(tileEntity);
        }
    }

    @Override
    public void removeTileEntity(int x, int y, int z) {
        Iterator<TileEntity> iterator = this.tileEntities.iterator();
        while (iterator.hasNext()) {
            TileEntity tileEntity = iterator.next();
            if (tileEntity.xCoord != x || tileEntity.yCoord != y || tileEntity.zCoord != z) continue;
            iterator.remove();
        }
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        if (!this.isValid(x, y, z)) {
            return 0;
        }
        return this.metadata[x][y][z];
    }

    @Override
    public boolean setBlockMetadata(int x, int y, int z, int metadata) {
        if (!this.isValid(x, y, z)) {
            return false;
        }
        this.metadata[x][y][z] = (byte)(metadata & 0xF);
        return true;
    }

    @Override
    public List<Entity> getEntities() {
        return this.entities;
    }

    public List<NBTTagCompound> getEntityTags() {
        return this.entityTags;
    }

    public void addEntityTag(NBTTagCompound entityTag) {
        if (entityTag == null) {
            return;
        }
        this.entityTags.add((NBTTagCompound)entityTag.copy());
    }

    public void clearEntityTags() {
        this.entityTags.clear();
    }

    @Override
    public void addEntity(Entity entity) {
        if (entity == null || entity.getUniqueID() == null || entity instanceof EntityPlayer) {
            return;
        }
        for (Entity e : this.entities) {
            if (!entity.getUniqueID().equals(e.getUniqueID())) continue;
            return;
        }
        this.entities.add(entity);
    }

    @Override
    public void removeEntity(Entity entity) {
        if (entity == null || entity.getUniqueID() == null) {
            return;
        }
        Iterator<Entity> iterator = this.entities.iterator();
        while (iterator.hasNext()) {
            Entity e = iterator.next();
            if (!entity.getUniqueID().equals(e.getUniqueID())) continue;
            iterator.remove();
        }
    }

    @Override
    public ItemStack getIcon() {
        return this.icon;
    }

    @Override
    public void setIcon(ItemStack icon) {
        this.icon = icon != null ? icon : DEFAULT_ICON.copy();
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getLength() {
        return this.length;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    private boolean isValid(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < this.width && y < this.height && z < this.length;
    }
}
