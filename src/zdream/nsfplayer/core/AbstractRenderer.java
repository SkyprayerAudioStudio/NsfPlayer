package zdream.nsfplayer.core;

import java.util.Arrays;

/**
 * <p>抽象的音源的渲染器, 用于输出以 byte / short 数组组织的 PCM 音频数据
 * <p>在 {@link AbstractNsfRenderer} 基础上作进一步的抽象
 * </p>
 * 
 * @author Zdream
 * @since v0.3.1
 */
public abstract class AbstractRenderer<T> {
	
	/* **********
	 * 准备部分 *
	 ********** */
	
	/**
	 * <p>让该渲染器读取对应的 audio 数据.
	 * <p>设置播放暂停位置为默认曲目的开头.
	 * </p>
	 * @param audio
	 *   音频数据
	 */
	public abstract void ready(T audio);
	
	/**
	 * 询问是否整个乐曲已经渲染完成
	 * @return
	 */
	public abstract boolean isFinished();
	
	/* **********
	 * 渲染部分 *
	 ********** */
	
	/**
	 * 渲染
	 * <br>线程不安全的方法
	 * @param bs
	 * @param offset
	 *   bs 存放数据的起始位置
	 * @param length
	 *   bs 存放的数据总量, 以 byte 为单位.
	 *   <br>这里是单声道、16 位深度, 该数据需要是 2 的倍数.
	 * @return
	 *   真正填充的数组元素个数
	 */
	public int render(byte[] bs, int offset, int length) {
		length = length / 2 * 2; // 化成 2 的倍数
		int bOffset = offset; // bs 的 offset
		int bLength = length;
		int ret = 0; // 已完成的采样数
		
		// 前面渲染剩余的采样、还没有被返回的
		int v = fillSample(bs, bOffset, bLength) * 2;
		ret += v;
		bOffset += v;
		bLength -= v;
		
		while (ret < length) {
			renderFrame();
			// data 数据已经就绪
			
			v = fillSample(bs, bOffset, bLength) * 2;
			ret += v;
			bOffset += v;
			bLength -= v;
			
			if (isFinished()) {
				break;
			}
		}
		
		return ret; // (现单位 byte)
	}
	
	/**
	 * 渲染, 以 short 数组的方式获取采样数据
	 * <br>线程不安全的方法
	 * @param bs
	 *   获取采样数据的数组, short 格式
	 * @param offset
	 *   bs 存放数据的起始位置
	 * @param length
	 *   bs 存放的数据总量.
	 * @return
	 *   真正填充的数组元素个数
	 * @since v0.2.9
	 */
	public int render(short[] bs, int offset, int length) {
		int bOffset = offset; // bs 的 offset
		int bLength = length; // bs 能获取的采样数
		int ret = 0; // 已完成的采样数
		
		// 前面渲染剩余的采样、还没有被返回的
		int v = fillSample(bs, bOffset, bLength);
		ret += v;
		bOffset += v;
		bLength -= v;
		
		while (ret < length) {
			renderFrame();
			// data 数据已经就绪
			
			v = fillSample(bs, bOffset, bLength);
			ret += v;
			bOffset += v;
			bLength -= v;
			
			if (isFinished()) {
				break;
			}
		}
		
		return ret;
	}
	
	/**
	 * <p>仅渲染一帧, 以 short 数组的方式获取采样数据.
	 * 如果之前有没有渲染完的、上一帧采样数据,
	 * 只将上一帧剩余的采样数据写进数组.
	 * <br>线程不安全的方法
	 * </p>
	 * @param bs
	 * @param offset
	 *   bs 存放数据的起始位置
	 * @param length
	 *   bs 存放的数据总量, 以 byte 为单位.
	 *   <br>这里是单声道、16 位深度, 该数据需要是 2 的倍数.
	 * @return
	 *   真正填充的数组元素个数
	 * @since v0.2.2
	 */
	public int renderOneFrame(byte[] bs, int offset, int length) {
		int bLength = length / 2 * 2; // 化成 2 的倍数
		
		// 前面渲染剩余的采样、还没有被返回的
		int ret = fillSample(bs, offset, bLength) * 2;
		if (ret == 0) {
			renderFrame();
			// data 数据已经就绪
			ret = fillSample(bs, offset, bLength) * 2;
		}
		
		return ret; // (现单位 byte)
	}
	
