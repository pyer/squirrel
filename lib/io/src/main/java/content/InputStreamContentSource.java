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

package ab.squirrel.io.content;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

import ab.squirrel.io.ByteBufferPool;
import ab.squirrel.io.Content;
import ab.squirrel.io.RetainableByteBuffer;
import ab.squirrel.util.ExceptionUtil;
import ab.squirrel.util.IO;
import ab.squirrel.util.thread.AutoLock;
import ab.squirrel.util.thread.SerializedInvoker;

/**
 * <p>
 * A {@link Content.Source} that is backed by an {@link InputStream}.
 * Data is read from the {@link InputStream} into a buffer that is optionally acquired
 * from a {@link ByteBufferPool}, and converted to a {@link Content.Chunk} that is
 * returned from {@link #read()}. If no {@link ByteBufferPool} is provided, then
 * a {@link ByteBufferPool.NonPooling} is used.
 * </p>
 */
public class InputStreamContentSource implements Content.Source
{
    private final AutoLock lock = new AutoLock();
    private final SerializedInvoker invoker = new SerializedInvoker();
    private final InputStream inputStream;
    private ByteBufferPool.Sized bufferPool;
    private Runnable demandCallback;
    private Content.Chunk errorChunk;
    private boolean closed;

    public InputStreamContentSource(InputStream inputStream)
    {
        this(inputStream, null);
    }

    public InputStreamContentSource(InputStream inputStream, ByteBufferPool bufferPool)
    {
        this(inputStream, bufferPool instanceof ByteBufferPool.Sized sized ? sized : new ByteBufferPool.Sized(bufferPool));
    }

    public InputStreamContentSource(InputStream inputStream, ByteBufferPool.Sized bufferPool)
    {
        this.inputStream = Objects.requireNonNull(inputStream);
        this.bufferPool = Objects.requireNonNullElse(bufferPool, ByteBufferPool.SIZED_NON_POOLING);
    }

    public int getBufferSize()
    {
        return bufferPool.getSize();
    }

    public boolean isUseDirectByteBuffers()
    {
        return bufferPool.isDirect();
    }

    @Override
    public Content.Chunk read()
    {
        try (AutoLock ignored = lock.lock())
        {
            if (errorChunk != null)
                return errorChunk;
            if (closed)
                return Content.Chunk.EOF;
        }

        RetainableByteBuffer streamBuffer = bufferPool.acquire();
        try
        {
            ByteBuffer buffer = streamBuffer.getByteBuffer();
            int read = fillBufferFromInputStream(inputStream, buffer.array());
            if (read < 0)
            {
                streamBuffer.release();
                close();
                return Content.Chunk.EOF;
            }
            else
            {
                buffer.limit(read);
                return Content.Chunk.asChunk(buffer, false, streamBuffer);
            }
        }
        catch (Throwable x)
        {
            streamBuffer.release();
            return failure(x);
        }
    }

    protected int fillBufferFromInputStream(InputStream inputStream, byte[] buffer) throws IOException
    {
        return inputStream.read(buffer, 0, buffer.length);
    }

    private void close()
    {
        try (AutoLock ignored = lock.lock())
        {
            closed = true;
        }
        IO.close(inputStream);
    }

    @Override
    public void demand(Runnable demandCallback)
    {
        try (AutoLock ignored = lock.lock())
        {
            if (this.demandCallback != null)
                throw new IllegalStateException("demand pending");
            this.demandCallback = demandCallback;
        }
        invoker.run(this::invokeDemandCallback);
    }

    private void invokeDemandCallback()
    {
        Runnable demandCallback;
        try (AutoLock ignored = lock.lock())
        {
            demandCallback = this.demandCallback;
            this.demandCallback = null;
        }
        if (demandCallback != null)
            ExceptionUtil.run(demandCallback, this::fail);
    }

    @Override
    public void fail(Throwable failure)
    {
        failure(failure);
    }

    private Content.Chunk failure(Throwable failure)
    {
        Content.Chunk error;
        try (AutoLock ignored = lock.lock())
        {
            error = errorChunk;
            if (error == null)
                error = errorChunk = Content.Chunk.from(failure);
        }
        IO.close(inputStream);
        return error;
    }
}
