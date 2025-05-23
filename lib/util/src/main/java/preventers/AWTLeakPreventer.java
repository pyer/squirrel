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

package ab.squirrel.util.preventers;

import java.awt.Toolkit;

/**
 * AWTLeakPreventer
 *
 * See https://issues.jboss.org/browse/AS7-3733
 *
 * The java.awt.Toolkit class has a static field that is the default toolkit.
 * Creating the default toolkit causes the creation of an EventQueue, which has a
 * classloader field initialized by the thread context class loader.
 */
public class AWTLeakPreventer extends AbstractLeakPreventer
{
    @Override
    public void prevent(ClassLoader loader)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Pinning classloader for java.awt.EventQueue using {}", loader);
        Toolkit.getDefaultToolkit();
    }
}
