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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import ab.squirrel.http.DateGenerator;
import ab.squirrel.http.HttpField;
import ab.squirrel.http.HttpHeader;
import ab.squirrel.http.PreEncodedHttpField;
import ab.squirrel.io.ArrayByteBufferPool;
import ab.squirrel.io.ByteBufferPool;
import ab.squirrel.io.Connection;
import ab.squirrel.server.ErrorHandler;
import ab.squirrel.server.internal.ResponseHttpFields;
import ab.squirrel.util.Attributes;
import ab.squirrel.util.Callback;
import ab.squirrel.util.DecoratedObjectFactory;
import ab.squirrel.util.IO;
import ab.squirrel.util.annotation.ManagedAttribute;
import ab.squirrel.util.annotation.ManagedObject;
import ab.squirrel.util.annotation.Name;
import ab.squirrel.util.component.AttributeContainerMap;
import ab.squirrel.util.component.LifeCycle;
import ab.squirrel.util.resource.FileSystemPool;
import ab.squirrel.util.resource.Resource;
import ab.squirrel.util.thread.AutoLock;
import ab.squirrel.util.thread.QueuedThreadPool;
import ab.squirrel.util.thread.ScheduledExecutorScheduler;
import ab.squirrel.util.thread.Scheduler;
import ab.squirrel.util.thread.ShutdownThread;
import ab.squirrel.util.thread.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedObject
public class Server extends Handler.Abstract implements Attributes
{
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    private final AttributeContainerMap _attributes = new AttributeContainerMap();
    private final ThreadPool _threadPool;
    private final Scheduler _scheduler;
    private final ByteBufferPool _bufferPool;
    private final List<Connector> _connectors = new CopyOnWriteArrayList<>();
    private final List<Handler> _handlers = new ArrayList<>();

    private final Context _serverContext = new ServerContext();
    private final AutoLock _dateLock = new AutoLock();
    private Request.Handler _errorHandler = new ErrorHandler();
    private volatile DateField _dateField;

    public Server()
    {
        this(8080);
    }

