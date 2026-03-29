package com.github.lunatrius.schematica.block;

import net.minecraft.Block;
import net.xiaoyu233.fml.reload.utils.IdUtil;

public final class SchematicaBlocks {
    public static final Block SCHEMATICA_PRINTER = new BlockSchematicPrinter(IdUtil.getNextBlockID());

    private SchematicaBlocks() {
    }
}
