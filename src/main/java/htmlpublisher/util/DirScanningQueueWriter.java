package htmlpublisher.util;

import java.io.File;
import java.io.IOException;

import java.util.UUID;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;

import jenkins.security.Roles;

import org.jenkinsci.remoting.RoleChecker;

/**
 * Executes a dir scanner and collects the scanned files into a queue on the
 * node, where the data is located
 */
public class DirScanningQueueWriter implements FilePath.FileCallable<FileEntryQueue.Statistic> {

	private final UUID queueKey;
	private final DirScanner dirScanner;

	/**
	 * Used to collect the scanned files to a queue
	 */
	private static class Visitor extends FileVisitor {

		private FileEntryQueue queue;

		public Visitor(FileEntryQueue queue) {
			this.queue = queue;
		}

		@Override
		public void visit(File file, String relativePath) {
			this.queue.add(file, relativePath);
		}

	}

	public DirScanningQueueWriter(DirScanner dirScanner, UUID queueKey) {

		this.queueKey = queueKey;
		this.dirScanner = dirScanner;

	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check(this, Roles.SLAVE);
	}

	@Override
	public FileEntryQueue.Statistic invoke(File f, VirtualChannel channel) throws IOException {

		// Find the queue
		FileEntryQueue queue = FileEntryQueue.getOrCreateQueue(this.queueKey);

		try {
			// Find the files use the provided dir scanner
			this.dirScanner.scan(f, new Visitor(queue));
		} catch (IOException e) {
			// Signal final end of queue, so our workers should exit now
			queue.shutdownNow();
			throw e;
		}

		// Signal normal end of queue, so our workers know when to exit
		queue.shutdown();

		return queue.getStatistic();

	}

	private static final long serialVersionUID = 1L;

}