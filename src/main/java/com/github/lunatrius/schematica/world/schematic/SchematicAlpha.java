// Based on Schematica by Lunatrius (https://github.com/Lunatrius/Schematica)
// Licensed under the MIT License. See LICENSE file for details.
package com.github.lunatrius.schematica.world.schematic;

import com.github.lunatrius.schematica.SchematicaPrinterConfig;
import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.nbt.NBTHelper;
import com.github.lunatrius.schematica.reference.Reference;
import com.github.lunatrius.schematica.world.storage.Schematic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
    private static final Object ALIAS_LOOKUP_LOCK = new Object();
    private static volatile AliasLookup aliasLookup;

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
        long totalBlocksLong = (long)width * (long)length * (long)height;
        if (width <= 0 || length <= 0 || height <= 0 || totalBlocksLong <= 0L || totalBlocksLong > Integer.MAX_VALUE) {
            SchematicFormat.setLastReadError("invalid dimensions: " + width + "x" + height + "x" + length);
            Reference.logger.error(
                    "Invalid schematic dimensions: width={}, length={}, height={}, volume={}",
                    Integer.valueOf(width),
                    Integer.valueOf(length),
                    Integer.valueOf(height),
                    Long.valueOf(totalBlocksLong));
            return null;
        }
        int totalBlocks = (int)totalBlocksLong;
        if (localBlocks == null || localBlocks.length < totalBlocks) {
            SchematicFormat.setLastReadError("invalid Blocks length: actual=" + (localBlocks == null ? 0 : localBlocks.length) + ", expectedAtLeast=" + totalBlocks);
            Reference.logger.error(
                    "Invalid schematic Blocks array length: actual={}, expectedAtLeast={}",
                    Integer.valueOf(localBlocks == null ? 0 : localBlocks.length),
                    Integer.valueOf(totalBlocks));
            return null;
        }
        if (localMetadata == null || localMetadata.length < totalBlocks) {
            SchematicFormat.setLastReadError("invalid Data length: actual=" + (localMetadata == null ? 0 : localMetadata.length) + ", expectedAtLeast=" + totalBlocks);
            Reference.logger.error(
                    "Invalid schematic Data array length: actual={}, expectedAtLeast={}",
                    Integer.valueOf(localMetadata == null ? 0 : localMetadata.length),
                    Integer.valueOf(totalBlocks));
            return null;
        }
        if (extra && (extraBlocks == null || extraBlocks.length < totalBlocks)) {
            SchematicFormat.setLastReadError("invalid Add/AddBlocks length: actual=" + (extraBlocks == null ? 0 : extraBlocks.length) + ", expectedAtLeast=" + totalBlocks);
            Reference.logger.error(
                    "Invalid schematic Add/AddBlocks array length: actual={}, expectedAtLeast={}",
                    Integer.valueOf(extraBlocks == null ? 0 : extraBlocks.length),
                    Integer.valueOf(totalBlocks));
            return null;
        }

        boolean hasMapping = tagCompound.hasKey("SchematicaMapping");
        HashMap<Short, Short> oldToNew = new HashMap<Short, Short>();
        Set<Short> unresolvedMappedIds = new HashSet<Short>();
        Map<Short, List<String>> aliasesByMappedId = new HashMap<Short, List<String>>();
        if (hasMapping) {
            NBTTagCompound mapping = tagCompound.getCompoundTag("SchematicaMapping");
            Map<Short, RemapChoice> bestChoices = new HashMap<Short, RemapChoice>();
            Set<Short> seenMappedIds = new HashSet<Short>();
            for (Object object : mapping.getTags()) {
                if (!(object instanceof NBTBase)) {
                    continue;
                }
                NBTBase base = (NBTBase)object;
                String name = base.getName();
                short oldId = mapping.getShort(name);
                seenMappedIds.add(oldId);
                List<String> aliases = aliasesByMappedId.get(oldId);
                if (aliases == null) {
                    aliases = new ArrayList<String>();
                    aliasesByMappedId.put(oldId, aliases);
                }
                if (name != null && !name.isEmpty() && aliases.size() < 8) {
                    aliases.add(name);
                }
                int mappedId = resolveBlockId(name, oldId);
                if (mappedId >= 0) {
                    int priority = mappingAliasPriority(name);
                    RemapChoice choice = bestChoices.get(oldId);
                    if (choice == null
                            || priority > choice.priority
                            || (priority == choice.priority && mappedId < choice.blockId)) {
                        bestChoices.put(oldId, new RemapChoice((short)mappedId, priority));
                    }
                }
            }

            for (Map.Entry<Short, RemapChoice> entry : bestChoices.entrySet()) {
                oldToNew.put(entry.getKey(), entry.getValue().blockId);
            }

            for (Short oldId : seenMappedIds) {
                if (!oldToNew.containsKey(oldId)) {
                    unresolvedMappedIds.add(oldId);
                }
            }
        }
        if (hasMapping && !unresolvedMappedIds.isEmpty()) {
            boolean strictRemap = SchematicaPrinterConfig.isSchematicStrictRemapEnabled();
            if (strictRemap) {
                SchematicFormat.setLastReadError("strict remap rejected unresolved mapped ids: " + unresolvedMappedIds.size());
                Reference.logger.error(
                        "Strict schematic remap enabled; unresolved mapped IDs={}, examples={}",
                        Integer.valueOf(unresolvedMappedIds.size()),
                        formatUnresolvedMappingDetails(unresolvedMappedIds, aliasesByMappedId));
                return null;
            }
            Reference.logger.warn(
                    "Schematic has unresolved mapped IDs={}, examples={}",
                    Integer.valueOf(unresolvedMappedIds.size()),
                    formatUnresolvedMappingDetails(unresolvedMappedIds, aliasesByMappedId));
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
            Reference.logger.warn(
                    "Schematic remap replaced {} block positions with air due to unresolved mapped IDs={}.",
                    Integer.valueOf(replacedWithAir),
                    Integer.valueOf(unresolvedMappedIds.size()));
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

    private static int resolveBlockId(String name, short preferredOldId) {
        if (name == null || name.isEmpty()) {
            return -1;
        }
        String raw = name.trim();
        if (raw.isEmpty()) {
            return -1;
        }
        if (isAirAlias(raw)) {
            return 0;
        }
        AliasLookup lookup = getAliasLookup();
        Short matchedId = lookup.aliasToBlockId.get(raw);
        if (matchedId == null) {
            String canonical = canonicalName(raw);
            if (!canonical.isEmpty()) {
                matchedId = lookup.canonicalAliasToBlockId.get(canonical);
            }
        }
        if (matchedId == null) {
            return resolvePreferredLegacyId(raw, preferredOldId);
        }
        if (matchedId.shortValue() >= 0) {
            return matchedId.shortValue() & 0xFFFF;
        }
        // Ambiguous alias: try conservative preferred-ID fallback for stair/slab families.
        return resolvePreferredLegacyId(raw, preferredOldId);
    }

    private static void addMappingAliases(Map<String, Short> mappings, Block block, short blockId) {
        if (block == null || blockId == 0) {
            putMappingAlias(mappings, "air", (short)0);
            putMappingAlias(mappings, "tile.air", (short)0);
            putMappingAlias(mappings, "minecraft:air", (short)0);
            return;
        }

        Set<String> aliases = collectBlockAliases(block, blockId & 0xFFFF);
        for (String alias : aliases) {
            putMappingAlias(mappings, alias, blockId);
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

    private static AliasLookup getAliasLookup() {
        AliasLookup lookup = aliasLookup;
        int currentSize = Block.blocksList.length;
        if (lookup != null && lookup.registrySize == currentSize) {
            return lookup;
        }
        synchronized (ALIAS_LOOKUP_LOCK) {
            lookup = aliasLookup;
            if (lookup != null && lookup.registrySize == currentSize) {
                return lookup;
            }
            lookup = buildAliasLookup(currentSize);
            aliasLookup = lookup;
            return lookup;
        }
    }

    private static AliasLookup buildAliasLookup(int registrySize) {
        Map<String, Short> aliasToBlockId = new HashMap<String, Short>();
        Map<String, Short> canonicalAliasToBlockId = new HashMap<String, Short>();
        putAlias(aliasToBlockId, canonicalAliasToBlockId, "air", (short)0);
        putAlias(aliasToBlockId, canonicalAliasToBlockId, "tile.air", (short)0);
        putAlias(aliasToBlockId, canonicalAliasToBlockId, "minecraft:air", (short)0);
        for (int id = 0; id < Block.blocksList.length; ++id) {
            Block block = Block.getBlock(id);
            if (block == null) {
                continue;
            }
            Set<String> aliases = collectBlockAliases(block, id);
            for (String alias : aliases) {
                putAlias(aliasToBlockId, canonicalAliasToBlockId, alias, (short)id);
            }
        }
        return new AliasLookup(aliasToBlockId, canonicalAliasToBlockId, registrySize);
    }

    private static void putAlias(Map<String, Short> aliasToBlockId, Map<String, Short> canonicalAliasToBlockId, String alias, short blockId) {
        if (alias == null || alias.isEmpty()) {
            return;
        }
        putAliasKey(aliasToBlockId, alias, blockId);
        String canonical = canonicalName(alias);
        if (!canonical.isEmpty()) {
            putAliasKey(canonicalAliasToBlockId, canonical, blockId);
        }
    }

    private static void putAliasKey(Map<String, Short> target, String key, short blockId) {
        Short existing = target.get(key);
        if (existing == null) {
            target.put(key, Short.valueOf(blockId));
            return;
        }
        if (existing.shortValue() != blockId) {
            // Ambiguous alias: mark as unresolved.
            target.put(key, Short.valueOf((short)-1));
        }
    }

    private static Set<String> collectBlockAliases(Block block, int blockId) {
        LinkedHashSet<String> aliases = new LinkedHashSet<String>();
        String unlocalized = block.getUnlocalizedName();
        addAliasVariants(aliases, unlocalized);
        addAliasVariants(aliases, stripTilePrefix(unlocalized));

        String textureName = tryReadTextureName(block);
        addAliasVariants(aliases, textureName);
        if (textureName != null) {
            int colon = textureName.indexOf(':');
            if (colon >= 0 && colon + 1 < textureName.length()) {
                String path = textureName.substring(colon + 1);
                addAliasVariants(aliases, path);
                if (path.startsWith("blocks/")) {
                    addAliasVariants(aliases, path.substring("blocks/".length()));
                }
                int slash = path.lastIndexOf('/');
                if (slash >= 0 && slash + 1 < path.length()) {
                    addAliasVariants(aliases, path.substring(slash + 1));
                }
            }
        }

        String stripped = stripTilePrefix(unlocalized);
        if (isLikelyVanillaBlock(block, blockId) && stripped != null && !stripped.isEmpty() && stripped.indexOf(':') < 0) {
            addAliasVariants(aliases, "minecraft:" + stripped);
        }
        return aliases;
    }

    private static void addAliasVariants(Set<String> aliases, String raw) {
        if (raw == null) {
            return;
        }
        String alias = raw.trim();
        if (alias.isEmpty()) {
            return;
        }
        aliases.add(alias);

        String lower = alias.toLowerCase(java.util.Locale.ROOT);
        aliases.add(lower);

        String stripped = stripTilePrefix(alias);
        if (stripped != null && !stripped.isEmpty()) {
            aliases.add(stripped);
            aliases.add(stripped.toLowerCase(java.util.Locale.ROOT));
        }

        int dot = lower.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < lower.length()) {
            aliases.add(lower.substring(dot + 1));
        }
    }

    private static String tryReadTextureName(Block block) {
        try {
            java.lang.reflect.Method method = block.getClass().getMethod("getTextureName");
            Object result = method.invoke(block);
            if (result instanceof String) {
                String value = ((String)result).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
        }

        String[] fieldNames = new String[]{"textureName", "field_111026_f"};
        Class<?> type = block.getClass();
        while (type != null && type != Object.class) {
            for (String fieldName : fieldNames) {
                try {
                    java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(block);
                    if (value instanceof String) {
                        String text = ((String)value).trim();
                        if (!text.isEmpty()) {
                            return text;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean isLikelyVanillaBlock(Block block, int blockId) {
        if (blockId >= 0 && blockId < 256) {
            return true;
        }
        Package blockPackage = block.getClass().getPackage();
        String packageName = blockPackage == null ? "" : blockPackage.getName();
        return packageName.startsWith("net.minecraft");
    }

    private static boolean isAirAlias(String name) {
        return "air".equals(name) || "tile.air".equals(name) || "minecraft:air".equals(name);
    }

    private static int resolvePreferredLegacyId(String alias, short preferredOldId) {
        int preferredId = preferredOldId & 0xFFFF;
        Block preferred = Block.getBlock(preferredId);
        if (preferred == null) {
            return -1;
        }
        String aliasCanonical = canonicalName(alias);
        int aliasFamily = detectFamily(alias);
        String unlocalized = preferred.getUnlocalizedName();
        String preferredCanonical = canonicalName(unlocalized);
        if (!aliasCanonical.isEmpty() && !preferredCanonical.isEmpty()) {
            if (aliasCanonical.equals(preferredCanonical)
                    || (aliasCanonical.length() >= 6 && preferredCanonical.contains(aliasCanonical))
                    || (preferredCanonical.length() >= 6 && aliasCanonical.contains(preferredCanonical))) {
                return preferredId;
            }
            if (hasStrongTokenOverlap(aliasCanonical, preferredCanonical)) {
                return preferredId;
            }
        }
        if (aliasFamily == 0) {
            return -1;
        }
        int preferredFamily = detectFamily(unlocalized);
        if (preferredFamily != aliasFamily) {
            return -1;
        }
        return preferredId;
    }

    private static int detectFamily(String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("stair")) {
            return 1;
        }
        if (lower.contains("slab") || lower.contains("step")) {
            return 2;
        }
        if (lower.contains("button")) {
            return 3;
        }
        if (lower.contains("pressureplate") || lower.contains("pressure_plate")) {
            return 4;
        }
        if (lower.contains("trapdoor")) {
            return 5;
        }
        if (lower.contains("door")) {
            return 6;
        }
        if (lower.contains("fencegate") || lower.contains("fence_gate") || lower.contains("gate")) {
            return 7;
        }
        if (lower.contains("fence")) {
            return 8;
        }
        if (lower.contains("wall")) {
            return 9;
        }
        if (lower.contains("pane")) {
            return 10;
        }
        return 0;
    }

    private static String canonicalName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String normalized = stripTilePrefix(name.trim().toLowerCase(java.util.Locale.ROOT));
        if (normalized.isEmpty()) {
            return "";
        }
        int namespace = normalized.indexOf(':');
        if (namespace >= 0 && namespace + 1 < normalized.length()) {
            normalized = normalized.substring(namespace + 1);
        }
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); ++i) {
            char ch = normalized.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static boolean hasStrongTokenOverlap(String leftCanonical, String rightCanonical) {
        String[] leftTokens = splitCanonicalTokens(leftCanonical);
        String[] rightTokens = splitCanonicalTokens(rightCanonical);
        if (leftTokens.length < 2 || rightTokens.length < 2) {
            return false;
        }
        int matches = 0;
        for (String left : leftTokens) {
            if (left.isEmpty()) {
                continue;
            }
            for (String right : rightTokens) {
                if (left.equals(right)) {
                    ++matches;
                    break;
                }
            }
        }
        int min = Math.min(leftTokens.length, rightTokens.length);
        return matches >= 2 && matches * 2 >= min + 1;
    }

    private static String[] splitCanonicalTokens(String canonical) {
        if (canonical == null || canonical.isEmpty()) {
            return new String[0];
        }
        // Tokenize known block-name vocabulary boundaries for robust cross-mod alias matching.
        String split = canonical
                .replace("pressureplate", "pressure plate")
                .replace("fencegate", "fence gate")
                .replace("polished", " polished ")
                .replace("deepslate", " deepslate ")
                .replace("cobblestone", " cobblestone ")
                .replace("stone", " stone ")
                .replace("brick", " brick ")
                .replace("button", " button ")
                .replace("stairs", " stairs ")
                .replace("stair", " stair ")
                .replace("slabs", " slabs ")
                .replace("slab", " slab ")
                .replace("step", " step ");
        String[] raw = split.trim().split("\\s+");
        List<String> tokens = new ArrayList<String>(raw.length);
        for (String token : raw) {
            if (token != null && !token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens.toArray(new String[tokens.size()]);
    }

    private static int mappingAliasPriority(String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        if (name.startsWith("tile.")) {
            return 3;
        }
        if (name.indexOf(':') >= 0) {
            return 2;
        }
        return 1;
    }

    private static String formatUnresolvedMappingDetails(Set<Short> unresolvedMappedIds, Map<Short, List<String>> aliasesByMappedId) {
        if (unresolvedMappedIds == null || unresolvedMappedIds.isEmpty()) {
            return "[]";
        }
        List<Integer> sortedIds = new ArrayList<Integer>(unresolvedMappedIds.size());
        for (Short oldId : unresolvedMappedIds) {
            if (oldId != null) {
                sortedIds.add(Integer.valueOf(oldId.intValue() & 0xFFFF));
            }
        }
        java.util.Collections.sort(sortedIds);
        StringBuilder builder = new StringBuilder("[");
        int count = 0;
        for (Integer unsignedId : sortedIds) {
            if (count > 0) {
                builder.append(", ");
            }
            builder.append(unsignedId.intValue()).append('=');
            List<String> aliases = aliasesByMappedId == null ? null : aliasesByMappedId.get(Short.valueOf((short)(unsignedId.intValue() & 0xFFFF)));
            if (aliases == null || aliases.isEmpty()) {
                builder.append("<no-alias>");
            } else {
                builder.append(aliases.toString());
            }
            ++count;
            if (count >= 6) {
                break;
            }
        }
        if (unresolvedMappedIds.size() > count) {
            builder.append(", ...");
        }
        builder.append(']');
        return builder.toString();
    }

    private static final class AliasLookup {
        private final Map<String, Short> aliasToBlockId;
        private final Map<String, Short> canonicalAliasToBlockId;
        private final int registrySize;

        private AliasLookup(Map<String, Short> aliasToBlockId, Map<String, Short> canonicalAliasToBlockId, int registrySize) {
            this.aliasToBlockId = aliasToBlockId;
            this.canonicalAliasToBlockId = canonicalAliasToBlockId;
            this.registrySize = registrySize;
        }
    }

    private static final class RemapChoice {
        private final short blockId;
        private final int priority;

        private RemapChoice(short blockId, int priority) {
            this.blockId = blockId;
            this.priority = priority;
        }
    }
}
