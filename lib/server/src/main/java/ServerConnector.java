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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.EventListener;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import ab.squirrel.io.ByteBufferPool;
import ab.squirrel.io.Connection;
import ab.squirrel.io.EndPoint;
import ab.squirrel.io.ManagedSelector;
import ab.squirrel.io.SelectorManager;
import ab.squirrel.io.SocketChannelEndPoint;
import ab.squirrel.util.IO;
import ab.squirrel.util.annotation.ManagedAttribute;
import ab.squirrel.util.annotation.ManagedObject;
import ab.squirrel.util.annotation.Name;
import ab.squirrel.util.thread.Scheduler;

@ManagedObject("HTTP connector using NIO ByteChannels and Selectors")
public class ServerConnector extends AbstractConnector
{
    private final SelectorManager _manager;
    private final AtomicReference<Closeable> _acceptor = new AtomicReference<>();
    private volatile ServerSocketChannel _acceptChannel;
    private volatile int _localPort = -1;
    private volatile int _acceptQueueSize = 0;
    private volatile boolean _reuseAddress = true;
    private volatile boolean _reusePort = false;
    private volatile boolean _acceptedTcpNoDelay = true;
    private volatile int _acceptedReceiveBufferSize = -1;
    private volatile int _acceptedSendBufferSize = -1;

    public ServerConnector(
        @Name("server") Server server)
    {
        super(server);
        _manager = new ServerConnectorManager(getExecutor(), getScheduler());
        installBean(_manager, true);
        setAcceptorPriorityDelta(-2);
    }

    @Override
    protected void doStart() throws Exception
    {
        addBean(_acceptChannel);
        for (EventListener l : getBeans(EventListener.class))
            _manager.addEventListener(l);

        super.doStart();
        if (getAcceptors() == 0) {
            _acceptChannel.configureBlocking(false);
            _acceptor.set(_manager.acceptor(_acceptChannel));
        }
    }

    @Override
    protected void doStop() throws Exception
    {
        _acceptor.set(null);

        super.doStop();
        removeBean(_acceptChannel);
        _acceptChannel = null;
        for (EventListener l : getBeans(EventListener.class)) {
            _manager.removeEventListener(l);
        }
    }

/*
    @Override
    public boolean isOpen()
    {
        ServerSocketChannel channel = _acceptChannel;
        return channel != null && channel.isOpen();
    }
*/
    /**
     * Open the connector using the passed ServerSocketChannel.
     * This open method can be called before starting the connector to pass it a ServerSocketChannel
     * that will be used instead of one returned from {@link #openAcceptChannel()}
     *
     * @param acceptChannel the channel to use
     * @throws IOException if the server channel is not bound
     */
    public void open(ServerSocketChannel acceptChannel) throws IOException
    {
        if (isStarted())
            throw new IllegalStateException(getState());
        _acceptChannel = acceptChannel;
        _acceptChannel.configureBlocking(true);
        _localPort = _acceptChannel.socket().getLocalPort();
        if (_localPort <= 0)
            throw new IOException("Server channel not bound");
    }

    @Override
    public void open() throws IOException
    {
        if (_acceptChannel == null)
        {
            open(openAcceptChannel());
            super.open();
        }
    }

