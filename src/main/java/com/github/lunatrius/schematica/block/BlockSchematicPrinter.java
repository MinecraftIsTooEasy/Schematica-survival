package com.github.lunatrius.schematica.block;

import com.github.lunatrius.schematica.client.gui.GuiSchematicPrinterSelector;
import net.minecraft.Block;
import net.minecraft.BlockConstants;
import net.minecraft.BlockContainer;
import net.minecraft.CreativeTabs;
import net.minecraft.EntityItem;
import net.minecraft.EntityPlayer;
import net.minecraft.EnumFace;
import net.minecraft.IInventory;
import net.minecraft.ItemStack;
import net.minecraft.Material;
import net.minecraft.Minecraft;
import net.minecraft.TileEntity;
import net.minecraft.TileEntityChest;
import net.minecraft.World;
import java.util.Random;

public class BlockSchematicPrinter extends BlockContainer {
    private static final Random RANDOM = new Random();

    public BlockSchematicPrinter(int id) {
        super(id, Material.stone, new BlockConstants());
        this.setHardness(2.0F);
        this.setResistance(10.0F);
        this.setStepSound(Block.soundStoneFootstep);
        this.setCreativeTab(CreativeTabs.tabDecorations);
        this.setUnlocalizedName("schematica_printer");
        this.setTextureName("command_block");
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, EnumFace face, float offsetX, float offsetY, float offsetZ) {
        if (world == null || player == null) {
            return false;
        }
        if (world.isRemote) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.displayGuiScreen(new GuiSchematicPrinterSelector(x, y, z));
            }
        }
        return true;
    }

    @Override
    public TileEntity createNewTileEntity(World world) {
        return new TileEntityChest();
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, int blockId, int metadata) {
        if (world != null && !world.isRemote) {
            TileEntity tileEntity = world.getBlockTileEntity(x, y, z);
            if (tileEntity instanceof IInventory) {
                IInventory inventory = (IInventory) tileEntity;
                for (int slot = 0; slot < inventory.getSizeInventory(); ++slot) {
                    ItemStack slotStack = inventory.getStackInSlot(slot);
                    if (slotStack == null || slotStack.stackSize <= 0) {
                        continue;
                    }
                    ItemStack stackToDrop = slotStack.copy();
                    inventory.setInventorySlotContents(slot, null);
                    spawnInventoryDrop(world, x, y, z, stackToDrop);
                }
                inventory.onInventoryChanged();
                world.func_96440_m(x, y, z, blockId);
            }
        }
        super.breakBlock(world, x, y, z, blockId, metadata);
    }

    private void spawnInventoryDrop(World world, int x, int y, int z, ItemStack stack) {
        if (world == null || stack == null || stack.stackSize <= 0) {
            return;
        }

        ItemStack source = stack.copy();
        float dx = RANDOM.nextFloat() * 0.8F + 0.1F;
        float dy = RANDOM.nextFloat() * 0.8F + 0.1F;
        float dz = RANDOM.nextFloat() * 0.8F + 0.1F;
        int remaining = source.stackSize;

        while (remaining > 0) {
            int count = RANDOM.nextInt(21) + 10;
            if (count > remaining) {
                count = remaining;
            }
            remaining -= count;

            ItemStack dropStack = source.copy();
            dropStack.stackSize = count;

            EntityItem drop = new EntityItem(world, (float) x + dx, (float) y + dy, (float) z + dz, dropStack);
            float velocity = 0.05F;
            drop.motionX = (float) RANDOM.nextGaussian() * velocity;
            drop.motionY = (float) RANDOM.nextGaussian() * velocity + 0.2F;
            drop.motionZ = (float) RANDOM.nextGaussian() * velocity;
            world.spawnEntityInWorld(drop);
        }
    }
}
