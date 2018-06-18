package nessiesson.newlight;

import com.google.common.collect.ImmutableList;
import com.mumfrey.liteloader.PluginChannelListener;
import com.mumfrey.liteloader.Tickable;
import nessiesson.newlight.network.NewLightPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;

import java.io.File;
import java.util.List;

public class LiteModNewLight implements Tickable, PluginChannelListener {

	@Override
	public void onTick(Minecraft minecraft, float partialTicks, boolean inGame, boolean clock) {
	}

	@Override
	public String getVersion() {
		return "@VERSION@";
	}

	@Override
	public void init(File configPath) {
	}

	@Override
	public void upgradeSettings(String version, File configPath, File oldConfigPath) {
	}

	@Override
	public String getName() {
		return "@NAME@";
	}

	@Override
	public void onCustomPayload(String channel, PacketBuffer data) {
		if ("newlight".equals(channel)) {
			NewLightPacketHandler.INSTANCE.processSPacket(data);
		}
	}

	@Override
	public List<String> getChannels() {
		return ImmutableList.of("newlight");
	}
}