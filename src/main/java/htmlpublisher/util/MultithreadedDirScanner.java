package htmlpublisher.util;

import hudson.util.DirScanner;
import hudson.util.FileVisitor;

import java.util.concurrent.ExecutorService;

import java.io.File;
import java.io.IOException;

public class MultithreadedDirScanner extends DirScanner {

	private DirScanner delegateDirScanner;
	private int numberOfWorkers;
	private ExecutorService executorService;

	public MultithreadedDirScanner(DirScanner delegateDirScanner, int numberOfWorkers,
			ExecutorService executorService) {
		this.delegateDirScanner = delegateDirScanner;
		this.numberOfWorkers = numberOfWorkers;
		this.executorService = executorService;
	}

	@Override
	public void scan(File dir, FileVisitor visitor) throws IOException {

		// Use visitor service handling multiple workers
		try (FileVisitorService fileVisitorService = new FileVisitorService(this.executorService, this.numberOfWorkers,
				visitor)) {

			try {
				// Delegate directory scan to visit workers using visitor service
				this.delegateDirScanner.scan(dir, new FileVisitor() {
					public void visit(File file, String relativePath) {
						// Add file to visitor service
						fileVisitorService.add(file, relativePath);
					}
				});

			} finally {
				// FINAL Shutdown & terminate visitor service including all workers
				fileVisitorService.close();

				// Propagate exception, if there has been one
				fileVisitorService.throwCatchedException();
			}

		}

	}

	private static final long serialVersionUID = 1L;

}