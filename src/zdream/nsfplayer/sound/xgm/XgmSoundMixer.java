package zdream.nsfplayer.sound.xgm;

import static zdream.nsfplayer.core.NsfChannelCode.chipOfChannel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import zdream.nsfplayer.core.CycleCounter;
import zdream.nsfplayer.core.NsfCommonParameter;
import zdream.nsfplayer.sound.interceptor.Amplifier;
import zdream.nsfplayer.sound.interceptor.Compressor;
import zdream.nsfplayer.sound.interceptor.DCFilter;
import zdream.nsfplayer.sound.interceptor.EchoUnit;
import zdream.nsfplayer.sound.interceptor.Filter;
import zdream.nsfplayer.sound.interceptor.ISoundInterceptor;
import zdream.nsfplayer.sound.mixer.IMixerChannel;
import zdream.nsfplayer.sound.mixer.SoundMixer;

/**
 * Nsf 的音频合成器
 * 
 * @author Zdream
 * @since v0.2.1
 */
public class XgmSoundMixer extends SoundMixer {
	
	public NsfCommonParameter param;

	public XgmSoundMixer() {
		
	}
	
	@Override
	public void init() {
		initInterceptors();
	}

	/**
	 * 设置配置项
	 * @param config
	 *   配置项数据
	 * @since v0.2.5
	 */
	public void setConfig(XgmMixerConfig config) {
		
	}
	
	/* **********
	 * 音频管道 *
	 ********** */
	/*
	 * 连接方式是：
	 * sound >> XgmAudioChannel >> AbstractXgmMultiMixer >> XgmSoundMixer
	 * 
	 * 其中, 一个 sound 连一个 XgmAudioChannel
	 * 多个 XgmAudioChannel 连一个 IXgmMultiChannelMixer
	 * 多个 IXgmMultiChannelMixer 连一个 XgmSoundMixer
	 * XgmSoundMixer 只有一个
	 */
	
	final HashMap<Byte, AbstractXgmMultiMixer> multis = new HashMap<>();
	private final ArrayList<AbstractXgmMultiMixer> multiList = new ArrayList<>();
	
	/**
	 * 缓存, 性能考虑
	 */
	private AbstractXgmMultiMixer[] multiArray;
	
	@Override
	public AbstractXgmAudioChannel allocateChannel(byte channelCode) {
		byte chip = chipOfChannel(channelCode);
		AbstractXgmMultiMixer multi = multis.get(chip);
		if (multi == null) {
			multi = createMultiChannelMixer(chip);
			multis.put(chip, multi);
			multiList.add(multi);
			multiArray = null;
		}
		
		AbstractXgmAudioChannel ch = multi.getAudioChannel(channelCode);
		ch.setEnable(true);
		return ch;
	}
	
	/**
	 * 按照 chip 选择 IXgmMultiChannelMixer
	 */
	private AbstractXgmMultiMixer createMultiChannelMixer(byte chip) {
		AbstractXgmMultiMixer multi = null;
		
		switch (chip) {
		case CHIP_2A03:
			multi = new Xgm2A03Mixer();
			break;
		case CHIP_2A07:
			multi = new Xgm2A07Mixer();
			break;
		case CHIP_VRC6:
			multi = new XgmVRC6Mixer();
			break;
		case CHIP_MMC5:
			multi = new XgmMMC5Mixer();
			break;
		case CHIP_FDS:
			multi = new XgmFDSMixer();
			break;
		case CHIP_N163:
			multi = new XgmN163Mixer();
			break;
		case CHIP_VRC7:
			multi = new XgmVRC7Mixer();
			break;
		case CHIP_S5B:
			multi = new XgmS5BMixer();
			break;
		}
		
		if (multi != null) {
			Filter f = new Filter();
			f.setRate(param.sampleRate);
			f.setParam(4700, 0);
			multi.attachIntercept(f);

			Amplifier amp = new Amplifier();
			amp.setCompress(100, -1);
			multi.attachIntercept(amp);
		}
		
		return multi;
	}

	@Override
	public void detachAll() {
		multis.clear();
		multiList.clear();
		multiArray = null;
	}

	@Override
	public IMixerChannel getMixerChannel(byte channelCode) {
		byte chip = chipOfChannel(channelCode);
		AbstractXgmMultiMixer multi = multis.get(chip);
		
		if (multi != null) {
			return multi.getAudioChannel(channelCode);
		}
		
		return null;
	}
	
	@Override
	public void reset() {
		multiList.forEach(multi -> multi.reset());
		interceptors.forEach(i -> i.reset());
	}
	
	/* **********
	 * 音频合成 *
	 ********** */
	
	short[] samples;

