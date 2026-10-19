package net.righthandedvoid.imperfectnpcs.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.righthandedvoid.imperfectnpcs.scripting.NPCEvents;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class NPCEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> SKIN_PATH = SynchedEntityData.defineId(
            NPCEntity.class,
            EntityDataSerializers.STRING
    );
    private static final EntityDataAccessor<Boolean> HAS_WIDE_ARMS = SynchedEntityData.defineId(
            NPCEntity.class,
            EntityDataSerializers.BOOLEAN
    );
    // Synced so the client knows whether right-clicking should count as an interaction.
    private static final EntityDataAccessor<String> SCRIPT_NAME = SynchedEntityData.defineId(
            NPCEntity.class,
            EntityDataSerializers.STRING
    );

    private static final String SkinPath = "SkinPath";
    private static final String HasWideArms = "HasWideArms";
    private static final String ScriptName = "Script";

    public NPCEntity(
            EntityType<? extends PathfinderMob> entityType,
            Level level
    ) {
        super(entityType, level);

        this.setPersistenceRequired();
    }

    public String getSkinPath() { return this.entityData.get(SKIN_PATH); }

    public String getNpcName() { return Objects.requireNonNull(this.getCustomName()).getString(); }

    public boolean getHasWideArms() { return this.entityData.get(HAS_WIDE_ARMS); }

    public void setSkinPath(String s) { this.entityData.set(SKIN_PATH, s); }

    public void setNpcName(String s) { this.setCustomName(Component.literal(s)); }

    public void setHasWideArms(boolean bool) { this.entityData.set(HAS_WIDE_ARMS, bool); }

    /** The script attached to this NPC, or "" for none. */
    public String getScriptName() { return this.entityData.get(SCRIPT_NAME); }

    /** Attaches a script (or clears it with ""). The change takes effect on the NPC's next tick. */
    public void setScriptName(String s) { this.entityData.set(SCRIPT_NAME, s); }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);

        builder.define(SKIN_PATH, "");
        builder.define(HAS_WIDE_ARMS, true);
        builder.define(SCRIPT_NAME, "");
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);

        compound.putString(SkinPath, this.getSkinPath());
        compound.putBoolean(HasWideArms, this.getHasWideArms());
        compound.putString(ScriptName, this.getScriptName());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        if (compound.contains(SkinPath)) {
            this.setSkinPath(compound.getString(SkinPath));
        }

        if (compound.contains(HasWideArms)) {
            this.setHasWideArms(compound.getBoolean(HasWideArms));
        }

        if (compound.contains(ScriptName)) {
            this.setScriptName(compound.getString(ScriptName));
        }
    }

    // region Script events (server side only)

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) NPCEvents.onTick(this);
    }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        // Without a script, behave like any other mob (name tags, leads, ...).
        // Right-clicking fires for both hands, so only count the main hand.
        if (this.getScriptName().isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return super.mobInteract(player, hand);
        }

        if (!this.level().isClientSide) NPCEvents.onInteract(this, player);

        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        boolean wasHurt = super.hurt(source, amount);

        // A lethal hit fires `died` (from die(), inside super.hurt) instead of `damaged`.
        if (wasHurt && this.isAlive() && !this.level().isClientSide) {
            NPCEvents.onDamaged(this, source, amount);
        }

        return wasHurt;
    }

    @Override
    public void die(@NotNull DamageSource source) {
        super.die(source);

        if (!this.level().isClientSide) NPCEvents.onDied(this, source);
    }

    // endregion
}