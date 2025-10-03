package htmlpublisher.util;

import org.junit.Test;

import hudson.model.TaskListener;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import hudson.FilePath;
import jenkins.util.Timer;

import java.io.File;
import java.io.IOException;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
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

					}, new FilePath(new File("")), // Target dir
					null, // No description
					1, Timer.get(), 10, TaskListener.NULL);
		});

	}

	@Test
	public void testWorkerWithTimeoutCancellation() {

		final int numberOfWorkers = 2;

		// Simulate a scheduler where all threads are busy so our worker gets no free
		// slot and runs into timeout
		TrackingExecutorService executorService = new TrackingExecutorService(
				Executors.newSingleThreadScheduledExecutor());

		executorService.submit(() -> {
			Thread.sleep(10000);
			return true;
		});

		// Check, that we come to an end and a TimeoutException is propagated
		assertThrows(TimeoutException.class, () -> {

			MultithreadedFileCopyHelper.copyRecursiveTo(new FilePath(new File("")), new DirScanner() {
				public void scan(File file, FileVisitor visitor) throws IOException {
					// noop
				}

				private static final long serialVersionUID = 1L;
			}, new FilePath(new File("target-dir")), // Target dir
					null, // No description
					numberOfWorkers, // Start number of workers
					executorService, // Limit parallel processing to 1 thread
					0, // Raise immediate timeout
					TaskListener.NULL);

		});

		// Check, that our worker tasks are cancelled
		long cancelledCount = executorService.getTrackedTasks().stream().filter(Future::isCancelled).count();
		assertEquals("Expected our worker tasks to be cancelled", numberOfWorkers, cancelledCount);

	}

	@Test
	public void testWorkerWithoutTimeout() throws Exception {

		// Simulate a scheduler where all threads are busy so our worker needs to wait
		// some time but comes to an end
		ExecutorService singleExecutorService = Executors.newSingleThreadScheduledExecutor();

		singleExecutorService.submit(() -> {
			Thread.sleep(1000);
			return true;
		});

		// Check, that we come to an end
		MultithreadedFileCopyHelper.copyRecursiveTo(new FilePath(new File("")), new DirScanner() {
			public void scan(File file, FileVisitor visitor) {
				// noop
			}

			private static final long serialVersionUID = 1L;
		}, new FilePath(new File("")), // Target dir
				null, // no description
				1, // Start one worker
				singleExecutorService, // Limit parallel processing to 1 thread
				10, // Timeout = 10 seconds (enough time!)
				TaskListener.NULL);

	}

}