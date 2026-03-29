// MITE port (c) 2025 hahahha. Licensed under the MIT License.
package com.github.lunatrius.schematica.client.render;

import com.github.lunatrius.schematica.SchematicaRuntime;
import com.github.lunatrius.schematica.api.ISchematic;
import net.minecraft.AxisAlignedBB;
import net.minecraft.Block;
import net.minecraft.Entity;
import net.minecraft.Minecraft;
import net.minecraft.Tessellator;
import org.lwjgl.opengl.GL11;

public final class SchematicProjectionRenderer {
    private static final int MAX_RENDER_BLOCKS = 12000;
    private static final double BOX_EPSILON = 0.002;

    private SchematicProjectionRenderer() {
    }

    public static void render(float partialTicks) {
        ISchematic schematic = SchematicaRuntime.loadedSchematic;
        if (schematic == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return;
        }

        Entity camera = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        if (camera == null) {
            return;
        }

        double camX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        double camY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        double camZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth(1.0F);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINES);
        tessellator.setColorRGBA_F(0.15F, 0.85F, 1.0F, 0.35F);

        int rendered = 0;
        outer:
        for (int x = 0; x < schematic.getWidth(); ++x) {
            for (int y = 0; y < schematic.getHeight(); ++y) {
                for (int z = 0; z < schematic.getLength(); ++z) {
                    Block block = schematic.getBlock(x, y, z);
                    if (block == null || block.blockID == 0) {
                        continue;
                    }
                    if (++rendered > MAX_RENDER_BLOCKS) {
                        break outer;
                    }

                    double minX = SchematicaRuntime.originX + x - camX - BOX_EPSILON;
                    double minY = SchematicaRuntime.originY + y - camY - BOX_EPSILON;
                    double minZ = SchematicaRuntime.originZ + z - camZ - BOX_EPSILON;
                    AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(minX, minY, minZ, minX + 1.0 + BOX_EPSILON * 2.0, minY + 1.0 + BOX_EPSILON * 2.0, minZ + 1.0 + BOX_EPSILON * 2.0);
                    addOutlinedBox(tessellator, bb);
                }
            }
        }

        tessellator.draw();

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    private static void addOutlinedBox(Tessellator tessellator, AxisAlignedBB bb) {
        addLine(tessellator, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ);
        addLine(tessellator, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
        addLine(tessellator, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ);
        addLine(tessellator, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.minZ);

        addLine(tessellator, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        addLine(tessellator, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
        addLine(tessellator, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        addLine(tessellator, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ);

        addLine(tessellator, bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ);
        addLine(tessellator, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        addLine(tessellator, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ);
        addLine(tessellator, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
    }

    private static void addLine(Tessellator tessellator, double x1, double y1, double z1, double x2, double y2, double z2) {
        tessellator.addVertex(x1, y1, z1);
        tessellator.addVertex(x2, y2, z2);
    }
}
