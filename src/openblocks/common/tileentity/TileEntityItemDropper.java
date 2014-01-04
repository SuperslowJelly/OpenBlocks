package openblocks.common.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.ForgeDirection;
import openblocks.client.gui.GuiItemDropper;
import openblocks.common.container.ContainerItemDropper;
import openmods.GenericInventory;
import openmods.api.IHasGui;
import openmods.api.INeighbourAwareTile;
import openmods.tileentity.OpenTileEntity;
import openmods.utils.InventoryUtils;
import openmods.utils.OpenModsFakePlayer;

public class TileEntityItemDropper extends OpenTileEntity implements INeighbourAwareTile, IInventory, IHasGui {
	static final int BUFFER_SIZE = 9;

	private boolean _redstoneSignal;

	public TileEntityItemDropper() {
		setInventory(new GenericInventory("itemDropper", false, 9));
	}

	public void setRedstoneSignal(boolean redstoneSignal) {
		if (redstoneSignal != _redstoneSignal) {
			_redstoneSignal = redstoneSignal;
			if (_redstoneSignal && !InventoryUtils.inventoryIsEmpty(inventory)) {
				dropItem();
			}
		}
	}

	private void dropItem() {
		if (worldObj.isRemote) return;

		for (int i = 0, l = getSizeInventory(); i < l; i++) {
			ItemStack stack = getStackInSlot(i);
			if (stack == null || stack.stackSize == 0) continue;

			ItemStack dropped = stack.splitStack(1);
			if (stack.stackSize <= 0) {
				setInventorySlotContents(i, null);
			}

			OpenModsFakePlayer.getPlayerForWorld(worldObj).dropItemAt(dropped, xCoord, yCoord, zCoord, ForgeDirection.DOWN);

			return;
		}
	}

	@Override
	public void onNeighbourChanged(int blockId) {
		if (!worldObj.isRemote) {
			setRedstoneSignal(worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord));
		}
	}

	@Override
	public Object getServerGui(EntityPlayer player) {
		return new ContainerItemDropper(player.inventory, this);
	}

	@Override
	public Object getClientGui(EntityPlayer player) {
		return new GuiItemDropper(new ContainerItemDropper(player.inventory, this));
	}

	@Override
	public boolean canOpenGui(EntityPlayer player) {
		return true;
	}

	@Override
	public int getSizeInventory() {
		return inventory.getSizeInventory();
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		return inventory.getStackInSlot(i);
	}

	@Override
	public ItemStack decrStackSize(int i, int j) {
		return inventory.decrStackSize(i, j);
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int i) {
		return inventory.getStackInSlotOnClosing(i);
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemstack) {
		inventory.setInventorySlotContents(i, itemstack);
	}

	@Override
	public String getInvName() {
		return inventory.getInvName();
	}

	@Override
	public boolean isInvNameLocalized() {
		return inventory.isInvNameLocalized();
	}

	@Override
	public int getInventoryStackLimit() {
		return inventory.getInventoryStackLimit();
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer entityplayer) {
		return inventory.isUseableByPlayer(entityplayer);
	}

	@Override
	public void openChest() {}

	@Override
	public void closeChest() {}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack) {
		return true;
	}
}
