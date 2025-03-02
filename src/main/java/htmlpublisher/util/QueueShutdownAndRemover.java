package htmlpublisher.util;

import java.io.File;
import java.io.IOException;

import java.util.UUID;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;

import jenkins.security.Roles;

import org.jenkinsci.remoting.RoleChecker;

/**
 * Removes a queue from the cache on the node, where the data is located
 */
public class QueueShutdownAndRemover implements FilePath.FileCallable<Void> {

	private final UUID queueKey;

	public QueueShutdownAndRemover(UUID queueKey) {

		this.queueKey = queueKey;

	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check(this, Roles.SLAVE);
	}

	@Override
	public Void invoke(File f, VirtualChannel channel) throws IOException {

		FileEntryQueue queue = FileEntryQueue.remove(this.queueKey);

		if (queue != null) {
			queue.shutdownNow(); // Final call to shutdown the queue to ensure all workers get the kill signal
		}

		return null;

	}

	private static final long serialVersionUID = 1L;

}
