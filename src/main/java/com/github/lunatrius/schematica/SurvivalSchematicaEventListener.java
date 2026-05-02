package com.github.lunatrius.schematica;

import com.github.lunatrius.schematica.FileFilterSchematic;
import com.github.lunatrius.schematica.SchematicaRuntime;
import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.block.SchematicaBlocks;
import com.github.lunatrius.schematica.network.S2CPrinterInventorySnapshotPacket;
import com.github.lunatrius.schematica.network.S2CPrinterPrintResultPacket;
import com.github.lunatrius.schematica.reference.Reference;
import com.github.lunatrius.schematica.util.I18n;
import com.github.lunatrius.schematica.world.schematic.SchematicFormat;
import com.github.lunatrius.schematica.world.storage.Schematic;
import com.google.common.eventbus.Subscribe;
import moddedmite.rustedironcore.api.event.listener.ITickListener;
import moddedmite.rustedironcore.network.Network;
import net.minecraft.Block;
import net.minecraft.EntityItem;
import net.minecraft.EntityPlayer;
import net.minecraft.EnumDirection;
import net.minecraft.IInventory;
import net.minecraft.InventoryPlayer;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.Minecraft;
import net.minecraft.TileEntity;
import net.minecraft.World;
import net.minecraft.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.xiaoyu233.fml.reload.event.HandleChatCommandEvent;

import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.WeakHashMap;

public class SurvivalSchematicaEventListener implements ITickListener {
    private static final long MAX_SAVE_VOLUME = 8_000_000L;
    private static final long MAX_PASTE_VOLUME = 8_000_000L;
    private static final int MIN_WORLD_Y = 0;
    private static final int MAX_WORLD_Y = 255;
    private static final long PROJECTION_ALERT_MARKER_TTL_MS = 12_000L;
    private static final int PRINTER_PRINT_BLOCKS_PER_TICK = 512;
    private static final int PRINTER_PRINT_MAX_ACTIVE_TASKS = 8;
    private static long PRINTER_TXN_SEQ = 0L;
    private static final Map<ISchematic, NonAirIndexCache> NON_AIR_INDEX_CACHE = new WeakHashMap<ISchematic, NonAirIndexCache>();
    private static final Map<String, ScheduledPrinterPrintTask> ACTIVE_PRINTER_PRINT_TASKS = new HashMap<String, ScheduledPrinterPrintTask>();
    private static final ConcurrentHashMap<Integer, Integer> ROTATION_META_RESULT_CACHE = new ConcurrentHashMap<Integer, Integer>();
    private static final List<RotationMetaRule> ROTATION_META_RULES = createRotationMetaRules();
    @Deprecated
    private static final List<RotationMetaFallbackHandler> ROTATION_META_FALLBACK_HANDLERS = createRotationMetaFallbackHandlers();

    @Override
    public void onServerTick(MinecraftServer server) {
        this.tickScheduledPrinterPrintTasks();
    }

    @Subscribe
    public void onCommand(HandleChatCommandEvent event) {
        String raw = event.getCommand();
        if (raw == null) {
            return;
        }

        String command = raw.trim();
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty()) {
            return;
        }

        String[] parts = command.split("\\s+");
        if (parts.length == 0 || !"schematica".equalsIgnoreCase(parts[0])) {
            return;
        }

        boolean clientWorld = isClientWorld(event);
        String sub = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : "help";

        if ("help".equals(sub)) {
            event.setExecuteSuccess(true);
            sendHelp(event);
            return;
        }

        if ("sel".equals(sub)) {
            event.setExecuteSuccess(true);
            handleSelectionCommand(event, parts);
            return;
        }

        if ("list".equals(sub)) {
            event.setExecuteSuccess(true);
            File[] files = getSchematicDir().listFiles(new FileFilterSchematic(false));
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.list.count", "Schematica files: %d", files == null ? 0 : files.length));
            return;
        }

        if ("load".equals(sub)) {
            event.setExecuteSuccess(true);
            handleLoad(event, parts);
            return;
        }

        if ("unload".equals(sub)) {
            event.setExecuteSuccess(true);
            SchematicaRuntime.clearLoadedSchematic();
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.unload.done", "Schematica projection unloaded."));
            return;
        }

        if ("status".equals(sub)) {
            event.setExecuteSuccess(true);
            handleStatus(event);
            return;
        }

        if ("origin".equals(sub)) {
            event.setExecuteSuccess(true);
            handleOrigin(event, parts);
            return;
        }

        if ("move".equals(sub)) {
            event.setExecuteSuccess(true);
            handleMove(event, parts);
            return;
        }

        if ("nudge".equals(sub)) {
            event.setExecuteSuccess(true);
            handleNudge(event, parts);
            return;
        }

        if ("rotate".equals(sub)) {
            event.setExecuteSuccess(true);
            handleRotate(event, parts);
            return;
        }

        if ("mirror".equals(sub)) {
            event.setExecuteSuccess(true);
            handleMirror(event, parts);
            return;
        }

        if ("paste".equals(sub)) {
            if (clientWorld) {
                event.setExecuteSuccess(false);
                return;
            }
            event.setExecuteSuccess(true);
            handlePaste(event, parts);
            return;
        }

        if ("undo".equals(sub)) {
            if (clientWorld) {
                event.setExecuteSuccess(false);
                return;
            }
            event.setExecuteSuccess(true);
            handleUndo(event);
            return;
        }

        if ("save".equals(sub)) {
            if (clientWorld) {
                event.setExecuteSuccess(false);
                return;
            }
            event.setExecuteSuccess(true);
            handleSave(event, parts);
            return;
        }

        if ("create".equals(sub)) {
            if (clientWorld) {
                event.setExecuteSuccess(false);
                return;
            }
            event.setExecuteSuccess(true);
            handleCreate(event, parts);
            return;
        }

