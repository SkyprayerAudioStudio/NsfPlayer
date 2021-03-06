package zdream.nsfplayer.mixer.xgm;

import static java.util.Objects.requireNonNull;
import static zdream.nsfplayer.core.NsfChannelCode.chipOfChannel;

import java.util.ArrayList;

import zdream.nsfplayer.core.NsfCommonParameter;
import zdream.nsfplayer.core.NsfPlayerException;
import zdream.nsfplayer.mixer.AbstractNsfSoundMixer;
import zdream.nsfplayer.mixer.IMixerHandler;
import zdream.nsfplayer.mixer.ITrackMixer;
import zdream.nsfplayer.mixer.interceptor.Amplifier;
import zdream.nsfplayer.mixer.interceptor.Compressor;
import zdream.nsfplayer.mixer.interceptor.DCFilter;
import zdream.nsfplayer.mixer.interceptor.EchoUnit;
import zdream.nsfplayer.mixer.interceptor.Filter;
import zdream.nsfplayer.mixer.interceptor.ISoundInterceptor;

/**
 * <p>Xgm 的合并轨混音器, 原来是 NsfPlayer 的默认使用混音器.
 * <p>自从 Mixer 渲染部分和 NSF / FTM 的执行部分分离之后,
 * 无论是 FamiTracker 还是 Nsf 部分, 均能够使用 Xgm 混音器作为输出混音器.
 * 
 * <p>与 Blip 混音器不同的是, 它渲染策略是, 对每个采样进行计算、加工, 最后输出.
 * 由于能够操作每个采样点, 因此它的灵活性和可扩展性均比 Blip 混音器高出很多,
 * 但是代价也是很明显的: 慢.
 * 
 * <p>根据测试的结果, 渲染同样的曲目, Xgm 混音器在开启所有内置效果拦截器的情况下,
 * 渲染时间是 Blip 混音器的 1.2 至 10 倍. 这个现象在渲染只有 2A03 + 2A07 轨道,
 * 而且 DPCM 轨道不发出声音的情况下尤为明显. 因此如果没有对音频数据作处理的需求, 建议使用 Blip 混音器.
 * 
 * <p>内置的效果拦截器中, 回音构造器花费的时间最长. 因此如果播放卡顿, 优先关闭回音构造器.
 * 关闭内置回音的方法: 以 NsfRenderer 为例:
 * <blockquote><pre>
 *     NsfRenderer renderer;
 *     
 *     ...
 * 
 *     XgmMixerHandler h = (XgmMixerHandler) renderer.getMixerHandler();
 *     List<ISoundInterceptor> itcs = h.getGlobalInterceptors();
 *     for (ISoundInterceptor itc: itcs) {
 *        if (itc instanceof EchoUnit) {
 *           itc.setEnable(false);
 *        }
 *     }
 * </pre></blockquote>
 * 以上方法能够成功的条件是启用 Xgm 混音器作为输出混音器,
 * 否则<code>renderer.getMixerHandler()</code> 不会返回 <code>XgmMixerHandler</code> 实例.
 * </p>
 * 
 * @version v0.2.10
 *   <br>自从大部分 Renderer 类开放了获取混音器操作类 {@link IMixerHandler} 的获取之后,
 *   音频拦截器的使用终于给用户们开放了. 这也极大地添加了 Xgm 混音器的灵活性.
 *   <br>另外, 这个版本对 Xgm 混音器作了大幅度的优化, 它的运行效率同版本 v0.2.9 相比
 *   提高了 10% - 30%.
 * 
 * @author Zdream
 * @since v0.2.1
 */
