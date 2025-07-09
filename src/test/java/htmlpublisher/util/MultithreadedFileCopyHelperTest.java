package htmlpublisher.util;

import hudson.model.TaskListener;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import hudson.FilePath;
import jenkins.util.Timer;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;

import java.io.Serial;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

class MultithreadedFileCopyHelperTest {

    @Test
    void testScanWithIOException() {

		// Check, that IOException on scanning for files is propagated
		assertThrows(IOException.class, () ->
			MultithreadedFileCopyHelper.copyRecursiveTo(new FilePath(new File("")),
					// Test Scanner that always throws IOException
					new DirScanner() {
						public void scan(File file, FileVisitor visitor) throws IOException {
							throw new IOException();
						}

						@Serial
						private static final long serialVersionUID = 1L;
					}, null, null, 1, Timer.get(), 10, TaskListener.NULL));

	}

    @Test
    void testWorkerWithTimeout() {

		// Simulate a scheduler where all threads are busy so our worker gets no free
		// slot and runs into timeout
		ExecutorService singleExecutorService = Executors.newSingleThreadScheduledExecutor();

		singleExecutorService.submit(() -> {
			Thread.sleep(10000);
			return true;
		});

		// Check, that we come to an end and a TimeoutException is propagated
		assertThrows(TimeoutException.class, () ->
			MultithreadedFileCopyHelper.copyRecursiveTo(new FilePath(new File("")), new DirScanner() {
				public void scan(File file, FileVisitor visitor) {
					// noop
				}

				@Serial
				private static final long serialVersionUID = 1L;
			}, null, null, // No target path, no description
					1, // Start one worker
					singleExecutorService, // Limit parallel processing to 1 thread
					1, // Timeout = 1 second (too short!)
					TaskListener.NULL));

	}

    @Test
    void testWorkerWithoutTimeout() throws Exception {

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

			@Serial
			private static final long serialVersionUID = 1L;
		}, new FilePath(new File("")), // Target dir
				null, // no description
				1, // Start one worker
				singleExecutorService, // Limit parallel processing to 1 thread
				10, // Timeout = 10 seconds (enough time!)
				TaskListener.NULL);

	}

}