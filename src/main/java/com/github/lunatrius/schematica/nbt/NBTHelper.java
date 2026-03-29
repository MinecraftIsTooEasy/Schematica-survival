// Based on Schematica by Lunatrius (https://github.com/Lunatrius/Schematica)
// Licensed under the MIT License. See LICENSE file for details.
package com.github.lunatrius.schematica.nbt;

import net.minecraft.Entity;
import net.minecraft.EntityList;
import net.minecraft.EntityLivingBase;
import net.minecraft.NBTTagCompound;
import net.minecraft.NBTTagDouble;
import net.minecraft.NBTTagFloat;
import net.minecraft.NBTTagList;
import net.minecraft.TileEntity;
import net.minecraft.World;

public final class NBTHelper {
    private NBTHelper() {
    }

    public static NBTTagCompound writeTileEntityToCompound(TileEntity tileEntity) {
        NBTTagCompound tileEntityCompound = new NBTTagCompound();
        tileEntity.writeToNBT(tileEntityCompound);
        return tileEntityCompound;
    }

    public static TileEntity readTileEntityFromCompound(NBTTagCompound tileEntityCompound) {
        return TileEntity.createAndLoadEntity(tileEntityCompound);
    }

    public static NBTTagCompound writeEntityToCompound(Entity entity) {
        if (entity == null) {
            return null;
        }

        try {
            NBTTagCompound entityCompound = new NBTTagCompound();
            if (entity.writeToNBTOptional(entityCompound)) {
                return entityCompound;
            }

            String id = EntityList.getEntityString(entity);
            if (id == null || id.isEmpty()) {
                return createMinimalEntityCompound(entity);
            }

            entityCompound.setString("id", id);
            entity.writeToNBT(entityCompound);
            return entityCompound;
        } catch (Throwable ignored) {
            return createMinimalEntityCompound(entity);
        }
    }

    private static NBTTagCompound createMinimalEntityCompound(Entity entity) {
        String id = EntityList.getEntityString(entity);
        if (id == null || id.isEmpty()) {
            return null;
        }

        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", id);
        tag.setTag("Pos", newDoubleList(entity.posX, entity.posY, entity.posZ));
        tag.setTag("Motion", newDoubleList(entity.motionX, entity.motionY, entity.motionZ));
        tag.setTag("Rotation", newFloatList(entity.rotationYaw, entity.rotationPitch));
        tag.setFloat("FallDistance", entity.fallDistance);
        tag.setShort("Fire", (short)0);
        tag.setShort("Air", (short)entity.getAir());
        tag.setBoolean("OnGround", entity.onGround);
        tag.setInteger("Dimension", entity.dimension);
        tag.setBoolean("Invulnerable", false);
        tag.setInteger("PortalCooldown", 0);
        if (entity instanceof EntityLivingBase) {
            tag.setFloat("HealF", ((EntityLivingBase)entity).getHealth());
        }
        return tag;
    }

    private static NBTTagList newDoubleList(double x, double y, double z) {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagDouble(null, x));
        list.appendTag(new NBTTagDouble(null, y));
        list.appendTag(new NBTTagDouble(null, z));
        return list;
    }

    private static NBTTagList newFloatList(float yaw, float pitch) {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagFloat(null, yaw));
        list.appendTag(new NBTTagFloat(null, pitch));
        return list;
    }

    public static Entity readEntityFromCompound(NBTTagCompound entityCompound, World world) {
        if (entityCompound == null || world == null) {
            return null;
        }

        NBTTagCompound spawnTag = (NBTTagCompound)entityCompound.copy();
        clearEntityIdentity(spawnTag);
        return EntityList.createEntityFromNBT(spawnTag, world);
    }

    private static void clearEntityIdentity(NBTTagCompound entityTag) {
        if (entityTag == null) {
            return;
        }

        entityTag.removeTag("UUIDMost");
        entityTag.removeTag("UUIDLeast");
        entityTag.removeTag("UUID");
        entityTag.removeTag("PersistentIDMSB");
        entityTag.removeTag("PersistentIDLSB");
        entityTag.removeTag("UniqueIDMost");
        entityTag.removeTag("UniqueIDLeast");
        entityTag.removeTag("EntityUUIDMost");
        entityTag.removeTag("EntityUUIDLeast");

        if (entityTag.hasKey("Riding")) {
            NBTTagCompound riding = entityTag.getCompoundTag("Riding");
            clearEntityIdentity(riding);
            entityTag.setTag("Riding", riding);
        }
    }
}
