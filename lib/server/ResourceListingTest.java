//

package ab.squirrel.server;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import ab.squirrel.toolchain.test.FS;
import ab.squirrel.toolchain.test.jupiter.WorkDir;
import ab.squirrel.toolchain.test.jupiter.WorkDirExtension;
import ab.squirrel.toolchain.xhtml.XHTMLValidator;
import ab.squirrel.util.resource.Resource;
import ab.squirrel.util.resource.ResourceFactory;


import nut.annotations.Test;
import static nut.Assert.*;


/*
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(WorkDirExtension.class)
*/

public class ResourceListingTest
{
    @Test
    public void testBasicResourceXHtmlListingRoot(WorkDir workDir) throws IOException
    {
        Path root = workDir.getEmptyPathDir();
        FS.touch(root.resolve("entry1.txt"));
        FS.touch(root.resolve("entry2.dat"));
        Files.createDirectory(root.resolve("dirFoo"));
        Files.createDirectory(root.resolve("dirBar"));
        Files.createDirectory(root.resolve("dirZed"));

        try (ResourceFactory.Closeable resourceFactory = ResourceFactory.closeable())
        {
            Resource resource = resourceFactory.newResource(root);
            String content = ResourceListing.getAsXHTML(resource, "/", false, null);
            assertDoesNotThrow(() -> XHTMLValidator.validate(content));

            assertThat(content, containsString("entry1.txt"));
            assertThat(content, containsString("<a href=\"/entry1.txt\">"));
            assertThat(content, containsString("entry2.dat"));
            assertThat(content, containsString("<a href=\"/entry2.dat\">"));
            assertThat(content, containsString("dirFoo/"));
            assertThat(content, containsString("<a href=\"/dirFoo/\">"));
            assertThat(content, containsString("dirBar/"));
            assertThat(content, containsString("<a href=\"/dirBar/\">"));
            assertThat(content, containsString("dirZed/"));
            assertThat(content, containsString("<a href=\"/dirZed/\">"));
        }
    }

    @Test
    public void testBasicResourceXHtmlListingDeep(WorkDir workDir) throws IOException
    {
        Path root = workDir.getEmptyPathDir();
        FS.touch(root.resolve("entry1.txt"));
        FS.touch(root.resolve("entry2.dat"));
        Files.createDirectory(root.resolve("dirFoo"));
        Files.createDirectory(root.resolve("dirBar"));
        Files.createDirectory(root.resolve("dirZed"));

        try (ResourceFactory.Closeable resourceFactory = ResourceFactory.closeable())
        {
            Resource resource = resourceFactory.newResource(root);
            String content = ResourceListing.getAsXHTML(resource, "/deep/", false, null);
            assertDoesNotThrow(() -> XHTMLValidator.validate(content));

            assertThat(content, containsString("entry1.txt"));
            assertThat(content, containsString("<a href=\"/deep/entry1.txt\">"));
            assertThat(content, containsString("entry2.dat"));
            assertThat(content, containsString("<a href=\"/deep/entry2.dat\">"));
            assertThat(content, containsString("dirFoo/"));
            assertThat(content, containsString("<a href=\"/deep/dirFoo/\">"));
            assertThat(content, containsString("dirBar/"));
            assertThat(content, containsString("<a href=\"/deep/dirBar/\">"));
            assertThat(content, containsString("dirZed/"));
            assertThat(content, containsString("<a href=\"/deep/dirZed/\">"));
        }
    }

