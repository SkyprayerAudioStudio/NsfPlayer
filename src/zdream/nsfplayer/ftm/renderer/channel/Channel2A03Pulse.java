package zdream.nsfplayer.ftm.renderer.channel;

import static zdream.nsfplayer.sound.Sound2A03.LENGTH_TABLE;

import zdream.nsfplayer.ftm.format.FtmSequence;
import zdream.nsfplayer.ftm.format.FtmSequenceType;
import zdream.nsfplayer.ftm.renderer.tools.NoteLookupTable;
import zdream.nsfplayer.sound.PulseSound;

/**
 * 2A03 矩形轨道 1 / 2
 * 
 * @version v0.2.5
 *   将两个 2A03 矩形轨道进行了合并
 * 
 * @author Zdream
 * @since v0.2.1
 */
public final class Channel2A03Pulse extends ChannelTone {

	/**
	 * @param isPulse1
	 *   如果是 2A03 一号矩形轨道, 为 true
	 *   如果是 2A03 二号矩形轨道, 为 false
	 */
	public Channel2A03Pulse(boolean isPulse1) {
		super(isPulse1 ? CHANNEL_2A03_PULSE1 : CHANNEL_2A03_PULSE2);
	}

	@Override
	public void playNote() {
		super.playNote();
		
		// sequence
		updateSequence();
		
		// 发声器
		writeToSound();
		processSound();
	}

	@Override
	public void reset() {
		super.reset();
		seq.reset();
		sound.reset();
	}
	
	/* **********
	 *  sweep   *
	 ********** */
	
	/**
	 * <p>范围 [0, 0xFF]
	 * <p>暂时没有使用
	 */
	protected int sweep;
	
	/* **********
	 *   序列   *
	 ********** */
	
	/**
	 * 更新序列, 并将序列的数据回写到轨道上
	 */
	private void updateSequence() {
		if (instrumentUpdated) {
			// 替换序列
			FtmSequence[] seqs = getRuntime().querier.getSequences(instrument);
			for (int i = 0; i < seqs.length; i++) {
				FtmSequence s = seqs[i];
				if (s != null) {
					seq.setupSequence(seqs[i]);
				} else {
					seq.clearSequence(FtmSequenceType.get(i));
				}
			}
		}
		
		seq.update();
		
		// 回写
		calculateVolume();
		calculatePeriod();
		calculateDuty();
		
	}
	
	@Override
	protected void calculateDuty() {
		super.calculateDuty();
		curDuty &= 0x3;
	}
	
	/* **********
	 *  发声器  *
	 ********** */
	
	/**
	 * 2A03 Pulse 1 音频发声器
	 */
	public final PulseSound sound = new PulseSound();

	@Override
	public PulseSound getSound() {
		return sound;
	}
	
	/**
	 * <p>将轨道中的数据写到发声器中.
	 * <p>参照原工程 Square1Chan.refreshChannel()
	 * </p>
	 */
	public void writeToSound() {
		sound.looping = true;
		sound.envelopeFix = true;
		
		if (this.curVolume == 0 || !playing || masterNote == 0) {
			sound.fixedVolume = 0;
			return;
		}
		
		sound.dutyLength = curDuty;
		sound.fixedVolume = curVolume / 16;
		
		if (sweep > 0) {
			if ((sweep & 0x80) != 0) {
				// 0x4001
				int s = sweep & 0x7F;
				sound.sweepEnabled = true;
				sound.sweepPeriod = ((s >> 4) & 0x07) + 1;
				sound.sweepMode = (s & 0x08) != 0;		
				sound.sweepShift = s & 0x07;
				sound.sweepUpdated = true;
				
				// TODO Clear sweep unit 不清楚如何清除 Sweep 部分
//				writeRegister(0x4017, (byte) 0x80);	// Clear sweep unit
//				writeRegister(0x4017, (byte) 0x00);
				
				// 0x4002 and 0x4003
				sound.period = curPeriod;
				sound.lengthCounter = LENGTH_TABLE[0];
			}
		} else {
			// 0x4001
			sound.sweepEnabled = false;
			sound.sweepPeriod = 1;
			sound.sweepMode = true;
			sound.sweepShift = 0;
			sound.sweepUpdated = true;
			
			// TODO 不清楚如何操作
//			writeRegister(0x4017, (byte) 0x80);	// Manually execute one APU frame sequence to kill the sweep unit
//			writeRegister(0x4017, (byte) 0x00);

			// 0x4002 and 0x4003
			sound.period = curPeriod;
			sound.lengthCounter = LENGTH_TABLE[0];
		}
		// sound.
	}
	
	/**
	 * 指导发声器工作一帧
	 */
	public void processSound() {
		// 拿到一帧对应的时钟周期数
		int freq = getRuntime().param.freqPerFrame;
		
		// TODO 暂时没有考虑 sweep 和 envelope 部分, 还有 4017 参数
		sound.process(freq);
		
		// 结束
		sound.endFrame();
	}
	
	/* **********
	 *   其它   *
	 ********** */
	
	/**
	 * 根据音键查询波长值.
	 * 工具方法
	 */
	public int periodTable(int note) {
		return NoteLookupTable.ntsc(note);
	}
	
}