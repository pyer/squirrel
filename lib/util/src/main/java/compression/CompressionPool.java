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

package ab.squirrel.util.compression;

import java.io.Closeable;

import ab.squirrel.util.ConcurrentPool;
import ab.squirrel.util.IO;
import ab.squirrel.util.Pool;
import ab.squirrel.util.annotation.ManagedObject;
import ab.squirrel.util.component.ContainerLifeCycle;

@ManagedObject
public abstract class CompressionPool<T> extends ContainerLifeCycle
{
    public static final int DEFAULT_CAPACITY = 1024;

    private int _capacity;
    private Pool<Entry> _pool;

    /**
     * Create a Pool of {@link T} instances.
     *
     * If given a capacity equal to zero the Objects will not be pooled
     * and will be created on acquire and ended on release.
     * If given a negative capacity equal to zero there will be no size restrictions on the Pool
     *
     * @param capacity maximum number of Objects which can be contained in the pool
     */
    public CompressionPool(int capacity)
    {
        _capacity = capacity;
    }

    public int getCapacity()
    {
        return _capacity;
    }

    public void setCapacity(int capacity)
    {
        if (isStarted())
            throw new IllegalStateException("Already Started");
        _capacity = capacity;
    }

    public Pool<Entry> getPool()
    {
        return _pool;
    }

    protected abstract T newPooled();

    protected abstract void end(T object);

    protected abstract void reset(T object);

    /**
     * @return Object taken from the pool if it is not empty or a newly created Object
     */
    public Entry acquire()
    {
        Entry entry = null;
        if (_pool != null)
        {
            Pool.Entry<Entry> acquiredEntry = _pool.acquire(e -> new Entry(newPooled(), e));
            if (acquiredEntry != null)
                entry = acquiredEntry.getPooled();
        }

        return (entry == null) ? new Entry(newPooled()) : entry;
    }

    /**
     * Release an Entry.
     * @param entry returns this Object to the pool or calls {@link #end(Object)} if the pool is full.
     */
    public void release(Entry entry)
    {
        entry.release();
    }

    @Override
    protected void doStart() throws Exception
    {
        if (_capacity > 0)
        {
            _pool = new ConcurrentPool<>(ConcurrentPool.StrategyType.THREAD_ID, _capacity);
            addBean(_pool);
        }
        super.doStart();
    }

    @Override
    public void doStop() throws Exception
    {
        super.doStop();
        if (_pool != null)
        {
            removeBean(_pool);
            _pool.terminate().stream()
                .map(Pool.Entry::getPooled)
                .forEach(IO::close);
        }
    }

    public class Entry implements Closeable
    {
        private final T _value;
        private final Pool.Entry<Entry> _entry;

        Entry(T value)
        {
            this(value, null);
        }

        Entry(T value, Pool.Entry<Entry> entry)
        {
            _value = value;
            _entry = entry;
        }

        public T get()
        {
            return _value;
        }

        public void release()
        {
            // Reset the value for the next usage.
            reset(_value);

            if (_entry != null)
            {
                // If release return false, the entry should be removed and the object should be disposed.
                if (!_entry.release())
                {
                    if (_entry.remove())
                        close();
                }
            }
            else
            {
                close();
            }
        }

        @Override
        public void close()
        {
            end(_value);
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x{%s,size=%d,capacity=%s}",
            getClass().getSimpleName(),
            hashCode(),
            getState(),
            (_pool == null) ? -1 : _pool.size(),
            _capacity);
    }
}
