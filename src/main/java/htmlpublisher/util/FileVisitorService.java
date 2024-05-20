package htmlpublisher.util;

import hudson.util.FileVisitor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.HashSet;
import java.util.Set;

import java.io.File;
import java.io.IOException;

/**
 * Manages file visits and dispatches them over multiple visit workers
 */
public class FileVisitorService implements AutoCloseable {

	/**
	 * Marker for stopping the worker
	 */
	private static final VisitEntry POISON_PILL = new VisitEntry(null, null);

	/**
	 * Origin visitor that does the "real" work
	 */
	private FileVisitor delegateVisitor;

	/**
	 * Files, that have been collected via directory scan
	 */
	BlockingQueue<VisitEntry> queue = new LinkedBlockingQueue<>();

	/**
	 * Workers, that delegates the collected files
	 */
	private Set<Future<IOException>> workers = new HashSet<>();

	/**
	 * Indicates that the visitor service is currently shutting down -> no more
	 * collects allowed
	 */
	private boolean shutdown = false;

	/**
	 * Exception that has been thrown while doing the work
	 */
	private IOException exception;

	static class VisitEntry {

		private File file;
		private String relativePath;

		VisitEntry(File file, String relativePath) {
			this.file = file;
			this.relativePath = relativePath;
		}

	}

	class Worker implements Callable<IOException> {

		@Override
		public IOException call() {

			try {
				while (true) {
					VisitEntry entry = queue.take();
					if (entry == POISON_PILL) {
						queue.add(POISON_PILL);
						throw new InterruptedException();
					}
					delegateVisitor.visit(entry.file, entry.relativePath);
				}
			} catch (InterruptedException e) {
				// noop
			} catch (IOException e) {
				shutdownNow(); // Clear all later tasks and signal shutdown
				return e; // Return IOException
			}

			return null; // No exception occurred
		}

	}

	public FileVisitorService(ExecutorService executorService, int numberOfWorkers, FileVisitor delegateVisitor) {

		this.delegateVisitor = delegateVisitor;

		for (int i = 0; i < numberOfWorkers; i++) {
			workers.add(executorService.submit(new Worker()));
		}

	}

	/**
	 * Add new work to our queue. If the service is already in shutdown mode, we
	 * will throw an {@link IllegalStateException}
	 */
	public VisitEntry add(File file, String relativePath) {
		if (shutdown)
			throw new IllegalStateException("Queue is shutting down");

		VisitEntry visitEntry = new VisitEntry(file, relativePath);
		queue.add(visitEntry);

		return visitEntry;
	}

	public void throwCatchedException() throws IOException {

		if (this.exception != null)
			throw this.exception;

	}

	/**
	 * Shutdown the service, so no new work will be accepted but the existing work
	 * will be executed by the running workers
	 */
	public void shutdown() {

		this.shutdown = true;
		this.queue.add(POISON_PILL);

	}

	/**
	 * All workers should stop there work as we want to stop as soon as possible -
	 * regardless if there is more to do or not
	 */
	public void shutdownNow() {

		// Remove all upcoming work
		this.queue.clear();

		// Inform workers, that this is the end and no more work will come
		this.shutdown();

	}

	@Override
	public void close() {

		// Inform workers, that there is the end and no more work will come
		this.shutdown();

		// Collect worker exceptions
		for (Future<IOException> worker : this.workers) {
			try {
				IOException exception = worker.get();
				if (this.exception == null)
					this.exception = exception;
			} catch (Exception e) {
				if (this.exception == null)
					this.exception = new IOException(e);
			}
		}

	}

}