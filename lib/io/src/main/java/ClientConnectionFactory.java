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
import java.util.Map;

/**
 * Factory for client-side {@link Connection} instances.
 */
public interface ClientConnectionFactory
{
    /**
     * @param endPoint the {@link ab.squirrel.io.EndPoint} to link the newly created connection to
     * @param context the context data to create the connection
     * @return a new {@link Connection}
     * @throws IOException if the connection cannot be created
     */
    public Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException;

}
