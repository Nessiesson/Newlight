package nessiesson.newlight;

import nessiesson.newlight.mixins.IMixinEnumFacing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class LightInitHooks
{
    public static final String neighborLightInitsKey = "PendingNeighborLightInits";
    public static final int CHUNK_COORD_OVERFLOW_MASK = -1 << 4;

    public static void fillSkylightColumn(final Chunk chunk, final int x, final int z)
    {
        final ExtendedBlockStorage[] extendedBlockStorage = chunk.getBlockStorageArray();

        final int height = chunk.getHeightValue(x, z);

        for (int j = height >> 4; j < extendedBlockStorage.length; ++j)
        {
            final ExtendedBlockStorage blockStorage = extendedBlockStorage[j];

            if (blockStorage == Chunk.NULL_BLOCK_STORAGE)
                continue;

            final int yMin = Math.max(j << 4, height);

            for (int y = yMin & 15; y < 16; ++y)
                blockStorage.setSkyLight(x, y, z, EnumSkyBlock.SKY.defaultLightValue);
        }

        chunk.markDirty();
    }

    public static void initChunkLighting(final World world, final Chunk chunk)
    {
        if (chunk.isLightPopulated() || ((IChunk)chunk).getPendingNeighborLightInits() != 0)
            return;
        
        ((IChunk)chunk).setPendingNeighborLightInits((short)15);

        chunk.markDirty();

        final int xBase = chunk.x << 4;
        final int zBase = chunk.z << 4;

        final PooledMutableBlockPos pos = PooledMutableBlockPos.retain();

        final ExtendedBlockStorage[] extendedBlockStorage = chunk.getBlockStorageArray();

        for (int j = 0; j < extendedBlockStorage.length; ++j)
        {
            final ExtendedBlockStorage blockStorage = extendedBlockStorage[j];

            if (blockStorage == Chunk.NULL_BLOCK_STORAGE)
                continue;

            for (int x = 0; x < 16; ++x)
            {
                for (int z = 0; z < 16; ++z)
                {
                    for (int y = 0; y < 16; ++y)
                    {
                        if (blockStorage.get(x, y, z).getLightValue() > 0)
                            world.checkLightFor(EnumSkyBlock.BLOCK, pos);
                    }
                }
            }
        }

        if (world.provider.hasSkyLight())
        {
            for (int x = 0; x < 16; ++x)
            {
                for (int z = 0; z < 16; ++z)
                {
                    final int yMax = chunk.getHeightValue(x, z);
                    int yMin = Math.max(yMax - 1, 0);

                    for (final EnumFacing dir : IMixinEnumFacing.getHorizontals())
                    {
                        final int nX = x + dir.getFrontOffsetX();
                        final int nZ = z + dir.getFrontOffsetZ();

                        if (((nX | nZ) & CHUNK_COORD_OVERFLOW_MASK) != 0)
                            continue;

                        yMin = Math.min(yMin, chunk.getHeightValue(nX, nZ));
                    }

                    LightUtils.scheduleRelightChecksForColumn(world, EnumSkyBlock.SKY, xBase + x, zBase + z, yMin, yMax - 1, pos);
                }
            }
        }

        pos.release();
    }

    static void initNeighborLight(final World world, final Chunk chunk, final Chunk nChunk, final EnumFacing nDir)
    {
        final int flag = 1 << nDir.getHorizontalIndex();

        if ((((IChunk)chunk).getPendingNeighborLightInits() & flag) == 0)
            return;

        ((IChunk)chunk).setPendingNeighborLightInits( (short)(((IChunk)chunk).getPendingNeighborLightInits() ^ flag) );

        if (((IChunk)chunk).getPendingNeighborLightInits() == 0)
            chunk.setLightPopulated(true);

        chunk.markDirty();

        final int xOffset = nDir.getFrontOffsetX();
        final int zOffset = nDir.getFrontOffsetZ();

        final int xMin;
        final int zMin;

        if ((xOffset | zOffset) > 0)
        {
            xMin = 0;
            zMin = 0;
        }
        else
        {
            xMin = 15 * (xOffset & 1);
            zMin = 15 * (zOffset & 1);
        }

        final int xMax = xMin + 15 * (zOffset & 1);
        final int zMax = zMin + 15 * (xOffset & 1);

        final int xBase = nChunk.x << 4;
        final int zBase = nChunk.z << 4;

        final PooledMutableBlockPos pos = PooledMutableBlockPos.retain();

        for (int x = xMin; x <= xMax; ++x)
        {
            for (int z = zMin; z <= zMax; ++z)
            {
                int yMin = chunk.getHeightValue((x - xOffset) & 15, (z - zOffset) & 15);

                // Restore a value <= initial height
                for (; yMin > 0; --yMin)
                {
                    if (((IChunk)chunk).getCachedLightFor(EnumSkyBlock.SKY, pos.setPos(xBase + x - xOffset, yMin - 1, zBase + z - zOffset)) < EnumSkyBlock.SKY.defaultLightValue)
                        break;
                }

                int yMax = nChunk.getHeightValue(x, z) - 1;

                for (final EnumFacing dir : IMixinEnumFacing.getHorizontals())
                {
                    final int nX = x + dir.getFrontOffsetX();
                    final int nZ = z + dir.getFrontOffsetZ();

                    if (((nX | nZ) & CHUNK_COORD_OVERFLOW_MASK) != 0)
                        continue;

                    yMax = Math.min(yMax, nChunk.getHeightValue(nX, nZ));
                }

                LightUtils.scheduleRelightChecksForColumn(world, EnumSkyBlock.SKY, xBase + x, zBase + z, yMin, yMax - 1, pos);
            }
        }

        pos.release();
    }

    public static void initNeighborLight(final World world, final Chunk chunk)
    {
        final IChunkProvider provider = world.getChunkProvider();

        for (final EnumFacing dir : IMixinEnumFacing.getHorizontals())
        {
            final Chunk nChunk = provider.getLoadedChunk(chunk.x + dir.getFrontOffsetX(), chunk.z + dir.getFrontOffsetZ());

            if (nChunk == null)
                continue;

            initNeighborLight(world, chunk, nChunk, dir);
            initNeighborLight(world, nChunk, chunk, dir.getOpposite());
        }
    }

    static void writeNeighborInitsToNBT(final Chunk chunk, final NBTTagCompound nbt)
    {
        if (((IChunk)chunk).getPendingNeighborLightInits() != 0)
            nbt.setShort(neighborLightInitsKey, ((IChunk)chunk).getPendingNeighborLightInits());
    }

    static void readNeighborInitsFromNBT(final Chunk chunk, final NBTTagCompound nbt)
    {
        if (nbt.hasKey(neighborLightInitsKey, 2))
            ((IChunk)chunk).setPendingNeighborLightInits(nbt.getShort(neighborLightInitsKey));
    }
}
