package nessiesson.newlight.mixins;

import nessiesson.newlight.IWorld;
import nessiesson.newlight.LightingEngine;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class MixinWorld implements IWorld {
	@Final
	@Mutable
	private LightingEngine lightingEngine;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void onInit(ISaveHandler saveHandlerIn, WorldInfo info, WorldProvider providerIn, Profiler profilerIn, boolean client, CallbackInfo ci) {
		this.lightingEngine = new LightingEngine((World) (Object) this);
	}

	@Inject(method = "checkLightFor", at = @At("HEAD"), cancellable = true)
	private void onCheckLightFor(EnumSkyBlock lightType, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		cir.cancel();
		this.getLightingEngine().scheduleLightUpdate(lightType, pos);
		cir.setReturnValue(true);
	}

	public final LightingEngine getLightingEngine() {
		return this.lightingEngine;
	}
}
