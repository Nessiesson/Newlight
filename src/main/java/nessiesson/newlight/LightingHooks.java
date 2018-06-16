/*
 * Minecraft Forge
 * Copyright (c) 2016.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package nessiesson.newlight;

import javax.annotation.Nullable;

import nessiesson.newlight.mixins.IMixinEnumFacing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class LightingHooks
{
    public static void onLoad(final World world, final Chunk chunk)
    {
        LightInitHooks.initChunkLighting(world, chunk);
        LightInitHooks.initNeighborLight(world, chunk);
        LightBoundaryCheckHooks.scheduleRelightChecksForChunkBoundaries(world, chunk);
    }

    public static void writeLightData(final Chunk chunk, final NBTTagCompound nbt)
    {
        LightInitHooks.writeNeighborInitsToNBT(chunk, nbt);
        LightBoundaryCheckHooks.writeNeighborLightChecksToNBT(chunk, nbt);
    }

    public static void readLightData(final Chunk chunk, final NBTTagCompound nbt)
    {
        LightInitHooks.readNeighborInitsFromNBT(chunk, nbt);
        LightBoundaryCheckHooks.readNeighborLightChecksFromNBT(chunk, nbt);
    }

    public static void initSkylightForSection(final World world, final Chunk chunk, final ExtendedBlockStorage section)
    {
        if (world.provider.hasSkyLight())
        {
            for (int x = 0; x < 16; ++x)
            {
                for (int z = 0; z < 16; ++z)
                {
                    if (chunk.getHeightValue(x, z) <= section.getYLocation())
                    {
                        for (int y = 0; y < 16; ++y)
                        {
                            section.setSkyLight(x, y, z, EnumSkyBlock.SKY.defaultLightValue);
                        }
                    }
                }
            }
        }
    }

    public static void relightSkylightColumns(final World world, final Chunk chunk, @Nullable final int[] oldHeightMap)
    {
        if (!world.provider.hasSkyLight())
            return;

        if (oldHeightMap == null)
            return;

        final PooledMutableBlockPos pos = PooledMutableBlockPos.retain();

        for (int x = 0; x < 16; ++x)
        {
            for (int z = 0; z < 16; ++z)
                relightSkylightColumn(world, chunk, x, z, oldHeightMap[z << 4 | x], chunk.getHeightValue(x, z), pos);
        }

        pos.release();
    }

    public static void relightSkylightColumn(final World world, final Chunk chunk, final int x, final int z, final int height1, final int height2)
    {
        final PooledMutableBlockPos pos = PooledMutableBlockPos.retain();
        relightSkylightColumn(world, chunk, x, z, height1, height2, pos);
        pos.release();
    }

    private static void relightSkylightColumn(final World world, final Chunk chunk, final int x, final int z, final int height1, final int height2, final MutableBlockPos pos)
    {
        final int yMin = Math.min(height1, height2);
        final int yMax = Math.max(height1, height2) - 1;

        final ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();

        final int xBase = (chunk.x << 4) + x;
        final int zBase = (chunk.z << 4) + z;

        LightUtils.scheduleRelightChecksForColumn(world, EnumSkyBlock.SKY, xBase, zBase, yMin, yMax, pos);

        if (sections[yMin >> 4] == Chunk.NULL_BLOCK_STORAGE && yMin > 0)
        {
            world.checkLightFor(EnumSkyBlock.SKY, new BlockPos(xBase, yMin - 1, zBase));
        }

        short emptySections = 0;

        for (int sec = yMax >> 4; sec >= yMin >> 4; --sec)
        {
            if (sections[sec] == Chunk.NULL_BLOCK_STORAGE)
            {
                emptySections |= 1 << sec;
            }
        }

        if (emptySections != 0)
        {
            for (final EnumFacing dir : IMixinEnumFacing.getHorizontals())
            {
                final int xOffset = dir.getFrontOffsetX();
                final int zOffset = dir.getFrontOffsetZ();

                final boolean neighborColumnExists =
                    (((x + xOffset) | (z + zOffset)) & 16) == 0 //Checks whether the position is at the specified border (the 16 bit is set for both 15+1 and 0-1)
                        || world.getChunkProvider().getLoadedChunk(chunk.x + xOffset, chunk.z + zOffset) != null;

                if (neighborColumnExists)
                {
                    for (int sec = yMax >> 4; sec >= yMin >> 4; --sec)
                    {
                        if ((emptySections & (1 << sec)) != 0)
                        {
                            LightUtils.scheduleRelightChecksForColumn(world, EnumSkyBlock.SKY, xBase + xOffset, zBase + zOffset, sec << 4, (sec << 4) + 15, pos);
                        }
                    }
                }
                else
                {
                    LightBoundaryCheckHooks.flagChunkBoundaryForUpdate(chunk, emptySections, EnumSkyBlock.SKY, dir, LightUtils.getAxisDirection(dir, x, z), LightUtils.EnumBoundaryFacing.OUT);
                }
            }
        }
    }
}
