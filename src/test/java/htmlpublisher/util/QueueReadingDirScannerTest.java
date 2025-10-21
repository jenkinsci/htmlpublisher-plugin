package htmlpublisher.util;

import java.io.File;
import java.io.IOException;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueReadingDirScannerTest {

    @Test
    void testVisitWithIOException() {

		UUID queueKey = UUID.randomUUID();
		FileEntryQueue queue = FileEntryQueue.getOrCreateQueue(queueKey);

		// Add two files
		queue.add(new File(""), "");
		queue.add(new File(""), "");

		QueueReadingDirScanner dirScanner = new QueueReadingDirScanner(queueKey);

		// Check, that IOException is propagated
		assertThrows(IOException.class, () ->
			dirScanner.scan(new File(""), new IOExceptionFileVisitor()));

		// Check, that queue is empty and closed because of exception on processing
		// first file (so no other worker should move on)
		assertThrows(InterruptedException.class, queue::take, "Queue must be empty and closed");

	}

}