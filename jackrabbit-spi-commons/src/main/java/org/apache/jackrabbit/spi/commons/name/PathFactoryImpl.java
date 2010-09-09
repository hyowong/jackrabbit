/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.spi.commons.name;

import org.apache.jackrabbit.spi.Path;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.PathFactory;
import org.apache.jackrabbit.spi.NameFactory;

import javax.jcr.RepositoryException;
import javax.jcr.PathNotFoundException;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * <code>PathFactoryImpl</code>...
 */
public class PathFactoryImpl implements PathFactory {

    private static PathFactory FACTORY = new PathFactoryImpl();

    private static final String CURRENT_LITERAL = ".";
    private static final String PARENT_LITERAL = "..";

    private static final NameFactory NAME_FACTORY = NameFactoryImpl.getInstance();
    private final static Name CURRENT_NAME = NAME_FACTORY.create(Name.NS_DEFAULT_URI, CURRENT_LITERAL);
    private final static Name PARENT_NAME = NAME_FACTORY.create(Name.NS_DEFAULT_URI, PARENT_LITERAL);
    private final static Name ROOT_NAME = NAME_FACTORY.create(Name.NS_DEFAULT_URI, "");

    /**
     * the root path
     */
    private static final Path ROOT =
        new PathImpl(new Path.Element[] { RootElement.INSTANCE }, true);

    private static final Path CURRENT_PATH =
        new PathImpl(new Path.Element[] { CurrentElement.INSTANCE }, true);

    private static final Path PARENT_PATH =
        new PathImpl(new Path.Element[] { ParentElement.INSTANCE }, true);

    private PathFactoryImpl() {}

    public static PathFactory getInstance() {
        return FACTORY;
    }

    //--------------------------------------------------------< PathFactory >---
    /**
     * @see PathFactory#create(Path, Path, boolean)
     */
    public Path create(Path parent, Path relPath, boolean normalize) throws IllegalArgumentException, RepositoryException {
        if (relPath.isAbsolute()) {
            throw new IllegalArgumentException(
                    "relPath is not a relative path: " + relPath);
        }
        List<Path.Element> l = new ArrayList<Path.Element>();
        l.addAll(Arrays.asList(parent.getElements()));
        l.addAll(Arrays.asList(relPath.getElements()));

        Builder pb;
        try {
            pb = new Builder(l);
        } catch (IllegalArgumentException iae) {
             throw new RepositoryException(iae.getMessage());
        }
        Path path = pb.getPath();
        if (normalize) {
            return path.getNormalizedPath();
        } else {
            return path;
        }
    }

    /**
     * @see PathFactory#create(Path, Name, boolean)
     */
    public Path create(Path parent, Name name, boolean normalize) throws RepositoryException {
        List<Path.Element> elements = new ArrayList<Path.Element>();
        elements.addAll(Arrays.asList(parent.getElements()));
        elements.add(createElement(name));

        Builder pb;
        try {
            pb = new Builder(elements);
        } catch (IllegalArgumentException iae) {
             throw new RepositoryException(iae.getMessage());
        }
        Path path = pb.getPath();
        if (normalize) {
            return path.getNormalizedPath();
        } else {
            return path;
        }
    }

    /**
     * @see PathFactory#create(Path, Name, int, boolean)
     */
    public Path create(Path parent, Name name, int index, boolean normalize) throws IllegalArgumentException, RepositoryException {
        List<Path.Element> elements = new ArrayList<Path.Element>();
        elements.addAll(Arrays.asList(parent.getElements()));
        elements.add(createElement(name, index));

        Builder pb;
        try {
            pb = new Builder(elements);
        } catch (IllegalArgumentException iae) {
             throw new RepositoryException(iae.getMessage());
        }
        Path path = pb.getPath();
        if (normalize) {
            return path.getNormalizedPath();
        } else {
            return path;
        }
    }

    /**
     * @see PathFactory#create(Name)
     */
    public Path create(Name name) throws IllegalArgumentException {
        Path.Element elem = createElement(name);
        return new Builder(new Path.Element[]{elem}).getPath();
    }

    /**
     * @see PathFactory#create(Name, int)
     */
    public Path create(Name name, int index) throws IllegalArgumentException {
        if (index < Path.INDEX_UNDEFINED) {
            throw new IllegalArgumentException(
                    "Index must not be negative: " + name + "[" + index + "]");
        }
        Path.Element elem = createElement(name, index);
        return new Builder(new Path.Element[]{elem}).getPath();
    }

