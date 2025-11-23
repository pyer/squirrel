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

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import ab.squirrel.http.ComplianceViolation;
import ab.squirrel.http.HttpVersion;
import ab.squirrel.io.AbstractConnection;
import ab.squirrel.io.Connection;
import ab.squirrel.io.EndPoint;
import ab.squirrel.server.internal.HttpConnection;
import ab.squirrel.util.annotation.Name;
import ab.squirrel.util.annotation.ManagedAttribute;
import ab.squirrel.util.annotation.ManagedObject;
import ab.squirrel.util.component.ContainerLifeCycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Connection Factory for HTTP Connections.
 * <p>Accepts connections either directly or via SSL and/or ALPN chained connection factories.  The accepted
 * {@link HttpConnection}s are configured by a {@link HttpConfiguration} instance that is either created by
 * default or passed in to the constructor.
 */
public class HttpConnectionFactory extends ContainerLifeCycle
{
    private static final Logger LOG = LoggerFactory.getLogger(HttpConnectionFactory.class);
    private HttpConfiguration _config;
    private boolean _useInputDirectByteBuffers;
    private boolean _useOutputDirectByteBuffers;
    private int _inputBufferSize = 8192;

    public HttpConnectionFactory()
    {
        new HttpConfiguration();
    }

/*
    public HttpConnectionFactory(@Name("config") HttpConfiguration config)
    {
        super(HttpVersion.HTTP_1_1.asString());
        _config = Objects.requireNonNull(config);
        installBean(_config);
        setUseInputDirectByteBuffers(_config.isUseInputDirectByteBuffers());
        setUseOutputDirectByteBuffers(_config.isUseOutputDirectByteBuffers());
    }
*/
    public int getInputBufferSize()
    {
        return _inputBufferSize;
    }

    public void setInputBufferSize(int size)
    {
        _inputBufferSize = size;
    }

    public HttpConfiguration getHttpConfiguration()
    {
        return _config;
    }

    public boolean isUseInputDirectByteBuffers()
    {
        return _useInputDirectByteBuffers;
    }

    public void setUseInputDirectByteBuffers(boolean useInputDirectByteBuffers)
    {
        _useInputDirectByteBuffers = useInputDirectByteBuffers;
    }

    public boolean isUseOutputDirectByteBuffers()
    {
        return _useOutputDirectByteBuffers;
    }

    public void setUseOutputDirectByteBuffers(boolean useOutputDirectByteBuffers)
    {
        _useOutputDirectByteBuffers = useOutputDirectByteBuffers;
    }

    public Connection newConnection(Connector connector, EndPoint endPoint)
    {
        _config = new HttpConfiguration();
        HttpConnection connection = new HttpConnection(_config, connector, endPoint);
        connection.setUseInputDirectByteBuffers(isUseInputDirectByteBuffers());
        connection.setUseOutputDirectByteBuffers(isUseOutputDirectByteBuffers());
        return configure(connection, connector, endPoint);
    }

    protected <T extends AbstractConnection> T configure(T connection, Connector connector, EndPoint endPoint)
    {
        // Add Connection.Listeners from Connector.
        connector.getEventListeners().forEach(connection::addEventListener);

        // Add Connection.Listeners from this factory.
        getEventListeners().forEach(connection::addEventListener);

        connection.setInputBufferSize(getInputBufferSize());

        return connection;
    }

    public String toString()
    {
        return String.format("%s@%x", this.getClass().getSimpleName(), hashCode());
    }

}
