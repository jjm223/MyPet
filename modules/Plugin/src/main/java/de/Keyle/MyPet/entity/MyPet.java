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

package de.Keyle.MyPet.entity;

import com.google.common.base.Optional;
import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.Util;
import de.Keyle.MyPet.api.WorldGroup;
import de.Keyle.MyPet.api.entity.EntitySize;
import de.Keyle.MyPet.api.entity.MyPetBukkitEntity;
import de.Keyle.MyPet.api.entity.MyPetMinecraftEntity;
import de.Keyle.MyPet.api.entity.MyPetType;
import de.Keyle.MyPet.api.event.MyPetCallEvent;
import de.Keyle.MyPet.api.event.MyPetLevelUpEvent;
import de.Keyle.MyPet.api.player.MyPetPlayer;
import de.Keyle.MyPet.api.player.Permissions;
import de.Keyle.MyPet.api.skill.MyPetExperience;
import de.Keyle.MyPet.api.skill.SkillInstance;
import de.Keyle.MyPet.api.skill.Skills;
import de.Keyle.MyPet.api.skill.experience.Experience;
import de.Keyle.MyPet.api.skill.skilltree.SkillTree;
import de.Keyle.MyPet.api.skill.skilltree.SkillTreeMobType;
import de.Keyle.MyPet.api.util.NBTStorage;
import de.Keyle.MyPet.api.util.NameFilter;
import de.Keyle.MyPet.api.util.Scheduler;
import de.Keyle.MyPet.api.util.Since;
import de.Keyle.MyPet.api.util.locale.Translation;
import de.Keyle.MyPet.api.util.service.types.RepositoryMyPetConverterService;
import de.Keyle.MyPet.skill.experience.Default;
import de.Keyle.MyPet.skill.experience.JavaScript;
import de.Keyle.MyPet.skill.skills.Damage;
import de.Keyle.MyPet.skill.skills.Inventory;
import de.Keyle.MyPet.skill.skills.Life;
import de.Keyle.MyPet.skill.skills.Ranged;
import de.Keyle.MyPet.util.hooks.VaultHook;
import de.keyle.knbt.*;
import org.apache.commons.lang.RandomStringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.util.*;

import static org.bukkit.Bukkit.getServer;

public abstract class MyPet implements de.Keyle.MyPet.api.entity.MyPet, NBTStorage {
    protected final MyPetPlayer petOwner;
    protected MyPetBukkitEntity bukkitEntity;
    protected String petName = "Pet";
    protected double health;
    protected int respawnTime = 0;
    protected int hungerTime = 0;
    protected double saturation = 100;
    protected UUID uuid = null;
    protected String worldGroup = "";

    @Override
    public void setExp(double exp) {
        getExperience().setExp(exp);
    }

    @Override
    public TagCompound getInfo() {
        return writeExtendedInfo();
    }

    @Override
    public void setInfo(TagCompound info) {
        readExtendedInfo(info);
    }

    @Override
    public void setOwner(MyPetPlayer owner) {
        throw new UnsupportedOperationException("You can't change the owner for an active MyPet!");
    }

    @Override
    public void setPetType(MyPetType petType) {
        throw new UnsupportedOperationException("You can't change the type for an active MyPet!");
    }

    @Override
    public void setSkills(TagCompound skills) {
    }

    protected PetState status = PetState.Despawned;
    protected boolean wantsToRespawn = false;
    protected SkillTree skillTree = null;
    protected Skills skills;
    protected MyPetExperience experience;
    protected long lastUsed = -1;

