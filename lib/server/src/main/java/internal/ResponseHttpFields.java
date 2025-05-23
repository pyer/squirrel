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

package ab.squirrel.server.internal;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import ab.squirrel.http.HttpField;
import ab.squirrel.http.HttpFields;
import ab.squirrel.http.HttpHeader;
import ab.squirrel.http.PreEncodedHttpField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static ab.squirrel.server.internal.ResponseHttpFields.Persistent.isPersistent;

public class ResponseHttpFields implements HttpFields.Mutable
{
    private static final Logger LOG = LoggerFactory.getLogger(ResponseHttpFields.class);
    private final Mutable _fields = HttpFields.build();
    private final AtomicBoolean _committed = new AtomicBoolean();

    public HttpFields.Mutable getMutableHttpFields()
    {
        return _fields;
    }

    public boolean commit()
    {
        boolean committed = _committed.compareAndSet(false, true);
        if (committed && LOG.isDebugEnabled())
            LOG.debug("{} committed", this);
        return committed;
    }

    public boolean isCommitted()
    {
        return _committed.get();
    }

    public void recycle()
    {
        _committed.set(false);
        _fields.clear();
    }

    @Override
    public HttpField getField(String name)
    {
        return _fields.getField(name);
    }

    @Override
    public HttpField getField(HttpHeader header)
    {
        return _fields.getField(header);
    }

    @Override
    public HttpField getField(int index)
    {
        return _fields.getField(index);
    }

    @Override
    public int size()
    {
        return _fields.size();
    }

    @Override
    public Stream<HttpField> stream()
    {
        return _fields.stream();
    }

    @Override
    public Mutable add(HttpField field)
    {
        if (field != null && !_committed.get())
            _fields.add(field);
        return this;
    }

    @Override
    public HttpFields asImmutable()
    {
        return _committed.get() ? this : _fields.asImmutable();
    }

    @Override
    public Mutable clear()
    {
        if (!_committed.get())
        {
            for (ListIterator<HttpField> iterator = _fields.listIterator(_fields.size()); iterator.hasPrevious();)
            {
                HttpField field = iterator.previous();
                if (field instanceof Persistent persistent)
                    iterator.set(persistent.getOriginal());
                else
                    iterator.remove();
            }
        }
        return this;
    }

    @Override
    public void ensureField(HttpField field)
    {
        if (!_committed.get())
            _fields.ensureField(field);
    }

    @Override
    public Iterator<HttpField> iterator()
    {
        return new Iterator<>()
        {
            private final Iterator<HttpField> i = _fields.iterator();
            private HttpField _current;

            @Override
            public boolean hasNext()
            {
                return i.hasNext();
            }

            @Override
            public HttpField next()
            {
                _current = i.next();
                return _current;
            }

            @Override
            public void remove()
            {
                if (_committed.get())
                    throw new UnsupportedOperationException("Read Only");
                if (isPersistent(_current))
                    throw new UnsupportedOperationException("Persistent field");
                if (_current == null)
                    throw new IllegalStateException("No current field");
                i.remove();
                _current = null;
            }
        };
    }

    @Override
    public ListIterator<HttpField> listIterator(int index)
    {
        ListIterator<HttpField> i = _fields.listIterator(index);
        return new ListIterator<>()
        {
            private HttpField _current;

            @Override
            public boolean hasNext()
            {
                return i.hasNext();
            }

            @Override
            public HttpField next()
            {
                _current = i.next();
                return _current;
            }

            @Override
            public boolean hasPrevious()
            {
                return i.hasPrevious();
            }

            @Override
            public HttpField previous()
            {
                _current = i.previous();
                return _current;
            }

            @Override
            public int nextIndex()
            {
                return i.nextIndex();
            }

            @Override
            public int previousIndex()
            {
                return i.previousIndex();
            }

            @Override
            public void remove()
            {
                if (_committed.get())
                    throw new UnsupportedOperationException("Read Only");
                if (isPersistent(_current))
                    throw new UnsupportedOperationException("Persistent field");
                if (_current == null)
                    throw new IllegalStateException("No current field");
                i.remove();
                _current = null;
            }

            @Override
            public void set(HttpField field)
            {
                if (_committed.get())
                    throw new UnsupportedOperationException("Read Only");
                if (_current instanceof Persistent persistent)
                {
                    // cannot change the field name
                    if (field == null || !field.isSameName(_current))
                        throw new UnsupportedOperationException("Persistent field");

                    // new field must also be persistent and clear back to the previous value
                    field = (field instanceof PreEncodedHttpField)
                        ? new PersistentPreEncodedHttpField(_current.getHeader(), field.getValue(), persistent.getOriginal())
                        : new PersistentHttpField(field, persistent.getOriginal());
                }
                if (_current == null)
                    throw new IllegalStateException("No current field");
                if (field == null)
                    i.remove();
                else
                    i.set(field);
                _current = field;
            }

            @Override
            public void add(HttpField field)
            {
                if (_committed.get())
                    throw new UnsupportedOperationException("Read Only");
                if (field != null)
                    i.add(field);
            }
        };
    }

    @Override
    public String toString()
    {
        return _fields.toString();
    }

    /**
     * A marker interface for {@link HttpField}s that cannot be {@link #remove(HttpHeader) removed} or {@link #clear() cleared}
     * from a {@link ResponseHttpFields} instance. Persistent fields are not immutable in the {@link ResponseHttpFields}
     * and may be replaced with a different value. i.e. A Persistent field cannot be removed but can be overwritten.
     */
    public interface Persistent
    {
        static boolean isPersistent(HttpField field)
        {
            return field instanceof Persistent;
        }

        /**
         * @return the original persistent field set before any mutations
         */
        HttpField getOriginal();
    }

    /**
     * A {@link HttpField} that is a {@link Persistent}.
     */
    public static class PersistentHttpField extends HttpField implements Persistent
    {
        private final HttpField _field;
        private final HttpField _original;

        public PersistentHttpField(HttpField field)
        {
            this(field, null);
        }

        PersistentHttpField(HttpField field, HttpField original)
        {
            super(field.getHeader(), field.getName(), field.getValue());
            _field = field;
            _original = original == null ? this : original;
        }

        @Override
        public int getIntValue()
        {
            return _field.getIntValue();
        }

        @Override
        public long getLongValue()
        {
            return _field.getIntValue();
        }

        @Override
        public HttpField getOriginal()
        {
            return _original;
        }
    }

    /**
     * A {@link PreEncodedHttpField} that is a {@link Persistent}.
     */
    public static class PersistentPreEncodedHttpField extends PreEncodedHttpField implements Persistent
    {
        private final HttpField _original;

        public PersistentPreEncodedHttpField(HttpHeader header, String value)
        {
            this(header, value, null);
        }

        PersistentPreEncodedHttpField(HttpHeader header, String value, HttpField original)
        {
            super(header, value);
            _original = original == null ? this : original;
        }

        @Override
        public HttpField getOriginal()
        {
            return _original;
        }
    }
}
