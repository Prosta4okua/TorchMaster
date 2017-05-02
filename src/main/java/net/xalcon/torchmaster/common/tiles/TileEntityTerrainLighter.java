package net.xalcon.torchmaster.common.tiles;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.xalcon.torchmaster.TorchMasterMod;
import net.xalcon.torchmaster.common.utils.BlockUtils;

import javax.annotation.Nullable;
import java.util.UUID;

public class TileEntityTerrainLighter extends TileEntity implements IInventory, ITickable
{
	private static final GameProfile TERRAIN_LIGHTER_IDENTITY = new GameProfile(UUID.fromString("d80c982d-de38-43e6-8554-de12f86914d9"), TorchMasterMod.MODID + ":terrain_lighter");
	private static final int FUEL_SLOT = 9;
	private int[] spiralMap;

	private ItemStack[] stacks = new ItemStack[10];
	private int burnTime;
	private int totalBurnTime;
	private int index;
	private int tick;
	private boolean done = false;

	public TileEntityTerrainLighter()
	{
		spiralMap = BlockUtils.createSpiralMap(TorchMasterMod.ConfigHandler.getTerrainLighterTorchCount());
	}

	/**
	 * Returns the number of slots in the inventory.
	 */
	@Override
	public int getSizeInventory()
	{
		return 10;
	}

	/**
	 * Returns the stack in the given slot.
	 */
	@Nullable
	@Override
	public ItemStack getStackInSlot(int index)
	{
		return this.stacks[index];
	}

	/**
	 * Removes up to a specified number of items from an inventory slot and returns them in a new stack.
	 */
	@Nullable
	@Override
	public ItemStack decrStackSize(int index, int count)
	{
		ItemStack itemstack = ItemStackHelper.getAndSplit(this.stacks, index, count);

		if (itemstack != null)
		{
			this.markDirty();
		}

		return itemstack;
	}

	/**
	 * Removes a stack from the given slot and returns it.
	 */
	@Nullable
	@Override
	public ItemStack removeStackFromSlot(int index)
	{
		return ItemStackHelper.getAndRemove(this.stacks, index);
	}

	/**
	 * Sets the given item stack to the specified slot in the inventory (can be crafting or armor sections).
	 */
	@Override
	public void setInventorySlotContents(int index, @Nullable ItemStack stack)
	{
		this.stacks[index] = stack;

		if (stack != null && stack.stackSize > this.getInventoryStackLimit())
		{
			stack.stackSize = this.getInventoryStackLimit();
		}

		this.markDirty();
	}

	@Override
	public void readFromNBT(NBTTagCompound compound)
	{
		super.readFromNBT(compound);

		NBTTagList nbttaglist = compound.getTagList("Items", 10);
		this.stacks = new ItemStack[this.getSizeInventory()];

		for (int i = 0; i < nbttaglist.tagCount(); ++i)
		{
			NBTTagCompound nbttagcompound = nbttaglist.getCompoundTagAt(i);
			int j = nbttagcompound.getByte("Slot") & 255;

			if (j >= 0 && j < this.stacks.length)
			{
				this.stacks[j] = ItemStack.loadItemStackFromNBT(nbttagcompound);
			}
		}

		this.index = compound.getInteger("Index");
		this.burnTime = compound.getInteger("BurnTime");
		this.totalBurnTime = compound.getInteger("TotalBurnTime");
		this.done = this.index >= this.getTorchPlacedMax();
	}

