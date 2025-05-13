package ab.squirrel.server.handler;

import java.net.URI;
import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import ab.squirrel.http.CompressedContentFormat;
import ab.squirrel.http.HttpMethod;
import ab.squirrel.http.HttpURI;
import ab.squirrel.http.MimeTypes;
import ab.squirrel.http.content.FileMappingHttpContentFactory;
import ab.squirrel.http.content.HttpContent;
import ab.squirrel.http.content.PreCompressedHttpContentFactory;
import ab.squirrel.http.content.ResourceHttpContentFactory;
import ab.squirrel.http.content.ValidatingCachingHttpContentFactory;
import ab.squirrel.http.content.VirtualHttpContentFactory;
import ab.squirrel.io.ArrayByteBufferPool;
import ab.squirrel.io.ByteBufferPool;
import ab.squirrel.server.Context;
import ab.squirrel.server.Handler;
import ab.squirrel.server.Request;
import ab.squirrel.server.ResourceService;
import ab.squirrel.server.Response;
import ab.squirrel.server.Server;
import ab.squirrel.util.Callback;
/*
import ab.squirrel.util.URIUtil;
*/
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

    private ByteBufferPool _byteBufferPool = new ArrayByteBufferPool();
    private ResourceService _resourceService;
    private Resource _baseResource;
    private MimeTypes _mimeTypes = new MimeTypes.Mutable();
//    private List<String> _welcomes = List.of("index.html");
    private boolean _useFileMapping = true;
    private String _rootDir = ".";

    public ResourceHandler(String rootDir, Server server)
    {
        _rootDir = rootDir;
        _resourceService = new ResourceService(rootDir);
        Path rootPath = Paths.get(rootDir).toAbsolutePath().normalize();
        if (Files.isDirectory(rootPath)) {
            _resourceService.setHttpContentFactory(newHttpContentFactory());
            ResourceFactory resourceFactory = ResourceFactory.of(server);
            _baseResource = resourceFactory.newResource(rootPath);
            LOG.info("Root directory is " + rootDir);
        } else {
            LOG.error("ERROR: Unable to find " + rootDir);
        }
    }

    private HttpContent.Factory newHttpContentFactory()
    {
        HttpContent.Factory contentFactory = new ResourceHttpContentFactory(getBaseResource(), getMimeTypes());
        if (isUseFileMapping())
            contentFactory = new FileMappingHttpContentFactory(contentFactory);
//        contentFactory = new VirtualHttpContentFactory(contentFactory, getStyleSheet(), "text/css");
        contentFactory = new PreCompressedHttpContentFactory(contentFactory, getPrecompressedFormats());
        contentFactory = new ValidatingCachingHttpContentFactory(contentFactory, Duration.ofSeconds(1).toMillis(), getByteBufferPool());
        return contentFactory;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception
    {
        String uri = request.getHttpURI().getPath();
        LOG.info(uri);

        if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.HEAD.is(request.getMethod()))
        {
            // try another handler
            return false;
        }

        HttpContent content = _resourceService.getContent(Request.getPathInContext(request), request);
        if (content == null)
        {
            return false;
        }

        _resourceService.doGet(request, response, callback, content);
        return true;
    }

       /**
     * @return Returns the resourceBase.
     */
    public Resource getBaseResource()
    {
        return _baseResource;
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
        return _resourceService.getCacheControl();
    }

    /**
     * @return file extensions that signify that a file is gzip compressed. Eg ".svgz"
     */
    public List<String> getGzipEquivalentFileExtensions()
    {
        return _resourceService.getGzipEquivalentFileExtensions();
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
        return _resourceService.isAcceptRanges();
    }

    /**
     * @return If true, directory listings are returned if no welcome file is found. Else 403 Forbidden.
     */
    public boolean isDirAllowed()
    {
        return _resourceService.isDirAllowed();
    }

    /**
     * @return True if ETag processing is done
     */
    public boolean isEtags()
    {
        return _resourceService.isEtags();
    }

    public boolean isUseFileMapping()
    {
        return _useFileMapping;
    }

    /**
     * @return Precompressed resources formats that can be used to serve compressed variant of resources.
     */
    public List<CompressedContentFormat> getPrecompressedFormats()
    {
        return _resourceService.getPrecompressedFormats();
    }


    /**
     * Create a new Resource representing a resources that is managed by the Server.
     *
     * @param name the name of the resource (relative to `/ab.squirrel/server/`)
     * @return the Resource found, or null if not found.
     */
    private Resource newResource(String name)
    {
        URL url = getClass().getResource(name);
        if (url == null)
            throw new IllegalStateException("Missing server resource: " + name);
        return ResourceFactory.root().newMemoryResource(url);
    }



}

