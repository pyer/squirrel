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

/**
 * Collection of helpful static methods for working with {@link Resource} objects.
 */
public final class Resources
{
    /**
     * True if the resource exists.
     *
     * @param resource the resource to test
     * @return true if resource is non-null and exists
     * @see Resource#exists()
     */
    public static boolean exists(Resource resource)
    {
        return resource != null && resource.exists();
    }

    /**
     * True if the resource has been created by {@link ResourceFactory#combine(java.util.List) combining} other resources.
     *
     * @param resource the resource to test
     * @return true if resource is a combination of other resources
     */
    public static boolean isCombined(Resource resource)
    {
        return resource instanceof CombinedResource;
    }

    /**
     * True if the resource is missing.
     *
     * @param resource the resource to test
     * @return true if resource is null or doesn't exist
     * @see Resource#exists()
     */
    public static boolean missing(Resource resource)
    {
        return resource == null || !resource.exists();
    }

    /**
     * True if resource is a valid directory.
     *
     * @param resource the resource to test
     * @return true if resource is non-null, exists, and is a directory
     * @see Resource#exists()
     * @see Resource#isDirectory()
     */
    public static boolean isDirectory(Resource resource)
    {
        return resource != null && resource.isDirectory();
    }

    /**
     * True if resource is readable.
     *
     * @param resource the resource to test
     * @return true if resource is non-null, exists, and is readable
     * @see Resource#exists()
     * @see Resource#isReadable()
     */
    public static boolean isReadable(Resource resource)
    {
        return resource != null && resource.isReadable();
    }

    /**
     * True if resource is a valid directory that can be read from.
     *
     * @param resource the resource to test
     * @return true if resource is non-null, exists, and is a directory
     * @see Resource#exists()
     * @see Resource#isDirectory()
     */
    public static boolean isReadableDirectory(Resource resource)
    {
        return resource != null && resource.isDirectory() && resource.isReadable();
    }

    /**
     * True if resource exists, is not a directory, is readable.
     *
     * @param resource the resource to test
     * @return true if resource exists, is not a directory, is
     */
    public static boolean isReadableFile(Resource resource)
    {
        return resource != null && !resource.isDirectory() && resource.isReadable();
    }
}
