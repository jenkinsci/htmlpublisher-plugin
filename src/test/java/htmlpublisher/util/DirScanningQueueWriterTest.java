package htmlpublisher.util;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import hudson.util.DirScanner;
import hudson.util.FileVisitor;

import java.util.UUID;

import static org.junit.Assert.assertThrows;

public class DirScanningQueueWriterTest {

	@Test
	public void testInvokeWithIOException() {

		UUID queueKey = UUID.randomUUID();
		FileEntryQueue queue = FileEntryQueue.getOrCreateQueue(queueKey);

		// Add a files
		queue.add(new File(""), "");

		DirScanningQueueWriter queueWriter = new DirScanningQueueWriter(new DirScanner() {
			public void scan(File file, FileVisitor visitor) throws IOException {
				throw new IOException();
			}

			private static final long serialVersionUID = 1L;
		}, queueKey);

		// Check, that IOException is propagated
		assertThrows(IOException.class, () -> {
			queueWriter.invoke(new File(""), null);
		});

		// Check, that queue is empty and closed because of exception on processing (so
		// no other worker should move on)
		assertThrows("Queue must be closed", InterruptedException.class, () -> {
			queue.take();
		});

	}

}