	@Override
	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt)
	{
		super.onDataPacket(net, pkt);
		readFromNBT(pkt.getNbtCompound());
	}

	@Override
	public NBTTagCompound getUpdateTag()
	{
		return writeToNBT(super.getUpdateTag());
	}

	@Nullable
	@Override
	public SPacketUpdateTileEntity getUpdatePacket()
	{
		return new SPacketUpdateTileEntity(this.pos, 0, this.getUpdateTag());
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound)
	{
		super.writeToNBT(compound);

		NBTTagList nbttaglist = new NBTTagList();

		for (int i = 0; i < this.stacks.length; ++i)
		{
			if (this.stacks[i] != null)
			{
				NBTTagCompound nbttagcompound = new NBTTagCompound();
				nbttagcompound.setByte("Slot", (byte)i);
				this.stacks[i].writeToNBT(nbttagcompound);
				nbttaglist.appendTag(nbttagcompound);
			}
		}

		compound.setInteger("Index", this.index);
		compound.setInteger("BurnTime", this.burnTime);
		compound.setInteger("TotalBurnTime", this.totalBurnTime);
		compound.setTag("Items", nbttaglist);

		return compound;
	}

	@Nullable
	@Override
	public ITextComponent getDisplayName()
	{
		return new TextComponentTranslation(this.getName());
	}

	/**
	 * Returns the maximum stack size for a inventory slot. Seems to always be 64, possibly will be extended.
	 */
	@Override
	public int getInventoryStackLimit()
	{
		return 64;
	}

	/**
	 * Don't rename this method to canInteractWith due to conflicts with Container
	 */
	@Override
	public boolean isUseableByPlayer(EntityPlayer player)
	{
		return this.worldObj.getTileEntity(this.pos) != this ? false : player.getDistanceSq((double)this.pos.getX() + 0.5D, (double)this.pos.getY() + 0.5D, (double)this.pos.getZ() + 0.5D) <= 64.0D;
	}

	@Override
	public void openInventory(EntityPlayer player) { }

	@Override
	public void closeInventory(EntityPlayer player) { }

	/**
	 * Returns true if automation is allowed to insert the given stack (ignoring stack size) into the given slot. For
	 * guis use Slot.isItemValid
	 */
	@Override
	public boolean isItemValidForSlot(int index, ItemStack stack)
	{
		return index == 9 ? TileEntityFurnace.isItemFuel(stack) : isItemAllowed(stack);
	}

	@Override
	public int getField(int id)
	{
		return 0;
	}

	@Override
	public void setField(int id, int value) { }

	@Override
	public int getFieldCount()
	{
		return 0;
	}

	@Override
	public void clear()
	{
		for (int i = 0; i < this.stacks.length; ++i)
		{
			this.stacks[i] = null;
		}
	}

	@Override
	public String getName()
	{
		return "container.terrain_lighter";
	}

	@Override
	public boolean hasCustomName()
	{
		return false;
	}

	@Override
	public void update()
	{
		if (this.isBurningFuel())
			this.burnTime--;

		if(this.getWorld().isRemote) return;
		if(done) return;
		if(tick++ % 5 != 0) return;
		if(!this.worldObj.isBlockPowered(this.pos)) return;

		boolean updated = false;
		if(this.burnTime <= 0 && this.stacks[FUEL_SLOT] != null && this.stacks[FUEL_SLOT].stackSize > 0)
		{
			int burnTime = TileEntityFurnace.getItemBurnTime(this.stacks[9]);
			if(burnTime > 0)
			{
				this.burnTime = this.totalBurnTime = burnTime;
				this.stacks[FUEL_SLOT].stackSize--;
				if (this.stacks[FUEL_SLOT].stackSize == 0)
				{
					this.stacks[FUEL_SLOT] = this.stacks[FUEL_SLOT].getItem().getContainerItem(this.stacks[FUEL_SLOT]);
				}
				updated = true;
			}
		}

		int torchSlot = getTorchSlot();
		if (torchSlot >= 0 && this.burnTime > 0)
		{
			IBlockState torchBlockState = BlockUtils.getBlockStateFromItemStack(this.stacks[torchSlot]);
			if(torchBlockState == null) return;
			BlockPos gridPos = getPosFromIndex(index);

			int height = worldObj.getHeight(new BlockPos(gridPos.getX(), worldObj.getActualHeight(), gridPos.getZ())).getY();
			int maxY =  this.pos.getY() + 8;
			int minY = this.pos.getY() - 8;
			if(height > minY)
			{
				if(height > maxY) height = maxY;
				for(int y = height + 1; y > minY; y--)
				{
					BlockPos checkPos = new BlockPos(gridPos.getX(), y, gridPos.getZ());
					IBlockState blockState = worldObj.getBlockState(checkPos);
					IBlockState upState = worldObj.getBlockState(checkPos.up());
					if(blockState.getBlock().canPlaceTorchOnTop(blockState, worldObj, checkPos) && upState.getMaterial().isReplaceable() && !upState.getMaterial().isLiquid())
					{
						FakePlayer fakePlayer = FakePlayerFactory.get((WorldServer) this.worldObj, TERRAIN_LIGHTER_IDENTITY);
						// move the player to the light position and let him face down
						// some mods use the players facing to determine placement details
						// instead of relying on the EnumFacing :( *points at BiblioCraft*
						fakePlayer.setPosition(checkPos.getX() + 0.5, checkPos.getY() + 1.5, checkPos.getZ() + 0.5);
						fakePlayer.rotationPitch = 90f;

						EnumActionResult result = this.stacks[torchSlot].onItemUse(fakePlayer, this.worldObj, checkPos, EnumHand.MAIN_HAND, EnumFacing.UP, 0.5f, 1f, 0.5f);
						if(result == EnumActionResult.SUCCESS)
						{
							//worldObj.setBlockState(checkPos.up(), torchBlockState);
							this.decrStackSize(torchSlot, 1);
							updated = true;
						}
						break;
					}
				}
			}

			index++;
			if(index >= getTorchPlacedMax())
				done = true;
		}

		if(updated)
		{
			this.markDirty();
			IBlockState meState = this.worldObj.getBlockState(this.pos);
			this.worldObj.notifyBlockUpdate(this.pos, meState, meState, 3);
		}
	}

	private BlockPos getPosFromIndex(int index)
	{
		int spacing = TorchMasterMod.ConfigHandler.getTerrainLighterSpacing();
		int x = this.spiralMap[index * 2] * spacing + this.getPos().getX();
		int z = this.spiralMap[index * 2 + 1] * spacing + this.getPos().getZ();
		return new BlockPos(x, 0, z);
	}

	private int getTorchSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			if(this.stacks[i] != null && this.stacks[i].stackSize > 0)
				return i;
		}
		return -1;
	}

	public static boolean isItemAllowed(ItemStack stack)
	{
		return TorchMasterMod.ConfigHandler.getTerrainLighterTorches().contains(stack.getItem().getRegistryName().toString());
	}

	public boolean isBurningFuel()
	{
		return this.burnTime > 0;
	}

	public int getTorchesPlaced()
	{
		return this.index;
	}

	public int getTorchPlacedMax()
	{
		int torchCount = TorchMasterMod.ConfigHandler.getTerrainLighterTorchCount() * 2 + 1;
		return torchCount * torchCount;
	}

	public int getBurnLeftScaled(int pixel)
	{
		float p = (float)this.burnTime / this.totalBurnTime;
		return (int) (pixel * p);
	}

	public int getProgressScaled(int pixel)
	{
		float p = (float)this.index / getTorchPlacedMax();
		return (int) (pixel * p);
	}
}
