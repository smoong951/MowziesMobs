package com.bobmowzie.mowziesmobs.server.entity.barakoa;

import com.bobmowzie.mowziesmobs.server.ai.NearestAttackableTargetPredicateGoal;
import com.bobmowzie.mowziesmobs.server.ai.AvoidProjectilesGoal;
import com.bobmowzie.mowziesmobs.server.item.BarakoaMask;
import com.bobmowzie.mowziesmobs.server.potion.EffectHandler;
import com.ilexiconn.llibrary.server.animation.AnimationHandler;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal.Flag;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

public class EntityBarakoaya extends EntityBarakoaVillager {
    public boolean hasTriedOrSucceededTeleport = true;
    private int teleportAttempts = 0;

    public EntityBarakoaya(EntityType<? extends EntityBarakoaVillager> type, Level world) {
        super(type, world);
        setWeapon(3);
        setMask(MaskType.FAITH);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(4, new HealTargetGoal(this));
        this.goalSelector.addGoal(6, new AvoidEntityGoal<Player>(this, Player.class, 50.0F, 0.8D, 0.6D, target -> {
            if (target instanceof Player) {
                if (this.level.getDifficulty() == Difficulty.PEACEFUL) return false;
                if (getTarget() == target) return true;
                if (getTarget() instanceof EntityBarako) return false;
                if (getAnimation() != NO_ANIMATION) return false;
                ItemStack headArmorStack = ((Player) target).getInventory().armor.get(3);
                return !(headArmorStack.getItem() instanceof BarakoaMask) || target == getMisbehavedPlayer();
            }
            return true;
        }){
            @Override
            public void stop() {
                super.stop();
                setMisbehavedPlayerId(null);
            }
        });
        this.goalSelector.addGoal(1, new TeleportToSafeSpotGoal(this));
        this.goalSelector.addGoal(1, new AvoidProjectilesGoal(this, Projectile.class, target -> {
            return getAnimation() == HEAL_LOOP_ANIMATION || getAnimation() == HEAL_START_ANIMATION;
        }, 3.0F, 0.8D, 0.6D));
    }

