// MITE port (c) 2025 hahahha. Licensed under the MIT License.
package com.github.lunatrius.schematica.mixins.client;

import com.github.lunatrius.schematica.SchematicaRuntime;
import com.github.lunatrius.schematica.client.gui.GuiSchematicaControl;
import com.github.lunatrius.schematica.util.I18n;
import net.minecraft.EntityClientPlayerMP;
import net.minecraft.GameSettings;
import net.minecraft.GuiScreen;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.minecraft.KeyBinding;
import net.minecraft.Minecraft;
import net.minecraft.RaycastCollision;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public GuiScreen currentScreen;

    @Shadow
    public boolean inGameHasFocus;

    @Shadow
    public EntityClientPlayerMP thePlayer;

    @Shadow
    public GameSettings gameSettings;

    @Shadow
    public RaycastCollision objectMouseOver;

    @Shadow
    public abstract void displayGuiScreen(GuiScreen guiScreen);

    @Unique
    private boolean schematica$menuKeyDown;
    @Unique
    private boolean schematica$selectionClickDown;
    @Unique
    private static final String SCHEMATICA_MENU_KEY_DESCRIPTION = "key.schematica.menu";
    @Unique
    private static final int SCHEMATICA_MENU_KEY_DEFAULT = Keyboard.KEY_EQUALS;
    @Unique
    private static KeyBinding schematica$menuKeyBinding;

    @Inject(method = "runTick", at = @At("RETURN"))
    private void schematica$onRunTick(CallbackInfo ci) {
        schematica$ensureMenuKeyBinding();
        boolean pressed = schematica$menuKeyBinding != null
                && schematica$menuKeyBinding.keyCode > 0
                && Keyboard.isKeyDown(schematica$menuKeyBinding.keyCode);
        if (pressed
                && !this.schematica$menuKeyDown
                && schematica$canOpenMenu()) {
            this.displayGuiScreen(new GuiSchematicaControl());
        }
        this.schematica$menuKeyDown = pressed;

        schematica$handleSelectionStickClick();
    }

    @Unique
    private boolean schematica$canOpenMenu() {
        return this.currentScreen == null
                && this.inGameHasFocus
                && this.thePlayer != null;
    }

    @Unique
    private void schematica$ensureMenuKeyBinding() {
        if (this.gameSettings == null) {
            return;
        }
        if (schematica$menuKeyBinding == null) {
            schematica$menuKeyBinding = new KeyBinding(
                    SCHEMATICA_MENU_KEY_DESCRIPTION,
                    SCHEMATICA_MENU_KEY_DEFAULT);
        }
        KeyBinding[] current = this.gameSettings.keyBindings;
        if (current == null || current.length == 0) {
            this.gameSettings.keyBindings = new KeyBinding[]{schematica$menuKeyBinding};
            return;
        }
        for (KeyBinding keyBinding : current) {
            if (keyBinding == schematica$menuKeyBinding) {
                return;
            }
        }
        KeyBinding[] expanded = new KeyBinding[current.length + 1];
        System.arraycopy(current, 0, expanded, 0, current.length);
        expanded[current.length] = schematica$menuKeyBinding;
        this.gameSettings.keyBindings = expanded;
    }

    @Unique
    private void schematica$handleSelectionStickClick() {
        boolean rightDown = Mouse.isButtonDown(1);
        if (!rightDown) {
            this.schematica$selectionClickDown = false;
            return;
        }
        if (this.schematica$selectionClickDown) {
            return;
        }
        this.schematica$selectionClickDown = true;

        if (this.currentScreen != null || !this.inGameHasFocus || this.thePlayer == null || this.objectMouseOver == null || !this.objectMouseOver.isBlock()) {
            return;
        }

        ItemStack held = this.thePlayer.getHeldItemStack();
        if (held == null || held.getItem() != Item.stick) {
            return;
        }

        int x = this.objectMouseOver.block_hit_x;
        int y = this.objectMouseOver.block_hit_y;
        int z = this.objectMouseOver.block_hit_z;

        if (this.thePlayer.isSneaking()) {
            SchematicaRuntime.setSelectionPos2(x, y, z);
            this.thePlayer.addChatMessage(I18n.trf(
                    "schematica.selection.pos2_set",
                    "Schematica Pos2 set: [%d,%d,%d] (Shift+RightClick)",
                    x, y, z));
        } else {
            SchematicaRuntime.setSelectionPos1(x, y, z);
            this.thePlayer.addChatMessage(I18n.trf(
                    "schematica.selection.pos1_set",
                    "Schematica Pos1 set: [%d,%d,%d] (RightClick)",
                    x, y, z));
        }

        if (SchematicaRuntime.hasSelection()) {
            int minX = Math.min(SchematicaRuntime.selectionPos1X, SchematicaRuntime.selectionPos2X);
            int minY = Math.min(SchematicaRuntime.selectionPos1Y, SchematicaRuntime.selectionPos2Y);
            int minZ = Math.min(SchematicaRuntime.selectionPos1Z, SchematicaRuntime.selectionPos2Z);
            int maxX = Math.max(SchematicaRuntime.selectionPos1X, SchematicaRuntime.selectionPos2X);
            int maxY = Math.max(SchematicaRuntime.selectionPos1Y, SchematicaRuntime.selectionPos2Y);
            int maxZ = Math.max(SchematicaRuntime.selectionPos1Z, SchematicaRuntime.selectionPos2Z);
            int width = maxX - minX + 1;
            int height = maxY - minY + 1;
            int length = maxZ - minZ + 1;
            long volume = (long) width * height * length;
            this.thePlayer.addChatMessage(I18n.trf(
                    "schematica.selection.ready",
                    "Selection ready: [%d,%d,%d] -> [%d,%d,%d]  %dx%dx%d (%d blocks)",
                    minX, minY, minZ, maxX, maxY, maxZ, width, height, length, volume));
            this.thePlayer.addChatMessage(I18n.tr(
                    "schematica.selection.create_hint",
                    "Use: /schematica create <name>"));
        }
    }
}
