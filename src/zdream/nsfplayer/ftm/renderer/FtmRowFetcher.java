package zdream.nsfplayer.ftm.renderer;

import java.util.HashMap;
import java.util.Map;

import zdream.nsfplayer.ftm.document.FamiTrackerQuerier;
import zdream.nsfplayer.ftm.document.FtmAudio;
import zdream.nsfplayer.ftm.format.FtmNote;
import zdream.nsfplayer.ftm.format.FtmTrack;
import zdream.nsfplayer.ftm.renderer.effect.FtmEffectType;
import zdream.nsfplayer.ftm.renderer.effect.IFtmEffect;
import zdream.nsfplayer.ftm.renderer.effect.IFtmEffectConverter;

/**
 * 确定 {@link FtmAudio} 已经播放的位置, 并能够获取 {@link FtmAudio} 正在播放的段和行.
 * <br>每一帧开始, 该类会计算 {@link FtmAudio} 的 tempo 和速度, 然后确定需要播放的段落和行.
 * @author Zdream
 * @since v0.2.1
 */
public class FtmRowFetcher implements IFtmRuntimeHolder {
	
	FamiTrackerRuntime runtime;
	
	/**
	 * 查询器
	 */
	FamiTrackerQuerier querier;

	@Override
	public FamiTrackerRuntime getRuntime() {
		return runtime;
	}
	
	/* **********
	 * 播放参数 *
	 ********** */
	
	/**
	 * 正播放的曲目号
	 */
	int trackIdx;
	
	/**
	 * <p>正播放的段号 (pattern)
	 * <p>一般取该数据会有误差, 问题详情见 {@link #row}
	 * </p>
	 */
	int sectionIdx;
	
	/**
	 * 正在播放的节奏值, 计数单位为拍 / 分钟
	 * @see FtmTrack#tempo
	 */
	int tempo;
	
	/**
	 * 正在播放的速度值
	 * @see FtmTrack#speed
	 */
	int speed;
	
	/**
	 * <p>正播放的行号, 从 0 开始.
	 * <p>当播放器播放第 0 行的音键时, 其实 row = 1, 即已经指向播放行的下一行. 因为 Famitracker 就是这样设计的.
	 * 这样做的话, 碰到跳行、跳段的效果时, 就不会出现连跳的 BUG 情况.
	 * <p>试想一下这个情况: 第 0 段第 x 行有跳行的效果 (D00 到下一段首行),
	 * 然后第 1 段、第 2 段等等第 0 行都有跳行的效果 (D00 到下一段首行), 就会出现连跳的情况.
	 * 这个在 Famitracker 中是不允许的.
	 * </p>
	 */
	int row;
	
	/**
	 * <p>重设速度值
	 * <p>原方法在 SoundGen.evaluateGlobalEffects() 中, 处理 EF_SPEED 部分的地方.
	 * </p>
	 */
	public void setSpeed(int speed) {
		this.speed = speed;
		setupSpeed();
	}
	
	/**
	 * <p>重设节奏值
	 * <p>原方法在 SoundGen.evaluateGlobalEffects() 中, 处理 EF_SPEED 部分的地方.
	 * </p>
	 */
	public void setTempo(int tempo) {
		this.tempo = tempo;
		setupSpeed();
	}
	
	/* **********
	 * 跳转参数 *
	 ********** */
	
	/**
	 * 跳转到的段号. 默认 -1 表示无效
	 */
	int jumpSection = -1;
	
	/**
	 * <p>跳转到的行号. 默认 -1 表示无效
	 * <p>这里做一个约定. 如果 {@link #jumpSection} 无效, 则为跳转到下一段的 skipRow 行;
	 * 如果 {@link #jumpSection} 有效, 则跳到 {@link #jumpSection} 段的 skipRow 行.
	 * </p>
	 */
	int skipRow = -1;
	
	/**
	 * 效果, 跳转至 section 段.
	 * @param section
	 *   段号
	 */
	public void jumpToSection(int section) {
		jumpSection = section;
	}
	
	/**
	 * <p>效果, 跳至下一段的 row 行.
	 * <p>此效果能和 {@link #jumpToSection(int)} 联合使用. 见 {@link #skipRow}
	 * </p>
	 * @param row
	 *   行号.
	 */
	public void skipRows(int row) {
		skipRow = row;
	}
	
	/* **********
	 * 状态参数 *
	 ********** */
	
	/**
	 * 节奏的累加器.
	 * 先加上播放一行音键所需的 tempo 值, 然后没帧减去一个 {@link #tempoDecrement}
	 * (accumulate)
	 */
	int tempoAccum;
	
	/**
	 * <p>由于节奏值和速度值并不是整数倍的关系, 所以 <code>节奏 / 速度</code> 会产生余数,
	 * 使得整数个帧之内, 不能将整个节奏解释完. 所以补充这个相当于 "余数" 的量.
	 * <p>每次确定解释器的速度之后, 这个值就被确定了.
	 * 除非解释器速度 ({@link #speed} 或 {@link #tempo}) 发生变化, 否则该值不会变.
	 * </p>
	 */
	int tempoRemainder;
	
