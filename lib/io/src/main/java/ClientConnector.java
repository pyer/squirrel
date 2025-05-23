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
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

import ab.squirrel.util.IO;
import ab.squirrel.util.Promise;
import ab.squirrel.util.annotation.ManagedAttribute;
import ab.squirrel.util.annotation.ManagedObject;
import ab.squirrel.util.component.ContainerLifeCycle;
import ab.squirrel.util.thread.QueuedThreadPool;
import ab.squirrel.util.thread.ScheduledExecutorScheduler;
import ab.squirrel.util.thread.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>The client-side component that connects to server sockets.</p>
 * <p>ClientConnector delegates the handling of {@link SocketChannel}s
 * to a {@link SelectorManager}, and centralizes the configuration of
 * necessary components such as the executor, the scheduler, etc.</p>
 * <p>ClientConnector offers a low-level API that can be used to
 * connect {@link SocketChannel}s to listening servers via the
 * {@link #connect(SocketAddress, Map)} method.</p>
 * <p>However, a ClientConnector instance is typically just configured
 * and then passed to an HttpClient transport, so that applications
 * can use high-level APIs to make HTTP requests to servers:</p>
 * <pre>
 * // Create a ClientConnector instance.
 * ClientConnector connector = new ClientConnector();
 *
 * // Configure the ClientConnector.
 * connector.setSelectors(1);
 * connector.setSslContextFactory(new SslContextFactory.Client());
 *
 * // Pass it to the HttpClient transport.
 * HttpClientTransport transport = new HttpClientTransportDynamic(connector);
 * HttpClient httpClient = new HttpClient(transport);
 * httpClient.start();
 * </pre>
 */
@ManagedObject
public class ClientConnector extends ContainerLifeCycle
{
    public static final String CLIENT_CONNECTOR_CONTEXT_KEY = "ab.squirrel.client.connector";
    public static final String REMOTE_SOCKET_ADDRESS_CONTEXT_KEY = CLIENT_CONNECTOR_CONTEXT_KEY + ".remoteSocketAddress";
    public static final String CLIENT_CONNECTION_FACTORY_CONTEXT_KEY = CLIENT_CONNECTOR_CONTEXT_KEY + ".clientConnectionFactory";
    public static final String CONNECTION_PROMISE_CONTEXT_KEY = CLIENT_CONNECTOR_CONTEXT_KEY + ".connectionPromise";
    public static final String APPLICATION_PROTOCOLS_CONTEXT_KEY = CLIENT_CONNECTOR_CONTEXT_KEY + ".applicationProtocols";
    private static final Logger LOG = LoggerFactory.getLogger(ClientConnector.class);

    private Executor executor;
    private Scheduler scheduler;
    private ByteBufferPool byteBufferPool;
    private SelectorManager selectorManager;
    private int selectors = 1;
    private boolean connectBlocking;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration idleTimeout = Duration.ofSeconds(30);
    private SocketAddress bindAddress;
    private boolean tcpNoDelay = true;
    private boolean reuseAddress = true;
    private boolean reusePort;
    private int receiveBufferSize = -1;
    private int sendBufferSize = -1;

    public ClientConnector()
    {
    }

    public SelectorManager getSelectorManager()
    {
        return selectorManager;
    }

    public Executor getExecutor()
    {
        return executor;
    }

    public void setExecutor(Executor executor)
    {
        if (isStarted())
            throw new IllegalStateException();
        updateBean(this.executor, executor);
        this.executor = executor;
    }

    public Scheduler getScheduler()
    {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler)
    {
        if (isStarted())
            throw new IllegalStateException();
        updateBean(this.scheduler, scheduler);
        this.scheduler = scheduler;
    }

    public ByteBufferPool getByteBufferPool()
    {
        return byteBufferPool;
    }

    public void setByteBufferPool(ByteBufferPool byteBufferPool)
    {
        if (isStarted())
            throw new IllegalStateException();
        updateBean(this.byteBufferPool, byteBufferPool);
        this.byteBufferPool = byteBufferPool;
    }

    /**
     * @return the number of NIO selectors
     */
    @ManagedAttribute("The number of NIO selectors")
    public int getSelectors()
    {
        return selectors;
    }

    public void setSelectors(int selectors)
    {
        if (isStarted())
            throw new IllegalStateException();
        this.selectors = selectors;
    }

    /**
     * @return whether {@link #connect(SocketAddress, Map)} operations are performed in blocking mode
     */
    @ManagedAttribute("Whether connect operations are performed in blocking mode")
    public boolean isConnectBlocking()
    {
        return connectBlocking;
    }

    public void setConnectBlocking(boolean connectBlocking)
    {
        this.connectBlocking = connectBlocking;
    }

    /**
     * @return the timeout of {@link #connect(SocketAddress, Map)} operations
     */
    @ManagedAttribute("The timeout of connect operations")
    public Duration getConnectTimeout()
    {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout)
    {
        this.connectTimeout = connectTimeout;
        if (selectorManager != null)
            selectorManager.setConnectTimeout(connectTimeout.toMillis());
    }

    /**
     * @return the max duration for which a connection can be idle (that is, without traffic of bytes in either direction)
     */
    @ManagedAttribute("The duration for which a connection can be idle")
    public Duration getIdleTimeout()
    {
        return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout)
    {
        this.idleTimeout = idleTimeout;
    }

    /**
     * @return the address to bind a socket to before the connect operation
     */
    @ManagedAttribute("The socket address to bind sockets to before the connect operation")
    public SocketAddress getBindAddress()
    {
        return bindAddress;
    }

    /**
     * <p>Sets the bind address of sockets before the connect operation.</p>
     * <p>In multi-homed hosts, you may want to connect from a specific address:</p>
     * <pre>
     * clientConnector.setBindAddress(new InetSocketAddress("127.0.0.2", 0));
     * </pre>
     * <p>Note the use of the port {@code 0} to indicate that a different ephemeral port
     * should be used for each different connection.</p>
     * <p>In the rare cases where you want to use the same port for all connections,
     * you must also call {@link #setReusePort(boolean) setReusePort(true)}.</p>
     *
     * @param bindAddress the socket address to bind to before the connect operation
     */
    public void setBindAddress(SocketAddress bindAddress)
    {
        this.bindAddress = bindAddress;
    }

    /**
     * @return whether small TCP packets are sent without delay
     */
    @ManagedAttribute("Whether small TCP packets are sent without delay")
    public boolean isTCPNoDelay()
    {
        return tcpNoDelay;
    }

    public void setTCPNoDelay(boolean tcpNoDelay)
    {
        this.tcpNoDelay = tcpNoDelay;
    }

    /**
     * @return whether rebinding is allowed with sockets in tear-down states
     */
    @ManagedAttribute("Whether rebinding is allowed with sockets in tear-down states")
    public boolean getReuseAddress()
    {
        return reuseAddress;
    }

    /**
     * <p>Sets whether it is allowed to bind a socket to a socket address
     * that may be in use by another socket in tear-down state, for example
     * in TIME_WAIT state.</p>
     * <p>This is useful when ClientConnector is restarted: an existing connection
     * may still be using a network address (same host and same port) that is also
     * chosen for a new connection.</p>
     *
     * @param reuseAddress whether rebinding is allowed with sockets in tear-down states
     * @see #setReusePort(boolean)
     */
    public void setReuseAddress(boolean reuseAddress)
    {
        this.reuseAddress = reuseAddress;
    }

    /**
     * @return whether binding to same host and port is allowed
     */
    @ManagedAttribute("Whether binding to same host and port is allowed")
    public boolean isReusePort()
    {
        return reusePort;
    }

    /**
     * <p>Sets whether it is allowed to bind multiple sockets to the same
     * socket address (same host and same port).</p>
     *
     * @param reusePort whether binding to same host and port is allowed
     */
    public void setReusePort(boolean reusePort)
    {
        this.reusePort = reusePort;
    }

    /**
     * @return the receive buffer size in bytes, or -1 for the default value
     */
    @ManagedAttribute("The receive buffer size in bytes")
    public int getReceiveBufferSize()
    {
        return receiveBufferSize;
    }

    public void setReceiveBufferSize(int receiveBufferSize)
    {
        this.receiveBufferSize = receiveBufferSize;
    }

    /**
     * @return the send buffer size in bytes, or -1 for the default value
     */
    @ManagedAttribute("The send buffer size in bytes")
    public int getSendBufferSize()
    {
        return sendBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize)
    {
        this.sendBufferSize = sendBufferSize;
    }

    @Override
    protected void doStart() throws Exception
    {
        if (executor == null)
        {
            QueuedThreadPool clientThreads = new QueuedThreadPool();
            clientThreads.setName(String.format("client-pool@%x", hashCode()));
            setExecutor(clientThreads);
        }
        if (scheduler == null)
            setScheduler(new ScheduledExecutorScheduler(String.format("client-scheduler@%x", hashCode()), false));
        if (byteBufferPool == null)
            setByteBufferPool(new ArrayByteBufferPool());
        selectorManager = newSelectorManager();
        selectorManager.setConnectTimeout(getConnectTimeout().toMillis());
        addBean(selectorManager);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception
    {
        super.doStop();
        removeBean(selectorManager);
    }

    protected SelectorManager newSelectorManager()
    {
        return new ClientSelectorManager(getExecutor(), getScheduler(), getSelectors());
    }

    public void connect(SocketAddress address, Map<String, Object> context)
    {
        SelectableChannel channel = null;
        try
        {
            context.put(ClientConnector.CLIENT_CONNECTOR_CONTEXT_KEY, this);

            Transport transport = (Transport)context.get(Transport.class.getName());

            if (address == null)
                address = transport.getSocketAddress();
            context.putIfAbsent(REMOTE_SOCKET_ADDRESS_CONTEXT_KEY, address);

            channel = transport.newSelectableChannel();
            configure(channel);

            if (channel instanceof NetworkChannel networkChannel)
            {
                SocketAddress bindAddress = getBindAddress();
                if (bindAddress != null)
                    bind(networkChannel, bindAddress);
                else if (networkChannel instanceof DatagramChannel)
                    bind(networkChannel, null);
            }

            boolean connected = true;
            if (channel instanceof SocketChannel socketChannel)
            {
                boolean blocking = isConnectBlocking() && address instanceof InetSocketAddress;
                if (LOG.isDebugEnabled())
                    LOG.debug("Connecting {} to {}", blocking ? "blocking" : "non-blocking", address);
                if (blocking)
                {
                    socketChannel.socket().connect(address, (int)getConnectTimeout().toMillis());
                    socketChannel.configureBlocking(false);
                }
                else
                {
                    socketChannel.configureBlocking(false);
                    connected = socketChannel.connect(address);
                }
            }
            else
            {
                channel.configureBlocking(false);
            }

            if (connected)
                selectorManager.accept(channel, context);
            else
                selectorManager.connect(channel, context);
        }
        // Must catch all exceptions, since some like
        // UnresolvedAddressException are not IOExceptions.
        catch (Throwable x)
        {
            // If IPv6 is not deployed, a generic SocketException "Network is unreachable"
            // exception is being thrown, so we attempt to provide a better error message.
            if (x.getClass() == SocketException.class)
                x = new SocketException("Could not connect to " + address).initCause(x);
            IO.close(channel);
            connectFailed(x, context);
        }
    }

    public void accept(SelectableChannel selectable, Map<String, Object> context)
    {
        try
        {
            SocketChannel channel = (SocketChannel)selectable;
            if (!channel.isConnected())
                throw new IllegalStateException("SocketChannel must be connected");

            context.put(ClientConnector.CLIENT_CONNECTOR_CONTEXT_KEY, this);

            configure(channel);
            channel.configureBlocking(false);
            selectorManager.accept(channel, context);
        }
        catch (Throwable failure)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Could not accept {}", selectable);
            IO.close(selectable);
            acceptFailed(failure, selectable, context);
        }
    }

    private void bind(NetworkChannel channel, SocketAddress bindAddress) throws IOException
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Binding {} to {}", channel, bindAddress);
        channel.bind(bindAddress);
    }

    protected void configure(SelectableChannel selectable) throws IOException
    {
        if (selectable instanceof NetworkChannel channel)
        {
            setSocketOption(channel, StandardSocketOptions.TCP_NODELAY, isTCPNoDelay());
            setSocketOption(channel, StandardSocketOptions.SO_REUSEADDR, getReuseAddress());
            setSocketOption(channel, StandardSocketOptions.SO_REUSEPORT, isReusePort());
            int receiveBufferSize = getReceiveBufferSize();
            if (receiveBufferSize >= 0)
                setSocketOption(channel, StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
            int sendBufferSize = getSendBufferSize();
            if (sendBufferSize >= 0)
                setSocketOption(channel, StandardSocketOptions.SO_SNDBUF, sendBufferSize);
        }
    }

    private <T> void setSocketOption(NetworkChannel channel, SocketOption<T> option, T value)
    {
        try
        {
            channel.setOption(option, value);
        }
        catch (Throwable x)
        {
            if (LOG.isTraceEnabled())
                LOG.trace("Could not configure {} to {} on {}", option, value, channel, x);
        }
    }

    protected EndPoint newEndPoint(SelectableChannel selectable, ManagedSelector selector, SelectionKey selectionKey)
    {
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>)selectionKey.attachment();
        Transport transport = (Transport)context.get(Transport.class.getName());
        return transport.newEndPoint(getScheduler(), selector, selectable, selectionKey);
    }

    protected Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException
    {
        Transport transport = (Transport)context.get(Transport.class.getName());
        return transport.newConnection(endPoint, context);
    }

    protected void acceptFailed(Throwable failure, SelectableChannel channel, Map<String, Object> context)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Could not accept {}", channel);
        Promise<?> promise = (Promise<?>)context.get(CONNECTION_PROMISE_CONTEXT_KEY);
        if (promise != null)
            promise.failed(failure);
    }

    protected void connectFailed(Throwable failure, Map<String, Object> context)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Could not connect to {}", context.get(REMOTE_SOCKET_ADDRESS_CONTEXT_KEY));
        Promise<?> promise = (Promise<?>)context.get(CONNECTION_PROMISE_CONTEXT_KEY);
        if (promise != null)
            promise.failed(failure);
    }

    protected class ClientSelectorManager extends SelectorManager
    {
        public ClientSelectorManager(Executor executor, Scheduler scheduler, int selectors)
        {
            super(executor, scheduler, selectors);
        }

        @Override
        protected EndPoint newEndPoint(SelectableChannel channel, ManagedSelector selector, SelectionKey selectionKey)
        {
            EndPoint endPoint = ClientConnector.this.newEndPoint(channel, selector, selectionKey);
            endPoint.setIdleTimeout(getIdleTimeout().toMillis());
            return endPoint;
        }

        @Override
        public Connection newConnection(SelectableChannel channel, EndPoint endPoint, Object attachment) throws IOException
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>)attachment;
            return ClientConnector.this.newConnection(endPoint, context);
        }

        @Override
        public void connectionOpened(Connection connection, Object context)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> contextMap = (Map<String, Object>)context;
            @SuppressWarnings("unchecked")
            Promise<Connection> promise = (Promise<Connection>)contextMap.get(CONNECTION_PROMISE_CONTEXT_KEY);
            try
            {
                super.connectionOpened(connection, context);
                // TODO: the block below should be moved to Connection.onOpen() in each implementation,
                //  so that each implementation can decide when to notify the promise, possibly not in onOpen().
                promise.succeeded(connection);
            }
            catch (Throwable x)
            {
                promise.failed(x);
            }
        }

        @Override
        protected void connectionFailed(SelectableChannel channel, Throwable failure, Object attachment)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>)attachment;
            connectFailed(failure, context);
        }
    }

}
