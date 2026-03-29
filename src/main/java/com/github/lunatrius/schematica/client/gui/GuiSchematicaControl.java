// MITE port (c) 2025 hahahha. Licensed under the MIT License.
package com.github.lunatrius.schematica.client.gui;

import com.github.lunatrius.schematica.FileFilterSchematic;
import com.github.lunatrius.schematica.SchematicaRuntime;
import com.github.lunatrius.schematica.api.ISchematic;
import com.github.lunatrius.schematica.util.I18n;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.GuiButton;
import net.minecraft.GuiScreen;
import net.minecraft.GuiTextField;
import net.minecraft.Minecraft;
import org.lwjgl.input.Keyboard;

public class GuiSchematicaControl extends GuiScreen {
    private static final int ID_STATUS = 1;
    private static final int ID_PASTE_REPLACE = 2;
    private static final int ID_PASTE_SOLID = 3;
    private static final int ID_UNDO = 4;
    private static final int ID_ORIGIN_HERE = 5;
    private static final int ID_ROTATE_90 = 6;
    private static final int ID_ROTATE_180 = 7;
    private static final int ID_ROTATE_270 = 8;
    private static final int ID_MIRROR_X = 9;
    private static final int ID_MIRROR_Z = 10;
    private static final int ID_NUDGE_X_POS = 11;
    private static final int ID_NUDGE_X_NEG = 12;
    private static final int ID_NUDGE_Z_POS = 13;
    private static final int ID_NUDGE_Z_NEG = 14;
    private static final int ID_NUDGE_Y_POS = 15;
    private static final int ID_NUDGE_Y_NEG = 16;
    private static final int ID_UNLOAD = 17;
    private static final int ID_CLOSE = 18;
    private static final int ID_FILE_RESCAN = 19;
    private static final int ID_FILE_PREV = 20;
    private static final int ID_FILE_NEXT = 21;
    private static final int ID_FILE_LOAD = 22;
    private static final int ID_MOVE_APPLY = 23;
    private static final int ID_CREATE_APPLY = 24;

    private final List<String> schematicFiles = new ArrayList<String>();
    private int selectedFileIndex = -1;

    private GuiTextField originXField;
    private GuiTextField originYField;
    private GuiTextField originZField;
    private GuiTextField createNameField;

    private int panelLeft;
    private int panelTop;
    private int buttonsTop;
    private int createTop;
    private int fieldsTop;

