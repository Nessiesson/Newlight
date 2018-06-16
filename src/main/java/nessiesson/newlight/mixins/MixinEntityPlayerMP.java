package nessiesson.newlight.mixins;

import nessiesson.newlight.IEntityPlayerMP;
import net.minecraft.entity.player.EntityPlayerMP;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityPlayerMP.class)
public abstract class MixinEntityPlayerMP implements IEntityPlayerMP {
    private boolean needsLightTickSync;

    public boolean getNeedsLightTickSync() { return this.needsLightTickSync; }
    public void setNeedsLightTickSync(boolean in) { this.needsLightTickSync = in; }
}
