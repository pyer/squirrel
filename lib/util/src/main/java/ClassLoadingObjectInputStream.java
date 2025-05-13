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

package ab.squirrel.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * ClassLoadingObjectInputStream
 *
 * For re-inflating serialized objects, this class uses the thread context classloader
 * rather than the jvm's default classloader selection.
 */
public class ClassLoadingObjectInputStream extends ObjectInputStream
{

    protected static class ClassLoaderThreadLocal extends ThreadLocal<ClassLoader>
    {
        protected static final ClassLoader UNSET = new ClassLoader() {};

        @Override
        protected ClassLoader initialValue()
        {
            return UNSET;
        }
    }

    private ThreadLocal<ClassLoader> _classloader = new ClassLoaderThreadLocal();

    public ClassLoadingObjectInputStream(java.io.InputStream in) throws IOException
    {
        super(in);
    }

    public ClassLoadingObjectInputStream() throws IOException
    {
        super();
    }

    public Object readObject(ClassLoader loader)
        throws IOException, ClassNotFoundException
    {
        try
        {
            _classloader.set(loader);
            return readObject();
        }
        finally
        {
            _classloader.set(ClassLoaderThreadLocal.UNSET);
        }
    }

    @Override
    public Class<?> resolveClass(java.io.ObjectStreamClass cl) throws IOException, ClassNotFoundException
    {
        try
        {
            ClassLoader loader = _classloader.get();
            if (ClassLoaderThreadLocal.UNSET == loader)
                loader = Thread.currentThread().getContextClassLoader();

            return Class.forName(cl.getName(), false, loader);
        }
        catch (ClassNotFoundException e)
        {
            return super.resolveClass(cl);
        }
    }

}

