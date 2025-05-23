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

package ab.squirrel.util.resource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ab.squirrel.util.Index;
import ab.squirrel.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java NIO Path Resource.
 */
public class PathResource extends Resource
{
    private static final Logger LOG = LoggerFactory.getLogger(PathResource.class);

    /**
     * @deprecated Using ResourceFactoryInternals.isSupported() instead.
     */
    @Deprecated(since = "12.0.4", forRemoval = true)
    public static Index<String> SUPPORTED_SCHEMES = new Index.Builder<String>().build();

    // The path object represented by this instance
    private final Path path;
    // The as-requested URI for this path object
    private final URI uri;
    // True / False to indicate if this is an alias of something else, or null if the alias hasn't been resolved
    private Boolean alias;
    // The Path representing the real-path of this PathResource instance. (populated during alias checking)
    private Path realPath;

    /**
     * Test if the paths are the same name.
     *
     * <p>
     * If the real path is not the same as the absolute path
     * then we know that the real path is the alias for the
     * provided path.
     * </p>
     *
     * <p>
     * For OS's that are case insensitive, this should
     * return the real (on-disk / case correct) version
     * of the path.
     * </p>
     *
     * <p>
     * We have to be careful on Windows and OSX.
     * </p>
     *
     * <p>
     * Assume we have the following scenario:
     * </p>
     *
     * <pre>
     *   Path a = new File("foo").toPath();
     *   Files.createFile(a);
     *   Path b = new File("FOO").toPath();
     * </pre>
     *
     * <p>
     * There now exists a file called {@code foo} on disk.
     * Using Windows or OSX, with a Path reference of
     * {@code FOO}, {@code Foo}, {@code fOO}, etc.. means the following
     * </p>
     *
     * <pre>
     *                        |  OSX    |  Windows   |  Linux
     * -----------------------+---------+------------+---------
     * Files.exists(a)        |  True   |  True      |  True
     * Files.exists(b)        |  True   |  True      |  False
     * Files.isSameFile(a,b)  |  True   |  True      |  False
     * a.equals(b)            |  False  |  True      |  False
     * </pre>
     *
     * <p>
     * See the javadoc for Path.equals() for details about this FileSystem
     * behavior difference
     * </p>
     *
     * <p>
     * We also cannot rely on a.compareTo(b) as this is roughly equivalent
     * in implementation to a.equals(b)
     * </p>
     */
    public static boolean isSameName(Path pathA, Path pathB)
    {
        int aCount = pathA.getNameCount();
        int bCount = pathB.getNameCount();
        if (aCount != bCount)
        {
            // different number of segments
            return false;
        }

        // compare each segment of path, backwards
        for (int i = bCount; i-- > 0; )
        {
            if (!pathA.getName(i).toString().equals(pathB.getName(i).toString()))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Construct a new PathResource from a URI object.
     * <p>
     * Must be an absolute URI using the <code>file</code> scheme.
     *
     * @param uri the URI to build this PathResource from.
     */
    PathResource(URI uri)
    {
        this(uri, false);
    }

    PathResource(URI uri, boolean bypassAllowedSchemeCheck)
    {
        this(Paths.get(uri), uri, bypassAllowedSchemeCheck);
    }

    PathResource(Path path)
    {
        this(path, path.toUri(), true);
    }

    /**
     * Create a PathResource.
     *
     * @param path the Path object
     * @param uri the as-requested URI for the resource
     * @param bypassAllowedSchemeCheck true to bypass the allowed schemes check
     */
    PathResource(Path path, URI uri, boolean bypassAllowedSchemeCheck)
    {
        if (!uri.isAbsolute())
            throw new IllegalArgumentException("not an absolute uri: " + uri);
        if (!bypassAllowedSchemeCheck && !ResourceFactoryInternals.isSupported(uri.getScheme()))
            throw new IllegalArgumentException("not an allowed scheme: " + uri);

        if (Files.isDirectory(path))
        {
            String uriString = uri.toASCIIString();
            if (!uriString.endsWith("/"))
                uri = URIUtil.correctURI(URI.create(uriString + "/"));
        }

        this.path = path;
        this.uri = uri;
    }

    @Override
    public boolean exists()
    {
        if (alias == null)
        {
            // no alias check performed
            return Files.exists(path);
        }
        else
        {
            if (realPath == null)
                return false;
            return Files.exists(realPath);
        }
    }

    @Override
    public Path getPath()
    {
        return path;
    }

    @Override
    public boolean contains(Resource other)
    {
        if (other == null)
            return false;

        Path thisPath = getPath();
        if (thisPath == null)
            throw new UnsupportedOperationException("Resources without a Path must implement contains");

        Path otherPath = other.getPath();
        return otherPath != null &&
            otherPath.getFileSystem().equals(thisPath.getFileSystem()) &&
            otherPath.startsWith(thisPath);
    }

    public Path getRealPath()
    {
        resolveAlias();
        return realPath;
    }

    @Override
    public URI getRealURI()
    {
        Path realPath = getRealPath();
        return (realPath == null) ? null : realPath.toUri();
    }

    public List<Resource> list()
    {
        if (!isDirectory())
            return List.of(); // empty

        try (Stream<Path> dirStream = Files.list(getPath()))
        {
            return dirStream.map(PathResource::new).collect(Collectors.toCollection(ArrayList::new));
        }
        catch (DirectoryIteratorException e)
        {
            LOG.debug("Directory list failure", e);
        }
        catch (IOException e)
        {
            LOG.debug("Directory list access failure", e);
        }
        return List.of(); // empty
    }

    @Override
    public boolean isAlias()
    {
        resolveAlias();
        return alias != null && alias;
    }

    @Override
    public String getName()
    {
        return path.toAbsolutePath().toString();
    }

    @Override
    public String getFileName()
    {
        Path fn = path.getFileName();
        if (fn == null) // if path has no segments (eg "/")
            return "";
        return fn.toString();
    }

    @Override
    public URI getURI()
    {
        return this.uri;
    }

    @Override
    public Resource resolve(String subUriPath)
    {
        // Check that the path is within the root,
        // but use the original path to create the
        // resource, to preserve aliasing.
        if (URIUtil.isNotNormalWithinSelf(subUriPath))
            throw new IllegalArgumentException(subUriPath);

        if ("/".equals(subUriPath))
            return this;

        URI uri = getURI();
        URI resolvedUri = URIUtil.addPath(uri, subUriPath);
        Path path = Paths.get(resolvedUri);
        return newResource(path, resolvedUri);
    }

    /**
     * Internal override for creating a new PathResource.
     * Used by MountedPathResource (eg)
     */
    protected Resource newResource(Path path, URI uri)
    {
        return new PathResource(path, uri, true);
    }

    @Override
    public boolean isDirectory()
    {
        return Files.isDirectory(getPath());
    }

    @Override
    public boolean isReadable()
    {
        return Files.isReadable(getPath());
    }

    @Override
    public Instant lastModified()
    {
        Path path = getPath();
        if (path == null)
            return Instant.EPOCH;

        if (!Files.exists(path))
            return Instant.EPOCH;

        try
        {
            FileTime ft = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
            return ft.toInstant();
        }
        catch (IOException e)
        {
            LOG.trace("IGNORED", e);
            return Instant.EPOCH;
        }
    }

    @Override
    public long length()
    {
        try
        {
            return Files.size(getPath());
        }
        catch (IOException e)
        {
            return -1L;
        }
    }

    /**
     * <p>
     * Perform a check of the original Path and as-requested URI to determine
     * if this resource is an alias to another name/location.
     * </p>
     *
     * <table>
     * <caption>Alias Check Logic</caption>
     * <thead>
     * <tr>
     * <th>path</th>
     * <th>realPath</th>
     * <th>uri-as-requested</th>
     * <th>uri-from-realPath</th>
     * <th>alias</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     * <td><code>C:/temp/aa./foo.txt</code></td>
     * <td><code>C:/temp/aa/foo.txt</code></td>
     * <td><code>file:///C:/temp/aa./foo.txt</code></td>
     * <td><code>file:///C:/temp/aa./foo.txt</code></td>
     * <td>true</td>
     * </tr>
     * <tr>
     * <td><code>/tmp/foo-symlink</code></td>
     * <td><code>/tmp/bar.txt</code></td>
     * <td><code>file:///tmp/foo-symlink</code></td>
     * <td><code>file:///tmp/bar.txt</code></td>
     * <td>true</td>
     * </tr>
     * <tr>
     * <td><code>C:/temp/aa.txt</code></td>
     * <td><code>C:/temp/AA.txt</code></td>
     * <td><code>file:///C:/temp/aa.txt</code></td>
     * <td><code>file:///C:/temp/AA.txt</code></td>
     * <td>true</td>
     * </tr>
     * <tr>
     * <td><code>/tmp/bar-exists/../foo.txt</code></td>
     * <td><code>/tmp/foo.txt</code></td>
     * <td><code>file:///tmp/bar-exists/../foo.txt</code></td>
     * <td><code>file:///tmp/foo.txt</code></td>
     * <td>true</td>
     * </tr>
     * <tr>
     * <td><code>/tmp/doesnt-exist.txt</code></td>
     * <td>null (does not exist)</td>
     * <td><code>file:///tmp/doesnt-exist.txt</code></td>
     * <td>null (does not exist)</td>
     * <td>false</td>
     * </tr>
     * <tr>
     * <td><code>/tmp/doesnt-exist/../foo.txt</code></td>
     * <td>null (intermediate does not exist)</td>
     * <td><code>file:///tmp/doesnt-exist/../foo.txt</code></td>
     * <td>null (intermediate does not exist)</td>
     * <td>false</td>
     * </tr>
     * <tr>
     * <td><code>/var/protected/config.xml</code></td>
     * <td>null (no permissions)</td>
     * <td><code>file:///var/protected/config.xml</code></td>
     * <td>null (no permission)</td>
     * <td>false</td>
     * </tr>
     * <tr>
     * <td><code>/tmp/foo-symlink</code></td>
     * <td>null (broken symlink, doesn't point to anything)</td>
     * <td><code>file:///tmp/foo-symlink</code></td>
     * <td>null (broken symlink, doesn't point to anything)</td>
     * <td>false</td>
     * </tr>
     * <tr>
     * <td><code>C:/temp/cannot:be:referenced</code></td>
     * <td>null (illegal filename)</td>
     * <td><code>file:///C:/temp/cannot:be:referenced</code></td>
     * <td>null (illegal filename)</td>
     * <td>false</td>
     * </tr>
     * </tbody>
     * </table>
     */
    private void resolveAlias()
    {
        if (alias == null)
        {
            try
            {
                // Default behavior is to follow symlinks.
                // We don't want to use the NO_FOLLOW_LINKS parameter as that takes this call from
                // being filesystem aware, and using FileSystem specific techniques to find
                // the real file, to being done in-API (which doesn't work reliably on
                // filesystems that have different names for the same file.
                // eg: case-insensitive file systems, unicode name normalization,
                // alternate names, etc)
                // We also don't want to use Path.normalize() here as that eliminates
                // the knowledge of what directories are being navigated through.
                realPath = path.toRealPath();
            }
            catch (Exception e)
            {
                if (e instanceof IOException)
                    LOG.trace("IGNORED", e);
                else
                    LOG.warn("bad alias ({} {}) for {}", e.getClass().getName(), e.getMessage(), path);
                // Not possible to serve this resource.
                //  - This resource doesn't exist.
                //  - No access rights to this resource.
                //  - Unable to read the file or directory.
                //  - Navigation segments (eg: "foo/../test.txt") would go through something that doesn't exist, or not accessible.
                //  - FileSystem doesn't support toRealPath.
                return;
            }

            /* If the path and realPath are the same, also check
             * The as-requested URI as it will represent what was
             * URI created this PathResource.
             * e.g. the input of `resolve("aa./foo.txt")
             * on windows would resolve the path, but the Path.toUri() would
             * not always show this extension-less access.
             * The as-requested URI will retain this extra '.' and be used
             * to evaluate if the realPath.toUri() is the same as the as-requested URI.
             *
             * // On Windows
             *  PathResource resource         = PathResource("C:/temp");
             *  PathResource child            = resource.resolve("aa./foo.txt");
             *  child.exists()                == true
             *  child.isAlias()               == true
             *  child.toUri()                 == "file:///C:/temp/aa./foo.txt"
             *  child.getPath().toUri()       == "file:///C:/temp/aa/foo.txt"
             *  child.getRealURI()            == "file:///C:/temp/aa/foo.txt"
             */
            alias = !isSameName(path, realPath) || !Objects.equals(uri, toUri(realPath));
        }
    }

    /**
     * Ensure Path to URI is sane when it returns a directory reference.
     *
     * <p>
     *     This is different than {@link Path#toUri()} in that not
     *     all FileSystems seem to put the trailing slash on a directory
     *     reference in the URI.
     * </p>
     *
     * @param path the path to convert to URI
     * @return the appropriate URI for the path
     */
    protected URI toUri(Path path)
    {
        URI pathUri = path.toUri();
        String rawUri = pathUri.toASCIIString();

        if (Files.isDirectory(path) && !rawUri.endsWith("/"))
        {
            return URI.create(rawUri + '/');
        }
        return pathUri;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PathResource other = (PathResource)obj;
        return Objects.equals(path, other.path) && Objects.equals(uri, other.uri);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(path, uri);
    }

    @Override
    public String toString()
    {
        return this.uri.toASCIIString();
    }
}
