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

package ab.squirrel.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.WritePendingException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import ab.squirrel.util.BufferUtil;
import ab.squirrel.util.Callback;
import ab.squirrel.util.thread.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Partial implementation of EndPoint that uses {@link FillInterest} and {@link WriteFlusher}.</p>
 */
//public abstract class AbstractEndPoint extends IdleTimeout implements EndPoint
public abstract class AbstractEndPoint implements EndPoint
{
    private static final Logger LOG = LoggerFactory.getLogger(AbstractEndPoint.class);

    private final AtomicReference<State> _state = new AtomicReference<>(State.OPEN);
    private final long _created = System.currentTimeMillis();
    private volatile Connection _connection;
    private final Scheduler _scheduler;

    private final AtomicReference<Scheduler.Task> _timeout = new AtomicReference<>();
    private volatile long _idleTimeout = 0;
    private volatile long _idleNanoTime = System.nanoTime();


    private final FillInterest _fillInterest = new FillInterest()
    {
        @Override
        protected void needsFillInterest() throws IOException
        {
            AbstractEndPoint.this.needsFillInterest();
        }
    };
    private final WriteFlusher _writeFlusher = new WriteFlusher(this)
    {
        @Override
        protected void onIncompleteFlush()
        {
            AbstractEndPoint.this.onIncompleteFlush();
        }
    };

    protected AbstractEndPoint(Scheduler scheduler)
    {
        _scheduler = scheduler;
    }

    /**
     * @return the idle timeout in milliseconds
     * @see #setIdleTimeout(long)
     */
    public long getIdleTimeout()
    {
        return _idleTimeout;
    }

    /**
     * <p>Sets the idle timeout in milliseconds.</p>
     * <p>A value that is less than or zero disables the idle timeout checks.</p>
     *
     * @param idleTimeout the idle timeout in milliseconds
     * @see #getIdleTimeout()
     */
    public void setIdleTimeout(long idleTimeout)
    {
        long old = _idleTimeout;
        _idleTimeout = idleTimeout;

        if (LOG.isDebugEnabled())
            LOG.debug("Setting idle timeout {} -> {} on {}", old, idleTimeout, this);

        // Do we have an old timeout
        if (old > 0) {
            // if the old was less than or equal to the new timeout, then nothing more to do
            if (old <= idleTimeout)
                return;
            // old timeout is too long, so cancel it.
            deactivate();
        }
        // If we have a new timeout, then check and reschedule
        if (isOpen())
            activate();
    }

    /**
     * This method should be called when non-idle activity has taken place.
     */
    public void notIdle()
    {
        _idleNanoTime = System.nanoTime();
    }

    private void activate()
    {
        if (_idleTimeout > 0)
            idleCheck();
    }

    private void deactivate()
    {
        Scheduler.Task oldTimeout = _timeout.getAndSet(null);
        if (oldTimeout != null)
            oldTimeout.cancel();
    }

    protected long checkIdleTimeout()
    {
        if (isOpen())
        {
            // milliseconds elapsed between the given begin nanoTime and the current nanoTime
            long idleElapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - _idleNanoTime);
            long idleLeft = _idleTimeout - idleElapsed;

            if (LOG.isDebugEnabled())
                LOG.debug("{} idle timeout check, elapsed: {} ms, remaining: {} ms", this, idleElapsed, idleLeft);

            if (_idleTimeout > 0) {
                if (idleLeft <= 0) {
                    TimeoutException timeout = new TimeoutException("Idle timeout expired: " + idleElapsed + "/" + _idleTimeout + " ms");
                    try {
                        onIdleExpired(timeout);
                    }
                    finally {
                        notIdle();
                    }
                }
            }
            return idleLeft >= 0 ? idleLeft : 0;
        }
        return -1;
    }

    private void idleCheck()
    {
        long idleLeft = checkIdleTimeout();
        if (idleLeft >= 0)
            scheduleIdleTimeout(idleLeft > 0 ? idleLeft : _idleTimeout);
    }

    private void scheduleIdleTimeout(long delay)
    {
        Scheduler.Task newTimeout = null;
        if (isOpen() && delay > 0 && _scheduler != null)
            newTimeout = _scheduler.schedule(this::idleCheck, delay, TimeUnit.MILLISECONDS);
        Scheduler.Task oldTimeout = _timeout.getAndSet(newTimeout);
        if (oldTimeout != null)
            oldTimeout.cancel();
    }

