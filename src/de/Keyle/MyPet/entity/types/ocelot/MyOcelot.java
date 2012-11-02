/*
 * Copyright (C) 2011-2012 Keyle
 *
 * This file is part of MyPet
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
 * along with MyPet. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.entity.types.ocelot;

import de.Keyle.MyPet.entity.types.MyPet;
import de.Keyle.MyPet.entity.types.MyPetType;
import de.Keyle.MyPet.util.MyPetPlayer;
import net.minecraft.server.NBTTagCompound;

public class MyOcelot extends MyPet
{
    private boolean isSitting = false;
    private int catType = 0;

    public MyOcelot(MyPetPlayer petOwner)
    {
        super(petOwner);
        this.petName = "Ocelot";
    }

    public boolean isSitting()
    {
        if (status == PetState.Here)
        {
            return ((CraftMyOcelot) craftPet).isSitting();
        }
        else
        {
            return isSitting;
        }
    }

    public void setSitting(boolean sitting)
    {
        ((CraftMyOcelot) craftPet).setSitting(sitting);
        this.isSitting = sitting;
    }

    public void setCatType(int catType)
    {
        this.catType = catType;
        if (status == PetState.Here)
        {
            ((EntityMyOcelot) craftPet.getHandle()).setCatType(catType);
        }
    }

    public int getCatType()
    {
        return catType;
    }

    @Override
    public NBTTagCompound getExtendedInfo()
    {
        NBTTagCompound info = new NBTTagCompound("Info");
        info.setInt("catType", catType);
        info.setBoolean("sitting", isSitting());
        return info;
    }

    @Override
    public void setExtendedInfo(NBTTagCompound info)
    {
        setCatType(info.getInt("catType"));
        if (info.hasKey("sitting"))
        {
            setSitting(info.getBoolean("sitting"));
        }
    }

    @Override
    public MyPetType getPetType()
    {
        return MyPetType.Ocelot;
    }

    @Override
    public String toString()
    {
        return "MyOcelot{owner=" + getOwner().getName() + ", name=" + petName + ", exp=" + experience.getExp() + "/" + experience.getRequiredExp() + ", lv=" + experience.getLevel() + ", status=" + status.name() + ", skilltree=" + skillTree.getName() + ", sitting=" + isSitting() + "}";
    }
}