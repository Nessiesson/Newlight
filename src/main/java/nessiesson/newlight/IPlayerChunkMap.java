package nessiesson.newlight;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMapEntry;

import java.util.List;

public interface IPlayerChunkMap {
    List<PlayerChunkMapEntry> getLightTrackingEntries();
    PlayerChunkMapEntry[] getNeighborChunksCache();
    List<EntityPlayerMP> getLightTickSyncPlayers();
}