/*
    @Override
    public InetSocketAddress getLocalAddress()
    {
        SocketAddress local = getLocalSocketAddress();
        if (local instanceof InetSocketAddress)
            return (InetSocketAddress)local;
        return null;
    }
*/

    @Override
    public abstract SocketAddress getLocalSocketAddress();

/*
    @Override
    public InetSocketAddress getRemoteAddress()
    {
        SocketAddress remote = getRemoteSocketAddress();
        if (remote instanceof InetSocketAddress)
            return (InetSocketAddress)remote;
        return null;
    }
*/
    @Override
    public abstract SocketAddress getRemoteSocketAddress();

    protected final void shutdownInput()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("shutdownInput {}", this);
        while (true)
        {
            State s = _state.get();
            switch (s)
            {
                case OPEN:
                    if (!_state.compareAndSet(s, State.ISHUTTING))
                        continue;
                    try
                    {
                        doShutdownInput();
                    }
                    finally
                    {
                        if (!_state.compareAndSet(State.ISHUTTING, State.ISHUT))
                        {
                            // If somebody else switched to CLOSED while we were ishutting,
                            // then we do the close for them
                            if (_state.get() == State.CLOSED)
                                doOnClose(null);
                        }
                    }
                    return;

                case ISHUTTING:  // Somebody else ishutting
                case ISHUT: // Already ishut
                    return;

                case OSHUTTING:
                    if (!_state.compareAndSet(s, State.CLOSED))
                        continue;
                    // The thread doing the OSHUT will close
                    return;

                case OSHUT:
                    if (!_state.compareAndSet(s, State.CLOSED))
                        continue;
                    // Already OSHUT so we close
                    doOnClose(null);
                    return;

                case CLOSED: // already closed
                    return;

                default:
                    throw new IllegalStateException(s.toString());
            }
        }
    }

    @Override
    public final void shutdownOutput()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("shutdownOutput {}", this);
        while (true)
        {
            State s = _state.get();
            switch (s)
            {
                case OPEN:
                    if (!_state.compareAndSet(s, State.OSHUTTING))
                        continue;
                    try
                    {
                        doShutdownOutput();
                    }
                    finally
                    {
                        if (!_state.compareAndSet(State.OSHUTTING, State.OSHUT))
                        {
                            // If somebody else switched to CLOSED while we were oshutting,
                            // then we do the close for them
                            if (_state.get() == State.CLOSED)
                                doOnClose(null);
                        }
                    }
                    return;

                case ISHUTTING:
                    if (!_state.compareAndSet(s, State.CLOSED))
                        continue;
                    // The thread doing the ISHUT will close
                    return;

                case ISHUT:
                    if (!_state.compareAndSet(s, State.CLOSED))
                        continue;
                    // Already ISHUT so we close
                    doOnClose(null);
                    return;

                case OSHUTTING:  // Somebody else oshutting
                case OSHUT: // Already oshut
                    return;

                case CLOSED: // already closed
                    return;

                default:
                    throw new IllegalStateException(s.toString());
            }
        }
    }

    @Override
    public final void close()
    {
        if (LOG.isDebugEnabled())
            LOG.debug("close {}", this);
        close(null);
    }

    public final void close(Throwable failure)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("close({}) {}", failure, this);
        while (true)
        {
            State s = _state.get();
            switch (s)
            {
                case OPEN:
                case ISHUT: // Already ishut
                case OSHUT: // Already oshut
                    if (!_state.compareAndSet(s, State.CLOSED))
                        continue;
                    doOnClose(failure);
                    return;

                case ISHUTTING: // Somebody else ishutting
                case OSHUTTING: // Somebody else oshutting
                    if (!_state.compareAndSet(s, State.CLOSED))
                        continue;
                    // The thread doing the IO SHUT will call doOnClose
                    return;

                case CLOSED: // already closed
                    return;

                default:
                    throw new IllegalStateException(s.toString());
            }
        }
    }

    protected void doShutdownInput()
    {
    }

    protected void doShutdownOutput()
    {
    }

    private void doOnClose(Throwable failure)
    {
        try
        {
            doClose();
        }
        finally
        {
            if (failure == null)
                onClose();
            else
                onClose(failure);
        }
    }

    protected void doClose()
    {
    }

    @Override
    public boolean isOutputShutdown()
    {
        return switch (_state.get())
        {
            case CLOSED, OSHUT, OSHUTTING -> true;
            default -> false;
        };
    }

    @Override
    public boolean isInputShutdown()
    {
        return switch (_state.get())
        {
            case CLOSED, ISHUT, ISHUTTING -> true;
            default -> false;
        };
    }

    @Override
    public boolean isOpen()
    {
        return _state.get() != State.CLOSED;
    }

    @Override
    public long getCreatedTimeStamp()
    {
        return _created;
    }

    @Override
    public Connection getConnection()
    {
        return _connection;
    }

    @Override
    public void setConnection(Connection connection)
    {
        _connection = connection;
    }

    protected void reset()
    {
        _state.set(State.OPEN);
        _writeFlusher.onClose();
        _fillInterest.onClose();
    }

    @Override
    public void onOpen()
    {
        activate();
        if (_state.get() != State.OPEN)
            throw new IllegalStateException();
    }

    public final void onClose()
    {
        deactivate();
        onClose(null);
    }

    @Override
    public void onClose(Throwable failure)
    {
        if (failure == null)
        {
            _writeFlusher.onClose();
            _fillInterest.onClose();
        }
        else
        {
            _writeFlusher.onFail(failure);
            _fillInterest.onFail(failure);
        }
    }

    @Override
    public void fillInterested(Callback callback)
    {
        notIdle();
        _fillInterest.register(callback);
    }

    @Override
    public boolean tryFillInterested(Callback callback)
    {
        notIdle();
        return _fillInterest.tryRegister(callback);
    }

    @Override
    public boolean isFillInterested()
    {
        return _fillInterest.isInterested();
    }

    @Override
    public void write(Callback callback, ByteBuffer... buffers) throws WritePendingException
    {
        _writeFlusher.write(callback, buffers);
    }

    protected abstract void onIncompleteFlush();

    protected abstract void needsFillInterest() throws IOException;

    public FillInterest getFillInterest()
    {
        return _fillInterest;
    }

    public WriteFlusher getWriteFlusher()
    {
        return _writeFlusher;
    }

    protected void onIdleExpired(TimeoutException timeout)
    {
        Connection connection = _connection;
        if (connection != null && !connection.onIdleExpired(timeout))
            return;

        boolean outputShutdown = isOutputShutdown();
        boolean inputShutdown = isInputShutdown();
        boolean fillFailed = _fillInterest.onFail(timeout);
        boolean writeFailed = _writeFlusher.onFail(timeout);
        boolean isOpen = isOpen();

        if (LOG.isDebugEnabled())
            LOG.debug("handled idle isOpen={} inputShutdown={} outputShutdown={} fillFailed={} writeFailed={} for {}",
                isOpen,
                inputShutdown,
                outputShutdown,
                fillFailed,
                writeFailed,
                this);

        // If the endpoint is open and there was no fill/write handling, then close here.
        if (isOpen && !(fillFailed || writeFailed))
            close(timeout);
    }

    @Override
    public void upgrade(Connection newConnection)
    {
        Connection oldConnection = getConnection();

        ByteBuffer buffer = (oldConnection instanceof Connection.UpgradeFrom)
            ? ((Connection.UpgradeFrom)oldConnection).onUpgradeFrom()
            : null;
        oldConnection.onClose(null);
        oldConnection.getEndPoint().setConnection(newConnection);

        if (LOG.isDebugEnabled())
            LOG.debug("{} upgrading from {} to {} with {}",
                this, oldConnection, newConnection, BufferUtil.toDetailString(buffer));

        if (BufferUtil.hasContent(buffer))
        {
            if (newConnection instanceof Connection.UpgradeTo)
                ((Connection.UpgradeTo)newConnection).onUpgradeTo(buffer);
            else
                throw new IllegalStateException("Cannot upgrade: " + newConnection + " does not implement " + Connection.UpgradeTo.class.getName());
        }

        newConnection.onOpen();
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x[%s]->[%s]", getClass().getSimpleName(), hashCode(), toEndPointString(), toConnectionString());
    }

    public String toEndPointString()
    {
        return String.format("{l=%s,r=%s,%s,fill=%s,flush=%s}",
            getLocalSocketAddress(),
            getRemoteSocketAddress(),
            _state.get(),
            _fillInterest.toStateString(),
            _writeFlusher.toStateString());
    }

    public String toConnectionString()
    {
        Connection connection = getConnection();
        if (connection == null) // can happen during upgrade
            return "<null>";
        if (connection instanceof AbstractConnection)
            return ((AbstractConnection)connection).toConnectionString();
        return String.format("%s@%x", connection.getClass().getSimpleName(), connection.hashCode());
    }

    private enum State
    {
        OPEN, ISHUTTING, ISHUT, OSHUTTING, OSHUT, CLOSED
    }
}