    @Override
    public void initGui() {
        this.buttonList.clear();

        int bw = 72;
        int bh = 20;
        int gap = 4;
        int sectionGap = 12;

        this.panelLeft = this.width / 2 - 152;
        this.panelTop = this.height / 2 - 142;
        this.buttonsTop = this.panelTop + 56;

        int x0 = panelLeft;
        int x1 = panelLeft + (bw + gap);
        int x2 = panelLeft + (bw + gap) * 2;
        int x3 = panelLeft + (bw + gap) * 3;

        int y = this.buttonsTop;
        this.buttonList.add(new GuiButton(ID_FILE_RESCAN, x0, y, bw, bh, tr("gui.schematica_control.button.rescan", "R Rescan")));
        this.buttonList.add(new GuiButton(ID_FILE_PREV, x1, y, 36, bh, tr("gui.schematica_control.button.file_prev", "[")));
        this.buttonList.add(new GuiButton(ID_FILE_NEXT, x1 + 40, y, 36, bh, tr("gui.schematica_control.button.file_next", "]")));
        this.buttonList.add(new GuiButton(ID_FILE_LOAD, panelLeft + 156, y, 144, bh, tr("gui.schematica_control.button.file_load", "L Load Selected")));

        y += bh + gap + sectionGap;
        this.createTop = y;
        this.createNameField = new GuiTextField(this.fontRenderer, panelLeft, y, 200, bh);
        this.createNameField.setMaxStringLength(64);
        this.buttonList.add(new GuiButton(ID_CREATE_APPLY, panelLeft + 204, y, 96, bh, tr("gui.schematica_control.button.create", "C Create")));

        y += bh + gap;
        this.buttonList.add(new GuiButton(ID_STATUS, x0, y, bw, bh, tr("gui.schematica_control.button.status", "T Status")));
        this.buttonList.add(new GuiButton(ID_PASTE_REPLACE, x1, y, bw, bh, tr("gui.schematica_control.button.paste_replace", "P Replace")));
        this.buttonList.add(new GuiButton(ID_PASTE_SOLID, x2, y, bw, bh, tr("gui.schematica_control.button.paste_solid", "O Solid")));
        this.buttonList.add(new GuiButton(ID_UNDO, x3, y, bw, bh, tr("gui.schematica_control.button.undo", "U Undo")));

        y += bh + gap;
        this.buttonList.add(new GuiButton(ID_ORIGIN_HERE, x0, y, bw, bh, tr("gui.schematica_control.button.origin_here", "H Origin Here")));
        this.buttonList.add(new GuiButton(ID_ROTATE_90, x1, y, bw, bh, tr("gui.schematica_control.button.rotate_90", "1 Rot 90")));
        this.buttonList.add(new GuiButton(ID_ROTATE_180, x2, y, bw, bh, tr("gui.schematica_control.button.rotate_180", "2 Rot 180")));
        this.buttonList.add(new GuiButton(ID_ROTATE_270, x3, y, bw, bh, tr("gui.schematica_control.button.rotate_270", "3 Rot 270")));

        y += bh + gap;
        this.buttonList.add(new GuiButton(ID_MIRROR_X, x0, y, bw, bh, tr("gui.schematica_control.button.mirror_x", "X Mirror X")));
        this.buttonList.add(new GuiButton(ID_MIRROR_Z, x1, y, bw, bh, tr("gui.schematica_control.button.mirror_z", "Z Mirror Z")));
        this.buttonList.add(new GuiButton(ID_NUDGE_X_POS, x2, y, bw, bh, tr("gui.schematica_control.button.nudge_x_pos", "D +X")));
        this.buttonList.add(new GuiButton(ID_NUDGE_X_NEG, x3, y, bw, bh, tr("gui.schematica_control.button.nudge_x_neg", "A -X")));

        y += bh + gap;
        this.buttonList.add(new GuiButton(ID_NUDGE_Z_POS, x0, y, bw, bh, tr("gui.schematica_control.button.nudge_z_pos", "S +Z")));
        this.buttonList.add(new GuiButton(ID_NUDGE_Z_NEG, x1, y, bw, bh, tr("gui.schematica_control.button.nudge_z_neg", "W -Z")));
        this.buttonList.add(new GuiButton(ID_NUDGE_Y_POS, x2, y, bw, bh, tr("gui.schematica_control.button.nudge_y_pos", "E +Y")));
        this.buttonList.add(new GuiButton(ID_NUDGE_Y_NEG, x3, y, bw, bh, tr("gui.schematica_control.button.nudge_y_neg", "Q -Y")));

        y += bh + gap;
        this.buttonList.add(new GuiButton(ID_UNLOAD, x0, y, bw * 2 + gap, bh, tr("gui.schematica_control.button.unload", "K Unload")));
        this.buttonList.add(new GuiButton(ID_CLOSE, x2, y, bw * 2 + gap, bh, tr("gui.schematica_control.button.close", "Esc Close")));

        y += bh + gap + sectionGap;
        this.fieldsTop = y;
        this.originXField = new GuiTextField(this.fontRenderer, panelLeft, y, 64, bh);
        this.originYField = new GuiTextField(this.fontRenderer, panelLeft + 68, y, 64, bh);
        this.originZField = new GuiTextField(this.fontRenderer, panelLeft + 136, y, 64, bh);
        this.originXField.setMaxStringLength(12);
        this.originYField.setMaxStringLength(12);
        this.originZField.setMaxStringLength(12);
        this.buttonList.add(new GuiButton(ID_MOVE_APPLY, panelLeft + 204, y, 96, bh, tr("gui.schematica_control.button.move_apply", "Enter Apply Move")));

        setOriginFieldsFromRuntime();
        refreshSchematicFiles();
        updateButtonStates();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }

        if (button.id == ID_CLOSE) {
            closeGui();
            return;
        }

