//------------------------------------------------------------------------------------------------
//
//   Greg's Mod Base for 1.10 - Generic Block with optional Tile Entity
//
//------------------------------------------------------------------------------------------------

package gcewing.sg;

import java.util.*;

import gcewing.sg.util.Info;
import net.minecraft.block.*;
import net.minecraft.block.material.*;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.client.particle.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.state.Property;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

import net.minecraftforge.fml.common.registry.*;
import net.minecraftforge.fml.relauncher.*;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

import static gcewing.sg.BaseMod.*;
import static gcewing.sg.BaseModClient.*;

public class BaseBlock<TE extends TileEntity>
    extends ContainerBlock implements BaseMod.IBlock
{

    public static boolean debugState = false;
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Info.modID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Info.modID);
    private static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, Info.modID);

    @Nullable
    @Override
    public TileEntity createNewTileEntity(IBlockReader worldIn) {
        return new BaseTileInventory();
    }

    // --------------------------- Orientation -------------------------------

    public interface IOrientationHandler {

        void defineProperties(BaseBlock block);
        BlockState onBlockPlaced(Block block, World world, BlockPos pos, Direction side,
            float hitX, float hitY, float hitZ, BlockState baseState, LivingEntity placer);
        //Trans3 localToGlobalTransformation(IBlockAccess world, BlockPos pos, IBlockState state);
        Trans3 localToGlobalTransformation(IBlockReader world, BlockPos pos, BlockState state, Vector3 origin);
    }
    
    public static class Orient1Way implements IOrientationHandler {
    
        public void defineProperties(BaseBlock block) {
        }
        
        public BlockState onBlockPlaced(Block block, World world, BlockPos pos, Direction side,
            float hitX, float hitY, float hitZ, BlockState baseState, LivingEntity placer)
        {
            return baseState;
        }
        
        public Trans3 localToGlobalTransformation(IBlockReader world, BlockPos pos, BlockState state, Vector3 origin) {
            return new Trans3(origin);
        }
    
    }
    
    public static IOrientationHandler orient1Way = new Orient1Way();

    // --------------------------- Members -------------------------------

    protected MaterialColor mapColor;
    protected Property[] properties;
    protected Object[][] propertyValues;
    protected int numProperties; // Do not explicitly initialise
    protected BlockRenderType renderID = BlockRenderType.MODEL;
    protected Class<? extends TileEntity> tileEntityClass = null;
    protected IOrientationHandler orientationHandler = orient1Way;
    protected String[] textureNames;
    protected ModelSpec modelSpec;
    protected BaseMod mod;
    protected AxisAlignedBB boxHit;

    // --------------------------- Constructors -------------------------------
    
    public BaseBlock(Material material) {
        this(material, null, null, null);
    }

    public BaseBlock(Material material, IOrientationHandler orient) {
        this(material, orient, null, null);
    }

    public BaseBlock(Material material, Class<TE> teClass) {
        this(material, null, teClass, null);
    }

    public BaseBlock(Material material, IOrientationHandler orient, Class<TE> teClass) {
        this(material, orient, teClass, null);
    }

    public BaseBlock(Material material, Class<TE> teClass, String teID) {
        this(material, null, teClass, teID);
    }

    public BaseBlock(Material material, IOrientationHandler orient, Class<TE> teClass, String teID) {
        super(Properties.create(material));
        if (orient == null)
            orient = orient1Way;
        this.orientationHandler = orient;
        // TE Fixer:
        // The following is designed to fix the fact that when gcewing registered TE's he used the full class name in the registration
        // So after I re-packaged the TE classes it broke the world data registrations.

        // Core TE's
        if (teClass.getName().equalsIgnoreCase("gcewing.sg.tileentity.DHDTE"))
            teID = "gcewing.sg.DHDTE";
        if (teClass.getName().equalsIgnoreCase("gcewing.sg.tileentity.PowerTE"))
            teID = "gcewing.sg.PowerTE";
        if (teClass.getName().equalsIgnoreCase("gcewing.sg.tileentity.SGBaseTE"))
            teID = "gcewing.sg.SGBaseTE";
        if (teClass.getName().equalsIgnoreCase("gcewing.sg.tileentity.SGInterfaceTE"))
            teID = "gcewing.sg.SGInterfaceTE";
        if (teClass.getName().equalsIgnoreCase("gcewing.sg.tileentity.SGRingTE"))
            teID = "gcewing.sg.SGRingTE";
        if (teClass.getName().equalsIgnoreCase("gcewing.sg.tileentity.SGPowerTE"))
            teID = "gcewing.sg.SGPowerTE";

        // Addon TE's
        if (teClass.getName().equalsIgnoreCase("gcewing.sg.features.ic2.IC2PowerTE"))
            teID = "gcewing.sg.ic2.IC2PowerTE";
        if (teClass.getName().equalsIgnoreCase("gcewing.sg.features.cc.CCInterfaceTE"))
            teID = "gcewing.sg.cc.CCInterfaceTE";
        if (teClass.getName().equalsIgnoreCase("gcewing.sg.features.oc.OCInterfaceTE"))
            teID = "gcewing.sg.oc.OCInterfaceTE";
        if (teClass.getName().equalsIgnoreCase("gcewing.sg.features.ic2.zpm.ZPMInterfaceCartTE"))
            teID = "gcewing.sg.ic2.zpm.ZPMInterfaceCartTE";
        // End TE World Fixer.

        tileEntityClass = teClass;
        // TODO: Register from main mod using new Forge methods. Probably has to be done individually :/
    }

    // --------------------------- States -------------------------------
    
    public IOrientationHandler getOrientationHandler() {
        return orient1Way;
    }
    
    protected void defineProperties() {
        properties = new Property[4];
        propertyValues = new Object[4][];
        getOrientationHandler().defineProperties(this);
    }
    
    protected void addProperty(Property property) {
        if (debugState)
            System.out.printf("BaseBlock.addProperty: %s to %s\n", property, getClass().getName());
        if (numProperties < 4) {
            int i = numProperties++;
            properties[i] = property;
            Object[] values = BaseUtils.arrayOf(property.getAllowedValues());
            propertyValues[i] = values;
        }
        else
            throw new IllegalStateException("Block " + getClass().getName() +
                " has too many properties");
        if (debugState)
            System.out.printf("BaseBlock.addProperty: %s now has %s properties\n",
                getClass().getName(), numProperties);
    }
    
    private void dumpProperties() {
        System.out.printf("BaseBlock: Properties of %s:\n", getClass().getName());
        for (int i = 0; i < numProperties; i++) {
            System.out.printf("%s: %s\n", i, properties[i]);
            Object[] values = propertyValues[i];
            for (int j = 0; j < values.length; j++)
                System.out.printf("   %s: %s\n", j, values[j]);
        }
    }

    protected void checkProperties() {
        int n = 1;
        for (int i = 0; i < numProperties; i++)
            n *= propertyValues[i].length;
        if (n > 16)
            throw new IllegalStateException(String.format(
                "Block %s has %s combinations of property values (16 allowed)",
                getClass().getName(), n));
    }

    // -------------------------- Subtypes ------------------------------
    
    public int getNumSubtypes() {
        return 1;
    }

    // -------------------------- Harvesting ----------------------------
    
    protected ThreadLocal<TileEntity> harvestingTileEntity = new ThreadLocal();
    
	@Override
	public void harvestBlock(World world, PlayerEntity player, BlockPos pos, BlockState state, TileEntity te, ItemStack stack) {
        harvestingTileEntity.set(te);
        super.harvestBlock(world, player, pos, state, te, stack);
        harvestingTileEntity.set(null);
    }
    
    // -------------------------- Rendering -----------------------------
    @Override
    public String getQualifiedRendererClassName() { // TODO: There's no way this works, figure out what's supposed to happen here
         return getRendererClassName();
    }

    protected String getRendererClassName() {
        return null;
    }
        
    public void setModelAndTextures(String modelName, String... textureNames) {
        this.textureNames = textureNames;
        this.modelSpec = new ModelSpec(modelName, textureNames);
    }
    
    public void setModelAndTextures(String modelName, Vector3 origin, String... textureNames) {
        this.textureNames = textureNames;
        this.modelSpec = new ModelSpec(modelName, origin, textureNames);
    }
    
    public String[] getTextureNames() {
        return textureNames;
    }

    public ModelSpec getModelSpec(BlockState state) {
        return modelSpec;
    }
    
    public Trans3 localToGlobalRotation(Blockreader world, BlockPos pos) {
        return localToGlobalRotation(world, pos, world.getBlockState(pos));
    }
    
    public Trans3 localToGlobalRotation(Blockreader world, BlockPos pos, BlockState state) {
        return localToGlobalTransformation(world, pos, state, Vector3.zero);
    }
    
    public Trans3 localToGlobalTransformation(Blockreader world, BlockPos pos) {
        return localToGlobalTransformation(world, pos, world.getBlockState(pos));
    }
    
    public Trans3 localToGlobalTransformation(Blockreader world, BlockPos pos, BlockState state) {
        return localToGlobalTransformation(world, pos, state, Vector3.blockCenter(pos));
    }
    
    public Trans3 localToGlobalTransformation(Blockreader world, BlockPos pos, BlockState state, Vector3 origin) {
        IOrientationHandler oh = getOrientationHandler();
        return oh.localToGlobalTransformation(world, pos, state, origin);
    }
    
    public Trans3 itemTransformation() {
        return Trans3.ident;
    }
    
    // -------------------------- Tile Entity -----------------------------
    
    @Override
    public boolean hasTileEntity(BlockState state) {
        return tileEntityClass != null;
    }
    
    public TE getTileEntity(Blockreader world, BlockPos pos) {
        if (hasTileEntity(world.getBlockState(pos)))
            return (TE)world.getTileEntity(pos);
        else
            return null;
    }
    
    @Override // TODO: Something is wrong here
    public TileEntity createNewTileEntity(World world, int meta) {
        if (tileEntityClass != null) {
            try {
                return tileEntityClass.newInstance();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        else
            return null;
    }
    
    // -------------------------------------------------------------------

    @Override // TODO: Another override fix
    public BlockState getStateForPlacement(World world, BlockPos pos, Direction side, float hitX, float hitY, float hitZ, int meta, LivingEntity placer)
    {
        return getOrientationHandler().onBlockPlaced(this, world, pos, side, hitX, hitY, hitZ, getDefaultState(), placer);
    }

    // TODO: Do we need any of these?
    /*@Override
    public void onBlockAdded(World world, BlockPos pos, BlockState state) {
	    super.onBlockPlacedBy(world, pos, state, null, );
        super.onBlockAdded(world, pos, state);
        if (hasTileEntity(state)) {
            TileEntity te = getTileEntity(world, pos);
            if (te instanceof BaseMod.ITileEntity)
                ((BaseMod.ITileEntity)te).onAddedToWorld();
        }
    }


    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (hasTileEntity(state)) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof IInventory)
                InventoryHelper.dropInventoryItems(world, pos, (IInventory)te);
        }
    }

    @Override
    public boolean addLandingEffects(IBlockState state, WorldServer world, BlockPos pos,
        IBlockState iblockstate, EntityLivingBase entity, int numParticles)
    {
        IBlockState particleState = getParticleState(world, pos);
        world.spawnParticle(EnumParticleTypes.BLOCK_DUST, entity.posX, entity.posY, entity.posZ,
            numParticles, 0, 0, 0, 0.15, new int[]{Block.getStateId(particleState)});
        return true;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean addHitEffects(IBlockState blockState, World world, RayTraceResult target, ParticleManager pm) {
        BlockPos pos = target.getBlockPos();
        IBlockState state = getParticleState(world, pos);
        ParticleDigging fx;
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        AxisAlignedBB boundingBox = blockState.getBoundingBox(world, pos);
        float f = 0.1F;
        double d0 = i + RANDOM.nextDouble() * (boundingBox.maxX - boundingBox.minX - (f * 2F)) + f + boundingBox.minX;
        double d1 = j + RANDOM.nextDouble() * (boundingBox.maxY - boundingBox.minY - (f * 2F)) + f + boundingBox.minY;
        double d2 = k + RANDOM.nextDouble() * (boundingBox.maxZ - boundingBox.minZ - (f * 2F)) + f + boundingBox.minZ;
        switch (target.sideHit) {
            case DOWN: d1 = j + boundingBox.minY - f; break;
            case UP: d1 = j + boundingBox.maxY + f; break;
            case NORTH: d2 = k + boundingBox.minZ - f; break;
            case SOUTH: d2 = k + boundingBox.maxZ + f; break;
            case WEST: d0 = i + boundingBox.minX - f; break;
            case EAST: d0 = i + boundingBox.maxX + f; break;
        }
        fx = new DiggingFX(world, d0, d1, d2, 0, 0, 0, state);
        pm.addEffect(fx.setBlockPos(pos).multiplyVelocity(0.2F).multipleParticleScaleBy(0.6F));
        return true;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean addDestroyEffects(World world, BlockPos pos, ParticleManager pm) {
        IBlockState state = getParticleState(world, pos);
        ParticleDigging fx;
        byte b0 = 4;
        for (int i = 0; i < b0; ++i) {
            for (int j = 0; j < b0; ++j) {
                for (int k = 0; k < b0; ++k) {
                    double d0 = pos.getX() + (i + 0.5D) / b0;
                    double d1 = pos.getY() + (j + 0.5D) / b0;
                    double d2 = pos.getZ() + (k + 0.5D) / b0;
                    fx = new DiggingFX(world, d0, d1, d2,
                        d0 - pos.getX() - 0.5D, d1 - pos.getY() - 0.5D, d2 - pos.getZ() - 0.5D,
                        state);
                    pm.addEffect(fx.setBlockPos(pos));
                }
            }
        }
        return true;
    }*/
    
    public BlockState getParticleState(Blockreader world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return getActualState(state, world, pos);
    }
    
    // Workaround for ParticleDigging having protected constructor
    
    @SideOnly(Side.CLIENT)
    public static class DiggingFX extends ParticleDigging {
    
        public DiggingFX(World world, double x1, double y1, double z1, double x2, double y2, double z2, IBlockState state) {
            super(world, x1, y1, z1, x2, y2, z2, state);
        }
    
    }

    //----------------------------- Bounds and collision boxes -----------------------------------

    @Override
	public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
	    boxHit = null;
		RayTraceResult result = null;
		double nearestDistance = 0;
		List<AxisAlignedBB> list = getGlobalCollisionBoxes(world, pos, state, null);
		if (list != null) {
			int n = list.size();
			for (int i = 0; i < n; i++) {
				AxisAlignedBB box = list.get(i);
				RayTraceResult mp = box.calculateIntercept(start, end);
				if (mp != null) {
					mp.subHit = i;
					double d = start.squareDistanceTo(mp.hitVec);
					if (result == null || d < nearestDistance) {
						result = mp;
						nearestDistance = d;
					}
				}
			}
		}
		if (result != null) {
			//setBlockBounds(list.get(result.subHit));
			int i = result.subHit;
			boxHit = list.get(i).offset(-pos.getX(), -pos.getY(), -pos.getZ());
			result = new RayTraceResult(result.hitVec, result.sideHit, pos);
			result.subHit = i;
		}
		return result;
	}

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
	    AxisAlignedBB box = boxHit;
	    if (box == null)
            box = getLocalBounds(world, pos, state, null);
		if (box != null)
			return box;
		else
			return super.getBoundingBox(state, world, pos);
	}

    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, World world, BlockPos pos) {
        // Update: This had an override above the method, may be needed.
        return getBoundingBox(state, world, pos);
    }


	protected AxisAlignedBB getLocalBounds(IBlockAccess world, BlockPos pos, IBlockState state,
	    Entity entity)
	{
	    ModelSpec spec = getModelSpec(state);
	    if (spec != null) {
	        IModel model = mod.getModel(spec.modelName);
            Trans3 t = localToGlobalTransformation(world, pos, state, Vector3.blockCenter).translate(spec.origin);
            return t.t(model.getBounds());
	    }
	    return null;
	}

    @Override
	public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, 
		AxisAlignedBB clip, List result, Entity entity, boolean enableStats)
	{
		List<AxisAlignedBB> list = getGlobalCollisionBoxes(world, pos, state, entity);
		if (list != null)
			for (AxisAlignedBB box : list)
				if (clip.intersects(box))
					result.add(box);
	    else
	        super.addCollisionBoxToList(state, world, pos, clip, result, entity, enableStats);
	}

	protected List<AxisAlignedBB> getGlobalCollisionBoxes(IBlockAccess world, BlockPos pos,
		IBlockState state, Entity entity)
	{
		Trans3 t = localToGlobalTransformation(world, pos, state);
		return getCollisionBoxes(world, pos, state, t, entity);
	}

	protected List<AxisAlignedBB> getLocalCollisionBoxes(IBlockAccess world, BlockPos pos,
		IBlockState state, Entity entity)
	{
		Trans3 t = localToGlobalTransformation(world, pos, state, Vector3.zero);
		return getCollisionBoxes(world, pos, state, t, entity);
	}

	protected List<AxisAlignedBB> getCollisionBoxes(IBlockAccess world, BlockPos pos, IBlockState state,
	    Trans3 t, Entity entity)
	{
	    ModelSpec spec = getModelSpec(state);
	    if (spec != null) {
	        IModel model = mod.getModel(spec.modelName);
            List<AxisAlignedBB> list = new ArrayList<AxisAlignedBB>();
            model.addBoxesToList(t.translate(spec.origin), list);
            return list;
        }
        return null;
	}
}
