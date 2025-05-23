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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * <p>Utility class to maintain a set of inclusions and exclusions.</p>
 * <p>Maintains a set of included and excluded elements.  The method {@link #test(Object)}
 * will return true IFF the passed object is not in the excluded set AND ( either the
 * included set is empty OR the object is in the included set)</p>
 * <p>The type of the underlying {@link Set} used may be passed into the
 * constructor, so special sets like Servlet PathMap may be used.</p>
 *
 * @param <T> The type of element of the set (often a pattern)
 * @param <P> The type of the instance passed to the predicate
 */
public class IncludeExcludeSet<T, P> implements Predicate<P>
{
    private final Set<T> _includes;
    private final Predicate<P> _includePredicate;
    private final Set<T> _excludes;
    private final Predicate<P> _excludePredicate;

    private record SetContainsPredicate<T>(Set<T> set) implements Predicate<T>
    {
        @Override
        public boolean test(T item)
            {
                return set.contains(item);
            }

        @Override
        public String toString()
            {
                return "CONTAINS";
            }
    }

    /**
     * Default constructor over {@link HashSet}
     */
    public IncludeExcludeSet()
    {
        // noinspection unchecked
        this(HashSet.class);
    }

    /**
     * Construct an IncludeExclude.
     *
     * @param setClass The type of {@link Set} to using internally to hold patterns. Two instances will be created.
     * one for include patterns and one for exclude patters.  If the class is also a {@link Predicate},
     * then it is also used as the item test for the set, otherwise a {@link SetContainsPredicate} instance
     * is created.
     * @param <SET> The type of {@link Set} to use as the backing store
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <SET extends Set<T>> IncludeExcludeSet(Class<SET> setClass)
    {
        try
        {
            _includes = setClass.getDeclaredConstructor().newInstance();
            _excludes = setClass.getDeclaredConstructor().newInstance();

            if (_includes instanceof Predicate)
            {
                _includePredicate = (Predicate<P>)_includes;
            }
            else
            {
                _includePredicate = new SetContainsPredicate(_includes);
            }

            if (_excludes instanceof Predicate)
            {
                _excludePredicate = (Predicate<P>)_excludes;
            }
            else
            {
                _excludePredicate = new SetContainsPredicate(_excludes);
            }
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Construct an IncludeExclude
     *
     * @param includeSet the Set of items that represent the included space
     * @param includePredicate the Predicate for included item testing (null for simple {@link Set#contains(Object)} test)
     * @param excludeSet the Set of items that represent the excluded space
     * @param excludePredicate the Predicate for excluded item testing (null for simple {@link Set#contains(Object)} test)
     * @param <SET> The type of {@link Set} to use as the backing store
     */
    @SuppressWarnings("unused")
    public <SET extends Set<T>> IncludeExcludeSet(Set<T> includeSet, Predicate<P> includePredicate, Set<T> excludeSet, Predicate<P> excludePredicate)
    {
        Objects.requireNonNull(includeSet, "Include Set");
        Objects.requireNonNull(includePredicate, "Include Predicate");
        Objects.requireNonNull(excludeSet, "Exclude Set");
        Objects.requireNonNull(excludePredicate, "Exclude Predicate");

        _includes = includeSet;
        _includePredicate = includePredicate;
        _excludes = excludeSet;
        _excludePredicate = excludePredicate;
    }

    public IncludeExcludeSet<T, P> asImmutable()
    {
        return new IncludeExcludeSet<>(
            Collections.unmodifiableSet(_includes),
            _includePredicate,
            Collections.unmodifiableSet(_excludes),
            _excludePredicate);
    }

    public void include(T element)
    {
        _includes.add(element);
    }

    @SafeVarargs
    public final void include(T... element)
    {
        _includes.addAll(Arrays.asList(element));
    }

    public void exclude(T element)
    {
        _excludes.add(element);
    }

    @SafeVarargs
    public final void exclude(T... element)
    {
        _excludes.addAll(Arrays.asList(element));
    }

    /**
     * Test includes and excludes for match.
     *
     * <p>
     *     Excludes always win over includes.
     * </p>
     *
     * <p>
     *     Empty includes means all inputs are allowed.
     * </p>
     *
     * @param t the input argument
     * @return true if the input matches an include, and is not excluded.
     */
    @Override
    public boolean test(P t)
    {
        if (!_includes.isEmpty())
        {
            if (!_includePredicate.test(t))
            {
                // If we have defined includes, but none match then
                // return false immediately, no need to check excluded
                return false;
            }
        }

        if (_excludes.isEmpty())
            return true;
        return !_excludePredicate.test(t);
    }

    /**
     * Test Included and not Excluded
     *
     * @param item The item to test
     * @return {@link Boolean#TRUE} if item is included, {@link Boolean#FALSE} if item is excluded, or null if neither
     */
    public Boolean isIncludedAndNotExcluded(P item)
    {
        if (!_excludes.isEmpty() && _excludePredicate.test(item))
            return Boolean.FALSE;
        if (!_includes.isEmpty() && _includePredicate.test(item))
            return Boolean.TRUE;

        return null;
    }

    public boolean hasIncludes()
    {
        return !_includes.isEmpty();
    }

    public boolean hasExcludes()
    {
        return !_excludes.isEmpty();
    }

    public int size()
    {
        return _includes.size() + _excludes.size();
    }

    public Set<T> getIncluded()
    {
        return _includes;
    }

    public Set<T> getExcluded()
    {
        return _excludes;
    }

    public void clear()
    {
        _includes.clear();
        _excludes.clear();
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x{i=%s,ip=%s,e=%s,ep=%s}", this.getClass().getSimpleName(), hashCode(),
            _includes,
            _includePredicate == _includes ? "SELF" : _includePredicate,
            _excludes,
            _excludePredicate == _excludes ? "SELF" : _excludePredicate);
    }

    public boolean isEmpty()
    {
        return _includes.isEmpty() && _excludes.isEmpty();
    }
}
