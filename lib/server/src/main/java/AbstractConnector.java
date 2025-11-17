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

package ab.squirrel.server;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.stream.Collectors;

import ab.squirrel.http.ComplianceViolation;
import ab.squirrel.io.ByteBufferPool;
import ab.squirrel.io.EndPoint;
import ab.squirrel.io.RetainableByteBuffer;
import ab.squirrel.server.internal.HttpConnection;
import ab.squirrel.util.StringUtil;
import ab.squirrel.util.annotation.ManagedAttribute;
import ab.squirrel.util.annotation.ManagedObject;
import ab.squirrel.util.component.ContainerLifeCycle;
import ab.squirrel.util.thread.AutoLock;
import ab.squirrel.util.thread.ScheduledExecutorScheduler;
import ab.squirrel.util.thread.Scheduler;
import ab.squirrel.util.thread.ThreadPoolBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedObject("Abstract implementation of the Connector Interface")
public abstract class AbstractConnector extends ContainerLifeCycle implements Connector
{
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractConnector.class);

    private final AutoLock _lock = new AutoLock();
    private final Condition _setAccepting = _lock.newCondition();
    private final Map<String, ConnectionFactory> _factories = new LinkedHashMap<>(); // Order is important on server side, so we use a LinkedHashMap
    private final Server _server;
    private final Executor _executor;
    private final Scheduler _scheduler;
    private final ByteBufferPool _bufferPool;
    private final Thread[] _acceptors;
    private final Set<EndPoint> _endpoints = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<EndPoint> _immutableEndPoints = Collections.unmodifiableSet(_endpoints);
    private Shutdown _shutdown;
    private long _idleTimeout = 30000;
    private long _shutdownIdleTimeout = 1000L;
    private String _defaultProtocol;
    private ConnectionFactory _defaultConnectionFactory;
    /* The name used to link up virtual host configuration to named connectors */
    private String _name;
    private int _cores;
    private int _acceptorPriorityDelta = -2;
    private boolean _accepting = true;
    private ThreadPoolBudget.Lease _lease;

    /**
     * @param server The {@link Server} this connector will be added to, must not be null
     * @param executor An {@link Executor} for this connector or null to use the Server's Executor
     * @param scheduler A {@link Scheduler} for this connector or null to use the Server's Scheduler
     * @param bufferPool A {@link ByteBufferPool} for this connector or null to use the Server's ByteBufferPool
     * @param acceptors the number of acceptor threads to use, or -1 for a default value.
     * If 0, then no acceptor threads will be launched and some other mechanism will need to be used to accept new connections.
     * @param factories The {@link ConnectionFactory} instances to use
     */
    public AbstractConnector(
        Server server,
        Executor executor,
        Scheduler scheduler,
        ByteBufferPool bufferPool,
        int acceptors,
        ConnectionFactory... factories)
    {
        _server = Objects.requireNonNull(server);

        _executor = executor != null ? executor : _server.getThreadPool();
        installBean(_executor, executor != null);

        _scheduler = scheduler != null ? scheduler : _server.getScheduler();
        installBean(_scheduler, scheduler != null);

        _bufferPool = bufferPool != null ? bufferPool : server.getByteBufferPool();
        installBean(_bufferPool, bufferPool != null);

        for (ConnectionFactory factory : factories)
        {
            addConnectionFactory(factory);
        }

        _cores = Runtime.getRuntime().availableProcessors();
        if (acceptors < 0)
            acceptors = 1;
        if (acceptors > _cores)
            LOG.warn("Acceptors should be <= availableProcessors: {} ", this);
        _acceptors = new Thread[acceptors];
    }

    @Override
    public Server getServer()
    {
        return _server;
    }

    @Override
    public Executor getExecutor()
    {
        return _executor;
    }

    @Override
    public ByteBufferPool getByteBufferPool()
    {
        return _bufferPool;
    }

    @Override
    @ManagedAttribute("The connection idle timeout in milliseconds")
    public long getIdleTimeout()
    {
        return _idleTimeout;
    }

    /**
     * <p>Returns the number of processors which are available to the Java virtual machine.</p>
     */
    public int availableProcessors()
    {
        return _cores;
    }

    /**
     * <p>Sets the maximum Idle time for a connection, which roughly translates to the {@link Socket#setSoTimeout(int)}
     * call, although with NIO implementations other mechanisms may be used to implement the timeout.</p>
     * <p>The max idle time is applied:</p>
     * <ul>
     * <li>When waiting for a new message to be received on a connection</li>
     * <li>When waiting for a new message to be sent on a connection</li>
     * </ul>
     * <p>This value is interpreted as the maximum time between some progress being made on the connection.
     * So if a single byte is read or written, then the timeout is reset.</p>
     *
     * @param idleTimeout the idle timeout
     */
    public void setIdleTimeout(long idleTimeout)
    {
        _idleTimeout = idleTimeout;
        if (_idleTimeout == 0)
            _shutdownIdleTimeout = 0;
        else if (_idleTimeout < _shutdownIdleTimeout)
            _shutdownIdleTimeout = Math.min(1000L, _idleTimeout);
    }

    public void setShutdownIdleTimeout(long idle)
    {
        _shutdownIdleTimeout = idle;
    }

    public long getShutdownIdleTimeout()
    {
        return _shutdownIdleTimeout;
    }

    /**
     * @return Returns the number of acceptor threads.
     */
    @ManagedAttribute("number of acceptor threads")
    public int getAcceptors()
    {
        return _acceptors.length;
    }

    @Override
    protected void doStart() throws Exception
    {
        if (!getBeans(ComplianceViolation.Listener.class).isEmpty() ||
            !getServer().getBeans(ComplianceViolation.Listener.class).isEmpty())
            LOG.warn("ComplianceViolation.Listeners must now be set on HttpConfiguration");

        getConnectionFactories().stream()
            .filter(ConnectionFactory.Configuring.class::isInstance)
            .map(ConnectionFactory.Configuring.class::cast)
            .forEach(configuring -> configuring.configure(this));

        _shutdown = new Shutdown(this)
        {
            @Override
            public boolean isShutdownDone()
            {
                if (!_endpoints.isEmpty())
                    return false;

                for (Thread a : _acceptors)
                {
                    if (a != null)
                        return false;
                }

                return true;
            }
        };

        if (_defaultProtocol == null)
            throw new IllegalStateException("No default protocol for " + this);
        _defaultConnectionFactory = getConnectionFactory(_defaultProtocol);
        if (_defaultConnectionFactory == null)
            throw new IllegalStateException("No protocol factory for default protocol '" + _defaultProtocol + "' in " + this);

        _lease = ThreadPoolBudget.leaseFrom(getExecutor(), this, _acceptors.length);

        super.doStart();

        for (int i = 0; i < _acceptors.length; i++)
        {
            Acceptor a = new Acceptor(i);
            addBean(a);
            getExecutor().execute(a);
        }

        LOG.info("Started {}", this);
    }

    protected void interruptAcceptors()
    {
        try (AutoLock lock = _lock.lock())
        {
            for (Thread thread : _acceptors)
            {
                if (thread != null)
                    thread.interrupt();
            }
        }
    }

    @Override
    public CompletableFuture<Void> shutdown()
    {
        Shutdown shutdown = _shutdown;
        if (shutdown == null)
            return CompletableFuture.completedFuture(null);

        // Signal for the acceptors to stop
        CompletableFuture<Void> done = shutdown.shutdown();
        interruptAcceptors();

        // Reduce the idle timeout of existing connections
        for (EndPoint ep : _endpoints)
            ep.setIdleTimeout(getShutdownIdleTimeout());

        // Return Future that waits for no acceptors and no connections.
        return done;
    }

    @Override
    public boolean isShutdown()
    {
        Shutdown shutdown = _shutdown;
        return shutdown == null || shutdown.isShutdown();
    }

    @Override
    protected void doStop() throws Exception
    {
        if (_lease != null)
            _lease.close();

        // Tell the acceptors we are stopping
        interruptAcceptors();
        super.doStop();
        for (Acceptor a : getBeans(Acceptor.class))
            removeBean(a);

        _shutdown = null;

        LOG.info("Stopped {}", this);
    }

    public void join() throws InterruptedException
    {
        join(0);
    }

    public void join(long timeout) throws InterruptedException
    {
        try (AutoLock lock = _lock.lock())
        {
            for (Thread thread : _acceptors)
            {
                if (thread != null)
                    thread.join(timeout);
            }
        }
    }

    protected abstract void accept(int acceptorID) throws IOException, InterruptedException;

    /**
     * @return Is the connector accepting new connections
     */
    public boolean isAccepting()
    {
        try (AutoLock lock = _lock.lock())
        {
            return _accepting;
        }
    }

    public void setAccepting(boolean accepting)
    {
        try (AutoLock l = _lock.lock())
        {
            _accepting = accepting;
            _setAccepting.signalAll();
        }
    }

    @Override
    public ConnectionFactory getConnectionFactory(String protocol)
    {
        try (AutoLock lock = _lock.lock())
        {
            return _factories.get(StringUtil.asciiToLowerCase(protocol));
        }
    }

    @Override
    public <T> T getConnectionFactory(Class<T> factoryType)
    {
        try (AutoLock lock = _lock.lock())
        {
            for (ConnectionFactory f : _factories.values())
            {
                if (factoryType.isAssignableFrom(f.getClass()))
                    return (T)f;
            }
            return null;
        }
    }

    public void addConnectionFactory(ConnectionFactory factory)
    {
        if (isRunning())
            throw new IllegalStateException(getState());

        Set<ConnectionFactory> toRemove = new HashSet<>();
        for (String key : factory.getProtocols())
        {
            key = StringUtil.asciiToLowerCase(key);
            ConnectionFactory old = _factories.remove(key);
            if (old != null)
            {
                if (old.getProtocol().equals(_defaultProtocol))
                    _defaultProtocol = null;
                toRemove.add(old);
            }
            _factories.put(key, factory);
        }

        // keep factories still referenced
        for (ConnectionFactory f : _factories.values())
        {
            toRemove.remove(f);
        }

        // remove old factories
        for (ConnectionFactory old : toRemove)
        {
            removeBean(old);
            if (LOG.isDebugEnabled())
                LOG.debug("{} removed {}", this, old);
        }

        // add new Bean
        addBean(factory);
        if (_defaultProtocol == null)
            _defaultProtocol = factory.getProtocol();
        if (LOG.isDebugEnabled())
            LOG.debug("{} added {}", this, factory);
    }

    public void addFirstConnectionFactory(ConnectionFactory factory)
    {
        if (isRunning())
            throw new IllegalStateException(getState());

        List<ConnectionFactory> existings = new ArrayList<>(_factories.values());
        clearConnectionFactories();
        addConnectionFactory(factory);
        for (ConnectionFactory existing : existings)
        {
            addConnectionFactory(existing);
        }
    }

    // Used from XML, do not remove.
    public void addIfAbsentConnectionFactory(ConnectionFactory factory)
    {
        if (isRunning())
            throw new IllegalStateException(getState());

        String key = StringUtil.asciiToLowerCase(factory.getProtocol());
        if (!_factories.containsKey(key))
            addConnectionFactory(factory);
    }

    public ConnectionFactory removeConnectionFactory(String protocol)
    {
        if (isRunning())
            throw new IllegalStateException(getState());

        ConnectionFactory factory = _factories.remove(StringUtil.asciiToLowerCase(protocol));
        if (_factories.isEmpty())
            _defaultProtocol = null;
        removeBean(factory);
        return factory;
    }

    @Override
    public Collection<ConnectionFactory> getConnectionFactories()
    {
        return _factories.values();
    }

    public void setConnectionFactories(Collection<ConnectionFactory> factories)
    {
        if (isRunning())
            throw new IllegalStateException(getState());

        List<ConnectionFactory> existing = new ArrayList<>(_factories.values());
        for (ConnectionFactory factory : existing)
        {
            removeConnectionFactory(factory.getProtocol());
        }
        for (ConnectionFactory factory : factories)
        {
            if (factory != null)
                addConnectionFactory(factory);
        }
    }

    public void clearConnectionFactories()
    {
        if (isRunning())
            throw new IllegalStateException(getState());

        _factories.clear();
        _defaultProtocol = null;
    }

    @ManagedAttribute("The priority delta to apply to acceptor threads")
    public int getAcceptorPriorityDelta()
    {
        return _acceptorPriorityDelta;
    }

    /**
     * Set the acceptor thread priority delta.
     * <p>This allows the acceptor thread to run at a different priority.
     * Typically this would be used to lower the priority to give preference
     * to handling previously accepted connections rather than accepting
     * new connections</p>
     *
     * @param acceptorPriorityDelta the acceptor priority delta
     */
    public void setAcceptorPriorityDelta(int acceptorPriorityDelta)
    {
        int old = _acceptorPriorityDelta;
        _acceptorPriorityDelta = acceptorPriorityDelta;
        if (old != acceptorPriorityDelta && isStarted())
        {
            for (Thread thread : _acceptors)
            {
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, thread.getPriority() - old + acceptorPriorityDelta)));
            }
        }
    }

    @Override
    @ManagedAttribute("Protocols supported by this connector")
    public List<String> getProtocols()
    {
        return new ArrayList<>(_factories.keySet());
    }

    @ManagedAttribute("This connector's default protocol")
    public String getDefaultProtocol()
    {
        return _defaultProtocol;
    }

    public void setDefaultProtocol(String defaultProtocol)
    {
        _defaultProtocol = StringUtil.asciiToLowerCase(defaultProtocol);
        if (isRunning())
            _defaultConnectionFactory = getConnectionFactory(_defaultProtocol);
    }

    @Override
    public ConnectionFactory getDefaultConnectionFactory()
    {
        if (isStarted())
            return _defaultConnectionFactory;
        return getConnectionFactory(_defaultProtocol);
    }

    protected boolean handleAcceptFailure(Throwable ex)
    {
        if (isRunning())
        {
            if (ex instanceof InterruptedException)
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("Accept Interrupted", ex);
                return true;
            }

            if (ex instanceof ClosedByInterruptException)
            {
                if (LOG.isDebugEnabled())
                    LOG.debug("Accept Closed by Interrupt", ex);
                return false;
            }

            LOG.warn("Accept Failure", ex);
            try
            {
                // Arbitrary sleep to avoid spin looping.
                // Subclasses may decide for a different
                // sleep policy or closing the connector.
                Thread.sleep(1000);
                return true;
            }
            catch (Throwable x)
            {
                LOG.trace("IGNORED", x);
            }
            return false;
        }
        else
        {
            LOG.trace("IGNORED", ex);
            return false;
        }
    }

    private class Acceptor implements Runnable
    {
        private final int _id;
        private String _name;

        private Acceptor(int id)
        {
            _id = id;
        }

        @Override
        public void run()
        {
            final Thread thread = Thread.currentThread();
            String name = thread.getName();
            _name = String.format("%s-acceptor-%d@%x-%s", name, _id, hashCode(), AbstractConnector.this.toString());
            thread.setName(_name);

            int priority = thread.getPriority();
            if (_acceptorPriorityDelta != 0)
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, priority + _acceptorPriorityDelta)));

            try (AutoLock l = _lock.lock())
            {
                _acceptors[_id] = thread;
            }

            try
            {
                while (isRunning() && !_shutdown.isShutdown())
                {
                    try (AutoLock l = _lock.lock())
                    {
                        if (!_accepting && isRunning())
                        {
                            _setAccepting.await();
                            continue;
                        }
                    }
                    catch (InterruptedException e)
                    {
                        continue;
                    }

                    try
                    {
                        accept(_id);
                    }
                    catch (Throwable x)
                    {
                        if (!handleAcceptFailure(x))
                            break;
                    }
                }
            }
            finally
            {
                thread.setName(name);
                if (_acceptorPriorityDelta != 0)
                    thread.setPriority(priority);

                try (AutoLock l = _lock.lock())
                {
                    _acceptors[_id] = null;
                }
                Shutdown shutdown = _shutdown;
                if (shutdown != null)
                    shutdown.check();
            }
        }

        @Override
        public String toString()
        {
            String name = _name;
            if (name == null)
                return String.format("acceptor-%d@%x", _id, hashCode());
            return name;
        }
    }

    @Override
    public Collection<EndPoint> getConnectedEndPoints()
    {
        return _immutableEndPoints;
    }

    protected void onEndPointOpened(EndPoint endp)
    {
        _endpoints.add(endp);
    }

    protected void onEndPointClosed(EndPoint endp)
    {
        _endpoints.remove(endp);
        Shutdown shutdown = _shutdown;
        if (shutdown != null)
            shutdown.check();
    }

    @Override
    public Scheduler getScheduler()
    {
        return _scheduler;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    /**
     * Set a connector name.   A context may be configured with
     * virtual hosts in the form "@contextname" and will only serve
     * requests from the named connector,
     *
     * @param name A connector name.
     */
    public void setName(String name)
    {
        _name = name;
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x{%s, %s}",
            _name == null ? getClass().getSimpleName() : _name,
            hashCode(),
            getDefaultProtocol(), getProtocols().stream().collect(Collectors.joining(", ", "(", ")")));
    }
}