    /**
     * Called by {@link #open()} to obtain the accepting channel.
     *
     * @return ServerSocketChannel used to accept connections.
     * @throws IOException if unable to obtain or configure the server channel
     */
    protected ServerSocketChannel openAcceptChannel() throws IOException
    {
        ServerSocketChannel serverChannel = null;
        InetSocketAddress bindAddress = getHost() == null ? new InetSocketAddress(getPort()) : new InetSocketAddress(getHost(), getPort());
        serverChannel = ServerSocketChannel.open();
        try {
                serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, getReuseAddress());
                serverChannel.setOption(StandardSocketOptions.SO_REUSEPORT, isReusePort());
                serverChannel.bind(bindAddress, getAcceptQueueSize());
        }
        catch (Throwable e) {
                IO.close(serverChannel);
                throw new IOException("Failed to bind to " + bindAddress, e);
        }
        return serverChannel;
    }

    @Override
    public void close()
    {
        super.close();
        if (getAcceptors() > 0)
            IO.close(_acceptChannel);

        _localPort = -2;
    }

    @Override
    public void accept(int acceptorID) throws IOException
    {
        ServerSocketChannel serverChannel = _acceptChannel;
        if (serverChannel != null && serverChannel.isOpen()) {
            SocketChannel channel = serverChannel.accept();
            accepted(channel);
        }
    }

    private void accepted(SocketChannel channel) throws IOException
    {
        channel.configureBlocking(false);
        Socket socket = channel.socket();
        configure(socket);
        _manager.accept(channel);
    }

    protected void configure(Socket socket)
    {
        try
        {
            socket.setTcpNoDelay(_acceptedTcpNoDelay);
            if (_acceptedReceiveBufferSize > -1)
                socket.setReceiveBufferSize(_acceptedReceiveBufferSize);
            if (_acceptedSendBufferSize > -1)
                socket.setSendBufferSize(_acceptedSendBufferSize);
        }
        catch (SocketException e)
        {
            LOG.trace("IGNORED", e);
        }
    }

    @Override
    @ManagedAttribute("local port")
    public int getLocalPort()
    {
        return _localPort;
    }

    protected SocketChannelEndPoint newEndPoint(SocketChannel channel, ManagedSelector selectSet, SelectionKey key) throws IOException
    {
        SocketChannelEndPoint endpoint = new SocketChannelEndPoint(channel, selectSet, key, getScheduler());
        endpoint.setIdleTimeout(getIdleTimeout());
        return endpoint;
    }

    /**
     * @return the accept queue size
     */
    @ManagedAttribute("Accept Queue size")
    public int getAcceptQueueSize()
    {
        return _acceptQueueSize;
    }

    /**
     * Set the accept queue size (also known as accept backlog).
     * @param acceptQueueSize the accept queue size (also known as accept backlog)
     */
    public void setAcceptQueueSize(int acceptQueueSize)
    {
        _acceptQueueSize = acceptQueueSize;
    }

    /**
     * @return whether rebinding the server socket is allowed with sockets in tear-down states
     * @see ServerSocket#getReuseAddress()
     */
    @ManagedAttribute("Server Socket SO_REUSEADDR")
    public boolean getReuseAddress()
    {
        return _reuseAddress;
    }

    /**
     * @param reuseAddress whether rebinding the server socket is allowed with sockets in tear-down states
     * @see ServerSocket#setReuseAddress(boolean)
     */
    public void setReuseAddress(boolean reuseAddress)
    {
        _reuseAddress = reuseAddress;
    }

    /**
     * @return whether it is allowed to bind multiple server sockets to the same host and port
     */
    @ManagedAttribute("Server Socket SO_REUSEPORT")
    public boolean isReusePort()
    {
        return _reusePort;
    }

    /**
     * Set whether it is allowed to bind multiple server sockets to the same host and port.
     * @param reusePort whether it is allowed to bind multiple server sockets to the same host and port
     */
    public void setReusePort(boolean reusePort)
    {
        _reusePort = reusePort;
    }

    /**
     * @return whether the accepted socket gets {@link java.net.SocketOptions#TCP_NODELAY TCP_NODELAY} enabled.
     * @see Socket#getTcpNoDelay()
     */
    @ManagedAttribute("Accepted Socket TCP_NODELAY")
    public boolean getAcceptedTcpNoDelay()
    {
        return _acceptedTcpNoDelay;
    }

    /**
     * @param tcpNoDelay whether {@link java.net.SocketOptions#TCP_NODELAY TCP_NODELAY} gets enabled on the the accepted socket.
     * @see Socket#setTcpNoDelay(boolean)
     */
    public void setAcceptedTcpNoDelay(boolean tcpNoDelay)
    {
        this._acceptedTcpNoDelay = tcpNoDelay;
    }

    /**
     * @return the {@link java.net.SocketOptions#SO_RCVBUF SO_RCVBUF} size to set onto the accepted socket.
     * A value of -1 indicates that it is left to its default value.
     * @see Socket#getReceiveBufferSize()
     */
    @ManagedAttribute("Accepted Socket SO_RCVBUF")
    public int getAcceptedReceiveBufferSize()
    {
        return _acceptedReceiveBufferSize;
    }

    /**
     * @param receiveBufferSize the {@link java.net.SocketOptions#SO_RCVBUF SO_RCVBUF} size to set onto the accepted socket.
     * A value of -1 indicates that it is left to its default value.
     * @see Socket#setReceiveBufferSize(int)
     */
    public void setAcceptedReceiveBufferSize(int receiveBufferSize)
    {
        this._acceptedReceiveBufferSize = receiveBufferSize;
    }

    /**
     * @return the {@link java.net.SocketOptions#SO_SNDBUF SO_SNDBUF} size to set onto the accepted socket.
     * A value of -1 indicates that it is left to its default value.
     * @see Socket#getSendBufferSize()
     */
    @ManagedAttribute("Accepted Socket SO_SNDBUF")
    public int getAcceptedSendBufferSize()
    {
        return _acceptedSendBufferSize;
    }

    /**
     * @param sendBufferSize the {@link java.net.SocketOptions#SO_SNDBUF SO_SNDBUF} size to set onto the accepted socket.
     * A value of -1 indicates that it is left to its default value.
     * @see Socket#setSendBufferSize(int)
     */
    public void setAcceptedSendBufferSize(int sendBufferSize)
    {
        this._acceptedSendBufferSize = sendBufferSize;
    }

    @Override
    public void setAccepting(boolean accepting)
    {
        super.setAccepting(accepting);
        if (getAcceptors() > 0)
            return;

        try
        {
            if (accepting)
            {
                if (_acceptor.get() == null)
                {
                    Closeable acceptor = _manager.acceptor(_acceptChannel);
                    if (!_acceptor.compareAndSet(null, acceptor))
                        acceptor.close();
                }
            }
            else
            {
                Closeable acceptor = _acceptor.get();
                if (acceptor != null && _acceptor.compareAndSet(acceptor, null))
                    acceptor.close();
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected class ServerConnectorManager extends SelectorManager
    {
        public ServerConnectorManager(Executor executor, Scheduler scheduler)
        {
            super(executor, scheduler);
        }

        @Override
        protected void accepted(SelectableChannel channel) throws IOException
        {
            ServerConnector.this.accepted((SocketChannel)channel);
        }

        @Override
        protected SocketChannelEndPoint newEndPoint(SelectableChannel channel, ManagedSelector selector, SelectionKey selectionKey) throws IOException
        {
            return ServerConnector.this.newEndPoint((SocketChannel)channel, selector, selectionKey);
        }

        @Override
        public Connection newConnection(SelectableChannel channel, EndPoint endpoint, Object attachment) throws IOException
        {
            HttpConnectionFactory factory = new HttpConnectionFactory();
            return factory.newConnection(ServerConnector.this, endpoint);
        }

        @Override
        protected void endPointOpened(EndPoint endpoint)
        {
            super.endPointOpened(endpoint);
            onEndPointOpened(endpoint);
        }

        @Override
        protected void endPointClosed(EndPoint endpoint)
        {
            onEndPointClosed(endpoint);
            super.endPointClosed(endpoint);
        }
    }
}
