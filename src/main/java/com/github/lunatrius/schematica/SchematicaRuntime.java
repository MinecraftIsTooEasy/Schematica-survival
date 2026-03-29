// MITE port (c) 2025 hahahha. Licensed under the MIT License.
package com.github.lunatrius.schematica;

import com.github.lunatrius.schematica.api.ISchematic;

public final class SchematicaRuntime {
    public static ISchematic loadedSchematic;
    public static String loadedSchematicName;
    public static int originX;
    public static int originY;
    public static int originZ;
    public static ISchematic lastUndoSchematic;
    public static int lastUndoOriginX;
    public static int lastUndoOriginY;
    public static int lastUndoOriginZ;
    public static String lastUndoLabel;
    public static boolean hasSelectionPos1;
    public static int selectionPos1X;
    public static int selectionPos1Y;
    public static int selectionPos1Z;
    public static boolean hasSelectionPos2;
    public static int selectionPos2X;
    public static int selectionPos2Y;
    public static int selectionPos2Z;

    private SchematicaRuntime() {
    }

    public static void setLoadedSchematic(ISchematic schematic, int x, int y, int z) {
        setLoadedSchematic(schematic, x, y, z, null);
    }

    public static void setLoadedSchematic(ISchematic schematic, int x, int y, int z, String schematicName) {
        loadedSchematic = schematic;
        loadedSchematicName = schematicName;
        originX = x;
        originY = y;
        originZ = z;
    }

    public static void clearLoadedSchematic() {
        loadedSchematic = null;
        loadedSchematicName = null;
    }

    public static boolean hasLoadedSchematic() {
        return loadedSchematic != null;
    }

    public static void setOrigin(int x, int y, int z) {
        originX = x;
        originY = y;
        originZ = z;
    }

    public static void setUndoSnapshot(ISchematic schematic, int x, int y, int z, String label) {
        lastUndoSchematic = schematic;
        lastUndoOriginX = x;
        lastUndoOriginY = y;
        lastUndoOriginZ = z;
        lastUndoLabel = label;
    }

    public static boolean hasUndoSnapshot() {
        return lastUndoSchematic != null;
    }

    public static void clearUndoSnapshot() {
        lastUndoSchematic = null;
        lastUndoLabel = null;
    }

    public static void setSelectionPos1(int x, int y, int z) {
        hasSelectionPos1 = true;
        selectionPos1X = x;
        selectionPos1Y = y;
        selectionPos1Z = z;
    }

    public static void setSelectionPos2(int x, int y, int z) {
        hasSelectionPos2 = true;
        selectionPos2X = x;
        selectionPos2Y = y;
        selectionPos2Z = z;
    }

    public static boolean hasSelection() {
        return hasSelectionPos1 && hasSelectionPos2;
    }

    public static void clearSelection() {
        hasSelectionPos1 = false;
        hasSelectionPos2 = false;
    }
}
