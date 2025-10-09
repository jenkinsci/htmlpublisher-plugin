package htmlpublisher.util;

import java.io.File;
import java.io.IOException;

import hudson.util.DirScanner;
import hudson.util.FileVisitor;

import java.io.Serial;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DirScanningQueueWriterTest {

    @Test
    void testInvokeWithIOException() {

		UUID queueKey = UUID.randomUUID();
		FileEntryQueue queue = FileEntryQueue.getOrCreateQueue(queueKey);

		// Add a files
		queue.add(new File(""), "");

		DirScanningQueueWriter queueWriter = new DirScanningQueueWriter(new DirScanner() {
			public void scan(File file, FileVisitor visitor) throws IOException {
				throw new IOException();
			}

			@Serial
			private static final long serialVersionUID = 1L;
		}, queueKey);

		// Check, that IOException is propagated
		assertThrows(IOException.class, () ->
			queueWriter.invoke(new File(""), null));

		// Check, that queue is empty and closed because of exception on processing (so
		// no other worker should move on)
		assertThrows(InterruptedException.class, queue::take, "Queue must be closed");

	}

}