	/**
	 * 拦截器组
	 */
	final ArrayList<ISoundInterceptor> interceptors = new ArrayList<>();
	
	/**
	 * 缓存, 性能考虑
	 */
	private ISoundInterceptor[] interceptorArray;
	
	/**
	 * 用于计算周期和采样关系的模型
	 */
	private final CycleCounter counter = new CycleCounter();
	
	private void initInterceptors() {
		// 构造拦截器组
		EchoUnit echo = new EchoUnit();
		echo.setRate(param.sampleRate);
		attachIntercept(echo); // 注意, 回音是这里产生的. 如果想去掉回音, 修改这里

		DCFilter dcf = new DCFilter();
		dcf.setRate(param.sampleRate);
		dcf.setParam(270, 164);
		attachIntercept(dcf);

		Filter f = new Filter();
		f.setRate(param.sampleRate);
		f.setParam(4700, 112);
		attachIntercept(f);

		Compressor cmp = new Compressor();
		cmp.setParam(1, 1, 1);
		attachIntercept(cmp);
	}
	
	/**
	 * @param value
	 * @param time
	 *   过去的时钟周期数
	 * @return
	 */
	int intercept(int value, int time) {
		int ret = value;
		final int length = interceptorArray.length;
		for (int i = 0; i < length; i++) {
			ISoundInterceptor interceptor = interceptorArray[i];
			if (interceptor.isEnable()) {
				ret = interceptor.execute(ret, time);
			}
		}
		return ret;
	}
	
	/**
	 * 添加音频数据的拦截器
	 * @param interceptor
	 */
	public void attachIntercept(ISoundInterceptor interceptor) {
		if (interceptor != null) {
			interceptors.add(interceptor);
		}
	}
	
	@Override
	public void readyBuffer() {
		allocateSampleArray();
		final int len = multiList.size();
		for (int i = 0; i < len; i++) {
			AbstractXgmMultiMixer multi = multiList.get(i);
			multi.checkCapacity(param.freqPerFrame);
		}
	}

	@Override
	public int finishBuffer() {
		beforeRender();
		final int length = param.sampleInCurFrame;
		int v, fromTime, delta, toTime = 0;
		for (int i = 0; i < length; i++) {
			
			// 渲染一帧的流程
			fromTime = toTime;
			delta = counter.tick();
			toTime = counter.getCycleCount();
			// fromTime 和 toTime 是时钟数
			v = 0;
			
			final int mlen = multiArray.length;
			for (int midx = 0; midx < mlen; midx++) {
				AbstractXgmMultiMixer multi = multiArray[midx];
				v += multi.render(i, fromTime, toTime);
			}
			v = intercept(v, delta) >> 1;
			
			if (v > Short.MAX_VALUE) {
				v = Short.MAX_VALUE;
			} else if (v < Short.MIN_VALUE) {
				v = Short.MIN_VALUE;
			}
			samples[i] = (short) v;
		}
		
		return length;
	}
	
	private void beforeRender() {
		final int len = multiList.size();
		for (int i = 0; i < len; i++) {
			AbstractXgmMultiMixer multi = multiList.get(i);
			multi.beforeRender();
		}
		
		counter.setParam(param.freqPerFrame, param.sampleInCurFrame);
		
		// 以下是性能考虑
		if (interceptorArray == null || interceptorArray.length != interceptors.size()) {
			interceptorArray = new ISoundInterceptor[interceptors.size()];
		}
		interceptors.toArray(interceptorArray);
		
		if (multiArray == null) {
			multiArray = new AbstractXgmMultiMixer[multiList.size()];
			multiList.toArray(multiArray);
		}
	}
	
	@Override
	public int readBuffer(short[] buf, int offset, int length) {
		int len = Math.min(length, param.sampleInCurFrame);
		System.arraycopy(samples, 0, buf, offset, len);
		
		// 重置 samples
		Arrays.fill(samples, (short) 0);
		
		return len;
	}
	
	/**
	 * 为 sample 数组分配空间, 创建数组.
	 * 在创建数组的同时, 构造输出相关的拦截器.
	 * 创建数组需要知道 param.sampleInCurFrame 的值
	 */
	private void allocateSampleArray() {
		if (this.samples != null) {
			if (this.samples.length < param.sampleInCurFrame) {
				this.samples = new short[param.sampleInCurFrame + 16];
			}
			return;
		}
		
		this.samples = new short[param.sampleInCurFrame + 16];
	}
	
	/* **********
	 * 用户操作 *
	 ********** */
	
	XgmMixerHandler handler;
	
	@Override
	public XgmMixerHandler getHandler() {
		if (handler == null) {
			handler = new XgmMixerHandler(this);
		}
		return handler;
	}

}
