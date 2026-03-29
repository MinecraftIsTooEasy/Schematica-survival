// Based on Schematica by Lunatrius (https://github.com/Lunatrius/Schematica)
// Licensed under the MIT License. See LICENSE file for details.
package com.github.lunatrius.schematica.world.schematic;

import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.nbt.NBTHelper;
import com.github.lunatrius.schematica.reference.Reference;
import com.github.lunatrius.schematica.world.storage.Schematic;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.Block;
import net.minecraft.Entity;
import net.minecraft.ItemStack;
import net.minecraft.NBTBase;
import net.minecraft.NBTTagCompound;
import net.minecraft.NBTTagList;
import net.minecraft.TileEntity;

public class SchematicAlpha extends SchematicFormat {
    @Override
    public ISchematic readFromNBT(NBTTagCompound tagCompound) {
        ItemStack icon = SchematicUtil.getIconFromNBT(tagCompound);
        byte[] localBlocks = tagCompound.getByteArray("Blocks");
        byte[] localMetadata = tagCompound.getByteArray("Data");
        boolean extra = false;
        byte[] extraBlocks = null;
        byte[] extraBlocksNibble = null;
        if (tagCompound.hasKey("AddBlocks")) {
            extra = true;
            extraBlocksNibble = tagCompound.getByteArray("AddBlocks");
            extraBlocks = new byte[extraBlocksNibble.length * 2];
            for (int i = 0; i < extraBlocksNibble.length; ++i) {
                extraBlocks[i * 2] = (byte)(extraBlocksNibble[i] >> 4 & 0xF);
                extraBlocks[i * 2 + 1] = (byte)(extraBlocksNibble[i] & 0xF);
            }
        } else if (tagCompound.hasKey("Add")) {
            extra = true;
            extraBlocks = tagCompound.getByteArray("Add");
        }

        int width = tagCompound.getShort("Width");
        int length = tagCompound.getShort("Length");
        int height = tagCompound.getShort("Height");

        boolean hasMapping = tagCompound.hasKey("SchematicaMapping");
        HashMap<Short, Short> oldToNew = new HashMap<Short, Short>();
        Set<Short> unresolvedMappedIds = new HashSet<Short>();
        if (hasMapping) {
            NBTTagCompound mapping = tagCompound.getCompoundTag("SchematicaMapping");
            for (Object object : mapping.getTags()) {
                if (!(object instanceof NBTBase)) {
                    continue;
                }
                NBTBase base = (NBTBase)object;
                String name = base.getName();
                short oldId = mapping.getShort(name);
                int mappedId = resolveBlockId(name);
                if (mappedId >= 0) {
                    oldToNew.put(oldId, (short)mappedId);
                    unresolvedMappedIds.remove(oldId);
                } else if (!oldToNew.containsKey(oldId)) {
                    unresolvedMappedIds.add(oldId);
                }
            }
        }

        int replacedWithAir = 0;
        Schematic schematic = new Schematic(icon, width, height, length);
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                for (int z = 0; z < length; ++z) {
                    int index = x + (y * length + z) * width;
                    int blockID = localBlocks[index] & 0xFF | (extra ? (extraBlocks[index] & 0xFF) << 8 : 0);
                    int meta = localMetadata[index] & 0xFF;
                    short oldId = (short)blockID;
                    Short id = oldToNew.get(oldId);
                    if (id != null) {
                        blockID = id;
                    } else if (unresolvedMappedIds.contains(oldId)) {
                        // Name mapping failed for this source ID. Avoid cross-mod ID mismatch by forcing air.
                        blockID = 0;
                        ++replacedWithAir;
                    }
                    schematic.setBlock(x, y, z, Block.getBlock(blockID), meta);
                }
            }
        }

        if (hasMapping && replacedWithAir > 0) {
            Reference.logger.warn("Schematic remap replaced {} block positions with air because mapped block names were not found.", replacedWithAir);
        }

        NBTTagList tileEntitiesList = tagCompound.getTagList("TileEntities");
        for (int i = 0; i < tileEntitiesList.tagCount(); ++i) {
            try {
                NBTBase base = tileEntitiesList.tagAt(i);
                if (!(base instanceof NBTTagCompound)) {
                    continue;
                }
                TileEntity tileEntity = NBTHelper.readTileEntityFromCompound((NBTTagCompound)base);
                if (tileEntity == null) {
                    continue;
                }
                schematic.setTileEntity(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, tileEntity);
            } catch (Exception e) {
                Reference.logger.error("TileEntity failed to load properly!", e);
            }
        }

        NBTTagList entitiesList = tagCompound.getTagList("Entities");
        if (entitiesList.tagCount() > 0) {
            for (int i = 0; i < entitiesList.tagCount(); ++i) {
                NBTBase base = entitiesList.tagAt(i);
                if (!(base instanceof NBTTagCompound)) {
                    continue;
                }
                schematic.addEntityTag((NBTTagCompound)((NBTTagCompound)base).copy());
            }
        }

        return schematic;
    }

    @Override
    public boolean writeToNBT(NBTTagCompound tagCompound, ISchematic schematic) {
        NBTTagCompound tagCompoundIcon = new NBTTagCompound();
        ItemStack icon = schematic.getIcon();
        icon.writeToNBT(tagCompoundIcon);
        tagCompound.setTag("Icon", tagCompoundIcon);
        tagCompound.setShort("Width", (short)schematic.getWidth());
        tagCompound.setShort("Length", (short)schematic.getLength());
        tagCompound.setShort("Height", (short)schematic.getHeight());

        int size = schematic.getWidth() * schematic.getLength() * schematic.getHeight();
        byte[] localBlocks = new byte[size];
        byte[] localMetadata = new byte[size];
        byte[] extraBlocks = new byte[size];
        byte[] extraBlocksNibble = new byte[(int)Math.ceil((double)size / 2.0)];
        boolean extra = false;
        HashMap<String, Short> mappings = new HashMap<String, Short>();

        for (int x = 0; x < schematic.getWidth(); ++x) {
            for (int y = 0; y < schematic.getHeight(); ++y) {
                for (int z = 0; z < schematic.getLength(); ++z) {
                    int index = x + (y * schematic.getLength() + z) * schematic.getWidth();
                    Block block = schematic.getBlock(x, y, z);
                    int blockId = block != null ? block.blockID : 0;
                    localBlocks[index] = (byte)blockId;
                    localMetadata[index] = (byte)schematic.getBlockMetadata(x, y, z);
                    extraBlocks[index] = (byte)(blockId >> 8);
                    if (extraBlocks[index] > 0) {
                        extra = true;
                    }
                    addMappingAliases(mappings, block, (short)blockId);
                }
            }
        }

        int count = 20;
        NBTTagList tileEntitiesList = new NBTTagList();
        for (TileEntity tileEntity : schematic.getTileEntities()) {
            try {
                NBTTagCompound tileEntityTagCompound = NBTHelper.writeTileEntityToCompound(tileEntity);
                tileEntitiesList.appendTag(tileEntityTagCompound);
            } catch (Exception e) {
                int pos = tileEntity.xCoord + (tileEntity.yCoord * schematic.getLength() + tileEntity.zCoord) * schematic.getWidth();
                if (--count > 0) {
                    Block block = schematic.getBlock(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
                    Reference.logger.error("TileEntity failed to save, replacing with bedrock at {},{},{}", tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, e);
                    if (block != null) {
                        Reference.logger.error("Original block was {}", block);
                    }
                }
                localBlocks[pos] = (byte)Block.bedrock.blockID;
                localMetadata[pos] = 0;
                extraBlocks[pos] = 0;
            }
        }

        for (int i = 0; i < extraBlocksNibble.length; ++i) {
            extraBlocksNibble[i] = i * 2 + 1 < extraBlocks.length ? (byte)(extraBlocks[i * 2] << 4 | extraBlocks[i * 2 + 1]) : (byte)(extraBlocks[i * 2] << 4);
        }

        NBTTagList entityList = new NBTTagList();
        if (schematic instanceof Schematic) {
            List<NBTTagCompound> entityTags = ((Schematic)schematic).getEntityTags();
            for (NBTTagCompound entityTag : entityTags) {
                if (entityTag == null) {
                    continue;
                }
                entityList.appendTag(entityTag.copy());
            }
        }

        if (entityList.tagCount() == 0) {
            List<Entity> entities = schematic.getEntities();
            for (Entity entity : entities) {
                try {
                    NBTTagCompound entityCompound = NBTHelper.writeEntityToCompound(entity);
                    if (entityCompound != null) {
                        entityList.appendTag(entityCompound);
                    }
                } catch (Throwable t) {
                    Reference.logger.error("Entity {} failed to save, skipping!", entity, t);
                }
            }
        }

        NBTTagCompound nbtMapping = new NBTTagCompound();
        for (Map.Entry<String, Short> entry : mappings.entrySet()) {
            nbtMapping.setShort(entry.getKey(), entry.getValue());
        }

        tagCompound.setString("Materials", "Alpha");
        tagCompound.setByteArray("Blocks", localBlocks);
        tagCompound.setByteArray("Data", localMetadata);
        if (extra) {
            tagCompound.setByteArray("AddBlocks", extraBlocksNibble);
        }
        tagCompound.setTag("Entities", entityList);
        tagCompound.setTag("TileEntities", tileEntitiesList);
        tagCompound.setTag("SchematicaMapping", nbtMapping);
        return true;
    }

    private static int resolveBlockId(String name) {
        if (name == null || name.isEmpty()) {
            return -1;
        }
        String raw = stripTilePrefix(name);
        if ("air".equals(raw) || "minecraft:air".equals(raw)) {
            return 0;
        }
        if (raw.startsWith("minecraft:")) {
            raw = raw.substring("minecraft:".length());
        }

        for (int id = 0; id < Block.blocksList.length; ++id) {
            Block block = Block.getBlock(id);
            if (block == null) {
                continue;
            }
            String unlocalized = block.getUnlocalizedName();
            String stripped = stripTilePrefix(unlocalized);
            if (raw.equals(unlocalized) || raw.equals(stripped)) {
                return id;
            }
        }
        return -1;
    }

    private static void addMappingAliases(Map<String, Short> mappings, Block block, short blockId) {
        if (block == null || blockId == 0) {
            putMappingAlias(mappings, "air", (short)0);
            putMappingAlias(mappings, "tile.air", (short)0);
            putMappingAlias(mappings, "minecraft:air", (short)0);
            return;
        }

        String unlocalized = block.getUnlocalizedName();
        String stripped = stripTilePrefix(unlocalized);
        putMappingAlias(mappings, unlocalized, blockId);
        putMappingAlias(mappings, stripped, blockId);
        if (stripped != null && !stripped.isEmpty() && stripped.indexOf(':') < 0) {
            putMappingAlias(mappings, "minecraft:" + stripped, blockId);
        }
    }

    private static void putMappingAlias(Map<String, Short> mappings, String name, short blockId) {
        if (name == null || name.isEmpty() || mappings.containsKey(name)) {
            return;
        }
        mappings.put(name, blockId);
    }

    private static String stripTilePrefix(String name) {
        if (name == null) {
            return null;
        }
        return name.startsWith("tile.") ? name.substring("tile.".length()) : name;
    }
}