public class XgmMultiSoundMixer extends AbstractNsfSoundMixer<AbstractXgmAudioChannel>
		implements ITrackMixer {
	
	public NsfCommonParameter param;

	public XgmMultiSoundMixer() {
		
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
	 * 轨道参数 *
	 ********** */
	
	/**
	 * 全局参数 : 声道数. 1 表示单声道, 2 表示立体声, 可以 3 或者更多.
	 */
	int trackCount;
	
	/**
	 * @return
	 *   当前的声道数
	 */
	public int getTrackCount() {
		return trackCount;
	}
	
	/**
	 * 声道数改变之后, 声道音量、拦截器组会重置, 前面的所有修改全部被丢弃.
	 * @param trackCount
	 *   声道数
	 */
	@SuppressWarnings("unchecked")
	public void setTrackCount(int trackCount) {
		if (trackCount <= 0) {
			throw new NsfPlayerException("声道数: " + trackCount + " 为非法值");
		}
		
		this.trackCount = trackCount;
		
		samples = new short[trackCount][];
		
		// 轨道
//		int len = attrs.size();
//		for (int i = 0; i < len; i++) {
//			ChannelAttr attr = attrs.get(i);
//			if (attr == null) {
//				continue;
//			}
//			
//			attr.channel.setTrackCount(trackCount, param);
//		}
		
		// 拦截器组
		interceptors = new ArrayList[trackCount];
		for (int i = 0; i < trackCount; i++) {
			interceptors[i] = initInterceptors(new ArrayList<>());
		}
		interceptorArray = new ISoundInterceptor[trackCount][];
	}
	
	protected class XgmMultiChannelAttr extends ChannelAttr {
		protected XgmMultiChannelAttr(byte code, AbstractXgmAudioChannel t) {
			super(code, t);
		}
		
		AbstractXgmMultiMixer multi;
	}
	
	@Override
	protected XgmMultiChannelAttr createChannelAttr(final byte code) {
		
		AbstractXgmAudioChannel channel;
		for (AbstractXgmMultiMixer multi : multiList) {
			channel = multi.getRemainAudioChannel(code);
			if (channel != null) {
				// 将该轨道插入到原来已经存在的合并轨道中
				XgmMultiChannelAttr attr = new XgmMultiChannelAttr(code, channel);
				multi.setEnable(channel, true);
				attr.multi = multi;
				return attr;
			}
		}
		
		// 这里就需要创建合并轨道了
		byte chip = chipOfChannel(code);
		AbstractXgmMultiMixer multi = createMultiChannelMixer(chip);
		multiList.add(multi);
		multiArray = null;
		
		channel = multi.getRemainAudioChannel(code);
		requireNonNull(channel);
		
		XgmMultiChannelAttr attr = new XgmMultiChannelAttr(code, channel);
		multi.setEnable(channel, true);
		attr.multi = multi;
		return attr;
	}
	
	XgmMultiChannelAttr getAttr(int id) {
		if (attrs.size() <= id) {
			return null;
		}
		return (XgmMultiChannelAttr) attrs.get(id);
	}
	
	@Override
	public void setInSample(int id, int inSample) {
		XgmMultiChannelAttr attr = getAttr(id);
		if (attr == null) {
			return;
		}
		
		if (inSample <= 0) {
			attr.inSample = 0;
		} else {
			attr.inSample = inSample;
		}
	}
	
	/* **********
	 * 音频管道 *
	 ********** */
	/*
	 * 连接方式是：
	 * sound (执行构件) >> XgmAudioChannel >> AbstractXgmMultiMixer >> XgmMultiSoundMixer
	 * 
	 * 其中, 一个 sound 连一个 XgmAudioChannel
	 * 多个 XgmAudioChannel 连一个 IXgmMultiChannelMixer
	 * 多个 IXgmMultiChannelMixer 连一个 XgmMultiSoundMixer
	 * XgmMultiSoundMixer 只有一个
	 */
	
	private final ArrayList<AbstractXgmMultiMixer> multiList = new ArrayList<>();
	
	/**
	 * 缓存, 性能考虑
	 */
	private AbstractXgmMultiMixer[] multiArray;
	
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
		multiList.clear();
		multiArray = null;
		super.detachAll();
	}
	
	@Override
	public void detach(int id) {
		XgmMultiChannelAttr attr = (XgmMultiChannelAttr) attrs.get(id);
		attr.multi.setEnable(attr.channel, false);
		
		super.detach(id);
	}
	
	@Override
	public void reset() {
		multiList.forEach(multi -> multi.reset());
		for (ArrayList<ISoundInterceptor> list : interceptors) {
			list.forEach(i -> i.reset());
		}
	}
	
	/* **********
	 * 音频合成 *
	 ********** */
	
	/**
	 * [声道][采样]
	 */
	short[][] samples;

	/**
	 * 拦截器组
	 */
	ArrayList<ISoundInterceptor>[] interceptors;
	
	/**
	 * 缓存, 性能考虑
	 */
	private ISoundInterceptor[][] interceptorArray;
	
	private ArrayList<ISoundInterceptor> initInterceptors(ArrayList<ISoundInterceptor> list) {
		// 构造拦截器组
		EchoUnit echo = new EchoUnit();
		echo.setRate(param.sampleRate);
		list.add(echo); // 注意, 回音是这里产生的. 如果想去掉回音, 修改这里

		DCFilter dcf = new DCFilter();
		dcf.setRate(param.sampleRate);
		dcf.setParam(270, 164);
		list.add(dcf);

		Filter f = new Filter();
		f.setRate(param.sampleRate);
		f.setParam(4700, 112);
		list.add(f);

		Compressor cmp = new Compressor();
		cmp.setParam(1, 1, 1);
		list.add(cmp);
		
		return list;
	}
	
	/**
	 * @param value
	 * @param time
	 *   过去的时钟周期数
	 * @param track
	 *   声道号
	 * @return
	 */
	int intercept(int value, int time, ISoundInterceptor[] array) {
		int ret = value;
		final int length = array.length;
		for (int i = 0; i < length; i++) {
			ISoundInterceptor interceptor = array[i];
			if (interceptor.isEnable()) {
				ret = interceptor.execute(ret, time);
			}
		}
		return ret;
	}
	
	@Override
	public void readyBuffer() {
		allocateSampleArray();
		int inSample;
		for (ChannelAttr attr : attrs) {
			if (attr == null) {
				continue;
			}
			
			XgmMultiChannelAttr a = (XgmMultiChannelAttr) attr;
			inSample = a.inSample;
			if (inSample == 0) {
				inSample = param.freqPerFrame;
			}
			a.channel.checkCapacity(inSample, param.sampleInCurFrame);
		}
	}

	@Override
	public int finishBuffer() {
		beforeRender();
		
		final int length = param.sampleInCurFrame;
		if (trackCount == 1) {
			handleMonoBuffer(length);
		} else {
			handleMultiTrackBuffer(length);
		}
		
		return length;
	}
	
	/**
	 * 处理单声道的情况
	 * @param length
	 *   采样数
	 */
	private void handleMonoBuffer(int length) {
		int v;
		short[] ss = samples[0];
		ISoundInterceptor[] itcpts = this.interceptorArray[0];
		
		for (int i = 0; i < length; i++) {
			// 渲染一个采样的流程
			v = 0;
			
			final int mlen = multiArray.length;
			for (int midx = 0; midx < mlen; midx++) {
				AbstractXgmMultiMixer multi = multiArray[midx];
				v += multi.render(i);
			}
			v = intercept(v, 1, itcpts) >> 1;
			
			if (v > Short.MAX_VALUE) {
				v = Short.MAX_VALUE;
			} else if (v < Short.MIN_VALUE) {
				v = Short.MIN_VALUE;
			}
			ss[i] = (short) v;
		}
	}
	
	/**
	 * 处理多声道的情况
	 * @param length
	 *   采样数
	 */
	private void handleMultiTrackBuffer(int length) {
		int v;
		short[] ss;
		final int mlen = multiArray.length;
		
		for (int i = 0; i < length; i++) {
			for (int track = 0; track < trackCount; track++) {
				ss = samples[track];
				v = 0;
				
				for (int midx = 0; midx < mlen; midx++) {
					AbstractXgmMultiMixer multi = multiArray[midx];
					v += multi.render(i);
				}
				v = intercept(v, 1, this.interceptorArray[track]) >> 1;
				
				if (v > Short.MAX_VALUE) {
					v = Short.MAX_VALUE;
				} else if (v < Short.MIN_VALUE) {
					v = Short.MIN_VALUE;
				}
				ss[i] = (short) v;
			}
		}
	}
	
	private void beforeRender() {
		final int len = multiList.size();
		for (int i = 0; i < len; i++) {
			AbstractXgmMultiMixer multi = multiList.get(i);
			multi.beforeRender();
		}
		
		// 以下是性能考虑
		for (int i = 0; i < interceptors.length; i++) {
			ISoundInterceptor[] array = interceptorArray[i];
			ArrayList<ISoundInterceptor> list = interceptors[i];
			
			if (array == null || array.length != list.size()) {
				interceptorArray[i] = array = new ISoundInterceptor[list.size()];
			}
			list.toArray(array);
		}
		
		if (multiArray == null) {
			multiArray = new AbstractXgmMultiMixer[multiList.size()];
			multiList.toArray(multiArray);
		}
	}
	
	@Override
	public int readBuffer(short[] buf, int offset, int length) {
		int len = Math.min(length, param.sampleInCurFrame);
		
		if (trackCount == 1) {
			System.arraycopy(samples[0], 0, buf, offset, len);
			return len;
		} else {
			int index = 0;
			for (int i = 0; i < len; i++) {
				for (int track = 0; track < trackCount; track++) {
					buf[index++] = samples[track][i];
				}
			}
			return len * trackCount;
		}
	}
	
	/**
	 * 为 sample 数组分配空间, 创建数组.
	 * 在创建数组的同时, 构造输出相关的拦截器.
	 * 创建数组需要知道 param.sampleInCurFrame 的值
	 */
	private void allocateSampleArray() {
		if (this.samples[0] != null) {
			int oldSize = this.samples[0].length;
			
			if (oldSize < param.sampleInCurFrame || oldSize - param.sampleInCurFrame > 32) {
				int newSize = param.sampleInCurFrame + 16;
				for (int i = 0; i < samples.length; i++) {
					samples[i] = new short[newSize];
				}
			}
			return;
		}
		
		int newSize = param.sampleInCurFrame + 16;
		for (int i = 0; i < samples.length; i++) {
			samples[i] = new short[newSize];
		}
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
