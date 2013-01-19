package ch.bergturbenthal.image.server.thumbnails;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import lombok.Cleanup;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FfmpegVideoThumbnailMaker implements VideoThumbnailMaker {
  private static final Logger log = LoggerFactory.getLogger(FfmpegVideoThumbnailMaker.class);
  private String binary;

  @Override
  public boolean makeVideoThumbnail(final File originalFile, final File thumbnailFile, final File tempDir) {
    // no valid binary found -> cannot convert
    if (binary == null)
      return false;
    final File tempFile = new File(tempDir, originalFile.getName() + "-tmp.mp4");
    if (tempFile.exists())
      tempFile.delete();
    final CommandLine cmdLine = new CommandLine(binary);
    cmdLine.addArguments(new String[] { "-i", originalFile.getAbsolutePath(), "-vcodec", "libx264", "-b:v", "1024k", "-profile:v", "baseline",
                                       "-b:a", "24k", "-vf", "yadif", "-vf", "scale=1280:720", "-acodec", "libvo_aacenc", "-sn", "-r", "30",
                                       tempFile.getAbsolutePath() }, false);
    final File logfile = new File(tempDir, originalFile.getName() + ".log");
    if (logfile.exists()) {
      logfile.renameTo(new File(tempDir, originalFile.getName() + ".log.1"));
    }
    boolean converted = false;
    try {
      @Cleanup
      final FileOutputStream logOutput = new FileOutputStream(logfile);
      logOutput.write((cmdLine.toString() + "\n").getBytes());
      converted = execute(cmdLine, TimeUnit.MINUTES.toMillis(5), logOutput);
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

  private boolean execute(final CommandLine cmdLine, final long timeout, final OutputStream output) {
    try {
      final Executor executor = new DefaultExecutor();
      if (output != null)
        executor.setStreamHandler(new PumpStreamHandler(output));
      executor.setWatchdog(new ExecuteWatchdog(timeout));
      final int result = executor.execute(cmdLine);
      return result == 0;
    } catch (final IOException e) {
      log.warn("Cannot execute " + cmdLine, e);
      return false;
    }
  }

  @PostConstruct
  private void init() {
    if (binary != null && testExecutable(binary)) {
      // binary is already configured and valid
    } else if (testExecutable("ffmpeg"))
      binary = "ffmpeg";
    else if (testExecutable("avconv"))
      binary = "avconv";
    else
      throw new RuntimeException("No ffmpeg-compatible video converter found");
  }

  private boolean testExecutable(final String executable) {
    if (executable.trim().length() == 0)
      return false;
    final CommandLine cmdLine = new CommandLine(executable);
    cmdLine.addArgument("-version");
    return execute(cmdLine, TimeUnit.SECONDS.toMillis(2), null);
  }

}
