package com.bobmowzie.mowziesmobs.server.entity.barakoa;

import com.bobmowzie.mowziesmobs.client.gui.GuiBarakoayaTrade;
import com.bobmowzie.mowziesmobs.server.ServerProxy;
import com.bobmowzie.mowziesmobs.server.ai.BarakoaAttackTargetAI;
import com.bobmowzie.mowziesmobs.server.ai.BarakoaHurtByTargetAI;
import com.bobmowzie.mowziesmobs.server.ai.EntityAIBarakoayaTrade;
import com.bobmowzie.mowziesmobs.server.ai.EntityAIBarakoayaTradeLook;
import com.bobmowzie.mowziesmobs.server.block.BlockHandler;
import com.bobmowzie.mowziesmobs.server.entity.LeaderSunstrikeImmune;
import com.bobmowzie.mowziesmobs.server.entity.barakoa.trade.Trade;
import com.bobmowzie.mowziesmobs.server.entity.barakoa.trade.TradeStore;
import com.bobmowzie.mowziesmobs.server.gui.GuiHandler;
import com.bobmowzie.mowziesmobs.server.gui.GuiHandler.ContainerHolder;
import com.bobmowzie.mowziesmobs.server.inventory.ContainerBarakoayaTrade;
import com.bobmowzie.mowziesmobs.server.item.BarakoaMask;
import com.bobmowzie.mowziesmobs.server.item.ItemHandler;
import com.google.common.base.Optional;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.monster.SkeletonEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.*;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.Hand;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class EntityBarakoaya extends EntityBarakoa implements ContainerHolder, LeaderSunstrikeImmune {
    private static final TradeStore DEFAULT = new TradeStore.Builder()
        .addTrade(Items.GOLD_INGOT, 2, ItemHandler.BLOWGUN, 1, 6)
        .addTrade(Items.DYE, 10, DyeColor.BROWN.getDyeDamage(), ItemHandler.DART, 8, 0, 6)
        .addTrade(Items.GOLD_INGOT, 3, ItemHandler.SPEAR, 1, 4)
        .addTrade(Items.GOLD_NUGGET, 5, Item.getItemFromBlock(BlockHandler.PAINTED_ACACIA), 2, 4)
        .addTrade(Items.DYE, 16, DyeColor.BROWN.getDyeDamage(), Item.getItemFromBlock(BlockHandler.PAINTED_ACACIA), 1, 0, 4)
        .addTrade(Items.DYE, 10, DyeColor.BROWN.getDyeDamage(), Items.COOKED_CHICKEN, 2, 0, 2)
        .addTrade(Items.GOLD_NUGGET, 8, Items.COOKED_CHICKEN, 1, 2)
        .addTrade(Items.DYE, 14, DyeColor.BROWN.getDyeDamage(), Items.COOKED_PORKCHOP, 2, 0, 2)
        .addTrade(Items.GOLD_NUGGET, 9, Items.COOKED_PORKCHOP, 1, 2)

        .addTrade(Items.MELON, 3, Items.GOLD_NUGGET, 5, 2)
        .addTrade(Items.CHICKEN, 1, Items.GOLD_NUGGET, 3, 2)
        .addTrade(Items.IRON_SWORD, 1, Items.GOLD_INGOT, 2, 2)
        .addTrade(Items.IRON_HELMET, 1, Items.GOLD_INGOT, 4, 2)
        .build();

    private static final DataParameter<Optional<Trade>> TRADE = EntityDataManager.createKey(EntityBarakoaya.class, ServerProxy.OPTIONAL_TRADE);
//    private static final DataParameter<Integer> NUM_SALES = EntityDataManager.createKey(EntityBarakoaya.class, DataSerializers.VARINT);
    //TODO: Sale limits. After X sales, go out of stock and change trade.

    private static final int MIN_OFFER_TIME = 5 * 60 * 20;

    private static final int MAX_OFFER_TIME = 20 * 60 * 20;

    private TradeStore tradeStore = TradeStore.EMPTY;

    private int timeOffering;

//    private static final int SOLD_OUT_TIME = 5 * 60 * 20;
//    private static final int MAX_SALES = 5;

    private PlayerEntity customer;

    public EntityBarakoaya(EntityType<? extends EntityBarakoaya> type, World world) {
        super(type, world);
        tasks.addTask(1, new EntityAIBarakoayaTrade(this));
        tasks.addTask(1, new EntityAIBarakoayaTradeLook(this));
        this.targetTasks.addTask(3, new BarakoaHurtByTargetAI(this, true));
        targetTasks.addTask(3, new BarakoaAttackTargetAI(this, PlayerEntity.class, 0, true, false));
        targetTasks.addTask(5, new NearestAttackableTargetGoal<>(this, ZombieEntity.class, 0, true, true, null));
        this.targetTasks.addTask(5, new NearestAttackableTargetGoal<>(this, SkeletonEntity.class, 0, true, false, null));
        setWeapon(0);
//        setNumSales(MAX_SALES);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        getDataManager().register(TRADE, Optional.absent());
//        getDataManager().register(NUM_SALES, MAX_SALES);
    }

    public void setOfferingTrade(Trade trade) {
        getDataManager().set(TRADE, Optional.fromNullable(trade));
    }

    public Trade getOfferingTrade() {
        return getDataManager().get(TRADE).orNull();
    }

//    public int getNumSales() {
//        return getDataManager().get(NUM_SALES);
//    }
//
//    public void setNumSales(int numSales) {
//        getDataManager().set(NUM_SALES, numSales);
//    }

    public boolean isOfferingTrade() {
        if (getDataManager().get(TRADE) instanceof Optional) {
            return getDataManager().get(TRADE).isPresent();
        }
        else return false;
    }

    public void setCustomer(PlayerEntity customer) {
        this.customer = customer;
    }

    public PlayerEntity getCustomer() {
        return customer;
    }

    public boolean isTrading() {
        return customer != null;
    }

    protected boolean canHoldVaryingWeapons() {
        return false;
    }

    @Override
    public Container createContainer(World world, PlayerEntity player, int x, int y, int z) {
        return new ContainerBarakoayaTrade(this, player.inventory, world);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public ContainerScreen createGui(World world, PlayerEntity player, int x, int y, int z) {
        return new GuiBarakoayaTrade(this, player.inventory, world);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if ((!isOfferingTrade() || timeOffering <= 0) && tradeStore.hasStock()) {
            setOfferingTrade(tradeStore.get(rand));
            timeOffering = rand.nextInt(MAX_OFFER_TIME - MIN_OFFER_TIME + 1) + MIN_OFFER_TIME;
        }
    }

    @Override
    protected boolean processInteract(PlayerEntity player, Hand hand) {
        if (canTradeWith(player) && getAttackTarget() == null && !isDead) {
            setCustomer(player);
            if (!world.isRemote) {
                GuiHandler.open(GuiHandler.BARAKOA_TRADE, player, this);
            }
            return true;
        }
        return false;
    }

    public boolean canTradeWith(PlayerEntity player) {
        if (isTrading()) {
            return false;
        }
        ItemStack headStack = player.inventory.armorInventory.get(3);
        return headStack.getItem() instanceof BarakoaMask && isOfferingTrade();
    }

    @Override
    public ILivingEntityData onInitialSpawn(DifficultyInstance difficulty, ILivingEntityData data) {
        tradeStore = DEFAULT;
        return super.onInitialSpawn(difficulty, data);
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public void writeEntityToNBT(CompoundNBT compound) {
        super.writeEntityToNBT(compound);
        compound.setTag("tradeStore", tradeStore.serialize());
        if (isOfferingTrade()) {
            compound.setTag("offeringTrade", getOfferingTrade().serialize());
        }
        compound.setInteger("timeOffering", timeOffering);
//        compound.setInteger("numSales", getNumSales());
    }

    @Override
    public void readEntityFromNBT(CompoundNBT compound) {
        super.readEntityFromNBT(compound);
        tradeStore = TradeStore.deserialize(compound.getCompoundTag("tradeStore"));
        setOfferingTrade(Trade.deserialize(compound.getCompoundTag("offeringTrade")));
        timeOffering = compound.getInteger("timeOffering");
//        setNumSales(compound.getInteger("numSales"));
    }
}
