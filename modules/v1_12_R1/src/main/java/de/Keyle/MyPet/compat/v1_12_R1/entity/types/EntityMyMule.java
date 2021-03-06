/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2016 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.compat.v1_12_R1.entity.types;

import com.google.common.base.Optional;
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.entity.EntitySize;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.entity.types.MyMule;
import de.Keyle.MyPet.compat.v1_12_R1.entity.EntityMyPet;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;

import java.util.UUID;

@EntitySize(width = 1.4F, height = 1.6F)
public class EntityMyMule extends EntityMyPet {
    protected static final DataWatcherObject<Boolean> ageWatcher = DataWatcher.a(EntityMyMule.class, DataWatcherRegistry.h);
    protected static final DataWatcherObject<Byte> saddleChestWatcher = DataWatcher.a(EntityMyMule.class, DataWatcherRegistry.a);
    protected static final DataWatcherObject<Optional<UUID>> ownerWatcher = DataWatcher.a(EntityMyMule.class, DataWatcherRegistry.m);
    private static final DataWatcherObject<Boolean> chestWatcher = DataWatcher.a(EntityMyMule.class, DataWatcherRegistry.h);

    int rearCounter = -1;

    public EntityMyMule(World world, MyPet myPet) {
        super(world, myPet);
    }

    /**
     * Possible visual horse effects:
     * 4 saddle
     * 8 chest
     * 32 head down
     * 64 rear
     * 128 mouth open
     */
    protected void applyVisual(int value, boolean flag) {
        int i = this.datawatcher.get(saddleChestWatcher);
        if (flag) {
            this.datawatcher.set(saddleChestWatcher, (byte) (i | value));
        } else {
            this.datawatcher.set(saddleChestWatcher, (byte) (i & (~value)));
        }
    }

    public boolean attack(Entity entity) {
        boolean flag = false;
        try {
            flag = super.attack(entity);
            if (flag) {
                applyVisual(64, true);
                rearCounter = 10;
                this.makeSound("entity.donkey.angry", 1.0F, 1.0F);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
    }

    public boolean handlePlayerInteraction(final EntityHuman entityhuman, EnumHand enumhand, final ItemStack itemStack) {
        if (super.handlePlayerInteraction(entityhuman, enumhand, itemStack)) {
            return true;
        }

        if (itemStack != null && canUseItem()) {
            if (itemStack.getItem() == Items.SADDLE && !getMyPet().hasSaddle() && !getMyPet().isBaby() && getOwner().getPlayer().isSneaking() && canEquip()) {
                getMyPet().setSaddle(CraftItemStack.asBukkitCopy(itemStack));
                if (!entityhuman.abilities.canInstantlyBuild) {
                    itemStack.subtract(1);
                    if (itemStack.getCount() <= 0) {
                        entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, ItemStack.a);
                    }
                }
                return true;
            } else if (itemStack.getItem() == Item.getItemOf(Blocks.CHEST) && getOwner().getPlayer().isSneaking() && !getMyPet().hasChest() && !getMyPet().isBaby() && canEquip()) {
                getMyPet().setChest(CraftItemStack.asBukkitCopy(itemStack));
                if (!entityhuman.abilities.canInstantlyBuild) {
                    itemStack.subtract(1);
                    if (itemStack.getCount() <= 0) {
                        entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, ItemStack.a);
                    }
                }
                return true;
            } else if (itemStack.getItem() == Items.SHEARS && getOwner().getPlayer().isSneaking() && canEquip()) {
                if (getMyPet().hasChest()) {
                    EntityItem entityitem = new EntityItem(this.world, this.locX, this.locY + 1, this.locZ, CraftItemStack.asNMSCopy(getMyPet().getChest()));
                    entityitem.pickupDelay = 10;
                    entityitem.motY += (double) (this.random.nextFloat() * 0.05F);
                    this.world.addEntity(entityitem);
                }
                if (getMyPet().hasSaddle()) {
                    EntityItem entityitem = new EntityItem(this.world, this.locX, this.locY + 1, this.locZ, CraftItemStack.asNMSCopy(getMyPet().getSaddle()));
                    entityitem.pickupDelay = 10;
                    entityitem.motY += (double) (this.random.nextFloat() * 0.05F);
                    this.world.addEntity(entityitem);
                }

                makeSound("entity.sheep.shear", 1.0F, 1.0F);
                getMyPet().setChest(null);
                getMyPet().setSaddle(null);
                if (!entityhuman.abilities.canInstantlyBuild) {
                    itemStack.damage(1, entityhuman);
                }

                return true;
            } else if (Configuration.MyPet.Horse.GROW_UP_ITEM.compare(itemStack) && getMyPet().isBaby() && getOwner().getPlayer().isSneaking()) {
                if (!entityhuman.abilities.canInstantlyBuild) {
                    itemStack.subtract(1);
                    if (itemStack.getCount() <= 0) {
                        entityhuman.inventory.setItem(entityhuman.inventory.itemInHandIndex, ItemStack.a);
                    }
                }
                getMyPet().setBaby(false);
                return true;
            }
        }
        return false;
    }

    protected void initDatawatcher() {
        this.datawatcher.register(ageWatcher, false);
        this.datawatcher.register(saddleChestWatcher, (byte) 0);
        this.datawatcher.register(ownerWatcher, Optional.absent());
        this.datawatcher.register(chestWatcher, false);
    }

    @Override
    public void updateVisuals() {
        this.datawatcher.set(ageWatcher, getMyPet().isBaby());
        this.datawatcher.set(chestWatcher, getMyPet().hasChest());
    }

    @Override
    protected String getDeathSound() {
        return "entity.mule.death";
    }

    @Override
    protected String getHurtSound() {
        return "entity.mule.hurt";
    }

    protected String getLivingSound() {
        return "entity.mule.ambient";
    }

    public void onLivingUpdate() {
        boolean oldRiding = hasRider;
        super.onLivingUpdate();
        if (rearCounter > -1 && rearCounter-- == 0) {
            applyVisual(64, false);
            rearCounter = -1;
        }
        if (oldRiding != hasRider) {
            if (hasRider) {
                applyVisual(4, true);
            } else {
                applyVisual(4, getMyPet().hasSaddle());
            }
        }
    }

    @Override
    public void playStepSound(BlockPosition pos, Block block) {
        SoundEffectType soundeffecttype = block.getStepSound();
        if (this.world.getType(pos) == Blocks.SNOW) {
            soundeffecttype = Blocks.SNOW_LAYER.getStepSound();
        }
        if (!block.getBlockData().getMaterial().isLiquid()) {
            if (soundeffecttype == SoundEffectType.a) {
                a(SoundEffects.cB, soundeffecttype.a() * 0.15F, soundeffecttype.b());
            } else {
                a(SoundEffects.cA, soundeffecttype.a() * 0.15F, soundeffecttype.b());
            }
        }
    }

    public MyMule getMyPet() {
        return (MyMule) myPet;
    }
}