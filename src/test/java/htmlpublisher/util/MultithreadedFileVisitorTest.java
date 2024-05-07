package htmlpublisher.util;

import org.junit.Test;

import java.io.IOException;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MultithreadedFileVisitorTest {

	@Test
	public void testVisitWithIOException() throws IOException, InterruptedException {
		ExecutorService executorService = Executors.newFixedThreadPool(1);

		try {

			MultithreadedFileVisitor visitor = new MultithreadedFileVisitor(executorService,
					new IOExceptionFileVisitor());
			visitor.visit(null, null);

			// Check, that IOException is propagated
			assertThrows(IOException.class, () -> {
				visitor.throwCatchedException();
			});

			// Check, that executor Service has been shut down
			assertTrue(executorService.isShutdown());

		} finally {
			executorService.shutdownNow();
			executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		}
	}

}