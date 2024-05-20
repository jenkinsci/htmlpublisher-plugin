package htmlpublisher.util;

import org.junit.Test;

import hudson.util.DirScanner;
import hudson.util.FileVisitor;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertThrows;

public class MultithreadedDirScannerTest {

	@Test
	public void testScanWithIOException() {

		MultithreadedDirScanner multithreadedDirScanner = new MultithreadedDirScanner(

				new DirScanner() { // Test Scanner, always throws IOException
					public void scan(File dir, FileVisitor visitor) throws IOException {
						visitor.visit(new File(""), "");
					}

					private static final long serialVersionUID = 1L;
				},

				1, // 1 worker

				Executors.newFixedThreadPool(5) // 5 threads

		);

		// Check, that IOException is propagated
		assertThrows(IOException.class, () -> {
			multithreadedDirScanner.scan(new File(""), new IOExceptionFileVisitor());
		});

	}

}