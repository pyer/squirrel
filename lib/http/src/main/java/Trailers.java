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

import ab.squirrel.io.Content;
import ab.squirrel.util.BufferUtil;

public class Trailers implements Content.Chunk
{
    private final HttpFields trailers;

    public Trailers(HttpFields trailers)
    {
        this.trailers = trailers;
    }

    @Override
    public ByteBuffer getByteBuffer()
    {
        return BufferUtil.EMPTY_BUFFER;
    }

    @Override
    public boolean isLast()
    {
        return true;
    }

    public HttpFields getTrailers()
    {
        return trailers;
    }
}
