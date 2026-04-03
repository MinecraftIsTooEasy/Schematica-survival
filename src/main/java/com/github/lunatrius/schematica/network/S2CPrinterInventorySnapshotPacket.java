package com.github.lunatrius.schematica.network;

import com.github.lunatrius.schematica.SchematicaRuntime;
import moddedmite.rustedironcore.network.Packet;
import moddedmite.rustedironcore.network.PacketByteBuf;
import net.minecraft.EntityPlayer;
import net.minecraft.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class S2CPrinterInventorySnapshotPacket implements Packet {
    private static final int MAX_ENTRIES = 256;

    private final int printerX;
    private final int printerY;
    private final int printerZ;
    private final boolean present;
    private final int[] itemIds;
    private final int[] subtypes;
    private final int[] counts;

    public static S2CPrinterInventorySnapshotPacket missing(int printerX, int printerY, int printerZ) {
        return new S2CPrinterInventorySnapshotPacket(printerX, printerY, printerZ, false, new int[0], new int[0], new int[0]);
    }

    public S2CPrinterInventorySnapshotPacket(int printerX, int printerY, int printerZ, Map<String, Integer> rawCounts) {
        this.printerX = printerX;
        this.printerY = printerY;
        this.printerZ = printerZ;
        this.present = true;

        if (rawCounts == null || rawCounts.isEmpty()) {
            this.itemIds = new int[0];
            this.subtypes = new int[0];
            this.counts = new int[0];
            return;
        }

        int limit = Math.min(MAX_ENTRIES, rawCounts.size());
        int[] itemIds = new int[limit];
        int[] subtypes = new int[limit];
        int[] counts = new int[limit];
        int kept = 0;

        for (Map.Entry<String, Integer> entry : rawCounts.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            int split = entry.getKey().indexOf(':');
            if (split <= 0 || split >= entry.getKey().length() - 1) {
                continue;
            }
            int itemId;
            int subtype;
            int count = entry.getValue().intValue();
            if (count <= 0) {
                continue;
            }
            try {
                itemId = Integer.parseInt(entry.getKey().substring(0, split));
                subtype = Integer.parseInt(entry.getKey().substring(split + 1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (itemId <= 0) {
                continue;
            }
            if (kept >= limit) {
                break;
            }
            itemIds[kept] = itemId;
            subtypes[kept] = subtype;
            counts[kept] = count;
            ++kept;
        }

        if (kept <= 0) {
            this.itemIds = new int[0];
            this.subtypes = new int[0];
            this.counts = new int[0];
            return;
        }
        this.itemIds = trim(itemIds, kept);
        this.subtypes = trim(subtypes, kept);
        this.counts = trim(counts, kept);
    }

    public S2CPrinterInventorySnapshotPacket(PacketByteBuf buffer) {
        this.printerX = buffer.readInt();
        this.printerY = buffer.readInt();
        this.printerZ = buffer.readInt();
        this.present = buffer.readBoolean();
        int entryCount = buffer.readInt();
        if (entryCount <= 0) {
            this.itemIds = new int[0];
            this.subtypes = new int[0];
            this.counts = new int[0];
            return;
        }

        int keepLimit = Math.min(entryCount, MAX_ENTRIES);
        int[] itemIds = new int[Math.max(0, keepLimit)];
        int[] subtypes = new int[Math.max(0, keepLimit)];
        int[] counts = new int[Math.max(0, keepLimit)];
        int kept = 0;

        for (int i = 0; i < entryCount; ++i) {
            int itemId = buffer.readInt();
            int subtype = buffer.readInt();
            int count = buffer.readInt();
            if (kept >= keepLimit || itemId <= 0 || count <= 0) {
                continue;
            }
            itemIds[kept] = itemId;
            subtypes[kept] = subtype;
            counts[kept] = count;
            ++kept;
        }
        if (kept <= 0) {
            this.itemIds = new int[0];
            this.subtypes = new int[0];
            this.counts = new int[0];
            return;
        }
        this.itemIds = trim(itemIds, kept);
        this.subtypes = trim(subtypes, kept);
        this.counts = trim(counts, kept);
    }

    @Override
    public void write(PacketByteBuf buffer) {
        buffer.writeInt(this.printerX);
        buffer.writeInt(this.printerY);
        buffer.writeInt(this.printerZ);
        buffer.writeBoolean(this.present);
        int size = Math.min(this.itemIds.length, Math.min(this.subtypes.length, this.counts.length));
        buffer.writeInt(size);
        for (int i = 0; i < size; ++i) {
            buffer.writeInt(this.itemIds[i]);
            buffer.writeInt(this.subtypes[i]);
            buffer.writeInt(this.counts[i]);
        }
    }

    @Override
    public void apply(EntityPlayer player) {
        if (!this.present) {
            SchematicaRuntime.clearPrinterInventorySnapshot(this.printerX, this.printerY, this.printerZ);
            return;
        }
        Map<String, Integer> values = new HashMap<String, Integer>();
        for (int i = 0; i < this.itemIds.length; ++i) {
            int itemId = this.itemIds[i];
            int subtype = this.subtypes[i];
            int amount = this.counts[i];
            if (itemId <= 0 || amount <= 0) {
                continue;
            }
            String key = itemId + ":" + subtype;
            Integer old = values.get(key);
            values.put(key, old == null ? amount : old + amount);
        }
        SchematicaRuntime.setPrinterInventorySnapshot(
                this.printerX,
                this.printerY,
                this.printerZ,
                values,
                System.currentTimeMillis());
    }

    @Override
    public ResourceLocation getChannel() {
        return SchematicaPrinterNetworking.S2C_PRINTER_INVENTORY_SNAPSHOT_CHANNEL;
    }

    private static int[] trim(int[] source, int length) {
        int[] result = new int[length];
        System.arraycopy(source, 0, result, 0, length);
        return result;
    }

    private S2CPrinterInventorySnapshotPacket(
            int printerX,
            int printerY,
            int printerZ,
            boolean present,
            int[] itemIds,
            int[] subtypes,
            int[] counts) {
        this.printerX = printerX;
        this.printerY = printerY;
        this.printerZ = printerZ;
        this.present = present;
        this.itemIds = itemIds == null ? new int[0] : itemIds;
        this.subtypes = subtypes == null ? new int[0] : subtypes;
        this.counts = counts == null ? new int[0] : counts;
    }
}
