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

package ab.squirrel.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IO Utilities.
 * Provides stream handling utilities in
 * singleton Threadpool implementation accessed by static members.
 */
public class IO
{
    private static final Logger LOG = LoggerFactory.getLogger(IO.class);

    public static final String
        CRLF = "\r\n";

    public static final byte[]
        CRLF_BYTES = {(byte)'\r', (byte)'\n'};

    public static final int bufferSize = 64 * 1024;

    /**
     * Copy Stream in to Stream out until EOF or exception.
     *
     * @param in the input stream to read from (until EOF)
     * @param out the output stream to write to
     * @throws IOException if unable to copy streams
     */
    public static void copy(InputStream in, OutputStream out)
        throws IOException
    {
        copy(in, out, -1);
    }

    /**
     * Copy Reader to Writer out until EOF or exception.
     *
     * @param in the read to read from (until EOF)
     * @param out the writer to write to
     * @throws IOException if unable to copy the streams
     */
    public static void copy(Reader in, Writer out)
        throws IOException
    {
        copy(in, out, -1);
    }

    /**
     * Copy Stream in to Stream for byteCount bytes or until EOF or exception.
     *
     * @param in the stream to read from
     * @param out the stream to write to
     * @param byteCount the number of bytes to copy
     * @throws IOException if unable to copy the streams
     */
    public static void copy(InputStream in,
                            OutputStream out,
                            long byteCount)
        throws IOException
    {
        byte[] buffer = new byte[bufferSize];
        int len;

        if (byteCount >= 0)
        {
            while (byteCount > 0)
            {
                int max = byteCount < bufferSize ? (int)byteCount : bufferSize;
                len = in.read(buffer, 0, max);

                if (len == -1)
                    break;

                byteCount -= len;
                out.write(buffer, 0, len);
            }
        }
        else
        {
            while (true)
            {
                len = in.read(buffer, 0, bufferSize);
                if (len < 0)
                    break;
                out.write(buffer, 0, len);
            }
        }
    }

    /**
     * Copy Reader to Writer for byteCount bytes or until EOF or exception.
     *
     * @param in the Reader to read from
     * @param out the Writer to write to
     * @param byteCount the number of bytes to copy
     * @throws IOException if unable to copy streams
     */
    public static void copy(Reader in,
                            Writer out,
                            long byteCount)
        throws IOException
    {
        char[] buffer = new char[bufferSize];
        int len;

        if (byteCount >= 0)
        {
            while (byteCount > 0)
            {
                if (byteCount < bufferSize)
                    len = in.read(buffer, 0, (int)byteCount);
                else
                    len = in.read(buffer, 0, bufferSize);

                if (len == -1)
                    break;

                byteCount -= len;
                out.write(buffer, 0, len);
            }
        }
        else if (out instanceof PrintWriter pout)
        {
            while (!pout.checkError())
            {
                len = in.read(buffer, 0, bufferSize);
                if (len == -1)
                    break;
                out.write(buffer, 0, len);
            }
        }
        else
        {
            while (true)
            {
                len = in.read(buffer, 0, bufferSize);
                if (len == -1)
                    break;
                out.write(buffer, 0, len);
            }
        }
    }

    /**
     * Copy files or directories
     *
     * @param from the file to copy
     * @param to the destination to copy to
     * @throws IOException if unable to copy
     */
    public static void copy(File from, File to) throws IOException
    {
        if (from.isDirectory())
            copyDir(from, to);
        else
            copyFile(from, to);
    }

    public static void copyDir(File from, File to) throws IOException
    {
        if (to.exists())
        {
            if (!to.isDirectory())
                throw new IllegalArgumentException(to.toString());
        }
        else
            to.mkdirs();

        File[] files = from.listFiles();
        if (files != null)
        {
            for (File file : files)
            {
                String name = file.getName();
                if (".".equals(name) || "..".equals(name))
                    continue;
                copy(file, new File(to, name));
            }
        }
    }

