package ab.squirrel;

import java.net.URI;
import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.Duration;
import java.util.List;

import org.eclipse.jetty.http.CompressedContentFormat;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.content.FileMappingHttpContentFactory;
import org.eclipse.jetty.http.content.HttpContent;
import org.eclipse.jetty.http.content.PreCompressedHttpContentFactory;
import org.eclipse.jetty.http.content.ResourceHttpContentFactory;
import org.eclipse.jetty.http.content.ValidatingCachingHttpContentFactory;
import org.eclipse.jetty.http.content.VirtualHttpContentFactory;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.server.Context;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Resource Handler will serve static content and handle If-Modified-Since headers. No caching is done.
 * Requests for resources that do not exist are let pass (Eg no 404's).
 */
public class ResourceHandler extends org.eclipse.jetty.server.Handler.Abstract
{
    private static final Logger LOG = LoggerFactory.getLogger(ResourceHandler.class);

    private final ResourceService _resourceService = new ResourceService();
    private ByteBufferPool _byteBufferPool = new ArrayByteBufferPool();
    private Resource _baseResource;
    private Resource _styleSheet = newResource("/org/eclipse/jetty/server/jetty-dir.css");
    private MimeTypes _mimeTypes = new MimeTypes.Mutable();
//    private List<String> _welcomes = List.of("index.html");
    private boolean _useFileMapping = true;
    private String _rootDir = ".";

    public ResourceHandler(String rootDir, Server server)
    {
        LOG.info("loaded");
        _rootDir = rootDir;
        Path rootPath = Paths.get(rootDir).toAbsolutePath().normalize();
        if (Files.isDirectory(rootPath)) {
//            _byteBufferPool = server.getByteBufferPool(server.getContext());

            _resourceService.setDirAllowed(true);
            _resourceService.setHttpContentFactory(newHttpContentFactory());
//            _resourceService.setWelcomeFactory(setupWelcomeFactory());

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

        if (!HttpMethod.GET.is(request.getMethod()) && !HttpMethod.HEAD.is(request.getMethod()))
        {
            // try another handler
            LOG.info("return FALSE");
            return false;
        }

        LOG.info("getContent " + _rootDir + Request.getPathInContext(request));
        HttpContent content = _resourceService.getContent(_rootDir + Request.getPathInContext(request), request);
        if (content == null)
        {
            LOG.info("content is null");
            return false;
        }

        LOG.info("doGet");
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
     * @return Returns the stylesheet as a Resource.
     */
    public Resource getStyleSheet()
    {
        return _styleSheet;
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
     * @param name the name of the resource (relative to `/org/eclipse/jetty/server/`)
     * @return the Resource found, or null if not found.
     */
    private Resource newResource(String name)
    {
        URL url = getClass().getResource(name);
        if (url == null)
            throw new IllegalStateException("Missing server resource: " + name);
        return ResourceFactory.root().newMemoryResource(url);
    }

    private HttpContent.Factory newHttpContentFactory()
    {
        HttpContent.Factory contentFactory = new ResourceHttpContentFactory(getBaseResource(), getMimeTypes());
        if (isUseFileMapping())
            contentFactory = new FileMappingHttpContentFactory(contentFactory);
        contentFactory = new VirtualHttpContentFactory(contentFactory, getStyleSheet(), "text/css");
        contentFactory = new PreCompressedHttpContentFactory(contentFactory, getPrecompressedFormats());
        contentFactory = new ValidatingCachingHttpContentFactory(contentFactory, Duration.ofSeconds(1).toMillis(), getByteBufferPool());
        return contentFactory;
    }



}

