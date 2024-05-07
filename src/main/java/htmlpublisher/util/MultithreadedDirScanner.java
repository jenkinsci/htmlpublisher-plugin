package htmlpublisher.util;

import hudson.util.DirScanner;
import hudson.util.FileVisitor;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import java.io.File;
import java.io.IOException;

public class MultithreadedDirScanner extends DirScanner {

	private DirScanner delegateDirScanner;
	private int numberOfThreads;

	public MultithreadedDirScanner(DirScanner delegateDirScanner, int numberOfThreads) {
		this.delegateDirScanner = delegateDirScanner;
		this.numberOfThreads = numberOfThreads;
	}

	@Override
	public void scan(File dir, FileVisitor visitor) throws IOException {
		// Create new executor service
		ExecutorService executorService = Executors.newFixedThreadPool(this.numberOfThreads);

		// Create visitor to delegate multithreaded
		MultithreadedFileVisitor multithreadedFileVisitor = new MultithreadedFileVisitor(executorService, visitor);

		try {
			// Delegate directory scan using multithreaded visitor
			this.delegateDirScanner.scan(dir, multithreadedFileVisitor);
		} finally {
			// Shutdown & terminate executor service
			executorService.shutdown();
			try {
				executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			} catch (InterruptedException e) {
				throw new IOException(e);
			}

		}

		// Propagate exception
		multithreadedFileVisitor.throwCatchedException();
	}

	private static final long serialVersionUID = 1L;

}