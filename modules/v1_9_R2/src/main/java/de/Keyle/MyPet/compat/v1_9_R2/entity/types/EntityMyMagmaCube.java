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

package de.Keyle.MyPet.compat.v1_9_R2.entity.types;

import de.Keyle.MyPet.api.entity.EntitySize;
import de.Keyle.MyPet.api.entity.MyPet;
import de.Keyle.MyPet.api.entity.types.MyMagmaCube;
import de.Keyle.MyPet.compat.v1_9_R2.entity.EntityMyPet;
import de.Keyle.MyPet.compat.v1_9_R2.entity.ai.attack.MeleeAttack;
import net.minecraft.server.v1_9_R2.DataWatcher;
import net.minecraft.server.v1_9_R2.DataWatcherObject;
import net.minecraft.server.v1_9_R2.DataWatcherRegistry;
import net.minecraft.server.v1_9_R2.World;

@EntitySize(width = 0.5100001F, height = 0.5100001F)
public class EntityMyMagmaCube extends EntityMyPet {
    private static final DataWatcherObject<Integer> sizeWatcher = DataWatcher.a(EntityMyMagmaCube.class, DataWatcherRegistry.b);

    int jumpDelay;

    public EntityMyMagmaCube(World world, MyPet myPet) {
        super(world, myPet);
    }

    @Override
    protected String getDeathSound() {
        return "entity.magmacube.death";
    }

    @Override
    protected String getHurtSound() {
        return "entity.magmacube.hurt";
    }

    protected String getLivingSound() {
        return null;
    }

    protected void initDatawatcher() {
        super.initDatawatcher();
        this.datawatcher.register(sizeWatcher, 1); //size
    }

    public void onLivingUpdate() {
        super.onLivingUpdate();

        if (this.onGround && jumpDelay-- <= 0) {
            getControllerJump().a();
            jumpDelay = (this.random.nextInt(20) + 50);
            this.makeSound("entity.magmacube.jump", 1.0F, ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
        }
    }

    @Override
    public void updateVisuals() {
        int size = Math.max(1, getMyPet().getSize());
        this.datawatcher.set(sizeWatcher, size);
        EntitySize es = EntityMyMagmaCube.class.getAnnotation(EntitySize.class);
        if (es != null) {
            this.setSize(es.width() * size, es.width() * size);
        }
        if (petPathfinderSelector != null && petPathfinderSelector.hasGoal("MeleeAttack")) {
            petPathfinderSelector.replaceGoal("MeleeAttack", new MeleeAttack(this, 0.1F, 3 + (getMyPet().getSize() * 0.51), 20));
        }
    }

    public MyMagmaCube getMyPet() {
        return (MyMagmaCube) myPet;
    }

    public void setPathfinder() {
        super.setPathfinder();
        petPathfinderSelector.replaceGoal("MeleeAttack", new MeleeAttack(this, 0.1F, 3 + (getMyPet().getSize() * 0.51), 20));
    }
}