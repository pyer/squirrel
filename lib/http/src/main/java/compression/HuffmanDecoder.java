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

package ab.squirrel.http.compression;

import java.nio.ByteBuffer;

import ab.squirrel.http.HttpTokens;
import ab.squirrel.util.CharsetStringBuilder;

import static ab.squirrel.http.compression.Huffman.rowbits;
import static ab.squirrel.http.compression.Huffman.rowsym;

/**
 * <p>Used to decoded Huffman encoded strings.</p>
 *
 * <p>Characters which are illegal field-vchar values are replaced with
 * either ' ' or '?' as described in RFC9110</p>
 */
public class HuffmanDecoder
{
    private final CharsetStringBuilder.Iso88591StringBuilder _builder = new CharsetStringBuilder.Iso88591StringBuilder();
    private int _length = 0;
    private int _count = 0;
    private int _node = 0;
    private int _current = 0;
    private int _bits = 0;

    /**
     * Set in bytes of the huffman data..
     * @param length in bytes of the huffman data.
     */
    public void setLength(int length)
    {
        if (_count != 0)
            throw new IllegalStateException();
        _length = length;
    }

    /**
     * @param buffer the buffer containing the Huffman encoded bytes.
     * @return the decoded String.
     * @throws EncodingException if the huffman encoding is invalid.
     */
    public String decode(ByteBuffer buffer) throws EncodingException
    {
        for (; _count < _length; _count++)
        {
            if (!buffer.hasRemaining())
                return null;

            int b = buffer.get() & 0xFF;
            _current = (_current << 8) | b;
            _bits += 8;
            while (_bits >= 8)
            {
                int i = (_current >>> (_bits - 8)) & 0xFF;
                _node = Huffman.tree[_node * 256 + i];
                if (rowbits[_node] != 0)
                {
                    if (rowsym[_node] == Huffman.EOS)
                    {
                        reset();
                        throw new EncodingException("eos_in_content");
                    }

                    // terminal node
                    char c = rowsym[_node];
                    c = HttpTokens.sanitizeFieldVchar(c);
                    _builder.append((byte)c);
                    _bits -= rowbits[_node];
                    _node = 0;
                }
                else
                {
                    // non-terminal node
                    _bits -= 8;
                }
            }
        }

        while (_bits > 0)
        {
            int i = (_current << (8 - _bits)) & 0xFF;
            int lastNode = _node;
            _node = Huffman.tree[_node * 256 + i];

            if (rowbits[_node] == 0 || rowbits[_node] > _bits)
            {
                int requiredPadding = 0;
                for (int j = 0; j < _bits; j++)
                {
                    requiredPadding = (requiredPadding << 1) | 1;
                }

                if ((i >> (8 - _bits)) != requiredPadding)
                    throw new EncodingException("incorrect_padding");

                _node = lastNode;
                break;
            }

            char c = rowsym[_node];
            c = HttpTokens.sanitizeFieldVchar(c);
            _builder.append((byte)c);
            _bits -= rowbits[_node];
            _node = 0;
        }

        if (_node != 0)
        {
            reset();
            throw new EncodingException("bad_termination");
        }

        String value = _builder.build();
        reset();
        return value;
    }

    public void reset()
    {
        _builder.reset();
        _count = 0;
        _current = 0;
        _node = 0;
        _bits = 0;
    }
}
