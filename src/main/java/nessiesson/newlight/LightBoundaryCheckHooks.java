package nessiesson.newlight;

import nessiesson.newlight.mixins.IMixinEnumFacing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.EnumFacing.AxisDirection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import nessiesson.newlight.LightUtils.EnumBoundaryFacing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LightBoundaryCheckHooks
{
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static final String neighborLightChecksKey = "NeighborLightChecks";
    public static final int FLAG_COUNT = 32; //2 light types * 4 directions * 2 halves * (inwards + outwards)

    public static void flagSecBoundaryForUpdate(final Chunk chunk, final BlockPos pos, final EnumSkyBlock lightType, final EnumFacing dir, final EnumBoundaryFacing boundaryFacing)
    {
        flagChunkBoundaryForUpdate(chunk, (short) (1 << (pos.getY() >> 4)), lightType, dir, LightUtils.getAxisDirection(dir, pos.getX(), pos.getZ()), boundaryFacing);
    }

    public static void flagChunkBoundaryForUpdate(final Chunk chunk, final short sectionMask, final EnumSkyBlock lightType, final EnumFacing dir, final AxisDirection axisDirection, final EnumBoundaryFacing boundaryFacing)
    {
        initNeighborLightChecks(chunk);
        ((IChunk)chunk).getNeighborLightChecks()[getFlagIndex(lightType, dir, axisDirection, boundaryFacing)] |= sectionMask;
        chunk.markDirty();
    }

    private static EnumFacing getDiagonalDir(final EnumFacing dir, final AxisDirection axisDir)
    {
        return LightUtils.getDirFromAxis(dir.getAxis() == Axis.X ? Axis.Z : Axis.X, axisDir);
    }

    public static void scheduleRelightChecksForChunkBoundaries(final World world, final Chunk chunk)
    {
        final PooledMutableBlockPos pos = PooledMutableBlockPos.retain();

        for (final EnumFacing dir : IMixinEnumFacing.getHorizontals())
        {
            final int xOffset = dir.getFrontOffsetX();
            final int zOffset = dir.getFrontOffsetZ();

            final Chunk nChunk = world.getChunkProvider().getLoadedChunk(chunk.x + xOffset, chunk.z + zOffset);

            if (nChunk == null)
            {
                continue;
            }

            for (final EnumSkyBlock lightType : LightUtils.ENUM_SKY_BLOCK_VALUES)
            {
                for (final AxisDirection axisDir : LightUtils.ENUM_AXIS_DIRECTION_VALUES)
                {
                    //Merge flags upon loading of a chunk. This ensures that all flags are always already on the IN boundary below
                    mergeFlags(lightType, chunk, nChunk, dir, axisDir);
                    mergeFlags(lightType, nChunk, chunk, dir.getOpposite(), axisDir);

                    //Check everything that might have been canceled due to this chunk not being loaded.
                    //Also, pass in chunks if already known
                    //The boundary to the neighbor chunk (both ways)
                    scheduleRelightChecksForBoundary(world, chunk, nChunk, null, lightType, xOffset, zOffset, axisDir, pos, dir);
                    scheduleRelightChecksForBoundary(world, nChunk, chunk, null, lightType, -xOffset, -zOffset, axisDir, pos, dir.getOpposite());

                    //The boundary to the diagonal neighbor (since the checks in that chunk were aborted if this chunk wasn't loaded, see scheduleRelightChecksForBoundary)
                    final EnumFacing diagDir = getDiagonalDir(dir, axisDir);
                    scheduleRelightChecksForBoundary(
                        world,
                        nChunk,
                        null,
                        chunk,
                        lightType,
                        diagDir.getFrontOffsetX(),
                        diagDir.getFrontOffsetZ(),
                        dir.getAxisDirection() == AxisDirection.POSITIVE ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE,
                        pos,
                        diagDir
                    );
                }
            }
        }

        pos.release();
    }

    private static void mergeFlags(final EnumSkyBlock lightType, final Chunk inChunk, final Chunk outChunk, final EnumFacing dir, final AxisDirection axisDir)
    {
        if (((IChunk)outChunk).getNeighborLightChecks() == null)
        {
            return;
        }

        initNeighborLightChecks(inChunk);

        final int inIndex = getFlagIndex(lightType, dir, axisDir, LightUtils.EnumBoundaryFacing.IN);
        final int outIndex = getFlagIndex(lightType, dir.getOpposite(), axisDir, LightUtils.EnumBoundaryFacing.OUT);

        ((IChunk)inChunk).getNeighborLightChecks()[inIndex] |= ((IChunk)outChunk).getNeighborLightChecks()[outIndex];
        //no need to call Chunk.setModified() since checks are not deleted from outChunk
    }

    private static void scheduleRelightChecksForBoundary(
        final World world,
        final Chunk chunk,
        Chunk nChunk,
        Chunk sChunk,
        final EnumSkyBlock lightType,
        final int xOffset,
        final int zOffset,
        final AxisDirection axisDir,
        final MutableBlockPos pos,
        EnumFacing dir
    )
    {
        if (((IChunk)chunk).getNeighborLightChecks() == null)
        {
            return;
        }

        final int flagIndex = getFlagIndex(lightType, xOffset, zOffset, axisDir, LightUtils.EnumBoundaryFacing.IN); //OUT checks from neighbor are already merged

        final int flags = ((IChunk)chunk).getNeighborLightChecks()[flagIndex];

        if (flags == 0)
        {
            return;
        }

        if (nChunk == null)
        {
            nChunk = world.getChunkProvider().getLoadedChunk(chunk.x + xOffset, chunk.z + zOffset);

            if (nChunk == null)
            {
                return;
            }
        }

        if (sChunk == null)
        {
            final EnumFacing diagDir = getDiagonalDir(dir, axisDir);

            sChunk = world.getChunkProvider().getLoadedChunk(chunk.x + diagDir.getFrontOffsetX(), chunk.z + diagDir.getFrontOffsetZ());

            if (sChunk == null)
            {
                return; //Cancel, since the checks in the corner columns require the corner column of sChunk
            }
        }

        final int reverseIndex = getFlagIndex(lightType, -xOffset, -zOffset, axisDir, LightUtils.EnumBoundaryFacing.OUT);

        ((IChunk)chunk).getNeighborLightChecks()[flagIndex] = 0;

        if (((IChunk)nChunk).getNeighborLightChecks() != null)
        {
            ((IChunk)nChunk).getNeighborLightChecks()[reverseIndex] = 0; //Clear only now that it's clear that the checks are processed
        }

        chunk.markDirty();
        nChunk.markDirty();

        //Get the area to check
        //Start in the corner...
        int xMin = chunk.x << 4;
        int zMin = chunk.z << 4;

        //move to other side of chunk if the direction is positive
        if ((xOffset | zOffset) > 0)
        {
            xMin += 15 * xOffset;
            zMin += 15 * zOffset;
        }

        //shift to other half if necessary (shift perpendicular to dir)
        if (axisDir == AxisDirection.POSITIVE)
        {
            xMin += 8 * (zOffset & 1); //x & 1 is same as abs(x) for x=-1,0,1
            zMin += 8 * (xOffset & 1);
        }

        //get maximal values (shift perpendicular to dir)
        final int xMax = xMin + 7 * (zOffset & 1);
        final int zMax = zMin + 7 * (xOffset & 1);

        for (int y = 0; y < 16; ++y)
        {
            if ((flags & (1 << y)) != 0)
            {
                LightUtils.scheduleRelightChecksForArea(world, lightType, xMin, y << 4, zMin, xMax, (y << 4) + 15, zMax, pos);
            }
        }

        if (world instanceof WorldServer)
        {
            final PlayerChunkMap playerChunkMap = ((WorldServer) world).getPlayerChunkMap();
            final PlayerChunkMapEntry playerChunk = playerChunkMap.getEntry(chunk.x, chunk.z);

            if (playerChunk != null)
                LightTrackingHooks.trackLightUpdates(playerChunk, playerChunkMap, flags, lightType, dir.getOpposite());
        }
    }

    public static void initNeighborLightChecks(final Chunk chunk)
    {
        if (((IChunk)chunk).getNeighborLightChecks() == null)
        {
            ((IChunk)chunk).setNeighborLightChecks(new short[FLAG_COUNT]);
        }
    }

    static void writeNeighborLightChecksToNBT(final Chunk chunk, final NBTTagCompound nbt)
    {
        if (((IChunk)chunk).getNeighborLightChecks() == null)
        {
            return;
        }

        boolean empty = true;
        final NBTTagList list = new NBTTagList();

        for (final short flags : ((IChunk)chunk).getNeighborLightChecks())
        {
            list.appendTag(new NBTTagShort(flags));

            if (flags != 0)
            {
                empty = false;
            }
        }

        if (!empty)
        {
            nbt.setTag(neighborLightChecksKey, list);
        }
    }

    static void readNeighborLightChecksFromNBT(final Chunk chunk, final NBTTagCompound nbt)
    {
        if (nbt.hasKey(neighborLightChecksKey, 9))
        {
            final NBTTagList list = nbt.getTagList(neighborLightChecksKey, 2);

            if (list.tagCount() == FLAG_COUNT)
            {
                initNeighborLightChecks(chunk);

                for (int i = 0; i < FLAG_COUNT; ++i)
                {
                    ((IChunk)chunk).getNeighborLightChecks()[i] = ((NBTTagShort) list.get(i)).getShort();
                }
            }
            else
            {
                LOGGER.warn("Chunk field {} had invalid length, ignoring it (chunk coordinates: {} {})", neighborLightChecksKey, chunk.x, chunk.z);
            }
        }
    }

    public static int getFlagIndex(final EnumSkyBlock lightType, final EnumFacing dir, final AxisDirection axisDirection, final EnumBoundaryFacing boundaryFacing)
    {
        return getFlagIndex(lightType, dir.getFrontOffsetX(), dir.getFrontOffsetZ(), axisDirection, boundaryFacing);
    }

    public static int getFlagIndex(final EnumSkyBlock lightType, final int xOffset, final int zOffset, final AxisDirection axisDirection, final EnumBoundaryFacing boundaryFacing)
    {
        return (LightUtils.getIndex(lightType) << 4) | ((xOffset + 1) << 2) | ((zOffset + 1) << 1) | (axisDirection.getOffset() + 1) | boundaryFacing.ordinal();
    }
}
