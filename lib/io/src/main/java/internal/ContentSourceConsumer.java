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

package ab.squirrel.io.internal;

import ab.squirrel.io.Content;
import ab.squirrel.util.Callback;
import ab.squirrel.util.thread.Invocable;

public class ContentSourceConsumer implements Invocable.Task
{
    private final Content.Source source;
    private final Callback callback;

    public ContentSourceConsumer(Content.Source source, Callback callback)
    {
        this.source = source;
        this.callback = callback;
    }

    @Override
    public void run()
    {
        while (true)
        {
            Content.Chunk chunk = source.read();

            if (chunk == null)
            {
                source.demand(this);
                return;
            }

            if (Content.Chunk.isFailure(chunk))
            {
                callback.failed(chunk.getFailure());
                if (!chunk.isLast())
                    source.fail(chunk.getFailure());
                return;
            }

            chunk.release();

            if (chunk.isLast())
            {
                callback.succeeded();
                return;
            }
        }
    }

    @Override
    public InvocationType getInvocationType()
    {
        return callback.getInvocationType();
    }
}
