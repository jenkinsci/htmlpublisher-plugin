package htmlpublisher.util;

import org.junit.Test;

import hudson.model.TaskListener;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import hudson.FilePath;
import jenkins.util.Timer;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertThrows;

public class MultithreadedFileCopyHelperTest {

	@Test
	public void testScanWithIOException() {

		// Check, that IOException on scanning for files is propagated
		assertThrows(IOException.class, () -> {
			MultithreadedFileCopyHelper.copyRecursiveTo(new FilePath(new File("")),
					// Test Scanner that always throws IOException
					new DirScanner() {
						public void scan(File file, FileVisitor visitor) throws IOException {
							throw new IOException();
						}

						private static final long serialVersionUID = 1L;
					}, null, null, 1, Timer.get(), TaskListener.NULL);
		});

	}

}