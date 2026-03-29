package com.github.lunatrius.schematica;

import com.github.lunatrius.schematica.FileFilterSchematic;
import com.github.lunatrius.schematica.SchematicaRuntime;
import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.block.SchematicaBlocks;
import com.github.lunatrius.schematica.util.I18n;
import com.github.lunatrius.schematica.world.schematic.SchematicFormat;
import com.github.lunatrius.schematica.world.storage.Schematic;
import com.google.common.eventbus.Subscribe;
import net.minecraft.Block;
import net.minecraft.EntityPlayer;
import net.minecraft.IInventory;
import net.minecraft.InventoryPlayer;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.Minecraft;
import net.minecraft.TileEntity;
import net.minecraft.World;
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

public class SurvivalSchematicaEventListener {
    private static final long MAX_SAVE_VOLUME = 8_000_000L;
    private static final long MAX_PASTE_VOLUME = 8_000_000L;
    private static final int MIN_WORLD_Y = 0;
    private static final int MAX_WORLD_Y = 255;

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
        event.getPlayer().addChatMessage(I18n.tr(
                "schematica.command.paste.disabled",
                "Direct paste is disabled in survival. Use the printer GUI to print."));
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
                    "Usage: /schematica printer <print|provide> ..."));
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

        if ("provide".equals(action)) {
            if (clientWorld) {
                boolean syntaxValid = handlePrinterProvide(event, parts, false);
                event.setExecuteSuccess(!syntaxValid);
            } else {
                event.setExecuteSuccess(true);
                handlePrinterProvide(event, parts, true);
            }
            return;
        }

        event.setExecuteSuccess(true);
        event.getPlayer().addChatMessage(I18n.tr(
                "schematica.command.printer.usage",
                "Usage: /schematica printer <print|provide> ..."));
    }

    private void handlePrinterPrint(HandleChatCommandEvent event, String[] parts) {
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

        if (!SchematicaRuntime.hasLoadedSchematic()) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.no_loaded", "No schematic loaded."));
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

        ISchematic schematic = SchematicaRuntime.loadedSchematic;
        long volume = (long) schematic.getWidth() * schematic.getHeight() * schematic.getLength();
        if (volume <= 0L || volume > MAX_PASTE_VOLUME) {
            event.getPlayer().addChatMessage(I18n.trf(
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
            event.getPlayer().addChatMessage(I18n.trf("schematica.command.paste.bounds", "Cannot paste: %s", boundsError));
            return;
        }

        PasteConflict conflict = findFirstPrinterCollision(
                event.getWorld(),
                schematic,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ);
        if (conflict != null) {
            event.getPlayer().addChatMessage(I18n.trf(
                    "schematica.command.printer.print.blocked",
                    "Print blocked at [%d,%d,%d]: found %s[%d:%d], target is %s[%d:%d].",
                    conflict.x, conflict.y, conflict.z,
                    conflict.existingName, conflict.existingId, conflict.existingMeta,
                    conflict.targetName, conflict.targetId, conflict.targetMeta));
            return;
        }

        MaterialCheckResult materialCheck = checkAndConsumePasteMaterialsFromInventory(
                printerInventory,
                event.getWorld(),
                schematic,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                mode);
        if (!materialCheck.canPaste) {
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

        Schematic undo = captureWorldRegion(
                event.getWorld(),
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                schematic.getWidth(),
                schematic.getHeight(),
                schematic.getLength(),
                copyIcon(schematic));
        if (undo == null) {
            event.getPlayer().addChatMessage(I18n.tr("schematica.command.paste.undo_capture_failed", "Failed to capture undo snapshot."));
            return;
        }
        SchematicaRuntime.setUndoSnapshot(
                undo,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                SchematicaRuntime.loadedSchematicName);

        PasteResult result = pasteSchematic(
                event.getWorld(),
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
        event.getPlayer().addChatMessage(I18n.trf(
                "schematica.command.printer.print.done",
                "Printer [%d,%d,%d] pasted %s mode=%s, materialsUsed=%d, materialTypes=%d: placed=%d, cleared=%d, containersEmptied=%d, unchanged=%d%s",
                printerX, printerY, printerZ, name, mode.id, materialCheck.totalConsumed, materialCheck.materialTypes,
                result.placed, result.cleared, result.containersEmptied, result.unchanged, failedSuffix));
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

        if (itemId <= 0 || itemId >= Item.itemsList.length || Item.itemsList[itemId] == null) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.printer.provide.invalid_item",
                        "Invalid item id: %d",
                        itemId));
            }
            return true;
        }
        if (amount <= 0) {
            if (sendFeedback) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.provide.usage",
                        "Usage: /schematica printer provide <x> <y> <z> <itemId> <subtype> [count]"));
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
            }
            return true;
        }

        int moved = transferFromPlayerToPrinter(player, printerInventory, itemId, subtype, amount);
        if (sendFeedback) {
            if (moved <= 0) {
                event.getPlayer().addChatMessage(I18n.tr(
                        "schematica.command.printer.provide.none",
                        "No matching items were moved."));
            } else {
                ItemStack display = new ItemStack(Item.itemsList[itemId], 1, subtype);
                int stored = countMaterialInInventory(printerInventory, new MaterialKey(itemId, subtype));
                event.getPlayer().addChatMessage(I18n.trf(
                        "schematica.command.printer.provide.done",
                        "Provided %d x %s to printer [%d,%d,%d]. Stored=%d",
                        moved, display.getDisplayName(), printerX, printerY, printerZ, stored));
            }
        }
        return true;
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
        for (int x = 0; x < schematic.getWidth(); ++x) {
            for (int y = 0; y < schematic.getHeight(); ++y) {
                for (int z = 0; z < schematic.getLength(); ++z) {
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
            }
        }
        return null;
    }

    private String safeBlockName(int blockId) {
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

        for (int x = 0; x < schematic.getWidth(); ++x) {
            for (int y = 0; y < schematic.getHeight(); ++y) {
                for (int z = 0; z < schematic.getLength(); ++z) {
                    Block block = schematic.getBlock(x, y, z);
                    if (block == null || block.blockID == 0) {
                        continue;
                    }
                    int metadata = schematic.getBlockMetadata(x, y, z);
                    int wx = originX + x;
                    int wy = originY + y;
                    int wz = originZ + z;
                    if (!needsMaterialForPlacement(world, wx, wy, wz, block.blockID, metadata, mode)) {
                        continue;
                    }
                    if (isDoorUpperHalf(block, metadata)) {
                        continue;
                    }

                    ItemStack cost = resolvePlacementCost(block, metadata);
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
                }
            }
        }

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

        if (!result.shortages.isEmpty()) {
            result.canPaste = false;
            compactShortages(result.shortages);
            sortShortages(result.shortages);
            return result;
        }

        for (Map.Entry<MaterialKey, Integer> entry : required.entrySet()) {
            if (!consumeFromInventory(inventory, entry.getKey(), entry.getValue())) {
                result.canPaste = false;
                result.shortages.add(new MaterialShortage(
                        I18n.tr("schematica.command.paste.shortage.inventory_changed", "Inventory changed while consuming materials."),
                        entry.getValue()));
                return result;
            }
        }

        return result;
    }

    private boolean needsMaterialForPlacement(World world, int x, int y, int z, int sourceBlockId, int sourceMeta, PasteMode mode) {
        if (mode != PasteMode.REPLACE && mode != PasteMode.SOLID) {
            return true;
        }
        int existingId = world.getBlockId(x, y, z);
        int existingMeta = world.getBlockMetadata(x, y, z);
        return existingId != sourceBlockId || existingMeta != (sourceMeta & 0xF);
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
        int newWidth = angle == 180 ? source.getWidth() : source.getLength();
        int newLength = angle == 180 ? source.getLength() : source.getWidth();
        Schematic rotated = new Schematic(copyIcon(source), newWidth, source.getHeight(), newLength);

        for (int x = 0; x < source.getWidth(); ++x) {
            for (int y = 0; y < source.getHeight(); ++y) {
                for (int z = 0; z < source.getLength(); ++z) {
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
                    rotated.setBlock(nx, y, nz, source.getBlock(x, y, z), source.getBlockMetadata(x, y, z));
                }
            }
        }
        return rotated;
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
        event.getPlayer().addChatMessage(I18n.tr("schematica.command.help.line9", "/schematica printer print <x> <y> <z> [replace|solid|nonair] | /schematica printer provide <x> <y> <z> <itemId> <subtype> [count]"));
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
        private int materialTypes;
        private final List<MaterialShortage> shortages = new ArrayList<MaterialShortage>();
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
