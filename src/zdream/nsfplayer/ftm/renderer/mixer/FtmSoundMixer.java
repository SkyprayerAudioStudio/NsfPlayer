package zdream.nsfplayer.ftm.renderer.mixer;

import java.util.HashMap;

import zdream.nsfplayer.ftm.renderer.FamiTrackerRuntime;
import zdream.nsfplayer.ftm.renderer.IFtmRuntimeHolder;
import zdream.nsfplayer.sound.buffer.BlipBuffer;
import zdream.nsfplayer.sound.buffer.BlipEQ;
import zdream.nsfplayer.sound.mixer.SoundMixer;

/**
 * Ftm 的音频合成器
 * @author Zdream
 * @since 0.2.1
 */
public class FtmSoundMixer extends SoundMixer implements IFtmRuntimeHolder {
	
	FamiTrackerRuntime runtime;

	public FtmSoundMixer(FamiTrackerRuntime runtime) {
		this.runtime = runtime;
	}

	@Override
	public FamiTrackerRuntime getRuntime() {
		return runtime;
	}

	@Override
	public void init() {
		final int sampleRate = runtime.setting.sampleRate;
		int size = sampleRate / runtime.setting.frameRate;
		
		buffer.setSampleRate(sampleRate, (size * 1000 * 2) / sampleRate);
		buffer.bassFreq(runtime.setting.bassFilter);
	}
	
	@Override
	public void reset() {
		super.reset();

		buffer.clockRate(runtime.param.freqPerSec);
	}
	
	/* **********
	 * 音频管道 *
	 ********** */
	
	/**
	 * 轨道号 - 音频管道
	 */
	HashMap<Byte, BlipMixerChannel> mixers = new HashMap<>();
	
	@Override
	public void detachAll() {
		mixers.clear();
	}
	
	@Override
	public BlipMixerChannel allocateChannel(byte code) {
		BlipMixerChannel c = new BlipMixerChannel(this);
		mixers.put(code, c);
		
		configMixChannel(code, c);
		c.synth.output(buffer);
		
		// EQ
		BlipEQ eq = new BlipEQ(-runtime.setting.trebleDamping, runtime.setting.trebleFilter,
				runtime.setting.sampleRate, 0);
		c.synth.trebleEq(eq);
		c.synth.volume(1.0);
		
		return c;
	}

	@Override
	public BlipMixerChannel getMixerChannel(byte code) {
		return mixers.get(code);
	}
	
	/**
	 * 配置音频轨道
	 * @param code
	 */
	private static void configMixChannel(byte code, BlipMixerChannel mixer) {
		switch (code) {
		case CHANNEL_2A03_PULSE1: case CHANNEL_2A03_PULSE2:
		{
			mixer.updateSetting(12, -500);
			mixer.setExpression((x) -> (x > 0) ? (int) (95.88 * 400 / ((8128.0 / x) + 156.0)) : 0);
		} break;
		
		case CHANNEL_2A03_TRIANGLE:
		{
			mixer.updateSetting(12, -500);
			mixer.setExpression((x) -> (x > 0) ? (int) (46159.29 / (1 / (x / 8227.0) + 30.0)) : 0);
		} break;
		
		case CHANNEL_2A03_NOISE:
		{
			mixer.updateSetting(12, -500);
			mixer.setExpression((x) -> (x > 0) ? (int) (41543.36 / (1 / (x / 12241.0) + 30.0)) : 0);
		} break;
		
		case CHANNEL_2A03_DPCM:
		{
			mixer.updateSetting(12, -500);
			mixer.setExpression((x) -> (x > 0) ? (int) (41543.36 / (1 / (x / 22638.0) + 30.0)) : 0);
		} break;
		
		case CHANNEL_MMC5_PULSE1: case CHANNEL_MMC5_PULSE2:
		case CHANNEL_VRC6_PULSE1: case CHANNEL_VRC6_PULSE2:
		case CHANNEL_VRC6_SAWTOOTH:
		{
			mixer.updateSetting(12, -500);
			mixer.setExpression((x) -> (x > 0) ? (int) (96 * 360 / ((8000.0 / x) + 180)) : 0);
		} break;
		
		default:
			break;
		}
		
	}
	
	/* **********
	 * 音频合成 *
	 ********** */
	
	/**
	 * 音频缓存
	 */
	BlipBuffer buffer = new BlipBuffer();

	@Override
	public int finishBuffer() {
		int freq = runtime.param.freqPerFrame;
		buffer.endFrame(freq);
		
		return buffer.samplesAvail();
	}
	
	@Override
	public int readBuffer(short[] buf, int offset, int length) {
		int ret = buffer.readSamples(buf, offset, length, false);
		
		// 这里为了避免 mixer 缓冲区的溢出, 用了一些方法
		buffer.removeSamples(buffer.samplesAvail());
		
		return ret;
	}

}
