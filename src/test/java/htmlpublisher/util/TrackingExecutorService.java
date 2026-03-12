package htmlpublisher.util;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A wrapper around ExecutorService that tracks all submitted tasks
 */
public class TrackingExecutorService implements ExecutorService {

	private final ExecutorService delegate;
	private final Set<Future<?>> trackedTasks = ConcurrentHashMap.newKeySet();

	public TrackingExecutorService(ExecutorService delegate) {
		this.delegate = delegate;
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		Future<T> future = delegate.submit(task);
		trackedTasks.add(future);
		return future;
	}

	@Override
	public Future<?> submit(Runnable task) {
		Future<?> future = delegate.submit(task);
		trackedTasks.add(future);
		return future;
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		Future<T> future = delegate.submit(task, result);
		trackedTasks.add(future);
		return future;
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		List<Future<T>> futures = delegate.invokeAll(tasks);
		trackedTasks.addAll(futures);
		return futures;
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException {
		List<Future<T>> futures = delegate.invokeAll(tasks, timeout, unit);
		trackedTasks.addAll(futures);
		return futures;
	}

	// Delegate other methods
	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		return delegate.invokeAny(tasks);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		return delegate.invokeAny(tasks, timeout, unit);
	}

	@Override
	public void shutdown() {
		delegate.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		return delegate.shutdownNow();
	}

	@Override
	public boolean isShutdown() {
		return delegate.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return delegate.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return delegate.awaitTermination(timeout, unit);
	}

	@Override
	public void execute(Runnable command) {
		delegate.execute(command);
	}

	public Set<Future<?>> getTrackedTasks() {
		return trackedTasks;
	}

}
