//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package ab.squirrel.util.thread;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ab.squirrel.util.StringUtil;
import ab.squirrel.util.annotation.ManagedAttribute;
import ab.squirrel.util.annotation.ManagedObject;
import ab.squirrel.util.annotation.Name;
import ab.squirrel.util.component.AbstractLifeCycle;

/**
 * Implementation of {@link Scheduler} based on JDK's {@link ScheduledThreadPoolExecutor}.
 * <p>
 * While use of {@link ScheduledThreadPoolExecutor} creates futures that will not be used,
 * it has the advantage of allowing to set a property to remove cancelled tasks from its
 * queue even if the task did not fire, which provides a huge benefit in the performance
 * of garbage collection in young generation.
 */
@ManagedObject
public class ScheduledExecutorScheduler extends AbstractLifeCycle implements Scheduler
{
    private final String name;
    private final boolean daemon;
    private final ClassLoader classloader;
    private final ThreadGroup threadGroup;
    private final int threads;
    private final AtomicInteger count = new AtomicInteger();
    private volatile ScheduledExecutorService scheduler;
    private volatile Thread thread;

    public ScheduledExecutorScheduler()
    {
        this(null, false);
    }

    public ScheduledExecutorScheduler(String name, boolean daemon)
    {
        this(name, daemon, null);
    }

    public ScheduledExecutorScheduler(@Name("name") String name, @Name("daemon") boolean daemon, @Name("threads") int threads)
    {
        this(name, daemon, null, null, threads);
    }

    public ScheduledExecutorScheduler(String name, boolean daemon, ClassLoader classLoader)
    {
        this(name, daemon, classLoader, null);
    }

    public ScheduledExecutorScheduler(String name, boolean daemon, ClassLoader classLoader, ThreadGroup threadGroup)
    {
        this(name, daemon, classLoader, threadGroup, -1);
    }

    /**
     * @param name The name of the scheduler threads or null for automatic name
     * @param daemon True if scheduler threads should be daemon
     * @param classLoader The classloader to run the threads with or null to use the current thread context classloader
     * @param threadGroup The threadgroup to use or null for no thread group
     * @param threads The number of threads to pass to the core {@link ScheduledExecutorService} or -1 for a
     * heuristic determined number of threads.
     */
    public ScheduledExecutorScheduler(@Name("name") String name, @Name("daemon") boolean daemon, @Name("classLoader") ClassLoader classLoader, @Name("threadGroup") ThreadGroup threadGroup, @Name("threads") int threads)
    {
        this.name = StringUtil.isBlank(name) ? "Scheduler-" + hashCode() : name;
        this.daemon = daemon;
        this.classloader = classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
        this.threadGroup = threadGroup;
        this.threads = threads;
    }

    /**
     * @param scheduledExecutorService the core {@link ScheduledExecutorService} to be used
     */
    public ScheduledExecutorScheduler(ScheduledExecutorService scheduledExecutorService)
    {
        this.name = null;
        this.daemon = false;
        this.classloader = null;
        this.threadGroup = null;
        this.threads = 0;
        this.scheduler = scheduledExecutorService;
    }

    @Override
    protected void doStart() throws Exception
    {
        if (this.scheduler == null)
        {
            int size = threads > 0 ? threads : 1;
            ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(size, r ->
            {
                Thread thread = ScheduledExecutorScheduler.this.thread = new Thread(threadGroup, r, name + "-" + count.incrementAndGet());
                thread.setDaemon(daemon);
                thread.setContextClassLoader(classloader);
                return thread;
            });
            scheduler.setRemoveOnCancelPolicy(true);
            this.scheduler = scheduler;
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception
    {
        // If name is set to null, this means we got the scheduler from the constructor.
        if (name != null)
        {
            scheduler.shutdownNow();
            scheduler = null;
        }
        super.doStop();
    }

    @Override
    public Task schedule(Runnable task, long delay, TimeUnit unit)
    {
        ScheduledExecutorService s = scheduler;
        if (s == null)
            return () -> false;
        ScheduledFuture<?> result = s.schedule(task, delay, unit);
        return new ScheduledFutureTask(result);
    }

    private static class ScheduledFutureTask implements Task
    {
        private final ScheduledFuture<?> scheduledFuture;

        ScheduledFutureTask(ScheduledFuture<?> scheduledFuture)
        {
            this.scheduledFuture = scheduledFuture;
        }

        @Override
        public boolean cancel()
        {
            return scheduledFuture.cancel(false);
        }
    }

    @ManagedAttribute("The name of the scheduler")
    public String getName()
    {
        return name;
    }

    @ManagedAttribute("Whether the scheduler uses daemon threads")
    public boolean isDaemon()
    {
        return daemon;
    }

    @ManagedAttribute("The number of scheduler threads")
    public int getThreads()
    {
        return threads;
    }
}
