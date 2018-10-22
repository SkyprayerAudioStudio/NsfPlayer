# NsfPlayer

NsfPlayer (NSF 解析/播放器)，是一个基于 Java 的播放 NSF 文件的开源工程。
主要支持将 .nsf (NSF 文件)、 .ftm (FamiTracker 编辑文件) 这类 8-bit 音频文件转成音频采样流 (PCM byte 数组)
以便后面使用其它的软件、工具进行加工。
	
*	该分支 (branch: fami) 主要完成对 FTM 文件的支持。

*	ftm 目标支持版本：
	<br>FamiTracker 0.4.6

### 如何使用 (Instruction)

#### 安装 Installation

*	没有任何安装包. 你的 Java 工程只需要导入 Jar 包即可。
	<br>(当完成到一定程度, 我想也需要发布一些 Jar 包)。
	
#### 使用 How To Use

首先需要声明的是，这个工程的目标是输出 PCM 格式的 byte 数组 / 音频流，而不是播放声音，尽管它内置了默认的播放音频的组件。

---

##### 第一步：首先需要创建 ``FtmAudio`` 实例，封装 FTM 音频文件。

Step 1: Create ``FtmAudio`` instance encapsulates the data of the FTM file.

``` Java
String path = "src\\assets\\test\\JtS Stage 3.ftm";
FtmAudio audio = FamiTrackerApplication.app.open(path);
```

其中 path 就是 FTM 文件的路径。这个工程在 ``src\assets\test\`` 下也内置了部分测试时使用的 FTM 文件，
如果找不到其它可用的文件，你也可以使用它们。

---

##### 第二步：创建 ``FamiTrackerRenderer`` 实例，它是将 FTM 文件数据转化为音频流的渲染器。

Step 2: Create ``FamiTrackerRenderer`` instance which convents FTM file data to bytes array.

``` Java
FamiTrackerRenderer renderer = new FamiTrackerRenderer();
renderer.ready(audio, 0);
```

渲染器在这个工程里面扮演非常重要的角色。你可能也发现了这个类的功能很多，
有调整各轨道音量、关闭轨道、设置音频从哪个位置开始渲染等等。

上面使用的方法 ``ready()`` 其实有许多重载方法，来让你决定开始的播放位置。

---

##### 第三步：渲染.

Step 3: Render it using a loop block.

``` Java
BytesPlayer player = new BytesPlayer();
byte[] bs = new byte[2400];

while (true) {
	int size = renderer.render(bs, 0, bs.length);
	player.writeSamples(bs, 0, size);
	
	if (renderer.isFinished()) {
		break;
	}
}
```

因为使用循环的方式进行渲染，这里推荐新建一个新的线程 ``(new Thread(...))`` 来单独进行渲染工作。

类 ``BytesPlayer`` 其实就是一个单纯的播放 byte 数组的类。它就是我前面提到的内置了默认的播放音频的组件中的一个。
说句实在话，你如果会使用 javax 底层的音频组件，实际上并不需要使用这个类进行音频播放，我使用它单纯为了方便而已。

上面使用 2400 bytes 作为缓冲区的长度，但实际上你可以改变它。如何确定这个值视你主机的情况而定。
``FamiTrackerRenderer`` 默认渲染的音频流格式如下：

*	48000 Hz, 16 bit signed | little-endian, mono (单声道)

好了，到了这里，你就已经可以播放这个音频了。不过要注意的是，选取的 JtS Stage 3.ftm 音频文件
使用的是 FC (NES) 游戏 Raf 的世界 / 星际魂斗罗 (RAF world / Journey to Silius) 第三关的背景音乐，
它是无限循环的，所以上面的代码运行时，它会在循环块当中，一直循环下去。

### 原始工程 / 链接 (Link)

*	nsfplay (C++)
	<br>这个工程的创建之初是从 nsfplay C++ 项目中移植过来的。这个项目的链接是：
	<br>[bbbradsmith/nsfplay](https://github.com/bbbradsmith/nsfplay)

*	FamiTracker (C++)
	<br>这个工程也将支持将 .ftm 文件转化成音频流。项目链接：
	<br>[Camano/FamiTracker](https://github.com/Camano/FamiTracker)

### 工作进度 (Progress)

*	支持程度 (Support)

<table>
	<tr>
		<td width=10%>2A03 + 2A07</td>
		<td width=40% align="left">
			大部分效果已经支持, 除了以下几个方面:
			<br>DPCM 轨道效果 (DPCM Channel Effect): 未完成 Unfinished
			<br>Hxx, Ixx 的 sweep 效果 (Hardware Sweep Effect): 不支持 Unsupported
		</td>
	</tr>
	<tr>
		<td width=10%>VRC6</td>
		<td width=40% align="left">
			支持 Supported
		</td>
	</tr>
	<tr>
		<td width=10%>MMC5</td>
		<td width=40% align="left">
			支持 Supported
		</td>
	</tr>
	<tr>
		<td width=10%>FDS</td>
		<td width=40% align="left">
			未完成 Unfinished
		</td>
	</tr>
	<tr>
		<td width=10%>N163</td>
		<td width=40% align="left">
			未完成 Unfinished
		</td>
	</tr>
	<tr>
		<td width=10%>VRC7</td>
		<td width=40% align="left">
			未完成 Unfinished
		</td>
	</tr>
</table>

*	版本(Version)

当前版本为 v0.2.3，剩余的工作有
*	（FTM）测试 2A03 VRC6 MMC5 芯片是否能正常工作
*	（NSF）无
*	（Mixer）测试 XgmSoundMixer 是否能正常工作

下一个版本 v0.2.4 +
*	（FTM）补充 FDS 芯片的实现（文件读取、乐器、效果、音频输出）
*	（NSF）重构
*	（Mixer）支持 FDS 芯片部分
