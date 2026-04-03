package com.github.lunatrius.schematica.network;

import com.github.lunatrius.schematica.SchematicaPrinterConfig;
import com.github.lunatrius.schematica.SchematicaRuntime;
import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.reference.Reference;
import com.github.lunatrius.schematica.world.schematic.SchematicFormat;
import moddedmite.rustedironcore.network.Network;
import moddedmite.rustedironcore.network.PacketReader;
import moddedmite.rustedironcore.network.PacketSupplier;
import net.minecraft.CompressedStreamTools;
import net.minecraft.Minecraft;
import net.minecraft.NBTTagCompound;
import net.minecraft.ResourceLocation;
import net.minecraft.ServerPlayer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class SchematicaPrinterNetworking {
    static final ResourceLocation C2S_PRINTER_PROJECTION_CHUNK_CHANNEL = new ResourceLocation("scs", "pp_chunk");
    static final ResourceLocation S2C_PRINTER_INVENTORY_SNAPSHOT_CHANNEL = new ResourceLocation("scs", "pi_snap");
    static final ResourceLocation S2C_PRINTER_PRINT_RESULT_CHANNEL = new ResourceLocation("scs", "pp_res");

    private static final int CHUNK_BYTES = 24_000;
    private static final int MAX_TOTAL_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TOTAL_CHUNKS = 1024;
    private static final long UPLOAD_SESSION_TTL_MS = 120_000L;
    private static final AtomicInteger NEXT_UPLOAD_ID = new AtomicInteger(0);
    private static final Map<Integer, UploadSession> uploadSessionsByPlayerId = new HashMap<Integer, UploadSession>();

    private SchematicaPrinterNetworking() {
    }

    public static void registerPacketReaders() {
        PacketReader.registerServerPacketReader(C2S_PRINTER_PROJECTION_CHUNK_CHANNEL, new PacketSupplier() {
            @Override
            public moddedmite.rustedironcore.network.Packet readPacket(moddedmite.rustedironcore.network.PacketByteBuf packetByteBuf) {
                return new C2SPrinterProjectionChunkPacket(packetByteBuf);
            }
        });
        PacketReader.registerClientPacketReader(S2C_PRINTER_INVENTORY_SNAPSHOT_CHANNEL, new PacketSupplier() {
            @Override
            public moddedmite.rustedironcore.network.Packet readPacket(moddedmite.rustedironcore.network.PacketByteBuf packetByteBuf) {
                return new S2CPrinterInventorySnapshotPacket(packetByteBuf);
            }
        });
        PacketReader.registerClientPacketReader(S2C_PRINTER_PRINT_RESULT_CHANNEL, new PacketSupplier() {
            @Override
            public moddedmite.rustedironcore.network.Packet readPacket(moddedmite.rustedironcore.network.PacketByteBuf packetByteBuf) {
                return new S2CPrinterPrintResultPacket(packetByteBuf);
            }
        });
    }

    public static boolean uploadLoadedProjectionToServer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }
        if (mc.isIntegratedServerRunning()) {
            return true;
        }
        if (!SchematicaRuntime.hasLoadedSchematic()) {
            return false;
        }
        return uploadProjectionToServer(
                SchematicaRuntime.loadedSchematic,
                SchematicaRuntime.loadedSchematicName,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ);
    }

    static void onServerProjectionChunkReceived(ServerPlayer player, C2SPrinterProjectionChunkPacket packet) {
        if (player == null || packet == null) {
            return;
        }
        if (!SchematicaPrinterConfig.isClientProjectionUploadEnabled()) {
            return;
        }
        if (packet.getTotalChunks() <= 0 || packet.getTotalChunks() > MAX_TOTAL_CHUNKS) {
            return;
        }
        if (packet.getTotalBytes() <= 0 || packet.getTotalBytes() > MAX_TOTAL_BYTES) {
            return;
        }

        final int playerEntityId = player.entityId;
        final long now = System.currentTimeMillis();
        UploadSession completedSession = null;
        byte[] completedBytes = null;

        synchronized (uploadSessionsByPlayerId) {
            cleanupExpiredUploadSessions(now);
            UploadSession session = uploadSessionsByPlayerId.get(Integer.valueOf(playerEntityId));
            if (session == null || !session.matchesUpload(packet)) {
                if (packet.getChunkIndex() != 0) {
                    return;
                }
                session = new UploadSession(packet, now);
                uploadSessionsByPlayerId.put(Integer.valueOf(playerEntityId), session);
            }
            if (!session.accept(packet, now)) {
                return;
            }
            if (!session.isComplete()) {
                return;
            }
            completedBytes = session.assemble();
            completedSession = session;
            uploadSessionsByPlayerId.remove(Integer.valueOf(playerEntityId));
        }

        if (completedSession == null || completedBytes == null) {
            return;
        }

        ISchematic schematic = deserializeSchematic(completedBytes);
        if (schematic == null) {
            Reference.logger.warn("Client projection upload decode failed for player entity {}", Integer.valueOf(playerEntityId));
            return;
        }
        SchematicaRuntime.setUploadedPrinterProjection(
                playerEntityId,
                schematic,
                completedSession.schematicName,
                completedSession.originX,
                completedSession.originY,
                completedSession.originZ,
                now);
    }

    private static boolean uploadProjectionToServer(
            ISchematic schematic,
            String schematicName,
            int originX,
            int originY,
            int originZ) {
        if (schematic == null) {
            return false;
        }
        byte[] compressed = serializeSchematic(schematic);
        if (compressed == null || compressed.length <= 0 || compressed.length > MAX_TOTAL_BYTES) {
            return false;
        }

        int totalChunks = (compressed.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        if (totalChunks <= 0 || totalChunks > MAX_TOTAL_CHUNKS) {
            return false;
        }

        int uploadId = NEXT_UPLOAD_ID.updateAndGet(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
        String normalizedName = normalizeSchematicName(schematicName);

        for (int index = 0; index < totalChunks; ++index) {
            int from = index * CHUNK_BYTES;
            int to = Math.min(compressed.length, from + CHUNK_BYTES);
            int length = to - from;
            byte[] chunk = new byte[length];
            System.arraycopy(compressed, from, chunk, 0, length);
            Network.sendToServer(new C2SPrinterProjectionChunkPacket(
                    uploadId,
                    index,
                    totalChunks,
                    compressed.length,
                    originX,
                    originY,
                    originZ,
                    normalizedName,
                    chunk));
        }
        return true;
    }

    private static byte[] serializeSchematic(ISchematic schematic) {
        try {
            SchematicFormat format = SchematicFormat.FORMATS.get(SchematicFormat.FORMAT_DEFAULT);
            if (format == null) {
                return null;
            }
            NBTTagCompound compound = new NBTTagCompound();
            if (!format.writeToNBT(compound, schematic)) {
                return null;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(compound, output);
            return output.toByteArray();
        } catch (Exception e) {
            Reference.logger.warn("Failed to serialize client projection for printer upload", e);
            return null;
        }
    }

    private static ISchematic deserializeSchematic(byte[] compressedBytes) {
        if (compressedBytes == null || compressedBytes.length <= 0) {
            return null;
        }
        try {
            NBTTagCompound compound = CompressedStreamTools.readCompressed(new ByteArrayInputStream(compressedBytes));
            if (compound == null) {
                return null;
            }
            String formatName = compound.getString("Materials");
            SchematicFormat format = SchematicFormat.FORMATS.get(formatName);
            if (format == null) {
                format = SchematicFormat.FORMATS.get(SchematicFormat.FORMAT_DEFAULT);
            }
            if (format == null) {
                return null;
            }
            return format.readFromNBT(compound);
        } catch (Exception e) {
            Reference.logger.warn("Invalid uploaded printer projection payload", e);
            return null;
        }
    }

    private static String normalizeSchematicName(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > 128) {
            return value.substring(0, 128);
        }
        return value;
    }

    private static void cleanupExpiredUploadSessions(long now) {
        Integer[] keys = uploadSessionsByPlayerId.keySet().toArray(new Integer[0]);
        for (Integer key : keys) {
            if (key == null) {
                continue;
            }
            UploadSession session = uploadSessionsByPlayerId.get(key);
            if (session == null || now - session.updatedAtMs > UPLOAD_SESSION_TTL_MS) {
                uploadSessionsByPlayerId.remove(key);
            }
        }
    }

    private static final class UploadSession {
        private final int uploadId;
        private final int totalChunks;
        private final int totalBytes;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final String schematicName;
        private final byte[][] chunks;
        private int receivedChunks;
        private long updatedAtMs;

        private UploadSession(C2SPrinterProjectionChunkPacket packet, long now) {
            this.uploadId = packet.getUploadId();
            this.totalChunks = packet.getTotalChunks();
            this.totalBytes = packet.getTotalBytes();
            this.originX = packet.getOriginX();
            this.originY = packet.getOriginY();
            this.originZ = packet.getOriginZ();
            this.schematicName = normalizeSchematicName(packet.getSchematicName());
            this.chunks = new byte[this.totalChunks][];
            this.updatedAtMs = now;
        }

        private boolean matchesUpload(C2SPrinterProjectionChunkPacket packet) {
            return packet.getUploadId() == this.uploadId
                    && packet.getTotalChunks() == this.totalChunks
                    && packet.getTotalBytes() == this.totalBytes
                    && packet.getOriginX() == this.originX
                    && packet.getOriginY() == this.originY
                    && packet.getOriginZ() == this.originZ;
        }

        private boolean accept(C2SPrinterProjectionChunkPacket packet, long now) {
            if (!matchesUpload(packet)) {
                return false;
            }
            int index = packet.getChunkIndex();
            if (index < 0 || index >= this.totalChunks) {
                return false;
            }
            byte[] payload = packet.getChunkData();
            if (payload == null || payload.length <= 0 || payload.length > CHUNK_BYTES) {
                return false;
            }
            if (this.chunks[index] == null) {
                this.chunks[index] = payload;
                ++this.receivedChunks;
            }
            this.updatedAtMs = now;
            return true;
        }

        private boolean isComplete() {
            return this.receivedChunks >= this.totalChunks;
        }

        private byte[] assemble() {
            byte[] combined = new byte[this.totalBytes];
            int offset = 0;
            for (int i = 0; i < this.totalChunks; ++i) {
                byte[] payload = this.chunks[i];
                if (payload == null) {
                    return null;
                }
                if (offset + payload.length > combined.length) {
                    return null;
                }
                System.arraycopy(payload, 0, combined, offset, payload.length);
                offset += payload.length;
            }
            if (offset != combined.length) {
                return null;
            }
            return combined;
        }
    }
}
