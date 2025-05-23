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
import java.util.EventListener;
import java.util.List;
import java.util.Map;

import ab.squirrel.util.component.ContainerLifeCycle;

/**
 * Factory for client-side {@link Connection} instances.
 */
public interface ClientConnectionFactory
{
    public static final String CLIENT_CONTEXT_KEY = "ab.squirrel.client";

    /**
     * @param endPoint the {@link ab.squirrel.io.EndPoint} to link the newly created connection to
     * @param context the context data to create the connection
     * @return a new {@link Connection}
     * @throws IOException if the connection cannot be created
     */
    public Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException;

    public default Connection customize(Connection connection, Map<String, Object> context)
    {
        ContainerLifeCycle client = (ContainerLifeCycle)context.get(CLIENT_CONTEXT_KEY);
        if (client != null)
            client.getBeans(EventListener.class).forEach(connection::addEventListener);
        return connection;
    }

    /**
     * <p>Wraps another ClientConnectionFactory.</p>
     * <p>This is typically done by protocols that send "preface" bytes with some metadata
     * before other protocols. The metadata could be, for example, proxying information
     * or authentication information.</p>
     */
    interface Decorator
    {
        /**
         * <p>Wraps the given {@code factory}.</p>
         *
         * @param factory the ClientConnectionFactory to wrap
         * @return the wrapping ClientConnectionFactory
         */
        ClientConnectionFactory apply(ClientConnectionFactory factory);
    }

    /**
     * <p>A holder for a list of protocol strings identifiers
     * (for example {@code ["h2", "h2-17", "h2-16"]}) and a
     * {@link ClientConnectionFactory} that creates connections
     * that speak an application protocol such as HTTP.</p>
     */
    public abstract static class Info extends ContainerLifeCycle
    {
        private final ClientConnectionFactory factory;

        public Info(ClientConnectionFactory factory)
        {
            this.factory = factory;
            addBean(factory);
        }

        /**
         * @param secure {@code true} for the secure protocol identifiers,
         * {@code false} for the clear-text protocol identifiers
         * @return a list of protocol string identifiers
         */
        public abstract List<String> getProtocols(boolean secure);

        /**
         * @return the {@link ClientConnectionFactory} that speaks the protocol
         */
        public ClientConnectionFactory getClientConnectionFactory()
        {
            return factory;
        }

        /**
         * @return the default {@link Transport} used by the protocol
         */
        public abstract Transport newTransport();

        /**
         * <p>Tests whether one of the protocol identifiers of this
         * class is also present in the given candidates list.</p>
         *
         * @param candidates the candidates to match against
         * @param secure whether the protocol should be a secure one
         * @return whether one of the protocols of this class is present in the candidates
         */
        public boolean matches(List<String> candidates, boolean secure)
        {
            return getProtocols(secure).stream().anyMatch(p -> candidates.stream().anyMatch(c -> c.equalsIgnoreCase(p)));
        }

        /**
         * <p>Upgrades the given {@link EndPoint} to the protocol represented by this class.</p>
         *
         * @param endPoint the {@link EndPoint} to upgrade
         * @param context the context information to perform the upgrade
         */
        public void upgrade(EndPoint endPoint, Map<String, Object> context)
        {
            throw new UnsupportedOperationException(this + " does not support upgrade to another protocol");
        }
    }
}
