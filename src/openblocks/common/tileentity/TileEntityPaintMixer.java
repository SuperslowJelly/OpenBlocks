package openblocks.common.tileentity;

import java.util.EnumMap;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import openblocks.OpenBlocks;
import openblocks.client.gui.GuiPaintMixer;
import openblocks.common.container.ContainerPaintMixer;
import openmods.GenericInventory;
import openmods.api.IHasGui;
import openmods.api.IInventoryCallback;
import openmods.sync.*;
import openmods.tileentity.SyncedTileEntity;
import openmods.utils.ColorUtils;

import com.google.common.collect.Maps;

public class TileEntityPaintMixer extends SyncedTileEntity implements IInventory, IHasGui, IInventoryCallback {

	private static final int FULL_CAN_SIZE = 30;
	private static final ItemStack PAINT_CAN = new ItemStack(OpenBlocks.Blocks.paintCan);
	private static final ItemStack MILK_BUCKET = new ItemStack(Item.bucketMilk);
	private static final int PROGRESS_TICKS = 300;

	public static enum Slots {
		input,
		output,
		dyeCyan,
		dyeMagenta,
		dyeYellow,
		dyeBlack
	}

	private static EnumMap<Slots, Integer> ALLOWED_COLORS = Maps.newEnumMap(Slots.class);

	static {
		ALLOWED_COLORS.put(Slots.dyeBlack, OreDictionary.getOreID("dyeBlack"));
		ALLOWED_COLORS.put(Slots.dyeCyan, OreDictionary.getOreID("dyeCyan"));
		ALLOWED_COLORS.put(Slots.dyeMagenta, OreDictionary.getOreID("dyeMagenta"));
		ALLOWED_COLORS.put(Slots.dyeYellow, OreDictionary.getOreID("dyeYellow"));
	}

	public enum Flags {
		hasPaint
	}

	private SyncableInt canColor;
	private SyncableInt color;
	private boolean enabled;
	private SyncableProgress progress;
	private SyncableFlags flags;
	private int chosenColor;
	// These could be optimized with a byte array later
	// Not important for release
	// Levels should be 0-2, so that if there is 0.3 left, 1 can be consumed and
	// not overflow ;)

	public SyncableFloat lvlCyan, lvlMagenta, lvlYellow, lvlBlack; /*
																	 * Black is
																	 * key ;)
																	 */

	public TileEntityPaintMixer() {
		setInventory(new GenericInventory("paintmixer", true, 6));
		inventory.addCallback(this);
	}

	@Override
	public void initialize() {
		if (!worldObj.isRemote) {
			sync();
		}
	}

	@Override
	public void updateEntity() {
		super.updateEntity();
		if (!worldObj.isRemote) {

			if (chosenColor != color.getValue()) {
				progress.reset();
				enabled = false;
			}

			if (enabled) {
				if (!hasValidInput() || !hasCMYK() || hasOutputStack()) {
					progress.reset();
					enabled = false;
					return;
				}
				if (!progress.isComplete()) {
					progress.increase();
				} else {
					inventory.decrStackSize(Slots.input.ordinal(), 1);
					consumeInk();
					ItemStack output = new ItemStack(OpenBlocks.Blocks.paintCan);
					setPaintCanColor(output);
					inventory.setInventorySlotContents(Slots.output.ordinal(), output);
					progress.reset();
					enabled = false;
				}
			}
			calculateCanColor();
			checkAutoConsumption();
			sync();
		}
	}

	private void checkAutoConsumption() {
		if (lvlCyan.getValue() <= 1f) { /* We can store 2.0, so <= */
			if (tryUseInk(Slots.dyeCyan, 1)) {
				lvlCyan.setValue(lvlCyan.getValue() + 1f);
			}
		}
		if (lvlMagenta.getValue() <= 1f) {
			if (tryUseInk(Slots.dyeMagenta, 1)) {
				lvlMagenta.setValue(lvlMagenta.getValue() + 1f);
			}
		}
		if (lvlYellow.getValue() <= 1f) {
			if (tryUseInk(Slots.dyeYellow, 1)) {
				lvlYellow.setValue(lvlYellow.getValue() + 1f);
			}
		}
		if (lvlBlack.getValue() <= 1f) {
			if (tryUseInk(Slots.dyeBlack, 1)) {
				lvlBlack.setValue(lvlBlack.getValue() + 1f);
			}
		}

	}

	public boolean hasOutputStack() {
		return inventory.getStackInSlot(Slots.output) != null;
	}

	public boolean hasCMYK() {
		return hasSufficientInk();
	}

	private void consumeInk() {
		ColorUtils.CYMK cymk = new ColorUtils.RGB(color.getValue()).toCYMK();
		lvlCyan.setValue(lvlCyan.getValue() - cymk.getCyan());
		lvlBlack.setValue(lvlBlack.getValue() - cymk.getKey());
		lvlYellow.setValue(lvlYellow.getValue() - cymk.getYellow());
		lvlMagenta.setValue(lvlMagenta.getValue() - cymk.getMagenta());
	}

