package htmlpublisher.util;

import java.io.File;
import java.io.Serializable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.UUID;
import java.util.Map;

/**
 * A queue that contains files
 *
 * When reading entries, the queue returns a file or waits until a file is
 * added.<br>
 * Also implements signaling the end of the queue with shutdown operations.
 */
public class FileEntryQueue {

	/**
	 * Marker for the end of the queue
	 */
	private static final FileEntry POISON_PILL = new FileEntry(null, null);

	/**
	 * Local cache that contains queues
	 */
	private static final Map<UUID, FileEntryQueue> queues = new ConcurrentHashMap<>();

	/**
	 * Internal queue for managing the entries
	 */
	private final LinkedBlockingQueue<FileEntry> queue = new LinkedBlockingQueue<>();

	/**
	 * Number of all added files since the creation of this queue
	 */
	private final AtomicInteger overallCount = new AtomicInteger(0);

	/**
	 * Size of all added files since the creation of this queue
	 */
	private final AtomicLong overallSize = new AtomicLong(0);

	/**
	 * An entry in a queue
	 */
	public static class FileEntry implements Serializable {

		private File file;
		private String relativePath;

		public FileEntry(File file, String relativePath) {

			this.file = file;
			this.relativePath = relativePath;

		}

		public File getFile() {
			return this.file;
		}

		public String getRelativePath() {
			return this.relativePath;
		}

		private static final long serialVersionUID = 1L;

	}

	/**
	 * Some statistical data about the queue
	 */
	public static class Statistic implements Serializable {

		private int overallCount;
		private long overallSize;

		public Statistic(int overallCount, long overallSize) {
			this.overallCount = overallCount;
			this.overallSize = overallSize;
		}

		public int getOverallCount() {
			return this.overallCount;
		}

		public long getOverallSize() {
			return this.overallSize;
		}

		private static final long serialVersionUID = 1L;

	}

	/**
	 * Get the queue specified by the key from the cache. If the key does not exist,
	 * a new queue will be created and added to the cache. The queue cache is
	 * located only on the local machine (agent or controller) and will not be
	 * remotely synchronized.
	 */
	public static FileEntryQueue getOrCreateQueue(UUID queueKey) {

		return queues.computeIfAbsent(queueKey, key -> new FileEntryQueue());

	}

	/**
	 * Remove the queue specified by the key from the cache
	 * 
	 * @return the removed queue or null, if queue was not in the cache before
	 */
	public static FileEntryQueue remove(UUID queueKey) {

		return queues.remove(queueKey);

	}

	/**
	 * Inserts the specified file into this queue if it is possible to do so
	 * immediately without violating capacity restrictions
	 * 
	 * @return the newly created file entry
	 * @throws IllegalStateException if no space is currently available
	 * 
	 */
	public FileEntry add(File file, String relativePath) {

		FileEntry entry = new FileEntry(file, relativePath);

		this.queue.add(entry);

		this.overallCount.incrementAndGet();
		this.overallSize.addAndGet(file.length());

		return entry;

	}

	/**
	 * Retrieves and removes the head of this queue, waiting if necessary until an
	 * element becomes available.
	 * 
	 * @throws InterruptedException if the queue is closed
	 * 
	 */
	public FileEntry take() throws InterruptedException {

		FileEntry entry = this.queue.take();

		if (entry == POISON_PILL) {
			this.queue.add(FileEntryQueue.POISON_PILL);
			throw new InterruptedException();
		}

		return entry;

	}

	/**
	 * Shutdown the queue, so no new work will be accepted but the existing work
	 * remains until processed
	 */
	public void shutdown() {

		this.queue.add(POISON_PILL);

	}

	/**
	 * All workers should stop there work as we want to stop as soon as possible -
	 * regardless if there is more to do or not
	 */
	public void shutdownNow() {

		// Remove all upcoming work
		this.queue.clear();

		// Signal, that this is the end and no more work will come
		this.shutdown();

	}

	/**
	 * @return the number of all added files since the creation of this queue
	 */
	public int getOverallCount() {

		return this.overallCount.get();

	}

	/**
	 * @return the size of all added files since the creation of this queue
	 */
	public long getOverallSize() {

		return this.overallSize.get();

	}

	/**
	 * @return some statistic about this queue
	 */
	public Statistic getStatistic() {

		return new FileEntryQueue.Statistic(this.getOverallCount(), this.getOverallSize());

	}

}