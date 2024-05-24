package htmlpublisher.util;

import java.io.IOException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import hudson.FilePath;
import hudson.util.DirScanner;

/**
 * Provides copying of files from the node (controller or agent) to the
 * controller using multiple threads.
 * 
 * On the node (controller or agent) the directory and sub-directories are
 * scanned and all files found are collected into a temporary queue on the node.
 * <br>
 * Beside this, multiple copy-workers are started on the controller: They are
 * calling the node (controller or agent) to copy the files to the controller.
 * This processes takes the previously collected files from the queue on the
 * node and transfers them to the controller. <br>
 * Finally, the node (controller or agent) is requested to cleanup the queue as
 * it is not needed anymore.
 */
public class MultithreadedFileCopyHelper {

	/**
	 * Copies files according to a specified scanner to the controller
	 */
	static public int copyRecursiveTo(FilePath archiveDir, DirScanner dirScanner, FilePath targetDir,
			String description, int numberOfWorkers, ExecutorService executorService)
			throws IOException, InterruptedException {

		// Generating a queue key, that is used for the scanner and inside each worker
		// for finding our queue
		UUID queueKey = UUID.randomUUID();

		try {
			// -------------------------------------------------------------
			// Start multiple copy workers on the node (controller or agent)
			// -------------------------------------------------------------
			Set<Future<Integer>> workers = new HashSet<>();
			for (int i = 0; i < numberOfWorkers; i++) {
				workers.add(executorService.submit(() -> {
					QueueReadingDirScanner queueReadingDirScanner = new QueueReadingDirScanner(queueKey);
					return archiveDir.copyRecursiveTo(queueReadingDirScanner, targetDir, description);
				}));
			}

			// ---------------------------------------------------------
			// Scan files / Fill queue on the node (controller or agent)
			// ---------------------------------------------------------
			archiveDir.act(new DirScanningQueueWriter(dirScanner, queueKey));

			// --------------------------------------------
			// Collect the results on the controller
			// --------------------------------------------
			int transferedFiles = 0;
			for (Future<Integer> worker : workers) {
				try {
					transferedFiles += worker.get(); // Wait workers to finish
				} catch (ExecutionException e) {
					throw new IOException(e);
				}
			}

			// Returning number of transfered files
			return transferedFiles;

		} finally {
			// ----------------------------------------------------------------------------
			// Remove queue as we are ended, especially on previous errors that might leave
			// a corrupt state
			// ----------------------------------------------------------------------------
			archiveDir.act(new QueueShutdownAndRemover(queueKey));
		}

	}

}