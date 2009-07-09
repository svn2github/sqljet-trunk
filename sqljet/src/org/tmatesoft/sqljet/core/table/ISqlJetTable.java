/**
 * ISqlJetTable.java
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
 *
 * For information on how to redistribute this software under
 * the terms of a license other than GNU General Public License
 * contact TMate Software at support@sqljet.com
 */
package org.tmatesoft.sqljet.core.table;

import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.schema.ISqlJetTableDef;

/**
 * Table's interface.
 * 
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public interface ISqlJetTable {

    /**
     * @return the isRowIdPrimaryKey
     */
    boolean isRowIdPrimaryKey() throws SqlJetException;

    /**
     * @return the isAutoincrement
     */
    boolean isAutoincrement() throws SqlJetException;

    /**
     * @return the primaryKeyIndex
     */
    String getPrimaryKeyIndexName() throws SqlJetException;

    /**
     * Get table's schema definition.
     */
    ISqlJetTableDef getDefinition() throws SqlJetException;

    /**
     * Open cursor for all table records. Client is responsible to close the
     * cursor after use.
     */
    ISqlJetCursor open() throws SqlJetException;

    /**
     * Open cursor for indexed records. Client is responsible to close the
     * cursor after use.
     * 
     * @param indexName
     *            Name of the searched index.
     * @param key
     *            Key for the index lookup.
     */
    ISqlJetCursor lookup(String indexName, Object... key) throws SqlJetException;

    /**
     * Add new record to the table with specified values. All relevant indexes
     * are updated automatically. If table have INTEGER PRIMARY KEY
     * AUTOINCREMENT field then 'values' should not have value for this field.
     * 
     * @param values
     *            Values for the new record.
     */
    long insert(Object... values) throws SqlJetException;

    /**
     * Version of insert() for tables with INTEGER PRIMARY KEY field. Method
     * implements AUTOINCREMENT even if it doesn't have AUTOINCREMENT in
     * definition.
     * 
     * @param values
     * @return
     * @throws SqlJetException
     */
    long insertAutoId(Object... values) throws SqlJetException;

    /**
     * Insert record by values by names of fields.
     * 
     * @param values
     * @return
     * @throws SqlJetException
     */
    long insertByFieldNames(Map<String, Object> values) throws SqlJetException;

    /**
     * Insert record by values by names of fields and .
     * 
     * 
     * @param values
     * @return
     * @throws SqlJetException
     */
    long insertByFieldNamesAutoId(Map<String, Object> values) throws SqlJetException;
}
