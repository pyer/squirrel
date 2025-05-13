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

package ab.squirrel.http.pathmap;

import java.util.Objects;

/**
 * A path specification is a URI path template that can be matched against.
 * <p>
 * Implementors <i>must</i> override {@link Object#equals(Object)} and {@link Object#hashCode()}.
 */
public interface PathSpec extends Comparable<PathSpec>
{
    static PathSpec from(String pathSpecString)
    {
        Objects.requireNonNull(pathSpecString, "null PathSpec not supported");

        if (pathSpecString.length() == 0)
            return new ServletPathSpec("");

        return pathSpecString.charAt(0) == '^' ? new RegexPathSpec(pathSpecString) : new ServletPathSpec(pathSpecString);
    }

    /**
     * The length of the spec.
     *
     * @return the length of the spec.
     */
    int getSpecLength();

    /**
     * The spec group.
     *
     * @return the spec group.
     */
    PathSpecGroup getGroup();

    /**
     * Get the number of path elements that this path spec declares.
     * <p>
     * This is used to determine longest match logic.
     *
     * @return the depth of the path segments that this spec declares
     */
    int getPathDepth();

    /**
     * The as-provided path spec.
     *
     * @return the as-provided path spec
     */
    String getDeclaration();

    /**
     * A simple prefix match for the pathspec or null
     *
     * @return A simple prefix match for the pathspec or null
     */
    String getPrefix();

    /**
     * A simple suffix match for the pathspec or null
     *
     * @return A simple suffix match for the pathspec or null
     */
    String getSuffix();

    /**
     * Test to see if the provided path matches this path spec.
     * This can be more efficient that {@link #matched(String)} if the details of the match are not required.
     *
     * @param path the path to test
     * @return true if the path matches this path spec, false otherwise
     * @see #matched(String) 
     */
    default boolean matches(String path)
    {
        return matched(path) != null;
    }

    /**
     * Get the complete matched details of the provided path.
     *
     * @param path the path to test
     * @return the matched details, if a match was possible, or null if not able to be matched.
     * @see #matches(String) 
     */
    MatchedPath matched(String path);
}
