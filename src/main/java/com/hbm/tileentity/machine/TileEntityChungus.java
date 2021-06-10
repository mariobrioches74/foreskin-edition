package com.hbm.tileentity.machine;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.handler.FluidTypeHandler.FluidType;
import com.hbm.interfaces.IConsumer;
import com.hbm.interfaces.IFluidAcceptor;
import com.hbm.interfaces.IFluidSource;
import com.hbm.interfaces.ISource;
import com.hbm.interfaces.Untested;
import com.hbm.inventory.FluidTank;
import com.hbm.inventory.MachineRecipes;
import com.hbm.lib.Library;
import com.hbm.packet.NBTPacket;
import com.hbm.packet.PacketDispatcher;
import com.hbm.tileentity.INBTPacketReceiver;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityChungus extends TileEntity implements IFluidAcceptor, IFluidSource, ISource, INBTPacketReceiver {

	public long power;
	public static final long maxPower = 100000000000L;
	private boolean shouldTurn;
	public float rotor;
	public float lastRotor;
	
	public List<IConsumer> list1 = new ArrayList();
	public List<IFluidAcceptor> list2 = new ArrayList();
	
	public FluidTank[] tanks;
	
	public TileEntityChungus() {
		
		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(FluidType.STEAM, 1000000000, 0);
		tanks[1] = new FluidTank(FluidType.WATER, 1000000000, 1);
	}

	@Untested
	@Override
	public void updateEntity() {
		
		if(!worldObj.isRemote) {
			
			Object[] outs = MachineRecipes.getTurbineOutput(tanks[0].getTankType());
			
			tanks[1].setTankType((FluidType) outs[0]);
			
			int processMax = (int) Math.ceil(tanks[0].getFill() / (Integer)outs[2]);				//the maximum amount of cycles total
			int processSteam = tanks[0].getFill() / (Integer)outs[2];								//the maximum amount of cycles depending on steam
			int processWater = (tanks[1].getMaxFill() - tanks[1].getFill()) / (Integer)outs[1];		//the maximum amount of cycles depending on water
			
			int cycles = Math.min(processMax, Math.min(processSteam, processWater));
			
			tanks[0].setFill(tanks[0].getFill() - (Integer)outs[2] * cycles);
			tanks[1].setFill(tanks[1].getFill() + (Integer)outs[1] * cycles);
			
			power += (Integer)outs[3] * cycles;
			
			if(power > maxPower)
				power = maxPower;
			
			shouldTurn = cycles > 0;
			
			NBTTagCompound data = new NBTTagCompound();
			data.setLong("power", power);
			data.setBoolean("operational", shouldTurn);
			this.networkPack(data, 150);
			
		} else {
			
			this.lastRotor = this.rotor;
			
			if(shouldTurn) {
				
				this.rotor += 30F;
				
				if(this.rotor >= 360) {
					this.rotor -= 360;
					this.lastRotor -= 360;
				}
			}
		}
	}
	
	public void networkPack(NBTTagCompound nbt, int range) {
		PacketDispatcher.wrapper.sendToAllAround(new NBTPacket(nbt, xCoord, yCoord, zCoord), new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, range));
	}

	@Override
	public void networkUnpack(NBTTagCompound data) {
		this.power = data.getLong("power");
		this.shouldTurn = data.getBoolean("operational");
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		tanks[0].readFromNBT(nbt, "water");
		tanks[1].readFromNBT(nbt, "steam");
		power = nbt.getLong("power");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		tanks[0].writeToNBT(nbt, "water");
		tanks[1].writeToNBT(nbt, "steam");
		nbt.setLong("power", power);
	}

	@Override
	public void ffgeua(int x, int y, int z, boolean newTact) {
		Library.ffgeua(x, y, z, newTact, this, worldObj);
	}

	@Override
	public void ffgeuaInit() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ffgeua(xCoord + dir.offsetX * -4, yCoord, zCoord + dir.offsetZ * -4, getTact());
	}

	@Override
	public void fillFluidInit(FluidType type) {
		
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		dir = dir.getRotation(ForgeDirection.UP);

		fillFluid(xCoord + dir.offsetX * 2, yCoord, zCoord + dir.offsetZ * 2, getTact(), type);
		fillFluid(xCoord + dir.offsetX * -2, yCoord, zCoord + dir.offsetZ * -2, getTact(), type);
	}

	@Override
	public void fillFluid(int x, int y, int z, boolean newTact, FluidType type) {
		Library.transmitFluid(x, y, z, newTact, this, worldObj, type);
	}
	
	@Override
	public boolean getTact() {
		return worldObj.getTotalWorldTime() % 2 == 0;
	}

	@Override
	public void setFluidFill(int i, FluidType type) {
		if(type.name().equals(tanks[0].getTankType().name()))
			tanks[0].setFill(i);
		else if(type.name().equals(tanks[1].getTankType().name()))
			tanks[1].setFill(i);
	}

	@Override
	public int getFluidFill(FluidType type) {
		if(type.name().equals(tanks[0].getTankType().name()))
			return tanks[0].getFill();
		else if(type.name().equals(tanks[1].getTankType().name()))
			return tanks[1].getFill();
		
		return 0;
	}

	@Override
	public int getMaxFluidFill(FluidType type) {
		if(type.name().equals(tanks[0].getTankType().name()))
			return tanks[0].getMaxFill();
		
		return 0;
	}

	@Override
	public void setFillstate(int fill, int index) {
		if(index < 2 && tanks[index] != null)
			tanks[index].setFill(fill);
	}

	@Override
	public void setType(FluidType type, int index) {
		if(index < 2 && tanks[index] != null)
			tanks[index].setTankType(type);
	}

	@Override
	public List<FluidTank> getTanks() {
		List<FluidTank> list = new ArrayList();
		list.add(tanks[0]);
		list.add(tanks[1]);
		
		return list;
	}
	
	@Override
	public List<IFluidAcceptor> getFluidList(FluidType type) {
		return list2;
	}
	
	@Override
	public void clearFluidList(FluidType type) {
		list2.clear();
	}

	@Override
	public long getSPower() {
		return power;
	}

	@Override
	public void setSPower(long i) {
		this.power = i;
	}

	@Override
	public List<IConsumer> getList() {
		return list1;
	}

	@Override
	public void clearList() {
		this.list1.clear();
	}
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