        if ("printer".equals(sub)) {
            handlePrinterCommand(event, parts, clientWorld);
        }
    }

    private void handleSelectionCommand(HandleChatCommandEvent event, String[] parts) {
        if (parts.length < 3) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.sel.usage", "Usage: /schematica sel <status|clear>"));
            return;
        }
        String action = parts[2].toLowerCase(Locale.ROOT);
        if ("status".equals(action)) {
            if (SchematicaRuntime.hasSelectionPos1) {
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.sel.pos1_set",
                        "Selection Pos1: [%d,%d,%d]",
                        SchematicaRuntime.selectionPos1X, SchematicaRuntime.selectionPos1Y, SchematicaRuntime.selectionPos1Z));
            } else {
                event.getPlayer().addChatMessage(I18n.tr("schematica.command.sel.pos1_unset", "Selection Pos1: <unset>"));
            }
            if (SchematicaRuntime.hasSelectionPos2) {
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.sel.pos2_set",
                        "Selection Pos2: [%d,%d,%d]",
                        SchematicaRuntime.selectionPos2X, SchematicaRuntime.selectionPos2Y, SchematicaRuntime.selectionPos2Z));
            } else {
                event.getPlayer().addChatMessage(I18n.tr("schematica.command.sel.pos2_unset", "Selection Pos2: <unset>"));
            }
            return;
        }
        if ("clear".equals(action)) {
            SchematicaRuntime.clearSelection();
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.sel.cleared", "Selection cleared."));
            return;
        }
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.sel.usage", "Usage: /schematica sel <status|clear>"));
    }

    private void handleLoad(HandleChatCommandEvent event, String[] parts) {
        if (parts.length < 3) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.load.usage", "Usage: /schematica load <name>"));
            return;
        }
        String rawName = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length)).trim();
        File file = resolveSchematicFile(rawName);
        if (file == null || !file.exists()) {
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.load.not_found", "Schematic not found: %s", normalizeFilename(rawName)));
            return;
        }

        ISchematic schematic = SchematicFormat.readFromFile(file);
        if (schematic == null) {
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.load.failed", "Failed to load schematic: %s", file.getName()));
            String reason = SchematicFormat.consumeLastReadError();
            if (reason != null && !reason.trim().isEmpty()) {
                event.getPlayer().addChatMessage(I18n.trf("schematica.command.load.failed_reason", "Reason: %s", reason));
            }
            return;
        }

        int x = event.getPlayer().getBlockPosX();
        int y = event.getPlayer().getBlockPosY();
        int z = event.getPlayer().getBlockPosZ();
        SchematicaRuntime.setLoadedSchematic(schematic, x, y, z, file.getName());
        event.getPlayer().addChatMessage(I18n.trf(
                "schematica.command.load.done",
                "Loaded %s (%dx%dx%d) at [%d,%d,%d]",
                file.getName(), schematic.getWidth(), schematic.getHeight(), schematic.getLength(), x, y, z));
    }

    private void handleStatus(HandleChatCommandEvent event) {
        if (!SchematicaRuntime.hasLoadedSchematic()) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.status.no_loaded", "No schematic loaded. Use /schematica load <name>."));
            return;
        }
        ISchematic schematic = SchematicaRuntime.loadedSchematic;
        String name = SchematicaRuntime.loadedSchematicName == null
                ? I18n.tr("schematica.command.value.unknown", "<unknown>")
                : SchematicaRuntime.loadedSchematicName;
        long volume = (long) schematic.getWidth() * schematic.getHeight() * schematic.getLength();
        event.getPlayer().addChatMessage(I18n.trf(
                "schematica.command.status.loaded",
                "Loaded %s (%dx%dx%d, blocks=%d) origin [%d,%d,%d], undo=%s",
                name, schematic.getWidth(), schematic.getHeight(), schematic.getLength(), volume,
                SchematicaRuntime.originX, SchematicaRuntime.originY, SchematicaRuntime.originZ,
                SchematicaRuntime.hasUndoSnapshot() ? I18n.tr("schematica.command.status.undo_ready", "ready") : I18n.tr("schematica.command.status.undo_empty", "empty")));
    }

    private void handleOrigin(HandleChatCommandEvent event, String[] parts) {
        if (!SchematicaRuntime.hasLoadedSchematic()) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_loaded", "No schematic loaded."));
            return;
        }
        if (parts.length >= 3 && "here".equalsIgnoreCase(parts[2])) {
            int x = event.getPlayer().getBlockPosX();
            int y = event.getPlayer().getBlockPosY();
            int z = event.getPlayer().getBlockPosZ();
            SchematicaRuntime.setOrigin(x, y, z);
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.origin.done", "Projection origin set to [%d,%d,%d]", x, y, z));
            return;
        }
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.origin.usage", "Usage: /schematica origin here"));
    }

    private void handleMove(HandleChatCommandEvent event, String[] parts) {
        if (!SchematicaRuntime.hasLoadedSchematic()) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_loaded", "No schematic loaded."));
            return;
        }
        if (parts.length != 5) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.move.usage", "Usage: /schematica move <x> <y> <z>"));
            return;
        }
        try {
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            int z = Integer.parseInt(parts[4]);
            SchematicaRuntime.setOrigin(x, y, z);
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.move.done", "Projection moved to [%d,%d,%d]", x, y, z));
        } catch (NumberFormatException e) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.coords_int", "Coordinates must be integers."));
        }
    }

    private void handleNudge(HandleChatCommandEvent event, String[] parts) {
        if (!SchematicaRuntime.hasLoadedSchematic()) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_loaded", "No schematic loaded."));
            return;
        }
        if (parts.length != 5) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.nudge.usage", "Usage: /schematica nudge <dx> <dy> <dz>"));
            return;
        }
        try {
            int dx = Integer.parseInt(parts[2]);
            int dy = Integer.parseInt(parts[3]);
            int dz = Integer.parseInt(parts[4]);
            int x = SchematicaRuntime.originX + dx;
            int y = SchematicaRuntime.originY + dy;
            int z = SchematicaRuntime.originZ + dz;
            SchematicaRuntime.setOrigin(x, y, z);
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.nudge.done", "Projection nudged to [%d,%d,%d]", x, y, z));
        } catch (NumberFormatException e) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.offsets_int", "Offsets must be integers."));
        }
    }

    private void handleRotate(HandleChatCommandEvent event, String[] parts) {
        if (!SchematicaRuntime.hasLoadedSchematic()) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_loaded", "No schematic loaded."));
            return;
        }
        if (parts.length != 3) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.rotate.usage", "Usage: /schematica rotate <90|180|270>"));
            return;
        }

        int angle;
        try {
            angle = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.rotate.angle", "Angle must be one of: 90, 180, 270."));
            return;
        }
        if (!(angle == 90 || angle == 180 || angle == 270)) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.rotate.angle", "Angle must be one of: 90, 180, 270."));
            return;
        }

        ISchematic rotated = rotateSchematic(SchematicaRuntime.loadedSchematic, angle);
        SchematicaRuntime.setLoadedSchematic(
                rotated,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                SchematicaRuntime.loadedSchematicName);
        event.getPlayer().addChatMessage(I18n.trf(
                "schematica.command.rotate.done",
                "Rotated projection %d degrees. New size: %dx%dx%d",
                angle, rotated.getWidth(), rotated.getHeight(), rotated.getLength()));
    }

    private void handleMirror(HandleChatCommandEvent event, String[] parts) {
        if (!SchematicaRuntime.hasLoadedSchematic()) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_loaded", "No schematic loaded."));
            return;
        }
        if (parts.length != 3) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.mirror.usage", "Usage: /schematica mirror <x|z>"));
            return;
        }
        String axis = parts[2].toLowerCase(Locale.ROOT);
        if (!"x".equals(axis) && !"z".equals(axis)) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.mirror.axis", "Mirror axis must be x or z."));
            return;
        }

        ISchematic mirrored = mirrorSchematic(SchematicaRuntime.loadedSchematic, axis);
        SchematicaRuntime.setLoadedSchematic(
                mirrored,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                SchematicaRuntime.loadedSchematicName);
        event.getPlayer().addChatMessage(I18n.trf("schematica.command.mirror.done", "Mirrored projection on %s axis.", axis));
    }

    private void handlePaste(HandleChatCommandEvent event, String[] parts) {
        EntityPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }
        if (!player.inCreativeMode()) {
            player.addChatMessage(I18n.tr(
                    "schematica.command.paste.disabled",
                    "Direct paste is disabled in survival. Use the printer GUI to print."));
            return;
        }
        if (!SchematicaRuntime.hasLoadedSchematic()) {
            player.addChatMessage(I18n.tr("schematica.command.no_loaded", "No schematic loaded."));
            return;
        }
        World world = event.getWorld();
        if (world == null) {
            player.addChatMessage(I18n.tr("schematica.command.no_world", "No world available."));
            return;
        }

        PasteMode mode = parsePasteMode(parts);
        if (mode == null) {
            player.addChatMessage(I18n.tr(
                    "schematica.command.paste.usage",
                    "Usage: /schematica paste [replace|solid|nonair]"));
            return;
        }

        ISchematic schematic = SchematicaRuntime.loadedSchematic;
        long volume = (long) schematic.getWidth() * schematic.getHeight() * schematic.getLength();
        if (volume <= 0L || volume > MAX_PASTE_VOLUME) {
            player.addChatMessage(I18n.trf(
                    "schematica.command.paste.too_large",
                    "Schematic too large to paste. Max blocks: %d",
                    MAX_PASTE_VOLUME));
            return;
        }

        String boundsError = validateRegionBounds(
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                schematic.getWidth(),
                schematic.getHeight(),
                schematic.getLength());
        if (boundsError != null) {
            player.addChatMessage(I18n.trf("schematica.command.paste.bounds", "Cannot paste: %s", boundsError));
            return;
        }

        MaterialCheckResult materialCheck = checkAndConsumePasteMaterials(
                player,
                world,
                schematic,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                mode);
        if (!materialCheck.canPaste) {
            player.addChatMessage(I18n.trf(
                    "schematica.command.paste.material_shortage",
                    "Not enough materials to paste. Missing %d types:",
                    materialCheck.shortages.size()));
            List<String> lines = buildCompactShortageLines(materialCheck.shortages, 3);
            int maxLines = 12;
            for (int i = 0; i < lines.size() && i < maxLines; ++i) {
                player.addChatMessage(lines.get(i));
            }
            if (lines.size() > maxLines) {
                player.addChatMessage(I18n.trf(
                        "schematica.command.paste.material_more",
                        "... omitted %d more lines.",
                        lines.size() - maxLines));
            }
            return;
        }

        Schematic undo = captureWorldRegion(
                world,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                schematic.getWidth(),
                schematic.getHeight(),
                schematic.getLength(),
                copyIcon(schematic));
        if (undo == null) {
            player.addChatMessage(I18n.tr("schematica.command.paste.undo_capture_failed", "Failed to capture undo snapshot."));
            return;
        }
        SchematicaRuntime.setUndoSnapshot(
                undo,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                SchematicaRuntime.loadedSchematicName);

        PasteResult result = pasteSchematic(
                world,
                schematic,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                mode);
        String name = SchematicaRuntime.loadedSchematicName == null
                ? I18n.tr("schematica.command.value.unknown", "<unknown>")
                : SchematicaRuntime.loadedSchematicName;
        String failedSuffix = result.failed > 0
                ? I18n.trf("schematica.command.paste.failed_suffix", ", failed=%d", result.failed)
                : "";
        player.addChatMessage(I18n.trf(
                "schematica.command.paste.done",
                "Pasted %s mode=%s, materialsUsed=%d, materialTypes=%d: placed=%d, cleared=%d, containersEmptied=%d, unchanged=%d%s",
                name, mode.id, materialCheck.totalConsumed, materialCheck.materialTypes,
                result.placed, result.cleared, result.containersEmptied, result.unchanged, failedSuffix));
    }

    private void handleUndo(HandleChatCommandEvent event) {
        if (!SchematicaRuntime.hasUndoSnapshot()) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.undo.no_snapshot", "No undo snapshot."));
            return;
        }
        if (event.getWorld() == null) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_world", "No world available."));
            return;
        }

        ISchematic undoSchematic = SchematicaRuntime.lastUndoSchematic;
        String undoBoundsError = validateRegionBounds(
                SchematicaRuntime.lastUndoOriginX,
                SchematicaRuntime.lastUndoOriginY,
                SchematicaRuntime.lastUndoOriginZ,
                undoSchematic.getWidth(),
                undoSchematic.getHeight(),
                undoSchematic.getLength());
        if (undoBoundsError != null) {
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.undo.bounds", "Cannot undo: %s", undoBoundsError));
            return;
        }

        PasteResult result = pasteSchematic(
                event.getWorld(),
                undoSchematic,
                SchematicaRuntime.lastUndoOriginX,
                SchematicaRuntime.lastUndoOriginY,
                SchematicaRuntime.lastUndoOriginZ,
                PasteMode.REPLACE);
        String label = SchematicaRuntime.lastUndoLabel == null
                ? I18n.tr("schematica.command.value.unknown", "<unknown>")
                : SchematicaRuntime.lastUndoLabel;
        String failedSuffix = result.failed > 0
                ? I18n.trf("schematica.command.paste.failed_suffix", ", failed=%d", result.failed)
                : "";
        SchematicaRuntime.clearUndoSnapshot();
        event.getPlayer().addChatMessage(I18n.trf(
                "schematica.command.undo.done",
                "Undo restored %s: placed=%d, cleared=%d, containersEmptied=%d, unchanged=%d%s",
                label, result.placed, result.cleared, result.containersEmptied, result.unchanged,
                failedSuffix));
    }

    private void handleSave(HandleChatCommandEvent event, String[] parts) {
        if (parts.length < 9) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.save.usage", "Usage: /schematica save <x1> <y1> <z1> <x2> <y2> <z2> <name>"));
            return;
        }
        int x1, y1, z1, x2, y2, z2;
        try {
            x1 = Integer.parseInt(parts[2]);
            y1 = Integer.parseInt(parts[3]);
            z1 = Integer.parseInt(parts[4]);
            x2 = Integer.parseInt(parts[5]);
            y2 = Integer.parseInt(parts[6]);
            z2 = Integer.parseInt(parts[7]);
        } catch (NumberFormatException e) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.coords_int", "Coordinates must be integers."));
            return;
        }

        String rawName = String.join(" ", Arrays.copyOfRange(parts, 8, parts.length)).trim();
        File outFile = resolveSchematicFile(rawName);
        if (outFile == null) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.file_name_invalid", "Invalid schematic name."));
            return;
        }
        saveSelection(event, x1, y1, z1, x2, y2, z2, outFile, false);
    }

    private void handleCreate(HandleChatCommandEvent event, String[] parts) {
        if (parts.length < 3) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.create.usage", "Usage: /schematica create <name>"));
            return;
        }
        if (!SchematicaRuntime.hasSelection()) {
            event.getPlayer().addChatMessage(I18n.tr(
                    "schematica.command.create.selection_incomplete",
                    "Selection incomplete. Use stick: RightClick=Pos1, Shift+RightClick=Pos2."));
            return;
        }

        String rawName = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length)).trim();
        File outFile = resolveSchematicFile(rawName);
        if (outFile == null) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.file_name_invalid", "Invalid schematic name."));
            return;
        }

        saveSelection(
                event,
                SchematicaRuntime.selectionPos1X, SchematicaRuntime.selectionPos1Y, SchematicaRuntime.selectionPos1Z,
                SchematicaRuntime.selectionPos2X, SchematicaRuntime.selectionPos2Y, SchematicaRuntime.selectionPos2Z,
                outFile,
                true);
    }

    private void handlePrinterCommand(HandleChatCommandEvent event, String[] parts, boolean clientWorld) {
        if (parts.length < 3) {
            event.setExecuteSuccess(true);
            event.getPlayer().addChatMessage(I18n.tr(
                    "schematica.command.printer.usage",
                    "Usage: /schematica printer <print|undo|provide|providefood|health|sync> ..."));
            return;
        }

        String action = parts[2].toLowerCase(Locale.ROOT);
        if ("print".equals(action)) {
            if (clientWorld) {
                event.setExecuteSuccess(false);
                return;
            }
            event.setExecuteSuccess(true);
            handlePrinterPrint(event, parts);
            return;
        }

        if ("undo".equals(action)) {
            if (clientWorld) {
                event.setExecuteSuccess(false);
                return;
            }
            event.setExecuteSuccess(true);
            handlePrinterUndo(event, parts);
            return;
        }

        if ("provide".equals(action)) {
            if (clientWorld) {
                event.setExecuteSuccess(false);
                return;
            }
            event.setExecuteSuccess(true);
            handlePrinterProvide(event, parts, true);
            return;
        }

        if ("providefood".equals(action)) {
            if (clientWorld) {
                event.setExecuteSuccess(false);
                return;
            }
            event.setExecuteSuccess(true);
            handlePrinterProvideFood(event, parts, true);
            return;
        }

        if ("health".equals(action)) {
            if (clientWorld) {
                event.setExecuteSuccess(false);
                return;
            }
            event.setExecuteSuccess(true);
            handlePrinterHealth(event, parts);
            return;
        }

        if ("sync".equals(action)) {
            if (clientWorld) {
                event.setExecuteSuccess(false);
                return;
            }
            event.setExecuteSuccess(true);
            handlePrinterSync(event, parts, false);
            return;
        }

        event.setExecuteSuccess(true);
        event.getPlayer().addChatMessage(I18n.tr(
                "schematica.command.printer.usage",
                "Usage: /schematica printer <print|undo|provide|providefood|health|sync> ..."));
    }

    private void handlePrinterPrint(HandleChatCommandEvent event, String[] parts) {
        SchematicaRuntime.clearProjectionAlertMarker();
        if (parts.length < 6) {
            event.getPlayer().addChatMessage(I18n.tr(
                    "schematica.command.printer.print.usage",
                    "Usage: /schematica printer print <x> <y> <z> [replace|solid|nonair]"));
            return;
        }
        int printerX;
        int printerY;
        int printerZ;
        try {
            printerX = Integer.parseInt(parts[3]);
            printerY = Integer.parseInt(parts[4]);
            printerZ = Integer.parseInt(parts[5]);
        } catch (NumberFormatException e) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.coords_int", "Coordinates must be integers."));
            return;
        }
        if (event.getWorld() == null) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_world", "No world available."));
            return;
        }

        IInventory printerInventory = getPrinterInventory(event.getWorld(), printerX, printerY, printerZ);
        if (printerInventory == null) {
            event.getPlayer().addChatMessage(I18n.trf(
                    "schematica.command.printer.not_found",
                    "Printer not found at [%d,%d,%d].",
                    printerX, printerY, printerZ));
            return;
        }

        PasteMode mode = parsePasteMode(parts, 6);
        if (mode == null) {
            event.getPlayer().addChatMessage(I18n.tr(
                    "schematica.command.printer.print.usage",
                    "Usage: /schematica printer print <x> <y> <z> [replace|solid|nonair]"));
            return;
        }
        PrintProjectionContext projection = resolvePrinterProjectionContext(event.getPlayer());
        if (projection == null || projection.schematic == null) {
            event.getPlayer().addChatMessage(I18n.tr(
                    "schematica.command.printer.print.no_projection",
                    "No projection is available for printer print. Load one on server, or enable printer.clientProjectionUpload."));
            return;
        }

        ISchematic schematic = projection.schematic;
        int originX = projection.originX;
        int originY = projection.originY;
        int originZ = projection.originZ;
        String schematicName = projection.schematicName;

        String txnId = newPrinterTxnId("print");
        logPrinterTxn(event, txnId, "START", String.format(Locale.ROOT,
                "printer=[%d,%d,%d], mode=%s, source=%s",
                printerX, printerY, printerZ, mode.id, projection.uploadedFromClient ? "client_upload" : "server_loaded"));
        long volume = (long) schematic.getWidth() * schematic.getHeight() * schematic.getLength();
        if (volume <= 0L || volume > MAX_PASTE_VOLUME) {
            logPrinterTxn(event, txnId, "FAIL", String.format(Locale.ROOT, "invalid_volume=%d", volume));
            event.getPlayer().addChatMessage(I18n.trf(
                    "schematica.command.paste.too_large",
                    "Schematic too large to paste. Max blocks: %d",
                    MAX_PASTE_VOLUME));
            return;
        }

        String boundsError = validateRegionBounds(
                originX,
                originY,
                originZ,
                schematic.getWidth(),
                schematic.getHeight(),
                schematic.getLength());
        if (boundsError != null) {
            logPrinterTxn(event, txnId, "FAIL", "bounds=" + boundsError);
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.paste.bounds", "Cannot paste: %s", boundsError));
            return;
        }

        PrinterWorldStateSnapshot worldStateSnapshot = buildPrinterWorldStateSnapshot(
                event.getWorld(),
                schematic,
                originX,
                originY,
                originZ);
        PasteConflict conflict = worldStateSnapshot == null ? null : worldStateSnapshot.conflict;
        if (conflict != null) {
            SchematicaRuntime.setProjectionAlertMarker(
                    conflict.x,
                    conflict.y,
                    conflict.z,
                    PROJECTION_ALERT_MARKER_TTL_MS,
                    false);
            event.getPlayer().addChatMessage(I18n.trf(
                    "schematica.command.printer.print.blocked",
                    "Print blocked at [%d,%d,%d]: found %s[%d:%d], target is %s[%d:%d].",
                    conflict.x, conflict.y, conflict.z,
                    conflict.existingName, conflict.existingId, conflict.existingMeta,
                    conflict.targetName, conflict.targetId, conflict.targetMeta));
            logPrinterTxn(event, txnId, "FAIL", String.format(Locale.ROOT,
                    "collision=[%d,%d,%d]",
                    conflict.x, conflict.y, conflict.z));
            return;
        }

        MaterialCheckResult materialCheck = checkAndConsumePasteMaterialsFromInventory(
                printerInventory,
                event.getWorld(),
                schematic,
                originX,
                originY,
                originZ,
                mode,
                worldStateSnapshot);
        if (!materialCheck.canPaste) {
            logPrinterTxn(event, txnId, "FAIL", String.format(Locale.ROOT,
                    "material_shortage_types=%d",
                    materialCheck.shortages.size()));
            event.getPlayer().addChatMessage(I18n.trf(
                    "schematica.command.paste.material_shortage",
                    "Not enough materials to paste. Missing %d types:",
                    materialCheck.shortages.size()));
            List<String> lines = buildCompactShortageLines(materialCheck.shortages, 3);
            int maxLines = 12;
            for (int i = 0; i < lines.size() && i < maxLines; ++i) {
                event.getPlayer().addChatMessage(lines.get(i));
            }
            if (lines.size() > maxLines) {
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.paste.material_more",
                        "... omitted %d more lines.",
                        lines.size() - maxLines));
            }
            return;
        }

        List<ItemStack> consumedStacks = buildConsumedMaterialStacks(materialCheck);
        Schematic undo = captureWorldRegion(
                event.getWorld(),
                originX,
                originY,
                originZ,
                schematic.getWidth(),
                schematic.getHeight(),
                schematic.getLength(),
                copyIcon(schematic));
        if (undo == null) {
            int returned = refundPrinterUndoMaterials(
                    event.getWorld(),
                    printerX,
                    printerY,
                    printerZ,
                    printerInventory,
                    consumedStacks);
            syncPrinterInventoryToClients(event.getWorld(), printerX, printerY, printerZ, printerInventory);
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.paste.undo_capture_failed", "Failed to capture undo snapshot."));
            if (materialCheck.totalConsumed > 0) {
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.printer.print.rollback",
                        "Print aborted; rollback attempted for %d consumed materials (returned=%d, overflow dropped nearby).",
                        materialCheck.totalConsumed,
                        returned));
            }
            logPrinterTxn(event, txnId, "FAIL", String.format(Locale.ROOT,
                    "undo_capture_failed, returned=%d",
                    returned));
            return;
        }
        SchematicaRuntime.setPrinterUndoSnapshot(
                undo,
                originX,
                originY,
                originZ,
                schematicName,
                printerX,
                printerY,
                printerZ,
                consumedStacks);

        String printerTaskKey = buildPrinterTaskKey(event.getWorld(), printerX, printerY, printerZ);
        synchronized (ACTIVE_PRINTER_PRINT_TASKS) {
            if (ACTIVE_PRINTER_PRINT_TASKS.containsKey(printerTaskKey)) {
                int returned = refundPrinterUndoMaterials(
                        event.getWorld(),
                        printerX,
                        printerY,
                        printerZ,
                        printerInventory,
                        consumedStacks);
                SchematicaRuntime.clearPrinterUndoSnapshot();
                syncPrinterInventoryToClients(event.getWorld(), printerX, printerY, printerZ, printerInventory);
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.printer.print.rollback",
                        "Print aborted; rollback attempted for %d consumed materials (returned=%d, overflow dropped nearby).",
                        materialCheck.totalConsumed,
                        returned));
                logPrinterTxn(event, txnId, "FAIL", "printer_busy_existing_task");
                return;
            }
            if (ACTIVE_PRINTER_PRINT_TASKS.size() >= PRINTER_PRINT_MAX_ACTIVE_TASKS) {
                int returned = refundPrinterUndoMaterials(
                        event.getWorld(),
                        printerX,
                        printerY,
                        printerZ,
                        printerInventory,
                        consumedStacks);
                SchematicaRuntime.clearPrinterUndoSnapshot();
                syncPrinterInventoryToClients(event.getWorld(), printerX, printerY, printerZ, printerInventory);
                event.getPlayer().addChatMessage(I18n.tr("schematica.command.printer.print.queue_busy", "Printer queue is busy. Try again shortly."));
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.printer.print.rollback",
                        "Print aborted; rollback attempted for %d consumed materials (returned=%d, overflow dropped nearby).",
                        materialCheck.totalConsumed,
                        returned));
                logPrinterTxn(event, txnId, "FAIL", "printer_queue_full");
                return;
            }

            ScheduledPrinterPrintTask task = new ScheduledPrinterPrintTask(
                    printerTaskKey,
                    event.getWorld(),
                    schematic,
                    schematicName,
                    originX,
                    originY,
                    originZ,
                    mode,
                    printerX,
                    printerY,
                    printerZ,
                    event.getPlayer() == null ? -1 : event.getPlayer().entityId,
                    projection.uploadedFromClient,
                    materialCheck.totalConsumed,
                    materialCheck.materialTypes,
                    materialCheck.requiredEmeralds,
                    txnId);
            ACTIVE_PRINTER_PRINT_TASKS.put(printerTaskKey, task);
        }
        syncPrinterInventoryToClients(event.getWorld(), printerX, printerY, printerZ, printerInventory);
        event.getPlayer().addChatMessage(I18n.trf(
                "schematica.command.printer.print.queued",
                "Printer [%d,%d,%d] queued print task: %s mode=%s, estimated blocks=%d.",
                printerX,
                printerY,
                printerZ,
                schematicName == null ? I18n.tr("schematica.command.value.unknown", "<unknown>") : schematicName,
                mode.id,
                schematic.getWidth() * schematic.getHeight() * schematic.getLength()));
        logPrinterTxn(event, txnId, "QUEUE", String.format(Locale.ROOT,
                "queued, blocks=%d, perTick=%d",
                schematic.getWidth() * schematic.getHeight() * schematic.getLength(),
                PRINTER_PRINT_BLOCKS_PER_TICK));
    }

    private PrintProjectionContext resolvePrinterProjectionContext(EntityPlayer player) {
        if (player != null && SchematicaPrinterConfig.isClientProjectionUploadEnabled()) {
            SchematicaRuntime.UploadedPrinterProjection uploaded = SchematicaRuntime.getUploadedPrinterProjection(player.entityId);
            if (uploaded != null && uploaded.schematic != null) {
                return new PrintProjectionContext(
                        uploaded.schematic,
                        uploaded.originX,
                        uploaded.originY,
                        uploaded.originZ,
                        uploaded.schematicName,
                        true);
            }
        }
        if (!SchematicaRuntime.hasLoadedSchematic()) {
            return null;
        }
        return new PrintProjectionContext(
                SchematicaRuntime.loadedSchematic,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                SchematicaRuntime.loadedSchematicName,
                false);
    }

    private void handlePrinterUndo(HandleChatCommandEvent event, String[] parts) {
        if (parts.length < 6) {
            event.getPlayer().addChatMessage(I18n.tr(
                    "schematica.command.printer.undo.usage",
                    "Usage: /schematica printer undo <x> <y> <z>"));
            return;
        }

        int printerX;
        int printerY;
        int printerZ;
        try {
            printerX = Integer.parseInt(parts[3]);
            printerY = Integer.parseInt(parts[4]);
            printerZ = Integer.parseInt(parts[5]);
        } catch (NumberFormatException e) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.coords_int", "Coordinates must be integers."));
            return;
        }

        World world = event.getWorld();
        if (world == null) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_world", "No world available."));
            return;
        }

        IInventory printerInventory = getPrinterInventory(world, printerX, printerY, printerZ);
        if (printerInventory == null) {
            event.getPlayer().addChatMessage(I18n.trf(
                    "schematica.command.printer.not_found",
                    "Printer not found at [%d,%d,%d].",
                    printerX, printerY, printerZ));
            return;
        }
        String txnId = newPrinterTxnId("undo");
        logPrinterTxn(event, txnId, "START", String.format(Locale.ROOT,
                "printer=[%d,%d,%d]",
                printerX, printerY, printerZ));

        if (!SchematicaRuntime.hasPrinterUndoSnapshotAt(printerX, printerY, printerZ)) {
            event.getPlayer().addChatMessage(I18n.trf(
                    "schematica.command.printer.undo.no_snapshot",
                    "No undo snapshot for printer [%d,%d,%d].",
                    printerX, printerY, printerZ));
            logPrinterTxn(event, txnId, "FAIL", "no_snapshot");
            return;
        }
        if (!SchematicaRuntime.hasPrinterPrintedSnapshotAt(printerX, printerY, printerZ)) {
            event.getPlayer().addChatMessage(I18n.trf(
                    "schematica.command.printer.undo.integrity_missing",
                    "Cannot verify undo integrity for printer [%d,%d,%d] (missing print snapshot).",
                    printerX, printerY, printerZ));
            logPrinterTxn(event, txnId, "FAIL", "integrity_snapshot_missing");
            return;
        }

        PasteConflict integrityMismatch = findFirstSchematicMismatch(
                world,
                SchematicaRuntime.lastPrinterPrintedSchematic,
                SchematicaRuntime.lastPrinterPrintedOriginX,
                SchematicaRuntime.lastPrinterPrintedOriginY,
                SchematicaRuntime.lastPrinterPrintedOriginZ,
                false);
        if (integrityMismatch != null) {
            SchematicaRuntime.setProjectionAlertMarker(
                    integrityMismatch.x,
                    integrityMismatch.y,
                    integrityMismatch.z,
                    PROJECTION_ALERT_MARKER_TTL_MS,
                    true);
            event.getPlayer().addChatMessage(I18n.trf(
                    "schematica.command.printer.undo.integrity_failed",
                    "Undo blocked at [%d,%d,%d]: expected %s[%d:%d], found %s[%d:%d].",
                    integrityMismatch.x, integrityMismatch.y, integrityMismatch.z,
                    integrityMismatch.targetName, integrityMismatch.targetId, integrityMismatch.targetMeta,
                    integrityMismatch.existingName, integrityMismatch.existingId, integrityMismatch.existingMeta));
            logPrinterTxn(event, txnId, "FAIL", String.format(Locale.ROOT,
                    "integrity_mismatch=[%d,%d,%d], expected=%d:%d, actual=%d:%d",
                    integrityMismatch.x, integrityMismatch.y, integrityMismatch.z,
                    integrityMismatch.targetId, integrityMismatch.targetMeta,
                    integrityMismatch.existingId, integrityMismatch.existingMeta));
            return;
        }

        ISchematic undoSchematic = SchematicaRuntime.lastPrinterUndoSchematic;
        String undoBoundsError = validateRegionBounds(
                SchematicaRuntime.lastPrinterUndoOriginX,
                SchematicaRuntime.lastPrinterUndoOriginY,
                SchematicaRuntime.lastPrinterUndoOriginZ,
                undoSchematic.getWidth(),
                undoSchematic.getHeight(),
                undoSchematic.getLength());
        if (undoBoundsError != null) {
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.printer.undo.bounds", "Cannot printer-undo: %s", undoBoundsError));
            logPrinterTxn(event, txnId, "FAIL", "bounds=" + undoBoundsError);
            return;
        }

        PasteResult result = pasteSchematic(
                world,
                undoSchematic,
                SchematicaRuntime.lastPrinterUndoOriginX,
                SchematicaRuntime.lastPrinterUndoOriginY,
                SchematicaRuntime.lastPrinterUndoOriginZ,
                PasteMode.REPLACE);
        String label = SchematicaRuntime.lastPrinterUndoLabel == null
                ? I18n.tr("schematica.command.value.unknown", "<unknown>")
                : SchematicaRuntime.lastPrinterUndoLabel;
        int materialsReturned = refundPrinterUndoMaterials(
                world,
                printerX,
                printerY,
                printerZ,
                printerInventory,
                SchematicaRuntime.lastPrinterUndoRefundStacks);
        syncPrinterInventoryToClients(world, printerX, printerY, printerZ, printerInventory);
        String failedSuffix = result.failed > 0
                ? I18n.trf("schematica.command.paste.failed_suffix", ", failed=%d", result.failed)
                : "";
        SchematicaRuntime.clearPrinterUndoSnapshot();
        SchematicaRuntime.clearProjectionAlertMarker();
        event.getPlayer().addChatMessage(I18n.trf(
                "schematica.command.printer.undo.done",
                "Printer [%d,%d,%d] undo restored %s: placed=%d, cleared=%d, containersEmptied=%d, unchanged=%d, materialsReturned=%d%s",
                printerX, printerY, printerZ, label, result.placed, result.cleared, result.containersEmptied, result.unchanged, materialsReturned, failedSuffix));
        logPrinterTxn(event, txnId, "OK", String.format(Locale.ROOT,
                "placed=%d, cleared=%d, unchanged=%d, failed=%d, materialsReturned=%d",
                result.placed, result.cleared, result.unchanged, result.failed, materialsReturned));
    }

    private boolean handlePrinterProvide(HandleChatCommandEvent event, String[] parts, boolean sendFeedback) {
        if (parts.length < 8) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.provide.usage",
                        "Usage: /schematica printer provide <x> <y> <z> <itemId> <subtype> [count]"));
            }
            return false;
        }

        int printerX;
        int printerY;
        int printerZ;
        int itemId;
        int subtype;
        int amount = Integer.MAX_VALUE;
        String txnId = null;
        try {
            printerX = Integer.parseInt(parts[3]);
            printerY = Integer.parseInt(parts[4]);
            printerZ = Integer.parseInt(parts[5]);
            itemId = Integer.parseInt(parts[6]);
            subtype = Integer.parseInt(parts[7]);
            if (parts.length >= 9) {
                amount = Integer.parseInt(parts[8]);
            }
        } catch (NumberFormatException e) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.provide.usage",
                        "Usage: /schematica printer provide <x> <y> <z> <itemId> <subtype> [count]"));
            }
            return false;
        }
        if (sendFeedback) {
            txnId = newPrinterTxnId("provide");
            logPrinterTxn(event, txnId, "START", String.format(Locale.ROOT,
                    "printer=[%d,%d,%d], item=%d:%d, request=%d",
                    printerX, printerY, printerZ, itemId, subtype, amount));
        }

        if (itemId <= 0 || itemId >= Item.itemsList.length || Item.itemsList[itemId] == null) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.printer.provide.invalid_item",
                        "Invalid item id: %d",
                        itemId));
                logPrinterTxn(event, txnId, "FAIL", "invalid_item_id=" + itemId);
            }
            return true;
        }
        if (amount <= 0) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.provide.usage",
                        "Usage: /schematica printer provide <x> <y> <z> <itemId> <subtype> [count]"));
                logPrinterTxn(event, txnId, "FAIL", "invalid_amount=" + amount);
            }
            return false;
        }

        World world = event.getWorld();
        EntityPlayer player = event.getPlayer();
        if (world == null || player == null || player.inventory == null) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.provide.internal",
                        "Printer provide failed (missing world/player/inventory)."));
                logPrinterTxn(event, txnId, "FAIL", "missing_world_or_player_inventory");
            }
            return true;
        }

        IInventory printerInventory = getPrinterInventory(world, printerX, printerY, printerZ);
        if (printerInventory == null) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.printer.not_found",
                        "Printer not found at [%d,%d,%d].",
                        printerX, printerY, printerZ));
                logPrinterTxn(event, txnId, "FAIL", "printer_not_found");
            }
            return true;
        }

        int moved = transferFromPlayerToPrinter(player, printerInventory, itemId, subtype, amount);
        if (moved > 0) {
            syncPrinterInventoryToClients(world, printerX, printerY, printerZ, printerInventory);
        }
        if (sendFeedback) {
            if (moved <= 0) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.provide.none",
                        "No matching items were moved."));
                logPrinterTxn(event, txnId, "OK", "moved=0");
            } else {
                ItemStack display = new ItemStack(Item.itemsList[itemId], 1, subtype);
                int stored = countMaterialInInventory(printerInventory, new MaterialKey(itemId, subtype));
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.printer.provide.done",
                        "Provided %d x %s to printer [%d,%d,%d]. Stored=%d",
                        moved, display.getDisplayName(), printerX, printerY, printerZ, stored));
                logPrinterTxn(event, txnId, "OK", String.format(Locale.ROOT,
                        "moved=%d, stored=%d",
                        moved, stored));
            }
        }
        return true;
    }

    private boolean handlePrinterProvideFood(HandleChatCommandEvent event, String[] parts, boolean sendFeedback) {
        if (parts.length < 6) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.provide_food.usage",
                        "Usage: /schematica printer providefood <x> <y> <z> [hungerTarget]"));
            }
            return false;
        }

        int printerX;
        int printerY;
        int printerZ;
        int hungerTarget = Integer.MAX_VALUE;
        String txnId = null;
        try {
            printerX = Integer.parseInt(parts[3]);
            printerY = Integer.parseInt(parts[4]);
            printerZ = Integer.parseInt(parts[5]);
            if (parts.length >= 7) {
                hungerTarget = Integer.parseInt(parts[6]);
            }
        } catch (NumberFormatException e) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.provide_food.usage",
                        "Usage: /schematica printer providefood <x> <y> <z> [hungerTarget]"));
            }
            return false;
        }
        if (hungerTarget <= 0) {
            hungerTarget = Integer.MAX_VALUE;
        }
        if (sendFeedback) {
            txnId = newPrinterTxnId("providefood");
            logPrinterTxn(event, txnId, "START", String.format(Locale.ROOT,
                    "printer=[%d,%d,%d], hungerTarget=%d",
                    printerX, printerY, printerZ, hungerTarget));
        }

        World world = event.getWorld();
        EntityPlayer player = event.getPlayer();
        if (world == null || player == null || player.inventory == null) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.provide.internal",
                        "Printer provide failed (missing world/player/inventory)."));
                logPrinterTxn(event, txnId, "FAIL", "missing_world_or_player_inventory");
            }
            return true;
        }

        IInventory printerInventory = getPrinterInventory(world, printerX, printerY, printerZ);
        if (printerInventory == null) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.printer.not_found",
                        "Printer not found at [%d,%d,%d].",
                        printerX, printerY, printerZ));
                logPrinterTxn(event, txnId, "FAIL", "printer_not_found");
            }
            return true;
        }

        FoodTransferResult moved = transferFoodFromPlayerToPrinter(player, printerInventory, hungerTarget);
        if (moved.movedCount > 0) {
            syncPrinterInventoryToClients(world, printerX, printerY, printerZ, printerInventory);
        }
        if (sendFeedback) {
            if (moved.movedCount <= 0) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.provide.none",
                        "No matching items were moved."));
                logPrinterTxn(event, txnId, "OK", "moved=0");
            } else {
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.printer.provide_food.done",
                        "Provided food to printer [%d,%d,%d]: items=%d, hunger=%d",
                        printerX, printerY, printerZ, moved.movedCount, moved.movedHunger));
                logPrinterTxn(event, txnId, "OK", String.format(Locale.ROOT,
                        "movedCount=%d, movedHunger=%d",
                        moved.movedCount, moved.movedHunger));
            }
        }
        return true;
    }

    private void handlePrinterHealth(HandleChatCommandEvent event, String[] parts) {
        if (parts.length < 6) {
            event.getPlayer().addChatMessage(I18n.tr(
                    "schematica.command.printer.health.usage",
                    "Usage: /schematica printer health <x> <y> <z>"));
            return;
        }

        int printerX;
        int printerY;
        int printerZ;
        try {
            printerX = Integer.parseInt(parts[3]);
            printerY = Integer.parseInt(parts[4]);
            printerZ = Integer.parseInt(parts[5]);
        } catch (NumberFormatException e) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.coords_int", "Coordinates must be integers."));
            return;
        }

        World world = event.getWorld();
        if (world == null) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_world", "No world available."));
            return;
        }

        String txnId = newPrinterTxnId("health");
        logPrinterTxn(event, txnId, "START", String.format(Locale.ROOT,
                "printer=[%d,%d,%d]",
                printerX, printerY, printerZ));

        int blockId = world.getBlockId(printerX, printerY, printerZ);
        boolean isPrinterBlock = blockId == SchematicaBlocks.SCHEMATICA_PRINTER.blockID;
        TileEntity tileEntity = world.getBlockTileEntity(printerX, printerY, printerZ);
        IInventory inventory = tileEntity instanceof IInventory ? (IInventory) tileEntity : null;
        int totalSlots = inventory == null ? 0 : inventory.getSizeInventory();
        int usedSlots = 0;
        int totalItems = 0;
        if (inventory != null) {
            for (int slot = 0; slot < inventory.getSizeInventory(); ++slot) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack == null || stack.stackSize <= 0) {
                    continue;
                }
                ++usedSlots;
                totalItems += stack.stackSize;
            }
        }
        boolean hasUndoSnapshot = SchematicaRuntime.hasPrinterUndoSnapshotAt(printerX, printerY, printerZ);
        String blockName = safeBlockName(blockId);
        String tileName = tileEntity == null ? "<none>" : tileEntity.getClass().getSimpleName();
        event.getPlayer().addChatMessage(I18n.trf(
                "schematica.command.printer.health.summary",
                "Printer health [%d,%d,%d]: block=%s[%d], isPrinter=%s, tile=%s, inventory=%s, slots=%d/%d, storedItems=%d, undoSnapshot=%s",
                printerX, printerY, printerZ,
                blockName, blockId,
                isPrinterBlock ? "true" : "false",
                tileName,
                inventory == null ? "missing" : "ok",
                usedSlots, totalSlots, totalItems,
                hasUndoSnapshot ? "true" : "false"));
        logPrinterTxn(event, txnId, "OK", String.format(Locale.ROOT,
                "isPrinter=%s, inventory=%s, slots=%d/%d, storedItems=%d, undoSnapshot=%s",
                isPrinterBlock ? "true" : "false",
                inventory == null ? "missing" : "ok",
                usedSlots, totalSlots, totalItems,
                hasUndoSnapshot ? "true" : "false"));
    }

    private void handlePrinterSync(HandleChatCommandEvent event, String[] parts, boolean sendFeedback) {
        if (parts.length < 6) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.sync.usage",
                        "Usage: /schematica printer sync <x> <y> <z>"));
            }
            return;
        }
        int printerX;
        int printerY;
        int printerZ;
        try {
            printerX = Integer.parseInt(parts[3]);
            printerY = Integer.parseInt(parts[4]);
            printerZ = Integer.parseInt(parts[5]);
        } catch (NumberFormatException e) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.tr("schematica.command.coords_int", "Coordinates must be integers."));
            }
            return;
        }
        World world = event.getWorld();
        if (world == null) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_world", "No world available."));
            }
            return;
        }
        IInventory printerInventory = getPrinterInventory(world, printerX, printerY, printerZ);
        if (printerInventory == null) {
            syncPrinterInventoryToClients(world, printerX, printerY, printerZ, null);
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.printer.not_found",
                        "Printer not found at [%d,%d,%d].",
                        printerX, printerY, printerZ));
            }
            return;
        }
        syncPrinterInventoryToClients(world, printerX, printerY, printerZ, printerInventory);
    }

    private IInventory getPrinterInventory(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        if (world.getBlockId(x, y, z) != SchematicaBlocks.SCHEMATICA_PRINTER.blockID) {
            return null;
        }
        TileEntity tileEntity = world.getBlockTileEntity(x, y, z);
        return tileEntity instanceof IInventory ? (IInventory) tileEntity : null;
    }

    private void syncPrinterInventoryToClients(World world, int x, int y, int z, IInventory printerInventory) {
        if (world == null || world.isRemote) {
            return;
        }
        Network.sendToAllPlayers(printerInventory == null
                ? S2CPrinterInventorySnapshotPacket.missing(x, y, z)
                : new S2CPrinterInventorySnapshotPacket(x, y, z, buildPrinterInventorySnapshotCounts(printerInventory)));
        updateRuntimePrinterInventorySnapshot(x, y, z, printerInventory, System.currentTimeMillis());
        if (printerInventory != null) {
            printerInventory.onInventoryChanged();
        }
        world.markBlockForUpdate(x, y, z);
        int blockId = world.getBlockId(x, y, z);
        if (blockId > 0) {
            world.func_96440_m(x, y, z, blockId);
        }
    }

    private void updateRuntimePrinterInventorySnapshot(int x, int y, int z, IInventory printerInventory, long updatedAtMs) {
        Map<String, Integer> counts = buildPrinterInventorySnapshotCounts(printerInventory);
        if (counts == null) {
            SchematicaRuntime.clearPrinterInventorySnapshot(x, y, z);
            return;
        }
        SchematicaRuntime.setPrinterInventorySnapshot(x, y, z, counts, updatedAtMs);
    }

    private Map<String, Integer> buildPrinterInventorySnapshotCounts(IInventory printerInventory) {
        if (printerInventory == null) {
            return null;
        }
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (int slot = 0; slot < printerInventory.getSizeInventory(); ++slot) {
            ItemStack stack = printerInventory.getStackInSlot(slot);
            if (stack == null || stack.itemID <= 0 || stack.stackSize <= 0) {
                continue;
            }
            String key = stack.itemID + ":" + stack.getItemSubtype();
            Integer old = counts.get(key);
            counts.put(key, old == null ? stack.stackSize : old + stack.stackSize);
        }
        return counts;
    }

    private int transferFromPlayerToPrinter(EntityPlayer player, IInventory printerInventory, int itemId, int subtype, int maxAmount) {
        if (player == null || player.inventory == null || printerInventory == null || maxAmount <= 0) {
            return 0;
        }
        int remaining = maxAmount;
        int moved = 0;
        for (int slot = 0; slot < player.inventory.mainInventory.length && remaining > 0; ++slot) {
            ItemStack source = player.inventory.mainInventory[slot];
            if (source == null || source.itemID != itemId || source.getItemSubtype() != subtype || source.stackSize <= 0) {
                continue;
            }
            int request = Math.min(remaining, source.stackSize);
            int inserted = insertIntoInventory(printerInventory, source, request);
            if (inserted <= 0) {
                continue;
            }

            source.stackSize -= inserted;
            moved += inserted;
            remaining -= inserted;
            if (source.stackSize <= 0) {
                player.inventory.setInventorySlotContents(slot, null);
            } else if (player.worldObj != null && !player.worldObj.isRemote) {
                player.inventory.inventorySlotChangedOnServer(slot);
            }
        }
        if (moved > 0) {
            player.inventory.onInventoryChanged();
            printerInventory.onInventoryChanged();
        }
        return moved;
    }

    private FoodTransferResult transferFoodFromPlayerToPrinter(EntityPlayer player, IInventory printerInventory, int hungerTarget) {
        FoodTransferResult result = new FoodTransferResult();
        if (player == null || player.inventory == null || printerInventory == null || hungerTarget <= 0) {
            return result;
        }
        int remaining = hungerTarget;
        for (int slot = 0; slot < player.inventory.mainInventory.length && remaining > 0; ++slot) {
            ItemStack source = player.inventory.mainInventory[slot];
            if (source == null || source.stackSize <= 0 || source.itemID <= 0) {
                continue;
            }
            int valuePerItem = getFoodHungerValuePerItem(source);
            if (valuePerItem <= 0) {
                continue;
            }
            int needCount = (remaining + valuePerItem - 1) / valuePerItem;
            int request = Math.min(source.stackSize, Math.max(1, needCount));
            int inserted = insertIntoInventory(printerInventory, source, request);
            if (inserted <= 0) {
                continue;
            }
            source.stackSize -= inserted;
            if (source.stackSize <= 0) {
                player.inventory.setInventorySlotContents(slot, null);
            } else {
                player.inventory.inventorySlotChangedOnServer(slot);
            }
            result.movedCount += inserted;
            result.movedHunger += inserted * valuePerItem;
            remaining -= inserted * valuePerItem;
        }
        if (result.movedCount > 0) {
            player.inventory.onInventoryChanged();
            printerInventory.onInventoryChanged();
        }
        return result;
    }

    private int insertIntoInventory(IInventory inventory, ItemStack schematica_survival, int amount) {
        if (inventory == null || schematica_survival == null || amount <= 0 || schematica_survival.itemID <= 0) {
            return 0;
        }
        int remaining = amount;
        int inventoryLimit = Math.max(1, inventory.getInventoryStackLimit());

        for (int slot = 0; slot < inventory.getSizeInventory() && remaining > 0; ++slot) {
            ItemStack existing = inventory.getStackInSlot(slot);
            if (existing == null) {
                continue;
            }
            if (existing.itemID != schematica_survival.itemID || existing.getItemSubtype() != schematica_survival.getItemSubtype()) {
                continue;
            }
            int slotLimit = Math.min(inventoryLimit, existing.getMaxStackSize());
            if (existing.stackSize >= slotLimit) {
                continue;
            }
            int canAdd = Math.min(remaining, slotLimit - existing.stackSize);
            if (canAdd <= 0) {
                continue;
            }
            existing.stackSize += canAdd;
            remaining -= canAdd;
            if (inventory instanceof InventoryPlayer) {
                InventoryPlayer inv = (InventoryPlayer) inventory;
                if (inv.player != null && inv.player.worldObj != null && !inv.player.worldObj.isRemote) {
                    inv.inventorySlotChangedOnServer(slot);
                }
            }
        }

        for (int slot = 0; slot < inventory.getSizeInventory() && remaining > 0; ++slot) {
            if (inventory.getStackInSlot(slot) != null) {
                continue;
            }
            ItemStack inserted = schematica_survival.copy();
            int slotLimit = Math.min(inventoryLimit, inserted.getMaxStackSize());
            int put = Math.min(remaining, slotLimit);
            inserted.stackSize = put;
            if (!inventory.isItemValidForSlot(slot, inserted)) {
                continue;
            }
            inventory.setInventorySlotContents(slot, inserted);
            remaining -= put;
        }
        return amount - remaining;
    }

    private int countMaterialInInventory(IInventory inventory, MaterialKey key) {
        if (inventory == null || key == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); ++slot) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null || stack.itemID != key.itemId || stack.getItemSubtype() != key.subtype || stack.stackSize <= 0) {
                continue;
            }
            total += stack.stackSize;
        }
        return total;
    }

    private List<ItemStack> buildConsumedMaterialStacks(MaterialCheckResult materialCheck) {
        return materialCheck == null
                ? new ArrayList<ItemStack>()
                : buildMaterialStacks(materialCheck.consumedByType);
    }

    private List<ItemStack> buildMaterialStacks(Map<MaterialKey, Integer> materialCounts) {
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        if (materialCounts == null || materialCounts.isEmpty()) {
            return stacks;
        }
        for (Map.Entry<MaterialKey, Integer> entry : materialCounts.entrySet()) {
            MaterialKey key = entry.getKey();
            int total = entry.getValue() == null ? 0 : entry.getValue();
            if (key == null || total <= 0) {
                continue;
            }
            if (key.itemId <= 0 || key.itemId >= Item.itemsList.length || Item.itemsList[key.itemId] == null) {
                continue;
            }
            ItemStack template = new ItemStack(Item.itemsList[key.itemId], 1, key.subtype);
            int maxPerStack = Math.max(1, template.getMaxStackSize());
            int remaining = total;
            while (remaining > 0) {
                int count = Math.min(remaining, maxPerStack);
                ItemStack stack = template.copy();
                stack.stackSize = count;
                stacks.add(stack);
                remaining -= count;
            }
        }
        return stacks;
    }

    private int restoreMaterialsToInventory(IInventory inventory, Map<MaterialKey, Integer> materialCounts) {
        if (inventory == null || materialCounts == null || materialCounts.isEmpty()) {
            return 0;
        }
        int restored = 0;
        List<ItemStack> stacks = buildMaterialStacks(materialCounts);
        for (ItemStack stack : stacks) {
            if (stack == null || stack.itemID <= 0 || stack.stackSize <= 0) {
                continue;
            }
            restored += insertIntoInventory(inventory, stack, stack.stackSize);
        }
        if (restored > 0) {
            inventory.onInventoryChanged();
        }
        return restored;
    }

    private int sumMaterialCounts(Map<MaterialKey, Integer> materialCounts) {
        if (materialCounts == null || materialCounts.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Integer value : materialCounts.values()) {
            if (value != null && value > 0) {
                total += value;
            }
        }
        return total;
    }

    private int refundPrinterUndoMaterials(World world, int x, int y, int z, IInventory printerInventory, List<ItemStack> refundStacks) {
        if (world == null || printerInventory == null || refundStacks == null || refundStacks.isEmpty()) {
            return 0;
        }
        int returned = 0;
        for (ItemStack stack : refundStacks) {
            if (stack == null || stack.itemID <= 0 || stack.stackSize <= 0) {
                continue;
            }
            int inserted = insertIntoInventory(printerInventory, stack, stack.stackSize);
            returned += inserted;
            int leftover = stack.stackSize - inserted;
            if (leftover > 0) {
                ItemStack drop = stack.copy();
                drop.stackSize = leftover;
                dropItemStack(world, x, y, z, drop);
            }
        }
        if (returned > 0) {
            printerInventory.onInventoryChanged();
        }
        return returned;
    }

    private void dropItemStack(World world, int x, int y, int z, ItemStack stack) {
        if (world == null || stack == null || stack.stackSize <= 0) {
            return;
        }
        EntityItem entity = new EntityItem(world, (float) x + 0.5F, (float) y + 0.5F, (float) z + 0.5F, stack);
        entity.motionX = 0.0F;
        entity.motionY = 0.1F;
        entity.motionZ = 0.0F;
        world.spawnEntityInWorld(entity);
    }

    private void saveSelection(HandleChatCommandEvent event, int x1, int y1, int z1, int x2, int y2, int z2, File outFile, boolean loadAfterSave) {
        if (event.getWorld() == null) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_world", "No world available."));
            return;
        }

        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;
        long volume = (long) width * height * length;
        if (volume <= 0L || volume > MAX_SAVE_VOLUME) {
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.save.selection_too_large", "Selection too large. Max blocks: %d", MAX_SAVE_VOLUME));
            return;
        }

        ItemStack icon = event.getPlayer().getHeldItemStack();
        if (icon == null) {
            icon = new ItemStack((Block) Block.stone);
        } else {
            icon = icon.copy();
        }
        Schematic schematic = captureWorldRegion(event.getWorld(), minX, minY, minZ, width, height, length, icon);
        if (schematic == null) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.save.capture_failed", "Failed to capture world region."));
            return;
        }
        if (!SchematicFormat.writeToFile(outFile, schematic)) {
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.save.failed", "Failed to save schematic: %s", outFile.getName()));
            return;
        }

        if (loadAfterSave) {
            SchematicaRuntime.setLoadedSchematic(schematic, minX, minY, minZ, outFile.getName());
            event.getPlayer().addChatMessage(I18n.trf(
                    "schematica.command.create.done",
                    "Created and loaded projection %s (%dx%dx%d, building-only) at [%d,%d,%d]",
                    outFile.getName(), width, height, length, minX, minY, minZ));
        } else {
            event.getPlayer().addChatMessage(I18n.trf(
                    "schematica.command.save.done",
                    "Saved %s (%dx%dx%d, building-only)",
                    outFile.getName(), width, height, length));
        }
    }

    private PasteResult pasteSchematic(World world, ISchematic schematic, int originX, int originY, int originZ, PasteMode mode) {
        PasteResult result = new PasteResult();
        if (mode == PasteMode.SOLID) {
            NonAirIndexCache cache = getOrBuildNonAirIndexCache(schematic);
            int width = schematic.getWidth();
            int length = schematic.getLength();
            for (int i = 0; i < cache.count; ++i) {
                int index = cache.indices[i];
                int x = index % width;
                int yz = index / width;
                int y = yz / length;
                int z = yz % length;
                int wx = originX + x;
                int wy = originY + y;
                int wz = originZ + z;

                Block block = schematic.getBlock(x, y, z);
                if (block == null || block.blockID == 0) {
                    continue;
                }

                int metadata = schematic.getBlockMetadata(x, y, z);
                int existingIdBefore = world.getBlockId(wx, wy, wz);
                int existingMetaBefore = world.getBlockMetadata(wx, wy, wz);
                if (existingIdBefore == block.blockID && existingMetaBefore == (metadata & 0xF)) {
                    ++result.unchanged;
                    continue;
                }

                boolean success = world.setBlock(wx, wy, wz, block.blockID, metadata, 2);
                int placedId = world.getBlockId(wx, wy, wz);
                int placedMeta = world.getBlockMetadata(wx, wy, wz);
                if (success) {
                    ++result.placed;
                } else if (placedId == block.blockID && placedMeta == (metadata & 0xF)) {
                    ++result.unchanged;
                } else {
                    ++result.failed;
                }

                if (placedId == block.blockID && clearInventoryAt(world, wx, wy, wz)) {
                    ++result.containersEmptied;
                }
            }
            return result;
        }

        for (int x = 0; x < schematic.getWidth(); ++x) {
            for (int y = 0; y < schematic.getHeight(); ++y) {
                for (int z = 0; z < schematic.getLength(); ++z) {
                    int wx = originX + x;
                    int wy = originY + y;
                    int wz = originZ + z;

                    Block block = schematic.getBlock(x, y, z);
                    if (block == null || block.blockID == 0) {
                        if (mode == PasteMode.REPLACE) {
                            boolean success = world.setBlockToAir(wx, wy, wz, 2);
                            if (success) {
                                ++result.cleared;
                            } else if (world.getBlockId(wx, wy, wz) == 0) {
                                ++result.unchanged;
                            } else {
                                ++result.failed;
                            }
                        }
                        continue;
                    }

                    int metadata = schematic.getBlockMetadata(x, y, z);
                    int existingIdBefore = world.getBlockId(wx, wy, wz);
                    int existingMetaBefore = world.getBlockMetadata(wx, wy, wz);
                    if (existingIdBefore == block.blockID && existingMetaBefore == (metadata & 0xF)) {
                        ++result.unchanged;
                        continue;
                    }

                    boolean success = world.setBlock(wx, wy, wz, block.blockID, metadata, 2);
                    int placedId = world.getBlockId(wx, wy, wz);
                    int placedMeta = world.getBlockMetadata(wx, wy, wz);
                    if (success) {
                        ++result.placed;
                    } else if (placedId == block.blockID && placedMeta == (metadata & 0xF)) {
                        ++result.unchanged;
                    } else {
                        ++result.failed;
                    }

                    if (placedId == block.blockID && clearInventoryAt(world, wx, wy, wz)) {
                        ++result.containersEmptied;
                    }
                }
            }
        }
        return result;
    }

    private PasteConflict findFirstPrinterCollision(World world, ISchematic schematic, int originX, int originY, int originZ) {
        if (world == null || schematic == null) {
            return null;
        }
        NonAirIndexCache cache = getOrBuildNonAirIndexCache(schematic);
        int width = schematic.getWidth();
        int length = schematic.getLength();
        for (int i = 0; i < cache.count; ++i) {
            int index = cache.indices[i];
            int x = index % width;
            int yz = index / width;
            int y = yz / length;
            int z = yz % length;
            Block target = schematic.getBlock(x, y, z);
            if (target == null || target.blockID == 0) {
                continue;
            }

            int targetMeta = schematic.getBlockMetadata(x, y, z) & 0xF;
            int wx = originX + x;
            int wy = originY + y;
            int wz = originZ + z;
            int existingId = world.getBlockId(wx, wy, wz);
            int existingMeta = world.getBlockMetadata(wx, wy, wz);
            if (existingId == 0) {
                continue;
            }
            if (existingId == target.blockID && existingMeta == targetMeta) {
                continue;
            }

            return new PasteConflict(
                    wx, wy, wz,
                    existingId, existingMeta, safeBlockName(existingId),
                    target.blockID, targetMeta, safeBlockName(target.blockID));
        }
        return null;
    }

    private PasteConflict findFirstSchematicMismatch(
            World world,
            ISchematic schematic,
            int originX,
            int originY,
            int originZ,
            boolean requireAirMatch) {
        if (world == null || schematic == null) {
            return null;
        }
        if (!requireAirMatch) {
            NonAirIndexCache cache = getOrBuildNonAirIndexCache(schematic);
            int width = schematic.getWidth();
            int length = schematic.getLength();
            for (int i = 0; i < cache.count; ++i) {
                int index = cache.indices[i];
                int x = index % width;
                int yz = index / width;
                int y = yz / length;
                int z = yz % length;
                Block expected = schematic.getBlock(x, y, z);
                int expectedId = expected == null ? 0 : expected.blockID;
                if (expectedId == 0) {
                    continue;
                }
                int expectedMeta = schematic.getBlockMetadata(x, y, z) & 0xF;
                int wx = originX + x;
                int wy = originY + y;
                int wz = originZ + z;
                int existingId = world.getBlockId(wx, wy, wz);
                int existingMeta = world.getBlockMetadata(wx, wy, wz) & 0xF;
                if (existingId == expectedId && existingMeta == expectedMeta) {
                    continue;
                }
                return new PasteConflict(
                        wx, wy, wz,
                        existingId, existingMeta, safeBlockName(existingId),
                        expectedId, expectedMeta, safeBlockName(expectedId));
            }
            return null;
        }

        for (int x = 0; x < schematic.getWidth(); ++x) {
            for (int y = 0; y < schematic.getHeight(); ++y) {
                for (int z = 0; z < schematic.getLength(); ++z) {
                    Block expected = schematic.getBlock(x, y, z);
                    int expectedId = expected == null ? 0 : expected.blockID;
                    if (expectedId == 0 && !requireAirMatch) {
                        continue;
                    }
                    int expectedMeta = schematic.getBlockMetadata(x, y, z) & 0xF;
                    int wx = originX + x;
                    int wy = originY + y;
                    int wz = originZ + z;
                    int existingId = world.getBlockId(wx, wy, wz);
                    int existingMeta = world.getBlockMetadata(wx, wy, wz) & 0xF;
                    if (existingId == expectedId && (expectedId == 0 || existingMeta == expectedMeta)) {
                        continue;
                    }
                    return new PasteConflict(
                            wx, wy, wz,
                            existingId, existingMeta, safeBlockName(existingId),
                            expectedId, expectedMeta, safeBlockName(expectedId));
                }
            }
        }
        return null;
    }

    private String safeBlockName(int blockId) {
        if (blockId == 0) {
            return I18n.tr("schematica.command.value.air", "<air>");
        }
        Block block = blockId >= 0 && blockId < Block.blocksList.length ? Block.blocksList[blockId] : null;
        return block == null ? I18n.tr("schematica.command.value.unknown", "<unknown>") : block.getLocalizedName();
    }

    private MaterialCheckResult checkAndConsumePasteMaterials(EntityPlayer player, World world, ISchematic schematic, int originX, int originY, int originZ, PasteMode mode) {
        MaterialCheckResult result = new MaterialCheckResult();
        result.canPaste = true;
        if (player == null || player.inventory == null || world == null || schematic == null) {
            result.canPaste = false;
            result.shortages.add(new MaterialShortage(I18n.tr("schematica.command.paste.shortage.internal", "Internal error (missing player/inventory/world)."), 1));
            return result;
        }
        if (player.inCreativeMode()) {
            return result;
        }
        return checkAndConsumePasteMaterialsFromInventory(player.inventory, world, schematic, originX, originY, originZ, mode);
    }

    private MaterialCheckResult checkAndConsumePasteMaterialsFromInventory(IInventory inventory, World world, ISchematic schematic, int originX, int originY, int originZ, PasteMode mode) {
        return checkAndConsumePasteMaterialsFromInventory(inventory, world, schematic, originX, originY, originZ, mode, null);
    }

    private MaterialCheckResult checkAndConsumePasteMaterialsFromInventory(
            IInventory inventory,
            World world,
            ISchematic schematic,
            int originX,
            int originY,
            int originZ,
            PasteMode mode,
            PrinterWorldStateSnapshot worldStateSnapshot) {
        MaterialCheckResult result = new MaterialCheckResult();
        result.canPaste = true;
        if (inventory == null || world == null || schematic == null) {
            result.canPaste = false;
            result.shortages.add(new MaterialShortage(I18n.tr("schematica.command.paste.shortage.internal", "Internal error (missing player/inventory/world)."), 1));
            return result;
        }
        Map<MaterialKey, Integer> required = new HashMap<MaterialKey, Integer>();
        Map<MaterialKey, ItemStack> displayStacks = new HashMap<MaterialKey, ItemStack>();
        Map<String, Integer> unsupported = new HashMap<String, Integer>();
        Map<Integer, ItemStack> placementCostCache = new HashMap<Integer, ItemStack>();
        Map<Integer, String> unsupportedLabelCache = new HashMap<Integer, String>();
        int requiredBlocks = 0;

        NonAirIndexCache cache = getOrBuildNonAirIndexCache(schematic);
        int[] existingPacked = worldStateSnapshot != null && worldStateSnapshot.matches(cache)
                ? worldStateSnapshot.existingPacked
                : null;
        int width = schematic.getWidth();
        int length = schematic.getLength();
        for (int i = 0; i < cache.count; ++i) {
            int index = cache.indices[i];
            int x = index % width;
            int yz = index / width;
            int y = yz / length;
            int z = yz % length;
            Block block = schematic.getBlock(x, y, z);
            if (block == null || block.blockID == 0) {
                continue;
            }
            int metadata = schematic.getBlockMetadata(x, y, z);
            int wx = originX + x;
            int wy = originY + y;
            int wz = originZ + z;
            int existingId;
            int existingMeta;
            if (existingPacked != null && i < existingPacked.length) {
                int packed = existingPacked[i];
                existingId = unpackExistingId(packed);
                existingMeta = unpackExistingMeta(packed);
            } else {
                existingId = world.getBlockId(wx, wy, wz);
                existingMeta = world.getBlockMetadata(wx, wy, wz);
            }
            if (!needsMaterialForPlacement(existingId, existingMeta, block.blockID, metadata, mode)) {
                continue;
            }
            if (isDoorUpperHalf(block, metadata)) {
                continue;
            }

            int costKey = toPlacementCostCacheKey(block.blockID, metadata);
            ItemStack cost = placementCostCache.get(Integer.valueOf(costKey));
            if (cost == null) {
                cost = resolvePlacementCost(block, metadata);
                if (cost == null || cost.itemID <= 0 || cost.stackSize <= 0) {
                    String blockLabel = unsupportedLabelCache.get(Integer.valueOf(costKey));
                    if (blockLabel == null) {
                        blockLabel = I18n.trf(
                                "schematica.command.paste.shortage.block_label",
                                "%s [id=%d,meta=%d]",
                                block.getLocalizedName(), block.blockID, metadata & 0xF);
                        unsupportedLabelCache.put(Integer.valueOf(costKey), blockLabel);
                    }
                    mergeCount(unsupported, blockLabel, 1);
                    continue;
                }
                ItemStack normalizedCost = cost.copy();
                normalizedCost.stackSize = Math.max(1, normalizedCost.stackSize);
                placementCostCache.put(Integer.valueOf(costKey), normalizedCost);
                cost = normalizedCost;
            }
            if (cost == null || cost.itemID <= 0 || cost.stackSize <= 0) {
                String blockLabel = I18n.trf(
                        "schematica.command.paste.shortage.block_label",
                        "%s [id=%d,meta=%d]",
                        block.getLocalizedName(), block.blockID, metadata & 0xF);
                mergeCount(unsupported, blockLabel, 1);
                continue;
            }

            int perPlacement = Math.max(1, cost.stackSize);
            MaterialKey key = new MaterialKey(cost.itemID, cost.getItemSubtype());
            mergeCount(required, key, perPlacement);
            if (!displayStacks.containsKey(key)) {
                ItemStack display = cost.copy();
                display.stackSize = 1;
                displayStacks.put(key, display);
            }
            result.totalConsumed += perPlacement;
            ++requiredBlocks;
        }
        result.totalRequiredBlocks = requiredBlocks;

        int requiredEmeralds = SchematicaPrinterConfig.computeRequiredEmeralds(requiredBlocks);
        result.requiredEmeralds = requiredEmeralds;
        if (requiredEmeralds > 0) {
            int emeraldItemId = SchematicaPrinterConfig.getEmeraldItemId();
            int emeraldSubtype = SchematicaPrinterConfig.getEmeraldSubtype();
            if (emeraldItemId > 0 && emeraldItemId < Item.itemsList.length && Item.itemsList[emeraldItemId] != null) {
                MaterialKey emeraldKey = new MaterialKey(emeraldItemId, emeraldSubtype);
                mergeCount(required, emeraldKey, requiredEmeralds);
                if (!displayStacks.containsKey(emeraldKey)) {
                    ItemStack emeraldDisplay = new ItemStack(Item.itemsList[emeraldItemId], 1, emeraldSubtype);
                    displayStacks.put(emeraldKey, emeraldDisplay);
                }
                result.totalConsumed += requiredEmeralds;
            } else {
                result.shortages.add(new MaterialShortage(
                        I18n.tr("schematica.command.printer.emerald_unavailable", "Emerald item is unavailable."),
                        requiredEmeralds));
            }
        }

        int baseFoodHunger = 0;
        for (int i = 0; i < cache.count; ++i) {
            int index = cache.indices[i];
            int x = index % width;
            int yz = index / width;
            int y = yz / length;
            int z = yz % length;
            Block block = schematic.getBlock(x, y, z);
            if (block == null || block.blockID == 0) {
                continue;
            }
            int metadata = schematic.getBlockMetadata(x, y, z);
            int wx = originX + x;
            int wy = originY + y;
            int wz = originZ + z;
            int existingId;
            int existingMeta;
            if (existingPacked != null && i < existingPacked.length) {
                int packed = existingPacked[i];
                existingId = unpackExistingId(packed);
                existingMeta = unpackExistingMeta(packed);
            } else {
                existingId = world.getBlockId(wx, wy, wz);
                existingMeta = world.getBlockMetadata(wx, wy, wz);
            }
            if (!needsMaterialForPlacement(existingId, existingMeta, block.blockID, metadata, mode)) {
                continue;
            }
            float hardness = Math.min(block.getBlockHardness(0), 20.0F);
            baseFoodHunger += (int) Math.floor(Math.max(0.0F, hardness));
        }
        int requiredFoodHunger = SchematicaPrinterConfig.computeRequiredFoodHunger(baseFoodHunger);
        result.requiredFoodHunger = requiredFoodHunger;

        result.materialTypes = required.size();

        Map<MaterialKey, Integer> available = collectInventoryMaterials(inventory);
        for (Map.Entry<MaterialKey, Integer> entry : required.entrySet()) {
            MaterialKey key = entry.getKey();
            int need = entry.getValue();
            int has = available.containsKey(key) ? available.get(key) : 0;
            if (has < need) {
                int missing = need - has;
                ItemStack display = displayStacks.get(key);
                String name = display == null
                        ? I18n.trf("schematica.command.paste.shortage.item_fallback", "item#%d:%d", key.itemId, key.subtype)
                        : display.getDisplayName();
                result.shortages.add(new MaterialShortage(name, missing));
            }
        }

        for (Map.Entry<String, Integer> unsupportedEntry : unsupported.entrySet()) {
            result.shortages.add(new MaterialShortage(
                    I18n.trf("schematica.command.paste.shortage.no_item", "%s (no carryable item)", unsupportedEntry.getKey()),
                    unsupportedEntry.getValue()));
        }

        if (requiredFoodHunger > 0) {
            int availableFoodHunger = collectFoodHungerInInventory(inventory);
            if (availableFoodHunger < requiredFoodHunger) {
                result.shortages.add(new MaterialShortage(
                        I18n.tr("schematica.command.printer.food_label", "Food"),
                        requiredFoodHunger - availableFoodHunger));
            }
        }

        if (!result.shortages.isEmpty()) {
            result.canPaste = false;
            compactShortages(result.shortages);
            sortShortages(result.shortages);
            return result;
        }

        for (Map.Entry<MaterialKey, Integer> entry : required.entrySet()) {
            if (!consumeFromInventory(inventory, entry.getKey(), entry.getValue())) {
                int consumedBeforeFailure = sumMaterialCounts(result.consumedByType);
                int restored = restoreMaterialsToInventory(inventory, result.consumedByType);
                int notRestored = Math.max(0, consumedBeforeFailure - restored);
                result.consumedByType.clear();
                result.canPaste = false;
                result.shortages.add(new MaterialShortage(
                        I18n.tr("schematica.command.paste.shortage.inventory_changed", "Inventory changed while consuming materials."),
                        entry.getValue()));
                if (notRestored > 0) {
                    result.shortages.add(new MaterialShortage(
                            I18n.trf(
                                    "schematica.command.paste.shortage.rollback_incomplete",
                                    "Rollback incomplete: restored %d/%d consumed items.",
                                    restored,
                                    consumedBeforeFailure),
                            notRestored));
                }
                return result;
            }
            mergeCount(result.consumedByType, entry.getKey(), entry.getValue());
        }

        if (requiredFoodHunger > 0) {
            int consumedFoodHunger = consumeFoodByHungerRequirement(inventory, requiredFoodHunger, result.consumedByType);
            if (consumedFoodHunger < 0) {
                int consumedBeforeFailure = sumMaterialCounts(result.consumedByType);
                int restored = restoreMaterialsToInventory(inventory, result.consumedByType);
                int notRestored = Math.max(0, consumedBeforeFailure - restored);
                result.consumedByType.clear();
                result.canPaste = false;
                result.shortages.add(new MaterialShortage(
                        I18n.tr("schematica.command.paste.shortage.inventory_changed", "Inventory changed while consuming materials."),
                        requiredFoodHunger));
                if (notRestored > 0) {
                    result.shortages.add(new MaterialShortage(
                            I18n.trf(
                                    "schematica.command.paste.shortage.rollback_incomplete",
                                    "Rollback incomplete: restored %d/%d consumed items.",
                                    restored,
                                    consumedBeforeFailure),
                            notRestored));
                }
                return result;
            }
            result.totalConsumed += consumedFoodHunger;
        }

        return result;
    }

    private boolean isDoorUpperHalf(Block block, int metadata) {
        if (block == null) {
            return false;
        }
        String simpleName = block.getClass().getSimpleName();
        return "BlockDoor".equals(simpleName) && (metadata & 8) != 0;
    }

    private ItemStack resolvePlacementCost(Block block, int metadata) {
        if (block == null || block.blockID == 0) {
            return null;
        }

        // ===================== 门修复 (无报错版) =====================
        if (block.getClass().getSimpleName().equals("BlockDoor")) {
            // 上半部门 不消耗物品
            if ((metadata & 8) != 0) {
                return null;
            }
            // 木门
            if (block.blockID == Block.doorWood.blockID) {
                return new ItemStack(Item.doorWood, 1);
            }
            // 铁门
            if (block.blockID == Block.doorIron.blockID) {
                return new ItemStack(Item.doorIron, 1);
            }
        }
        // ==========================================================

        ItemStack cost = block.createStackedBlock(metadata);
        if (cost != null && cost.itemID > 0 && cost.stackSize > 0) {
            return cost;
        }
        if (block.blockID <= 0 || block.blockID >= Item.itemsList.length || Item.itemsList[block.blockID] == null) {
            return null;
        }
        ItemStack fallback = new ItemStack(Item.itemsList[block.blockID], 1, metadata & 0xF);
        if (fallback.itemID <= 0 || fallback.stackSize <= 0) {
            return null;
        }
        return fallback;
    }

    private Map<MaterialKey, Integer> collectInventoryMaterials(IInventory inventory) {
        Map<MaterialKey, Integer> counts = new HashMap<MaterialKey, Integer>();
        if (inventory == null) {
            return counts;
        }
        for (int slot = 0; slot < inventory.getSizeInventory(); ++slot) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null || stack.itemID <= 0 || stack.stackSize <= 0) {
                continue;
            }
            MaterialKey key = new MaterialKey(stack.itemID, stack.getItemSubtype());
            mergeCount(counts, key, stack.stackSize);
        }
        return counts;
    }

    private int collectFoodHungerInInventory(IInventory inventory) {
        if (inventory == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); ++slot) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null || stack.stackSize <= 0 || stack.itemID <= 0) {
                continue;
            }
            int value = getFoodHungerValuePerItem(stack);
            if (value <= 0) {
                continue;
            }
            total += value * stack.stackSize;
        }
        return total;
    }

    private int getFoodHungerValuePerItem(ItemStack stack) {
        if (stack == null || stack.itemID <= 0 || stack.itemID >= Item.itemsList.length) {
            return 0;
        }
        Item item = Item.itemsList[stack.itemID];
        if (item == null) {
            return 0;
        }
        int satiation = Math.max(0, item.getSatiation(null));
        int nutrition = Math.max(0, item.getNutrition());
        return satiation + nutrition;
    }

    private int consumeFoodByHungerRequirement(IInventory inventory, int requiredFoodHunger, Map<MaterialKey, Integer> consumedByType) {
        if (inventory == null || requiredFoodHunger <= 0) {
            return 0;
        }
        List<FoodUnit> units = new ArrayList<FoodUnit>();
        int totalFoodHunger = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); ++slot) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null || stack.stackSize <= 0 || stack.itemID <= 0) {
                continue;
            }
            int value = getFoodHungerValuePerItem(stack);
            if (value <= 0) {
                continue;
            }
            MaterialKey key = new MaterialKey(stack.itemID, stack.getItemSubtype());
            for (int i = 0; i < stack.stackSize; ++i) {
                units.add(new FoodUnit(value, key));
            }
            totalFoodHunger += value * stack.stackSize;
        }
        if (totalFoodHunger < requiredFoodHunger) {
            return -1;
        }
        Collections.sort(units, new Comparator<FoodUnit>() {
            @Override
            public int compare(FoodUnit a, FoodUnit b) {
                return a.value - b.value;
            }
        });

        int remaining = requiredFoodHunger;
        List<FoodUnit> selected = new ArrayList<FoodUnit>();
        for (FoodUnit unit : units) {
            selected.add(unit);
            remaining -= unit.value;
            if (remaining <= 0) {
                break;
            }
        }
        int selectedTotal = 0;
        for (FoodUnit unit : selected) {
            selectedTotal += unit.value;
        }
        int overflow = selectedTotal - requiredFoodHunger;
        if (overflow > 0) {
            Collections.sort(selected, new Comparator<FoodUnit>() {
                @Override
                public int compare(FoodUnit a, FoodUnit b) {
                    return b.value - a.value;
                }
            });
            for (int i = 0; i < selected.size(); ++i) {
                FoodUnit unit = selected.get(i);
                if (unit.value <= overflow) {
                    overflow -= unit.value;
                    selected.remove(i);
                    --i;
                }
            }
        }

        Map<MaterialKey, Integer> consumeCounts = new HashMap<MaterialKey, Integer>();
        for (FoodUnit unit : selected) {
            mergeCount(consumeCounts, unit.key, 1);
        }
        int consumedHunger = 0;
        for (Map.Entry<MaterialKey, Integer> entry : consumeCounts.entrySet()) {
            if (!consumeFromInventory(inventory, entry.getKey(), entry.getValue())) {
                return -1;
            }
            mergeCount(consumedByType, entry.getKey(), entry.getValue());
            ItemStack probe = new ItemStack(Item.itemsList[entry.getKey().itemId], 1, entry.getKey().subtype);
            consumedHunger += getFoodHungerValuePerItem(probe) * entry.getValue();
        }
        return consumedHunger;
    }

    private boolean consumeFromInventory(IInventory inventory, MaterialKey key, int amount) {
        if (inventory == null || key == null || amount <= 0) {
            return amount <= 0;
        }
        int remaining = amount;
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSizeInventory() && remaining > 0; ++slot) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null || stack.itemID != key.itemId || stack.getItemSubtype() != key.subtype) {
                continue;
            }
            int take = Math.min(remaining, stack.stackSize);
            stack.stackSize -= take;
            remaining -= take;
            changed = true;
            if (stack.stackSize <= 0) {
                inventory.setInventorySlotContents(slot, null);
            } else {
                if (inventory instanceof InventoryPlayer) {
                    InventoryPlayer playerInventory = (InventoryPlayer) inventory;
                    if (playerInventory.player != null
                            && playerInventory.player.worldObj != null
                            && !playerInventory.player.worldObj.isRemote) {
                        playerInventory.inventorySlotChangedOnServer(slot);
                    }
                }
            }
        }
        if (changed) {
            inventory.onInventoryChanged();
        }
        return remaining <= 0;
    }

    private <K> void mergeCount(Map<K, Integer> counts, K key, int delta) {
        if (counts == null || key == null || delta <= 0) {
            return;
        }
        Integer old = counts.get(key);
        counts.put(key, old == null ? delta : old + delta);
    }

    private void sortShortages(List<MaterialShortage> shortages) {
        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(shortages, new Comparator<MaterialShortage>() {
            @Override
            public int compare(MaterialShortage a, MaterialShortage b) {
                if (a == b) {
                    return 0;
                }
                if (a == null) {
                    return 1;
                }
                if (b == null) {
                    return -1;
                }
                if (a.displayName == null && b.displayName == null) {
                    return 0;
                }
                if (a.displayName == null) {
                    return 1;
                }
                if (b.displayName == null) {
                    return -1;
                }
                int byName = collator.compare(a.displayName, b.displayName);
                if (byName != 0) {
                    return byName;
                }
                return Integer.compare(b.missing, a.missing);
            }
        });
    }

    private void compactShortages(List<MaterialShortage> shortages) {
        if (shortages == null || shortages.isEmpty()) {
            return;
        }
        Map<String, Integer> merged = new HashMap<String, Integer>();
        for (MaterialShortage shortage : shortages) {
            if (shortage == null || shortage.missing <= 0) {
                continue;
            }
            String name = shortage.displayName == null
                    ? I18n.tr("schematica.command.value.unknown", "<unknown>")
                    : shortage.displayName.trim();
            if (name.isEmpty()) {
                name = I18n.tr("schematica.command.value.unknown", "<unknown>");
            }
            mergeCount(merged, name, shortage.missing);
        }
        shortages.clear();
        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
            shortages.add(new MaterialShortage(entry.getKey(), entry.getValue()));
        }
    }

    private List<String> buildCompactShortageLines(List<MaterialShortage> shortages, int entriesPerLine) {
        List<String> lines = new ArrayList<String>();
        if (shortages == null || shortages.isEmpty()) {
            return lines;
        }
        int lineWidth = Math.max(1, entriesPerLine);
        StringBuilder builder = new StringBuilder();
        int inLine = 0;
        for (MaterialShortage shortage : shortages) {
            if (shortage == null || shortage.displayName == null || shortage.displayName.isEmpty() || shortage.missing <= 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("  ");
            }
            builder.append(I18n.trf(
                    "schematica.command.paste.shortage.entry",
                    "%s x%d",
                    shortage.displayName,
                    shortage.missing));
            ++inLine;
            if (inLine >= lineWidth) {
                lines.add("- " + builder.toString());
                builder.setLength(0);
                inLine = 0;
            }
        }
        if (builder.length() > 0) {
            lines.add("- " + builder.toString());
        }
        return lines;
    }

    private boolean clearInventoryAt(World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        if (world.getBlockId(x, y, z) == SchematicaBlocks.SCHEMATICA_PRINTER.blockID) {
            return false;
        }
        TileEntity tileEntity = world.getBlockTileEntity(x, y, z);
        if (!(tileEntity instanceof IInventory)) {
            return false;
        }
        IInventory inventory = (IInventory) tileEntity;
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSizeInventory(); ++slot) {
            if (inventory.getStackInSlot(slot) != null) {
                inventory.setInventorySlotContents(slot, null);
                changed = true;
            }
        }
        if (changed) {
            inventory.onInventoryChanged();
        }
        return changed;
    }

    private Schematic captureWorldRegion(World world, int originX, int originY, int originZ, int width, int height, int length, ItemStack icon) {
        if (world == null || width <= 0 || height <= 0 || length <= 0) {
            return null;
        }
        ItemStack safeIcon = icon == null ? new ItemStack((Block) Block.stone) : icon.copy();
        Schematic schematic = new Schematic(safeIcon, width, height, length);
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                for (int z = 0; z < length; ++z) {
                    int wx = originX + x;
                    int wy = originY + y;
                    int wz = originZ + z;
                    schematic.setBlock(x, y, z, world.getBlock(wx, wy, wz), world.getBlockMetadata(wx, wy, wz));
                }
            }
        }
        return schematic;
    }

    private ISchematic rotateSchematic(ISchematic source, int angle) {
        ROTATION_META_RESULT_CACHE.clear();
        int newWidth = angle == 180 ? source.getWidth() : source.getLength();
        int newLength = angle == 180 ? source.getLength() : source.getWidth();
        Schematic rotated = new Schematic(copyIcon(source), newWidth, source.getHeight(), newLength);

        for (int x = 0; x < source.getWidth(); ++x) {
            for (int y = 0; y < source.getHeight(); ++y) {
                for (int z = 0; z < source.getLength(); ++z) {
                    Block sourceBlock = source.getBlock(x, y, z);
                    int sourceMeta = source.getBlockMetadata(x, y, z);
                    int nx;
                    int nz;
                    if (angle == 90) {
                        nx = source.getLength() - 1 - z;
                        nz = x;
                    } else if (angle == 180) {
                        nx = source.getWidth() - 1 - x;
                        nz = source.getLength() - 1 - z;
                    } else {
                        nx = z;
                        nz = source.getWidth() - 1 - x;
                    }
                    int rotatedMeta = adjustMetadataForRotation(sourceBlock, sourceMeta, angle);
                    rotated.setBlock(nx, y, nz, sourceBlock, rotatedMeta);
                }
            }
        }
        ROTATION_META_RESULT_CACHE.clear();
        return rotated;
    }

    private int adjustMetadataForRotation(Block block, int metadata, int angle) {
        if (block == null) {
            return metadata;
        }
        if (!SchematicaPrinterConfig.isPrinterRotationUseBlockFacingApiEnabled()) {
            return metadata;
        }
        boolean inverseRotation = false;
        int cacheKey = toRotationMetaCacheKey(block.blockID, metadata, angle, inverseRotation);
        Integer cached = ROTATION_META_RESULT_CACHE.get(cacheKey);
        if (cached != null) {
            return cached.intValue();
        }
        Integer rotated = tryRotateMetadataByRuleSet(block, metadata, angle, inverseRotation);
        if (rotated == null) {
            rotated = tryRotateMetadataByBlockFacingApi(block, metadata, angle, inverseRotation);
        }
        int result = rotated == null ? metadata : rotated.intValue();
        ROTATION_META_RESULT_CACHE.put(cacheKey, Integer.valueOf(result));
        return result;
    }

    private int toRotationMetaCacheKey(int blockId, int metadata, int angle, boolean inverseRotation) {
        int meta4 = metadata & 0xF;
        int angleBits = (angle / 90) & 0x3;
        int inverseBit = inverseRotation ? 1 : 0;
        return ((blockId & 0x3FF) << 8) | (meta4 << 4) | (angleBits << 1) | inverseBit;
    }

    private Integer tryRotateMetadataByBlockFacingApi(Block block, int metadata, int angle, boolean inverseRotation) {
        try {
            EnumDirection facing = block.getDirectionFacing(metadata);
            if (facing == null || !isHorizontalFacing(facing)) {
                return null;
            }
            EnumDirection rotatedFacing = rotateHorizontalDirection(facing, angle, inverseRotation);
            int rotatedMeta = block.getMetadataForDirectionFacing(metadata, rotatedFacing);
            return Integer.valueOf(rotatedMeta);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isHorizontalFacing(EnumDirection direction) {
        return direction == EnumDirection.NORTH
                || direction == EnumDirection.SOUTH
                || direction == EnumDirection.WEST
                || direction == EnumDirection.EAST;
    }

    private EnumDirection rotateHorizontalDirection(EnumDirection direction, int angle, boolean inverseRotation) {
        if (angle == 180) {
            return rotateHorizontalDirection180(direction);
        }
        boolean clockwise = angle == 90;
        if (inverseRotation) {
            clockwise = !clockwise;
        }
        return clockwise ? rotateHorizontalDirectionClockwise(direction) : rotateHorizontalDirectionCounterClockwise(direction);
    }

    private EnumDirection rotateHorizontalDirectionClockwise(EnumDirection direction) {
        if (direction == EnumDirection.NORTH) return EnumDirection.EAST;
        if (direction == EnumDirection.EAST) return EnumDirection.SOUTH;
        if (direction == EnumDirection.SOUTH) return EnumDirection.WEST;
        if (direction == EnumDirection.WEST) return EnumDirection.NORTH;
        return direction;
    }

    private EnumDirection rotateHorizontalDirectionCounterClockwise(EnumDirection direction) {
        if (direction == EnumDirection.NORTH) return EnumDirection.WEST;
        if (direction == EnumDirection.WEST) return EnumDirection.SOUTH;
        if (direction == EnumDirection.SOUTH) return EnumDirection.EAST;
        if (direction == EnumDirection.EAST) return EnumDirection.NORTH;
        return direction;
    }

    private EnumDirection rotateHorizontalDirection180(EnumDirection direction) {
        if (direction == EnumDirection.NORTH) return EnumDirection.SOUTH;
        if (direction == EnumDirection.SOUTH) return EnumDirection.NORTH;
        if (direction == EnumDirection.WEST) return EnumDirection.EAST;
        if (direction == EnumDirection.EAST) return EnumDirection.WEST;
        return direction;
    }

    private Integer tryWallAttachmentFallback(Block block, int metadata, int angle, boolean inverseRotation) {
        if (block == null) {
            return null;
        }
        if (block.blockID == 65) {
            return rotateLadderMetadata(metadata, angle, inverseRotation);
        }
        if (block.blockID == 106) {
            return rotateVineMetadata(metadata, angle, inverseRotation);
        }
        return null;
    }

    private Integer rotateLadderMetadata(int metadata, int angle, boolean inverseRotation) {
        int mount = metadata & 0x7; // 2..5
        int rotated = mount;
        if (angle == 180) {
            rotated = mount == 2 ? 3 : mount == 3 ? 2 : mount == 4 ? 5 : mount == 5 ? 4 : mount;
        } else {
            boolean clockwise = angle == 90;
            if (inverseRotation) {
                clockwise = !clockwise;
            }
            if (clockwise) {
                rotated = mount == 2 ? 5 : mount == 5 ? 3 : mount == 3 ? 4 : mount == 4 ? 2 : mount;
            } else {
                rotated = mount == 2 ? 4 : mount == 4 ? 3 : mount == 3 ? 5 : mount == 5 ? 2 : mount;
            }
        }
        return Integer.valueOf((metadata & ~0x7) | (rotated & 0x7));
    }

    private Integer rotateVineMetadata(int metadata, int angle, boolean inverseRotation) {
        int mask = metadata & 0xF; // 1=south,2=west,4=north,8=east
        int rotated;
        if (angle == 180) {
            rotated = rotateVineMask90(rotateVineMask90(mask));
        } else {
            boolean clockwise = angle == 90;
            if (inverseRotation) {
                clockwise = !clockwise;
            }
            rotated = clockwise ? rotateVineMask90(mask) : rotateVineMask270(mask);
        }
        return Integer.valueOf((metadata & ~0xF) | (rotated & 0xF));
    }

    private int rotateVineMask90(int mask) {
        int out = 0;
        if ((mask & 0x1) != 0) out |= 0x2;
        if ((mask & 0x2) != 0) out |= 0x4;
        if ((mask & 0x4) != 0) out |= 0x8;
        if ((mask & 0x8) != 0) out |= 0x1;
        return out;
    }

    private int rotateVineMask270(int mask) {
        int out = 0;
        if ((mask & 0x1) != 0) out |= 0x8;
        if ((mask & 0x8) != 0) out |= 0x4;
        if ((mask & 0x4) != 0) out |= 0x2;
        if ((mask & 0x2) != 0) out |= 0x1;
        return out;
    }

    private Integer tryRotateMetadataByRuleSet(Block block, int metadata, int angle, boolean inverseRotation) {
        for (RotationMetaRule rule : ROTATION_META_RULES) {
            if (rule == null || !rule.matches(block, metadata)) {
                continue;
            }
            Integer rotated = rule.rotate(metadata, angle, inverseRotation);
            if (rotated != null) {
                return rotated;
            }
        }
        return null;
    }

    private static List<RotationMetaRule> createRotationMetaRules() {
        List<RotationMetaRule> rules = new ArrayList<RotationMetaRule>();
        rules.add(new StairRotationRule());
        rules.add(new Cardinal2To5RotationRule());
        rules.add(new Horizontal0To3RotationRule());
        rules.add(new LadderRotationRule());
        rules.add(new TorchRotationRule());
        rules.add(new VineRotationRule());
        return Collections.unmodifiableList(rules);
    }

    private interface RotationMetaRule {
        boolean matches(Block block, int metadata);

        Integer rotate(int metadata, int angle, boolean inverseRotation);
    }

    private static abstract class HorizontalMountRule implements RotationMetaRule {
        protected int rotateByQuarterTurns(int value, int angle, boolean inverseRotation) {
            if (angle == 180) {
                return rotate180(value);
            }
            boolean clockwise = angle == 90;
            if (inverseRotation) {
                clockwise = !clockwise;
            }
            return clockwise ? rotateClockwise(value) : rotateCounterClockwise(value);
        }

        protected abstract int rotateClockwise(int value);

        protected abstract int rotateCounterClockwise(int value);

        protected abstract int rotate180(int value);
    }

    private static final class StairRotationRule extends HorizontalMountRule {
        @Override
        public boolean matches(Block block, int metadata) {
            if (block == null) {
                return false;
            }
            String className = block.getClass().getSimpleName();
            return className != null && className.toLowerCase(Locale.ROOT).contains("stair");
        }

        @Override
        public Integer rotate(int metadata, int angle, boolean inverseRotation) {
            int topBit = metadata & 0x4;
            int facing = metadata & 0x3;
            int rotatedFacing = rotateByQuarterTurns(facing, angle, inverseRotation);
            return Integer.valueOf(topBit | (rotatedFacing & 0x3));
        }

        @Override
        protected int rotateClockwise(int value) {
            return value == 0 ? 2 : value == 1 ? 3 : value == 2 ? 1 : 0;
        }

        @Override
        protected int rotateCounterClockwise(int value) {
            return value == 0 ? 3 : value == 1 ? 2 : value == 2 ? 0 : 1;
        }

        @Override
        protected int rotate180(int value) {
            return value == 0 ? 1 : value == 1 ? 0 : value == 2 ? 3 : 2;
        }
    }

    private static final class Horizontal0To3RotationRule implements RotationMetaRule {
        @Override
        public boolean matches(Block block, int metadata) {
            return block != null && classNameContainsAny(block, "bed", "pumpkin", "repeater", "diode", "comparator", "trapdoor", "anvil", "cocoa", "fencegate", "fence_gate");
        }

        @Override
        public Integer rotate(int metadata, int angle, boolean inverseRotation) {
            int facing = metadata & 0x3;
            int rotated = facing;
            if (angle == 180) {
                rotated = (facing + 2) & 0x3;
            } else {
                boolean clockwise = angle == 90;
                if (inverseRotation) {
                    clockwise = !clockwise;
                }
                rotated = clockwise ? ((facing + 1) & 0x3) : ((facing + 3) & 0x3);
            }
            return Integer.valueOf((metadata & ~0x3) | (rotated & 0x3));
        }
    }

    private static final class LadderRotationRule implements RotationMetaRule {
        @Override
        public boolean matches(Block block, int metadata) {
            return block != null && block.blockID == 65;
        }

        @Override
        public Integer rotate(int metadata, int angle, boolean inverseRotation) {
            int mount = metadata & 0x7;
            int rotated = mount;
            if (angle == 180) {
                rotated = mount == 2 ? 3 : mount == 3 ? 2 : mount == 4 ? 5 : mount == 5 ? 4 : mount;
            } else {
                boolean clockwise = angle == 90;
                if (inverseRotation) {
                    clockwise = !clockwise;
                }
                if (clockwise) {
                    rotated = mount == 2 ? 5 : mount == 5 ? 3 : mount == 3 ? 4 : mount == 4 ? 2 : mount;
                } else {
                    rotated = mount == 2 ? 4 : mount == 4 ? 3 : mount == 3 ? 5 : mount == 5 ? 2 : mount;
                }
            }
            return Integer.valueOf((metadata & ~0x7) | (rotated & 0x7));
        }
    }

    private static final class Cardinal2To5RotationRule implements RotationMetaRule {
        @Override
        public boolean matches(Block block, int metadata) {
            if (block == null) {
                return false;
            }
            return classNameContainsAny(block, "furnace", "chest", "strongbox", "dispenser", "dropper", "ladder", "wallsign", "wall_sign", "sign", "hopper", "oven");
        }

        @Override
        public Integer rotate(int metadata, int angle, boolean inverseRotation) {
            int facing = metadata & 0x7;
            if (facing < 2 || facing > 5) {
                return Integer.valueOf(metadata);
            }
            int rotated = facing;
            if (angle == 180) {
                rotated = facing == 2 ? 3 : facing == 3 ? 2 : facing == 4 ? 5 : 4;
            } else {
                boolean clockwise = angle == 90;
                if (inverseRotation) {
                    clockwise = !clockwise;
                }
                if (clockwise) {
                    rotated = facing == 2 ? 5 : facing == 5 ? 3 : facing == 3 ? 4 : 2;
                } else {
                    rotated = facing == 2 ? 4 : facing == 4 ? 3 : facing == 3 ? 5 : 2;
                }
            }
            return Integer.valueOf((metadata & ~0x7) | (rotated & 0x7));
        }
    }

    private static boolean classNameContainsAny(Block block, String... keywords) {
        if (block == null || keywords == null || keywords.length == 0) {
            return false;
        }
        String className = block.getClass().getSimpleName();
        if (className == null || className.isEmpty()) {
            return false;
        }
        String lower = className.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static final class ButtonRotationRule extends HorizontalMountRule {
        @Override
        public boolean matches(Block block, int metadata) {
            if (block == null) {
                return false;
            }
            int id = block.blockID;
            return id == 77 || id == 143;
        }

        @Override
        public Integer rotate(int metadata, int angle, boolean inverseRotation) {
            int pressedBit = metadata & 0x8;
            int mount = metadata & 0x7;
            int rotated = rotateByQuarterTurns(mount, angle, inverseRotation);
            return Integer.valueOf(pressedBit | (rotated & 0x7));
        }

        @Override
        protected int rotateClockwise(int value) {
            return value == 1 ? 3 : value == 3 ? 2 : value == 2 ? 4 : value == 4 ? 1 : value;
        }

        @Override
        protected int rotateCounterClockwise(int value) {
            return value == 1 ? 4 : value == 4 ? 2 : value == 2 ? 3 : value == 3 ? 1 : value;
        }

        @Override
        protected int rotate180(int value) {
            return value == 1 ? 2 : value == 2 ? 1 : value == 3 ? 4 : value == 4 ? 3 : value;
        }
    }

    private static final class FenceGateRotationRule implements RotationMetaRule {
        @Override
        public boolean matches(Block block, int metadata) {
            return block != null && block.blockID == 107;
        }

        @Override
        public Integer rotate(int metadata, int angle, boolean inverseRotation) {
            int openBit = metadata & 0x4;
            int facing = metadata & 0x3;
            int rotated = angle == 180 ? ((facing + 2) & 0x3) : (((facing + (inverseRotation ? (angle == 90 ? 3 : 1) : (angle == 90 ? 1 : 3))) & 0x3));
            return Integer.valueOf(openBit | rotated);
        }
    }

    private static final class DoorRotationRule implements RotationMetaRule {
        @Override
        public boolean matches(Block block, int metadata) {
            if (block == null) {
                return false;
            }
            int id = block.blockID;
            return id == 64 || id == 71 || id == 208 || id == 209 || id == 210 || id == 211 || id == 212 || id == 253;
        }

        @Override
        public Integer rotate(int metadata, int angle, boolean inverseRotation) {
            if ((metadata & 0x8) != 0) {
                return Integer.valueOf(metadata);
            }
            int openBit = metadata & 0x4;
            int facing = metadata & 0x3;
            int rotated = angle == 180 ? ((facing + 2) & 0x3) : (((facing + (inverseRotation ? (angle == 90 ? 3 : 1) : (angle == 90 ? 1 : 3))) & 0x3));
            return Integer.valueOf(openBit | rotated);
        }
    }

    private static final class TorchRotationRule extends HorizontalMountRule {
        @Override
        public boolean matches(Block block, int metadata) {
            if (block == null) {
                return false;
            }
            int id = block.blockID;
            return id == 50 || id == 75 || id == 76;
        }

        @Override
        public Integer rotate(int metadata, int angle, boolean inverseRotation) {
            int mount = metadata & 0x7;
            int rotated = rotateByQuarterTurns(mount, angle, inverseRotation);
            return Integer.valueOf(rotated & 0x7);
        }

        @Override
        protected int rotateClockwise(int value) {
            return value == 1 ? 3 : value == 3 ? 2 : value == 2 ? 4 : value == 4 ? 1 : value;
        }

        @Override
        protected int rotateCounterClockwise(int value) {
            return value == 1 ? 4 : value == 4 ? 2 : value == 2 ? 3 : value == 3 ? 1 : value;
        }

        @Override
        protected int rotate180(int value) {
            return value == 1 ? 2 : value == 2 ? 1 : value == 3 ? 4 : value == 4 ? 3 : value;
        }
    }

    private static final class VineRotationRule implements RotationMetaRule {
        @Override
        public boolean matches(Block block, int metadata) {
            return block != null && block.blockID == 106;
        }

        @Override
        public Integer rotate(int metadata, int angle, boolean inverseRotation) {
            int mask = metadata & 0xF;
            int rotated;
            if (angle == 180) {
                rotated = rotateVineMask90(rotateVineMask90(mask));
            } else {
                boolean clockwise = angle == 90;
                if (inverseRotation) {
                    clockwise = !clockwise;
                }
                rotated = clockwise ? rotateVineMask90(mask) : rotateVineMask270(mask);
            }
            return Integer.valueOf((metadata & ~0xF) | (rotated & 0xF));
        }

        private int rotateVineMask90(int mask) {
            int out = 0;
            if ((mask & 0x1) != 0) out |= 0x2;
            if ((mask & 0x2) != 0) out |= 0x4;
            if ((mask & 0x4) != 0) out |= 0x8;
            if ((mask & 0x8) != 0) out |= 0x1;
            return out;
        }

        private int rotateVineMask270(int mask) {
            int out = 0;
            if ((mask & 0x1) != 0) out |= 0x8;
            if ((mask & 0x8) != 0) out |= 0x4;
            if ((mask & 0x4) != 0) out |= 0x2;
            if ((mask & 0x2) != 0) out |= 0x1;
            return out;
        }
    }

    // Legacy fallback chain kept for historical comparison and emergency rollback.
    // Do not use in normal flow: current metadata rotation goes through ROTATION_META_RULES.
    @Deprecated
    private Integer tryRotateMetadataByFallbackHandlers(Block block, int metadata, int angle, boolean inverseRotation) {
        for (RotationMetaFallbackHandler handler : ROTATION_META_FALLBACK_HANDLERS) {
            if (handler == null || !handler.matches(block)) {
                continue;
            }
            Integer rotated = handler.rotate(block, metadata, angle, inverseRotation);
            if (rotated != null) {
                return rotated;
            }
        }
        return null;
    }

    @Deprecated
    private static List<RotationMetaFallbackHandler> createRotationMetaFallbackHandlers() {
        List<RotationMetaFallbackHandler> handlers = new ArrayList<RotationMetaFallbackHandler>();
        handlers.add(new StairRotationMetaFallbackHandler());
        handlers.add(new ButtonRotationMetaFallbackHandler());
        handlers.add(new FenceGateRotationMetaFallbackHandler());
        handlers.add(new DoorRotationMetaFallbackHandler());
        handlers.add(new TorchRotationMetaFallbackHandler());
        handlers.add(new VineRotationMetaFallbackHandler());
        return Collections.unmodifiableList(handlers);
    }

    private static int rotateStairFacingClockwise(int facingBits) {
        if (facingBits == 0) {
            return 2;
        }
        if (facingBits == 1) {
            return 3;
        }
        if (facingBits == 2) {
            return 1;
        }
        return 0;
    }

    private static int rotateStairFacingCounterClockwise(int facingBits) {
        if (facingBits == 0) {
            return 3;
        }
        if (facingBits == 1) {
            return 2;
        }
        if (facingBits == 2) {
            return 0;
        }
        return 1;
    }

    private static int rotateStairFacing180(int facingBits) {
        if (facingBits == 0) {
            return 1;
        }
        if (facingBits == 1) {
            return 0;
        }
        if (facingBits == 2) {
            return 3;
        }
        return 2;
    }

    @Deprecated
    private interface RotationMetaFallbackHandler {
        boolean matches(Block block);

        Integer rotate(Block block, int metadata, int angle, boolean inverseRotation);
    }

    @Deprecated
    private static final class StairRotationMetaFallbackHandler implements RotationMetaFallbackHandler {
        @Override
        public boolean matches(Block block) {
            if (block == null) {
                return false;
            }
            String className = block.getClass().getSimpleName();
            return className != null && className.toLowerCase(Locale.ROOT).contains("stair");
        }

        @Override
        public Integer rotate(Block block, int metadata, int angle, boolean inverseRotation) {
            int upsideDownBit = metadata & 0x4;
            int facingBits = metadata & 0x3;
            int rotatedFacing = facingBits;
            if (angle == 180) {
                rotatedFacing = rotateStairFacing180(facingBits);
            } else {
                boolean clockwise = angle == 90;
                if (inverseRotation) {
                    clockwise = !clockwise;
                }
                rotatedFacing = clockwise
                        ? rotateStairFacingClockwise(facingBits)
                        : rotateStairFacingCounterClockwise(facingBits);
            }
            return Integer.valueOf(upsideDownBit | rotatedFacing);
        }
    }

    @Deprecated
    private static final class ButtonRotationMetaFallbackHandler implements RotationMetaFallbackHandler {
        @Override
        public boolean matches(Block block) {
            if (block == null) {
                return false;
            }
            int id = block.blockID;
            return id == 77 || id == 143;
        }

        @Override
        public Integer rotate(Block block, int metadata, int angle, boolean inverseRotation) {
            int pressedBit = metadata & 0x8;
            int mount = metadata & 0x7;
            int rotatedMount = mount;
            if (angle == 180) {
                rotatedMount = rotateButtonMount180(mount);
            } else {
                boolean clockwise = angle == 90;
                if (inverseRotation) {
                    clockwise = !clockwise;
                }
                rotatedMount = clockwise
                        ? rotateButtonMountClockwise(mount)
                        : rotateButtonMountCounterClockwise(mount);
            }
            return Integer.valueOf(pressedBit | (rotatedMount & 0x7));
        }

        private int rotateButtonMountClockwise(int mount) {
            if (mount == 1) { // west
                return 3;     // north
            }
            if (mount == 3) { // north
                return 2;     // east
            }
            if (mount == 2) { // east
                return 4;     // south
            }
            if (mount == 4) { // south
                return 1;     // west
            }
            return mount;
        }

        private int rotateButtonMountCounterClockwise(int mount) {
            if (mount == 1) { // west
                return 4;     // south
            }
            if (mount == 4) { // south
                return 2;     // east
            }
            if (mount == 2) { // east
                return 3;     // north
            }
            if (mount == 3) { // north
                return 1;     // west
            }
            return mount;
        }

        private int rotateButtonMount180(int mount) {
            if (mount == 1) {
                return 2;
            }
            if (mount == 2) {
                return 1;
            }
            if (mount == 3) {
                return 4;
            }
            if (mount == 4) {
                return 3;
            }
            return mount;
        }
    }

    @Deprecated
    private static final class FenceGateRotationMetaFallbackHandler implements RotationMetaFallbackHandler {
        @Override
        public boolean matches(Block block) {
            return block != null && block.blockID == 107;
        }

        @Override
        public Integer rotate(Block block, int metadata, int angle, boolean inverseRotation) {
            int openBit = metadata & 0x4;
            int facing = metadata & 0x3;
            int rotatedFacing = facing;
            if (angle == 180) {
                rotatedFacing = (facing + 2) & 0x3;
            } else {
                boolean clockwise = angle == 90;
                if (inverseRotation) {
                    clockwise = !clockwise;
                }
                rotatedFacing = clockwise ? ((facing + 1) & 0x3) : ((facing + 3) & 0x3);
            }
            return Integer.valueOf(openBit | rotatedFacing);
        }
    }

    @Deprecated
    private static final class DoorRotationMetaFallbackHandler implements RotationMetaFallbackHandler {
        @Override
        public boolean matches(Block block) {
            if (block == null) {
                return false;
            }
            int id = block.blockID;
            return id == 64 || id == 71 || id == 208 || id == 209 || id == 210 || id == 211 || id == 212 || id == 253;
        }

        @Override
        public Integer rotate(Block block, int metadata, int angle, boolean inverseRotation) {
            if ((metadata & 0x8) != 0) {
                // Upper half: keep hinge bit/state as-is for pure rotation.
                return Integer.valueOf(metadata);
            }
            int openBit = metadata & 0x4;
            int facing = metadata & 0x3;
            int rotatedFacing = facing;
            if (angle == 180) {
                rotatedFacing = (facing + 2) & 0x3;
            } else {
                boolean clockwise = angle == 90;
                if (inverseRotation) {
                    clockwise = !clockwise;
                }
                rotatedFacing = clockwise ? ((facing + 1) & 0x3) : ((facing + 3) & 0x3);
            }
            return Integer.valueOf(openBit | rotatedFacing);
        }
    }

    @Deprecated
    private static final class TorchRotationMetaFallbackHandler implements RotationMetaFallbackHandler {
        @Override
        public boolean matches(Block block) {
            if (block == null) {
                return false;
            }
            int id = block.blockID;
            return id == 50 || id == 75 || id == 76;
        }

        @Override
        public Integer rotate(Block block, int metadata, int angle, boolean inverseRotation) {
            int mount = metadata & 0x7;
            int rotatedMount = mount;
            if (angle == 180) {
                rotatedMount = rotateTorchMount180(mount);
            } else {
                boolean clockwise = angle == 90;
                if (inverseRotation) {
                    clockwise = !clockwise;
                }
                rotatedMount = clockwise
                        ? rotateTorchMountClockwise(mount)
                        : rotateTorchMountCounterClockwise(mount);
            }
            return Integer.valueOf(rotatedMount & 0x7);
        }

        private int rotateTorchMountClockwise(int mount) {
            if (mount == 1) { // west
                return 3;     // north
            }
            if (mount == 3) { // north
                return 2;     // east
            }
            if (mount == 2) { // east
                return 4;     // south
            }
            if (mount == 4) { // south
                return 1;     // west
            }
            return mount; // floor/invalid keep
        }

        private int rotateTorchMountCounterClockwise(int mount) {
            if (mount == 1) {
                return 4;
            }
            if (mount == 4) {
                return 2;
            }
            if (mount == 2) {
                return 3;
            }
            if (mount == 3) {
                return 1;
            }
            return mount;
        }

        private int rotateTorchMount180(int mount) {
            if (mount == 1) {
                return 2;
            }
            if (mount == 2) {
                return 1;
            }
            if (mount == 3) {
                return 4;
            }
            if (mount == 4) {
                return 3;
            }
            return mount;
        }
    }

    @Deprecated
    private static final class VineRotationMetaFallbackHandler implements RotationMetaFallbackHandler {
        @Override
        public boolean matches(Block block) {
            return block != null && block.blockID == 106;
        }

        @Override
        public Integer rotate(Block block, int metadata, int angle, boolean inverseRotation) {
            int mask = metadata & 0xF;
            int rotated = mask;
            if (angle == 180) {
                rotated = rotateVineMask90(rotateVineMask90(mask));
            } else {
                boolean clockwise = angle == 90;
                if (inverseRotation) {
                    clockwise = !clockwise;
                }
                rotated = clockwise ? rotateVineMask90(mask) : rotateVineMask270(mask);
            }
            return Integer.valueOf((metadata & ~0xF) | (rotated & 0xF));
        }

        // Bit layout (1.6.x): 0x1=south, 0x2=west, 0x4=north, 0x8=east
        private int rotateVineMask90(int mask) {
            int out = 0;
            if ((mask & 0x1) != 0) { // south -> west
                out |= 0x2;
            }
            if ((mask & 0x2) != 0) { // west -> north
                out |= 0x4;
            }
            if ((mask & 0x4) != 0) { // north -> east
                out |= 0x8;
            }
            if ((mask & 0x8) != 0) { // east -> south
                out |= 0x1;
            }
            return out;
        }

        private int rotateVineMask270(int mask) {
            int out = 0;
            if ((mask & 0x1) != 0) { // south -> east
                out |= 0x8;
            }
            if ((mask & 0x8) != 0) { // east -> north
                out |= 0x4;
            }
            if ((mask & 0x4) != 0) { // north -> west
                out |= 0x2;
            }
            if ((mask & 0x2) != 0) { // west -> south
                out |= 0x1;
            }
            return out;
        }
    }


    private ISchematic mirrorSchematic(ISchematic source, String axis) {
        Schematic mirrored = new Schematic(copyIcon(source), source.getWidth(), source.getHeight(), source.getLength());
        for (int x = 0; x < source.getWidth(); ++x) {
            for (int y = 0; y < source.getHeight(); ++y) {
                for (int z = 0; z < source.getLength(); ++z) {
                    int nx = "x".equals(axis) ? source.getWidth() - 1 - x : x;
                    int nz = "z".equals(axis) ? source.getLength() - 1 - z : z;
                    mirrored.setBlock(nx, y, nz, source.getBlock(x, y, z), source.getBlockMetadata(x, y, z));
                }
            }
        }
        return mirrored;
    }

    private String validateRegionBounds(int originX, int originY, int originZ, int width, int height, int length) {
        if (width <= 0 || height <= 0 || length <= 0) {
            return I18n.tr("schematica.command.bounds.invalid_size", "invalid region size.");
        }

        long minX = originX;
        long minY = originY;
        long minZ = originZ;
        long maxX = originX + (long) width - 1L;
        long maxY = originY + (long) height - 1L;
        long maxZ = originZ + (long) length - 1L;

        if (minY < MIN_WORLD_Y || maxY > MAX_WORLD_Y) {
            return I18n.trf(
                    "schematica.command.bounds.y_outside",
                    "Y range %d..%d is outside world bounds %d..%d.",
                    minY, maxY, MIN_WORLD_Y, MAX_WORLD_Y);
        }
        if (maxX > Integer.MAX_VALUE || maxZ > Integer.MAX_VALUE || minX < Integer.MIN_VALUE || minZ < Integer.MIN_VALUE) {
            return I18n.trf(
                    "schematica.command.bounds.xz_overflow",
                    "X/Z range overflow: [%d,%d]..[%d,%d].",
                    minX, minZ, maxX, maxZ);
        }
        return null;
    }

    private PasteMode parsePasteMode(String[] parts) {
        return parsePasteMode(parts, 2);
    }

    private PasteMode parsePasteMode(String[] parts, int modeIndex) {
        if (parts.length <= modeIndex) {
            return PasteMode.REPLACE;
        }
        String mode = parts[modeIndex].toLowerCase(Locale.ROOT);
        if ("replace".equals(mode)) {
            return PasteMode.REPLACE;
        }
        if ("solid".equals(mode) || "nonair".equals(mode)) {
            return PasteMode.SOLID;
        }
        return null;
    }

    private boolean isClientWorld(HandleChatCommandEvent event) {
        World world = event.getWorld();
        return world != null && world.isRemote;
    }

    private static synchronized String newPrinterTxnId(String action) {
        PRINTER_TXN_SEQ = (PRINTER_TXN_SEQ % 99999L) + 1L;
        String prefix;
        if (action == null || action.isEmpty()) {
            prefix = "P";
        } else {
            prefix = action.substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return prefix + String.format(Locale.ROOT, "%05d", PRINTER_TXN_SEQ);
    }

    private void logPrinterTxn(HandleChatCommandEvent event, String txnId, String phase, String detail) {
        if (event == null || event.getPlayer() == null || txnId == null || txnId.isEmpty()) {
            return;
        }
        String safePhase = phase == null || phase.isEmpty() ? "INFO" : phase;
        String safeDetail = detail == null ? "" : detail;
        event.getPlayer().addChatMessage(I18n.trf(
                "schematica.command.printer.txn",
                "[TXN %s][%s] %s",
                txnId,
                safePhase,
                safeDetail));
    }

    private void sendHelp(HandleChatCommandEvent event) {
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.title", "Survival Schematica commands:"));
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.line1", "/schematica help | /schematica list"));
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.line2", "/schematica load <name> | /schematica unload | /schematica status"));
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.line3", "/schematica origin here | /schematica move <x> <y> <z> | /schematica nudge <dx> <dy> <dz>"));
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.line4", "/schematica rotate <90|180|270> | /schematica mirror <x|z>"));
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.line5", "/schematica paste [replace|solid|nonair] | /schematica undo"));
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.line6", "/schematica save <x1> <y1> <z1> <x2> <y2> <z2> <name>"));
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.line7", "/schematica create <name> (from stick selection)"));
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.line8", "/schematica sel status | /schematica sel clear"));
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.line9", "/schematica printer print <x> <y> <z> [replace|solid|nonair] | /schematica printer undo <x> <y> <z> | /schematica printer provide <x> <y> <z> <itemId> <subtype> [count] | /schematica printer providefood <x> <y> <z> [hungerTarget] | /schematica printer health <x> <y> <z> | /schematica printer sync <x> <y> <z>"));
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.rule", "Rule: survival paste consumes inventory materials; no entity copy; printed containers are always empty."));
    }

    private ItemStack copyIcon(ISchematic schematic) {
        ItemStack icon = schematic.getIcon();
        return icon == null ? new ItemStack((Block) Block.stone) : icon.copy();
    }

    private File getSchematicDir() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "schematics");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File resolveSchematicFile(String rawName) {
        String filename = normalizeFilename(rawName);
        if (filename == null || filename.isEmpty()) {
            return null;
        }

        File dir = getSchematicDir();
        File file = new File(dir, filename);
        try {
            String dirPath = dir.getCanonicalPath() + File.separator;
            String filePath = file.getCanonicalPath();
            if (!filePath.startsWith(dirPath)) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        return file;
    }

    private String normalizeFilename(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String base = trimmed.toLowerCase(Locale.ROOT).endsWith(".schematic")
                ? trimmed.substring(0, trimmed.length() - ".schematic".length())
                : trimmed;
        base = base.replaceAll("[\\\\/:*?\"<>|]+", "_").replaceAll("\\s+", "_");
        if (base.isEmpty()) {
            return null;
        }
        return base + ".schematic";
    }

    private void tickScheduledPrinterPrintTasks() {
        List<ScheduledPrinterPrintTask> tasks;
        synchronized (ACTIVE_PRINTER_PRINT_TASKS) {
            if (ACTIVE_PRINTER_PRINT_TASKS.isEmpty()) {
                return;
            }
            tasks = new ArrayList<ScheduledPrinterPrintTask>(ACTIVE_PRINTER_PRINT_TASKS.values());
        }
        for (ScheduledPrinterPrintTask task : tasks) {
            if (task == null || task.completed) {
                removeScheduledTask(task);
                continue;
            }
            boolean done;
            try {
                done = task.processTick(PRINTER_PRINT_BLOCKS_PER_TICK, this);
            } catch (Throwable t) {
                Reference.logger.error("Scheduled printer task failed unexpectedly", t);
                task.completed = true;
                task.failedHard = true;
                done = true;
            }
            if (!done) {
                continue;
            }
            finishScheduledPrinterTask(task);
            removeScheduledTask(task);
        }
    }

    private static void removeScheduledTask(ScheduledPrinterPrintTask task) {
        if (task == null) {
            return;
        }
        synchronized (ACTIVE_PRINTER_PRINT_TASKS) {
            ACTIVE_PRINTER_PRINT_TASKS.remove(task.taskKey);
        }
    }

    private void finishScheduledPrinterTask(ScheduledPrinterPrintTask task) {
        if (task == null) {
            return;
        }
        SchematicaRuntime.setPrinterPrintedSnapshot(
                task.schematic,
                task.originX,
                task.originY,
                task.originZ,
                task.printerX,
                task.printerY,
                task.printerZ);
        IInventory printerInventory = getPrinterInventory(task.world, task.printerX, task.printerY, task.printerZ);
        syncPrinterInventoryToClients(task.world, task.printerX, task.printerY, task.printerZ, printerInventory);

        EntityPlayer player = task.resolvePlayer();
        String name = task.schematicName == null
                ? I18n.tr("schematica.command.value.unknown", "<unknown>")
                : task.schematicName;
        String failedSuffix = task.result.failed > 0
                ? I18n.trf("schematica.command.paste.failed_suffix", ", failed=%d", task.result.failed)
                : "";
        String emeraldSuffix = task.requiredEmeralds > 0
                ? I18n.trf("schematica.command.printer.print.emerald_suffix", ", emeraldUsed=%d", task.requiredEmeralds)
                : "";
        boolean printSucceeded = !task.failedHard && task.result.failed == 0;
        boolean autoUnloaded = printSucceeded && SchematicaPrinterConfig.isAutoUnloadProjectionAfterPrintEnabled();
        if (autoUnloaded) {
            if (task.uploadedFromClient && task.playerEntityId > 0) {
                SchematicaRuntime.clearUploadedPrinterProjection(task.playerEntityId);
            } else {
                SchematicaRuntime.clearLoadedSchematic();
            }
        }

        if (player != null) {
            player.addChatMessage(I18n.trf(
                    "schematica.command.printer.print.done",
                    "Printer [%d,%d,%d] pasted %s mode=%s, materialsUsed=%d, materialTypes=%d%s: placed=%d, cleared=%d, containersEmptied=%d, unchanged=%d%s",
                    task.printerX, task.printerY, task.printerZ, name, task.mode.id, task.totalConsumed, task.materialTypes,
                    emeraldSuffix, task.result.placed, task.result.cleared, task.result.containersEmptied, task.result.unchanged, failedSuffix));
            if (autoUnloaded) {
                player.addChatMessage(I18n.tr(
                        "schematica.command.printer.print.auto_unload",
                        "Projection auto-unloaded after printer print."));
            }
            if (task.failedHard) {
                player.addChatMessage(I18n.tr("schematica.command.printer.print.internal_failed", "Printer print task failed due to an internal error."));
            }
            if (player instanceof ServerPlayer) {
                Network.sendToClient((ServerPlayer) player, new S2CPrinterPrintResultPacket(
                        task.printerX,
                        task.printerY,
                        task.printerZ,
                        printSucceeded,
                        autoUnloaded));
            }
        }

        Reference.logger.info(
                "Printer task {} finished: world={}, printer=[{},{},{}], placed={}, cleared={}, unchanged={}, failed={}, hardFailed={}",
                task.txnId,
                Integer.valueOf(System.identityHashCode(task.world)),
                Integer.valueOf(task.printerX),
                Integer.valueOf(task.printerY),
                Integer.valueOf(task.printerZ),
                Integer.valueOf(task.result.placed),
                Integer.valueOf(task.result.cleared),
                Integer.valueOf(task.result.unchanged),
                Integer.valueOf(task.result.failed),
                Boolean.valueOf(task.failedHard));
    }

    private static String buildPrinterTaskKey(World world, int printerX, int printerY, int printerZ) {
        int worldId = world == null ? 0 : System.identityHashCode(world);
        return worldId + ":" + printerX + "," + printerY + "," + printerZ;
    }

    private PrinterWorldStateSnapshot buildPrinterWorldStateSnapshot(
            World world,
            ISchematic schematic,
            int originX,
            int originY,
            int originZ) {
        if (world == null || schematic == null) {
            return null;
        }
        NonAirIndexCache cache = getOrBuildNonAirIndexCache(schematic);
        int[] existingPacked = new int[cache.count];
        int width = schematic.getWidth();
        int length = schematic.getLength();
        for (int i = 0; i < cache.count; ++i) {
            int index = cache.indices[i];
            int x = index % width;
            int yz = index / width;
            int y = yz / length;
            int z = yz % length;
            Block target = schematic.getBlock(x, y, z);
            if (target == null || target.blockID == 0) {
                existingPacked[i] = packExistingState(0, 0);
                continue;
            }

            int wx = originX + x;
            int wy = originY + y;
            int wz = originZ + z;
            int existingId = world.getBlockId(wx, wy, wz);
            int existingMeta = world.getBlockMetadata(wx, wy, wz);
            existingPacked[i] = packExistingState(existingId, existingMeta);

            int targetMeta = schematic.getBlockMetadata(x, y, z) & 0xF;
            if (existingId != 0 && !(existingId == target.blockID && existingMeta == targetMeta)) {
                PasteConflict conflict = new PasteConflict(
                        wx, wy, wz,
                        existingId, existingMeta, safeBlockName(existingId),
                        target.blockID, targetMeta, safeBlockName(target.blockID));
                return new PrinterWorldStateSnapshot(cache, existingPacked, conflict);
            }
        }
        return new PrinterWorldStateSnapshot(cache, existingPacked, null);
    }

    private static int packExistingState(int existingId, int existingMeta) {
        return ((existingId & 0xFFFF) << 16) | (existingMeta & 0xFFFF);
    }

    private static int unpackExistingId(int packed) {
        return (packed >>> 16) & 0xFFFF;
    }

    private static int unpackExistingMeta(int packed) {
        return packed & 0xFFFF;
    }

    private boolean needsMaterialForPlacement(int existingId, int existingMeta, int sourceBlockId, int sourceMeta, PasteMode mode) {
        if (mode != PasteMode.REPLACE && mode != PasteMode.SOLID) {
            return true;
        }
        return existingId != sourceBlockId || existingMeta != (sourceMeta & 0xF);
    }

    private static int toPlacementCostCacheKey(int blockId, int metadata) {
        return (blockId << 8) | (metadata & 0xFF);
    }

    private static synchronized NonAirIndexCache getOrBuildNonAirIndexCache(ISchematic schematic) {
        if (schematic == null) {
            return new NonAirIndexCache(0, 0, 0, new int[0], 0);
        }
        NonAirIndexCache cached = NON_AIR_INDEX_CACHE.get(schematic);
        int width = schematic.getWidth();
        int height = schematic.getHeight();
        int length = schematic.getLength();
        if (cached != null && cached.matches(width, height, length)) {
            return cached;
        }

        int expectedVolume = Math.max(0, width * height * length);
        int[] indices = new int[Math.min(expectedVolume, 4096)];
        int count = 0;
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                for (int z = 0; z < length; ++z) {
                    Block block = schematic.getBlock(x, y, z);
                    if (block == null || block.blockID == 0) {
                        continue;
                    }
                    if (count >= indices.length) {
                        int newSize = Math.max(indices.length * 2, count + 1);
                        int[] expanded = new int[newSize];
                        System.arraycopy(indices, 0, expanded, 0, indices.length);
                        indices = expanded;
                    }
                    indices[count++] = x + (y * length + z) * width;
                }
            }
        }
        NonAirIndexCache rebuilt = new NonAirIndexCache(width, height, length, indices, count);
        NON_AIR_INDEX_CACHE.put(schematic, rebuilt);
        return rebuilt;
    }

    private static final class PrintProjectionContext {
        private final ISchematic schematic;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final String schematicName;
        private final boolean uploadedFromClient;

        private PrintProjectionContext(
                ISchematic schematic,
                int originX,
                int originY,
                int originZ,
                String schematicName,
                boolean uploadedFromClient) {
            this.schematic = schematic;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.schematicName = schematicName;
            this.uploadedFromClient = uploadedFromClient;
        }
    }

    private static final class PrinterWorldStateSnapshot {
        private final NonAirIndexCache cache;
        private final int[] existingPacked;
        private final PasteConflict conflict;

        private PrinterWorldStateSnapshot(NonAirIndexCache cache, int[] existingPacked, PasteConflict conflict) {
            this.cache = cache;
            this.existingPacked = existingPacked;
            this.conflict = conflict;
        }

        private boolean matches(NonAirIndexCache cache) {
            return this.cache == cache;
        }
    }

    private static final class NonAirIndexCache {
        private final int width;
        private final int height;
        private final int length;
        private final int[] indices;
        private final int count;

        private NonAirIndexCache(int width, int height, int length, int[] indices, int count) {
            this.width = width;
            this.height = height;
            this.length = length;
            this.indices = indices;
            this.count = count;
        }

        private boolean matches(int width, int height, int length) {
            return this.width == width && this.height == height && this.length == length;
        }
    }

    private static final class ScheduledPrinterPrintTask {
        private final String taskKey;
        private final World world;
        private final ISchematic schematic;
        private final String schematicName;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final PasteMode mode;
        private final int printerX;
        private final int printerY;
        private final int printerZ;
        private final int playerEntityId;
        private final boolean uploadedFromClient;
        private final int totalConsumed;
        private final int materialTypes;
        private final int requiredEmeralds;
        private final String txnId;
        private final int width;
        private final int height;
        private final int length;
        private final int totalVolume;
        private int cursor;
        private final PasteResult result = new PasteResult();
        private boolean completed;
        private boolean failedHard;

        private ScheduledPrinterPrintTask(
                String taskKey,
                World world,
                ISchematic schematic,
                String schematicName,
                int originX,
                int originY,
                int originZ,
                PasteMode mode,
                int printerX,
                int printerY,
                int printerZ,
                int playerEntityId,
                boolean uploadedFromClient,
                int totalConsumed,
                int materialTypes,
                int requiredEmeralds,
                String txnId) {
            this.taskKey = taskKey;
            this.world = world;
            this.schematic = schematic;
            this.schematicName = schematicName;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.mode = mode;
            this.printerX = printerX;
            this.printerY = printerY;
            this.printerZ = printerZ;
            this.playerEntityId = playerEntityId;
            this.uploadedFromClient = uploadedFromClient;
            this.totalConsumed = totalConsumed;
            this.materialTypes = materialTypes;
            this.requiredEmeralds = requiredEmeralds;
            this.txnId = txnId;
            this.width = schematic == null ? 0 : schematic.getWidth();
            this.height = schematic == null ? 0 : schematic.getHeight();
            this.length = schematic == null ? 0 : schematic.getLength();
            this.totalVolume = Math.max(0, this.width * this.height * this.length);
        }

        private boolean processTick(int budget, SurvivalSchematicaEventListener helper) {
            if (this.completed) {
                return true;
            }
            if (helper == null || this.world == null || this.schematic == null || this.totalVolume <= 0) {
                this.completed = true;
                this.failedHard = true;
                return true;
            }
            int steps = 0;
            while (steps < budget && this.cursor < this.totalVolume) {
                int index = this.cursor++;
                ++steps;
                int x = index % this.width;
                int yz = index / this.width;
                int y = yz / this.length;
                int z = yz % this.length;
                int wx = this.originX + x;
                int wy = this.originY + y;
                int wz = this.originZ + z;

                Block block = this.schematic.getBlock(x, y, z);
                if (block == null || block.blockID == 0) {
                    if (this.mode == PasteMode.REPLACE) {
                        boolean success = this.world.setBlockToAir(wx, wy, wz, 2);
                        if (success) {
                            ++this.result.cleared;
                        } else if (this.world.getBlockId(wx, wy, wz) == 0) {
                            ++this.result.unchanged;
                        } else {
                            ++this.result.failed;
                        }
                    }
                    continue;
                }

                int metadata = this.schematic.getBlockMetadata(x, y, z);
                int existingIdBefore = this.world.getBlockId(wx, wy, wz);
                int existingMetaBefore = this.world.getBlockMetadata(wx, wy, wz);
                if (existingIdBefore == block.blockID && existingMetaBefore == (metadata & 0xF)) {
                    ++this.result.unchanged;
                    continue;
                }

                boolean success = this.world.setBlock(wx, wy, wz, block.blockID, metadata, 2);
                int placedId = this.world.getBlockId(wx, wy, wz);
                int placedMeta = this.world.getBlockMetadata(wx, wy, wz);
                if (success) {
                    ++this.result.placed;
                } else if (placedId == block.blockID && placedMeta == (metadata & 0xF)) {
                    ++this.result.unchanged;
                } else {
                    ++this.result.failed;
                }
                if (placedId == block.blockID && helper.clearInventoryAt(this.world, wx, wy, wz)) {
                    ++this.result.containersEmptied;
                }
            }
            if (this.cursor >= this.totalVolume) {
                this.completed = true;
            }
            return this.completed;
        }

        private EntityPlayer resolvePlayer() {
            if (this.world == null || this.playerEntityId <= 0) {
                return null;
            }
            try {
                net.minecraft.Entity entity = this.world.getEntityByID(this.playerEntityId);
                return entity instanceof EntityPlayer ? (EntityPlayer) entity : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private enum PasteMode {
        REPLACE("replace"),
        SOLID("solid");

        private final String id;

        PasteMode(String id) {
            this.id = id;
        }
    }

    private static final class PasteResult {
        private int placed;
        private int cleared;
        private int containersEmptied;
        private int unchanged;
        private int failed;
    }

    private static final class MaterialCheckResult {
        private boolean canPaste;
        private int totalConsumed;
        private int totalRequiredBlocks;
        private int requiredEmeralds;
        private int requiredFoodHunger;
        private int materialTypes;
        private final Map<MaterialKey, Integer> consumedByType = new HashMap<MaterialKey, Integer>();
        private final List<MaterialShortage> shortages = new ArrayList<MaterialShortage>();
    }

    private static final class FoodTransferResult {
        private int movedCount;
        private int movedHunger;
    }

    private static final class FoodUnit {
        private final int value;
        private final MaterialKey key;

        private FoodUnit(int value, MaterialKey key) {
            this.value = value;
            this.key = key;
        }
    }

    private static final class PasteConflict {
        private final int x;
        private final int y;
        private final int z;
        private final int existingId;
        private final int existingMeta;
        private final String existingName;
        private final int targetId;
        private final int targetMeta;
        private final String targetName;

        private PasteConflict(int x, int y, int z, int existingId, int existingMeta, String existingName, int targetId, int targetMeta, String targetName) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.existingId = existingId;
            this.existingMeta = existingMeta;
            this.existingName = existingName;
            this.targetId = targetId;
            this.targetMeta = targetMeta;
            this.targetName = targetName;
        }
    }

    private static final class MaterialShortage {
        private final String displayName;
        private final int missing;

        private MaterialShortage(String displayName, int missing) {
            this.displayName = displayName;
            this.missing = missing;
        }
    }

    private static final class MaterialKey {
        private final int itemId;
        private final int subtype;

        private MaterialKey(int itemId, int subtype) {
            this.itemId = itemId;
            this.subtype = subtype;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MaterialKey)) {
                return false;
            }
            MaterialKey other = (MaterialKey) obj;
            return this.itemId == other.itemId && this.subtype == other.subtype;
        }

        @Override
        public int hashCode() {
            return 31 * this.itemId + this.subtype;
        }
    }
}
