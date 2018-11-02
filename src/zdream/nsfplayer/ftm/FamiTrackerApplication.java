package zdream.nsfplayer.ftm;

import java.io.IOException;
import java.nio.charset.Charset;

import zdream.nsfplayer.ftm.audio.FtmAudio;
import zdream.nsfplayer.ftm.factory.FamiTrackerFormatException;
import zdream.nsfplayer.ftm.factory.FtmAudioFactory;

/**
 * 应用的实体. 用于打开 FamiTracker 的文件等操作
 * 
 * @author Zdream
 * @date 2018-04-25
 * @since v0.1
 */
public class FamiTrackerApplication {
	
	public static final FamiTrackerApplication app;
	
	public static Charset defCharset;
	
	static {
		defCharset = Charset.forName("UTF-8");
		app = new FamiTrackerApplication();
	}
	
	public FamiTrackerApplication() {
		factory = new FtmAudioFactory();
	}
	
	public final FtmAudioFactory factory;
	
	/**
	 * 加载 FamiTracker (.ftm) 的文件, 生成 {@link FtmAudio} 实例
	 * @param filePath
	 *   文件路径
	 */
	public FtmAudio open(String filePath) throws IOException, FamiTrackerFormatException {
		return factory.create(filePath);
	}
	
	/**
	 * 加载 FamiTracker 导出的文本文件 (.txt), 生成 {@link FtmAudio} 实例
	 * @param filePath
	 *   文件路径
	 * @since v0.2.5
	 */
	public FtmAudio openWithTxt(String filePath) throws IOException, FamiTrackerFormatException {
		return factory.createFromTextPath(filePath);
	}
	
}
