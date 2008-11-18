/**
 * ISqlJet.java
 * Copyright (C) 2008 TMate Software Ltd
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package org.tmatesoft.sqljet.core;

/**
 * 
 * The list of all registered {@link ISqlJetFileSystem} implementations.
 * 
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public interface ISqlJetFileSystemsManager {

    /**
     * Locate a {@link ISqlJetFileSystem} by name. If no name is given, simply return the
     * default {@link ISqlJetFileSystem}.
     * 
     * @param name
     * @return
     */
    ISqlJetFileSystem find(final String name);

    /**
     * Register a {@link ISqlJetFileSystem} with the system.  It is harmless to register the same
     * {@link ISqlJetFileSystem} multiple times.  The new {@link ISqlJetFileSystem} becomes 
     * the default if isDefault is true.
     * 
     * @param fs
     * @param isDefault
     */
    void register(final ISqlJetFileSystem fs, final boolean isDefault);

    /**
     * Unregister a {@link ISqlJetFileSystem} so that it is no longer accessible.
     * 
     * 
     * @param fs
     */
    void unregister(final ISqlJetFileSystem fs);

}