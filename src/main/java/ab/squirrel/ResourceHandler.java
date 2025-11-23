package ab.squirrel;

import java.io.IOException;

import java.net.URI;
import java.net.URL;
import java.net.MalformedURLException;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

//import ab.squirrel.http.content.FileMappingHttpContentFactory;
//import ab.squirrel.http.content.PreCompressedHttpContentFactory;
//import ab.squirrel.http.content.ValidatingCachingHttpContentFactory;
//import ab.squirrel.http.content.VirtualHttpContentFactory;

import ab.squirrel.http.ByteRange;
import ab.squirrel.http.EtagUtils;
import ab.squirrel.http.HttpContent;
import ab.squirrel.http.HttpDateTime;
import ab.squirrel.http.HttpField;
import ab.squirrel.http.HttpFields;
import ab.squirrel.http.HttpHeader;
import ab.squirrel.http.HttpMethod;
import ab.squirrel.http.HttpStatus;
import ab.squirrel.http.HttpURI;
import ab.squirrel.http.MimeTypes;
import ab.squirrel.http.MultiPart;
import ab.squirrel.http.MultiPartByteRanges;
import ab.squirrel.http.PreEncodedHttpField;
import ab.squirrel.http.QuotedCSV;
import ab.squirrel.http.QuotedQualityCSV;
//import ab.squirrel.http.ResourceHttpContentFactory;

import ab.squirrel.io.ArrayByteBufferPool;
import ab.squirrel.io.ByteBufferPool;
import ab.squirrel.io.Content;
import ab.squirrel.io.IOResources;

import ab.squirrel.server.Context;
import ab.squirrel.server.Handler;
import ab.squirrel.server.Request;
import ab.squirrel.server.Response;
import ab.squirrel.server.Server;

