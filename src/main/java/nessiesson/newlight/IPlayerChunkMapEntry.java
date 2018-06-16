package nessiesson.newlight;

import net.minecraft.entity.player.EntityPlayerMP;

import java.util.List;

public interface IPlayerChunkMapEntry {
    java.util.Map<EntityPlayerMP, long[]> getLightTrackingData();
    long[] getLightTrackingTick();
    long[] getLightTrackingAdd();
    List<EntityPlayerMP> getPlayers();

    boolean getLightTrackingEmpty();
    int getLightTrackingSectionMask();

    void setLightTrackingEmpty(boolean in);
    void setLightTrackingSectionMask(int in);
}