	/**
	 * 计算的每一帧需要扣掉的节奏数
	 */
	int tempoDecrement;
	
	/**
	 * 该帧是否更新了行
	 */
	boolean updateRow;
	
	public int getFrameRate() {
		return querier.getFrameRate();
	}
	
	/* **********
	 * 其它方法 *
	 ********** */
	
	public FtmRowFetcher(FamiTrackerRuntime runtime) {
		this.runtime = runtime;
		runtime.fetcher = this;
	}
	
	public void ready(FtmAudio audio, int track, int section) {
		this.querier = new FamiTrackerQuerier(audio);
		runtime.querier = querier;
		
		// 向 runtime.effects 中添加 map
		runtime.effects.clear();
		final int len = querier.channelCount();
		for (int i = 0; i < len; i++) {
			byte code = querier.channelCode(i);
			runtime.effects.put(code, new HashMap<>());
		}
		
		ready(track, section);
	}
	
	/**
	 * 不换曲目的 ready
	 * @param track
	 * @param section
	 */
	public void ready(int track, int section) {
		this.trackIdx = track;
		this.sectionIdx = section;
		
		resetSpeed();
		for (Map<FtmEffectType, IFtmEffect> map : runtime.effects.values()) {
			map.clear();
		}
	}
	
	/**
	 * <p>重置速度值和节奏值
	 * <p>以 {@link FtmTrack} 里面定义的速度为准
	 * </p>
	 */
	void resetSpeed() {
		speed = querier.audio.getTrack(trackIdx).speed;
		tempo = querier.audio.getTrack(trackIdx).tempo;
		
		setupSpeed();
		tempoAccum = 0;
		updateRow = false;
	}
	
	/**
	 * 重置 {@link #tempoDecrement} 和 {@link #tempoRemainder}
	 */
	void setupSpeed() {
		int i = tempo * 24;
		tempoDecrement = i / speed;
		tempoRemainder = i % speed;
	}
	
	/**
	 * 音乐向前跑一帧. 看看现在跑到 Ftm 的哪一行上
	 */
	public void runFrame() {
		// 重置
		updateRow = false;
		
		// (SoundGen.runFrame)
		if (tempoAccum <= 0) {
			// Enable this to skip rows on high tempos
			updateRow = true;
			handleJump();
			storeRow();
			nextRow();
		} else {
			runtime.converter.clear();
		}
	}
	
	/**
	 * <p>更新播放状态
	 * <p>这个调用是在 {@link FtmNote} 的效果处理完之后调用的.
	 * 这样可以确保改变 speed 的效果可以被计算进去.
	 * </p>
	 */
	public void updateState() {
		// (SoundGen.updatePlayer)
		if (tempoAccum <= 0) {
			int ticksPerSec = querier.getFrameRate();
			// 将拍 / 秒 -> 拍 / 分钟
			tempoAccum += (60 * ticksPerSec) - tempoRemainder;
		}
		tempoAccum -= tempoDecrement;
	}
	
	/**
	 * <p>处理跳行的情况.
	 * <p>如果上一帧有 Bxx Dxx 等跳着执行播放的效果触发,
	 * 则 {@link #jumpSection} 或 {@link #skipRow} 不等于 -1. 这时就直接进行跳转;
	 * </p>
	 */
	private void handleJump() {
		if (skipRow >= 0) {
			if (jumpSection >= 0) {
				sectionIdx = jumpSection;
				jumpSection = -1;
			} else {
				sectionIdx++;
			}
			row = skipRow;
			skipRow = -1;
		} else if (jumpSection >= 0) {
			sectionIdx = jumpSection;
			row = 0;
			jumpSection = -1;
		}
	}
	
	/**
	 * 确定现在正在播放的行, 让 {@link IFtmEffectConverter} 获取并处理
	 */
	public void storeRow() {
		final int len = querier.channelCount();
		runtime.converter.beforeConvert();
		
		for (int i = 0; i < len; i++) {
			runtime.converter.convert(querier.channelCode(i), querier.getNote(trackIdx, sectionIdx, i, row));
		}
	}
	
	/**
	 * 按照正常的习惯, 确定下一个播放的行.
	 * <p>一般而言, 下一个播放的行是该行的下一行, 但是在以下情况下, 会有变化:
	 * <p>当到某段的结尾, 会跳转到下一段的首行;
	 * </p>
	 */
	private void nextRow() {
		row++;
		// 是否到段尾
		int len = querier.maxRow(trackIdx); // 段长
		if (row >= len) {
			// 跳到下一段的第 0 行
			row = 0;
			sectionIdx++;
			
			// Loop
			if (sectionIdx >= querier.trackCount(trackIdx)) {
				sectionIdx = 0;
			}
		}
	}

}
