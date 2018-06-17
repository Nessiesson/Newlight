package nessiesson.newlight;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;

public interface IChunk {
	int[] getNeighborLightChecks();

	void setNeighborLightChecks(int[] in);

	short getPendingNeighborLightInits();

	void setPendingNeighborLightInits(short in);

	int getCachedLightFor(EnumSkyBlock type, BlockPos pos);
}
