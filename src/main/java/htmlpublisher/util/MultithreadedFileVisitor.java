package htmlpublisher.util;

import hudson.util.FileVisitor;

import java.io.File;
import java.io.IOException;

import java.util.concurrent.ExecutorService;

public class MultithreadedFileVisitor extends FileVisitor {

	ExecutorService executorService;
	FileVisitor delegateVisitor;

	private IOException exception = null;

	public MultithreadedFileVisitor(ExecutorService executorService, FileVisitor delegateVisitor) {
		this.executorService = executorService;
		this.delegateVisitor = delegateVisitor;
	}

	@Override
	public void visit(File f, String relativePath) throws IOException {
		// Add file to executor service
		this.executorService.submit(() -> {
			try {
				// Delegate visit to origin visitor
				this.delegateVisitor.visit(f, relativePath);
			} catch (IOException e) {
				// Something went wrong: Stop parallel visits and save exception
				this.executorService.shutdownNow();
				this.exception = e;
			}
		});

	}

	public void throwCatchedException() throws IOException {
		if (this.exception != null)
			throw this.exception;
	}

}