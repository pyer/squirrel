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

import java.util.Arrays;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * An immutable implementation of {@link HttpFields}.
 */
class ImmutableHttpFields implements HttpFields
{
    final HttpField[] _fields;
    final int _size;

    /**
     * Initialize HttpFields from copy.
     *
     * @param fields the fields to copy data from
     */
    protected ImmutableHttpFields(HttpField[] fields)
    {
        this(fields, fields.length);
    }

    protected ImmutableHttpFields(HttpField[] fields, int size)
    {
        _fields = fields;
        _size = size;
    }

    @Override
    public HttpFields asImmutable()
    {
        return this;
    }

    @Override
    public int hashCode()
    {
        int hash = 1993; // prime
        for (int i = _size; i-- > 0; )
        {
            hash ^= _fields[i].hashCode();
        }
        return hash;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o instanceof HttpFields httpFields)
            return isEqualTo(httpFields);
        return false;
    }

    @Override
    public String get(String name)
    {
        // default impl overridden for efficiency
        for (HttpField f : _fields)
        {
            if (f != null && f.is(name))
                return f.getValue();
        }
        return null;
    }

    @Override
    public String get(HttpHeader header)
    {
        // default impl overridden for efficiency
        for (HttpField f : _fields)
        {
            if (f != null && f.getHeader() == header)
                return f.getValue();
        }
        return null;
    }

    @Override
    public HttpField getField(HttpHeader header)
    {
        // default impl overridden for efficiency
        for (HttpField f : _fields)
        {
            if (f != null && f.getHeader() == header)
                return f;
        }
        return null;
    }

    @Override
    public HttpField getField(String name)
    {
        // default impl overridden for efficiency
        for (HttpField f : _fields)
        {
            if (f != null && f.is(name))
                return f;
        }
        return null;
    }

    @Override
    public HttpField getField(int index)
    {
        if (index >= _size)
            throw new NoSuchElementException();
        return _fields[index];
    }

    @Override
    public ListIterator<HttpField> listIterator(int index)
    {
        return new Listerator(index);
    }

    @Override
    public int size()
    {
        return _size;
    }

    @Override
    public Stream<HttpField> stream()
    {
        return Arrays.stream(_fields).filter(Objects::nonNull);
    }

    @Override
    public String toString()
    {
        return asString();
    }

    private class Listerator implements ListIterator<HttpField>
    {
        private int _index;
        private int _last = -1;

        Listerator(int index)
        {
            if (index < 0 || index > _size)
                throw new NoSuchElementException(Integer.toString(index));
            _index = index;
        }

        @Override
        public void add(HttpField field)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasNext()
        {
            return _index < _size;
        }

        @Override
        public boolean hasPrevious()
        {
            return _index > 0;
        }

        @Override
        public HttpField next()
        {
            if (_index >= _size)
                throw new NoSuchElementException(Integer.toString(_index));
            _last = _index++;
            return _fields[_last];
        }

        @Override
        public int nextIndex()
        {
            return _index + 1;
        }

        @Override
        public HttpField previous()
        {
            if (_index <= 0)
                throw new NoSuchElementException(Integer.toString(_index - 1));
            _last = --_index;
            return _fields[_last];
        }

        @Override
        public int previousIndex()
        {
            return _index - 1;
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(HttpField field)
        {
            throw new UnsupportedOperationException();
        }
    }
}
