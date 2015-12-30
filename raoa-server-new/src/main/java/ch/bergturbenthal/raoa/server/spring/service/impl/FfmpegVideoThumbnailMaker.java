package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;

import ch.bergturbenthal.raoa.server.spring.service.ThumbnailMaker;
import lombok.Cleanup;

@Service
public class FfmpegVideoThumbnailMaker implements ThumbnailMaker {
	private static String[] binaryCandiates = new String[] { "ffmpeg", "avconv" };
	private static final Logger log = LoggerFactory.getLogger(FfmpegVideoThumbnailMaker.class);
	private String binary;

	private final ExecutorService executorService = new ThreadPoolExecutor(	0,
																																					1,
																																					30,
																																					TimeUnit.SECONDS,
																																					new ArrayBlockingQueue<Runnable>(20),
																																					new CustomizableThreadFactory("ffmpeg-pool-"));

	@Override
	public boolean canMakeThumbnail(final String filename) {
		final String lowerFilename = filename.toLowerCase();
		return lowerFilename.endsWith(".mp4") || lowerFilename.endsWith(".mkv") || lowerFilename.endsWith(".avi");
	}

	private boolean execute(final CommandLine cmdLine, final long timeout, final OutputStream output) {
		try {
			return executeInternal(cmdLine, timeout, output);
		} catch (final IOException e) {
			throw new RuntimeException("Cannot execute " + cmdLine, e);
		}
	}

	private boolean executeInternal(final CommandLine cmdLine, final long timeout, final OutputStream output) throws ExecuteException, IOException {
		final Executor executor = new DefaultExecutor();
		if (output != null) {
			executor.setStreamHandler(new PumpStreamHandler(output));
		}
		executor.setWatchdog(new ExecuteWatchdog(timeout));
		final int result = executor.execute(cmdLine);
		return result == 0;
	}

	@PostConstruct
	public void init() {
		if (binary != null && testExecutable(binary)) {
			// binary is already configured and valid
			return;
		}
		for (final String candidate : binaryCandiates) {
			if (testExecutable(candidate)) {
				binary = candidate;
				return;
			}
		}
		throw new RuntimeException("No ffmpeg-compatible video converter found");
	}

	@Override
	public boolean makeThumbnail(final File originalFile, final File thumbnailFile, final File tempDir) {
		final File tempFile = new File(tempDir, originalFile.getName() + "-tmp.mp4");
		if (tempFile.exists()) {
			tempFile.delete();
		}
		final String[] arguments;
		if (originalFile.length() < 1024 * 1024 * 1024) {
			arguments = new String[] {	"-i",
																	originalFile.getAbsolutePath(),
																	"-vcodec",
																	"libx264",
																	"-b:v",
																	"512k",
																	"-profile:v",
																	"baseline",
																	"-b:a",
																	"24k",
																	"-vf",
																	"yadif",
																	"-vf",
																	"scale=1280:-1",
																	"-acodec",
																	"libvo_aacenc",
																	"-sn",
																	"-r",
																	"30",
																	tempFile.getAbsolutePath() };
		} else {
			arguments = new String[] {	"-i",
																	originalFile.getAbsolutePath(),
																	"-vf",
																	"yadif",
																	"-vf",
																	"scale=480:-1",
																	"-vcodec",
																	"libx264",
																	"-b:v",
																	"256k",
																	"-profile:v",
																	"baseline",
																	"-b:a",
																	"24k",
																	"-acodec",
																	"libvo_aacenc",
																	"-aspect",
																	"16:9",
																	"-sn",
																	"-r",
																	"30",
																	tempFile.getAbsolutePath() };
		}

		return process(originalFile, thumbnailFile, tempFile, arguments);
	}

	@Override
	public boolean makeThumbnailImage(final File originalFile, final File thumbnailFile, final int thumbnailSize, final File tempDir) {
		final File tempFile = new File(tempDir, originalFile.getName() + "-tmp.jpg");
		if (tempFile.exists()) {
			tempFile.delete();
		}
		final String[] arguments = new String[] {	"-i",
																							originalFile.getAbsolutePath(),
																							"-vf",
																							"scale=" + thumbnailSize + ":-1",
																							"-vsync",
																							"1",
																							"-r",
																							"1",
																							"-an",
																							"-frames",
																							"1",
																							"-y",
																							tempFile.getAbsolutePath() };

		return process(originalFile, thumbnailFile, tempFile, arguments);
	}

	private boolean process(final File originalFile, final File thumbnailFile, final File tempFile, final String[] arguments) {
		// no valid binary found -> cannot convert
		if (binary == null) {
			return false;
		}
		final CommandLine cmdLine = new CommandLine(binary);
		cmdLine.addArguments(arguments, false);
		final File logfile = new File(tempFile.getParentFile(), originalFile.getName() + ".log");
		if (logfile.exists()) {
			logfile.renameTo(new File(tempFile.getParentFile(), originalFile.getName() + ".log.1"));
		}
		boolean converted = false;
		try {
			// give approximatly 5 hour per gigabyte input-length
			final long maximumTime = 5 * originalFile.length() * 3600 / 1024 / 1024 + TimeUnit.MINUTES.toMillis(5);
			@Cleanup
			final FileOutputStream logOutput = new FileOutputStream(logfile);
			logOutput.write((cmdLine.toString() + "\n").getBytes());
			converted = execute(cmdLine, maximumTime, logOutput);
		} catch (final IOException e) {
			throw new RuntimeException("Cannot write to logfile " + logfile);
		} finally {
			if (converted) {
				tempFile.renameTo(thumbnailFile);
			} else {
				tempFile.delete();
			}
		}
		return converted;
	}

	/**
	 * Override Binary
	 *
	 * @param binary
	 */
	public void setBinary(final String binary) {
		this.binary = binary;
	}

	@Override
	public <T> Future<T> submit(final Callable<T> callable) {
		return executorService.submit(callable);
	}

	private boolean testExecutable(final String executable) {
		if (executable.trim().length() == 0) {
			return false;
		}
		final CommandLine cmdLine = new CommandLine(executable);
		cmdLine.addArgument("-version");
		try {
			return executeInternal(cmdLine, TimeUnit.MINUTES.toMillis(2), null);
		} catch (final IOException e) {
			log.debug("Executable " + executable + " not found", e);
			return false;
		}
	}

}
