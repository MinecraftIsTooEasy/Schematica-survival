// MITE port (c) 2025 hahahha. Licensed under the MIT License.
package com.github.lunatrius.schematica.client.render;

import com.github.lunatrius.schematica.SchematicaPrinterConfig;
import com.github.lunatrius.schematica.SchematicaRuntime;
import com.github.lunatrius.schematica.api.ISchematic;
import net.minecraft.AxisAlignedBB;
import net.minecraft.BiomeGenBase;
import net.minecraft.Block;
import net.minecraft.Entity;
import net.minecraft.IBlockAccess;
import net.minecraft.Material;
import net.minecraft.Minecraft;
import net.minecraft.RenderBlocks;
import net.minecraft.Tessellator;
import net.minecraft.TextureMap;
import net.minecraft.TileEntity;
import net.minecraft.Vec3Pool;
import net.minecraft.World;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public final class SchematicProjectionRenderer {
    private static final int MAX_RENDER_BLOCKS = 12000;
    private static final double BOX_EPSILON = 0.002;
    private static ProjectionRenderCache projectionCache;

    private static final int FACE_DOWN = 1;
    private static final int FACE_UP = 1 << 1;
    private static final int FACE_NORTH = 1 << 2;
    private static final int FACE_SOUTH = 1 << 3;
    private static final int FACE_WEST = 1 << 4;
    private static final int FACE_EAST = 1 << 5;

    private SchematicProjectionRenderer() {
    }

    public static void render(float partialTicks) {
        ISchematic schematic = SchematicaRuntime.loadedSchematic;
        boolean markerActive = SchematicaRuntime.hasProjectionAlertMarker();
        int markerX = markerActive ? SchematicaRuntime.getProjectionAlertMarkerX() : 0;
        int markerY = markerActive ? SchematicaRuntime.getProjectionAlertMarkerY() : 0;
        int markerZ = markerActive ? SchematicaRuntime.getProjectionAlertMarkerZ() : 0;
        boolean markerOnly = markerActive && SchematicaRuntime.isProjectionAlertMarkerRenderOnly();
        if (schematic == null && !markerActive) {
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

        float ghostAlphaSolid = SchematicaPrinterConfig.getProjectionGhostAlphaSolid();
        float ghostAlphaTranslucent = SchematicaPrinterConfig.getProjectionGhostAlphaTranslucent();
        float lineAlpha = SchematicaPrinterConfig.getProjectionLineAlpha();

        GL11.glPushMatrix();
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT
                        | GL11.GL_COLOR_BUFFER_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_CURRENT_BIT
                        | GL11.GL_LINE_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(1.5F);

            Tessellator tessellator = Tessellator.instance;
            boolean markerDrawnFromProjection = false;

            if (!markerOnly && schematic != null) {
                int width = schematic.getWidth();
                int height = schematic.getHeight();
                int length = schematic.getLength();
                int originX = SchematicaRuntime.originX;
                int originY = SchematicaRuntime.originY;
                int originZ = SchematicaRuntime.originZ;
                ProjectionRenderCache cache = getOrBuildProjectionCache(
                        schematic,
                        originX,
                        originY,
                        originZ,
                        width,
                        height,
                        length);
                IBlockAccess schematicAccess = new SchematicBlockAccess(
                        schematic,
                        mc.theWorld,
                        originX,
                        originY,
                        originZ,
                        width,
                        height,
                        length);
                RenderBlocks renderBlocks = new RenderBlocks(schematicAccess);

                mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
                // Ghost block pass: keep proper self-occlusion (covered faces won't bleed through).
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
                GL11.glEnable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonOffset(-0.3F, -0.6F);
                List<ChestRenderEntry> chestEntries = new ArrayList<ChestRenderEntry>();
                renderGhostBlocks(
                        tessellator,
                        renderBlocks,
                        schematic,
                        cache.pass0Indices,
                        cache.pass0Count,
                        originX,
                        originY,
                        originZ,
                        width,
                        height,
                        length,
                        camX,
                        camY,
                        camZ,
                        0,
                        ghostAlphaSolid,
                        chestEntries);
                renderGhostBlocks(
                        tessellator,
                        renderBlocks,
                        schematic,
                        cache.pass1Indices,
                        cache.pass1Count,
                        originX,
                        originY,
                        originZ,
                        width,
                        height,
                        length,
                        camX,
                        camY,
                        camZ,
                        1,
                        ghostAlphaTranslucent,
                        null);
                GL11.glPolygonOffset(0.0F, 0.0F);
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                collectChestEntries(chestEntries, cache, schematic, originX, originY, originZ, width, length);
                renderGhostChests(renderBlocks, chestEntries, camX, camY, camZ, ghostAlphaSolid);

                // Outline pass: keep through-geometry readability.
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                tessellator.startDrawing(GL11.GL_LINES);

                int rendered = 0;
                for (int i = 0; i < cache.outlineCount && rendered < MAX_RENDER_BLOCKS; ++i) {
                    int index = cache.outlineIndices[i];
                    int x = index % width;
                    int yz = index / width;
                    int y = yz / length;
                    int z = yz % length;
                    Block block = schematic.getBlock(x, y, z);
                    if (block == null || block.blockID == 0) {
                        continue;
                    }
                    ++rendered;

                    int exposedFaces = getExposedFaces(schematic, x, y, z, width, height, length);
                    if (exposedFaces == 0) {
                        continue;
                    }
                    int worldX = originX + x;
                    int worldY = originY + y;
                    int worldZ = originZ + z;
                    boolean markerMatch = markerActive
                            && worldX == markerX
                            && worldY == markerY
                            && worldZ == markerZ;
                    if (markerMatch) {
                        markerDrawnFromProjection = true;
                        tessellator.setColorRGBA_F(1.0F, 0.2F, 0.2F, lineAlpha);
                    } else {
                        tessellator.setColorRGBA_F(0.15F, 0.85F, 1.0F, lineAlpha);
                    }

                    double minX = worldX - camX - BOX_EPSILON;
                    double minY = worldY - camY - BOX_EPSILON;
                    double minZ = worldZ - camZ - BOX_EPSILON;
                    AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                            minX, minY, minZ,
                            minX + 1.0 + BOX_EPSILON * 2.0,
                            minY + 1.0 + BOX_EPSILON * 2.0,
                            minZ + 1.0 + BOX_EPSILON * 2.0);
                    addExposedFaceOutlines(tessellator, bb, exposedFaces);
                }
                tessellator.draw();
            }

            if (markerActive && (!markerDrawnFromProjection || markerOnly)) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                tessellator.startDrawing(GL11.GL_LINES);
                tessellator.setColorRGBA_F(1.0F, 0.2F, 0.2F, lineAlpha);
                drawMarkerBox(tessellator, markerX, markerY, markerZ, camX, camY, camZ);
                tessellator.draw();
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GL11.glPopAttrib();
        }
        GL11.glPopMatrix();
    }

    private static void renderGhostBlocks(
            Tessellator tessellator,
            RenderBlocks renderBlocks,
            ISchematic schematic,
            int[] indices,
            int indexCount,
            int originX,
            int originY,
            int originZ,
            int width,
            int height,
            int length,
            double camX,
            double camY,
            double camZ,
            int renderPass,
            float alpha,
            List<ChestRenderEntry> chestEntries) {
        tessellator.startDrawingQuads();
        tessellator.setTranslation(-camX, -camY, -camZ);
        tessellator.disableColor();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);

        int rendered = 0;
        for (int i = 0; i < indexCount && rendered < MAX_RENDER_BLOCKS; ++i) {
            int index = indices[i];
            int x = index % width;
            int yz = index / width;
            int y = yz / length;
            int z = yz % length;
            Block block = schematic.getBlock(x, y, z);
            if (block == null || block.blockID == 0 || block.getRenderBlockPass() != renderPass) {
                continue;
            }
            if (block.getRenderType() == 33) {
                continue;
            }
            ++rendered;
            int exposedFaces = getExposedFaces(schematic, x, y, z, width, height, length);
            if (exposedFaces == 0) {
                continue;
            }

            int worldX = originX + x;
            int worldY = originY + y;
            int worldZ = originZ + z;
            if (block.getRenderType() == 22) {
                if (renderPass == 0 && chestEntries != null) {
                    chestEntries.add(new ChestRenderEntry(block, schematic.getBlockMetadata(x, y, z), worldX, worldY, worldZ));
                }
                continue;
            }
            renderBlocks.renderBlockByRenderType(block, worldX, worldY, worldZ);
        }

        tessellator.draw();
        tessellator.setTranslation(0.0, 0.0, 0.0);
    }

    private static ProjectionRenderCache getOrBuildProjectionCache(
            ISchematic schematic,
            int originX,
            int originY,
            int originZ,
            int width,
            int height,
            int length) {
        ProjectionRenderCache cache = projectionCache;
        if (cache != null
                && cache.matches(schematic, originX, originY, originZ, width, height, length)) {
            return cache;
        }

        ProjectionRenderCache rebuilt = new ProjectionRenderCache(schematic, originX, originY, originZ, width, height, length);
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                for (int z = 0; z < length; ++z) {
                    Block block = schematic.getBlock(x, y, z);
                    if (block == null || block.blockID == 0) {
                        continue;
                    }
                    int index = x + (y * length + z) * width;
                    rebuilt.appendOutline(index);
                    int renderType = block.getRenderType();
                    if (renderType == 22) {
                        rebuilt.appendChest(index, schematic.getBlockMetadata(x, y, z));
                        continue;
                    }
                    if (renderType == 33) {
                        continue;
                    }
                    if (block.getRenderBlockPass() == 0) {
                        rebuilt.appendPass0(index);
                    } else {
                        rebuilt.appendPass1(index);
                    }
                }
            }
        }
        projectionCache = rebuilt;
        return rebuilt;
    }

    private static void collectChestEntries(
            List<ChestRenderEntry> out,
            ProjectionRenderCache cache,
            ISchematic schematic,
            int originX,
            int originY,
            int originZ,
            int width,
            int length) {
        if (out == null || cache == null || cache.chestCount <= 0) {
            return;
        }
        int limit = Math.min(cache.chestCount, MAX_RENDER_BLOCKS);
        for (int i = 0; i < limit; ++i) {
            int index = cache.chestIndices[i];
            int x = index % width;
            int yz = index / width;
            int y = yz / length;
            int z = yz % length;
            Block block = schematic.getBlock(x, y, z);
            if (block == null || block.blockID == 0 || block.getRenderType() != 22) {
                continue;
            }
            out.add(new ChestRenderEntry(
                    block,
                    cache.chestMetadata[i] & 0xF,
                    originX + x,
                    originY + y,
                    originZ + z));
        }
    }

    private static void renderGhostChests(
            RenderBlocks renderBlocks,
            List<ChestRenderEntry> chestEntries,
            double camX,
            double camY,
            double camZ,
            float alpha) {
        if (chestEntries == null || chestEntries.isEmpty()) {
            return;
        }
        boolean oldTint = renderBlocks.useInventoryTint;
        renderBlocks.useInventoryTint = false;
        for (ChestRenderEntry entry : chestEntries) {
            GL11.glPushMatrix();
            GL11.glTranslatef(
                    (float) (entry.worldX - camX + 0.5D),
                    (float) (entry.worldY - camY + 0.5D),
                    (float) (entry.worldZ - camZ + 0.5D));
            GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
            renderBlocks.renderBlockAsItem(entry.block, entry.metadata, 1.0F);
            GL11.glPopMatrix();
        }
        renderBlocks.useInventoryTint = oldTint;
    }

    private static void drawMarkerBox(Tessellator tessellator, int worldX, int worldY, int worldZ, double camX, double camY, double camZ) {
        double minX = worldX - camX - BOX_EPSILON;
        double minY = worldY - camY - BOX_EPSILON;
        double minZ = worldZ - camZ - BOX_EPSILON;
        AxisAlignedBB bb = AxisAlignedBB.getBoundingBox(
                minX, minY, minZ,
                minX + 1.0 + BOX_EPSILON * 2.0,
                minY + 1.0 + BOX_EPSILON * 2.0,
                minZ + 1.0 + BOX_EPSILON * 2.0);
        int allFaces = FACE_DOWN | FACE_UP | FACE_NORTH | FACE_SOUTH | FACE_WEST | FACE_EAST;
        addExposedFaceOutlines(tessellator, bb, allFaces);
    }

    private static int getExposedFaces(ISchematic schematic, int x, int y, int z, int width, int height, int length) {
        int faces = 0;
        if (!isSolid(schematic, x, y - 1, z, width, height, length)) {
            faces |= FACE_DOWN;
        }
        if (!isSolid(schematic, x, y + 1, z, width, height, length)) {
            faces |= FACE_UP;
        }
        if (!isSolid(schematic, x, y, z - 1, width, height, length)) {
            faces |= FACE_NORTH;
        }
        if (!isSolid(schematic, x, y, z + 1, width, height, length)) {
            faces |= FACE_SOUTH;
        }
        if (!isSolid(schematic, x - 1, y, z, width, height, length)) {
            faces |= FACE_WEST;
        }
        if (!isSolid(schematic, x + 1, y, z, width, height, length)) {
            faces |= FACE_EAST;
        }
        return faces;
    }

    private static boolean isSolid(ISchematic schematic, int x, int y, int z, int width, int height, int length) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= length) {
            return false;
        }
        Block block = schematic.getBlock(x, y, z);
        return block != null && block.blockID != 0;
    }

    private static void addExposedFaceOutlines(Tessellator tessellator, AxisAlignedBB bb, int faces) {
        boolean down = (faces & FACE_DOWN) != 0;
        boolean up = (faces & FACE_UP) != 0;
        boolean north = (faces & FACE_NORTH) != 0;
        boolean south = (faces & FACE_SOUTH) != 0;
        boolean west = (faces & FACE_WEST) != 0;
        boolean east = (faces & FACE_EAST) != 0;

        if (down || north) {
            addLine(tessellator, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ);
        }
        if (down || south) {
            addLine(tessellator, bb.minX, bb.minY, bb.maxZ, bb.maxX, bb.minY, bb.maxZ);
        }
        if (down || west) {
            addLine(tessellator, bb.minX, bb.minY, bb.minZ, bb.minX, bb.minY, bb.maxZ);
        }
        if (down || east) {
            addLine(tessellator, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
        }

        if (up || north) {
            addLine(tessellator, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        }
        if (up || south) {
            addLine(tessellator, bb.minX, bb.maxY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ);
        }
        if (up || west) {
            addLine(tessellator, bb.minX, bb.maxY, bb.minZ, bb.minX, bb.maxY, bb.maxZ);
        }
        if (up || east) {
            addLine(tessellator, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
        }

        if (north || west) {
            addLine(tessellator, bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ);
        }
        if (north || east) {
            addLine(tessellator, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        }
        if (south || west) {
            addLine(tessellator, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        }
        if (south || east) {
            addLine(tessellator, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ);
        }
    }

    private static void addLine(Tessellator tessellator, double x1, double y1, double z1, double x2, double y2, double z2) {
        tessellator.addVertex(x1, y1, z1);
        tessellator.addVertex(x2, y2, z2);
    }

    private static final class ChestRenderEntry {
        private final Block block;
        private final int metadata;
        private final int worldX;
        private final int worldY;
        private final int worldZ;

        private ChestRenderEntry(Block block, int metadata, int worldX, int worldY, int worldZ) {
            this.block = block;
            this.metadata = metadata;
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
        }
    }

    private static final class ProjectionRenderCache {
        private final ISchematic schematic;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int width;
        private final int height;
        private final int length;
        private int[] pass0Indices = new int[256];
        private int pass0Count;
        private int[] pass1Indices = new int[256];
        private int pass1Count;
        private int[] outlineIndices = new int[256];
        private int outlineCount;
        private int[] chestIndices = new int[64];
        private byte[] chestMetadata = new byte[64];
        private int chestCount;

        private ProjectionRenderCache(
                ISchematic schematic,
                int originX,
                int originY,
                int originZ,
                int width,
                int height,
                int length) {
            this.schematic = schematic;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.width = width;
            this.height = height;
            this.length = length;
        }

        private boolean matches(ISchematic schematic, int originX, int originY, int originZ, int width, int height, int length) {
            return this.schematic == schematic
                    && this.originX == originX
                    && this.originY == originY
                    && this.originZ == originZ
                    && this.width == width
                    && this.height == height
                    && this.length == length;
        }

        private void appendPass0(int index) {
            this.pass0Indices = ensureCapacity(this.pass0Indices, this.pass0Count + 1);
            this.pass0Indices[this.pass0Count++] = index;
        }

        private void appendPass1(int index) {
            this.pass1Indices = ensureCapacity(this.pass1Indices, this.pass1Count + 1);
            this.pass1Indices[this.pass1Count++] = index;
        }

        private void appendOutline(int index) {
            this.outlineIndices = ensureCapacity(this.outlineIndices, this.outlineCount + 1);
            this.outlineIndices[this.outlineCount++] = index;
        }

        private void appendChest(int index, int metadata) {
            this.chestIndices = ensureCapacity(this.chestIndices, this.chestCount + 1);
            this.chestMetadata = ensureCapacity(this.chestMetadata, this.chestCount + 1);
            this.chestIndices[this.chestCount] = index;
            this.chestMetadata[this.chestCount] = (byte) (metadata & 0xF);
            ++this.chestCount;
        }

        private static int[] ensureCapacity(int[] source, int minSize) {
            if (source.length >= minSize) {
                return source;
            }
            int newSize = Math.max(source.length * 2, minSize);
            int[] expanded = new int[newSize];
            System.arraycopy(source, 0, expanded, 0, source.length);
            return expanded;
        }

        private static byte[] ensureCapacity(byte[] source, int minSize) {
            if (source.length >= minSize) {
                return source;
            }
            int newSize = Math.max(source.length * 2, minSize);
            byte[] expanded = new byte[newSize];
            System.arraycopy(source, 0, expanded, 0, source.length);
            return expanded;
        }
    }

    private static final class SchematicBlockAccess implements IBlockAccess {
        private final ISchematic schematic;
        private final IBlockAccess world;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int width;
        private final int height;
        private final int length;

        private SchematicBlockAccess(
                ISchematic schematic,
                IBlockAccess world,
                int originX,
                int originY,
                int originZ,
                int width,
                int height,
                int length) {
            this.schematic = schematic;
            this.world = world;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.width = width;
            this.height = height;
            this.length = length;
        }

        @Override
        public int getBlockId(int x, int y, int z) {
            Block block = this.getBlock(x, y, z);
            return block != null ? block.blockID : 0;
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            if (this.isInsideSchematic(x, y, z)) {
                int sx = x - this.originX;
                int sy = y - this.originY;
                int sz = z - this.originZ;
                return this.schematic.getBlock(sx, sy, sz);
            }
            return this.world.getBlock(x, y, z);
        }

        @Override
        public TileEntity getBlockTileEntity(int x, int y, int z) {
            if (this.isInsideSchematic(x, y, z)) {
                int sx = x - this.originX;
                int sy = y - this.originY;
                int sz = z - this.originZ;
                return this.schematic.getTileEntity(sx, sy, sz);
            }
            return this.world.getBlockTileEntity(x, y, z);
        }

        @Override
        public int getLightBrightnessForSkyBlocks(int x, int y, int z, int fallback) {
            return this.world.getLightBrightnessForSkyBlocks(x, y, z, fallback);
        }

        @Override
        public float getBrightness(int x, int y, int z, int fallback) {
            return this.world.getBrightness(x, y, z, fallback);
        }

        @Override
        public float getLightBrightness(int x, int y, int z) {
            return this.world.getLightBrightness(x, y, z);
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            if (this.isInsideSchematic(x, y, z)) {
                int sx = x - this.originX;
                int sy = y - this.originY;
                int sz = z - this.originZ;
                return this.schematic.getBlockMetadata(sx, sy, sz);
            }
            return this.world.getBlockMetadata(x, y, z);
        }

        @Override
        public Material getBlockMaterial(int x, int y, int z) {
            Block block = this.getBlock(x, y, z);
            return block != null ? block.blockMaterial : Material.air;
        }

        @Override
        public Material getBlockMaterial(int blockId) {
            Block block = Block.getBlock(blockId);
            return block != null ? block.blockMaterial : Material.air;
        }

        @Override
        public boolean isBlockStandardFormOpaqueCube(int x, int y, int z) {
            Block block = this.getBlock(x, y, z);
            return block != null && block.isOpaqueStandardFormCube(this, x, y, z);
        }

        @Override
        public boolean isBlockNormalCube(int x, int y, int z) {
            return Block.isNormalCube(this.getBlock(x, y, z));
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            Block block = this.getBlock(x, y, z);
            return block == null || block.blockID == 0 || block.blockMaterial == Material.air;
        }

        @Override
        public BiomeGenBase getBiomeGenForCoords(int x, int z) {
            return this.world.getBiomeGenForCoords(x, z);
        }

        @Override
        public int getHeight() {
            return this.world.getHeight();
        }

        @Override
        public boolean extendedLevelsInChunkCache() {
            return this.world.extendedLevelsInChunkCache();
        }

        @Override
        public boolean isBlockTopFlatAndSolid(int x, int y, int z) {
            Block block = this.getBlock(x, y, z);
            return block != null && block.isTopFlatAndSolid(this.getBlockMetadata(x, y, z));
        }

        @Override
        public Vec3Pool getWorldVec3Pool() {
            return this.world.getWorldVec3Pool();
        }

        @Override
        public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
            return this.world.isBlockProvidingPowerTo(x, y, z, side);
        }

        @Override
        public World getWorld() {
            return this.world.getWorld();
        }

        @Override
        public boolean isBlockSolid(int x, int y, int z) {
            return Block.isBlockSolid(this, x, y, z);
        }

        private boolean isInsideSchematic(int x, int y, int z) {
            int sx = x - this.originX;
            int sy = y - this.originY;
            int sz = z - this.originZ;
            return sx >= 0 && sy >= 0 && sz >= 0 && sx < this.width && sy < this.height && sz < this.length;
        }
    }
}
