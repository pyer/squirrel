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

import java.util.Collection;
import java.util.EventListener;
import java.util.List;
import java.util.concurrent.Executor;

import ab.squirrel.io.ByteBufferPool;
import ab.squirrel.io.EndPoint;
import ab.squirrel.util.annotation.ManagedAttribute;
import ab.squirrel.util.annotation.ManagedObject;
import ab.squirrel.util.component.Container;
import ab.squirrel.util.component.Graceful;
import ab.squirrel.util.component.LifeCycle;
import ab.squirrel.util.thread.Scheduler;

/**
 * <p>A {@link Connector} accept connections and data from remote peers,
 * and allows applications to send data to remote peers, by setting up
 * the machinery needed to handle such tasks.</p>
 */
@ManagedObject("Connector Interface")
public interface Connector extends LifeCycle, Container, Closeable, Graceful
{
    /**
     * Get the {@link Server} instance associated with this {@link Connector}.
     * @return the {@link Server} instance associated with this {@link Connector}
     */
    public Server getServer();

    /**
     * Get the {@link Executor} used to submit tasks.
     * @return the {@link Executor} used to submit tasks
     */
    public Executor getExecutor();

    /**
     * Get the {@link Scheduler} used to schedule tasks.
     * @return the {@link Scheduler} used to schedule tasks
     */
    public Scheduler getScheduler();

    /**
     * Get the {@link ByteBufferPool} to acquire buffers from and release buffers to.
     * @return the {@link ByteBufferPool} to acquire buffers from and release buffers to
     */
    public ByteBufferPool getByteBufferPool();

    /**
     * @return The hostname representing the interface to which
     * this connector will bind, or null for all interfaces.
     */
    String getHost();

    /**
     * @return The configured port for the connector or 0 if any available
     * port may be used.
     */
    int getPort();

    /**
     * @return The actual port the connector is listening on, or
     * -1 if it has not been opened, or -2 if it has been closed.
     */
    int getLocalPort();

    /**
     * <p>Performs the activities needed to open the network communication
     * (for example, to start accepting incoming network connections).</p>
     * <p>Implementation must be idempotent.</p>
     *
     * @throws IOException if this connector cannot be opened
     * @see #close()
     */
    void open() throws IOException;

    /**
     * <p>Performs the activities needed to close the network communication
     * (for example, to stop accepting network connections).</p>
     * <p>Once a connector has been closed, it cannot be opened again without first
     * calling {@link #stop()} and it will not be active again until a subsequent call to {@link #start()}.</p>
     * <p>Implementation must be idempotent.</p>
     */
    void close();

    /**
     * @param nextProtocol the next protocol
     * @return the {@link ConnectionFactory} associated with the protocol name
     */
    public ConnectionFactory getConnectionFactory(String nextProtocol);

    public <T> T getConnectionFactory(Class<T> factoryType);

    /**
     * Get the default {@link ConnectionFactory} associated with the default protocol name.
     * @return the default {@link ConnectionFactory} associated with the default protocol name
     */
    public ConnectionFactory getDefaultConnectionFactory();

    public Collection<ConnectionFactory> getConnectionFactories();

    public List<String> getProtocols();

    /**
     * @return the max idle timeout for connections in milliseconds
     */
    @ManagedAttribute("maximum time a connection can be idle before being closed (in ms)")
    public long getIdleTimeout();

    /**
     * @return immutable collection of connected endpoints
     */
    public Collection<EndPoint> getConnectedEndPoints();

    /**
     * Get the connector name if set.
     * <p>A {@link ContextHandler} may be configured with
     * virtual hosts in the form "@connectorName" and will only serve
     * requests from the named connector.
     *
     * @return The connector name or null.
     */
    public String getName();

    /**
     * <p>Receives notifications of the {@link Connector#open()}
     * and {@link Connector#close()} events.</p>
     */
    interface Listener extends EventListener
    {
        /**
         * <p>Invoked when the given {@link Connector} has been opened.</p>
         *
         * @param connector the {@link Connector} that has been opened
         */
        default void onOpen(Connector connector)
        {
        }

        /**
         * <p>Invoked when the given {@link Connector} has been closed.</p>
         *
         * @param connector the {@link Connector} that has been closed
         */
        default void onClose(Connector connector)
        {
        }
    }
}