    /**
     * Convenience constructor
     * Creates server and a {@link ServerConnector} at the passed port.
     *
     * @param port The port of a network HTTP connector (or 0 for a randomly allocated port).
     * @see Connector#getLocalPort()
     */
    public Server(@Name("port") int port)
    {
        _threadPool = new QueuedThreadPool();
        installBean(_threadPool);
        _scheduler = new ScheduledExecutorScheduler();
        installBean(_scheduler);
        _bufferPool = new ArrayByteBufferPool();
        installBean(_bufferPool);
        installBean(FileSystemPool.INSTANCE, false);

        ServerConnector connector = new ServerConnector(this);
        connector.setPort(port);
        _connectors.add(connector);
        addBean(connector);
        installBean(_attributes);
        LOG.info("Server created");
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception
    {
        if (_handlers == null || _handlers.isEmpty()) {
            return false;
        }

        String path = Request.getPathInContext(request);
        if (!path.startsWith("/")) {
            return false;
        }

        boolean ret = false;
        for (Handler handler: _handlers) {
            ret = handler.handle(request, response, callback);
            if (ret)
              break;
        }
        return ret;
    }

    /**
     * Get the {@link Context} associated with all {@link Request}s prior to being handled by a
     * {@link ContextHandler}. A {@code Server}'s {@link Context}:
     * <ul>
     *     <li>has a {@code null} {@link Context#getContextPath() context path}</li>
     *     <li>returns the {@link ClassLoader} that loaded the {@link Server} from {@link Context#getClassLoader()}.</li>
     *     <li>is an {@link java.util.concurrent.Executor} that delegates to the {@link Server#getThreadPool() Server ThreadPool}</li>
     *     <li>is a {@link ab.squirrel.util.Decorator} using the {@link DecoratedObjectFactory} found
     *     as a {@link #getBean(Class) bean} of the {@link Server}</li>
     * </ul>
     */
    public Context getContext()
    {
        return _serverContext;
    }

    public Request.Handler getErrorHandler()
    {
        return _errorHandler;
    }

    /*
     * Hhandlers
     */
    public void addHandler(Handler handler)
    {
      _handlers.add(handler);
    }

    /**
     * @return Returns the connectors.
     */
    @ManagedAttribute(value = "connectors for this server", readonly = true)
    public Connector[] getConnectors()
    {
        List<Connector> connectors = new ArrayList<>(_connectors);
        return connectors.toArray(new Connector[0]);
    }

    /**
     * @return Returns the threadPool.
     */
    @ManagedAttribute("The server Thread pool")
    public ThreadPool getThreadPool()
    {
        return _threadPool;
    }

    @ManagedAttribute("The server Scheduler")
    public Scheduler getScheduler()
    {
        return _scheduler;
    }

    @ManagedAttribute("The server ByteBuffer pool")
    public ByteBufferPool getByteBufferPool()
    {
        return _bufferPool;
    }

    /**
     * @return A {@link HttpField} instance efficiently recording the current time to a second resolution,
     * that cannot be cleared from a {@link ResponseHttpFields} instance.
     */
    public HttpField getDateField()
    {
        long now = System.currentTimeMillis();
        long seconds = now / 1000;
        DateField df = _dateField;

        if (df == null || df._seconds != seconds)
        {
            try (AutoLock ignore = _dateLock.lock())
            {
                df = _dateField;
                if (df == null || df._seconds != seconds)
                {
                    // Create a persistent HttpField
                    HttpField field = new PreEncodedHttpField(HttpHeader.DATE, DateGenerator.formatDate(now), true, null);
                    _dateField = new DateField(seconds, field);
                    return field;
                }
            }
        }
        return df._dateField;
    }

    @Override
    protected void doStart() throws Exception
    {
            //The Server should be stopped when the jvm exits, register
            //with the shutdown handler thread.
            ShutdownThread.register(this);

            // Open network connector to ensure ports are available
            _connectors.stream().filter(Connector.class::isInstance).map(Connector.class::cast).forEach(connector ->
                {
                    try {
                        connector.open();
                    }
                    catch (IOException e) {
                        LOG.error("open connector", e);
                    }
                });

            // Start the server and components, but not connectors!
            // #start(LifeCycle) is overridden so that connectors are not started
            super.doStart();

            // start connectors
            for (Connector connector : _connectors) {
                connector.start();
            }
            LOG.info(String.format("Start server %s", this));
    }

    @Override
    protected void start(LifeCycle l) throws Exception
    {
        // start connectors last
        if (!(l instanceof Connector))
            super.start(l);
    }

    @Override
    protected void doStop() throws Exception
    {
        System.out.println("");
        LOG.info(String.format("Stop server %s", this));

        // Now stop the connectors (this will close existing connections)
        for (Connector connector : _connectors) {
                connector.stop();
        }

        // And finally stop everything else
        super.doStop();
        ShutdownThread.deregister(this);
    }

    @Override
    public Object setAttribute(String name, Object attribute)
    {
        return _attributes.setAttribute(name, attribute);
    }

    @Override
    public Object removeAttribute(String name)
    {
        return _attributes.removeAttribute(name);
    }

    @Override
    public Object getAttribute(String name)
    {
        return _attributes.getAttribute(name);
    }

    @Override
    public Set<String> getAttributeNameSet()
    {
        return _attributes.getAttributeNameSet();
    }

    @Override
    public void clearAttributes()
    {
        _attributes.clearAttributes();
    }

    /**
     * @return The URI of the first {@link Connector} or null
     */
    public URI getURI()
    {
        Connector connector = null;
        for (Connector c : _connectors)
        {
            if (c instanceof Connector)
            {
                connector = (Connector)c;
                break;
            }
        }

        if (connector == null)
            return null;

        try
        {
            String scheme = "http";

            String host = connector.getHost();
            if (host == null)
                host = InetAddress.getLocalHost().getHostAddress();
            int port = connector.getLocalPort();

            String path = "/";
            return new URI(scheme, null, host, port, path, null, null);
        }
        catch (Exception e)
        {
            LOG.warn("Unable to build server URI", e);
            return null;
        }
    }

    public Server getServer()
    {
        return this;
    }

    @Override
    public String toString()
    {
        return super.toString();
    }

    private static class DateField
    {
        final long _seconds;
        final HttpField _dateField;

        public DateField(long seconds, HttpField dateField)
        {
            super();
            _seconds = seconds;
            _dateField = dateField;
        }
    }

    class ServerContext extends Attributes.Wrapper implements Context
    {

        private ServerContext()
        {
            super(Server.this);
        }

        @Override
        public String getContextPath()
        {
            return null;
        }

        @Override
        public ClassLoader getClassLoader()
        {
            return Server.class.getClassLoader();
        }

        @Override
        public Resource getBaseResource()
        {
            return null;
        }

        @Override
        public List<String> getVirtualHosts()
        {
            return Collections.emptyList();
        }

        @Override
        public void run(Runnable runnable)
        {
            runnable.run();
        }

        @Override
        public void run(Runnable runnable, Request request)
        {
            runnable.run();
        }

        @Override
        public void execute(Runnable runnable)
        {
            getThreadPool().execute(runnable);
        }

        @Override
        public Request.Handler getErrorHandler()
        {
            return Server.this.getErrorHandler();
        }

        @Override
        public <T> T decorate(T o)
        {
            // TODO cache factory lookup?
            DecoratedObjectFactory factory = Server.this.getBean(DecoratedObjectFactory.class);
            if (factory != null)
                return factory.decorate(o);
            return o;
        }

        @Override
        public void destroy(Object o)
        {
            // TODO cache factory lookup?
            DecoratedObjectFactory factory = Server.this.getBean(DecoratedObjectFactory.class);
            if (factory != null)
                factory.destroy(o);
        }

        @Override
        public String getPathInContext(String canonicallyEncodedPath)
        {
            return canonicallyEncodedPath;
        }

        @Override
        public String toString()
        {
            return "ServerContext@%x".formatted(Server.this.hashCode());
        }
    }

}
