package com.teammoeg.steampowered.tileentity;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.base.KineticTileEntity;
import com.simibubi.create.foundation.utility.Lang;
import com.teammoeg.steampowered.SPConfig;
import com.teammoeg.steampowered.SteamPowered;
import com.teammoeg.steampowered.block.AlternatorBlock;
import com.teammoeg.steampowered.item.Multimeter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.List;

/**
 * Adapted from: Create: Crafts & Additions
 */
public class AlternatorTileEntity extends KineticTileEntity {

    protected final InternalEnergyStorage energy;
    private LazyOptional<IEnergyStorage> lazyEnergy;

    private static final int
            MAX_FE_IN = SPConfig.COMMON.alternatorFeMaxIn.get(),
            MAX_FE_OUT = SPConfig.COMMON.alternatorFeMaxOut.get(), // FE Output
            FE_CAPACITY = SPConfig.COMMON.alternatorFeCapacity.get(), // FE Storage
            IMPACT = SPConfig.COMMON.alternatorImpact.get(); // Impact on network
    private static final double
            EFFICIENCY = SPConfig.COMMON.alternatorEfficiency.get();

    public AlternatorTileEntity(TileEntityType<?> typeIn) {
        super(typeIn);
        energy = new InternalEnergyStorage(FE_CAPACITY, MAX_FE_IN, MAX_FE_OUT);
        lazyEnergy = LazyOptional.of(() -> energy);
    }

    @Override
    public boolean addToGoggleTooltip(List<ITextComponent> tooltip, boolean isPlayerSneaking) {
        tooltip.add(new StringTextComponent(spacing).append(new TranslationTextComponent(SteamPowered.MODID + ".tooltip.energy.production").withStyle(TextFormatting.GRAY)));
        tooltip.add(new StringTextComponent(spacing).append(new StringTextComponent(" " + Multimeter.format(getEnergyProductionRate((int) (isSpeedRequirementFulfilled() ? getSpeed() : 0))) + "fe/t ") // fix
                .withStyle(TextFormatting.AQUA)).append(Lang.translate("gui.goggles.at_current_speed").withStyle(TextFormatting.DARK_GRAY)));
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    @Override
    public float calculateStressApplied() {
        this.lastStressApplied = IMPACT;
        return IMPACT;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == CapabilityEnergy.ENERGY && (isEnergyInput(side) || isEnergyOutput(side)))// && !level.isClientSide
            return lazyEnergy.cast();
        return super.getCapability(cap, side);
    }

    public boolean isEnergyInput(Direction side) {
        return false;
    }

    public boolean isEnergyOutput(Direction side) {
        return side != getBlockState().getValue(AlternatorBlock.FACING);
    }

    @Override
    public void fromTag(BlockState state, CompoundNBT compound, boolean clientPacket) {
        super.fromTag(state, compound, clientPacket);
        energy.read(compound);
    }

    @Override
    public void write(CompoundNBT compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        energy.write(compound);
    }

    private boolean firstTickState = true;

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide())
            return;
        if (firstTickState)
            firstTick();
        firstTickState = false;

        if (Math.abs(getSpeed()) > 0 && isSpeedRequirementFulfilled())
            energy.internalProduceEnergy(getEnergyProductionRate((int) getSpeed()));

        for (Direction d : Direction.values()) {
            if (!isEnergyOutput(d))
                continue;
            IEnergyStorage ies = getCachedEnergy(d);
            if (ies == null)
                continue;
            int ext = energy.extractEnergy(ies.receiveEnergy(MAX_FE_OUT, true), false);
            int rec = ies.receiveEnergy(ext, false);
        }
    }

    public static int getEnergyProductionRate(int rpm) {
        rpm = Math.abs(rpm);
        return (int) (Math.abs(rpm) * EFFICIENCY);
    }

    @Override
    protected Block getStressConfigKey() {
        return AllBlocks.MECHANICAL_MIXER.get();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        lazyEnergy.invalidate();
    }

    public void firstTick() {
        updateCache();
    }

    ;

    public void updateCache() {
        if (level.isClientSide())
            return;
        for (Direction side : Direction.values()) {
            TileEntity te = level.getBlockEntity(worldPosition.relative(side));
            if (te == null) {
                setCache(side, LazyOptional.empty());
                continue;
            }
            LazyOptional<IEnergyStorage> le = te.getCapability(CapabilityEnergy.ENERGY, side.getOpposite());
            setCache(side, le);
        }
    }

    private LazyOptional<IEnergyStorage> escacheUp = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> escacheDown = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> escacheNorth = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> escacheEast = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> escacheSouth = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> escacheWest = LazyOptional.empty();

    public void setCache(Direction side, LazyOptional<IEnergyStorage> storage) {
        switch (side) {
            case DOWN:
                escacheDown = storage;
                break;
            case EAST:
                escacheEast = storage;
                break;
            case NORTH:
                escacheNorth = storage;
                break;
            case SOUTH:
                escacheSouth = storage;
                break;
            case UP:
                escacheUp = storage;
                break;
            case WEST:
                escacheWest = storage;
                break;
        }
    }

    public IEnergyStorage getCachedEnergy(Direction side) {
        switch (side) {
            case DOWN:
                return escacheDown.orElse(null);
            case EAST:
                return escacheEast.orElse(null);
            case NORTH:
                return escacheNorth.orElse(null);
            case SOUTH:
                return escacheSouth.orElse(null);
            case UP:
                return escacheUp.orElse(null);
            case WEST:
                return escacheWest.orElse(null);
        }
        return null;
    }

    @Override
    public World getWorld() {
        return getLevel();
    }
}
