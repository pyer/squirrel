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

package ab.squirrel.server;

//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.function.Supplier;

import ab.squirrel.util.Callback;
import ab.squirrel.util.annotation.ManagedAttribute;
import ab.squirrel.util.annotation.ManagedObject;
import ab.squirrel.util.component.ContainerLifeCycle;
import ab.squirrel.util.component.Destroyable;
import ab.squirrel.util.component.LifeCycle;
import ab.squirrel.util.thread.Invocable;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

/**
 * <p>A Jetty component that handles HTTP requests, of any version (HTTP/1.1, HTTP/2 or HTTP/3).
 * A {@code Handler} is a {@link Request.Handler} with the addition of {@link LifeCycle}
 * behaviours, plus variants that allow organizing {@code Handler}s as a tree structure.</p>
 * <p>{@code Handler}s may wrap the {@link Request}, {@link Response} and/or {@link Callback} and
 * then forward the wrapped instances to their children, so that they see a modified request;
 * and/or to intercept the read of the request content; and/or intercept the generation of the
 * response; and/or to intercept the completion of the callback.</p>
 * <p>A {@code Handler} is an {@link Invocable} and implementations must respect
 * the {@link InvocationType} they declare within calls to
 * {@link #handle(Request, Response, Callback)}.</p>
 * <p>A minimal tree structure could be:</p>
 * <pre>{@code
 * Server
 * `- YourCustomHandler
 * }</pre>
 * <p>A more sophisticated tree structure:</p>
 * <pre>{@code
 * Server
 *       |  `- YourUserHandler
 *       |  `- YourAdminHandler
 * }</pre>
 *
 * <p>A more sophisticated example of a {@code Handler} that decides whether to handle
 * requests based on their URI path:</p>
 * <pre>{@code
 * class YourHelloHandler extends Handler.Abstract.NonBlocking
 * {
 *     @Override
 *     public boolean handle(Request request, Response response, Callback callback)
 *     {
 *         if (request.getHttpURI().getPath().startsWith("/yourPath"))
 *         {
 *             // The request is for this Handler
 *             response.setStatus(200);
 *             // The callback is completed when the write is completed.
 *             response.write(true, UTF_8.encode("hello"), callback);
 *             return true;
 *         }
 *         return false;
 *     }
 * }
 * }</pre>
 */
@ManagedObject
public interface Handler extends LifeCycle, Destroyable, Request.Handler
{
    /**
     * <p>An abstract implementation of {@link Handler} that is a {@link ContainerLifeCycle}.</p>
     * <p>The {@link InvocationType} is by default {@link InvocationType#BLOCKING} unless the
     * {@code NonBlocking} variant is used or a specific {@link InvocationType} is passed to
     * the constructor.</p>
     *
     * @see NonBlocking
     */
    @ManagedObject
    abstract class Abstract extends ContainerLifeCycle implements Handler
    {
        //private static final Logger LOG = LoggerFactory.getLogger(Handler.class);

        private final InvocationType _invocationType;

        /**
         * <p>Creates a {@code Handler} with invocation type {@link InvocationType#BLOCKING}.</p>
         */
        public Abstract()
        {
            _invocationType = InvocationType.BLOCKING;

        }

        @Override
        public InvocationType getInvocationType()
        {
            return _invocationType;
        }

        @Override
        protected void doStart() throws Exception
        {
            super.doStart();
        }

        @Override
        protected void doStop() throws Exception
        {
            super.doStop();
        }

        @Override
        public void destroy()
        {
            if (isRunning())
                throw new IllegalStateException(getState());
            super.destroy();
        }

        public Handler getHandler()
        {
            return null;
        }

        public void setHandler(Handler handler)
        {
        }

    }

}