    @Test
    public void testResourceCollectionXHtmlListingContext(WorkDir workDir) throws IOException
    {
        Path root = workDir.getEmptyPathDir();
        Path docrootA = root.resolve("docrootA");
        Files.createDirectory(docrootA);
        FS.touch(docrootA.resolve("entry1.txt"));
        FS.touch(docrootA.resolve("entry2.dat"));
        FS.touch(docrootA.resolve("similar.txt"));
        Files.createDirectory(docrootA.resolve("dirSame"));
        Files.createDirectory(docrootA.resolve("dirFoo"));
        Files.createDirectory(docrootA.resolve("dirBar"));

        Path docrootB = root.resolve("docrootB");
        Files.createDirectory(docrootB);
        FS.touch(docrootB.resolve("entry3.png"));
        FS.touch(docrootB.resolve("entry4.tar.gz"));
        FS.touch(docrootB.resolve("similar.txt")); // same filename as in docrootA
        Files.createDirectory(docrootB.resolve("dirSame")); // same directory name as in docrootA
        Files.createDirectory(docrootB.resolve("dirCid"));
        Files.createDirectory(docrootB.resolve("dirZed"));

        try (ResourceFactory.Closeable resourceFactory = ResourceFactory.closeable())
        {
            List<URI> uriRootList = List.of(docrootA.toUri(), docrootB.toUri());
            Resource resource = resourceFactory.newResource(uriRootList);
            String content = ResourceListing.getAsXHTML(resource, "/context/", false, null);
            assertDoesNotThrow(() -> XHTMLValidator.validate(content));

            assertThat(content, containsString("entry1.txt"));
            assertThat(content, containsString("<a href=\"/context/entry1.txt\">"));
            assertThat(content, containsString("entry2.dat"));
            assertThat(content, containsString("<a href=\"/context/entry2.dat\">"));
            assertThat(content, containsString("entry3.png"));
            assertThat(content, containsString("<a href=\"/context/entry3.png\">"));
            assertThat(content, containsString("entry4.tar.gz"));
            assertThat(content, containsString("<a href=\"/context/entry4.tar.gz\">"));
            assertThat(content, containsString("dirFoo/"));
            assertThat(content, containsString("<a href=\"/context/dirFoo/\">"));
            assertThat(content, containsString("dirBar/"));
            assertThat(content, containsString("<a href=\"/context/dirBar/\">"));
            assertThat(content, containsString("dirCid/"));
            assertThat(content, containsString("<a href=\"/context/dirCid/\">"));
            assertThat(content, containsString("dirZed/"));
            assertThat(content, containsString("<a href=\"/context/dirZed/\">"));

            int count;

            // how many dirSame links do we have?
            count = content.split(Pattern.quote("<a href=\"/context/dirSame/\">"), -1).length - 1;
            assertThat(count, is(1));

            // how many similar.txt do we have?
            count = content.split(Pattern.quote("<a href=\"/context/similar.txt\">"), -1).length - 1;
            assertThat(count, is(1));
        }
    }

    /**
     * A regression on Windows allowed the directory listing show
     * the fully qualified paths within the directory listing.
     * This test ensures that this behavior will not arise again.
     */
    @Test
    public void testListingFilenamesOnly(WorkDir workDir) throws Exception
    {
        Path docRoot = workDir.getEmptyPathDir();
        /* create some content in the docroot */
        FS.ensureDirExists(docRoot);
        Path one = docRoot.resolve("one");
        FS.ensureDirExists(one);
        Path deep = one.resolve("deep");
        FS.ensureDirExists(deep);
        FS.touch(deep.resolve("foo"));
        FS.ensureDirExists(docRoot.resolve("two"));
        FS.ensureDirExists(docRoot.resolve("three"));

        try (ResourceFactory.Closeable resourceFactory = ResourceFactory.closeable())
        {
            Resource resourceBase = resourceFactory.newResource(docRoot);
            Resource resource = resourceBase.resolve("one/deep/");

            String content = ResourceListing.getAsXHTML(resource, "/context/", false, null);
            assertDoesNotThrow(() -> XHTMLValidator.validate(content));

            assertThat(content, containsString("/foo"));

            String resBasePath = docRoot.toAbsolutePath().toString();
            assertThat(content, not(containsString(resBasePath)));
        }
    }

