package net.righthandedvoid.imperfectnpcs.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class NPCEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> SKIN_PATH = SynchedEntityData.defineId(
            NPCEntity.class,
            EntityDataSerializers.STRING
    );
    private static final EntityDataAccessor<Boolean> SLIM_ARMS = SynchedEntityData.defineId(
            NPCEntity.class,
            EntityDataSerializers.BOOLEAN
    );

    public NPCEntity(
            EntityType<? extends PathfinderMob> entityType,
            Level level
    ) {
        super(entityType, level);

        this.setPersistenceRequired();
    }

    public String getSkinPath() { return this.entityData.get(SKIN_PATH); }

    public Boolean isSlim() { return this.entityData.get(SLIM_ARMS); }

    public void setSkinPath(String s) { this.entityData.set(SKIN_PATH, s); }

    public void setSlimArms(boolean b) { this.entityData.set(SLIM_ARMS, b); }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        builder.define(SKIN_PATH, "");
        builder.define(SLIM_ARMS, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);

        compound.putString("SkinPath", this.getSkinPath());
        compound.putBoolean("SlimArms", this.isSlim());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        if (compound.contains("SkinPath")) {
            this.setSkinPath(compound.getString("SkinPath"));
        }

        if (compound.contains("SlimArms")) {
            this.setSlimArms(compound.getBoolean("SlimArms"));
        }
    }
}
