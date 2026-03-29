package com.github.lunatrius.schematica;

import com.github.lunatrius.schematica.block.SchematicaBlocks;
import com.google.common.eventbus.Subscribe;
import net.minecraft.Block;
import net.minecraft.Item;
import net.minecraft.ItemStack;
import net.xiaoyu233.fml.reload.event.BlockRegistryEvent;
import net.xiaoyu233.fml.reload.event.ItemRegistryEvent;
import net.xiaoyu233.fml.reload.event.RecipeRegistryEvent;

public class SchematicaRegistryEventListener {
    @Subscribe
    public void onBlockRegister(BlockRegistryEvent event) {
        event.registerBlock(SchematicaSurvival.MOD_ID, "schematica_printer", "schematica_printer", SchematicaBlocks.SCHEMATICA_PRINTER);
    }

    @Subscribe
    public void onItemRegister(ItemRegistryEvent event) {
        event.registerItemBlock(SchematicaSurvival.MOD_ID, "schematica_printer", "schematica_printer", SchematicaBlocks.SCHEMATICA_PRINTER);
    }

    @Subscribe
    public void onRecipeRegister(RecipeRegistryEvent event) {
        event.registerShapedRecipe(new ItemStack(SchematicaBlocks.SCHEMATICA_PRINTER, 1), true,
                "SSS",
                "SRS",
                "SSS",
                'S', Item.ingotIron,
                'R', Item.redstone);
    }
}
