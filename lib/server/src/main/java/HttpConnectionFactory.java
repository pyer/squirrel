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

import java.util.Objects;

import ab.squirrel.http.ComplianceViolation;
import ab.squirrel.http.HttpVersion;
import ab.squirrel.io.Connection;
import ab.squirrel.io.EndPoint;
import ab.squirrel.server.internal.HttpConnection;
import ab.squirrel.util.annotation.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Connection Factory for HTTP Connections.
 * <p>Accepts connections either directly or via SSL and/or ALPN chained connection factories.  The accepted
 * {@link HttpConnection}s are configured by a {@link HttpConfiguration} instance that is either created by
 * default or passed in to the constructor.
 */
public class HttpConnectionFactory extends AbstractConnectionFactory implements HttpConfiguration.ConnectionFactory
{
    private static final Logger LOG = LoggerFactory.getLogger(HttpConnectionFactory.class);
    private final HttpConfiguration _config;
    private boolean _useInputDirectByteBuffers;
    private boolean _useOutputDirectByteBuffers;

    public HttpConnectionFactory()
    {
        this(new HttpConfiguration());
    }

    public HttpConnectionFactory(@Name("config") HttpConfiguration config)
    {
        super(HttpVersion.HTTP_1_1.asString());
        _config = Objects.requireNonNull(config);
        installBean(_config);
        setUseInputDirectByteBuffers(_config.isUseInputDirectByteBuffers());
        setUseOutputDirectByteBuffers(_config.isUseOutputDirectByteBuffers());
    }

    @Override
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

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint)
    {
        HttpConnection connection = new HttpConnection(_config, connector, endPoint);
        connection.setUseInputDirectByteBuffers(isUseInputDirectByteBuffers());
        connection.setUseOutputDirectByteBuffers(isUseOutputDirectByteBuffers());
        return configure(connection, connector, endPoint);
    }
}