	/**
	 * <p>仅渲染一帧. 如果之前有没有渲染完的、上一帧采样数据,
	 * 只将上一帧剩余的采样数据写进数组.
	 * <br>线程不安全的方法
	 * </p>
	 * @param bs
	 *   获取采样数据的数组, short 格式
	 * @param offset
	 *   bs 存放数据的起始位置
	 * @param length
	 *   bs 存放的数据总量, 以 short 为单位.
	 * @return
	 *   真正填充的数组元素个数
	 * @since v0.2.9
	 */
	public int renderOneFrame(short[] bs, int offset, int length) {
		// 前面渲染剩余的采样、还没有被返回的
		int ret = fillSample(bs, offset, length);
		if (ret == 0) {
			renderFrame();
			// data 数据已经就绪
			ret = fillSample(bs, offset, length);
		}
		
		return ret;
	}
	
	/**
	 * <p>跳过指定帧数.
	 * <p>如果之前有没有渲染完的、上一帧采样数据, 该帧数据将被丢弃, 且不计入跳过的帧数;
	 * <p><code>skip(0)</code> 表示丢弃上一帧没有渲染完的采样数据, 从下一帧开头开始渲染.
	 * </p>
	 * @param frame
	 *   帧数. 必须为正数
	 * @since v0.2.9
	 */
	public void skip(final int frame) {
		for (int i = 0; i < frame; i++) {
			this.skipFrame();
		}
		
		// 如果前面渲染剩余的采样、还没有被返回的, 直接清空
		this.offset = this.length = 0;
	}
	
	/**
	 * <p>剩余采样数.
	 * <p>返回上一帧没有渲染完的、采样数据. 如果没有, 返回 0.
	 * </p>
	 * @return
	 *   上一帧未渲染完的采样数
	 * @since v0.2.9
	 */
	public int remain() {
		return this.length - this.offset;
	}
	
	/*
	 * 渲染参数 
	 */
	/**
	 * 采样率计数器.
	 * @since v0.2.5
	 */
	protected final CycleCounter counter = new CycleCounter();
	
	/**
	 * 音频数据.
	 * <br>还没有返回的采样数据在这一块: [offset, length)
	 */
	protected short[] data;
	protected int offset = 0;
	protected int length = 0;
	
	/**
	 * <p>填充采样数据. byte[] 数组.
	 * <p>按照 48000 Hz, 16 bit signed | little-endian, mono (单声道) 的方式填充
	 * </p>
	 * @param bs
	 * @param bOffset
	 * @param bLength
	 * @return
	 *   实际填充的采样数
	 */
	protected int fillSample(byte[] bs, int bOffset, int bLength) {
		int bRemain = bLength / 2;
		int dRemain = this.length - this.offset; // data 中剩下的 (单位 采样)
		int ret = 0;
		
		if (dRemain != 0) {
			if (bRemain <= dRemain) {
				// 将 data 的数据填充到 bs 中
				fillSample(bs, bOffset, bLength, bRemain);
				// bs 填满了
				
				ret += bRemain;
			} else {
				// 将 data 的数据填充到 bs 中
				fillSample(bs, bOffset, bLength, dRemain);
				// data 用完了
				
				ret += dRemain;
			}
		}
		
		return ret;
	}
	
