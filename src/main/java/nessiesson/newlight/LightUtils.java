package nessiesson.newlight;

import net.minecraft.util.EnumFacing.AxisDirection;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

public class LightUtils
{
    public static final EnumSkyBlock[] ENUM_SKY_BLOCK_VALUES = EnumSkyBlock.values();
    public static final AxisDirection[] ENUM_AXIS_DIRECTION_VALUES = AxisDirection.values();

    public static int getIndex(final EnumSkyBlock lightType)
    {
        return lightType == EnumSkyBlock.BLOCK ? 0 : 1;
    }

    public static void scheduleRelightChecksForArea(final World world, final EnumSkyBlock lightType, final int xMin, final int yMin, final int zMin, final int xMax, final int yMax, final int zMax, final MutableBlockPos pos)
    {
        for (int x = xMin; x <= xMax; ++x)
        {
            for (int z = zMin; z <= zMax; ++z)
            {
                scheduleRelightChecksForColumn(world, lightType, x, z, yMin, yMax, pos);
            }
        }
    }

    public static void scheduleRelightChecksForColumn(final World world, final EnumSkyBlock lightType, final int x, final int z, final int yMin, final int yMax, final MutableBlockPos pos)
    {
        for (int y = yMin; y <= yMax; ++y)
        {
            world.checkLightFor(lightType, pos.setPos(x, y, z));
        }
    }
}
