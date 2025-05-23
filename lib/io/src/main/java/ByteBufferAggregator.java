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

import java.nio.ByteBuffer;

import ab.squirrel.util.BufferUtil;
import ab.squirrel.util.TypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Aggregates data into a single ByteBuffer of a specified maximum size.</p>
 * <p>The buffer automatically grows as data is written to it, up until it reaches the specified maximum size.
 * Once the buffer is full, the aggregator will not aggregate any more bytes until its buffer is taken out,
 * after which a new aggregate/take buffer cycle can start.</p>
 * <p>The buffers are taken from the supplied {@link ByteBufferPool} or freshly allocated if one is not supplied.</p>
 */
public class ByteBufferAggregator
{
    private static final Logger LOG = LoggerFactory.getLogger(ByteBufferAggregator.class);

    private final ByteBufferPool _bufferPool;
    private final boolean _direct;
    private final int _maxSize;
    private RetainableByteBuffer _retainableByteBuffer;
    private int _aggregatedSize;
    private int _currentSize;

    /**
     * Creates a ByteBuffer aggregator.
     * @param bufferPool The {@link ByteBufferPool} from which to acquire the buffers
     * @param direct whether to get direct buffers
     * @param startSize the starting size of the buffer
     * @param maxSize the maximum size of the buffer which must be greater than {@code startSize}
     */
    public ByteBufferAggregator(ByteBufferPool bufferPool, boolean direct, int startSize, int maxSize)
    {
        if (maxSize <= 0)
            throw new IllegalArgumentException("maxSize must be > 0, was: " + maxSize);
        if (startSize <= 0)
            throw new IllegalArgumentException("startSize must be > 0, was: " + startSize);
        if (startSize > maxSize)
            throw new IllegalArgumentException("maxSize (" + maxSize + ") must be >= startSize (" + startSize + ")");
        _bufferPool = (bufferPool == null) ? ByteBufferPool.NON_POOLING : bufferPool;
        _direct = direct;
        _maxSize = maxSize;
        _currentSize = startSize;
    }

    /**
     * Get the currently aggregated length.
     * @return The current total aggregated bytes.
     */
    public int length()
    {
        return _aggregatedSize;
    }

    /**
     * Aggregates the given ByteBuffer. This copies bytes up to the specified maximum size, at which
     * time this method returns {@code true} and {@link #takeRetainableByteBuffer()} must be called
     * for this method to accept aggregating again.
     * @param buffer the buffer to copy into this aggregator; its position is updated according to
     * the number of aggregated bytes
     * @return true if the aggregator's buffer is full and should be taken, false otherwise
     */
    public boolean aggregate(ByteBuffer buffer)
    {
        tryExpandBufferCapacity(buffer.remaining());
        if (_retainableByteBuffer == null)
        {
            _retainableByteBuffer = _bufferPool.acquire(_currentSize, _direct);
            BufferUtil.flipToFill(_retainableByteBuffer.getByteBuffer());
        }
        int copySize = Math.min(_currentSize - _aggregatedSize, buffer.remaining());

        ByteBuffer byteBuffer = _retainableByteBuffer.getByteBuffer();
        byteBuffer.put(byteBuffer.position(), buffer, buffer.position(), copySize);
        byteBuffer.position(byteBuffer.position() + copySize);
        buffer.position(buffer.position() + copySize);
        _aggregatedSize += copySize;
        return _aggregatedSize == _maxSize;
    }

    private void tryExpandBufferCapacity(int remaining)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("tryExpandBufferCapacity remaining: {} _currentSize: {} _accumulatedSize={}", remaining, _currentSize, _aggregatedSize);
        if (_currentSize == _maxSize)
            return;
        int capacityLeft = _currentSize - _aggregatedSize;
        if (remaining <= capacityLeft)
            return;
        int need = remaining - capacityLeft;
        _currentSize = Math.min(_maxSize, TypeUtil.ceilToNextPowerOfTwo(_currentSize + need));

        if (_retainableByteBuffer != null)
        {
            BufferUtil.flipToFlush(_retainableByteBuffer.getByteBuffer(), 0);
            RetainableByteBuffer newBuffer = _bufferPool.acquire(_currentSize, _direct);
            BufferUtil.flipToFill(newBuffer.getByteBuffer());
            newBuffer.getByteBuffer().put(_retainableByteBuffer.getByteBuffer());
            _retainableByteBuffer.release();
            _retainableByteBuffer = newBuffer;
        }
    }

    /**
     * Takes the buffer out of the aggregator. Once the buffer has been taken out,
     * the aggregator resets itself and a new buffer will be acquired from the pool
     * during the next {@link #aggregate(ByteBuffer)} call.
     * @return the aggregated buffer, or null if nothing has been buffered yet
     */
    public RetainableByteBuffer takeRetainableByteBuffer()
    {
        if (_retainableByteBuffer == null)
            return null;
        BufferUtil.flipToFlush(_retainableByteBuffer.getByteBuffer(), 0);
        RetainableByteBuffer result = _retainableByteBuffer;
        _retainableByteBuffer = null;
        _aggregatedSize = 0;
        return result;
    }

    @Override
    public String toString()
    {
        return "%s@%x{a=%d c=%d m=%d b=%s}".formatted(getClass().getSimpleName(), hashCode(), _aggregatedSize, _currentSize, _maxSize, _retainableByteBuffer);
    }
}
