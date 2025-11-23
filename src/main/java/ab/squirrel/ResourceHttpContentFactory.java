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

package ab.squirrel;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.Objects;

import ab.squirrel.http.HttpContent;
import ab.squirrel.http.MimeTypes;
import ab.squirrel.util.resource.Resource;
import ab.squirrel.util.resource.ResourceFactory;
import ab.squirrel.util.resource.Resources;

/**
 * An HttpContent.Factory for transient content (not cached).  The HttpContent's created by
 * this factory are not intended to be cached, so memory limits for individual
 * HttpOutput streams are enforced.
 */
//public class ResourceHttpContentFactory implements HttpContent.Factory
public class ResourceHttpContentFactory
{
    private final Resource _baseResource;
    private final MimeTypes _mimeTypes;

    public ResourceHttpContentFactory(Resource baseResource, MimeTypes mimeTypes)
    {
        Objects.requireNonNull(mimeTypes, "MimeTypes cannot be null");
        _baseResource = Objects.requireNonNullElse(baseResource, ResourceFactory.root().newResource("."));
        _mimeTypes = mimeTypes;
    }

    public HttpContent getContent(String pathInContext) throws IOException
    {
        try
        {
            Resource resource = resolve(pathInContext);
            if (Resources.missing(resource))
                return null;
            return load(pathInContext, resource);
        }
        catch (Throwable t)
        {
            // There are many potential Exceptions that can reveal a fully qualified path.
            // See Issue #2560 - Always wrap a Throwable here in an InvalidPathException
            // that is limited to only the provided pathInContext.
            // The cause (which might reveal a fully qualified path) is still available,
            // on the Exception and the logging, but is not reported in normal error page situations.
            // This specific exception also allows WebApps to specifically hook into a known / reliable
            // Exception type for ErrorPageErrorHandling logic.
            InvalidPathException saferException = new InvalidPathException(pathInContext, "Invalid PathInContext");
            saferException.initCause(t);
            throw saferException;
        }
    }

    protected Resource resolve(String pathInContext)
    {
        return _baseResource.resolve(pathInContext);
    }

    private HttpContent load(String pathInContext, Resource resource)
    {
        if (resource == null || !resource.exists())
            return null;
        return new HttpContent(resource, _mimeTypes.getMimeByExtension(pathInContext));
    }

    public String toString()
    {
        return "ResourceContentFactory[" + _baseResource + "]@" + hashCode();
    }
}
