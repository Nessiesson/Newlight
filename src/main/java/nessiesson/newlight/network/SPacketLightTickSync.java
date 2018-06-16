package nessiesson.newlight.network;

import io.netty.buffer.Unpooled;
import nessiesson.newlight.IWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;

public class SPacketLightTickSync implements ICustomPayload
{
    private final PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
    public static final SPacketLightTickSync INSTANCE = new SPacketLightTickSync();
    
    public SPacketLightTickSync()
    {
        this.buf.writeByte(1);
    }

    public PacketBuffer toPacketBuffer() {
        return this.buf;
    }

    public void fromPacketBuffer(final PacketBuffer buf)
    {
        // Packet contains no data
    }

    public static void handler(final PacketBuffer message)
    {
        Minecraft.getMinecraft().addScheduledTask(() -> ((IWorld)Minecraft.getMinecraft().world).getLightingEngine().procLightUpdates());
    }
}
