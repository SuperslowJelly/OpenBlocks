package openblocks.common.block;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import openblocks.OpenBlocks;
import openblocks.api.IPaintableBlock;
import openblocks.client.renderer.block.canvas.CanvasState;
import openblocks.client.renderer.block.canvas.InnerBlockState;
import openblocks.common.tileentity.TileEntityCanvas;
import openmods.block.OpenBlock;
import openmods.colors.ColorMeta;
import openmods.infobook.BookDocumentation;

@BookDocumentation(hasVideo = true)
public class BlockCanvas extends OpenBlock implements IPaintableBlock {

	protected enum CanvasMaterial implements IStringSerializable {
		SPONGE(Material.SPONGE),
		GRASS(Material.GRASS),
		GROUND(Material.GROUND),
		WOOD(Material.WOOD),
		ROCK(Material.ROCK),
		IRON(Material.IRON),
		LEAVES(Material.LEAVES),
		PLANTS(Material.PLANTS),
		CLOTH(Material.CLOTH),
		SAND(Material.SAND),
		CIRCUITS(Material.CIRCUITS),
		GLASS(Material.GLASS),
		ICE(Material.ICE),
		SNOW(Material.SNOW),
		CLAY(Material.CLAY),
		CARPET(Material.CARPET);

		private CanvasMaterial(Material material) {
			this.material = material;
			this.name = name().toLowerCase(Locale.ROOT);
		}

		private static final Map<Material, CanvasMaterial> MATERIAL_TO_VALUE;

		private static final CanvasMaterial[] ID_TO_VALUE = new CanvasMaterial[16];

		static {
			ImmutableMap.Builder<Material, CanvasMaterial> builder = ImmutableMap.builder();
			int i = 0;
			for (CanvasMaterial material : values()) {
				builder.put(material.material, material);
				ID_TO_VALUE[i++] = material;
			}

			while (i < 16)
				ID_TO_VALUE[i++] = SPONGE;

			MATERIAL_TO_VALUE = builder.build();
		}

		public static CanvasMaterial wrap(Material material) {
			return MATERIAL_TO_VALUE.getOrDefault(material, CanvasMaterial.SPONGE);
		}

		public final Material material;

		public final String name;

		@Override
		public String getName() {
			return name;
		}
	}

	public static final PropertyEnum<CanvasMaterial> MATERIAL = PropertyEnum.create("material", CanvasMaterial.class);

	public static class InnerBlockColorHandler implements IBlockColor {

		private final BlockColors blockColors;

		public InnerBlockColorHandler(BlockColors blockColors) {
			this.blockColors = blockColors;
		}

		@Override
		public int colorMultiplier(IBlockState state, @Nullable IBlockAccess worldIn, @Nullable BlockPos pos, int tintIndex) {
			if (state instanceof IExtendedBlockState) {
				final IExtendedBlockState extendedState = (IExtendedBlockState)state;
				final IBlockState innerState = extendedState.getValue(InnerBlockState.PROPERTY);
				if (innerState != null) return blockColors.colorMultiplier(innerState, worldIn, pos, tintIndex);
			}

			return 0xFFFFFFFF;
		}
	}

	public BlockCanvas() {
		super(Material.SPONGE);
	}

	public static boolean replaceBlock(World world, BlockPos pos) {
		final IBlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof BlockCanvas) return true;

		final Block toReplace = state.isOpaqueCube()? OpenBlocks.Blocks.canvas : OpenBlocks.Blocks.canvasGlass;
		if (toReplace == null) return false;

		final CanvasMaterial material = CanvasMaterial.wrap(state.getMaterial());
		world.setBlockState(pos, toReplace.getDefaultState().withProperty(MATERIAL, material));

