package nessiesson.newlight.network;

/*

This is kind of awkward, but the goal is to maintain compatibility between the LiteLoader and Forge versions without
needing to modify LightTrackingHooks.java.

 */

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;

public class NewLightPacketHandler
{
    public static final NewLightPacketHandler INSTANCE = new NewLightPacketHandler();
    
    private NewLightPacketHandler()
    {
        
    }
    
    public void processSPacket(final PacketBuffer data)
    {
        switch (data.readByte())
        {
            case 0:
                SPacketLightTracking.handler(data);
                break;
            case 1:
                SPacketLightTickSync.handler(data);
                break;
        }
    }
    
    public void sendTo(final ICustomPayload packet, final EntityPlayerMP player)
    {
        player.connection.sendPacket(new SPacketCustomPayload("newlight", packet.toPacketBuffer()));
    }
}