        switch (button.id) {
            case ID_FILE_RESCAN:
                refreshSchematicFiles();
                break;
            case ID_FILE_PREV:
                selectPrevFile();
                break;
            case ID_FILE_NEXT:
                selectNextFile();
                break;
            case ID_FILE_LOAD:
                loadSelectedFile();
                break;
            case ID_CREATE_APPLY:
                applyCreateFromField();
                break;
            case ID_STATUS:
                runCommand("schematica status");
                setOriginFieldsFromRuntime();
                break;
            case ID_PASTE_REPLACE:
                sendLocalMessage(tr("gui.schematica_control.msg.paste_disabled", "Survival direct paste is disabled. Use the printer block GUI."));
                break;
            case ID_PASTE_SOLID:
                sendLocalMessage(tr("gui.schematica_control.msg.paste_disabled", "Survival direct paste is disabled. Use the printer block GUI."));
                break;
            case ID_UNDO:
                runCommand("schematica undo");
                break;
            case ID_ORIGIN_HERE:
                runCommand("schematica origin here");
                setOriginFieldsFromRuntime();
                break;
            case ID_ROTATE_90:
                runCommand("schematica rotate 90");
                break;
            case ID_ROTATE_180:
                runCommand("schematica rotate 180");
                break;
            case ID_ROTATE_270:
                runCommand("schematica rotate 270");
                break;
            case ID_MIRROR_X:
                runCommand("schematica mirror x");
                break;
            case ID_MIRROR_Z:
                runCommand("schematica mirror z");
                break;
            case ID_NUDGE_X_POS:
                runCommand("schematica nudge 1 0 0");
                setOriginFieldsFromRuntime();
                break;
            case ID_NUDGE_X_NEG:
                runCommand("schematica nudge -1 0 0");
                setOriginFieldsFromRuntime();
                break;
            case ID_NUDGE_Z_POS:
                runCommand("schematica nudge 0 0 1");
                setOriginFieldsFromRuntime();
                break;
            case ID_NUDGE_Z_NEG:
                runCommand("schematica nudge 0 0 -1");
                setOriginFieldsFromRuntime();
                break;
            case ID_NUDGE_Y_POS:
                runCommand("schematica nudge 0 1 0");
                setOriginFieldsFromRuntime();
                break;
            case ID_NUDGE_Y_NEG:
                runCommand("schematica nudge 0 -1 0");
                setOriginFieldsFromRuntime();
                break;
            case ID_MOVE_APPLY:
                applyMoveFromFields();
                break;
            case ID_UNLOAD:
                runCommand("schematica unload");
                break;
            default:
                break;
        }

