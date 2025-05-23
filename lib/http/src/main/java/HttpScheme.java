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

package ab.squirrel.http;

import java.nio.ByteBuffer;

import ab.squirrel.util.BufferUtil;
import ab.squirrel.util.Index;
import ab.squirrel.util.URIUtil;

/**
 * HTTP and WebSocket Schemes
 */
public enum HttpScheme
{
    HTTP("http"),
    HTTPS("https"),
    WS("ws"),
    WSS("wss");

    public static final Index<HttpScheme> CACHE = new Index.Builder<HttpScheme>()
        .caseSensitive(false)
        .withAll(HttpScheme.values(), HttpScheme::asString)
        .build();

    private final String _string;
    private final ByteBuffer _buffer;
    private final int _defaultPort;

    HttpScheme(String s)
    {
        _string = s;
        _buffer = BufferUtil.toBuffer(s);
        _defaultPort = URIUtil.getDefaultPortForScheme(s);
    }

    public ByteBuffer asByteBuffer()
    {
        return _buffer.asReadOnlyBuffer();
    }

    public boolean is(String s)
    {
        return _string.equalsIgnoreCase(s);
    }

    public String asString()
    {
        return _string;
    }

    public int getDefaultPort()
    {
        return _defaultPort;
    }

    public int normalizePort(int port)
    {
        return port == _defaultPort ? 0 : port;
    }

    @Override
    public String toString()
    {
        return _string;
    }

    /**
     * Get the default port for a URI scheme
     * @param scheme The scheme
     * @return Default port for URI scheme
     * @deprecated Use {@link URIUtil#getDefaultPortForScheme(String)}
     */
    @Deprecated
    public static int getDefaultPort(String scheme)
    {
        return URIUtil.getDefaultPortForScheme(scheme);
    }

    /**
     * Normalize a port for a URI scheme
     * @param scheme the scheme
     * @param port the port to normalize
     * @return The normalized port
     * @deprecated Use {@link URIUtil#normalizePortForScheme(String, int)}
     */
    @Deprecated
    public static int normalizePort(String scheme, int port)
    {
        return URIUtil.normalizePortForScheme(scheme, port);
    }

    public static boolean isSecure(String scheme)
    {
        return HttpScheme.HTTPS.is(scheme) || HttpScheme.WSS.is(scheme);
    }
}
