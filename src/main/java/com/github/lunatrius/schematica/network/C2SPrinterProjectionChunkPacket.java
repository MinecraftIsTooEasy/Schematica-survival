package com.github.lunatrius.schematica.network;

import moddedmite.rustedironcore.network.Packet;
import moddedmite.rustedironcore.network.PacketByteBuf;
import net.minecraft.EntityPlayer;
import net.minecraft.ResourceLocation;
import net.minecraft.ServerPlayer;

public class C2SPrinterProjectionChunkPacket implements Packet {
    private static final int MAX_CHUNK_BYTES = 24_000;

    private final int uploadId;
    private final int chunkIndex;
    private final int totalChunks;
    private final int totalBytes;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final String schematicName;
    private final byte[] chunkData;

    public C2SPrinterProjectionChunkPacket(
            int uploadId,
            int chunkIndex,
            int totalChunks,
            int totalBytes,
            int originX,
            int originY,
            int originZ,
            String schematicName,
            byte[] chunkData) {
        this.uploadId = uploadId;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.totalBytes = totalBytes;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.schematicName = schematicName == null ? "" : schematicName;
        this.chunkData = chunkData == null ? new byte[0] : chunkData;
    }

    public C2SPrinterProjectionChunkPacket(PacketByteBuf buffer) {
        this.uploadId = buffer.readInt();
        this.chunkIndex = buffer.readInt();
        this.totalChunks = buffer.readInt();
        this.totalBytes = buffer.readInt();
        this.originX = buffer.readInt();
        this.originY = buffer.readInt();
        this.originZ = buffer.readInt();
        this.schematicName = buffer.readString();
        int length = buffer.readInt();
        if (length <= 0 || length > MAX_CHUNK_BYTES) {
            this.chunkData = new byte[0];
            return;
        }
        this.chunkData = new byte[length];
        buffer.readFully(this.chunkData);
    }

    @Override
    public void write(PacketByteBuf buffer) {
        buffer.writeInt(this.uploadId);
        buffer.writeInt(this.chunkIndex);
        buffer.writeInt(this.totalChunks);
        buffer.writeInt(this.totalBytes);
        buffer.writeInt(this.originX);
        buffer.writeInt(this.originY);
        buffer.writeInt(this.originZ);
        buffer.writeString(this.schematicName == null ? "" : this.schematicName);
        buffer.writeInt(this.chunkData.length);
        if (this.chunkData.length > 0) {
            buffer.write(this.chunkData, 0, this.chunkData.length);
        }
    }

    @Override
    public void apply(EntityPlayer player) {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        SchematicaPrinterNetworking.onServerProjectionChunkReceived((ServerPlayer) player, this);
    }

    @Override
    public ResourceLocation getChannel() {
        return SchematicaPrinterNetworking.C2S_PRINTER_PROJECTION_CHUNK_CHANNEL;
    }

    public int getUploadId() {
        return this.uploadId;
    }

    public int getChunkIndex() {
        return this.chunkIndex;
    }

    public int getTotalChunks() {
        return this.totalChunks;
    }

    public int getTotalBytes() {
        return this.totalBytes;
    }

    public int getOriginX() {
        return this.originX;
    }

    public int getOriginY() {
        return this.originY;
    }

    public int getOriginZ() {
        return this.originZ;
    }

    public String getSchematicName() {
        return this.schematicName;
    }

    public byte[] getChunkData() {
        return this.chunkData;
    }
}
