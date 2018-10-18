package zdream.nsfplayer.ftm.renderer.channel;

import zdream.nsfplayer.ftm.format.FtmSequence;
import zdream.nsfplayer.ftm.renderer.AbstractFtmChannel;
import zdream.nsfplayer.ftm.renderer.sequence.DefaultSequenceHandler;
import zdream.nsfplayer.ftm.renderer.tools.NoteLookupTable;

/**
 * VRC6 芯片的轨道, 一共有三种
 * 
 * @author Zdream
 * @since v0.2.3
 */
public abstract class ChannelVRC6 extends AbstractFtmChannel {

	public ChannelVRC6(byte channelCode) {
		super(channelCode);
	}
	
	/* **********
	 *   序列   *
	 ********** */
	
	public final DefaultSequenceHandler seq = new DefaultSequenceHandler();
	
	/**
	 * 计算音量, 将序列所得出的音量合并计算, 最后将音量限定在 [0, 240] 范围内
	 */
	protected void calculateVolume() {
		int volume = masterVolume * 16 + curVolume; // 精度 240
		if (volume <= 0) {
			curVolume = 0;
			return;
		}
		
		volume = (seq.volume * volume) / 15;
		
		if (volume > 240) {
			curVolume = 240;
		} else if (volume < 1) {
			curVolume = (seq.volume == 0) ? 0 : 1;
		} else {
			curVolume = volume;
		}
	}
	
	/**
	 * 计算波长, 将序列所得出的波长、音高、音键, 还有其它效果得出的音高、音键值
	 * 最后综合出波长值
	 */
	protected void calculatePeriod() {
		if (masterNote == 0) {
			// 不播放
			curNote = 0;
			curPeriod = 0;
			return;
		}
		
		int note = masterNote + curNote + seq.deltaNote;
		int period = -masterPitch + curPeriod + seq.period;
		
		if (seq.arp != 0) {
			// TODO 存在问题
			// 请注意 SequenceHandler.updateSequenceRunning 方法
			switch (seq.arpSetting) {
			case FtmSequence.ARP_SETTING_ABSOLUTE:
				note += seq.arp;
				break;
			case FtmSequence.ARP_SETTING_FIXED: // 重置
				note = seq.arp;
				break;
			case FtmSequence.ARP_SETTING_RELATIVE:
				this.masterNote += seq.arp;
				note += seq.arp;
			default:
				break;
			}
		}
		
		if (note <= 1) {
			note = 1;
		} else if (note > 96) {
			note = 96;
		}
		
		period += periodTable(note);
		if (period < 1) {
			period = 1;
		}
		
		curNote = note;
		curPeriod = period;
	}
	
	/**
	 * 计算音色
	 */
	protected void calculateDuty() {
		if (seq.duty >= 0) {
			curDuty = seq.duty;
		} else {
			curDuty = masterDuty;
		}
		
		if (curDuty < 0 || curDuty > 7) {
			curDuty = curDuty & 0x7;
		}
	}
	
	/**
	 * 根据音键查询波长值.
	 * 工具方法
	 */
	public int periodTable(int note) {
		return NoteLookupTable.ntsc(note);
	}
	
	@Override
	public void doRelease() {
		seq.setRelease(true);
	}

}