    /**
     * Copy the contents of a source directory to destination directory.
     *
     * <p>
     *     This version does not use the standard {@link Files#copy(Path, Path, CopyOption...)}
     *     technique to copy files, as that technique might incur a "foreign target" behavior
     *     when the {@link java.nio.file.FileSystem} types of the srcDir and destDir are
     *     different.
     *     Instead, this implementation uses the {@link #copyFile(Path, Path)} method instead.
     * </p>
     *
     * @param srcDir the source directory
     * @param destDir the destination directory
     * @throws IOException if unable to copy the file
     */
    public static void copyDir(Path srcDir, Path destDir) throws IOException
    {
        if (!Files.isDirectory(Objects.requireNonNull(srcDir)))
            throw new IllegalArgumentException("Source is not a directory: " + srcDir);
        Objects.requireNonNull(destDir);
        if (Files.exists(destDir) && !Files.isDirectory(destDir))
            throw new IllegalArgumentException("Destination is not a directory: " + destDir);
        else if (!Files.exists(destDir))
            Files.createDirectory(destDir); // only attempt top create 1 level of directory (parent must exist)

        try (Stream<Path> sourceStream = Files.walk(srcDir))
        {
            Iterator<Path> iterFiles = sourceStream
                .filter(Files::isRegularFile)
                .iterator();
            while (iterFiles.hasNext())
            {
                Path sourceFile = iterFiles.next();
                Path relative = srcDir.relativize(sourceFile);
                Path destFile = resolvePath(destDir, relative);
                if (!Files.exists(destFile.getParent()))
                    Files.createDirectories(destFile.getParent());
                copyFile(sourceFile, destFile);
            }
        }
    }

    /**
     * Copy the contents of a source directory to destination directory.
     *
     * <p>
     *     Copy the contents of srcDir to the destDir using
     *     {@link Files#copy(Path, Path, CopyOption...)} to
     *     copy individual files.
     * </p>
     *
     * @param srcDir the source directory
     * @param destDir the destination directory (must exist)
     * @param copyOptions the options to use on the {@link Files#copy(Path, Path, CopyOption...)} commands.
     * @throws IOException if unable to copy the file
     * @deprecated use {@link #copyDir(Path, Path)} instead to avoid foreign target behavior across FileSystems.
     */
    @Deprecated(since = "12.0.8", forRemoval = true)
    public static void copyDir(Path srcDir, Path destDir, CopyOption... copyOptions) throws IOException
    {
        if (!Files.isDirectory(Objects.requireNonNull(srcDir)))
            throw new IllegalArgumentException("Source is not a directory: " + srcDir);
        if (!Files.isDirectory(Objects.requireNonNull(destDir)))
            throw new IllegalArgumentException("Dest is not a directory: " + destDir);

        try (Stream<Path> sourceStream = Files.walk(srcDir))
        {
            Iterator<Path> iterFiles = sourceStream
                .filter(Files::isRegularFile)
                .iterator();
            while (iterFiles.hasNext())
            {
                Path sourceFile = iterFiles.next();
                Path relative = srcDir.relativize(sourceFile);
                Path destFile = resolvePath(destDir, relative);
                if (!Files.exists(destFile.getParent()))
                    Files.createDirectories(destFile.getParent());
                Files.copy(sourceFile, destFile, copyOptions);
            }
        }
    }

    /**
     * Perform a resolve of a {@code basePath} {@link Path} against
     * a {@code relative} {@link Path} in a way that ignores
     * {@link java.nio.file.FileSystem} differences between
     * the two {@link Path} parameters.
     *
     * <p>
     *     This implementation is intended to be a replacement for
     *     {@link Path#resolve(Path)} in cases where the the
     *     {@link java.nio.file.FileSystem} might be different,
     *     avoiding a {@link java.nio.file.ProviderMismatchException}
     *     from occurring.
     * </p>
     *
     * @param basePath the base Path
     * @param relative the relative Path to resolve against base Path
     * @return the new Path object relative to the base Path
     */
    public static Path resolvePath(Path basePath, Path relative)
    {
        if (relative.isAbsolute())
            throw new IllegalArgumentException("Relative path cannot be absolute");

        if (basePath.getFileSystem().equals(relative.getFileSystem()))
        {
            return basePath.resolve(relative);
        }
        else
        {
            for (Path segment : relative)
                basePath = basePath.resolve(segment.toString());
            return basePath;
        }
    }

    /**
     * Ensure that the given path exists, and is a directory.
     *
     * <p>
     *     Uses {@link Files#createDirectories(Path, FileAttribute[])} when
     *     the provided path needs to be created as directories.
     * </p>
     *
     * @param dir the directory to check and/or create.
     * @throws IOException if the {@code dir} exists, but isn't a directory, or if unable to create the directory.
     */
    public static void ensureDirExists(Path dir) throws IOException
    {
        if (Files.exists(dir))
        {
            if (!Files.isDirectory(dir))
            {
                throw new IOException("Conflict, unable to create directory where file exists: " + dir);
            }
            return;
        }
        Files.createDirectories(dir);
    }