    protected MyPet(MyPetPlayer petOwner) {
        if (petOwner == null) {
            throw new IllegalArgumentException("Owner must not be null.");
        }
        this.petOwner = petOwner;
        skills = new Skills(this);

        Experience expMode = null;
        if (Configuration.LevelSystem.CALCULATION_MODE.equalsIgnoreCase("JS") || Configuration.LevelSystem.CALCULATION_MODE.equalsIgnoreCase("JavaScript")) {
            if (!new File(MyPetApi.getPlugin().getDataFolder(), "rhino.jar").exists()) {
                MyPetApi.getLogger().warning("rhino.jar is missing. Please download it here (https://github.com/mozilla/rhino/releases) and put it into the MyPet folder.");
            } else {
                expMode = new JavaScript(this);
            }
        }
        if (expMode == null || !expMode.isUsable()) {
            expMode = new Default(this);
            Configuration.LevelSystem.CALCULATION_MODE = "Default";
        }

        experience = new MyPetExperience(this, expMode);
        hungerTime = Configuration.HungerSystem.HUNGER_SYSTEM_TIME;
        petName = Translation.getString("Name." + getPetType().name(), this.petOwner);
    }

    public Optional<MyPetBukkitEntity> getEntity() {
        if (getStatus() == PetState.Here) {
            return Optional.of(bukkitEntity);
        }
        return Optional.absent();
    }

    public double getYSpawnOffset() {
        return 0;
    }

    public Optional<Location> getLocation() {
        if (status == PetState.Here) {
            return Optional.of(bukkitEntity.getLocation());
        } else if (petOwner.isOnline()) {
            return Optional.of(petOwner.getPlayer().getLocation());
        } else {
            return Optional.absent();
        }
    }

    public void setLocation(Location loc) {
        if (status == PetState.Here && MyPetApi.getPlatformHelper().canSpawn(loc, this.bukkitEntity.getHandle())) {
            bukkitEntity.teleport(loc);
        }
    }

    public double getDamage() {
        return getSkills().hasSkill(Damage.class) ? getSkills().getSkill(Damage.class).get().getDamage() : 0;
    }

    public double getRangedDamage() {
        return getSkills().hasSkill(Ranged.class) ? getSkills().getSkill(Ranged.class).get().getDamage() : 0;
    }

    public boolean isPassiv() {
        return getDamage() == 0 && getRangedDamage() == 0;
    }

    public boolean hasTarget() {
        return this.getStatus() == PetState.Here && bukkitEntity.getHandle().hasTarget();
    }

    public double getExp() {
        return getExperience().getExp();
    }

    public MyPetExperience getExperience() {
        return experience;
    }

    public TagCompound writeExtendedInfo() {
        TagCompound newTag = new TagCompound();
        newTag.put("Version", new TagInt(RepositoryMyPetConverterService.Version.valueOf(MyPetApi.getCompatUtil().getInternalVersion()).ordinal()));
        return newTag;
    }

    public void readExtendedInfo(TagCompound info) {
    }

    public double getMaxHealth() {
        return MyPetApi.getMyPetInfo().getStartHP(getPetType()) + (skills.isSkillActive(Life.class) ? skills.getSkill(Life.class).get().getHpIncrease() : 0);
    }

    public double getHealth() {
        if (status == PetState.Here) {
            return bukkitEntity.getHealth();
        } else {
            return health;
        }
    }

    public void setHealth(double d) {
        if (d > getMaxHealth()) {
            health = getMaxHealth();
        } else {
            health = d;
        }
        if (status == PetState.Here) {
            bukkitEntity.setHealth(health);
        }
    }

    @Deprecated
    @Since("24.11.2016")
    public double getHungerValue() {
        return getSaturation();
    }

    public double getSaturation() {
        if (Configuration.HungerSystem.USE_HUNGER_SYSTEM) {
            return saturation;
        } else {
            return 100;
        }
    }

    @Deprecated
    @Since("24.11.2016")
    public void setHungerValue(double value) {
        setSaturation(value);
    }

    public void setSaturation(double value) {
        if (!Double.isNaN(value) && !Double.isInfinite(value)) {
            saturation = Math.max(1, Math.min(100, value));
            hungerTime = Configuration.HungerSystem.HUNGER_SYSTEM_TIME;
        } else {
            MyPetApi.getLogger().warning("Saturation was set to an invalid number!\n" + Util.stackTraceToString());
        }
    }

    @Deprecated
    @Since("24.11.2016")
    public void decreaseHunger(double value) {
        decreaseSaturation(value);
    }