		final TileEntityCanvas tile = getTileEntity(world, pos, TileEntityCanvas.class);
		if (tile != null) tile.setPaintedBlock(state);
		return true;
	}

	@Override
	public boolean recolorBlock(World world, BlockPos pos, EnumFacing side, int color) {
		final TileEntityCanvas te = getTileEntity(world, pos, TileEntityCanvas.class);
		return te != null? te.applyPaint(0xFF000000 | color, side) : false;
	}

	@Override
	public boolean recolorBlock(World world, BlockPos pos, EnumFacing side, EnumDyeColor colour) {
		ColorMeta color = ColorMeta.fromVanillaEnum(colour);
		return recolorBlock(world, pos, side, color.rgb);
	}

	@Override
	protected BlockStateContainer createBlockState() {
		return new ExtendedBlockState(this,
				new IProperty[] { getPropertyOrientation(), MATERIAL },
				new IUnlistedProperty[] { CanvasState.PROPERTY, InnerBlockState.PROPERTY });
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return state.getValue(MATERIAL).ordinal();
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return super.getStateFromMeta(meta).withProperty(MATERIAL, CanvasMaterial.ID_TO_VALUE[meta]);
	}

	@Override
	public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
		final IExtendedBlockState extendedState = (IExtendedBlockState)super.getExtendedState(state, world, pos);
		final TileEntityCanvas te = getTileEntity(world, pos, TileEntityCanvas.class);

		if (te != null) {
			return extendedState
					.withProperty(CanvasState.PROPERTY, te.getCanvasState())
					.withProperty(InnerBlockState.PROPERTY, te.getActualPaintedBlockState());
		} else {
			return extendedState;
		}
	}

	@Override
	public BlockRenderLayer getBlockLayer() {
		return BlockRenderLayer.CUTOUT;
	}

	@Override
	public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
		return layer == BlockRenderLayer.CUTOUT || layer == BlockRenderLayer.TRANSLUCENT;
	}

	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

	@Override
	public boolean isFullCube(IBlockState state) {
		return false;
	}

	@Override
	public boolean isFullBlock(IBlockState state) {
		return false;
	}

	@Override
	public boolean canProvidePower(IBlockState state) {
		return true;
	}

	@Override
	public boolean hasComparatorInputOverride(IBlockState state) {
		return true;
	}

	private interface IBlockPropertyGetter<T> {
		T get(IBlockState state, IBlockAccess world, BlockPos pos);
	}

	private static <T> T getPaintedBlockProperty(IBlockAccess world, @Nullable IBlockState state, BlockPos pos, IBlockPropertyGetter<T> getter, IBlockPropertyGetter<T> defaultValue) {
		final TileEntity te = world.getTileEntity(pos);
		if (te instanceof TileEntityCanvas) {
			final IBlockState paintedBlockState = ((TileEntityCanvas)te).getPaintedBlockState();
			if (paintedBlockState != Blocks.AIR.getDefaultState()) {
				final World actualWorld = te.getWorld();
				final TileEntityCanvas.UnpackingBlockAccess fakedWorldAccess = new TileEntityCanvas.UnpackingBlockAccess(actualWorld);
				if (actualWorld != null) {
					try {
						return getter.get(paintedBlockState, fakedWorldAccess, pos);
					} catch (Exception e) {
						// NO-OP, best effort
					}
				}
			}
		}

		return defaultValue.get(state, world, pos);
	}

	private static <T> T getPaintedBlockProperty(IBlockAccess world, IBlockState state, BlockPos pos, IBlockPropertyGetter<T> getter, final T defaultValue) {
		return getPaintedBlockProperty(world, state, pos, getter, (IBlockPropertyGetter<T>)(state1, world1, pos1) -> defaultValue);
	}

	private interface IWorldBlockPropertyGetter<T> {
		T get(IBlockState state, World world, BlockPos pos);
	}

	// NOTE is possible, every user of this method should be migrated to IBlockAccess version, once API changed
	private static <T> T getPaintedBlockProperty(World world, IBlockState state, BlockPos pos, IWorldBlockPropertyGetter<T> getter, IWorldBlockPropertyGetter<T> defaultValue) {
		final TileEntity te = world.getTileEntity(pos);
		if (te instanceof TileEntityCanvas) {
			final IBlockState paintedBlockState = ((TileEntityCanvas)te).getPaintedBlockState();
			if (paintedBlockState != Blocks.AIR.getDefaultState()) {
				try {
					return getter.get(paintedBlockState, world, pos);
				} catch (Exception e) {
					// NO-OP, best effort
				}
			}
		}

		return defaultValue.get(state, world, pos);
	}

	private static <T> T getPaintedBlockProperty(World world, IBlockState state, BlockPos pos, IWorldBlockPropertyGetter<T> getter, final T defaultValue) {
		return getPaintedBlockProperty(world, state, pos, getter, (IWorldBlockPropertyGetter<T>)(state1, world1, pos1) -> defaultValue);
	}

	@Override
	public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
		return getPaintedBlockProperty(world, state, pos, (IBlockPropertyGetter<Integer>)(state1, world1, pos1) -> state1.getLightOpacity(world1, pos1), this.lightOpacity);
	}

	@Override
	public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
		return getPaintedBlockProperty(world, state, pos, (IBlockPropertyGetter<Integer>)(state1, world1, pos1) -> state1.getLightValue(world1, pos1), this.lightValue);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public int getPackedLightmapCoords(IBlockState state, IBlockAccess source, BlockPos pos) {
		return getPaintedBlockProperty(source, state, pos, (IBlockPropertyGetter<Integer>)(state1, world, pos1) -> state1.getPackedLightmapCoords(world, pos1),
				new IBlockPropertyGetter<Integer>() {
					@Override
					@SuppressWarnings("deprecation")
					public Integer get(IBlockState state, IBlockAccess world, BlockPos pos) {
						return BlockCanvas.super.getPackedLightmapCoords(state, world, pos);
					}
				});
	}

	@Override
	public int getWeakPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, final EnumFacing side) {
		return getPaintedBlockProperty(blockAccess, blockState, pos, (IBlockPropertyGetter<Integer>)(state, world, pos1) -> state.getWeakPower(world, pos1, side), 0);
	}

	@Override
	public int getStrongPower(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, final EnumFacing side) {
		return getPaintedBlockProperty(blockAccess, blockState, pos, (IBlockPropertyGetter<Integer>)(state, world, pos1) -> state.getStrongPower(world, pos1, side), 0);
	}

	@Override
	public int getComparatorInputOverride(IBlockState blockState, World worldIn, BlockPos pos) {
		return getPaintedBlockProperty(worldIn, blockState, pos, (IWorldBlockPropertyGetter<Integer>)(state, world, pos1) -> state.getComparatorInputOverride(world, pos1), 0);
	}

	@Override
	public float getBlockHardness(IBlockState blockState, World worldIn, BlockPos pos) {
		return getPaintedBlockProperty(worldIn, blockState, pos, (IWorldBlockPropertyGetter<Float>)(state, world, pos1) -> state.getBlockHardness(world, pos1), this.blockHardness);
	}

	@Override
	public float getPlayerRelativeBlockHardness(IBlockState state, final EntityPlayer player, World worldIn, BlockPos pos) {
		return getPaintedBlockProperty(worldIn, state, pos, (IWorldBlockPropertyGetter<Float>)(state1, world, pos1) -> state1.getPlayerRelativeBlockHardness(player, world, pos1), new IWorldBlockPropertyGetter<Float>() {
			@Override
			@SuppressWarnings("deprecation")
			public Float get(IBlockState state, World world, BlockPos pos) {
				return BlockCanvas.super.getPlayerRelativeBlockHardness(state, player, world, pos);
			}
		});
	}

	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getSelectedBoundingBox(IBlockState state, World worldIn, BlockPos pos) {
		return getPaintedBlockProperty(worldIn, state, pos, (IWorldBlockPropertyGetter<AxisAlignedBB>)(state1, world, pos1) -> state1.getSelectedBoundingBox(world, pos1), (IWorldBlockPropertyGetter<AxisAlignedBB>)(state1, world, pos1) -> FULL_BLOCK_AABB.offset(pos1));
	}

	@Override
	@Nullable
	public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
		return getPaintedBlockProperty(worldIn, blockState, pos, (IBlockPropertyGetter<AxisAlignedBB>)(state, world, pos1) -> state.getCollisionBoundingBox(world, pos1), FULL_BLOCK_AABB);
	}

	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		return getPaintedBlockProperty(source, state, pos, (IBlockPropertyGetter<AxisAlignedBB>)(state1, world, pos1) -> state1.getBoundingBox(world, pos1), FULL_BLOCK_AABB);
	}

	@Override
	public void addCollisionBoxToList(IBlockState state, World worldIn, BlockPos pos, final AxisAlignedBB entityBox, final List<AxisAlignedBB> collidingBoxes, final @Nullable Entity entityIn, final boolean p_185477_7_) {
		getPaintedBlockProperty(worldIn, state, pos, (IWorldBlockPropertyGetter<Void>)(state1, world, pos1) -> {
			state1.addCollisionBoxToList(world, pos1, entityBox, collidingBoxes, entityIn, p_185477_7_);
			return null;
		}, (IWorldBlockPropertyGetter<Void>)(state1, world, pos1) -> {
			addCollisionBoxToList(pos1, entityBox, collidingBoxes, FULL_BLOCK_AABB);
			return null;
		});
	}

	@Override
	@Nullable
	public RayTraceResult collisionRayTrace(IBlockState blockState, World worldIn, BlockPos pos, final Vec3d start, final Vec3d end) {
		return getPaintedBlockProperty(worldIn, blockState, pos, (IWorldBlockPropertyGetter<RayTraceResult>)(state, world, pos1) -> state.collisionRayTrace(world, pos1, start, end), (IWorldBlockPropertyGetter<RayTraceResult>)(state, world, pos1) -> rayTrace(pos1, start, end, FULL_BLOCK_AABB));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean shouldSideBeRendered(IBlockState blockState, IBlockAccess blockAccess, BlockPos pos, final EnumFacing side) {
		return getPaintedBlockProperty(blockAccess, blockState, pos, (IBlockPropertyGetter<Boolean>)(state, world, pos1) -> state.shouldSideBeRendered(world, pos1, side), true);
	}

	@Override
	public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, final EnumFacing face) {
		return getPaintedBlockProperty(world, state, pos, (IBlockPropertyGetter<Boolean>)(state1, world1, pos1) -> state1.doesSideBlockRendering(world1, pos1, face), Boolean.TRUE);
	}

	@Override
	public boolean isSideSolid(IBlockState base_state, IBlockAccess world, BlockPos pos, final EnumFacing side) {
		return getPaintedBlockProperty(world, base_state, pos, (IBlockPropertyGetter<Boolean>)(state, world1, pos1) -> state.isSideSolid(world1, pos1, side), Boolean.TRUE);
	}

	@Override
	public boolean isPassable(IBlockAccess worldIn, BlockPos pos) {
		return getPaintedBlockProperty(worldIn, null, pos, (IBlockPropertyGetter<Boolean>)(state, world, pos1) -> state.getBlock().isPassable(world, pos1), Boolean.FALSE);
	}

	@Override
	public boolean canSustainLeaves(IBlockState state, IBlockAccess world, BlockPos pos) {
		return getPaintedBlockProperty(world, state, pos, (IBlockPropertyGetter<Boolean>)(state1, world1, pos1) -> state1.getBlock().canSustainLeaves(state1, world1, pos1), Boolean.FALSE);
	}

	@Override
	public boolean canSustainPlant(IBlockState state, IBlockAccess world, BlockPos pos, final EnumFacing direction, final IPlantable plantable) {
		return getPaintedBlockProperty(world, state, pos, (IBlockPropertyGetter<Boolean>)(state1, world1, pos1) -> state1.getBlock().canSustainPlant(state1, world1, pos1, direction, plantable), Boolean.FALSE);
	}

	@Override
	public float getEnchantPowerBonus(World world, BlockPos pos) {
		return getPaintedBlockProperty(world, null, pos, (IWorldBlockPropertyGetter<Float>)(state, world1, pos1) -> state.getBlock().getEnchantPowerBonus(world1, pos1), 0.0f);
	}

	@Override
	public float getExplosionResistance(World world, BlockPos pos, final Entity exploder, final Explosion explosion) {
		return getPaintedBlockProperty(world, null, pos, (IWorldBlockPropertyGetter<Float>)(state, world1, pos1) -> state.getBlock().getExplosionResistance(world1, pos1, exploder, explosion), super.getExplosionResistance(world, pos, exploder, explosion));
	}

	@Override
	public int getFlammability(IBlockAccess world, BlockPos pos, final EnumFacing face) {
		return getPaintedBlockProperty(world, null, pos, (IBlockPropertyGetter<Integer>)(state, world1, pos1) -> state.getBlock().getFlammability(world1, pos1, face), (IBlockPropertyGetter<Integer>)(state, world1, pos1) -> BlockCanvas.super.getFlammability(world1, pos1, face));
	}

	@Override
	public int getFireSpreadSpeed(IBlockAccess world, BlockPos pos, final EnumFacing face) {
		return getPaintedBlockProperty(world, null, pos, (IBlockPropertyGetter<Integer>)(state, world1, pos1) -> state.getBlock().getFireSpreadSpeed(world1, pos1, face), 200);
	}

	@Override
	public boolean isFlammable(IBlockAccess world, BlockPos pos, final EnumFacing face) {
		return getPaintedBlockProperty(world, null, pos, (IBlockPropertyGetter<Boolean>)(state, world1, pos1) -> state.getBlock().isFlammable(world1, pos1, face), Boolean.TRUE);
	}

	@Override
	public boolean isBurning(IBlockAccess world, BlockPos pos) {
		return getPaintedBlockProperty(world, null, pos, (IBlockPropertyGetter<Boolean>)(state, world1, pos1) -> state.getBlock().isBurning(world1, pos1), Boolean.FALSE);
	}

	@Override
	public boolean isFireSource(World world, BlockPos pos, final EnumFacing side) {
		return getPaintedBlockProperty(world, null, pos, (IWorldBlockPropertyGetter<Boolean>)(state, world1, pos1) -> state.getBlock().isFireSource(world1, pos1, side), Boolean.FALSE);
	}

	@Override
	public boolean isFertile(World world, BlockPos pos) {
		return getPaintedBlockProperty(world, null, pos, (IWorldBlockPropertyGetter<Boolean>)(state, world1, pos1) -> state.getBlock().isFertile(world1, pos1), Boolean.FALSE);
	}

	@Override
	public void onFallenUpon(World worldIn, BlockPos pos, final Entity entityIn, final float fallDistance) {
		getPaintedBlockProperty(worldIn, null, pos, (IWorldBlockPropertyGetter<Void>)(state, world, pos1) -> {
			state.getBlock().onFallenUpon(world, pos1, entityIn, fallDistance);
			return null;
		}, (IWorldBlockPropertyGetter<Void>)(state, world, pos1) -> {
			BlockCanvas.super.onFallenUpon(world, pos1, entityIn, fallDistance);
			return null;
		});
	}

	@Override
	public void onLanded(World worldIn, final Entity entityIn) {
		final BlockPos pos = new BlockPos(entityIn.posX, entityIn.posY - 0.2, entityIn.posZ);
		getPaintedBlockProperty(worldIn, null, pos, (IWorldBlockPropertyGetter<Void>)(state, world, pos1) -> {
			state.getBlock().onLanded(world, entityIn);
			return null;
		}, (IWorldBlockPropertyGetter<Void>)(state, world, pos1) -> {
			BlockCanvas.super.onLanded(world, entityIn);
			return null;
		});
	}

	@Override
	public void onEntityWalk(World worldIn, BlockPos pos, final Entity entityIn) {
		getPaintedBlockProperty(worldIn, null, pos, (IWorldBlockPropertyGetter<Void>)(state, world, pos1) -> {
			state.getBlock().onEntityWalk(world, pos1, entityIn);
			return null;
		}, (IWorldBlockPropertyGetter<Void>)(state, world, pos1) -> {
			BlockCanvas.super.onEntityWalk(world, pos1, entityIn);
			return null;
		});
	}

	@Override
	public SoundType getSoundType(IBlockState state, World worldIn, BlockPos pos, Entity entity) {
		return getPaintedBlockProperty(worldIn, state, pos, (IWorldBlockPropertyGetter<SoundType>)(innerState, world, pos1) -> {
			return innerState.getBlock().getSoundType(state, world, pos1, entity);
		}, blockSoundType);
	}

	@Override
	public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
		return getPaintedBlockProperty(worldIn, state, pos, (IBlockPropertyGetter<BlockFaceShape>)(state1, world, pos1) -> state1.getBlockFaceShape(world, pos1, face), BlockFaceShape.SOLID);
	}

	@Override
	public Material getMaterial(IBlockState state) {
		return state.getValue(MATERIAL).material;
	}
}
