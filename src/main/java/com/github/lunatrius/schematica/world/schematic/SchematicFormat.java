// Based on Schematica by Lunatrius (https://github.com/Lunatrius/Schematica)
// Licensed under the MIT License. See LICENSE file for details.
package com.github.lunatrius.schematica.world.schematic;

import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.reference.Reference;
import com.github.lunatrius.schematica.world.schematic.SchematicAlpha;
import com.github.lunatrius.schematica.world.schematic.SchematicUtil;
import com.github.lunatrius.schematica.world.schematic.UnsupportedFormatException;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.CompressedStreamTools;
import net.minecraft.NBTTagCompound;

public abstract class SchematicFormat {
    public static final Map<String, SchematicFormat> FORMATS = new HashMap<String, SchematicFormat>();
    public static String FORMAT_DEFAULT;

    public abstract ISchematic readFromNBT(NBTTagCompound var1);

    public abstract boolean writeToNBT(NBTTagCompound var1, ISchematic var2);

    public static ISchematic readFromFile(File file) {
        try {
            NBTTagCompound tagCompound = SchematicUtil.readTagCompoundFromFile(file);
            String format = tagCompound.getString("Materials");
            SchematicFormat schematicFormat = FORMATS.get(format);
            if (schematicFormat == null) {
                throw new UnsupportedFormatException(format);
            }
            return schematicFormat.readFromNBT(tagCompound);
        }
        catch (Exception ex) {
            Reference.logger.error("Failed to read schematic!", (Throwable)ex);
            return null;
        }
    }

    public static ISchematic readFromFile(File directory, String filename) {
        return SchematicFormat.readFromFile(new File(directory, filename));
    }

    public static boolean writeToFile(File file, ISchematic schematic) {
        try {
            NBTTagCompound tagCompound = new NBTTagCompound();
            FORMATS.get(FORMAT_DEFAULT).writeToNBT(tagCompound, schematic);
            CompressedStreamTools.writeCompressed(tagCompound, new FileOutputStream(file));
            return true;
        }
        catch (Exception ex) {
            Reference.logger.error("Failed to write schematic!", (Throwable)ex);
            return false;
        }
    }

    public static boolean writeToFile(File directory, String filename, ISchematic schematic) {
        return SchematicFormat.writeToFile(new File(directory, filename), schematic);
    }

    static {
        FORMATS.put("Alpha", new SchematicAlpha());
        FORMAT_DEFAULT = "Alpha";
    }
}
