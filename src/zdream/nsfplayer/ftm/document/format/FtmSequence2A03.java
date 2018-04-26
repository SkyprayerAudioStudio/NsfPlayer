package zdream.nsfplayer.ftm.document.format;

/**
 * 2A03 类型的序列
 * @author Zdream
 * @date 2018-04-25
 */
public class FtmSequence2A03 implements IFtmSequence {
	
	/**
	 * 类型
	 */
	public FtmSequenceType type;
	
	/**
	 * 序号
	 */
	public int index;

	public FtmSequence2A03() {
		// do nothing
	}

	@Override
	public FtmSequenceType getType() {
		return type;
	}

	@Override
	public FtmChipType getChip() {
		return FtmChipType._2A03;
	}

	@Override
	public int getIndex() {
		return index;
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(24);
		b.append("2A03").append(' ').append("Sequence").append(' ').append('#').append(index);
		return b.toString();
	}

}
