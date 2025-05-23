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

/**
 * Used to encode integers as described in RFC7541.
 */
public class NBitIntegerEncoder
{
    private NBitIntegerEncoder()
    {
    }

    /**
     * @param prefix the prefix used to encode this long.
     * @param value the integer to encode.
     * @return the number of octets it would take to encode the long.
     */
    public static int octetsNeeded(int prefix, long value)
    {
        if (prefix <= 0 || prefix > 8)
            throw new IllegalArgumentException();

        int nbits = 0xFF >>> (8 - prefix);
        value = value - nbits;
        if (value < 0)
            return 1;
        if (value == 0)
            return 2;
        int lz = Long.numberOfLeadingZeros(value);
        int log = 64 - lz;

        // The return value is 1 for the prefix + the number of 7-bit groups necessary to encode the value.
        return 1 + (log + 6) / 7;
    }

    /**
     *
     * @param buffer the buffer to encode into.
     * @param prefix the prefix used to encode this long.
     * @param value the long to encode into the buffer.
     */
    public static void encode(ByteBuffer buffer, int prefix, long value)
    {
        if (prefix <= 0 || prefix > 8)
            throw new IllegalArgumentException();

        // If prefix is 8 we add an empty byte as we initially modify last byte from the buffer.
        if (prefix == 8)
            buffer.put((byte)0x00);

        int bits = 0xFF >>> (8 - prefix);
        int p = buffer.position() - 1;
        if (value < bits)
        {
            buffer.put(p, (byte)((buffer.get(p) & ~bits) | value));
        }
        else
        {
            buffer.put(p, (byte)(buffer.get(p) | bits));
            long length = value - bits;
            while (true)
            {
                // The value of ~0x7F is different to 0x80 because of all the 1s from the MSB.
                if ((length & ~0x7FL) == 0)
                {
                    buffer.put((byte)length);
                    return;
                }
                else
                {
                    buffer.put((byte)((length & 0x7F) | 0x80));
                    length >>>= 7;
                }
            }
        }
    }
}
