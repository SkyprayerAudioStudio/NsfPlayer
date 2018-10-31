package zdream.nsfplayer.ftm.renderer.tools;

import zdream.nsfplayer.core.INsfChannelCode;
import zdream.nsfplayer.ftm.renderer.AbstractFtmChannel;
import zdream.nsfplayer.ftm.renderer.TestFtmChannel;
import zdream.nsfplayer.ftm.renderer.channel.ChannelFDS;
import zdream.nsfplayer.ftm.renderer.channel.ChannelMMC5Pulse;
import zdream.nsfplayer.ftm.renderer.channel.ChannelVRC6Pulse;
import zdream.nsfplayer.ftm.renderer.channel.ChannelVRC6Sawtooth;
import zdream.nsfplayer.ftm.renderer.channel.DPCMChannel;
import zdream.nsfplayer.ftm.renderer.channel.NoiseChannel;
import zdream.nsfplayer.ftm.renderer.channel.Square1Channel;
import zdream.nsfplayer.ftm.renderer.channel.Square2Channel;
import zdream.nsfplayer.ftm.renderer.channel.TriangleChannel;

/**
 * <p>轨道设备的选择工具
 * <p>原来名称为 <code>zdream.nsfplayer.ftm.renderer.channel.ChannalFactory</code>
 * 后来发现对于每个不同的轨道, 不仅是 Ftm 轨道, 还有发声器、音频轨道等都不相同,
 * 因此将其功能加强, 对每个不同的轨道选择各种设备和类.
 * </p>
 * 
 * @author Zdream
 * @since 0.2.1
 */
public class ChannalDeviceSelector implements INsfChannelCode {

	/**
	 * 建立各个轨道
	 * @param code
	 *   轨道号
	 * @return
	 */
	public static AbstractFtmChannel selectFtmChannel(byte code) {
		switch (code) {
		// 2A03
		case CHANNEL_2A03_PULSE1: {
			Square1Channel s = new Square1Channel();
			return s;
		}
		case CHANNEL_2A03_PULSE2: {
			Square2Channel s = new Square2Channel();
			return s;
		}
		case CHANNEL_2A03_TRIANGLE: {
			TriangleChannel s = new TriangleChannel();
			return s;
		}
		case CHANNEL_2A03_NOISE: {
			NoiseChannel s = new NoiseChannel();
			return s;
		}
		case CHANNEL_2A03_DPCM:
			DPCMChannel s = new DPCMChannel();
			return s;
			
		// VRC6
		case CHANNEL_VRC6_PULSE1: {
			return new ChannelVRC6Pulse(true);
		}
		case CHANNEL_VRC6_PULSE2: {
			return new ChannelVRC6Pulse(false);
		}
		case CHANNEL_VRC6_SAWTOOTH: {
			return new ChannelVRC6Sawtooth();
		}
			
		// MMC5
		case CHANNEL_MMC5_PULSE1: {
			return new ChannelMMC5Pulse(true);
		}
		case CHANNEL_MMC5_PULSE2: {
			return new ChannelMMC5Pulse(false);
		}
		
		// FDS
		case CHANNEL_FDS: {
			return new ChannelFDS();
		}

		default:
			break;
		}
		
		return new TestFtmChannel(code);
	}

}
