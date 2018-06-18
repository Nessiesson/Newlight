package nessiesson.newlight;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import nessiesson.newlight.mixins.IMixinEnumFacing;
import nessiesson.newlight.network.NewLightPacketHandler;
import nessiesson.newlight.network.SPacketLightTickSync;
import nessiesson.newlight.network.SPacketLightTracking;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class LightTrackingHooks
{
    private static final int VERTICAL_OFFSET = 2 * 4 * 16;
    private static final int CHUNK_COUNT_OFFSET = VERTICAL_OFFSET + 2 * 2 * 15;

    public static void trackLightUpdate(final PlayerChunkMapEntry targetChunk, final PlayerChunkMap chunkMap, final int y, final EnumSkyBlock lightType, final EnumFacing dir)
    {
        trackLightUpdates(targetChunk, chunkMap, 1 << (y >> 4), lightType, dir);
    }

    public static void trackLightUpdates(final PlayerChunkMapEntry targetChunk, final PlayerChunkMap chunkMap, int targetSectionMask, final EnumSkyBlock lightType, final EnumFacing dir)
    {
        final int offset = LightTrackingHooks.getOffset(dir, lightType);

        if (dir == EnumFacing.UP)
            targetSectionMask >>>= 1;

        final long updateMask = ((long) targetSectionMask << (offset & 63));

        ((IPlayerChunkMapEntry)targetChunk).getLightTrackingTick()[offset >> 6] |= updateMask;
        ((IPlayerChunkMapEntry)targetChunk).getLightTrackingAdd()[offset >> 6] |= updateMask;

        if (((IPlayerChunkMapEntry)targetChunk).getLightTrackingEmpty())
        {
            ((IPlayerChunkMapEntry)targetChunk).setLightTrackingEmpty(false);
            ((IPlayerChunkMap)chunkMap).getLightTrackingEntries().add(targetChunk);
        }
    }

    private static int getTrackingFlags(final long[] data, final EnumFacing dir, final EnumSkyBlock lightType)
    {
        final int offset = LightTrackingHooks.getOffset(dir, lightType);

        return (int) (data[offset >> 6] >> (offset & 63)) & ((1 << (dir.getAxis() == Axis.Y ? 15 : 16)) - 1);
    }

    private static int getOffset(final EnumFacing dir)
    {
        return dir.getAxis() == Axis.Y ? VERTICAL_OFFSET + dir.getIndex() * 30 : (dir.getHorizontalIndex() * 32);
    }

    private static int getOffset(final EnumFacing dir, final EnumSkyBlock lightType)
    {
        return dir.getAxis() == Axis.Y
            ? VERTICAL_OFFSET + dir.getIndex() * 30 + LightUtils.getIndex(lightType) * 15
            : (dir.getHorizontalIndex() * 32 + LightUtils.getIndex(lightType) * 16);
    }

    private static void clearIncomingTrackingData(final PlayerChunkMapEntry chunk, final int sectionMask)
    {
        clearTrackingData(((IPlayerChunkMapEntry)chunk).getLightTrackingTick(), sectionMask);
    }

    private static void clearOutgoingTrackingData(final PlayerChunkMap chunkMap, final int sectionMask)
    {
        clearTrackingData(((IPlayerChunkMap)chunkMap).getNeighborChunksCache(), sectionMask);
    }

    private static void clearTrackingData(final PlayerChunkMapEntry chunk, final PlayerChunkMap chunkMap, final int sectionMask)
    {
        clearIncomingTrackingData(chunk, sectionMask);
        clearOutgoingTrackingData(chunkMap, sectionMask);
    }

    private static void clearTrackingData(final PlayerChunkMapEntry[] chunks, final int sectionMask)
    {
        for (int i = 0; i < 6; ++i)
        {
            final PlayerChunkMapEntry chunk = chunks[i];

            if (chunk != null && !((IPlayerChunkMapEntry)chunk).getLightTrackingEmpty())
                clearTrackingData(IMixinEnumFacing.getValues()[i], ((IPlayerChunkMapEntry)chunk).getLightTrackingTick(), sectionMask);
        }
    }

    private static void clearTrackingData(final EntityPlayerMP player, final PlayerChunkMapEntry chunk, final int sectionMask)
    {
        final long[] data = ((IPlayerChunkMapEntry)chunk).getLightTrackingData().get(player);

        if (data == null)
            return;

        clearTrackingData(data, sectionMask);

        if (((IPlayerChunkMapEntry)chunk).getLightTrackingEmpty() && isTrackingTrivial(data))
            ((IPlayerChunkMapEntry)chunk).getLightTrackingData().remove(player);
    }

    private static void clearTrackingData(final long[] data, final int sectionMask)
    {
        for (final EnumFacing dir : IMixinEnumFacing.getValues())
            clearTrackingData(dir, data, sectionMask);
    }

    private static void clearTrackingData(final EnumFacing dir, final long[] data, int sectionMask)
    {
        final int offset = getOffset(dir);

        if (dir == EnumFacing.UP)
            sectionMask >>>= 1;
        else if (dir == EnumFacing.DOWN)
            sectionMask &= (1 << 15) - 1;

        final int shift = dir.getAxis() == Axis.Y ? 15 : 16;

        final long removeMask = (((long) sectionMask << shift) | (long) sectionMask) << (offset & 63);

        data[offset >> 6] &= ~removeMask;
    }

    private static void moveTrackingData(
        final long[] data,
        final EnumFacing dir,
        final EntityPlayerMP player,
        final PlayerChunkMapEntry neighborChunk,
        final int sectionMask
    )
    {
        final long[] neighborData = ((IPlayerChunkMapEntry)neighborChunk).getLightTrackingData().get(player);

        if (neighborData == null)
            copyTrackingData(dir, ((IPlayerChunkMapEntry)neighborChunk).getLightTrackingTick(), data, sectionMask, false);
        else
        {
            copyTrackingData(dir, neighborData, data, sectionMask, true);

            if (((IPlayerChunkMapEntry)neighborChunk).getLightTrackingEmpty() && isTrackingTrivial(neighborData))
                ((IPlayerChunkMapEntry)neighborChunk).getLightTrackingData().remove(player);
        }
    }

    private static void copyTrackingData(final EnumFacing dir, final long[] fromData, final long[] toData, int sectionMask, final boolean delete)
    {
        final int offset = getOffset(dir);

        if (dir == EnumFacing.DOWN)
            sectionMask >>>= 1;
        else if (dir == EnumFacing.UP)
            sectionMask &= (1 << 15) - 1;

        final int shift = dir.getAxis() == Axis.Y ? 15 : 16;

        final long copyMask = (((long) sectionMask << shift) | (long) sectionMask) << (offset & 63);

        toData[offset >> 6] |= fromData[offset >> 6] & copyMask;

        if (delete)
            fromData[offset >> 6] &= ~copyMask;
    }

    private static long[] collectTrackingData(final EntityPlayerMP player, final int sectionMask, final PlayerChunkMap chunkMap)
    {
        return collectTrackingData(player, sectionMask, ((IPlayerChunkMap)chunkMap).getNeighborChunksCache());
    }

    private static long[] collectTrackingData(final EntityPlayerMP player, final int sectionMask, final PlayerChunkMapEntry[] chunks)
    {
        final long[] data = new long[3];

        for (int i = 0; i < 6; ++i)
        {
            final PlayerChunkMapEntry chunk = chunks[i];

            if (chunk != null)
                moveTrackingData(data, IMixinEnumFacing.getValues()[i], player, chunk, sectionMask);
        }

        return data;
    }

    private static void prepareNeighborChunks(final PlayerChunkMapEntry chunk, final PlayerChunkMap chunkMap)
    {
        final ChunkPos pos = chunk.getPos();

        for (int i = 0; i < 6; ++i)
        {
            final EnumFacing dir = IMixinEnumFacing.getValues()[i];

            final PlayerChunkMapEntry neighborChunk = dir.getAxis() == Axis.Y
                ? chunk
                : chunkMap.getEntry(pos.x + dir.getFrontOffsetX(), pos.z + dir.getFrontOffsetZ());

            ((IPlayerChunkMap)chunkMap).getNeighborChunksCache()[i] = neighborChunk;

            if (neighborChunk != null)
                applyTrackings(neighborChunk);
        }
    }

    public static void onSendChunkToPlayers(final PlayerChunkMapEntry chunk, final PlayerChunkMap chunkMap, final List<EntityPlayerMP> players)
    {
        final int sectionMask = (1 << 16) - 1;

        prepareNeighborChunks(chunk, chunkMap);

        for (final EntityPlayerMP player : players)
        {
            clearTrackingData(player, chunk, sectionMask);
            final long[] data = collectTrackingData(player, sectionMask, chunkMap);

            if (!isTrackingTrivial(data))
                NewLightPacketHandler.INSTANCE.sendTo(new SPacketLightTracking(chunk.getPos(), data), player);
        }

        clearTrackingData(chunk, chunkMap, sectionMask);
        Arrays.fill(((IPlayerChunkMap)chunkMap).getNeighborChunksCache(), null);
    }

    public static void onSendChunkToPlayer(final EntityPlayerMP player, final PlayerChunkMapEntry chunk, final PlayerChunkMap chunkMap)
    {
        final int sectionMask = (1 << 16) - 1;

        prepareNeighborChunks(chunk, chunkMap);

        clearTrackingData(player, chunk, sectionMask);
        final long[] data = collectTrackingData(player, sectionMask, chunkMap);

        if (!isTrackingTrivial(data))
            NewLightPacketHandler.INSTANCE.sendTo(new SPacketLightTracking(chunk.getPos(), data), player);

        Arrays.fill(((IPlayerChunkMap)chunkMap).getNeighborChunksCache(), null);
    }

    public static void onUpdateChunk(final PlayerChunkMapEntry chunk, final int sectionMask)
    {
        ((IPlayerChunkMapEntry)chunk).setLightTrackingSectionMask(((IPlayerChunkMapEntry)chunk).getLightTrackingSectionMask() | sectionMask);
        applyTrackings(chunk);

        clearIncomingTrackingData(chunk, sectionMask);

        for (final Iterator<long[]> it = ((IPlayerChunkMapEntry)chunk).getLightTrackingData().values().iterator(); it.hasNext(); )
        {
            final long[] data = it.next();

            clearTrackingData(data, sectionMask);

            if (((IPlayerChunkMapEntry)chunk).getLightTrackingEmpty() && isTrackingTrivial(data))
                it.remove();
        }
    }

    public static void onChunkMapTick(final PlayerChunkMap chunkMap, final Collection<PlayerChunkMapEntry> chunks)
    {
        for (final PlayerChunkMapEntry chunk : chunks)
        {
            if (((IPlayerChunkMapEntry)chunk).getLightTrackingSectionMask() == 0)
                continue;

            prepareNeighborChunks(chunk, chunkMap);

            for (final EntityPlayerMP player : ((IPlayerChunkMapEntry)chunk).getPlayers())
            {
                final long[] data = collectTrackingData(player, ((IPlayerChunkMapEntry)chunk).getLightTrackingSectionMask(), chunkMap);

                if (!isTrackingTrivial(data))
                    NewLightPacketHandler.INSTANCE.sendTo(new SPacketLightTracking(chunk.getPos(), data), player);
            }

            clearOutgoingTrackingData(chunkMap, ((IPlayerChunkMapEntry)chunk).getLightTrackingSectionMask());
            ((IPlayerChunkMapEntry)chunk).setLightTrackingSectionMask(0);
        }

        Arrays.fill(((IPlayerChunkMap)chunkMap).getNeighborChunksCache(), null);
    }

    private static void addMissingNeighbors(final long[] data, final int num)
    {
        data[CHUNK_COUNT_OFFSET >> 6] += ((long) num << (CHUNK_COUNT_OFFSET & 63));
    }

    private static boolean isTrackingTrivial(final long[] data)
    {
        for (final long l : data)
            if (l != 0)
                return false;

        return true;
    }

    private static long[] createTrackingData(final int missingNeighbors)
    {
        final long[] data = new long[3];
        addMissingNeighbors(data, missingNeighbors);
        return data;
    }

    private static boolean canPlayerSeeChunk(final EntityPlayerMP player, final PlayerChunkMapEntry chunk)
    {
        return chunk.isSentToPlayers() && chunk.containsPlayer(player);
    }

    public static void addPlayer(final EntityPlayerMP player, final PlayerChunkMapEntry chunk, final PlayerChunkMap chunkMap)
    {
        if (!chunk.isSentToPlayers())
            return;

        int neighborCount = 0;

        final ChunkPos pos = chunk.getPos();

        for (int x = -1; x <= 1; ++x)
        {
            for (int z = -1; z <= 1; ++z)
            {
                if (x == 0 && z == 0)
                    continue;

                final PlayerChunkMapEntry neighborChunk = chunkMap.getEntry(pos.x + x, pos.z + z);

                if (neighborChunk != null && canPlayerSeeChunk(player, neighborChunk))
                {
                    ++neighborCount;
                    addNeighbor(player, neighborChunk);
                }
            }
        }

        if (neighborCount < 8 || !((IPlayerChunkMapEntry)chunk).getLightTrackingEmpty())
            addTrackingEntry(player, chunk, 8 - neighborCount);
    }

    private static void addNeighbor(final EntityPlayerMP player, final PlayerChunkMapEntry chunk)
    {
        final long[] data = ((IPlayerChunkMapEntry)chunk).getLightTrackingData().get(player);

        if (data == null)
            return;

        addMissingNeighbors(data, -1);

        if (((IPlayerChunkMapEntry)chunk).getLightTrackingEmpty() && isTrackingTrivial(data))
            ((IPlayerChunkMapEntry)chunk).getLightTrackingData().remove(player);
    }

    public static void removePlayer(final EntityPlayerMP player, final PlayerChunkMapEntry chunk, final PlayerChunkMap chunkMap)
    {
        if (!chunk.isSentToPlayers())
            return;

        final ChunkPos pos = chunk.getPos();

        for (int x = -1; x <= 1; ++x)
        {
            for (int z = -1; z <= 1; ++z)
            {
                if (x == 0 && z == 0)
                    continue;

                final PlayerChunkMapEntry neighborChunk = chunkMap.getEntry(pos.x + x, pos.z + z);

                if (neighborChunk != null)
                    removeNeighbor(player, neighborChunk);
            }
        }

        ((IPlayerChunkMapEntry)chunk).getLightTrackingData().remove(player);
    }

    private static void removeNeighbor(final EntityPlayerMP player, final PlayerChunkMapEntry chunk)
    {
        long[] data = ((IPlayerChunkMapEntry)chunk).getLightTrackingData().get(player);

        if (data == null)
        {
            if (!canPlayerSeeChunk(player, chunk))
                return;

            data = createTrackingData(1);
            for (int i = 0; i < data.length; ++i)
                data[i] |= ((IPlayerChunkMapEntry)chunk).getLightTrackingTick()[i];

            addTrackingEntry(player, chunk, data);
        }
        else
            addMissingNeighbors(data, 1);
    }

    public static void tick(final PlayerChunkMap chunkMap)
    {
        for (final PlayerChunkMapEntry chunk : ((IPlayerChunkMap)chunkMap).getLightTrackingEntries())
        {
            for (final EntityPlayerMP player : ((IPlayerChunkMapEntry)chunk).getPlayers())
            {
                if (!((IEntityPlayerMP)player).getNeedsLightTickSync())
                {
                    ((IEntityPlayerMP)player).setNeedsLightTickSync(true);
                    ((IPlayerChunkMap)chunkMap).getLightTickSyncPlayers().add(player);
                }
            }

            cleanupTrackingTick(chunk);
        }

        for (final EntityPlayerMP player : ((IPlayerChunkMap)chunkMap).getLightTickSyncPlayers())
        {
            ((IEntityPlayerMP)player).setNeedsLightTickSync(false);
            NewLightPacketHandler.INSTANCE.sendTo(SPacketLightTickSync.INSTANCE, player);
        }

        ((IPlayerChunkMap)chunkMap).getLightTickSyncPlayers().clear();

        ((IPlayerChunkMap)chunkMap).getLightTrackingEntries().clear();
    }

    private static void cleanupTrackingTick(final PlayerChunkMapEntry chunk)
    {
        if (((IPlayerChunkMapEntry)chunk).getLightTrackingEmpty())
            return;

        ((IPlayerChunkMapEntry)chunk).setLightTrackingEmpty(true);

        applyTrackings(chunk);

        for (final Iterator<long[]> it = ((IPlayerChunkMapEntry)chunk).getLightTrackingData().values().iterator(); it.hasNext(); )
            if (isTrackingTrivial(it.next()))
                it.remove();

        Arrays.fill(((IPlayerChunkMapEntry)chunk).getLightTrackingTick(), 0);
    }

    private static void addTrackingEntry(final EntityPlayerMP player, final PlayerChunkMapEntry chunk, final int missingNeighbors)
    {
        addTrackingEntry(player, chunk, createTrackingData(missingNeighbors));
    }

    private static void addTrackingEntry(final EntityPlayerMP player, final PlayerChunkMapEntry chunk, final long[] data)
    {
        applyTrackings(chunk);
        ((IPlayerChunkMapEntry)chunk).getLightTrackingData().put(player, data);
    }

    private static void applyTrackings(final PlayerChunkMapEntry chunk)
    {
        if (((IPlayerChunkMapEntry)chunk).getLightTrackingEmpty() || isTrackingTrivial(((IPlayerChunkMapEntry)chunk).getLightTrackingAdd()))
            return;

        for (final long[] data : ((IPlayerChunkMapEntry)chunk).getLightTrackingData().values())
        {
            for (int i = 0; i < data.length; ++i)
                data[i] |= ((IPlayerChunkMapEntry)chunk).getLightTrackingAdd()[i];
        }

        Arrays.fill(((IPlayerChunkMapEntry)chunk).getLightTrackingAdd(), 0);
    }

    public static void scheduleChecksForSectionBoundaries(final World world, final int chunkX, final int chunkZ, final long[] data)
    {
        final PooledMutableBlockPos pos = PooledMutableBlockPos.retain();

        for (final EnumFacing dir : IMixinEnumFacing.getHorizontals())
        {
            for (final EnumSkyBlock lightType : LightUtils.ENUM_SKY_BLOCK_VALUES)
            {
                final int sectionMask = getTrackingFlags(data, dir, lightType);

                if (sectionMask == 0)
                    continue;

                final int xOffset = dir.getFrontOffsetX();
                final int zOffset = dir.getFrontOffsetZ();

                final Chunk nChunk = world.getChunkProvider().getLoadedChunk(chunkX + xOffset, chunkZ + zOffset);

                if (nChunk == null)
                    continue;

                final int xBase = nChunk.x << 4;
                final int zBase = nChunk.z << 4;

                int xMin = xBase;
                int zMin = zBase;

                if ((xOffset | zOffset) < 0)
                {
                    xMin += 15 * (xOffset & 1);
                    zMin += 15 * (zOffset & 1);
                }

                final int xMax = xMin + 15 * (zOffset & 1);
                final int zMax = zMin + 15 * (xOffset & 1);

                for (int y = 0; y < 16; ++y)
                {
                    if ((sectionMask & (1 << y)) != 0)
                    {
                        final int yMin = y << 4;

                        LightUtils.scheduleRelightChecksForArea(world, lightType, xMin, yMin, zMin, xMax, yMin + 15, zMax, pos);
                    }
                }
            }
        }

        for (final EnumSkyBlock lightType : LightUtils.ENUM_SKY_BLOCK_VALUES)
        {
            final int xBase = chunkX << 4;
            final int zBase = chunkZ << 4;

            final int upSectionMask = getTrackingFlags(data, EnumFacing.UP, lightType);
            final int downSectionMask = getTrackingFlags(data, EnumFacing.DOWN, lightType);

            if ((upSectionMask | downSectionMask) == 0)
                continue;

            for (int y = 0; y < 15; ++y)
            {
                final int yBase = y << 4;

                if ((upSectionMask & (1 << y)) != 0)
                    LightUtils.scheduleRelightChecksForArea(world, lightType, xBase, yBase + 16, zBase, xBase + 15, yBase + 16, zBase + 15, pos);

                if ((downSectionMask & (1 << y)) != 0)
                    LightUtils.scheduleRelightChecksForArea(world, lightType, xBase, yBase + 15, zBase, xBase + 15, yBase + 15, zBase + 15, pos);
            }
        }

        pos.release();
    }
}