        updateButtonStates();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeGui();
            return;
        }

        boolean editingCreateField = this.createNameField.isFocused();
        boolean editingMoveFields = this.originXField.isFocused()
                || this.originYField.isFocused()
                || this.originZField.isFocused();
        boolean editingFields = editingCreateField || editingMoveFields;

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (editingCreateField) {
                applyCreateFromField();
                return;
            }
            if (editingMoveFields) {
                applyMoveFromFields();
                return;
            }
            if (triggerButton(ID_MOVE_APPLY)) {
                return;
            }
        }

        if (this.createNameField.textboxKeyTyped(character, keyCode)
                || this.originXField.textboxKeyTyped(character, keyCode)
                || this.originYField.textboxKeyTyped(character, keyCode)
                || this.originZField.textboxKeyTyped(character, keyCode)) {
            updateButtonStates();
            return;
        }

        if (editingFields) {
            return;
        }

        if (handleHotkey(keyCode)) {
            return;
        }

        super.keyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.createNameField.mouseClicked(mouseX, mouseY, mouseButton);
        this.originXField.mouseClicked(mouseX, mouseY, mouseButton);
        this.originYField.mouseClicked(mouseX, mouseY, mouseButton);
        this.originZField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.createNameField.updateCursorCounter();
        this.originXField.updateCursorCounter();
        this.originYField.updateCursorCounter();
        this.originZField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int titleY = this.panelTop + 2;
        this.drawCenteredString(this.fontRenderer, tr("gui.schematica_control.title", "Schematica Control (M)"), this.width / 2, titleY, 0xFFFFFF);

        int lineY = titleY + 12;
        ISchematic schematic = SchematicaRuntime.loadedSchematic;
        if (schematic == null) {
            this.drawCenteredString(this.fontRenderer, tr("gui.schematica_control.no_loaded", "No schematic loaded (use /schematica load <name>)"), this.width / 2, lineY, 0xCCCCCC);
        } else {
            String name = SchematicaRuntime.loadedSchematicName == null
                    ? tr("gui.schematica_control.value.unknown", "<unknown>")
                    : SchematicaRuntime.loadedSchematicName;
            name = trimToWidth(name, 170);
            this.drawCenteredString(
                    this.fontRenderer,
                    trf("gui.schematica_control.loaded_line", "Loaded: %s  %dx%dx%d", name, schematic.getWidth(), schematic.getHeight(), schematic.getLength()),
                    this.width / 2,
                    lineY,
                    0xA8E6FF);
            this.drawCenteredString(
                    this.fontRenderer,
                    trf("gui.schematica_control.origin_line", "Origin: [%d,%d,%d]", SchematicaRuntime.originX, SchematicaRuntime.originY, SchematicaRuntime.originZ),
                    this.width / 2,
                    lineY + 10,
                    0xA8E6FF);
        }

        int selectedY = titleY + 34;
        if (this.schematicFiles.isEmpty() || this.selectedFileIndex < 0 || this.selectedFileIndex >= this.schematicFiles.size()) {
            this.drawCenteredString(this.fontRenderer, tr("gui.schematica_control.files_none", "Files: none"), this.width / 2, selectedY, 0xAAAAAA);
        } else {
            String selected = trimToWidth(this.schematicFiles.get(this.selectedFileIndex), 170);
            this.drawCenteredString(this.fontRenderer, trf("gui.schematica_control.files_line", "Files: %d/%d  %s", this.selectedFileIndex + 1, this.schematicFiles.size(), selected), this.width / 2, selectedY, 0xC6FFB7);
        }

        int hintY = titleY + 44;
        this.drawCenteredString(this.fontRenderer, tr("gui.schematica_control.hotkeys", "Hotkeys: [ ] / L / C / P / O / U / H / 1 2 3 / WASDQE"), this.width / 2, hintY, 0xAAAAAA);

        int createLabelY = this.createTop - 10;
        this.drawString(this.fontRenderer, tr("gui.schematica_control.label.create_name", "Create Name"), this.panelLeft + 6, createLabelY, 0xAAAAAA);

        int moveLabelY = this.fieldsTop - 10;
        this.drawString(this.fontRenderer, tr("gui.schematica_control.label.move_x", "Move X"), this.panelLeft + 8, moveLabelY, 0xAAAAAA);
        this.drawString(this.fontRenderer, tr("gui.schematica_control.label.move_y", "Y"), this.panelLeft + 94, moveLabelY, 0xAAAAAA);
        this.drawString(this.fontRenderer, tr("gui.schematica_control.label.move_z", "Z"), this.panelLeft + 160, moveLabelY, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
        this.createNameField.drawTextBox();
        this.originXField.drawTextBox();
        this.originYField.drawTextBox();
        this.originZField.drawTextBox();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void loadSelectedFile() {
        if (this.schematicFiles.isEmpty() || this.selectedFileIndex < 0 || this.selectedFileIndex >= this.schematicFiles.size()) {
            sendLocalMessage(tr("gui.schematica_control.msg.no_files", "No schematic files found."));
            return;
        }
        String name = this.schematicFiles.get(this.selectedFileIndex);
        runCommand("schematica load " + name);
    }

    private void selectPrevFile() {
        if (this.schematicFiles.isEmpty()) {
            this.selectedFileIndex = -1;
            return;
        }
        if (this.selectedFileIndex < 0) {
            this.selectedFileIndex = 0;
            return;
        }
        this.selectedFileIndex = (this.selectedFileIndex - 1 + this.schematicFiles.size()) % this.schematicFiles.size();
    }

    private void selectNextFile() {
        if (this.schematicFiles.isEmpty()) {
            this.selectedFileIndex = -1;
            return;
        }
        if (this.selectedFileIndex < 0) {
            this.selectedFileIndex = 0;
            return;
        }
        this.selectedFileIndex = (this.selectedFileIndex + 1) % this.schematicFiles.size();
    }

    private void refreshSchematicFiles() {
        String selectedBefore = null;
        if (this.selectedFileIndex >= 0 && this.selectedFileIndex < this.schematicFiles.size()) {
            selectedBefore = this.schematicFiles.get(this.selectedFileIndex);
        }

        this.schematicFiles.clear();
        File[] files = getSchematicDir().listFiles(new FileFilterSchematic(false));
        if (files != null) {
            for (File file : files) {
                if (file != null) {
                    this.schematicFiles.add(file.getName());
                }
            }
        }
        Collections.sort(this.schematicFiles, String.CASE_INSENSITIVE_ORDER);

        if (this.schematicFiles.isEmpty()) {
            this.selectedFileIndex = -1;
            return;
        }

        if (selectedBefore != null) {
            int idx = this.schematicFiles.indexOf(selectedBefore);
            if (idx >= 0) {
                this.selectedFileIndex = idx;
                return;
            }
        }

        this.selectedFileIndex = 0;
    }

    private void setOriginFieldsFromRuntime() {
        this.originXField.setText(String.valueOf(SchematicaRuntime.originX));
        this.originYField.setText(String.valueOf(SchematicaRuntime.originY));
        this.originZField.setText(String.valueOf(SchematicaRuntime.originZ));
    }

    private void applyMoveFromFields() {
        Integer x = parseInteger(this.originXField.getText());
        Integer y = parseInteger(this.originYField.getText());
        Integer z = parseInteger(this.originZField.getText());
        if (x == null || y == null || z == null) {
            sendLocalMessage(tr("gui.schematica_control.msg.move_invalid_int", "Move fields must be valid integers."));
            return;
        }
        runCommand("schematica move " + x + " " + y + " " + z);
    }

    private void applyCreateFromField() {
        String rawName = this.createNameField.getText();
        if (rawName == null || rawName.trim().isEmpty()) {
            sendLocalMessage(tr("gui.schematica_control.msg.create_empty", "Create name cannot be empty."));
            return;
        }
        if (!SchematicaRuntime.hasSelection()) {
            sendLocalMessage(tr("gui.schematica_control.msg.selection_missing", "Selection not set. Use stick Pos1/Pos2 first."));
            return;
        }
        runCommand("schematica create " + rawName.trim());
        refreshSchematicFiles();
    }

    private Integer parseInteger(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updateButtonStates() {
        boolean hasFiles = !this.schematicFiles.isEmpty();
        setButtonEnabled(ID_FILE_PREV, hasFiles);
        setButtonEnabled(ID_FILE_NEXT, hasFiles);
        setButtonEnabled(ID_FILE_LOAD, hasFiles);
        setButtonEnabled(ID_CREATE_APPLY, SchematicaRuntime.hasSelection());
        setButtonEnabled(ID_PASTE_REPLACE, false);
        setButtonEnabled(ID_PASTE_SOLID, false);
        setButtonEnabled(ID_UNDO, SchematicaRuntime.hasUndoSnapshot());
    }

    private boolean handleHotkey(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_R:
                return triggerButton(ID_FILE_RESCAN);
            case Keyboard.KEY_LBRACKET:
                return triggerButton(ID_FILE_PREV);
            case Keyboard.KEY_RBRACKET:
                return triggerButton(ID_FILE_NEXT);
            case Keyboard.KEY_L:
                return triggerButton(ID_FILE_LOAD);
            case Keyboard.KEY_C:
                return triggerButton(ID_CREATE_APPLY);
            case Keyboard.KEY_T:
                return triggerButton(ID_STATUS);
            case Keyboard.KEY_P:
                sendLocalMessage(tr("gui.schematica_control.msg.paste_disabled", "Survival direct paste is disabled. Use the printer block GUI."));
                return true;
            case Keyboard.KEY_O:
                sendLocalMessage(tr("gui.schematica_control.msg.paste_disabled", "Survival direct paste is disabled. Use the printer block GUI."));
                return true;
            case Keyboard.KEY_U:
                return triggerButton(ID_UNDO);
            case Keyboard.KEY_H:
                return triggerButton(ID_ORIGIN_HERE);
            case Keyboard.KEY_1:
                return triggerButton(ID_ROTATE_90);
            case Keyboard.KEY_2:
                return triggerButton(ID_ROTATE_180);
            case Keyboard.KEY_3:
                return triggerButton(ID_ROTATE_270);
            case Keyboard.KEY_X:
                return triggerButton(ID_MIRROR_X);
            case Keyboard.KEY_Z:
                return triggerButton(ID_MIRROR_Z);
            case Keyboard.KEY_D:
                return triggerButton(ID_NUDGE_X_POS);
            case Keyboard.KEY_A:
                return triggerButton(ID_NUDGE_X_NEG);
            case Keyboard.KEY_S:
                return triggerButton(ID_NUDGE_Z_POS);
            case Keyboard.KEY_W:
                return triggerButton(ID_NUDGE_Z_NEG);
            case Keyboard.KEY_E:
                return triggerButton(ID_NUDGE_Y_POS);
            case Keyboard.KEY_Q:
                return triggerButton(ID_NUDGE_Y_NEG);
            case Keyboard.KEY_K:
                return triggerButton(ID_UNLOAD);
            default:
                return false;
        }
    }

    private boolean triggerButton(int id) {
        for (Object object : this.buttonList) {
            if (!(object instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) object;
            if (button.id != id || !button.enabled) {
                continue;
            }
            actionPerformed(button);
            return true;
        }
        return false;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null || this.fontRenderer == null) {
            return "";
        }
        if (this.fontRenderer.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        int allowed = maxWidth - this.fontRenderer.getStringWidth(suffix);
        if (allowed <= 0) {
            return suffix;
        }
        return this.fontRenderer.trimStringToWidth(text, allowed) + suffix;
    }

    private void setButtonEnabled(int id, boolean enabled) {
        for (Object object : this.buttonList) {
            if (!(object instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) object;
            if (button.id == id) {
                button.enabled = enabled;
                return;
            }
        }
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

    private void sendLocalMessage(String message) {
        if (this.mc == null || this.mc.thePlayer == null || message == null || message.isEmpty()) {
            return;
        }
        this.mc.thePlayer.addChatMessage(message);
    }

    private File getSchematicDir() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "schematics");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private void closeGui() {
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
}

