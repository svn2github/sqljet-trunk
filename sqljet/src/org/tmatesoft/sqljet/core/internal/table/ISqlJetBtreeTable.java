/**
 * ISqlJetBtreeTable.java
 * Copyright (C) 2009 TMate Software Ltd
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
package org.tmatesoft.sqljet.core.internal.table;

import org.tmatesoft.sqljet.core.SqlJetEncoding;
import org.tmatesoft.sqljet.core.SqlJetException;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public interface ISqlJetBtreeTable {

    void close() throws SqlJetException;

    void lock();

    void unlock();

    boolean eof();

    boolean first() throws SqlJetException;

    boolean last() throws SqlJetException;

    boolean next() throws SqlJetException;

    boolean previous() throws SqlJetException;

    ISqlJetBtreeRecord getRecord() throws SqlJetException;

    void lockTable(boolean write);

    /**
     * @return
     * @throws SqlJetException 
     */
    SqlJetEncoding getEncoding() throws SqlJetException;
}