    /**
     * @see PathFactory#create(org.apache.jackrabbit.spi.Path.Element[])
     */
    public Path create(Path.Element[] elements) throws IllegalArgumentException {
        return new Builder(elements).getPath();
    }

    /**
     * @see PathFactory#create(String)
     */
    public Path create(String pathString) throws IllegalArgumentException {
        if (pathString == null || "".equals(pathString)) {
            throw new IllegalArgumentException("No Path literal specified");
        }
        // split into path elements
        int lastPos = 0;
        int pos = pathString.indexOf(Path.DELIMITER);
        ArrayList<Path.Element> list = new ArrayList<Path.Element>();
        while (lastPos >= 0) {
            Path.Element elem;
            if (pos >= 0) {
                elem = createElementFromString(pathString.substring(lastPos, pos));
                lastPos = pos + 1;
                pos = pathString.indexOf(Path.DELIMITER, lastPos);
            } else {
                elem = createElementFromString(pathString.substring(lastPos));
                lastPos = -1;
            }
            list.add(elem);
        }
        return new Builder(list).getPath();
    }

    /**
     * @see PathFactory#createElement(Name)
     */
    public Path.Element createElement(Name name) throws IllegalArgumentException {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        } else if (name.equals(PARENT_NAME)) {
            return ParentElement.INSTANCE;
        } else if (name.equals(CURRENT_NAME)) {
            return CurrentElement.INSTANCE;
        } else if (name.equals(ROOT_NAME)) {
            return RootElement.INSTANCE;
        } else {
            return NameElement.create(name, Path.INDEX_UNDEFINED);
        }
    }

    /**
     * @see PathFactory#createElement(Name, int)
     */
    public Path.Element createElement(Name name, int index) throws IllegalArgumentException {
        if (index < Path.INDEX_UNDEFINED) {
            throw new IllegalArgumentException(
                    "The index may not be negative: " + name + "[" + index + "]");
        } else if (name == null) {
            throw new IllegalArgumentException("The name must not be null");
        } else if (name.equals(PARENT_NAME)
                || name.equals(CURRENT_NAME)
                || name.equals(ROOT_NAME)) {
            throw new IllegalArgumentException(
                    "Special path elements (root, '.' and '..') can not have an explicit index: "
                    + name + "[" + index + "]");
        } else {
            return NameElement.create(name, index);
        }
    }

    public Path.Element createElement(String identifier) throws IllegalArgumentException {
        if (identifier == null) {
            throw new IllegalArgumentException("The id must not be null.");
        } else {
            return new IdentifierElement(identifier);
        }
    }

    /**
     * Create an element from the element string
     */
    private Path.Element createElementFromString(String elementString) {
        if (elementString == null) {
            throw new IllegalArgumentException("null PathElement literal");
        }
        if (elementString.equals(ROOT_NAME.toString())) {
            return RootElement.INSTANCE;
        } else if (elementString.equals(CURRENT_LITERAL)) {
            return CurrentElement.INSTANCE;
        } else if (elementString.equals(PARENT_LITERAL)) {
            return ParentElement.INSTANCE;
        } else if (elementString.startsWith("[") && elementString.endsWith("]") && elementString.length() > 2) {
            return new IdentifierElement(elementString.substring(1, elementString.length()-1));
        }

        int pos = elementString.indexOf('[');
        if (pos == -1) {
            Name name = NAME_FACTORY.create(elementString);
            return NameElement.create(name, Path.INDEX_UNDEFINED);
        }
        Name name = NAME_FACTORY.create(elementString.substring(0, pos));
        int pos1 = elementString.indexOf(']');
        if (pos1 == -1) {
            throw new IllegalArgumentException("invalid PathElement literal: " + elementString + " (missing ']')");
        }
        try {
            int index = Integer.valueOf(elementString.substring(pos + 1, pos1));
            if (index < 1) {
                throw new IllegalArgumentException("invalid PathElement literal: " + elementString + " (index is 1-based)");
            }
            return NameElement.create(name, index);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid PathElement literal: " + elementString + " (" + e.getMessage() + ")");
        }
    }

    /**
     * @see PathFactory#getCurrentElement()
     */
    public Path.Element getCurrentElement() {
        return CurrentElement.INSTANCE;
    }

    /**
     * @see PathFactory#getParentElement()
     */
    public Path.Element getParentElement() {
        return ParentElement.INSTANCE;
    }

    /**
     * @see PathFactory#getRootElement()
     */
    public Path.Element getRootElement() {
        return RootElement.INSTANCE;
    }

    /**
     * @see PathFactory#getRootPath()
     */
    public Path getRootPath() {
        return ROOT;
    }

    //--------------------------------------------------------------------------
    private static final class PathImpl implements Path {

        /**
         * the elements of this path
         */
        private final Path.Element[] elements;

        /**
         * flag indicating if this path is normalized
         */
        private final boolean normalized;

        /**
         * flag indicating if this path is absolute
         */
        private final boolean absolute;

        /**
         * the cached hashcode of this path
         */
        private transient int hash = 0;

        /**
         * the cached 'toString' of this path
         */
        private transient String string;

        private PathImpl(Path.Element[] elements, boolean isNormalized) {
            if (elements == null || elements.length == 0) {
                throw new IllegalArgumentException("Empty paths are not allowed");
            }
            this.elements = elements;
            this.absolute = elements[0].denotesRoot() || elements[0].denotesIdentifier();
            this.normalized = isNormalized;
        }

        /**
         * @see Path#denotesRoot()
         */
        public boolean denotesRoot() {
            return absolute && elements.length == 1 && elements[0].denotesRoot();
        }

        /**
         * @see Path#denotesIdentifier()
         */
        public boolean denotesIdentifier() {
            return elements.length == 1 && elements[0].denotesIdentifier();
        }

        /**
         * @see Path#isAbsolute()
         */
        public boolean isAbsolute() {
            return absolute;
        }

        /**
         * @see Path#isCanonical()
         */
        public boolean isCanonical() {
            return absolute && normalized;
        }

        /**
         * @see Path#isNormalized()
         */
        public boolean isNormalized() {
            return normalized;
        }

        /**
         * @see Path#getNormalizedPath()
         */
        public Path getNormalizedPath() throws RepositoryException {
            if (isNormalized()) {
                return this;
            }
            if (denotesIdentifier()) {
                throw new RepositoryException(
                        "Identifier-based path cannot be normalized: " + this);
            }
            LinkedList<Path.Element> queue = new LinkedList<Path.Element>();
            for (Element elem : elements) {
                if (elem.denotesParent() && !queue.isEmpty() && !queue.getLast().denotesParent()) {
                    if (queue.getLast().denotesRoot()) {
                        throw new RepositoryException("Path cannot be normalized: " + this);
                    }
                    queue.removeLast();
                } else if (!elem.denotesCurrent()) {
                    queue.add(elem);
                }
            }
            if (queue.isEmpty()) {
                return CURRENT_PATH;
            }
            boolean isNormalized = true;
            return new PathImpl(queue.toArray(new Element[queue.size()]), isNormalized);
        }

        /**
         * @see Path#getCanonicalPath()
         */
        public Path getCanonicalPath() throws RepositoryException {
            if (isCanonical()) {
                return this;
            }
            if (!isAbsolute()) {
                throw new RepositoryException(
                        "Only an absolute path can be canonicalized: "  + this);
            }
            if (denotesIdentifier()) {
                throw new RepositoryException(
                        "Identifier-based path cannot be canonicalized: " + this);
            }
            return getNormalizedPath();
        }

        /**
         * @see Path#computeRelativePath(Path)
         */
        public Path computeRelativePath(Path other) throws RepositoryException {
            if (other == null) {
                throw new IllegalArgumentException("null argument");
            }

            // make sure both paths are absolute and not id-based
            if (!isAbsolute() || !other.isAbsolute()) {
                throw new RepositoryException(
                        "Cannot compute relative path from relative paths: "
                        + this + " vs. " + other);
            }
            if (denotesIdentifier() || other.denotesIdentifier()) {
                throw new RepositoryException(
                        "Cannot compute relative path from identifier-based paths: "
                        + this + " vs. " + other);
            }

            // make sure we're comparing canonical paths
            Path p0 = getCanonicalPath();
            Path p1 = other.getCanonicalPath();

            if (p0.equals(p1)) {
                // both paths are equal, the relative path is therefore '.'
                return CURRENT_PATH;
            }

            // determine length of common path fragment
            int lengthCommon = 0;
            Path.Element[] elems0 = p0.getElements();
            Path.Element[] elems1 = p1.getElements();
            for (int i = 0; i < elems0.length && i < elems1.length; i++) {
                if (!elems0[i].equals(elems1[i])) {
                    break;
                }
                lengthCommon++;
            }
            List<Path.Element> l = new ArrayList<Path.Element>();
            if (lengthCommon < elems0.length) {
                /**
                 * the common path fragment is an ancestor of this path;
                 * this has to be accounted for by prepending '..' elements
                 * to the relative path
                 */
                int tmp = elems0.length - lengthCommon;
                while (tmp-- > 0) {
                    l.add(0, ParentElement.INSTANCE);
                }
            }
            // add remainder of other path
            for (int i = lengthCommon; i < elems1.length; i++) {
                l.add(elems1[i]);
            }
            return new Builder(l).getPath();
        }

        /**
         * @see Path#getAncestor(int)
         */
        public Path getAncestor(int degree) throws IllegalArgumentException, PathNotFoundException, RepositoryException {
            if (degree < 0) {
                throw new IllegalArgumentException(
                        "degree must be >= 0: " + this);
            } else if (degree == 0) {
                return getNormalizedPath();
            }

            if (isAbsolute()) {
                Path.Element[] normElems = getNormalizedPath().getElements();
                int length = normElems.length - degree;
                if (length < 1) {
                    throw new PathNotFoundException(
                            "no ancestor at degree " + degree + ": " + this);
                }
                Path.Element[] ancestorElements = new Element[length];
                System.arraycopy(normElems, 0, ancestorElements, 0, length);
                return new PathImpl(ancestorElements, true);
            } else {
                Path.Element[] ancestorElements = new Element[elements.length + degree];
                System.arraycopy(elements, 0, ancestorElements, 0, elements.length);

                for (int i = elements.length; i < ancestorElements.length; i++) {
                    ancestorElements[i] = ParentElement.INSTANCE;
                }
                return new PathImpl(ancestorElements, false).getNormalizedPath();
            }
        }

        /**
         * @see Path#getAncestorCount()
         */
        public int getAncestorCount() {
            try {
                return (isAbsolute() && !denotesIdentifier()) ? getDepth() : -1;
            } catch (RepositoryException e) {
                // never gets here.
                return -1;
            }
        }

        /**
         * @see Path#getLength()
         */
        public int getLength() {
            return elements.length;
        }

        /**
         * @see Path#getDepth()
         */
        public int getDepth() throws RepositoryException {
            if (denotesIdentifier()) {
                throw new RepositoryException(
                        "Cannot determine depth of an identifier based path: " + this);
            }
            int depth = ROOT_DEPTH;
            for (Element element : elements) {
                if (element.denotesParent()) {
                    depth--;
                } else if (element.denotesName()) {
                    // don't count root/current element.
                    depth++;
                }
            }
            return depth;
        }

        /**
         * @see Path#isEquivalentTo(Path)
         */
        public boolean isEquivalentTo(Path other) throws RepositoryException {
            if (other == null) {
                throw new IllegalArgumentException("null argument");
            }
            if (isAbsolute() != other.isAbsolute()) {
                throw new IllegalArgumentException(
                        "Cannot compare a relative path with an absolute path: "
                        + this + " vs. " + other);
            }

            if (getDepth() != other.getDepth()) {
                return false;
            }

            Element[] elems0 = getNormalizedPath().getElements();
            Element[] elems1 = other.getNormalizedPath().getElements();

            if (elems0.length != elems1.length)
                return false;

            for (int k = 0; k < elems0.length; k++) {
                if (!elems0[k].equals(elems1[k]))
                    return false;
            }
            return true;
        }

        /**
         * @see Path#isAncestorOf(Path)
         */
        public boolean isAncestorOf(Path other) throws IllegalArgumentException, RepositoryException {
            if (other == null) {
                throw new IllegalArgumentException("null argument");
            }
            // make sure both paths are either absolute or relative
            if (isAbsolute() != other.isAbsolute()) {
                throw new IllegalArgumentException(
                        "Cannot compare a relative path with an absolute path: "
                        + this + " vs. " + other);
            }

            int delta = other.getDepth() - getDepth();
            if (delta <= 0)
                return false;

            return isEquivalentTo(other.getAncestor(delta));
        }

        /**
         * @see Path#isDescendantOf(Path)
         */
        public boolean isDescendantOf(Path other) throws IllegalArgumentException, RepositoryException {
            if (other == null) {
                throw new IllegalArgumentException("Null argument");
            }
            return other.isAncestorOf(this);
        }

        /**
         * @see Path#subPath(int, int)
         */
        public Path subPath(int from, int to) throws IllegalArgumentException, RepositoryException {
            if (from < 0 || to > elements.length || from >= to) {
                throw new IllegalArgumentException();
            }
            if (!isNormalized()) {
                throw new RepositoryException(
                        "Cannot extract sub-Path from a non-normalized Path: "
                        + this);
            }
            Path.Element[] dest = new Path.Element[to-from];
            System.arraycopy(elements, from, dest, 0, dest.length);
            Builder pb = new Builder(dest);
            return pb.getPath();
        }

        /**
         * @see Path#getNameElement()
         */
        public Element getNameElement() {
            return elements[elements.length - 1];
        }

        /**
         * @see Path#getString()
         */
        public String getString() {
            return toString();
        }

        /**
         * @see Path#getElements()
         */
        public Element[] getElements() {
            return elements;
        }

        //---------------------------------------------------------< Object >---
        /**
         * Returns the internal string representation of this <code>Path</code>.
         * <p/>
         * Note that the returned string is not a valid JCR path, i.e. the
         * namespace URI's of the individual path elements are not replaced with
         * their mapped prefixes.
         *
         * @return the internal string representation of this <code>Path</code>.
         */
        @Override
        public String toString() {
            // Path is immutable, we can store the string representation
            if (string == null) {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < elements.length; i++) {
                    if (i > 0) {
                        sb.append(Path.DELIMITER);
                    }
                    Path.Element element = elements[i];
                    String elem = element.toString();
                    sb.append(elem);
                }
                string = sb.toString();
            }
            return string;
        }

        /**
         * Returns a hash code value for this path.
         *
         * @return a hash code value for this path.
         * @see Object#hashCode()
         */
        @Override
        public int hashCode() {
            // Path is immutable, we can store the computed hash code value
            int h = hash;
            if (h == 0) {
                h = 17;
                for (Element element : elements) {
                    h = 37 * h + element.hashCode();
                }
                hash = h;
            }
            return h;
        }

        /**
         * Compares the specified object with this path for equality.
         *
         * @param obj the object to be compared for equality with this path.
         * @return <tt>true</tt> if the specified object is equal to this path.
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Path) {
                Path other = (Path) obj;
                return Arrays.equals(elements, other.getElements());
            }
            return false;
        }
    }

    /**
     * Builder internal class
     */
    private static final class Builder {

        /**
         * the lpath elements of the constructed path
         */
        private final Path.Element[] elements;

        /**
         * flag indicating if the current path is normalized
         */
        private boolean isNormalized;

        /**
         * Creates a new Builder and initialized it with the given path
         * elements.
         *
         * @param elemList
         * @throws IllegalArgumentException if the given elements array is null
         * or has a zero length or would otherwise constitute an invalid path
         */
        private Builder(List<Path.Element> elemList) throws IllegalArgumentException {
            this(elemList.toArray(new Path.Element[elemList.size()]));
        }

        /**
         * Creates a new Builder and initialized it with the given path
         * elements.
         *
         * @param elements
         * @throws IllegalArgumentException if the given elements array is null
         * or has a zero length or would otherwise constitute an invalid path
         */
        private Builder(Path.Element[] elements) throws IllegalArgumentException {
            if (elements == null || elements.length == 0) {
                throw new IllegalArgumentException("Cannot build path from null or 0 elements.");
            }

            this.elements = elements;
            if (elements.length == 1) {
                isNormalized = !elements[0].denotesIdentifier();
            } else {
                boolean absolute = elements[0].denotesRoot();
                isNormalized = true;
                int depth = 0;
                for (int i = 0; i < elements.length; i++) {
                    Path.Element elem = elements[i];
                    if (elem.denotesName()) {
                        depth++;
                    } else if (elem.denotesRoot()) {
                        if (i > 0) {
                            throw new IllegalArgumentException("Invalid path: The root element may only occur at the beginning.");
                        }
                    } else if (elem.denotesIdentifier()) {
                        throw new IllegalArgumentException("Invalid path: The identifier element may only occur at the beginning of a single element path.");
                    } else  if (elem.denotesParent()) {
                        depth--;
                        if (absolute && depth < 0) {
                            throw new IllegalArgumentException("Invalid path: Too many parent elements.");
                        }
                        if (absolute || (i > 0 && elements[i - 1].denotesName())) {
                            isNormalized = false;
                        }
                    } else /* current element */ {
                        isNormalized = false;
                    }
                }
            }
        }

        /**
         * Assembles the built path and returns a new {@link Path}.
         *
         * @return a new {@link Path}
         */
        private Path getPath() {
            // special path with a single element
            if (elements.length == 1) {
                if (elements[0].denotesRoot()) {
                    return PathFactoryImpl.ROOT;
                }
                if (elements[0].denotesParent()) {
                    return PathFactoryImpl.PARENT_PATH;
                }
                if (elements[0].denotesCurrent()) {
                    return PathFactoryImpl.CURRENT_PATH;
                }
            }

            // default: build a new path
            // no need to check the path format, assuming all names correct
            return new PathImpl(elements, isNormalized);
        }
    }

}
