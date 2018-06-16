package nessiesson.newlight.network;

import net.minecraft.network.PacketBuffer;

public interface ICustomPayload {
    PacketBuffer toPacketBuffer();
    void fromPacketBuffer(final PacketBuffer buf);
    static void handler(final PacketBuffer buf) { }
}
