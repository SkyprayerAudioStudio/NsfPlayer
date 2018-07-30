package zdream.nsfplayer.sound;

/**
 * 矩形波发声器
 * @author Zdream
 * @since 0.2.1
 */
public class PulseSound extends Sound2A03 {
	
	@SuppressWarnings("unused")
	private static final boolean[][] DUTY_TABLE = {
			{ false, false,  true,  true, false, false, false, false, false, false, false, false, false, false, false, false },
			{ false, false,  true,  true,  true,  true, false, false, false, false, false, false, false, false, false, false },
			{ false, false,  true,  true,  true,  true,  true,  true,  true,  true, false, false, false, false, false, false },
			{  true,  true, false, false, false, false,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true }
	};
	
	/* **********
	 *   参数   *
	 ********** */
	/*
	 * 原始记录参数
	 */
	
	/**
	 * <p>0 号位: xx000000
	 * <p>unsigned, 值域 [0, 3]. 指示音色的值, 指向 DUTY_TABLE 的一级索引
	 * </p>
	 */
	public int dutyLength;

	/**
	 * <p>0 号位: 00x00000
	 * <p>为 1 时为 true, 为 0 时为 false
	 * </p>
	 */
	public boolean looping;
	
	/**
	 * <p>0 号位: 000x0000
	 * <p>为 1 时为 true, 为 0 时为 false
	 * </p>
	 */
	public boolean envelopeFix;

	/**
	 * <p>0 号位: 0000xxxx
	 * <p>unsigned, 值域 [0, 15]
	 * </p>
	 */
	public int fixedVolume;
	
	/**
	 * <p>1 号位: x0000000
	 * <p>为 1 时为 true, 为 0 时为 false
	 * </p>
	 */
	public boolean sweepEnabled;

	/**
	 * <p>1 号位: 0xxx0000, 取得数值之后加 1
	 * <p>unsigned, 值域 [1, 8]
	 * </p>
	 */
	public int sweepPeriod;

	/**
	 * <p>1 号位: 0000x000
	 * <p>为 1 时为 true, 为 0 时为 false
	 * </p>
	 */
	public boolean sweepMode;

	/**
	 * <p>1 号位: 00000xxx, 偏移位
	 * <p>unsigned, 值域 [0, 7]
	 * </p>
	 */
	public int sweepShift;
	
	/**
	 * <p>2 号位: xxxxxxxx (低八位), 3 号位: 00000xxx (高三位) 共 11 位
	 * <p>为波长值; unsigned, 值域 [0, 2047]
	 * </p>
	 */
	public int period;
	
	/**
	 * <p>3 号位: xxxxx000
	 * <p>查找索引
	 * </p>
	 */
	public int lengthCounter;
	
	/*
	 * 辅助参数
	 * 
	 * 注意, 0x4015 位: (Pulse 1) 0000000x, (Pulse 2) 000000x0 是 enable, 在超类中
	 */
	/**
	 * 记录 sweep 相关参数是否被修改
	 */
	public boolean sweepUpdated;
	
	/* **********
	 * 公共方法 *
	 ********** */
	
	/**
	 * 重置相关数据
	 */
	public void reset() {
		// 原始记录参数
		
		dutyLength = 0;
		fixedVolume = 0;
		looping = false;
		envelopeFix = false;
		// envelopeSpeed = fixedVolume + 1;
		
		sweepEnabled = false;
		sweepPeriod = 1;
		sweepMode = false;		
		sweepShift = 0;
		// sweepWritten = true;
		
		period = 0;
		
		lengthCounter = LENGTH_TABLE[0] & 0xFF;
//		dutyCycle = 0;
//		envelopeVolume = 0x0F;
//		if (m_iControlReg != 0)
//			m_iEnabled = 1;
		
		
		// TODO
		
		super.reset();
	}
	
	@Override
	protected void onProcess(int time) {
		if (period <= 0) {
			return;
		}
		
		// TODO
	}

}
