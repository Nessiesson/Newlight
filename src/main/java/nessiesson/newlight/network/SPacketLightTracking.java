package nessiesson.newlight.network;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.ChunkPos;
import nessiesson.newlight.LightTrackingHooks;

public class SPacketLightTracking implements ICustomPayload
{
    public int chunkX;
    public int chunkZ;
    public long[] data;

    public SPacketLightTracking(final PacketBuffer buf)
    {
        fromPacketBuffer(buf);
    }

    public SPacketLightTracking(final int chunkX, final int chunkZ, final long[] data)
    {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.data = data;
    }

    public SPacketLightTracking(final ChunkPos pos, final long[] data)
    {
        this(pos.x, pos.z, data);
    }

    public PacketBuffer toPacketBuffer() {
        final PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        
        buf.writeByte(0);
        
        buf.writeInt(this.chunkX).writeInt(this.chunkZ);

        for (final long l : this.data)
            buf.writeLong(l);
        
        return buf;
    }

    public void fromPacketBuffer(final PacketBuffer buf)
    {
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();

        this.data = new long[3];

        for (int i = 0; i < 3; ++i)
            this.data[i] = buf.readLong();
    }

    public static void handler(final PacketBuffer buf)
    {
        SPacketLightTracking message = new SPacketLightTracking(buf);
        Minecraft.getMinecraft().addScheduledTask(
            () -> LightTrackingHooks.scheduleChecksForSectionBoundaries(Minecraft.getMinecraft().world, message.chunkX, message.chunkZ, message.data)
        );
    }
}
