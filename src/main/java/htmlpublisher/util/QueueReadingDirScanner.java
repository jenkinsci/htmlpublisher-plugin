package htmlpublisher.util;

import java.io.File;
import java.io.IOException;

import java.util.UUID;

import hudson.util.DirScanner;
import hudson.util.FileVisitor;

/**
 * Reads files from a queue and makes them accessible as dir scanner locally (on
 * the controller or on the agent)
 */
public class QueueReadingDirScanner extends DirScanner {

	private UUID queueKey;

	public QueueReadingDirScanner(UUID queueKey) {
		this.queueKey = queueKey;
	}

	@Override
	public void scan(File file, FileVisitor visitor) throws IOException {

		// Find the queue
		FileEntryQueue queue = FileEntryQueue.getOrCreateQueue(this.queueKey);

		try { // Process entries from the queue
			while (true) {

				FileEntryQueue.FileEntry entry = queue.take(); // throws InterruptedException on the end of the
																// queue
				visitor.visit(entry.getFile(), entry.getRegularPath());
			}
		} catch (InterruptedException e) {
			// noop, just exit
		} catch (IOException e) {
			queue.shutdownNow(); // Clear all later tasks and signal shutdown
			throw (e);
		}

	};

	private static final long serialVersionUID = 1L;

}
