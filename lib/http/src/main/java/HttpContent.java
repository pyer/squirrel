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

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

//import ab.squirrel.http.CompressedContentFormat;
import ab.squirrel.http.DateGenerator;
import ab.squirrel.http.EtagUtils;
import ab.squirrel.http.HttpField;
import ab.squirrel.http.HttpHeader;
import ab.squirrel.http.MimeTypes;
import ab.squirrel.http.MimeTypes.Type;
import ab.squirrel.util.resource.Resource;

/**
 * HttpContent created from a {@link Resource}.
 * <p>The HttpContent is used to server static content that is not
 * cached. So fields and values are only generated as need be an not
 * kept for reuse</p>
 */
//public class ResourceHttpContent implements HttpContent
public class HttpContent
{
    final Resource _resource;
    final Path _path;
    final String _contentType;
    final HttpField _etag;

    public HttpContent(final Resource resource, final String contentType)
    {
        _resource = resource;
        _path = resource.getPath();
        _contentType = contentType;
        _etag = EtagUtils.createWeakEtagField(resource);
    }

    public String getContentTypeValue()
    {
        return _contentType;
    }

    public HttpField getContentType()
    {
        return _contentType == null ? null : new HttpField(HttpHeader.CONTENT_TYPE, _contentType);
    }

    public HttpField getContentEncoding()
    {
        return null;
    }

    public String getContentEncodingValue()
    {
        return null;
    }

    public String getCharacterEncoding()
    {
        return _contentType == null ? null : MimeTypes.getCharsetFromContentType(_contentType);
    }

    public Type getMimeType()
    {
        return _contentType == null ? null : MimeTypes.CACHE.get(MimeTypes.getContentTypeWithoutCharset(_contentType));
    }

    public Instant getLastModifiedInstant()
    {
        return _resource.lastModified();
    }

    public HttpField getLastModified()
    {
        Instant lm = _resource.lastModified();
        return new HttpField(HttpHeader.LAST_MODIFIED, DateGenerator.formatDate(lm));
    }

    public String getLastModifiedValue()
    {
        Instant lm = _resource.lastModified();
        return DateGenerator.formatDate(lm);
    }

    public HttpField getETag()
    {
        return _etag;
    }

    public String getETagValue()
    {
        if (_etag == null)
            return null;
        return _etag.getValue();
    }

    public HttpField getContentLength()
    {
        long l = getContentLengthValue();
        return l == -1 ? null : new HttpField.LongValueHttpField(HttpHeader.CONTENT_LENGTH, l);
    }

    public long getContentLengthValue()
    {
        return _resource.length();
    }

    public Resource getResource()
    {
        return _resource;
    }

    public String toString()
    {
        return String.format("%s@%x{r=%s,ct=%s}", this.getClass().getSimpleName(), hashCode(), _resource, _contentType);
    }

    public ByteBuffer getByteBuffer()
    {
        return null;
    }

    public void release()
    {
    }
}
