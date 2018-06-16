package nessiesson.newlight.mixins;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import nessiesson.newlight.IPlayerChunkMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

@Mixin(PlayerChunkMap.class)
public abstract class MixinPlayerChunkMap implements IPlayerChunkMap {
    @Final
    @Shadow
    private Set<PlayerChunkMapEntry> dirtyEntries;

    private final List<PlayerChunkMapEntry> lightTrackingEntries = Lists.<PlayerChunkMapEntry>newArrayList();
    private final PlayerChunkMapEntry[] neighborChunksCache = new PlayerChunkMapEntry[6];
    private final List<EntityPlayerMP> lightTickSyncPlayers = Lists.newArrayList();

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Ljava/util/Set;clear()V", ordinal = 0))
    private void callOnChunkMapTick(CallbackInfo ci)
    {
        nessiesson.newlight.LightTrackingHooks.onChunkMapTick((PlayerChunkMap)(Object)this, this.dirtyEntries);
    }

    public List<PlayerChunkMapEntry> getLightTrackingEntries() { return lightTrackingEntries; }
    public PlayerChunkMapEntry[] getNeighborChunksCache() { return neighborChunksCache; }
    public List<EntityPlayerMP> getLightTickSyncPlayers() { return lightTickSyncPlayers; }
}
