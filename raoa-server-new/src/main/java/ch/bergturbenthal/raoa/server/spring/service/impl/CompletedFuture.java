package ch.bergturbenthal.raoa.server.spring.service.impl;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CompletedFuture<R> implements Future<R> {
	private final R value;

	public CompletedFuture(final R value) {
		this.value = value;
	}

	@Override
	public boolean cancel(final boolean mayInterruptIfRunning) {
		return false;
	}

	@Override
	public R get() {
		return value;
	}

	@Override
	public R get(final long timeout, final TimeUnit unit) {
		return value;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean isDone() {
		return true;
	}
}