	private boolean hasSufficientInk() {
		ColorUtils.CYMK cymk = new ColorUtils.RGB(color.getValue()).toCYMK();
		if (cymk.getCyan() > lvlCyan.getValue()) {
			if (tryUseInk(Slots.dyeCyan, 1)) {
				lvlCyan.setValue(lvlCyan.getValue() + 1f);
			} else {
				return false;
			}
		}
		if (cymk.getYellow() > lvlYellow.getValue()) {
			if (tryUseInk(Slots.dyeYellow, 1)) {
				lvlYellow.setValue(lvlYellow.getValue() + 1f);
			} else {
				return false;
			}
		}
		if (cymk.getMagenta() > lvlMagenta.getValue()) {
			if (tryUseInk(Slots.dyeMagenta, 1)) {
				lvlMagenta.setValue(lvlMagenta.getValue() + 1f);
			} else {
				return false;
			}
		}
		if (cymk.getKey() > lvlBlack.getValue()) {
			if (tryUseInk(Slots.dyeBlack, 1)) {
				lvlBlack.setValue(lvlBlack.getValue() + 1f);
			} else {
				return false;
			}
		}
		return true;
	}

	public boolean tryUseInk(Slots slot, int consume) {
		Integer allowedColor = ALLOWED_COLORS.get(slot);
		if (allowedColor == null) return false;

		ItemStack stack = inventory.getStackInSlot(slot);
		if (stack == null || OreDictionary.getOreID(stack) != allowedColor) return false;
		return inventory.decrStackSize(slot.ordinal(), consume) != null;
	}

	@Override
	protected void createSyncedFields() {
		color = new SyncableInt(0xFF0000);
		flags = new SyncableFlags();
		progress = new SyncableProgress(PROGRESS_TICKS);
		lvlBlack = new SyncableFloat();
		lvlCyan = new SyncableFloat();
		lvlMagenta = new SyncableFloat();
		lvlYellow = new SyncableFloat();
		canColor = new SyncableInt(0xFFFFFF);
	}

	public SyncableInt getColor() {
		return color;
	}

	public boolean hasPaint() {
		return flags.get(Flags.hasPaint);
	}

	private void setPaintCanColor(ItemStack stack) {
		if (stack != null && stack.isItemEqual(PAINT_CAN)) {
			NBTTagCompound tag = new NBTTagCompound();
			tag.setInteger("color", color.getValue());
			tag.setInteger("amount", FULL_CAN_SIZE);
			stack.setTagCompound(tag);
		}
	}

	@Override
	public void onSynced(Set<ISyncableObject> changes) {}

	@Override
	public void onSync() {}

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
		return inventory.isItemValidForSlot(i, itemstack);
	}

	@Override
	public Object getServerGui(EntityPlayer player) {
		return new ContainerPaintMixer(player.inventory, this);
	}

	@Override
	public Object getClientGui(EntityPlayer player) {
		return new GuiPaintMixer(new ContainerPaintMixer(player.inventory, this));
	}

	@Override
	public boolean canOpenGui(EntityPlayer player) {
		return true;
	}

	public void tryStartMixer() {
		if (!worldObj.isRemote) {
			enabled = true;
			chosenColor = color.getValue();
		}
	}

	public SyncableProgress getProgress() {
		return progress;
	}

	public int getCanColor() {
		return canColor.getValue();
	}

	public boolean isEnabled() {
		return progress.getValue() > 0;
	}

	public boolean hasValidInput() {
		return hasStack(Slots.input, PAINT_CAN) || hasStack(Slots.input, MILK_BUCKET);
	}

	@Override
	public void onInventoryChanged(IInventory invent, int slotNumber) {
		if (worldObj.isRemote) {
			flags.set(Flags.hasPaint, hasValidInput() || hasOutputStack());
			calculateCanColor();
			sync();
		}
	}

	private int getColorFromPaintCanSlot(Slots slot) {
		ItemStack stack = inventory.getStackInSlot(slot);
		if (stack != null && stack.isItemEqual(PAINT_CAN)) {
			NBTTagCompound tag = stack.getTagCompound();
			if (tag != null && tag.hasKey("color")) { return tag.getInteger("color"); }
		}
		return 0xFFFFFF;
	}

	private boolean hasStack(Slots slot, ItemStack stack) {
		ItemStack gotStack = inventory.getStackInSlot(slot);
		if (gotStack == null) { return false; }
		return gotStack.isItemEqual(stack);
	}

	public void calculateCanColor() {
		if (hasStack(Slots.output, PAINT_CAN)) {
			canColor.setValue(getColorFromPaintCanSlot(Slots.output));
		} else if (enabled) {
			canColor.setValue(color.getValue());
		} else {
			canColor.setValue(0xFFFFFF);
		}
	}

}