    /**
     * Copy the contents of a source file to destination file.
     *
     * <p>
     *     Copy the contents of {@code srcFile} to the {@code destFile} using
     *     {@link Files#copy(Path, OutputStream)}.
     *     The {@code destFile} is opened with the {@link OpenOption} of
     *     {@link StandardOpenOption#CREATE},{@link StandardOpenOption#WRITE},{@link StandardOpenOption#TRUNCATE_EXISTING}.
     * </p>
     *
     * <p>
     *     Unlike {@link Files#copy(Path, Path, CopyOption...)}, this implementation will
     *     not perform a "foreign target" behavior (a special mode that kicks in
     *     when the {@code srcFile} and {@code destFile} are on different {@link java.nio.file.FileSystem}s)
     *     which will attempt to delete the destination file before creating a new
     *     file and then copying the contents over.
     * </p>
     * <p>
     *     In this implementation if the file exists, it will just be opened
     *     and written to from the start of the file.
     * </p>
     *
     * @param srcFile the source file (must exist)
     * @param destFile the destination file
     * @throws IOException if unable to copy the file
     */
    public static void copyFile(Path srcFile, Path destFile) throws IOException
    {
        if (!Files.isRegularFile(Objects.requireNonNull(srcFile)))
            throw new IllegalArgumentException("Source is not a file: " + srcFile);
        Objects.requireNonNull(destFile);

        try (OutputStream out = Files.newOutputStream(destFile,
            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))
        {
            Files.copy(srcFile, out);
        }
    }

