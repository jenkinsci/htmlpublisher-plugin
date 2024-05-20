package htmlpublisher.util;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertFalse;

public class FileVisitorServiceTest {

	@Test
	public void testVisitNoEntry() throws IOException, InterruptedException {

		ExecutorService executorService = Executors.newFixedThreadPool(5);

		try {

			try (FileVisitorService fileVisitorService = new FileVisitorService(executorService, 5,
					new IOExceptionFileVisitor())) {

				// Shutdown all running workers
				fileVisitorService.close();

				// Check, that IllegalStateException is thrown
				assertThrows(IllegalStateException.class, () -> {
					// Add dummy file
					fileVisitorService.add(new File(""), "");
				});

				// No exception should be thrown
				fileVisitorService.throwCatchedException();

			}

		} finally {
			executorService.shutdownNow();
			executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		}

	}

	@Test
	public void testVisitSingleEntryWithIOException() throws InterruptedException {

		ExecutorService executorService = Executors.newFixedThreadPool(5);

		try {

			// Check, that IOException is propagated
			assertThrows(IOException.class, () -> {

				try (FileVisitorService fileVisitorService = new FileVisitorService(executorService, 5,
						new IOExceptionFileVisitor())) {

					// Add dummy file
					fileVisitorService.add(new File(""), "");

					// Shutdown all running workers and collect raised exceptions
					fileVisitorService.close();

					// Dummy file was corrupt --> exception should be thrown
					fileVisitorService.throwCatchedException();
				}

			});

		} finally {
			executorService.shutdownNow();
			executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		}

	}

	@Test
	public void testVisitLaterEntriesWithIOException() throws InterruptedException {

		ExecutorService executorService = Executors.newFixedThreadPool(1);

		try {

			// Check, that IOException is propagated
			assertThrows(IOException.class, () -> {

				try (FileVisitorService fileVisitorService = new FileVisitorService(executorService, 1,
						new IOExceptionFileVisitor())) {

					// Add multiple dummy files
					fileVisitorService.add(new File(""), "");
					FileVisitorService.VisitEntry laterEntry = fileVisitorService.add(new File(""), "");

					// Shutdown all running workers and collect raised exceptions
					fileVisitorService.close();

					assertFalse("Queue must not contain later entry anymore",
							fileVisitorService.queue.contains(laterEntry));

					// Dummy file was corrupt --> exception should be thrown
					fileVisitorService.throwCatchedException();

				}

			});

		} finally {
			executorService.shutdownNow();
			executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		}

	}

}