    public void decreaseSaturation(double value) {
        if (!Double.isNaN(value) && !Double.isInfinite(value)) {
            saturation = Math.max(1, Math.min(100, saturation - value));
        } else {
            MyPetApi.getLogger().warning("Saturation was decreased by an invalid number!\n" + Util.stackTraceToString());
        }
    }

    public String getPetName() {
        return this.petName;
    }

    public void setPetName(String newName) {
        if (!NameFilter.isClean(newName)) {
            newName = Translation.getString("Name." + getPetType().name(), getOwner().getLanguage());
        }
        this.petName = newName;
        if (status == PetState.Here) {
            if (Configuration.Name.Tag.SHOW) {
                getEntity().get().getHandle().updateNameTag();
            }
        }
    }

    public abstract MyPetType getPetType();

    public int getRespawnTime() {
        return respawnTime;
    }

    public void setRespawnTime(int time) {
        respawnTime = time > 0 ? time : 0;

        if (respawnTime > 0) {
            status = PetState.Dead;
        }
    }

    public boolean autoAssignSkilltree() {
        if (skillTree == null && this.petOwner.isOnline()) {
            if (Configuration.Skilltree.AUTOMATIC_SKILLTREE_ASSIGNMENT) {
                if (SkillTreeMobType.getSkillTreeNames(this.getPetType()).size() > 0) {
                    List<SkillTree> skilltrees = SkillTreeMobType.getSkillTrees(this.getPetType());
                    if (Configuration.Skilltree.RANDOM_SKILLTREE_ASSIGNMENT) {
                        Collections.shuffle(skilltrees);
                    }
                    for (SkillTree skillTree : skilltrees) {
                        if (Permissions.hasLegacy(this.petOwner.getPlayer(), "MyPet.skilltree.", skillTree.getPermission())) {
                            return setSkilltree(skillTree);
                        }
                    }
                }
            } else {
                for (SkillTree skillTree : SkillTreeMobType.getSkillTrees(this.getPetType())) {
                    if (Permissions.hasLegacy(this.petOwner.getPlayer(), "MyPet.skilltree.", skillTree.getPermission())) {
                        getOwner().sendMessage(Util.formatText(Translation.getString("Message.Skilltree.SelectionPrompt", getOwner()), getPetName()));
                        break;
                    }
                }
                return false;
            }
        }
        return true;
    }

    public SkillTree getSkilltree() {
        return skillTree;
    }

    public TagCompound getSkillInfo() {
        TagCompound skillsNBT = new TagCompound();
        Collection<SkillInstance> skillList = this.getSkills().getSkills();
        if (skillList.size() > 0) {
            for (SkillInstance skill : skillList) {
                if (skill instanceof NBTStorage) {
                    NBTStorage storageSkill = (NBTStorage) skill;
                    TagCompound s = storageSkill.save();
                    if (s != null) {
                        skillsNBT.getCompoundData().put(skill.getName(), s);
                    }
                }
            }
        }
        return skillsNBT;
    }

    public Skills getSkills() {
        return skills;
    }

    public PetState getStatus() {
        if (status == PetState.Here) {
            if (bukkitEntity == null || bukkitEntity.getHandle() == null) {
                status = PetState.Despawned;
            } else if (bukkitEntity.getHealth() <= 0 || bukkitEntity.isDead()) {
                status = PetState.Dead;
            }
        }
        return status;
    }

    public void setStatus(PetState status) {
        if (status == PetState.Here) {
            if (this.status == PetState.Dead) {
                respawnPet();
            } else if (this.status == PetState.Despawned) {
                createEntity();
            }
        } else if (status == PetState.Dead) {
            this.status = PetState.Dead;
        } else {
            if (this.status == PetState.Here) {
                removePet();
            }
        }
    }

    public UUID getUUID() {
        if (this.uuid == null) {
            this.uuid = UUID.randomUUID();
        }
        return this.uuid;
    }

    public void setUUID(UUID uuid) {
        this.uuid = uuid;
    }

    public void setLastUsed(long date) {
        this.lastUsed = date;
    }

