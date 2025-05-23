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

import ab.squirrel.io.AbstractConnection;
import ab.squirrel.io.EndPoint;
import ab.squirrel.util.annotation.ManagedAttribute;
import ab.squirrel.util.annotation.ManagedObject;
import ab.squirrel.util.component.ContainerLifeCycle;

/**
 * <p>Provides the common handling for {@link ConnectionFactory} implementations.</p>
 */
@ManagedObject
public abstract class AbstractConnectionFactory extends ContainerLifeCycle implements ConnectionFactory
{
    private final String _protocol;
    private final List<String> _protocols;
    private int _inputBufferSize = 8192;

    protected AbstractConnectionFactory(String protocol)
    {
        _protocol = protocol;
        _protocols = List.of(protocol);
    }

    protected AbstractConnectionFactory(String... protocols)
    {
        _protocol = protocols[0];
        _protocols = List.of(protocols);
    }

    @Override
    @ManagedAttribute(value = "The protocol name", readonly = true)
    public String getProtocol()
    {
        return _protocol;
    }

    @Override
    public List<String> getProtocols()
    {
        return _protocols;
    }

    @ManagedAttribute("The buffer size used to read from the network")
    public int getInputBufferSize()
    {
        return _inputBufferSize;
    }

    public void setInputBufferSize(int size)
    {
        _inputBufferSize = size;
    }

    protected String findNextProtocol(Connector connector)
    {
        return findNextProtocol(connector, getProtocol());
    }

    protected static String findNextProtocol(Connector connector, String currentProtocol)
    {
        String nextProtocol = null;
        for (Iterator<String> it = connector.getProtocols().iterator(); it.hasNext(); )
        {
            String protocol = it.next();
            if (currentProtocol.equalsIgnoreCase(protocol))
            {
                nextProtocol = it.hasNext() ? it.next() : null;
                break;
            }
        }
        return nextProtocol;
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

    @Override
    public String toString()
    {
        return String.format("%s@%x%s", this.getClass().getSimpleName(), hashCode(), getProtocols());
    }

}
