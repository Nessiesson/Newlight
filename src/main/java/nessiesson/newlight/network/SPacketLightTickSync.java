package nessiesson.newlight.network;

import io.netty.buffer.Unpooled;
import nessiesson.newlight.IWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;

public class SPacketLightTickSync implements ICustomPayload
{
    public static final SPacketLightTickSync INSTANCE = new SPacketLightTickSync();
    
    public SPacketLightTickSync()
    {
        
    }

    public PacketBuffer toPacketBuffer()
    {
        final PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        buf.writeByte(1);
        
        return buf;
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
