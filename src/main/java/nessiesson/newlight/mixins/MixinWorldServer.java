package nessiesson.newlight.mixins;

import nessiesson.newlight.IWorld;
import nessiesson.newlight.LightingEngine;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldServer.class)
public abstract class MixinWorldServer extends World {
	@Final
	@Shadow
	private PlayerChunkMap playerChunkMap;
	
	protected MixinWorldServer(ISaveHandler saveHandlerIn, WorldInfo info, WorldProvider providerIn, Profiler profilerIn, boolean client) {
		super(saveHandlerIn, info, providerIn, profilerIn, client);
	}
	
	@Inject(method = "<init>", at = @At("RETURN"))
	private void initLightingChunkMap(CallbackInfo ci)
	{
		((IWorld)this).getLightingEngine().setPlayerChunkMap(this.playerChunkMap);
	}

	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;sendQueuedBlockEvents()V"))
	private void onTick(CallbackInfo ci) {
		this.profiler.startSection("lighting");
		((IWorld)this).getLightingEngine().procLightUpdates();
		this.profiler.startSection("tracking");
		nessiesson.newlight.LightTrackingHooks.tick(this.playerChunkMap);
		this.profiler.endSection();
		this.profiler.endSection();
	}
}