    @Override
    public long getLastUsed() {
        return lastUsed;
    }

    @Override
    public String getWorldGroup() {
        return this.worldGroup;
    }

    public void setWorldGroup(String worldGroup) {
        if (worldGroup == null) {
            return;
        }
        if (WorldGroup.getGroupByName(worldGroup) == null) {
            worldGroup = "default";
        }
        this.worldGroup = worldGroup;
    }

    public SpawnFlags createEntity() {
        lastUsed = System.currentTimeMillis();
        if (status != PetState.Here && getOwner().isOnline()) {
            Player owner = getOwner().getPlayer();
            if (owner.isDead()) {
                status = PetState.Despawned;
                return SpawnFlags.OwnerDead;
            }
            if (owner.getGameMode().name().equals("SPECTATOR")) {
                return SpawnFlags.Spectator;
            }

            if (respawnTime <= 0) {
                Location loc = petOwner.getPlayer().getLocation();

                if (!WorldGroup.getGroupByWorld(loc.getWorld().getName()).getName().equals(getWorldGroup())) {
                    return SpawnFlags.WrongWorldGroup;
                }

                if (owner.isFlying()) {
                    boolean groundFound = false;
                    for (int i = 10; i >= 0; i--) {
                        Block b = loc.getBlock();
                        if (b.getRelative(BlockFace.DOWN).getType().isSolid()) {
                            groundFound = true;
                            break;
                        }
                        loc = loc.subtract(0, 1, 0);
                    }

                    if (!groundFound) {
                        return SpawnFlags.Flying;
                    }
                }

                MyPetCallEvent event = new MyPetCallEvent(this);
                Bukkit.getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return SpawnFlags.NotAllowed;
                }

                MyPetMinecraftEntity minecraftEntity = MyPetApi.getEntityRegistry().createMinecraftEntity(this, loc.getWorld());

                if (minecraftEntity == null) {
                    status = PetState.Despawned;
                    return SpawnFlags.Canceled;
                }
                bukkitEntity = minecraftEntity.getBukkitEntity();

                if (MyPetApi.getCompatUtil().compareWithMinecraftVersion("1.9") >= 0) {
                    Random r = new Random(petOwner.getInternalUUID().toString().hashCode());
                    String random = RandomStringUtils.random(10, 0, 0, true, true, null, r);

                    Team t;
                    if (owner.getScoreboard().getTeam("MyPet-" + random) != null) {
                        t = owner.getScoreboard().getTeam("MyPet-" + random);
                    } else {
                        t = owner.getScoreboard().registerNewTeam("MyPet-" + random);
                        t.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                    }

                    for (String entry : t.getEntries()) {
                        t.removeEntry(entry);
                    }
                    t.addEntry(minecraftEntity.getUniqueID().toString());
                }

                if (getYSpawnOffset() > 0) {
                    loc = loc.add(0, getYSpawnOffset(), 0);
                }
                loc.setPitch(0);
                loc.setYaw(0);

                Location origin = loc.clone();
                boolean positionFound = false;

                loc.subtract(1, 0, 1);
                for (double x = 0; x <= 2; x += 0.5) {
                    for (double z = 0; z <= 2; z += 0.5) {
                        if (x != 1 && z != 1) {
                            minecraftEntity.setLocation(loc);
                            if (MyPetApi.getPlatformHelper().canSpawn(loc, minecraftEntity)) {
                                Block b = loc.getBlock();
                                if (b.getRelative(BlockFace.DOWN).getType().isSolid()) {
                                    positionFound = true;
                                    break;
                                }
                            }
                        }
                        loc.add(0, 0, 0.5);
                    }
                    if (positionFound) {
                        break;
                    }
                    loc.subtract(0, 0, 2);
                    loc.add(0.5, 0, 0);
                }
                if (!positionFound) {
                    minecraftEntity.setLocation(origin);
                    if (!MyPetApi.getPlatformHelper().canSpawn(origin, minecraftEntity)) {
                        status = PetState.Despawned;
                        return SpawnFlags.NoSpace;
                    }
                }

                if (MyPetApi.getEntityRegistry().spawnMinecraftEntity(minecraftEntity, loc.getWorld())) {

                    bukkitEntity.setMetadata("MyPet", new FixedMetadataValue(MyPetApi.getPlugin(), this));
                    status = PetState.Here;

                    if (worldGroup == null || worldGroup.equals("")) {
                        setWorldGroup(WorldGroup.getGroupByWorld(loc.getWorld().getName()).getName());
                    }

                    autoAssignSkilltree();

                    wantsToRespawn = false;

                    return SpawnFlags.Success;
                }
                return SpawnFlags.Canceled;
            }
        }
        if (status == PetState.Dead) {
            return SpawnFlags.Dead;
        } else {
            return SpawnFlags.AlreadyHere;
        }
    }

    public void removePet() {
        if (status == PetState.Here) {
            health = bukkitEntity.getHealth();
            status = PetState.Despawned;
            bukkitEntity.removeEntity();
            bukkitEntity = null;

            Optional<Inventory> invSkill = getSkills().getSkill(Inventory.class);
            if (invSkill.isPresent()) {
                invSkill.get().closeInventory();
            }
        }
    }

    public void removePet(boolean wantToRespawn) {
        this.wantsToRespawn = wantToRespawn;
        removePet();
    }

    public void respawnPet() {
        if (status != PetState.Here && getOwner().isOnline()) {
            this.status = PetState.Despawned;
            respawnTime = 0;
            switch (createEntity()) {
                case Success:
                    getOwner().sendMessage(Util.formatText(Translation.getString("Message.Spawn.Respawn", petOwner), petName));
                    break;
                case Canceled:
                    getOwner().sendMessage(Util.formatText(Translation.getString("Message.Spawn.Prevent", petOwner), petName));
                    break;
                case NoSpace:
                    getOwner().sendMessage(Util.formatText(Translation.getString("Message.Spawn.NoSpace", petOwner), petName));
                    break;
                case Flying:
                    getOwner().sendMessage(Util.formatText(Translation.getString("Message.Spawn.Flying", petOwner), petName));
                    break;
            }
            if (Configuration.HungerSystem.USE_HUNGER_SYSTEM) {
                setHealth((int) Math.ceil(getMaxHealth() / 100. * (saturation + 1 - (saturation % 10))));
            } else {
                setHealth(getMaxHealth());
            }
        }
    }

    public MyPetPlayer getOwner() {
        return petOwner;
    }

    public void setWantsToRespawn(boolean wantsToRespawn) {
        this.wantsToRespawn = wantsToRespawn;
    }

    public boolean wantsToRespawn() {
        return wantsToRespawn;
    }

    public void schedule() {
        if (status != PetState.Despawned && getOwner().isOnline()) {
            for (SkillInstance skill : skills.getSkills()) {
                if (skill instanceof Scheduler) {
                    ((Scheduler) skill).schedule();
                }
            }
            if (status == PetState.Dead) {
                respawnTime--;
                if (respawnTime <= 0) {
                    respawnPet();
                } else if (MyPetApi.getPluginHookManager().isHookActive(VaultHook.class) && getOwner().hasAutoRespawnEnabled() && respawnTime >= getOwner().getAutoRespawnMin() && Permissions.has(getOwner().getPlayer(), "MyPet.user.respawn")) {
                    double cost = respawnTime * Configuration.Respawn.COSTS_FACTOR + Configuration.Respawn.COSTS_FIXED;
                    VaultHook vaultHook = MyPetApi.getPluginHookManager().getHook(VaultHook.class);
                    if (vaultHook.canPay(getOwner().getPlayer(), cost)) {
                        vaultHook.pay(getOwner().getPlayer(), cost);
                        getOwner().sendMessage(Util.formatText(Translation.getString("Message.Command.Respawn.Paid", petOwner.getLanguage()), petName, cost + " " + vaultHook.currencyNameSingular()));
                        respawnTime = 1;
                    }
                }
            }
            if (status == PetState.Here) {
                if (Configuration.HungerSystem.USE_HUNGER_SYSTEM) {
                    if (saturation > 1 && --hungerTime <= 0) {
                        saturation--;
                        hungerTime = Configuration.HungerSystem.HUNGER_SYSTEM_TIME;
                        if (saturation == 66) {
                            getOwner().sendMessage(Util.formatText(Translation.getString("Message.Hunger.Rumbling", getOwner()), getPetName()));
                        } else if (saturation == 33) {
                            getOwner().sendMessage(Util.formatText(Translation.getString("Message.Hunger.Hungry", getOwner()), getPetName()));
                        } else if (saturation == 1) {
                            getOwner().sendMessage(Util.formatText(Translation.getString("Message.Hunger.Starving", getOwner()), getPetName()));
                        }
                    }
                    if (saturation == 1 && getHealth() >= 2) {
                        getEntity().get().damage(1.);
                    }
                }
            }
        }
    }

    @Override
    public void load(TagCompound myPetNBT) {
    }

    @Override
    public TagCompound save() {
        TagCompound petNBT = new TagCompound();

        petNBT.getCompoundData().put("UUID", new TagString(getUUID().toString()));
        petNBT.getCompoundData().put("Type", new TagString(this.getPetType().name()));
        petNBT.getCompoundData().put("Health", new TagDouble(this.health));
        petNBT.getCompoundData().put("Respawntime", new TagInt(this.respawnTime));
        petNBT.getCompoundData().put("Hunger", new TagDouble(this.saturation));
        petNBT.getCompoundData().put("Name", new TagString(this.petName));
        petNBT.getCompoundData().put("WorldGroup", new TagString(this.worldGroup));
        petNBT.getCompoundData().put("Exp", new TagDouble(this.getExp()));
        petNBT.getCompoundData().put("LastUsed", new TagLong(this.lastUsed));
        petNBT.getCompoundData().put("Info", writeExtendedInfo());
        petNBT.getCompoundData().put("Internal-Owner-UUID", new TagString(this.petOwner.getInternalUUID().toString()));
        petNBT.getCompoundData().put("Wants-To-Respawn", new TagByte(wantsToRespawn));
        if (this.skillTree != null) {
            petNBT.getCompoundData().put("Skilltree", new TagString(skillTree.getName()));
        }
        TagCompound skillsNBT = new TagCompound();
        Collection<SkillInstance> skillList = this.getSkills().getSkills();
        if (skillList.size() > 0) {
            for (SkillInstance skill : skillList) {
                if (skill instanceof NBTStorage) {
                    NBTStorage storageSkill = (NBTStorage) skill;
                    TagCompound s = storageSkill.save();
                    if (s != null) {
                        skillsNBT.getCompoundData().put(skill.getName(), s);
                    }
                }
            }
        }
        petNBT.getCompoundData().put("Skills", skillsNBT);

        return petNBT;
    }

    @Override
    public String toString() {
        return "MyPet{owner=" + getOwner().getName() + ", name=" + ChatColor.stripColor(petName) + ", exp=" + experience.getExp() + "/" + experience.getRequiredExp() + ", lv=" + experience.getLevel() + ", status=" + status.name() + ", skilltree=" + skillTree.getName() + ", worldgroup=" + worldGroup + "}";
    }

    public static float[] getEntitySize(Class<? extends MyPetMinecraftEntity> entityMyPetClass) {
        EntitySize es = entityMyPetClass.getAnnotation(EntitySize.class);
        if (es != null) {
            return new float[]{es.height(), es.width()};
        }
        return new float[]{0, 0};
    }


    public boolean setSkilltree(SkillTree skillTree) {
        if (skillTree == null || this.skillTree == skillTree) {
            return false;
        }
        if (skillTree.getRequiredLevel() > 1 && getExperience().getLevel() < skillTree.getRequiredLevel()) {
            return false;
        }
        skills.reset();
        this.skillTree = skillTree;
        getServer().getPluginManager().callEvent(new MyPetLevelUpEvent(this, experience.getLevel(), 0, true));
        return true;
    }
}