	/**
	 * 填充采样数据. short[] 数组
	 * @param bs
	 *   short 数组
	 * @param bOffset
	 * @param bLength
	 * @return
	 *   实际填充的采样数
	 * @since v0.2.9
	 */
	protected int fillSample(short[] bs, int bOffset, int bLength) {
		int bRemain = bLength;
		int dRemain = this.length - this.offset; // data 中剩下的 (单位 采样)
		int ret = 0;
		
		if (dRemain != 0) {
			if (bRemain <= dRemain) {
				// 将 data 的数据填充到 bs 中
				System.arraycopy(this.data, this.offset, bs, bOffset, bRemain);
				this.offset += bRemain;
				// bs 填满了
				ret = bRemain;
			} else {
				// 将 data 的数据填充到 bs 中
				System.arraycopy(this.data, this.offset, bs, bOffset, dRemain);
				// data 用完了
				ret = dRemain;
				this.offset += dRemain;
			}
		}
		
		return ret;
	}
	
	protected void fillSample(byte[] bs, int bOffset, int bLength, int dLength) {
		int bptr = bOffset;
		int dptr = this.offset;
		for (int i = 0; i < dLength; i++) {
			short sample = this.data[dptr++];
			bs[bptr++] = (byte) sample; // 低位
			bs[bptr++] = (byte) ((sample & 0xFF00) >> 8); // 高位
		}
		
		this.offset += dLength;
	}
	
	/**
	 * 计算下一帧需要的采样数 (每个声道), 已经将播放速度造成的影响计入
	 * @return
	 *   下一帧需要的采样数
	 */
	protected int countNextFrame() {
		int ret = counter.tick();
		
		if (data == null || data.length < ret || data.length - ret > 16) {
			data = new short[ret + 8];
		} else {
			Arrays.fill(data, (byte) 0);
		}
		length = ret;
		offset = 0;
		
		return ret;
	}
	
	/**
	 * 重置帧率与采样率. 如果该渲染器需要替换音频, 则需要调用该参数来重置计数器
	 * @param maxFrameCount
	 *   帧率, 一般为 60
	 * @param maxSampleCount
	 *   采样率, 一般为 48000. 该值不受播放速度影响
	 * @since v0.2.5
	 */
	protected void resetCounterParam(int maxFrameCount, int maxSampleCount) {
		float speed = getSpeed();
		int cycle = maxSampleCount;
		if (speed != 1) {
			cycle = (int) (cycle / speed);
		}
		
		// 重置计数器
		counter.setParam(cycle, maxFrameCount);
	}
	
	/**
	 * 重置音频部分, 包括 buffer 数组
	 * @since v0.2.9
	 */
	protected void clearBuffer() {
		// 重置音频部分, 包括 buffer 数组
		offset = 0;
		length = 0;
		if (data != null) {
			Arrays.fill(data, (byte) 0);
		}
	}
	
	/**
	 * 渲染一帧
	 * @return
	 *  本函数已渲染的采样数 (按单声道计算)
	 */
	protected abstract int renderFrame();
	
	/* **********
	 * 仪表盘区 *
	 ********** */
	
	/**
	 * 跳过一帧
	 * @return
	 *   本函数跳过的采样数 (按单声道计算)
	 * @since v0.2.9
	 */
	protected abstract int skipFrame();
	
	/**
	 * <p>设置播放速度.
	 * <p>如果当前帧的音频数据没有取完, 这部分的音频数据不再做变速处理, 变速效果从下一帧开始.
	 * </p>
	 * @param speed
	 *   播放速度. 有效值范围: [0.1f, 10f]
	 * @since v0.2.9
	 */
	public abstract void setSpeed(float speed);
	
	/**
	 * 获取当前的播放速度.
	 * @return
	 *   播放速度. 有效值范围: [0.1f, 10f]
	 * @since v0.2.9
	 */
	public abstract float getSpeed();
	
	/**
	 * 重置播放速度
	 * @since v0.2.9
	 */
	public void resetSpeed() {
		setSpeed(1.0f);
	}

}