import ab.squirrel.util.Callback;
import ab.squirrel.util.resource.Resource;
import ab.squirrel.util.resource.ResourceFactory;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Resource Handler will serve static content and handle If-Modified-Since headers. No caching is done.
 * Requests for resources that do not exist are let pass (Eg no 404's).
 */
public class ResourceHandler extends ab.squirrel.server.Handler.Abstract
{
    private static final Logger LOG = LoggerFactory.getLogger(ResourceHandler.class);

    private static final int NO_CONTENT_LENGTH = -1;
    private static final int USE_KNOWN_CONTENT_LENGTH = -2;

    private final Map<String, List<String>> _preferredEncodingOrderCache = new ConcurrentHashMap<>();
    private final List<String> _preferredEncodingOrder = new ArrayList<>();

    private String _rootDir = "./";
    private boolean _etags = false;
    private int _encodingCacheSize = 100;
    private boolean _dirAllowed = true;
    private boolean _acceptRanges = true;

    private HttpField _cacheControl;
    private List<String> _gzipEquivalentFileExtensions;

    private ResourceHttpContentFactory _contentFactory;
    private Resource _baseResource;
    private ByteBufferPool _byteBufferPool = new ArrayByteBufferPool();
    private MimeTypes _mimeTypes = new MimeTypes.Mutable();


    public ResourceHandler(String rootDir, Server server)
    {
        _rootDir = rootDir;
        Path rootPath = Paths.get(rootDir).toAbsolutePath().normalize();
        if (Files.isDirectory(rootPath)) {
            ResourceHttpContentFactory contentFactory = new ResourceHttpContentFactory(_baseResource, getMimeTypes());
            _contentFactory = contentFactory;
            ResourceFactory resourceFactory = ResourceFactory.of(server);
            _baseResource = resourceFactory.newResource(rootPath);
            LOG.info("Root directory is " + rootDir);
        } else {
            LOG.error("ERROR: Unable to find " + rootDir);
        }
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception
    {
        String uri = request.getHttpURI().getPath();
        LOG.info(uri);

        if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.HEAD.is(request.getMethod())) {
            // try another handler
            return false;
        }

        String path = _rootDir + Request.getPathInContext(request);
        HttpContent content = _contentFactory.getContent(path == null ? "" : path);
        if (content == null) {
            return false;
        }

        // doGet(request, response, callback, content);
        String pathInContext = Request.getPathInContext(request);

        // Is this a Range request?
        List<String> reqRanges = request.getHeaders().getValuesList(HttpHeader.RANGE.asString());
        if (!_acceptRanges && !reqRanges.isEmpty())
        {
            reqRanges = List.of();
            response.getHeaders().add(HttpHeader.ACCEPT_RANGES.asString(), "none");
        }

        boolean endsWithSlash = pathInContext.endsWith("/");

        try
        {
            // Directory?
            if (content.getResource().isDirectory())
            {
                sendWelcome(content, pathInContext, endsWithSlash, request, response, callback);
                return true;
            }

            // Conditional response?
            if (passConditionalHeaders(request, response, content, callback))
                return true;

            HttpField contentEncoding = content.getContentEncoding();
            if (contentEncoding != null)
                response.getHeaders().put(contentEncoding);
//            else if (isImplicitlyGzippedContent(pathInContext))
//                response.getHeaders().put(HttpHeader.CONTENT_ENCODING, "gzip");

            // Send the data
            sendData(request, response, callback, content, reqRanges);
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to serve resource: {}", pathInContext, t);
            if (!response.isCommitted())
                writeHttpError(request, response, callback, t);
            else
                callback.failed(t);
        }
        return true;
    }


    public ByteBufferPool getByteBufferPool()
    {
        return _byteBufferPool;
    }

    /**
     * Get the cacheControl header to set on all static content..
     * @return the cacheControl header to set on all static content.
     */
    public String getCacheControl()
    {
        return _cacheControl.getValue();
    }

    /**
     * Set the cacheControl header to set on all static content..
     * @param cacheControl the cacheControl header to set on all static content.
     */
    public void setCacheControl(String cacheControl)
    {
        _cacheControl = new PreEncodedHttpField(HttpHeader.CACHE_CONTROL, cacheControl);
    }

    /**
     * @return file extensions that signify that a file is gzip compressed. Eg ".svgz"
     */
    public List<String> getGzipEquivalentFileExtensions()
    {
        return _gzipEquivalentFileExtensions;
    }

    /**
     * Set file extensions that signify that a file is gzip compressed. Eg ".svgz".
     * @param gzipEquivalentFileExtensions file extensions that signify that a file is gzip compressed. Eg ".svgz"
     */
    public void setGzipEquivalentFileExtensions(List<String> gzipEquivalentFileExtensions)
    {
        _gzipEquivalentFileExtensions = gzipEquivalentFileExtensions;
    }

    public MimeTypes getMimeTypes()
    {
        return _mimeTypes;
    }

    /**
     * @return If true, range requests and responses are supported
     */
    public boolean isAcceptRanges()
    {
        return _acceptRanges;
    }

    /**
     * @param acceptRanges If true, range requests and responses are supported
     */
    public void setAcceptRanges(boolean acceptRanges)
    {
        _acceptRanges = acceptRanges;
    }

    /**
     * @return If true, directory listings are returned if no welcome file is found. Else 403 Forbidden.
     */
    public boolean isDirAllowed()
    {
        return _dirAllowed;
    }

    /**
     * @param dirAllowed If true, directory listings are returned if no welcome target is found. Else 403 Forbidden.
     */
    public void setDirAllowed(boolean dirAllowed)
    {
        _dirAllowed = dirAllowed;
    }

    /**
     * @return True if ETag processing is done
     */
    public boolean isEtags()
    {
        return _etags;
    }

    /**
     * @param etags True if ETag processing is done
     */
    public void setEtags(boolean etags)
    {
        _etags = etags;
    }

/*
    public WelcomeMode getWelcomeMode()
    {
        return _welcomeMode;
    }

    public WelcomeFactory getWelcomeFactory()
    {
        return _welcomeFactory;
    }
*/
/*
    public void setWelcomeMode(WelcomeMode welcomeMode)
    {
        _welcomeMode = Objects.requireNonNull(welcomeMode);
    }
*/

    public int getEncodingCacheSize()
    {
        return _encodingCacheSize;
    }

    public void setEncodingCacheSize(int encodingCacheSize)
    {
        _encodingCacheSize = encodingCacheSize;
        if (encodingCacheSize > _preferredEncodingOrderCache.size())
            _preferredEncodingOrderCache.clear();
    }


    protected void writeHttpError(Request request, Response response, Callback callback, int status)
    {
        Response.writeError(request, response, callback, status);
    }

    protected void writeHttpError(Request request, Response response, Callback callback, Throwable cause)
    {
        Response.writeError(request, response, callback, cause);
    }

    protected void writeHttpError(Request request, Response response, Callback callback, int status, String msg, Throwable cause)
    {
        Response.writeError(request, response, callback, status, msg, cause);
    }

    private List<String> getPreferredEncodingOrder(Request request)
    {
        Enumeration<String> headers = request.getHeaders().getValues(HttpHeader.ACCEPT_ENCODING.asString());
        if (!headers.hasMoreElements())
            return Collections.emptyList();

        String key = headers.nextElement();
        if (headers.hasMoreElements())
        {
            StringBuilder sb = new StringBuilder(key.length() * 2);
            do
            {
                sb.append(',').append(headers.nextElement());
            }
            while (headers.hasMoreElements());
            key = sb.toString();
        }

        List<String> values = _preferredEncodingOrderCache.get(key);
        if (values == null)
        {
            QuotedQualityCSV encodingQualityCSV = new QuotedQualityCSV(_preferredEncodingOrder);
            encodingQualityCSV.addValue(key);
            values = encodingQualityCSV.getValues();

            // keep cache size in check even if we get strange/malicious input
            if (_preferredEncodingOrderCache.size() > _encodingCacheSize)
                _preferredEncodingOrderCache.clear();

            _preferredEncodingOrderCache.put(key, values);
        }

        return values;
    }

    /**
     * @return true if the request was processed, false otherwise.
     */
    protected boolean passConditionalHeaders(Request request, Response response, HttpContent content, Callback callback) throws IOException
    {
        try
        {
            String ifm = null;
            String ifnm = null;
            String ifms = null;
            String ifums = null;

            // Find multiple fields by iteration as an optimization
            for (HttpField field : request.getHeaders())
            {
                if (field.getHeader() != null)
                {
                    switch (field.getHeader())
                    {
                        case IF_MATCH -> ifm = field.getValue();
                        case IF_NONE_MATCH -> ifnm = field.getValue();
                        case IF_MODIFIED_SINCE -> ifms = field.getValue();
                        case IF_UNMODIFIED_SINCE -> ifums = field.getValue();
                        default ->
                        {
                        }
                    }
                }
            }

            if (_etags)
            {
                String etag = content.getETagValue();
                if (etag != null)
                {
                    // TODO: this is a hack to get the etag of the non-preCompressed version.
                    etag = EtagUtils.rewriteWithSuffix(content.getETagValue(), "");
                    if (ifm != null)
                    {
                        String matched = matchesEtag(etag, ifm);
                        if (matched == null)
                        {
                            writeHttpError(request, response, callback, HttpStatus.PRECONDITION_FAILED_412);
                            return true;
                        }
                    }

                    if (ifnm != null)
                    {
                        String matched = matchesEtag(etag, ifnm);
                        if (matched != null)
                        {
                            response.getHeaders().put(HttpHeader.ETAG, matched);
                            writeHttpError(request, response, callback, HttpStatus.NOT_MODIFIED_304);
                            return true;
                        }

                        // If etag requires content to be served, then do not check if-modified-since
                        return false;
                    }
                }
            }

            // Handle if modified since
            if (ifms != null && ifnm == null)
            {
                //Get jetty's Response impl
                String mdlm = content.getLastModifiedValue();
                if (ifms.equals(mdlm))
                {
                    writeHttpError(request, response, callback, HttpStatus.NOT_MODIFIED_304);
                    return true;
                }

                // TODO: what should we do when we get a crappy date?
                long ifmsl = HttpDateTime.parseToEpoch(ifms);
                if (ifmsl != -1)
                {
                    long lm = content.getResource().lastModified().toEpochMilli();
                    if (lm != -1 && lm / 1000 <= ifmsl / 1000)
                    {
                        writeHttpError(request, response, callback, HttpStatus.NOT_MODIFIED_304);
                        return true;
                    }
                }
            }

            // Parse the if[un]modified dates and compare to resource
            if (ifums != null && ifm == null)
            {
                // TODO: what should we do when we get a crappy date?
                long ifumsl = HttpDateTime.parseToEpoch(ifums);
                if (ifumsl != -1)
                {
                    long lm = content.getResource().lastModified().toEpochMilli();
                    if (lm != -1 && lm / 1000 > ifumsl / 1000)
                    {
                        writeHttpError(request, response, callback, HttpStatus.PRECONDITION_FAILED_412);
                        return true;
                    }
                }
            }
        }
        catch (IllegalArgumentException iae)
        {
            if (!response.isCommitted())
                writeHttpError(request, response, callback, HttpStatus.BAD_REQUEST_400, null, iae);
            throw iae;
        }

        return false;
    }

    /**
     * Find a matches between a Content ETag and a Request Field ETag reference.
     * @param contentETag the content etag to match against (can be null)
     * @param requestEtag the request etag (can be null, a single entry, or even a CSV list)
     * @return the matched etag, or null if no matches.
     */
    private String matchesEtag(String contentETag, String requestEtag)
    {
        if (contentETag == null || requestEtag == null)
        {
            return null;
        }

        // Per https://www.rfc-editor.org/rfc/rfc9110#section-8.8.3
        // An Etag header field value can contain a "," (comma) within itself.
        //   If-Match: W/"abc,xyz", "123456"
        // This means we have to parse with QuotedCSV all the time, as we cannot just
        // test for the existence of a "," (comma) in the value to know if it's delimited or not
        QuotedCSV quoted = new QuotedCSV(true, requestEtag);
        for (String tag : quoted)
        {
            if (EtagUtils.matches(contentETag, tag))
            {
                return tag;
            }
        }

        // no matches
        return null;
    }

    private void sendWelcome(HttpContent content, String pathInContext, boolean endsWithSlash, Request request, Response response, Callback callback) throws Exception
    {
        String welcome = "index.html";
        String welcomeTarget = _rootDir + welcome;
        if (LOG.isDebugEnabled()) {
            LOG.debug("sendWelcome(req={}, rsp={}, cbk={}) target={}", request, response, callback, welcomeTarget);
        }

        if (Files.isReadable(Paths.get(welcomeTarget))) {
            HttpContent c = _contentFactory.getContent(welcomeTarget);
            sendData(request, response, callback, c, List.of());
        } else {

            if (!passConditionalHeaders(request, response, content, callback)) {
                writeHttpError(request, response, callback, HttpStatus.FORBIDDEN_403);
            }
        }
    }

    private void sendData(Request request, Response response, Callback callback, HttpContent content, List<String> reqRanges) throws IOException
    {
        if (LOG.isDebugEnabled()) {
            LOG.debug("sendData(req={}, resp={}, callback={}) content={}, reqRanges={})",
                request, response, callback, content, reqRanges);
        }

        long contentLength = content.getContentLengthValue();
        callback = Callback.from(callback, content::release);

        if (LOG.isDebugEnabled())
            LOG.debug(String.format("sendData content=%s", content));

        if (reqRanges.isEmpty())
        {
            // If there are no ranges, send the entire content.
            if (contentLength >= 0)
                putHeaders(response, content, USE_KNOWN_CONTENT_LENGTH);
            else
                putHeaders(response, content, NO_CONTENT_LENGTH);
            writeHttpContent(request, response, callback, content);
            return;
        }

        // Parse the satisfiable ranges.
        List<ByteRange> ranges = ByteRange.parse(reqRanges, contentLength);

        // If there are no satisfiable ranges, send a 416 response.
        if (ranges.isEmpty())
        {
            response.getHeaders().put(HttpHeader.CONTENT_RANGE, ByteRange.toNonSatisfiableHeaderValue(contentLength));
            Response.writeError(request, response, callback, HttpStatus.RANGE_NOT_SATISFIABLE_416);
            return;
        }

        // If there is only a single valid range, send that range with a 206 response.
        if (ranges.size() == 1)
        {
            ByteRange range = ranges.get(0);
            putHeaders(response, content, range.getLength());
            response.setStatus(HttpStatus.PARTIAL_CONTENT_206);
            response.getHeaders().put(HttpHeader.CONTENT_RANGE, range.toHeaderValue(contentLength));

            // TODO use a buffer pool
            IOResources.copy(content.getResource(), response, null, 0, false, range.first(), range.getLength(), callback);
            return;
        }

        // There are multiple non-overlapping ranges, send a multipart/byteranges 206 response.
        response.setStatus(HttpStatus.PARTIAL_CONTENT_206);
        String contentType = "multipart/byteranges; boundary=";
        String boundary = MultiPart.generateBoundary(null, 24);
        MultiPartByteRanges.ContentSource byteRanges = new MultiPartByteRanges.ContentSource(boundary);
        ranges.forEach(range -> byteRanges.addPart(new MultiPartByteRanges.Part(content.getContentTypeValue(), content.getResource(), range, contentLength, request.getComponents().getByteBufferPool())));
        byteRanges.close();
        long partsContentLength = byteRanges.getLength();
        putHeaders(response, content, partsContentLength);
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, contentType + boundary);
        Content.copy(byteRanges, response, callback);
    }

    protected void writeHttpContent(Request request, Response response, Callback callback, HttpContent content)
    {
        try
        {
            ByteBuffer buffer = content.getByteBuffer(); // this buffer is going to be consumed by response.write()
            if (buffer != null)
            {
                response.write(true, buffer, callback);
            }
            else
            {
                IOResources.copy(
                    content.getResource(),
                    response, request.getComponents().getByteBufferPool(),
                    request.getConnectionMetaData().getHttpConfiguration().getOutputBufferSize(),
                    request.getConnectionMetaData().getHttpConfiguration().isUseOutputDirectByteBuffers(),
                    callback);
            }
        }
        catch (Throwable x)
        {
            content.release();
            callback.failed(x);
        }
    }

    protected void putHeaders(Response response, HttpContent content, long contentLength)
    {
        // TODO it is very inefficient to do many put's to a HttpFields, as each put is a full iteration.
        //      it might be better remove headers en masse and then just add the extras:
        // NOTE: If these headers come from a Servlet Filter we shouldn't override them here.
//        headers.remove(EnumSet.of(
//            HttpHeader.LAST_MODIFIED,
//            HttpHeader.CONTENT_LENGTH,
//            HttpHeader.CONTENT_TYPE,
//            HttpHeader.CONTENT_ENCODING,
//            HttpHeader.ETAG,
//            HttpHeader.ACCEPT_RANGES,
//            HttpHeader.CACHE_CONTROL
//            ));
//        HttpField lm = content.getLastModified();
//        if (lm != null)
//            headers.add(lm);
//        etc.

        HttpField lm = content.getLastModified();
        if (lm != null)
            response.getHeaders().put(lm);

        if (contentLength == USE_KNOWN_CONTENT_LENGTH)
        {
            response.getHeaders().put(content.getContentLength());
        }
        else if (contentLength > NO_CONTENT_LENGTH)
        {
            response.getHeaders().put(HttpHeader.CONTENT_LENGTH, contentLength);
        }

        HttpField ct = content.getContentType();
        if (ct != null)
            response.getHeaders().put(ct);

        HttpField ce = content.getContentEncoding();
        if (ce != null)
            response.getHeaders().put(ce);

        if (_etags)
        {
            HttpField et = content.getETag();
            if (et != null)
                response.getHeaders().put(et);
        }

        if (_acceptRanges && !response.getHeaders().contains(HttpHeader.ACCEPT_RANGES))
            response.getHeaders().put(new PreEncodedHttpField(HttpHeader.ACCEPT_RANGES, "bytes"));
        if (_cacheControl != null && !response.getHeaders().contains(HttpHeader.CACHE_CONTROL))
            response.getHeaders().put(_cacheControl);
    }

}
