package com.github.lunatrius.schematica.network;

import com.github.lunatrius.schematica.SchematicaRuntime;
import moddedmite.rustedironcore.network.Packet;
import moddedmite.rustedironcore.network.PacketByteBuf;
import net.minecraft.EntityPlayer;
import net.minecraft.ResourceLocation;

public class S2CPrinterPrintResultPacket implements Packet {
    private final int printerX;
    private final int printerY;
    private final int printerZ;
    private final boolean success;
    private final boolean projectionUnloaded;

    public S2CPrinterPrintResultPacket(
            int printerX,
            int printerY,
            int printerZ,
            boolean success,
            boolean projectionUnloaded) {
        this.printerX = printerX;
        this.printerY = printerY;
        this.printerZ = printerZ;
        this.success = success;
        this.projectionUnloaded = projectionUnloaded;
    }

    public S2CPrinterPrintResultPacket(PacketByteBuf buffer) {
        this.printerX = buffer.readInt();
        this.printerY = buffer.readInt();
        this.printerZ = buffer.readInt();
        this.success = buffer.readBoolean();
        this.projectionUnloaded = buffer.readBoolean();
    }

    @Override
    public void write(PacketByteBuf buffer) {
        buffer.writeInt(this.printerX);
        buffer.writeInt(this.printerY);
        buffer.writeInt(this.printerZ);
        buffer.writeBoolean(this.success);
        buffer.writeBoolean(this.projectionUnloaded);
    }

    @Override
    public void apply(EntityPlayer player) {
        if (this.success && this.projectionUnloaded) {
            SchematicaRuntime.clearLoadedSchematic();
        }
    }

    @Override
    public ResourceLocation getChannel() {
        return SchematicaPrinterNetworking.S2C_PRINTER_PRINT_RESULT_CHANNEL;
    }
}