    @Test
    public void testListingProperUrlEncoding(WorkDir workDir) throws Exception
    {
        /* create some content in the docroot */
        Path docRoot = workDir.getEmptyPathDir();
        Path wackyDir = docRoot.resolve("dir;"); // this should not be double-encoded.
        FS.ensureDirExists(wackyDir);

        FS.ensureDirExists(wackyDir.resolve("four"));
        FS.ensureDirExists(wackyDir.resolve("five"));
        FS.ensureDirExists(wackyDir.resolve("six"));

        /* At this point we have the following
         * testListingProperUrlEncoding/
         * `-- docroot
         *     `-- dir;
         *         |-- five
         *         |-- four
         *         `-- six
         */

        try (ResourceFactory.Closeable resourceFactory = ResourceFactory.closeable())
        {
            Resource resourceBase = resourceFactory.newResource(docRoot);

            // Resolve directory
            Resource resource = resourceBase.resolve("dir%3B");

            // Context
            String content = ResourceListing.getAsXHTML(resource, "/context/dir%3B/", false, null);
            assertDoesNotThrow(() -> XHTMLValidator.validate(content));

            // Should not see double-encoded ";"
            // First encoding: ";" -> "%3B"
            // Second encoding: "%3B" -> "%253B" (BAD!)
            assertThat(content, not(containsString("%253B")));

            assertThat(content, containsString("/dir%3B/"));
            assertThat(content, containsString("/dir%3B/four/"));
            assertThat(content, containsString("/dir%3B/five/"));
            assertThat(content, containsString("/dir%3B/six/"));
        }
    }

    @Test
    public void testListingWithQuestionMarks(WorkDir workDir) throws Exception
    {
        Path docRoot = workDir.getEmptyPathDir();
        /* create some content in the docroot */
        FS.ensureDirExists(docRoot.resolve("one"));
        FS.ensureDirExists(docRoot.resolve("two"));
        FS.ensureDirExists(docRoot.resolve("three"));

        // Creating dir 'f??r' (Might not work in Windows)
        assumeMkDirSupported(docRoot, "f??r");

        try (ResourceFactory.Closeable resourceFactory = ResourceFactory.closeable())
        {
            Resource resource = resourceFactory.newResource(docRoot);

            String content = ResourceListing.getAsXHTML(resource, "/context/", false, null);
            assertDoesNotThrow(() -> XHTMLValidator.validate(content));

            assertThat(content, containsString("f??r"));
        }
    }

    @Test
    public void testListingEncoding(WorkDir workDir) throws Exception
    {
        Path docRoot = workDir.getEmptyPathDir();
        /* create some content in the docroot */
        Path one = docRoot.resolve("one");
        FS.ensureDirExists(one);

        // example of content on disk that could cause problems when taken to the HTML space.
        Path alert = one.resolve("onmouseclick='alert(oops)'");
        FS.touch(alert);

        try (ResourceFactory.Closeable resourceFactory = ResourceFactory.closeable())
        {
            Resource resourceBase = resourceFactory.newResource(docRoot);
            Resource resource = resourceBase.resolve("one");

            String content = ResourceListing.getAsXHTML(resource, "/context/one", false, null);
            assertDoesNotThrow(() -> XHTMLValidator.validate(content));

            // Entry should be properly encoded
            assertThat(content, containsString("<a href=\"/context/one/onmouseclick=%27alert(oops)%27\">"));
        }
    }

    /**
     * Attempt to create the directory, skip testcase if not supported on OS.
     */
    private static Path assumeMkDirSupported(Path path, String subpath)
    {
        Path ret = null;

        try
        {
            ret = path.resolve(subpath);

            if (Files.exists(ret))
                return ret;

            Files.createDirectories(ret);
        }
        catch (InvalidPathException | IOException ignore)
        {
            // ignore
        }

        assumeTrue(ret != null, "Directory creation not supported on OS: " + path + File.separator + subpath);
        assumeTrue(Files.exists(ret), "Directory creation not supported on OS: " + ret);

        return ret;
    }
}
