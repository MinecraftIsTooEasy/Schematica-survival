package com.github.lunatrius.schematica.client.gui;

import com.github.lunatrius.schematica.FileFilterSchematic;
import com.github.lunatrius.schematica.SchematicaPrinterConfig;
import com.github.lunatrius.schematica.SchematicaRuntime;
import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.block.SchematicaBlocks;
import com.github.lunatrius.schematica.network.SchematicaPrinterNetworking;
import com.github.lunatrius.schematica.util.I18n;
import com.github.lunatrius.schematica.world.schematic.SchematicFormat;
import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.Block;
import net.minecraft.GuiButton;
import net.minecraft.GuiScreen;
import net.minecraft.IInventory;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.Minecraft;
import net.minecraft.TileEntity;
import net.minecraft.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GuiSchematicPrinterSelector extends GuiScreen {
    private static final int FOOD_VIRTUAL_ITEM_ID = -1;
    private static final int FOOD_VIRTUAL_SUBTYPE = 0;
    private static final int ID_PREV = 1;
    private static final int ID_NEXT = 2;
    private static final int ID_LOAD = 3;
    private static final int ID_PRINT = 4;
    private static final int ID_RESCAN = 5;
    private static final int ID_CLOSE = 6;
    private static final int ID_ROTATE = 7;
    private static final int ID_MIRROR_X = 8;
    private static final int ID_MIRROR_Z = 9;
    private static final int ID_UNDO = 10;
    private static final int ID_ALPHA_DOWN = 11;
    private static final int ID_ALPHA_UP = 12;
    private static final int ID_SUPPLY_BASE = 1000;

    private static final int SUPPLY_VISIBLE_ROWS = 6;
    private static final int SUPPLY_ROW_STRIDE = 21;
    private static final int SUPPLY_SCROLL_KNOB_WIDTH = 42;

    private static final int PANEL_WIDTH = 404;
    private static final int PANEL_HEIGHT = 344;
    private static final int PANEL_TOP_OFFSET = 172;
    private static final long SERVER_SNAPSHOT_MAX_AGE_MS = 300_000L;
    private static final long PRINT_COMPLETE_TIMEOUT_MS = 300000L;

    private static final Map<String, PrinterGuiState> LAST_STATE_BY_POS = new HashMap<String, PrinterGuiState>();

    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final List<String> schematicFiles = new ArrayList<String>();
    private final List<RequiredMaterial> requiredMaterials = new ArrayList<RequiredMaterial>();
    private final List<SupplyEntry> supplyEntries = new ArrayList<SupplyEntry>();
    private final List<GuiButton> supplyButtons = new ArrayList<GuiButton>();

    private int selectedFileIndex = -1;
    private int previewWidth;
    private int previewHeight;
    private int previewLength;
    private String confirmedFileName;
    private int rotationDegrees;
    private boolean mirrorX;
    private boolean mirrorZ;
    private String statusLine = "";

    private float supplyScrollProgress;
    private boolean supplyScrollDragging;
    private long lastSupplyRefreshAtMs;
    private long lastSupplyRequestAtMs;
    private int supplyListLeft;
    private int supplyListTop;
    private int supplyButtonWidth;
    private int supplyScrollBarLeft;
    private int supplyScrollBarTop;
    private int supplyScrollBarWidth;
    private int supplyScrollBarHeight;
    private boolean closeSyncApplied;
    private boolean awaitingPrintCompletion;
    private long awaitingPrintStartedAtMs;
    private String awaitingPrintConfirmedFile;

    public GuiSchematicPrinterSelector(int blockX, int blockY, int blockZ) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.closeSyncApplied = false;
        this.awaitingPrintCompletion = false;
        this.awaitingPrintStartedAtMs = 0L;
        this.awaitingPrintConfirmedFile = null;
        this.buttonList.clear();
        this.supplyButtons.clear();

        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = this.height / 2 - PANEL_TOP_OFFSET;
        int row1 = panelTop + 94;
        int row2 = row1 + 28;

        this.buttonList.add(new GuiButton(ID_PREV, panelLeft + 12, row1, 34, 20, "<"));
        this.buttonList.add(new GuiButton(ID_NEXT, panelLeft + 52, row1, 34, 20, ">"));
        this.buttonList.add(new GuiButton(ID_RESCAN, panelLeft + 92, row1, 78, 20, tr("gui.schematica_printer.button.rescan", "Rescan")));
        this.buttonList.add(new GuiButton(ID_LOAD, panelLeft + 176, row1, 116, 20, tr("gui.schematica_printer.button.load", "Confirm & Load")));
        this.buttonList.add(new GuiButton(ID_CLOSE, panelLeft + 298, row1, 94, 20, tr("gui.schematica_printer.button.close", "Close")));

        this.buttonList.add(new GuiButton(ID_ROTATE, panelLeft + 12, row2, 60, 20, ""));
        this.buttonList.add(new GuiButton(ID_MIRROR_X, panelLeft + 76, row2, 60, 20, ""));
        this.buttonList.add(new GuiButton(ID_MIRROR_Z, panelLeft + 140, row2, 60, 20, ""));
        this.buttonList.add(new GuiButton(ID_ALPHA_DOWN, panelLeft + 204, row2, 32, 20, "Op-"));
        this.buttonList.add(new GuiButton(ID_ALPHA_UP, panelLeft + 240, row2, 32, 20, "Op+"));
        this.buttonList.add(new GuiButton(ID_UNDO, panelLeft + 276, row2, 64, 20, tr("gui.schematica_printer.button.undo", "Undo")));
        this.buttonList.add(new GuiButton(ID_PRINT, panelLeft + 344, row2, 48, 20, tr("gui.schematica_printer.button.print", "Print")));

        this.supplyListLeft = panelLeft + 12;
        this.supplyListTop = panelTop + 182;
        this.supplyButtonWidth = PANEL_WIDTH - 24;
        for (int i = 0; i < SUPPLY_VISIBLE_ROWS; ++i) {
            GuiButton button = new GuiButton(ID_SUPPLY_BASE + i, this.supplyListLeft, this.supplyListTop + i * SUPPLY_ROW_STRIDE, this.supplyButtonWidth, 20, "");
            this.buttonList.add(button);
            this.supplyButtons.add(button);
        }

        this.supplyScrollBarLeft = this.supplyListLeft;
        this.supplyScrollBarTop = this.supplyListTop + SUPPLY_VISIBLE_ROWS * SUPPLY_ROW_STRIDE + 4;
        this.supplyScrollBarWidth = this.supplyButtonWidth;
        this.supplyScrollBarHeight = 12;

        restoreCachedState();
        updateTransformButtonLabels();
        updateAlphaButtonLabels();
        refreshSchematicFiles();
        refreshSupplyEntries(true);
        requestPrinterInventorySnapshot();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        if (button.id >= ID_SUPPLY_BASE && button.id < ID_SUPPLY_BASE + SUPPLY_VISIBLE_ROWS) {
            handleSupplyRow(button.id - ID_SUPPLY_BASE);
            return;
        }
        switch (button.id) {
            case ID_PREV:
                cycleSelection(-1);
                return;
            case ID_NEXT:
                cycleSelection(1);
                return;
            case ID_LOAD:
                confirmSelectedProjection(true);
                return;
            case ID_PRINT:
                printNorth();
                return;
            case ID_RESCAN:
                refreshSchematicFiles();
                return;
            case ID_CLOSE:
                closeGui();
                return;
            case ID_ROTATE:
                cycleRotation();
                return;
            case ID_MIRROR_X:
                this.mirrorX = !this.mirrorX;
                updateTransformButtonLabels();
                cacheCurrentState(false);
                return;
            case ID_MIRROR_Z:
                this.mirrorZ = !this.mirrorZ;
                updateTransformButtonLabels();
                cacheCurrentState(false);
                return;
            case ID_UNDO:
                runUndo();
                return;
            case ID_ALPHA_DOWN:
                adjustProjectionAlpha(-0.05F);
                return;
            case ID_ALPHA_UP:
                adjustProjectionAlpha(0.05F);
                return;
            default:
        }
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeGui();
            return;
        }
        if (keyCode == Keyboard.KEY_LBRACKET || keyCode == Keyboard.KEY_LEFT) {
            cycleSelection(-1);
            return;
        }
        if (keyCode == Keyboard.KEY_RBRACKET || keyCode == Keyboard.KEY_RIGHT) {
            cycleSelection(1);
            return;
        }
        if (keyCode == Keyboard.KEY_R) {
            refreshSchematicFiles();
            return;
        }
        if (keyCode == Keyboard.KEY_C) {
            confirmSelectedProjection(true);
            return;
        }
        if (keyCode == Keyboard.KEY_G) {
            cycleRotation();
            return;
        }
        if (keyCode == Keyboard.KEY_X) {
            this.mirrorX = !this.mirrorX;
            updateTransformButtonLabels();
            cacheCurrentState(false);
            return;
        }
        if (keyCode == Keyboard.KEY_Z) {
            this.mirrorZ = !this.mirrorZ;
            updateTransformButtonLabels();
            cacheCurrentState(false);
            return;
        }
        if (keyCode == Keyboard.KEY_L) {
            confirmSelectedProjection(true);
            return;
        }
        if (keyCode == Keyboard.KEY_P) {
            printNorth();
            return;
        }
        if (keyCode == Keyboard.KEY_U) {
            runUndo();
            return;
        }
        if (keyCode == Keyboard.KEY_MINUS || keyCode == Keyboard.KEY_SUBTRACT || keyCode == Keyboard.KEY_N) {
            adjustProjectionAlpha(-0.05F);
            return;
        }
        if (keyCode == Keyboard.KEY_EQUALS || keyCode == Keyboard.KEY_ADD || keyCode == Keyboard.KEY_B) {
            adjustProjectionAlpha(0.05F);
            return;
        }
        if (keyCode == Keyboard.KEY_UP) {
            adjustSupplyScrollByStep(-1);
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            adjustSupplyScrollByStep(1);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            confirmSelectedProjection(true);
            return;
        }
        super.keyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && isInSupplyScrollBar(mouseX, mouseY) && getSupplyMaxStartIndex() > 0) {
            this.supplyScrollDragging = true;
            updateSupplyScrollFromMouse(mouseX);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            this.supplyScrollDragging = false;
        }
        super.mouseMovedOrUp(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long dragTime) {
        if (this.supplyScrollDragging) {
            updateSupplyScrollFromMouse(mouseX);
        }
        super.mouseClickMove(mouseX, mouseY, button, dragTime);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        long now = System.currentTimeMillis();
        handlePendingPrintCompletion(now);
        refreshSupplyEntries(false);
        updateAlphaButtonLabels();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        int panelTop = this.height / 2 - PANEL_TOP_OFFSET;
        int panelRight = panelLeft + PANEL_WIDTH;
        int panelBottom = panelTop + PANEL_HEIGHT;
        int centerX = this.width / 2;
        int textTop = panelTop + 8;

        int wheel = Mouse.getDWheel();
        if (wheel != 0 && isInSupplyListArea(mouseX, mouseY)) {
            adjustSupplyScrollByStep(wheel > 0 ? -1 : 1);
        }

        drawRect(panelLeft, panelTop, panelRight, panelBottom, 0x90000000);
        drawRect(panelLeft, panelTop, panelRight, panelTop + 1, 0xD0FFFFFF);
        drawRect(panelLeft, panelBottom - 1, panelRight, panelBottom, 0x60FFFFFF);
        drawRect(panelLeft, panelTop, panelLeft + 1, panelBottom, 0x60FFFFFF);
        drawRect(panelRight - 1, panelTop, panelRight, panelBottom, 0x60FFFFFF);

        this.drawCenteredString(this.fontRenderer, tr("gui.schematica_printer.title", "Schematica Printer"), centerX, textTop, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer,
                trf("gui.schematica_printer.target_line", "Target block: [%d,%d,%d]", this.blockX, this.blockY, this.blockZ),
                centerX, textTop + 16, 0xAAAAAA);

        if (this.schematicFiles.isEmpty() || this.selectedFileIndex < 0 || this.selectedFileIndex >= this.schematicFiles.size()) {
            this.drawCenteredString(this.fontRenderer, tr("gui.schematica_printer.no_files", "No schematic files found in /schematics"), centerX, textTop + 36, 0xFF7777);
        } else {
            String selected = this.schematicFiles.get(this.selectedFileIndex);
            String confirmed = hasConfirmedSelection() ? this.confirmedFileName : tr("gui.schematica_printer.value.none", "<none>");
            String dimensions = hasConfirmedSelection() && this.previewWidth > 0 && this.previewHeight > 0 && this.previewLength > 0
                    ? trf("gui.schematica_printer.dimensions", " (%dx%dx%d)", this.previewWidth, this.previewHeight, this.previewLength)
                    : "";
            this.drawCenteredString(this.fontRenderer,
                    trf("gui.schematica_printer.selected_line", "Candidate: %d/%d  %s",
                            this.selectedFileIndex + 1, this.schematicFiles.size(), selected),
                    centerX, textTop + 36, 0xC6FFB7);
            this.drawCenteredString(this.fontRenderer,
                    trf("gui.schematica_printer.confirmed_line", "Confirmed: %s%s",
                            confirmed, dimensions),
                    centerX, textTop + 52, hasConfirmedSelection() ? 0x9CD7FF : 0xAAAAAA);

            if (hasConfirmedSelection() && this.previewWidth > 0 && this.previewLength > 0) {
                int transformedWidth = getTransformedWidth(this.previewWidth, this.previewLength);
                int transformedLength = getTransformedLength(this.previewWidth, this.previewLength);
                int originX = this.blockX - transformedWidth / 2;
                int originY = this.blockY;
                int originZ = this.blockZ - transformedLength;
                this.drawCenteredString(this.fontRenderer,
                        trf("gui.schematica_printer.transform_line", "Transform: rot=%d  mirrorX=%s  mirrorZ=%s",
                                this.rotationDegrees, onOff(this.mirrorX), onOff(this.mirrorZ)),
                        centerX, textTop + 68, 0xFFD37A);
                this.drawCenteredString(this.fontRenderer,
                        trf("gui.schematica_printer.origin_line", "North print origin: [%d,%d,%d]", originX, originY, originZ),
                        centerX, textTop + 82, 0x9CD7FF);
            }
        }
        this.drawCenteredString(
                this.fontRenderer,
                trf(
                        "gui.schematica_printer.opacity_line",
                        "Projection opacity: ghost=%d%%  line=%d%%  (- / =)",
                        alphaPercent(SchematicaPrinterConfig.getProjectionGhostAlphaSolid()),
                        alphaPercent(SchematicaPrinterConfig.getProjectionLineAlpha())),
                centerX,
                textTop + 100,
                0x8EC8FF);

        this.drawCenteredString(
                this.fontRenderer,
                tr("gui.schematica_printer.hotkeys", "Enter/L confirm+load  C confirm+load  P print  U undo  [ ] cycle  G/X/Z transform  -/= opacity"),
                centerX,
                textTop + 130,
                0xAAAAAA
        );

        if (this.statusLine != null && !this.statusLine.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, this.statusLine, centerX, textTop + 144, 0xFFE08A);
        }

        drawRect(panelLeft + 8, panelTop + 166, panelRight - 8, panelTop + 167, 0x40FFFFFF);
        this.drawCenteredString(this.fontRenderer,
                tr("gui.schematica_printer.supply.title", "Supply Materials (click a row to provide that item)"),
                centerX,
                textTop + 156,
                0xB8E8FF);

        if (this.requiredMaterials.isEmpty()) {
            this.drawCenteredString(
                    this.fontRenderer,
                    tr("gui.schematica_printer.supply.empty", "No required materials to show."),
                    centerX,
                    this.supplyListTop + 48,
                    0xAAAAAA);
        }

        drawRect(this.supplyScrollBarLeft, this.supplyScrollBarTop, this.supplyScrollBarLeft + this.supplyScrollBarWidth, this.supplyScrollBarTop + this.supplyScrollBarHeight, 0x40202020);
        if (getSupplyMaxStartIndex() > 0) {
            int knobTravel = Math.max(1, this.supplyScrollBarWidth - SUPPLY_SCROLL_KNOB_WIDTH);
            int knobLeft = this.supplyScrollBarLeft + (int) (knobTravel * this.supplyScrollProgress);
            drawRect(knobLeft, this.supplyScrollBarTop, knobLeft + SUPPLY_SCROLL_KNOB_WIDTH, this.supplyScrollBarTop + this.supplyScrollBarHeight, 0x90A6D9FF);
        } else {
            drawRect(this.supplyScrollBarLeft, this.supplyScrollBarTop, this.supplyScrollBarLeft + this.supplyScrollBarWidth, this.supplyScrollBarTop + this.supplyScrollBarHeight, 0x40909090);
        }
        this.drawCenteredString(
                this.fontRenderer,
                tr("gui.schematica_printer.supply.scroll_hint", "Drag the bar or use mouse wheel / Up / Down"),
                centerX,
                this.supplyScrollBarTop + this.supplyScrollBarHeight + 2,
                0x8EA0B8);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        applyCloseSyncIfNeeded();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public boolean allowsImposedChat() {
        return false;
    }

    private void refreshSchematicFiles() {
        String previousSelection = null;
        if (this.selectedFileIndex >= 0 && this.selectedFileIndex < this.schematicFiles.size()) {
            previousSelection = this.schematicFiles.get(this.selectedFileIndex);
        }
        PrinterGuiState cachedState = LAST_STATE_BY_POS.get(positionKey());
        String remembered = cachedState != null ? cachedState.selectedFile : null;

        this.schematicFiles.clear();
        File[] files = getSchematicDir().listFiles(new FileFilterSchematic(false));
        if (files != null) {
            for (File file : files) {
                if (file == null || !file.isFile()) {
                    continue;
                }
                this.schematicFiles.add(file.getName());
            }
        }
        Collections.sort(this.schematicFiles, String.CASE_INSENSITIVE_ORDER);

        if (this.schematicFiles.isEmpty()) {
            this.selectedFileIndex = -1;
            clearConfirmedSelection(false);
            this.statusLine = tr("gui.schematica_printer.status.put_files", "Put .schematic files into /schematics.");
            updateSupplyButtons();
            return;
        }

        int targetIndex = -1;
        if (remembered != null) {
            targetIndex = this.schematicFiles.indexOf(remembered);
        }
        if (targetIndex < 0 && previousSelection != null) {
            targetIndex = this.schematicFiles.indexOf(previousSelection);
        }
        if (targetIndex < 0) {
            targetIndex = 0;
        }

        selectIndex(targetIndex);
        if (hasConfirmedSelection()) {
            if (this.schematicFiles.indexOf(this.confirmedFileName) >= 0) {
                updatePreview(this.confirmedFileName);
            } else {
                clearConfirmedSelection(false);
            }
        } else {
            clearPreviewAndMaterials();
        }
        refreshSupplyEntries(true);
        this.statusLine = tr("gui.schematica_printer.status.select_confirm", "Select file, press Enter/L to confirm+load projection.");
    }

    private void cycleSelection(int delta) {
        if (this.schematicFiles.isEmpty()) {
            this.selectedFileIndex = -1;
            return;
        }
        if (this.selectedFileIndex < 0) {
            selectIndex(0);
            return;
        }
        int next = (this.selectedFileIndex + delta) % this.schematicFiles.size();
        if (next < 0) {
            next += this.schematicFiles.size();
        }
        selectIndex(next);
    }

    private void selectIndex(int index) {
        if (this.schematicFiles.isEmpty()) {
            this.selectedFileIndex = -1;
            clearPreviewAndMaterials();
            updateSupplyButtons();
            return;
        }
        int safeIndex = index;
        if (safeIndex < 0) {
            safeIndex = 0;
        }
        if (safeIndex >= this.schematicFiles.size()) {
            safeIndex = this.schematicFiles.size() - 1;
        }
        this.selectedFileIndex = safeIndex;
        cacheCurrentState(false);
        this.statusLine = tr("gui.schematica_printer.status.select_confirm", "Select file, press Enter/L to confirm+load projection.");
    }

    private void updatePreview(String selectedFileName) {
        this.previewWidth = 0;
        this.previewHeight = 0;
        this.previewLength = 0;
        this.requiredMaterials.clear();
        if (selectedFileName == null || selectedFileName.isEmpty()) {
            return;
        }
        File file = new File(getSchematicDir(), selectedFileName);
        ISchematic schematic = SchematicFormat.readFromFile(file);
        if (schematic == null) {
            return;
        }
        this.previewWidth = schematic.getWidth();
        this.previewHeight = schematic.getHeight();
        this.previewLength = schematic.getLength();
        rebuildRequiredMaterials(schematic);
    }

    private void clearPreviewAndMaterials() {
        this.previewWidth = 0;
        this.previewHeight = 0;
        this.previewLength = 0;
        this.requiredMaterials.clear();
        this.supplyEntries.clear();
        updateSupplyButtons();
    }

    private void clearConfirmedSelection(boolean keepCursorSelection) {
        cancelAwaitingPrintCompletion();
        this.confirmedFileName = null;
        clearPreviewAndMaterials();
        cacheCurrentState(false);
        if (!keepCursorSelection) {
            return;
        }
        if (!hasValidSelection() && !this.schematicFiles.isEmpty()) {
            this.selectedFileIndex = 0;
        }
    }

    private void rebuildRequiredMaterials(ISchematic schematic) {
        rebuildRequiredMaterials(schematic, null, 0, 0, 0, false);
    }

    private void rebuildRequiredMaterials(ISchematic schematic, World world, int originX, int originY, int originZ, boolean skipAlreadyPlaced) {
        this.requiredMaterials.clear();
        if (schematic == null) {
            return;
        }
        Map<MaterialKey, RequiredMaterial> merged = new HashMap<MaterialKey, RequiredMaterial>();
        int requiredBlocks = 0;
        for (int x = 0; x < schematic.getWidth(); ++x) {
            for (int y = 0; y < schematic.getHeight(); ++y) {
                for (int z = 0; z < schematic.getLength(); ++z) {
                    Block block = schematic.getBlock(x, y, z);
                    if (block == null || block.blockID == 0) {
                        continue;
                    }
                    int meta = schematic.getBlockMetadata(x, y, z);
                    if (skipAlreadyPlaced && world != null) {
                        int wx = originX + x;
                        int wy = originY + y;
                        int wz = originZ + z;
                        int existingId = world.getBlockId(wx, wy, wz);
                        int existingMeta = world.getBlockMetadata(wx, wy, wz);
                        if (existingId == block.blockID && existingMeta == (meta & 0xF)) {
                            continue;
                        }
                    }
                    if (isDoorUpperHalf(block, meta)) {
                        continue;
                    }
                    ItemStack cost = resolvePlacementCost(block, meta);
                    if (cost == null || cost.itemID <= 0 || cost.stackSize <= 0) {
                        continue;
                    }
                    MaterialKey key = new MaterialKey(cost.itemID, cost.getItemSubtype());
                    RequiredMaterial material = merged.get(key);
                    if (material == null) {
                        ItemStack display = cost.copy();
                        display.stackSize = 1;
                        material = new RequiredMaterial(key, display.getDisplayName());
                        merged.put(key, material);
                    }
                    material.required += Math.max(1, cost.stackSize);
                    ++requiredBlocks;
                }
            }
        }

        int requiredEmeralds = SchematicaPrinterConfig.computeRequiredEmeralds(requiredBlocks);
        if (requiredEmeralds > 0) {
            int emeraldItemId = SchematicaPrinterConfig.getEmeraldItemId();
            int emeraldSubtype = SchematicaPrinterConfig.getEmeraldSubtype();
            if (emeraldItemId > 0 && emeraldItemId < Item.itemsList.length && Item.itemsList[emeraldItemId] != null) {
                MaterialKey emeraldKey = new MaterialKey(emeraldItemId, emeraldSubtype);
                RequiredMaterial emeraldMaterial = merged.get(emeraldKey);
                if (emeraldMaterial == null) {
                    ItemStack display = new ItemStack(Item.itemsList[emeraldItemId], 1, emeraldSubtype);
                    emeraldMaterial = new RequiredMaterial(emeraldKey, display.getDisplayName());
                    merged.put(emeraldKey, emeraldMaterial);
                }
                emeraldMaterial.required += requiredEmeralds;
            }
        }
        int baseFoodHunger = 0;
        for (int x = 0; x < schematic.getWidth(); ++x) {
            for (int y = 0; y < schematic.getHeight(); ++y) {
                for (int z = 0; z < schematic.getLength(); ++z) {
                    Block block = schematic.getBlock(x, y, z);
                    if (block == null || block.blockID == 0) {
                        continue;
                    }
                    int meta = schematic.getBlockMetadata(x, y, z);
                    if (skipAlreadyPlaced && world != null) {
                        int wx = originX + x;
                        int wy = originY + y;
                        int wz = originZ + z;
                        int existingId = world.getBlockId(wx, wy, wz);
                        int existingMeta = world.getBlockMetadata(wx, wy, wz);
                        if (existingId == block.blockID && existingMeta == (meta & 0xF)) {
                            continue;
                        }
                    }
                    float hardness = Math.min(block.getBlockHardness(0), 20.0F);
                    baseFoodHunger += (int) Math.floor(Math.max(0.0F, hardness));
                }
            }
        }
        int requiredFoodHunger = SchematicaPrinterConfig.computeRequiredFoodHunger(baseFoodHunger);

        this.requiredMaterials.addAll(merged.values());
        if (requiredFoodHunger > 0) {
            this.requiredMaterials.add(new RequiredMaterial(
                    new MaterialKey(FOOD_VIRTUAL_ITEM_ID, FOOD_VIRTUAL_SUBTYPE),
                    tr("schematica.command.printer.food_label", "Food"),
                    requiredFoodHunger));
        }
    }

    private void confirmSelectedProjection(boolean fromConfirmKey) {
        cancelAwaitingPrintCompletion();
        if (!hasValidSelection()) {
            this.statusLine = tr("gui.schematica_printer.status.no_selection", "No schematic selected.");
            return;
        }
        this.confirmedFileName = this.schematicFiles.get(this.selectedFileIndex);
        cacheCurrentState(false);
        loadProjection(fromConfirmKey);
    }

    private void printNorth() {
        cancelAwaitingPrintCompletion();
        if (getPrinterInventory() == null) {
            this.statusLine = tr("gui.schematica_printer.supply.no_printer", "Printer block is missing or invalid.");
            return;
        }
        if (!loadProjection(false)) {
            return;
        }
        if (!SchematicaPrinterNetworking.uploadLoadedProjectionToServer()) {
            this.statusLine = tr(
                    "gui.schematica_printer.status.upload_failed",
                    "Failed to upload projection to server.");
            return;
        }
        long now = System.currentTimeMillis();
        runCommand("schematica printer print " + this.blockX + " " + this.blockY + " " + this.blockZ + " replace");
        beginAwaitingPrintCompletion(now);
        this.lastSupplyRefreshAtMs = 0L;
        refreshSupplyEntries(true);
        this.statusLine = tr(
                "gui.schematica_printer.status.print_requested_wait",
                "Print requested (using printer storage). Waiting for completion...");
        if (this.mc != null && this.mc.thePlayer != null) {
            this.mc.thePlayer.addChatMessage(tr("gui.schematica_printer.chat.print_executed", "Schematica printer: print requested."));
        }
    }

    private void beginAwaitingPrintCompletion(long now) {
        this.awaitingPrintCompletion = true;
        this.awaitingPrintStartedAtMs = now;
        this.awaitingPrintConfirmedFile = this.confirmedFileName;
    }

    private void cancelAwaitingPrintCompletion() {
        this.awaitingPrintCompletion = false;
        this.awaitingPrintStartedAtMs = 0L;
        this.awaitingPrintConfirmedFile = null;
    }

    private void handlePendingPrintCompletion(long now) {
        if (!this.awaitingPrintCompletion) {
            return;
        }
        if (!hasConfirmedSelection()) {
            cancelAwaitingPrintCompletion();
            return;
        }
        if (this.awaitingPrintConfirmedFile != null && !this.awaitingPrintConfirmedFile.equals(this.confirmedFileName)) {
            cancelAwaitingPrintCompletion();
            return;
        }
        if (SchematicaRuntime.hasLoadedSchematic()) {
            String loadedName = SchematicaRuntime.loadedSchematicName;
            if (loadedName != null
                    && this.awaitingPrintConfirmedFile != null
                    && !loadedName.equalsIgnoreCase(this.awaitingPrintConfirmedFile)) {
                cancelAwaitingPrintCompletion();
                return;
            }
            if (this.awaitingPrintStartedAtMs > 0L && now - this.awaitingPrintStartedAtMs > PRINT_COMPLETE_TIMEOUT_MS) {
                cancelAwaitingPrintCompletion();
                this.statusLine = tr(
                        "gui.schematica_printer.status.print_pending_timeout",
                        "Print still in progress or failed; selection kept.");
            }
            return;
        }
        finishPendingPrintAsSuccess();
    }

    private void finishPendingPrintAsSuccess() {
        cancelAwaitingPrintCompletion();
        clearConfirmedSelection(true);
        this.lastSupplyRefreshAtMs = 0L;
        refreshSupplyEntries(true);
        this.statusLine = tr(
                "gui.schematica_printer.status.print_requested_unselected",
                "Print requested. Projection reset to unconfirmed.");
    }

    private void runUndo() {
        if (getPrinterInventory() == null) {
            this.statusLine = tr("gui.schematica_printer.supply.no_printer", "Printer block is missing or invalid.");
            return;
        }
        runCommand("schematica printer undo " + this.blockX + " " + this.blockY + " " + this.blockZ);
        this.lastSupplyRefreshAtMs = 0L;
        refreshSupplyEntries(true);
        this.statusLine = tr("gui.schematica_printer.status.undo", "Undo requested.");
    }

    private boolean loadProjection(boolean fromConfirmKey) {
        if (this.mc == null || this.mc.thePlayer == null) {
            return false;
        }
        String selected = this.confirmedFileName;
        if (selected == null || selected.isEmpty()) {
            this.statusLine = tr("gui.schematica_printer.status.no_confirmed", "No confirmed schematic. Press Enter/L to confirm+load.");
            return false;
        }
        File file = new File(getSchematicDir(), selected);
        ISchematic schematic = SchematicFormat.readFromFile(file);
        if (schematic == null) {
            this.statusLine = tr("gui.schematica_printer.status.failed_load", "Failed to load selected schematic.");
            return false;
        }
        this.previewWidth = schematic.getWidth();
        this.previewHeight = schematic.getHeight();
        this.previewLength = schematic.getLength();
        rebuildRequiredMaterials(schematic);

        int transformedWidth = getTransformedWidth(schematic.getWidth(), schematic.getLength());
        int transformedLength = getTransformedLength(schematic.getWidth(), schematic.getLength());
        int originX = this.blockX - transformedWidth / 2;
        int originY = this.blockY;
        int originZ = this.blockZ - transformedLength;

        runCommand("schematica load " + selected);
        applySelectedTransforms();
        runCommand("schematica move " + originX + " " + originY + " " + originZ);

        this.statusLine = trf("gui.schematica_printer.status.outline_loaded", "Outline loaded at [%d,%d,%d]", originX, originY, originZ);
        this.mc.thePlayer.addChatMessage(trf(
                "gui.schematica_printer.chat.projection_loaded",
                "Projection loaded: %s -> [%d,%d,%d] (rot=%d, mirrorX=%s, mirrorZ=%s)",
                selected, originX, originY, originZ, this.rotationDegrees, onOff(this.mirrorX), onOff(this.mirrorZ)));
        if (fromConfirmKey) {
            this.mc.thePlayer.addChatMessage(tr("gui.schematica_printer.chat.outline_ready", "Outline ready. Press P to print, or Esc to inspect projection."));
        }
        cacheCurrentState(true);
        refreshSupplyEntries(true);
        return true;
    }

    private void applySelectedTransforms() {
        if (this.rotationDegrees == 90 || this.rotationDegrees == 180 || this.rotationDegrees == 270) {
            runCommand("schematica rotate " + this.rotationDegrees);
        }
        if (this.mirrorX) {
            runCommand("schematica mirror x");
        }
        if (this.mirrorZ) {
            runCommand("schematica mirror z");
        }
    }

    private void cycleRotation() {
        this.rotationDegrees += 90;
        if (this.rotationDegrees >= 360) {
            this.rotationDegrees = 0;
        }
        updateTransformButtonLabels();
        cacheCurrentState(false);
    }

    private void restoreCachedState() {
        String key = positionKey();
        PrinterGuiState state = LAST_STATE_BY_POS.get(key);
        if (state == null) {
            PrinterGuiState persisted = new PrinterGuiState();
            persisted.selectedFile = SchematicaPrinterConfig.getPrinterGuiSelectedFile(key);
            persisted.rotationDegrees = SchematicaPrinterConfig.getPrinterGuiRotation(key);
            persisted.mirrorX = SchematicaPrinterConfig.isPrinterGuiMirrorX(key);
            persisted.mirrorZ = SchematicaPrinterConfig.isPrinterGuiMirrorZ(key);
            persisted.lastAppliedSignature = SchematicaPrinterConfig.getPrinterGuiLastAppliedSignature(key);
            if (persisted.selectedFile != null
                    || persisted.rotationDegrees != 0
                    || persisted.mirrorX
                    || persisted.mirrorZ
                    || persisted.lastAppliedSignature != null) {
                state = persisted;
                LAST_STATE_BY_POS.put(key, state);
            }
        }
        if (state == null) {
            this.confirmedFileName = null;
            this.rotationDegrees = 0;
            this.mirrorX = false;
            this.mirrorZ = false;
            return;
        }
        this.confirmedFileName = state.selectedFile;
        this.rotationDegrees = normalizeRotation(state.rotationDegrees);
        this.mirrorX = state.mirrorX;
        this.mirrorZ = state.mirrorZ;
    }

    private PrinterGuiState getOrCreateCachedState() {
        String key = positionKey();
        PrinterGuiState state = LAST_STATE_BY_POS.get(key);
        if (state == null) {
            state = new PrinterGuiState();
            LAST_STATE_BY_POS.put(key, state);
        }
        return state;
    }

    private boolean hasValidSelection() {
        return !this.schematicFiles.isEmpty()
                && this.selectedFileIndex >= 0
                && this.selectedFileIndex < this.schematicFiles.size();
    }

    private boolean hasConfirmedSelection() {
        return this.confirmedFileName != null && !this.confirmedFileName.isEmpty();
    }

    private void cacheCurrentState(boolean projectionLoaded) {
        PrinterGuiState state = getOrCreateCachedState();
        state.rotationDegrees = normalizeRotation(this.rotationDegrees);
        state.mirrorX = this.mirrorX;
        state.mirrorZ = this.mirrorZ;
        state.selectedFile = hasConfirmedSelection() ? this.confirmedFileName : null;
        if (projectionLoaded) {
            state.lastAppliedSignature = buildProjectionSignature();
        } else if (!hasConfirmedSelection()) {
            state.lastAppliedSignature = null;
        }
        SchematicaPrinterConfig.setPrinterGuiState(
                positionKey(),
                state.selectedFile,
                state.rotationDegrees,
                state.mirrorX,
                state.mirrorZ,
                state.lastAppliedSignature);
    }

    private String buildProjectionSignature() {
        if (!hasConfirmedSelection()) {
            return "";
        }
        return this.confirmedFileName
                + "|" + normalizeRotation(this.rotationDegrees)
                + "|" + this.mirrorX
                + "|" + this.mirrorZ;
    }

    private void applyCloseSyncIfNeeded() {
        if (this.closeSyncApplied) {
            return;
        }
        this.closeSyncApplied = true;
        maybeAutoLoadProjectionOnClose();
    }

    private void maybeAutoLoadProjectionOnClose() {
        cacheCurrentState(false);
        String currentSignature = buildProjectionSignature();
        if (currentSignature.isEmpty()) {
            return;
        }
        PrinterGuiState state = getOrCreateCachedState();
        if (currentSignature.equals(state.lastAppliedSignature)) {
            return;
        }
        loadProjection(false);
    }

    private int normalizeRotation(int value) {
        int normalized = value % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        if (normalized == 90 || normalized == 180 || normalized == 270) {
            return normalized;
        }
        return 0;
    }

    private void updateTransformButtonLabels() {
        setButtonLabel(ID_ROTATE, trf("gui.schematica_printer.button.rotate_state", "Rotate:%d", this.rotationDegrees));
        setButtonLabel(ID_MIRROR_X, trf("gui.schematica_printer.button.mirror_x_state", "MirrorX:%s", onOff(this.mirrorX)));
        setButtonLabel(ID_MIRROR_Z, trf("gui.schematica_printer.button.mirror_z_state", "MirrorZ:%s", onOff(this.mirrorZ)));
    }

    private void updateAlphaButtonLabels() {
        int ghost = alphaPercent(SchematicaPrinterConfig.getProjectionGhostAlphaSolid());
        setButtonLabel(ID_ALPHA_DOWN, trf("gui.schematica_printer.button.alpha_down", "Op-%d", ghost));
        setButtonLabel(ID_ALPHA_UP, trf("gui.schematica_printer.button.alpha_up", "Op+%d", ghost));
    }

    private void adjustProjectionAlpha(float delta) {
        float solid = SchematicaPrinterConfig.getProjectionGhostAlphaSolid() + delta;
        float translucent = SchematicaPrinterConfig.getProjectionGhostAlphaTranslucent() + delta;
        float line = SchematicaPrinterConfig.getProjectionLineAlpha() + delta;
        SchematicaPrinterConfig.setProjectionAlphas(solid, translucent, line);
        updateAlphaButtonLabels();
        this.statusLine = trf(
                "gui.schematica_printer.status.opacity",
                "Projection opacity updated: ghost=%d%% line=%d%%",
                alphaPercent(SchematicaPrinterConfig.getProjectionGhostAlphaSolid()),
                alphaPercent(SchematicaPrinterConfig.getProjectionLineAlpha()));
    }

    private void setButtonLabel(int id, String label) {
        for (Object object : this.buttonList) {
            if (!(object instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) object;
            if (button.id == id) {
                button.displayString = label;
                return;
            }
        }
    }

    private void handleSupplyRow(int row) {
        int index = getSupplyStartIndex() + row;
        if (index < 0 || index >= this.supplyEntries.size()) {
            return;
        }
        SupplyEntry entry = this.supplyEntries.get(index);

        IInventory printerInventory = getPrinterInventory();
        if (printerInventory == null) {
            this.statusLine = tr("gui.schematica_printer.supply.no_printer", "Printer block is missing or invalid.");
            refreshSupplyEntries(true);
            return;
        }

        int missingNow = Math.max(0, entry.missing);
        if (missingNow <= 0) {
            this.statusLine = tr("gui.schematica_printer.supply.already_enough", "Printer already has enough of this material.");
            refreshSupplyEntries(true);
            return;
        }

        Map<MaterialKey, Integer> playerNow = collectMaterialCounts(this.mc != null && this.mc.thePlayer != null ? this.mc.thePlayer.inventory : null);
        int availableNow = entry.key.itemId == FOOD_VIRTUAL_ITEM_ID
                ? collectFoodHungerValue(this.mc != null && this.mc.thePlayer != null ? this.mc.thePlayer.inventory : null)
                : (playerNow.containsKey(entry.key) ? playerNow.get(entry.key) : 0);
        if (availableNow <= 0) {
            this.statusLine = tr("gui.schematica_printer.supply.no_player_item", "You do not have this item in inventory.");
            refreshSupplyEntries(true);
            return;
        }
        int request = missingNow <= 0 ? availableNow : Math.min(missingNow, availableNow);
        if (request <= 0) {
            this.statusLine = tr("gui.schematica_printer.supply.no_player_item", "You do not have this item in inventory.");
            refreshSupplyEntries(true);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastSupplyRequestAtMs < 150L) {
            this.statusLine = tr("gui.schematica_printer.supply.too_fast", "Too fast; wait a moment before the next supply request.");
            return;
        }
        this.lastSupplyRequestAtMs = now;

        if (entry.key.itemId == FOOD_VIRTUAL_ITEM_ID) {
            runCommand("schematica printer providefood "
                    + this.blockX + " " + this.blockY + " " + this.blockZ + " "
                    + request);
        } else {
            runCommand("schematica printer provide "
                    + this.blockX + " " + this.blockY + " " + this.blockZ + " "
                    + entry.key.itemId + " " + entry.key.subtype + " " + request);
        }
        this.lastSupplyRefreshAtMs = 0L;
        this.statusLine = trf("gui.schematica_printer.supply.provide_sent", "Supply requested: %s x%d", entry.displayName, request);
        refreshSupplyEntries(true);
    }

    private void refreshSupplyEntries(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - this.lastSupplyRefreshAtMs < 250L) {
            return;
        }
        this.lastSupplyRefreshAtMs = now;
        if (force) {
            rebuildRequiredMaterialsFromLoadedProjectionIfPresent();
        }

        this.supplyEntries.clear();
        if (this.requiredMaterials.isEmpty()) {
            updateSupplyButtons();
            return;
        }

        IInventory printerInventory = getPrinterInventory();
        if (printerInventory == null) {
            this.statusLine = tr("gui.schematica_printer.supply.no_printer", "Printer block is missing or invalid.");
            updateSupplyButtons();
            return;
        }

        Map<MaterialKey, Integer> stored = collectStoredCountsFromServerSnapshot(now);
        if (stored == null) {
            // Dedicated server does not share runtime snapshot memory with client.
            // Fall back to the client-side tile inventory view to avoid sync-command spam.
            stored = collectMaterialCounts(printerInventory);
        }
        Map<MaterialKey, Integer> player = collectMaterialCounts(this.mc != null && this.mc.thePlayer != null ? this.mc.thePlayer.inventory : null);
        int playerFoodHunger = collectFoodHungerValue(this.mc != null && this.mc.thePlayer != null ? this.mc.thePlayer.inventory : null);
        int storedFoodHunger = collectFoodHungerValue(stored);
        if (storedFoodHunger <= 0) {
            storedFoodHunger = collectFoodHungerValue(printerInventory);
        }

        for (RequiredMaterial required : this.requiredMaterials) {
            SupplyEntry entry = new SupplyEntry(required.key, required.displayName, required.required);
            if (required.key.itemId == FOOD_VIRTUAL_ITEM_ID) {
                entry.stored = storedFoodHunger;
                entry.playerAvailable = playerFoodHunger;
            } else {
                entry.stored = stored.containsKey(required.key) ? stored.get(required.key) : 0;
                entry.playerAvailable = player.containsKey(required.key) ? player.get(required.key) : 0;
            }
            entry.missing = Math.max(0, entry.required - entry.stored);
            this.supplyEntries.add(entry);
        }

        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(this.supplyEntries, new Comparator<SupplyEntry>() {
            @Override
            public int compare(SupplyEntry a, SupplyEntry b) {
                if (a == b) {
                    return 0;
                }
                int byMissing = Integer.compare(b.missing, a.missing);
                if (byMissing != 0) {
                    return byMissing;
                }
                return collator.compare(a.displayName, b.displayName);
            }
        });

        clampSupplyScroll();
        updateSupplyButtons();
    }

    private void rebuildRequiredMaterialsFromLoadedProjectionIfPresent() {
        if (this.mc == null || this.mc.theWorld == null || !SchematicaRuntime.hasLoadedSchematic()) {
            return;
        }
        if (!hasConfirmedSelection()) {
            return;
        }
        String selected = this.confirmedFileName;
        String loadedName = SchematicaRuntime.loadedSchematicName;
        if (selected == null || loadedName == null || !loadedName.equalsIgnoreCase(selected)) {
            return;
        }
        rebuildRequiredMaterials(
                SchematicaRuntime.loadedSchematic,
                this.mc.theWorld,
                SchematicaRuntime.originX,
                SchematicaRuntime.originY,
                SchematicaRuntime.originZ,
                true);
    }

    private IInventory getPrinterInventory() {
        if (this.mc == null || this.mc.theWorld == null) {
            return null;
        }
        if (this.mc.theWorld.getBlockId(this.blockX, this.blockY, this.blockZ) != SchematicaBlocks.SCHEMATICA_PRINTER.blockID) {
            return null;
        }
        TileEntity tileEntity = this.mc.theWorld.getBlockTileEntity(this.blockX, this.blockY, this.blockZ);
        return tileEntity instanceof IInventory ? (IInventory) tileEntity : null;
    }

    private Map<MaterialKey, Integer> collectMaterialCounts(IInventory inventory) {
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
            Integer old = counts.get(key);
            counts.put(key, old == null ? stack.stackSize : old + stack.stackSize);
        }
        return counts;
    }

    private int collectFoodHungerValue(IInventory inventory) {
        if (inventory == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); ++slot) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null || stack.stackSize <= 0 || stack.itemID <= 0 || stack.itemID >= Item.itemsList.length) {
                continue;
            }
            Item item = Item.itemsList[stack.itemID];
            if (item == null) {
                continue;
            }
            int value = Math.max(0, item.getSatiation(null)) + Math.max(0, item.getNutrition());
            if (value <= 0) {
                continue;
            }
            total += value * stack.stackSize;
        }
        return total;
    }

    private int collectFoodHungerValue(Map<MaterialKey, Integer> materialCounts) {
        if (materialCounts == null || materialCounts.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Map.Entry<MaterialKey, Integer> entry : materialCounts.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            int count = entry.getValue().intValue();
            if (count <= 0) {
                continue;
            }
            MaterialKey key = entry.getKey();
            if (key.itemId <= 0 || key.itemId >= Item.itemsList.length) {
                continue;
            }
            Item item = Item.itemsList[key.itemId];
            if (item == null) {
                continue;
            }
            int value = Math.max(0, item.getSatiation(null)) + Math.max(0, item.getNutrition());
            if (value <= 0) {
                continue;
            }
            total += value * count;
        }
        return total;
    }

    private Map<MaterialKey, Integer> collectStoredCountsFromServerSnapshot(long now) {
        long updatedAt = SchematicaRuntime.getPrinterInventorySnapshotUpdatedAt(this.blockX, this.blockY, this.blockZ);
        if (updatedAt <= 0L || now - updatedAt > SERVER_SNAPSHOT_MAX_AGE_MS) {
            return null;
        }
        Map<String, Integer> raw = SchematicaRuntime.getPrinterInventorySnapshotCounts(this.blockX, this.blockY, this.blockZ);
        if (raw == null) {
            return new HashMap<MaterialKey, Integer>();
        }
        Map<MaterialKey, Integer> counts = new HashMap<MaterialKey, Integer>();
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            MaterialKey key = parseMaterialKey(entry.getKey());
            if (key == null) {
                continue;
            }
            int amount = entry.getValue().intValue();
            if (amount <= 0) {
                continue;
            }
            Integer old = counts.get(key);
            counts.put(key, old == null ? amount : old + amount);
        }
        return counts;
    }

    private MaterialKey parseMaterialKey(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        int split = raw.indexOf(':');
        if (split <= 0 || split >= raw.length() - 1) {
            return null;
        }
        try {
            int itemId = Integer.parseInt(raw.substring(0, split));
            int subtype = Integer.parseInt(raw.substring(split + 1));
            if (itemId <= 0) {
                return null;
            }
            return new MaterialKey(itemId, subtype);
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private void clampSupplyScroll() {
        int maxStart = getSupplyMaxStartIndex();
        if (maxStart <= 0) {
            this.supplyScrollProgress = 0.0F;
            return;
        }
        if (this.supplyScrollProgress < 0.0F) {
            this.supplyScrollProgress = 0.0F;
        } else if (this.supplyScrollProgress > 1.0F) {
            this.supplyScrollProgress = 1.0F;
        }
    }

    private int getSupplyMaxStartIndex() {
        return Math.max(0, this.supplyEntries.size() - SUPPLY_VISIBLE_ROWS);
    }

    private int getSupplyStartIndex() {
        int maxStart = getSupplyMaxStartIndex();
        if (maxStart <= 0) {
            return 0;
        }
        return Math.round(this.supplyScrollProgress * maxStart);
    }

    private void adjustSupplyScrollByStep(int step) {
        int maxStart = getSupplyMaxStartIndex();
        if (maxStart <= 0) {
            return;
        }
        int next = getSupplyStartIndex() + step;
        if (next < 0) {
            next = 0;
        }
        if (next > maxStart) {
            next = maxStart;
        }
        this.supplyScrollProgress = maxStart == 0 ? 0.0F : (float) next / (float) maxStart;
        updateSupplyButtons();
    }

    private void updateSupplyScrollFromMouse(int mouseX) {
        int maxStart = getSupplyMaxStartIndex();
        if (maxStart <= 0) {
            this.supplyScrollProgress = 0.0F;
            updateSupplyButtons();
            return;
        }
        int travel = Math.max(1, this.supplyScrollBarWidth - SUPPLY_SCROLL_KNOB_WIDTH);
        float raw = (float) (mouseX - this.supplyScrollBarLeft - SUPPLY_SCROLL_KNOB_WIDTH / 2) / (float) travel;
        if (raw < 0.0F) {
            raw = 0.0F;
        } else if (raw > 1.0F) {
            raw = 1.0F;
        }
        this.supplyScrollProgress = raw;
        updateSupplyButtons();
    }

    private void updateSupplyButtons() {
        int start = getSupplyStartIndex();
        for (int row = 0; row < this.supplyButtons.size(); ++row) {
            GuiButton button = this.supplyButtons.get(row);
            int index = start + row;
            if (index < 0 || index >= this.supplyEntries.size()) {
                button.displayString = "";
                button.enabled = false;
                continue;
            }

            SupplyEntry entry = this.supplyEntries.get(index);
            button.displayString = trimToWidth(trf(
                    "gui.schematica_printer.supply.entry",
                    "%s | need:%d stored:%d inv:%d missing:%d",
                    entry.displayName, entry.required, entry.stored, entry.playerAvailable, entry.missing), this.supplyButtonWidth - 8);
            button.enabled = entry.playerAvailable > 0 && entry.missing > 0;
        }
    }

    private boolean isInSupplyScrollBar(int mouseX, int mouseY) {
        return mouseX >= this.supplyScrollBarLeft
                && mouseX < this.supplyScrollBarLeft + this.supplyScrollBarWidth
                && mouseY >= this.supplyScrollBarTop
                && mouseY < this.supplyScrollBarTop + this.supplyScrollBarHeight;
    }

    private boolean isInSupplyListArea(int mouseX, int mouseY) {
        int height = SUPPLY_VISIBLE_ROWS * SUPPLY_ROW_STRIDE;
        return mouseX >= this.supplyListLeft
                && mouseX < this.supplyListLeft + this.supplyButtonWidth
                && mouseY >= this.supplyListTop
                && mouseY < this.supplyListTop + height;
    }

    private int getTransformedWidth(int baseWidth, int baseLength) {
        return this.rotationDegrees == 90 || this.rotationDegrees == 270 ? baseLength : baseWidth;
    }

    private int getTransformedLength(int baseWidth, int baseLength) {
        return this.rotationDegrees == 90 || this.rotationDegrees == 270 ? baseWidth : baseLength;
    }

    private String onOff(boolean value) {
        return value ? tr("gui.schematica_printer.value.on", "On") : tr("gui.schematica_printer.value.off", "Off");
    }

    private int alphaPercent(float alpha) {
        return Math.round(alpha * 100.0F);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty() || this.fontRenderer == null || maxWidth <= 0) {
            return "";
        }
        if (this.fontRenderer.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = this.fontRenderer.getStringWidth(suffix);
        String value = text;
        while (!value.isEmpty() && this.fontRenderer.getStringWidth(value) + suffixWidth > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    private void runCommand(String command) {
        if (this.mc == null || this.mc.thePlayer == null || command == null || command.isEmpty()) {
            return;
        }
        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        this.mc.thePlayer.sendChatMessage(trimmed);
    }

    private void requestPrinterInventorySnapshot() {
        runCommand("schematica printer sync " + this.blockX + " " + this.blockY + " " + this.blockZ);
    }

    private File getSchematicDir() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "schematics");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private String positionKey() {
        return this.blockX + "," + this.blockY + "," + this.blockZ;
    }

    private void closeGui() {
        applyCloseSyncIfNeeded();
        if (this.mc != null) {
            this.mc.displayGuiScreen(null);
            this.mc.setIngameFocus();
        }
    }

    private String tr(String key, String fallback) {
        return I18n.tr(key, fallback);
    }

    private String trf(String key, String fallback, Object... args) {
        return I18n.trf(key, fallback, args);
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

    private static final class RequiredMaterial {
        private final MaterialKey key;
        private final String displayName;
        private int required;

        private RequiredMaterial(MaterialKey key, String displayName) {
            this(key, displayName, 0);
        }

        private RequiredMaterial(MaterialKey key, String displayName, int required) {
            this.key = key;
            this.displayName = displayName;
            this.required = required;
        }
    }

    private static final class SupplyEntry {
        private final MaterialKey key;
        private final String displayName;
        private final int required;
        private int stored;
        private int playerAvailable;
        private int missing;

        private SupplyEntry(MaterialKey key, String displayName, int required) {
            this.key = key;
            this.displayName = displayName;
            this.required = required;
        }
    }

    private static final class PrinterGuiState {
        private String selectedFile;
        private int rotationDegrees;
        private boolean mirrorX;
        private boolean mirrorZ;
        private String lastAppliedSignature;
    }
}