    @Override
    protected void registerTargetGoals() {
        super.registerTargetGoals();
        this.targetSelector.addGoal(2, new NearestAttackableTargetPredicateGoal<EntityBarako>(this, EntityBarako.class, 0, false, false, TargetingConditions.forNonCombat().range(getAttributeValue(Attributes.FOLLOW_RANGE) * 2).selector(target -> {
            if (!active) return false;
            if (target instanceof Mob) {
                return ((Mob) target).getTarget() != null || target.getHealth() < target.getMaxHealth();
            }
            return false;
        }).ignoreLineOfSight().ignoreInvisibilityTesting()) {
            @Override
            public boolean canContinueToUse() {
                LivingEntity livingentity = this.mob.getTarget();
                if (livingentity == null) {
                    livingentity = this.targetMob;
                }
                boolean targetHasTarget = false;
                if (livingentity instanceof Mob) targetHasTarget = ((Mob)livingentity).getTarget() != null;
                boolean canHeal = true;
                if (this.mob instanceof EntityBarakoa) canHeal = ((EntityBarakoa)this.mob).canHeal(livingentity);
                return super.canContinueToUse() && (livingentity.getHealth() < livingentity.getMaxHealth() || targetHasTarget) && canHeal;
            }

            @Override
            protected double getFollowDistance() {
                return super.getFollowDistance() * 2;
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (active && teleportAttempts > 3 && (getTarget() == null || !getTarget().isAlive())) hasTriedOrSucceededTeleport = true;
        if (getAnimation() == HEAL_LOOP_ANIMATION && !canHeal(getTarget())) AnimationHandler.INSTANCE.sendAnimationMessage(this, HEAL_STOP_ANIMATION);

//        if (getAnimation() == NO_ANIMATION) AnimationHandler.INSTANCE.sendAnimationMessage(this, HEAL_START_ANIMATION);
    }

    @Override
    protected void updateAttackAI() {

    }

    @Override
    public boolean canHeal(LivingEntity entity) {
        return entity instanceof EntityBarako;
    }

    public class TeleportToSafeSpotGoal extends Goal {
        private final EntityBarakoaya entity;

        public TeleportToSafeSpotGoal(EntityBarakoaya entityIn) {
            this.entity = entityIn;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!entity.active) return false;
            if (entity.getAnimation() == TELEPORT_ANIMATION) return false;
            if (entity.getTarget() != null && entity.canHeal(entity.getTarget()) && (
                    (entity.targetDistance >= 0 && entity.targetDistance < 7) || !hasTriedOrSucceededTeleport
            )) {
                return findTeleportLocation();
            }
            return false;
        }

        @Override
        public void start() {
            super.start();
            hasTriedOrSucceededTeleport = true;
            AnimationHandler.INSTANCE.sendAnimationMessage(entity, TELEPORT_ANIMATION);
        }

        private boolean findTeleportLocation() {
            int i;
            int j;
            int k;
            if (entity.getRestrictRadius() > -1) {
                i = Mth.floor(entity.getRestrictCenter().getX());
                j = Mth.floor(entity.getRestrictCenter().getY());
                k = Mth.floor(entity.getRestrictCenter().getZ());
            }
            else if (entity.getTarget() != null) {
                i = Mth.floor(entity.getTarget().getX());
                j = Mth.floor(entity.getTarget().getY());
                k = Mth.floor(entity.getTarget().getZ());
            }
            else {
                i = Mth.floor(entity.getX());
                j = Mth.floor(entity.getY());
                k = Mth.floor(entity.getZ());
            }
            boolean foundPosition = false;
            for(int l = 0; l < 50; ++l) {
                double radius = Math.pow(random.nextFloat(), 1.35) * 25;
                double angle = random.nextFloat() * Math.PI * 2;
                int i1 = i + (int)(Math.cos(angle) * radius);
                int j1 = j + Mth.nextInt(entity.random, 0, 15) * Mth.nextInt(entity.random, -1, 1);
                int k1 = k + (int)(Math.sin(angle) * radius);
                BlockPos blockpos = new BlockPos(i1, j1, k1);
                Vec3 newPos = new Vec3(i1, j1, k1);
                Vec3 offset = newPos.subtract(entity.position());
                AABB newBB = entity.getBoundingBox().move(offset);
                if (testBlock(blockpos, newBB) && entity.level.getEntitiesOfClass(EntityBarako.class, newBB.inflate(7)).isEmpty()) {
                    entity.teleportDestination = newPos.add(0, 0, 0);
                    if (entity.teleportAttempts >= 3) foundPosition = true;
                    if (entity.level.getEntitiesOfClass(EntityBarakoaya.class, newBB.inflate(5)).isEmpty()) {
                        if (entity.teleportAttempts >= 2) foundPosition = true;
                        if (!entity.level.hasNearbyAlivePlayer(i1, j1, k1, 5) && !entity.level.containsAnyLiquid(newBB)) {
                            if (entity.teleportAttempts >= 1) foundPosition = true;
                            LivingEntity target = getTarget();
                            if (target instanceof Mob && ((Mob) target).getTarget() != null) {
                                if (!canEntityBeSeenFromLocation(((Mob) target).getTarget(), newPos)) {
                                    return true;
                                }
                            } else return true;
                        }
                    }
                }
            }
            entity.teleportAttempts++;
            if (entity.teleportAttempts > 3) hasTriedOrSucceededTeleport = true;
            return foundPosition;
        }

        public boolean canEntityBeSeenFromLocation(Entity entityIn, Vec3 location) {
            Vec3 vector3d = new Vec3(location.x(), location.y() + entity.getEyeHeight(), location.z());
            Vec3 vector3d1 = new Vec3(entityIn.getX(), entityIn.getEyeY(), entityIn.getZ());
            return entity.level.clip(new ClipContext(vector3d, vector3d1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() != HitResult.Type.BLOCK;
        }

        public boolean testBlock(BlockPos blockpos, AABB aabb) {
            Level world = entity.level;
            if (world.hasChunkAt(blockpos)) {
                BlockPos blockpos1 = blockpos.below();
                BlockState blockstate = world.getBlockState(blockpos1);
                return blockstate.getMaterial().isSolid() && blockstate.getMaterial().blocksMotion() && world.noCollision(aabb);
            }
            return false;
        }
    }

    public static class HealTargetGoal extends Goal {
        private final EntityBarakoa entity;

        public HealTargetGoal(EntityBarakoa entityIn) {
            this.entity = entityIn;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canContinueToUse() {
            return entity.canHeal(entity.getTarget());
        }

        @Override
        public boolean canUse() {
            if (!entity.active) return false;
            return entity.canHeal(entity.getTarget());
        }

        @Override
        public void start() {
            super.start();
            AnimationHandler.INSTANCE.sendAnimationMessage(entity, EntityBarakoa.HEAL_START_ANIMATION);
        }

        @Override
        public void stop() {
            super.stop();
//            if (entity.getAnimation() == HEAL_LOOP_ANIMATION || entity.getAnimation() == HEAL_START_ANIMATION) AnimationHandler.INSTANCE.sendAnimationMessage(entity, EntityBarakoa.HEAL_STOP_ANIMATION);
        }
    }

    @Override
    protected void sunBlockTarget() {
        LivingEntity target = getTarget();
        if (target != null) {
            EffectHandler.addOrCombineEffect(target, EffectHandler.SUNBLOCK, 20, 0, true, false);
            if (target.tickCount % 20 == 0) target.heal(0.15f);
        }
    }
    
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        boolean teleporting = getAnimation() == TELEPORT_ANIMATION && getAnimationTick() <= 16;
        return super.isInvulnerableTo(source) || ((!active || teleporting || !hasTriedOrSucceededTeleport) && source != DamageSource.OUT_OF_WORLD && timeUntilDeath != 0);
    }

    @Override
    public void setSecondsOnFire(int seconds) {
        boolean teleporting = getAnimation() == TELEPORT_ANIMATION && getAnimationTick() <= 16;
        if (!active || teleporting || !hasTriedOrSucceededTeleport) return;
        super.setSecondsOnFire(seconds);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData livingData, CompoundTag compound) {
        setMask(MaskType.FAITH);
        setWeapon(3);
        return super.finalizeSpawn(world, difficulty, reason, livingData, compound);
    }
}
