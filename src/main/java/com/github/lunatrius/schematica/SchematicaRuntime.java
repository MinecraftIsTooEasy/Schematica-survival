// MITE port (c) 2025 hahahha. Licensed under the MIT License.
package com.github.lunatrius.schematica;

import com.github.lunatrius.schematica.api.ISchematic;
import net.minecraft.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public static ISchematic lastPrinterUndoSchematic;
    public static int lastPrinterUndoOriginX;
    public static int lastPrinterUndoOriginY;
    public static int lastPrinterUndoOriginZ;
    public static String lastPrinterUndoLabel;
    public static ISchematic lastPrinterPrintedSchematic;
    public static int lastPrinterPrintedOriginX;
    public static int lastPrinterPrintedOriginY;
    public static int lastPrinterPrintedOriginZ;
    public static int lastPrinterPrintedPrinterX;
    public static int lastPrinterPrintedPrinterY;
    public static int lastPrinterPrintedPrinterZ;
    public static int lastPrinterUndoPrinterX;
    public static int lastPrinterUndoPrinterY;
    public static int lastPrinterUndoPrinterZ;
    public static final List<ItemStack> lastPrinterUndoRefundStacks = new ArrayList<ItemStack>();
    private static final Map<String, Map<String, Integer>> printerInventorySnapshotCountsByPos = new HashMap<String, Map<String, Integer>>();
    private static final Map<String, Long> printerInventorySnapshotUpdatedAtByPos = new HashMap<String, Long>();
    private static final long CLIENT_UPLOADED_PROJECTION_TTL_MS = 600_000L;
    private static final Map<Integer, UploadedPrinterProjection> uploadedPrinterProjectionByPlayerId = new HashMap<Integer, UploadedPrinterProjection>();
    private static boolean projectionAlertMarkerActive;
    private static int projectionAlertMarkerX;
    private static int projectionAlertMarkerY;
    private static int projectionAlertMarkerZ;
    private static long projectionAlertMarkerExpireAtMs;
    private static boolean projectionAlertMarkerRenderOnly;
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
        if (projectionAlertMarkerRenderOnly) {
            clearProjectionAlertMarker();
        }
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

    public static void setPrinterUndoSnapshot(ISchematic schematic, int originX, int originY, int originZ, String label, int printerX, int printerY, int printerZ) {
        setPrinterUndoSnapshot(schematic, originX, originY, originZ, label, printerX, printerY, printerZ, null);
    }

    public static void setPrinterUndoSnapshot(ISchematic schematic, int originX, int originY, int originZ, String label, int printerX, int printerY, int printerZ, List<ItemStack> refundStacks) {
        lastPrinterUndoSchematic = schematic;
        lastPrinterUndoOriginX = originX;
        lastPrinterUndoOriginY = originY;
        lastPrinterUndoOriginZ = originZ;
        lastPrinterUndoLabel = label;
        lastPrinterUndoPrinterX = printerX;
        lastPrinterUndoPrinterY = printerY;
        lastPrinterUndoPrinterZ = printerZ;
        lastPrinterUndoRefundStacks.clear();
        if (refundStacks != null) {
            for (ItemStack stack : refundStacks) {
                if (stack == null || stack.itemID <= 0 || stack.stackSize <= 0) {
                    continue;
                }
                lastPrinterUndoRefundStacks.add(stack.copy());
            }
        }
    }

    public static void setPrinterPrintedSnapshot(ISchematic schematic, int originX, int originY, int originZ, int printerX, int printerY, int printerZ) {
        lastPrinterPrintedSchematic = schematic;
        lastPrinterPrintedOriginX = originX;
        lastPrinterPrintedOriginY = originY;
        lastPrinterPrintedOriginZ = originZ;
        lastPrinterPrintedPrinterX = printerX;
        lastPrinterPrintedPrinterY = printerY;
        lastPrinterPrintedPrinterZ = printerZ;
    }

    public static boolean hasPrinterPrintedSnapshot() {
        return lastPrinterPrintedSchematic != null;
    }

    public static boolean hasPrinterPrintedSnapshotAt(int printerX, int printerY, int printerZ) {
        return hasPrinterPrintedSnapshot()
                && lastPrinterPrintedPrinterX == printerX
                && lastPrinterPrintedPrinterY == printerY
                && lastPrinterPrintedPrinterZ == printerZ;
    }

    public static void clearPrinterPrintedSnapshot() {
        lastPrinterPrintedSchematic = null;
    }

    public static boolean hasPrinterUndoSnapshot() {
        return lastPrinterUndoSchematic != null;
    }

    public static boolean hasPrinterUndoSnapshotAt(int printerX, int printerY, int printerZ) {
        return hasPrinterUndoSnapshot()
                && lastPrinterUndoPrinterX == printerX
                && lastPrinterUndoPrinterY == printerY
                && lastPrinterUndoPrinterZ == printerZ;
    }

    public static void clearPrinterUndoSnapshot() {
        lastPrinterUndoSchematic = null;
        lastPrinterUndoLabel = null;
        lastPrinterUndoRefundStacks.clear();
        clearPrinterPrintedSnapshot();
    }

    public static synchronized void setProjectionAlertMarker(int x, int y, int z, long ttlMs, boolean renderOnly) {
        projectionAlertMarkerActive = true;
        projectionAlertMarkerX = x;
        projectionAlertMarkerY = y;
        projectionAlertMarkerZ = z;
        long now = System.currentTimeMillis();
        projectionAlertMarkerExpireAtMs = now + Math.max(500L, ttlMs);
        projectionAlertMarkerRenderOnly = renderOnly;
    }

    public static synchronized boolean hasProjectionAlertMarker() {
        if (!projectionAlertMarkerActive) {
            return false;
        }
        if (System.currentTimeMillis() > projectionAlertMarkerExpireAtMs) {
            clearProjectionAlertMarker();
            return false;
        }
        return true;
    }

    public static synchronized int getProjectionAlertMarkerX() {
        return projectionAlertMarkerX;
    }

    public static synchronized int getProjectionAlertMarkerY() {
        return projectionAlertMarkerY;
    }

    public static synchronized int getProjectionAlertMarkerZ() {
        return projectionAlertMarkerZ;
    }

    public static synchronized boolean isProjectionAlertMarkerRenderOnly() {
        return hasProjectionAlertMarker() && projectionAlertMarkerRenderOnly;
    }

    public static synchronized void clearProjectionAlertMarker() {
        projectionAlertMarkerActive = false;
        projectionAlertMarkerRenderOnly = false;
        projectionAlertMarkerExpireAtMs = 0L;
    }

    public static synchronized void setPrinterInventorySnapshot(
            int printerX,
            int printerY,
            int printerZ,
            Map<String, Integer> counts,
            long updatedAtMs) {
        String key = printerPosKey(printerX, printerY, printerZ);
        Map<String, Integer> copy = new HashMap<String, Integer>();
        if (counts != null) {
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null) {
                    continue;
                }
                int amount = entry.getValue().intValue();
                if (amount <= 0) {
                    continue;
                }
                copy.put(entry.getKey(), Integer.valueOf(amount));
            }
        }
        if (copy.isEmpty()) {
            printerInventorySnapshotCountsByPos.remove(key);
        } else {
            printerInventorySnapshotCountsByPos.put(key, copy);
        }
        printerInventorySnapshotUpdatedAtByPos.put(key, Long.valueOf(Math.max(0L, updatedAtMs)));
    }

    public static synchronized Map<String, Integer> getPrinterInventorySnapshotCounts(int printerX, int printerY, int printerZ) {
        String key = printerPosKey(printerX, printerY, printerZ);
        Map<String, Integer> counts = printerInventorySnapshotCountsByPos.get(key);
        if (counts == null || counts.isEmpty()) {
            return null;
        }
        return new HashMap<String, Integer>(counts);
    }

    public static synchronized long getPrinterInventorySnapshotUpdatedAt(int printerX, int printerY, int printerZ) {
        String key = printerPosKey(printerX, printerY, printerZ);
        Long updatedAt = printerInventorySnapshotUpdatedAtByPos.get(key);
        return updatedAt == null ? 0L : updatedAt.longValue();
    }

    public static synchronized void clearPrinterInventorySnapshot(int printerX, int printerY, int printerZ) {
        String key = printerPosKey(printerX, printerY, printerZ);
        printerInventorySnapshotCountsByPos.remove(key);
        printerInventorySnapshotUpdatedAtByPos.remove(key);
    }

    public static synchronized void setUploadedPrinterProjection(
            int playerEntityId,
            ISchematic schematic,
            String schematicName,
            int uploadedOriginX,
            int uploadedOriginY,
            int uploadedOriginZ,
            long uploadedAtMs) {
        if (playerEntityId <= 0 || schematic == null) {
            return;
        }
        cleanupExpiredUploadedPrinterProjections(System.currentTimeMillis());
        uploadedPrinterProjectionByPlayerId.put(
                Integer.valueOf(playerEntityId),
                new UploadedPrinterProjection(
                        schematic,
                        schematicName,
                        uploadedOriginX,
                        uploadedOriginY,
                        uploadedOriginZ,
                        Math.max(0L, uploadedAtMs)));
    }

    public static synchronized UploadedPrinterProjection getUploadedPrinterProjection(int playerEntityId) {
        if (playerEntityId <= 0) {
            return null;
        }
        long now = System.currentTimeMillis();
        cleanupExpiredUploadedPrinterProjections(now);
        return uploadedPrinterProjectionByPlayerId.get(Integer.valueOf(playerEntityId));
    }

    public static synchronized void clearUploadedPrinterProjection(int playerEntityId) {
        if (playerEntityId <= 0) {
            return;
        }
        uploadedPrinterProjectionByPlayerId.remove(Integer.valueOf(playerEntityId));
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

    private static void cleanupExpiredUploadedPrinterProjections(long now) {
        List<Integer> expired = null;
        for (Map.Entry<Integer, UploadedPrinterProjection> entry : uploadedPrinterProjectionByPlayerId.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                if (expired == null) {
                    expired = new ArrayList<Integer>();
                }
                expired.add(entry == null ? null : entry.getKey());
                continue;
            }
            if (now - entry.getValue().uploadedAtMs > CLIENT_UPLOADED_PROJECTION_TTL_MS) {
                if (expired == null) {
                    expired = new ArrayList<Integer>();
                }
                expired.add(entry.getKey());
            }
        }
        if (expired == null || expired.isEmpty()) {
            return;
        }
        for (Integer key : expired) {
            if (key != null) {
                uploadedPrinterProjectionByPlayerId.remove(key);
            }
        }
    }

    private static String printerPosKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public static final class UploadedPrinterProjection {
        public final ISchematic schematic;
        public final String schematicName;
        public final int originX;
        public final int originY;
        public final int originZ;
        public final long uploadedAtMs;

        private UploadedPrinterProjection(
                ISchematic schematic,
                String schematicName,
                int originX,
                int originY,
                int originZ,
                long uploadedAtMs) {
            this.schematic = schematic;
            this.schematicName = schematicName;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.uploadedAtMs = uploadedAtMs;
        }
    }
}
