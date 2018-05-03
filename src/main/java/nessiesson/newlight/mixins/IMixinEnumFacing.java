package nessiesson.newlight.mixins;

import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnumFacing.class)
public interface IMixinEnumFacing {
	@Accessor("VALUES")
	static EnumFacing[] getValues() {
		return null;
	}

	@Accessor("HORIZONTALS")
	static EnumFacing[] getHorizontals() {
		return null;
	}
}