    /**
     * Copy the contents of a source file to destination file.
     *
     * <p>
     *     Copy the contents of {@code from} {@link File} to the {@code to} {@link File} using
     *     standard {@link InputStream} / {@link OutputStream} behaviors.
     * </p>
     *
     * @param from the source file (must exist)
     * @param to the destination file
     * @throws IOException if unable to copy the file
     */
    public static void copyFile(File from, File to) throws IOException
    {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to))
        {
            copy(in, out);
        }
    }

    public static IOException rethrow(Throwable cause)
    {
        if (cause instanceof ExecutionException xx)
            cause = xx.getCause();
        if (cause instanceof CompletionException xx)
            cause = xx.getCause();
        if (cause instanceof IOException)
            return (IOException)cause;
        if (cause instanceof Error)
            throw (Error)cause;
        if (cause instanceof RuntimeException)
            throw (RuntimeException)cause;
        if (cause instanceof InterruptedException)
            return (InterruptedIOException)new InterruptedIOException().initCause(cause);
        return new IOException(cause);
    }

    /**
     * Read Path to string.
     *
     * @param path the path to read from (until EOF)
     * @param charset the charset to read with
     * @return the String parsed from path (default Charset)
     * @throws IOException if unable to read the path (or handle the charset)
     */
    public static String toString(Path path, Charset charset)
        throws IOException
    {
        byte[] buf = Files.readAllBytes(path);
        return new String(buf, charset);
    }

    /**
     * Read input stream to string.
     *
     * @param in the stream to read from (until EOF)
     * @return the String parsed from stream (default Charset)
     * @throws IOException if unable to read the stream (or handle the charset)
     */
    public static String toString(InputStream in)
        throws IOException
    {
        return toString(in, (Charset)null);
    }

    /**
     * Read input stream to string.
     *
     * @param in the stream to read from (until EOF)
     * @param encoding the encoding to use (can be null to use default Charset)
     * @return the String parsed from the stream
     * @throws IOException if unable to read the stream (or handle the charset)
     */
    public static String toString(InputStream in, String encoding)
        throws IOException
    {
        return toString(in, encoding == null ? null : Charset.forName(encoding));
    }

    /**
     * Read input stream to string.
     *
     * @param in the stream to read from (until EOF)
     * @param encoding the Charset to use (can be null to use default Charset)
     * @return the String parsed from the stream
     * @throws IOException if unable to read the stream (or handle the charset)
     */
    public static String toString(InputStream in, Charset encoding)
        throws IOException
    {
        StringWriter writer = new StringWriter();
        InputStreamReader reader = encoding == null ? new InputStreamReader(in) : new InputStreamReader(in, encoding);

        copy(reader, writer);
        return writer.toString();
    }

    /**
     * Read input stream to string.
     *
     * @param in the reader to read from (until EOF)
     * @return the String parsed from the reader
     * @throws IOException if unable to read the stream (or handle the charset)
     */
    public static String toString(Reader in)
        throws IOException
    {
        StringWriter writer = new StringWriter();
        copy(in, writer);
        return writer.toString();
    }

    /**
     * Delete File.
     * This delete will recursively delete directories - BE CAREFUL
     *
     * @param file The file (or directory) to be deleted.
     * @return true if file was deleted, or directory referenced was deleted.
     * false if file doesn't exist, or was null.
     */
    public static boolean delete(File file)
    {
        if (file == null)
            return false;
        if (!file.exists())
            return false;
        if (file.isDirectory())
        {
            File[] files = file.listFiles();
            for (int i = 0; files != null && i < files.length; i++)
            {
                delete(files[i]);
            }
        }
        return file.delete();
    }

    /**
     * Delete the path, recursively.
     *
     * @param path the path to delete
     * @return true if able to delete the path, false if unable to delete the path.
     */
    public static boolean delete(Path path)
    {
        if (path == null)
            return false;
        try
        {
            Files.walkFileTree(path, new SimpleFileVisitor<>()
            {
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
                {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
                {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        }
        catch (IOException e)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Unable to delete path: {}", path, e);
            return false;
        }
    }

    /**
     * Test if directory is empty.
     *
     * @param dir the directory
     * @return true if directory is null, doesn't exist, or has no content.
     * false if not a directory, or has contents
     */
    public static boolean isEmptyDir(File dir)
    {
        if (dir == null)
            return true;
        if (!dir.exists())
            return true;
        if (!dir.isDirectory())
            return false;
        String[] list = dir.list();
        if (list == null)
            return true;
        return list.length <= 0;
    }

    /**
     * Closes an arbitrary closable, and logs exceptions at ignore level
     *
     * @param closeable the closeable to close
     */
    public static void close(AutoCloseable closeable)
    {
        try
        {
            if (closeable != null)
                closeable.close();
        }
        catch (Exception x)
        {
            LOG.trace("IGNORED", x);
        }
    }

    /**
     * Closes an arbitrary closable, and logs exceptions at ignore level
     *
     * @param closeable the closeable to close
     */
    public static void close(Closeable closeable)
    {
        close((AutoCloseable)closeable);
    }

    /**
     * closes an input stream, and logs exceptions
     *
     * @param is the input stream to close
     */
    public static void close(InputStream is)
    {
        close((AutoCloseable)is);
    }

    /**
     * closes an output stream, and logs exceptions
     *
     * @param os the output stream to close
     */
    public static void close(OutputStream os)
    {
        close((AutoCloseable)os);
    }

    /**
     * closes a reader, and logs exceptions
     *
     * @param reader the reader to close
     */
    public static void close(Reader reader)
    {
        close((AutoCloseable)reader);
    }

    /**
     * closes a writer, and logs exceptions
     *
     * @param writer the writer to close
     */
    public static void close(Writer writer)
    {
        close((AutoCloseable)writer);
    }

    public static byte[] readBytes(InputStream in)
        throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        copy(in, bout);
        return bout.toByteArray();
    }

    /**
     * A gathering write utility wrapper.
     * <p>
     * This method wraps a gather write with a loop that handles the limitations of some operating systems that have a
     * limit on the number of buffers written. The method loops on the write until either all the content is written or
     * no progress is made.
     *
     * @param out The GatheringByteChannel to write to
     * @param buffers The buffers to write
     * @param offset The offset into the buffers array
     * @param length The length in buffers to write
     * @return The total bytes written
     * @throws IOException if unable write to the GatheringByteChannel
     */
    public static long write(GatheringByteChannel out, ByteBuffer[] buffers, int offset, int length) throws IOException
    {
        long total = 0;
        write:
        while (length > 0)
        {
            // Write as much as we can
            long wrote = out.write(buffers, offset, length);

            // If we can't write any more, give up
            if (wrote == 0)
                break;

            // count the total
            total += wrote;

            // Look for unwritten content
            for (int i = offset; i < buffers.length; i++)
            {
                if (buffers[i].hasRemaining())
                {
                    // loop with new offset and length;
                    length = length - (i - offset);
                    offset = i;
                    continue write;
                }
            }
            length = 0;
        }

        return total;
    }

    /**
     * <p>Convert an object to a {@link File} if possible.</p>
     * @param fileObject A File, String, Path or null to be converted into a File
     * @return A File representation of the passed argument or null.
     */
    public static File asFile(Object fileObject)
    {
        if (fileObject == null)
            return null;
        if (fileObject instanceof File)
            return (File)fileObject;
        if (fileObject instanceof String)
            return new File((String)fileObject);
        if (fileObject instanceof Path)
            return ((Path)fileObject).toFile();

        return null;
    }

    private IO()
    {
        // prevent instantiation
    }
}









