package nessiesson.newlight.mixins;

import com.google.common.collect.Lists;
import nessiesson.newlight.IPlayerChunkMapEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.List;

@Mixin(PlayerChunkMapEntry.class)
public abstract class MixinPlayerChunkMapEntry implements IPlayerChunkMapEntry {
    @Final
    @Shadow
    private List<EntityPlayerMP> players;

    @Final
    @Shadow
    private PlayerChunkMap playerChunkMap;
    
    @Shadow
    private int changedSectionFilter;

    private final java.util.Map<EntityPlayerMP, long[]> lightTrackingData = new java.util.HashMap<>();
    private long[] lightTrackingTick = new long[3];
    private long[] lightTrackingAdd = new long[3];
    private boolean lightTrackingEmpty = true;
    private int lightTrackingSectionMask;

    @Inject(method = "addPlayer", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void callAddPlayer(EntityPlayerMP player, CallbackInfo ci)
    {
        nessiesson.newlight.LightTrackingHooks.addPlayer(player, (PlayerChunkMapEntry)(Object)this, this.playerChunkMap);
    }
    
    @Inject(method = "removePlayer", at = @At(value = "INVOKE", target = "Ljava/util/List;remove(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void callRemovePlayer(EntityPlayerMP player, CallbackInfo ci)
    {
        nessiesson.newlight.LightTrackingHooks.removePlayer(player, (PlayerChunkMapEntry)(Object)this, this.playerChunkMap);
    }
    
    @Inject(method = "sendToPlayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void callAddPlayer2(CallbackInfoReturnable<Boolean> cir, Packet packet, Iterator var2, EntityPlayerMP entityplayermp)
    {
        nessiesson.newlight.LightTrackingHooks.addPlayer(entityplayermp, (PlayerChunkMapEntry)(Object)this, this.playerChunkMap);
    }

    @Inject(method = "sendToPlayers", at = @At(value = "RETURN", ordinal = 3))
    private void callOnSendChunkToPlayers(CallbackInfoReturnable ci)
    {
        nessiesson.newlight.LightTrackingHooks.onSendChunkToPlayers((PlayerChunkMapEntry)(Object)this, this.playerChunkMap, this.players);
    }
    
    @Inject(method = "sendToPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityTracker;sendLeashedEntitiesInChunk(Lnet/minecraft/entity/player/EntityPlayerMP;Lnet/minecraft/world/chunk/Chunk;)V", shift = At.Shift.AFTER))
    private void callOnSendChunkToPlayer(EntityPlayerMP player, CallbackInfo ci)
    {
        nessiesson.newlight.LightTrackingHooks.onSendChunkToPlayer(player, (PlayerChunkMapEntry)(Object)this, this.playerChunkMap);
    }
    
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/management/PlayerChunkMapEntry;sendPacket(Lnet/minecraft/network/Packet;)V", shift = At.Shift.AFTER, ordinal = 1))
    private void callOnUpdateChunk(CallbackInfo ci)
    {
        nessiesson.newlight.LightTrackingHooks.onUpdateChunk((PlayerChunkMapEntry)(Object)this, this.changedSectionFilter);
    }
    
    public java.util.Map<EntityPlayerMP, long[]> getLightTrackingData() { return this.lightTrackingData; }
    public long[] getLightTrackingTick() { return this.lightTrackingTick; }
    public long[] getLightTrackingAdd() { return this.lightTrackingAdd; }
    public List<EntityPlayerMP> getPlayers() { return this.players; }

    public boolean getLightTrackingEmpty() { return this.lightTrackingEmpty; }
    public int getLightTrackingSectionMask() { return this.lightTrackingSectionMask; }

    public void setLightTrackingEmpty(boolean in) { this.lightTrackingEmpty = in; }
    public void setLightTrackingSectionMask(int in) { this.lightTrackingSectionMask = in; }
}
