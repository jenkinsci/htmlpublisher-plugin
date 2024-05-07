package htmlpublisher.util;

import org.junit.Test;

import hudson.util.DirScanner;
import hudson.util.FileVisitor;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertThrows;

public class MultithreadedDirScannerTest {

	@Test
	public void testScanWithIOException() {
		MultithreadedDirScanner multithreadedDirScanner = new MultithreadedDirScanner(new DirScanner() {
			public void scan(File dir, FileVisitor visitor) throws IOException {
				visitor.visit(null, null);
			}

			private static final long serialVersionUID = 1L;
		}, 1);

		// Check, that IOException is propagated
		assertThrows(IOException.class, () -> {
			multithreadedDirScanner.scan(null, new IOExceptionFileVisitor());
		});
	}

}