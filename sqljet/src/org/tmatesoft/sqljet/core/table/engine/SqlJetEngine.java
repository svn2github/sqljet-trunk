/**
 * SqlJetEngine.java
 * Copyright (C) 2009-2010 TMate Software Ltd
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
package org.tmatesoft.sqljet.core.table.engine;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.tmatesoft.sqljet.core.ISqlJetMutex;
import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.internal.ISqlJetBtree;
import org.tmatesoft.sqljet.core.internal.ISqlJetDbHandle;
import org.tmatesoft.sqljet.core.internal.ISqlJetFile;
import org.tmatesoft.sqljet.core.internal.ISqlJetPager;
import org.tmatesoft.sqljet.core.internal.SqlJetBtreeFlags;
import org.tmatesoft.sqljet.core.internal.SqlJetFileOpenPermission;
import org.tmatesoft.sqljet.core.internal.SqlJetFileType;
import org.tmatesoft.sqljet.core.internal.SqlJetUtility;
import org.tmatesoft.sqljet.core.internal.btree.SqlJetBtree;
import org.tmatesoft.sqljet.core.internal.db.SqlJetDbHandle;
import org.tmatesoft.sqljet.core.internal.schema.SqlJetSchema;
import org.tmatesoft.sqljet.core.internal.table.SqlJetOptions;
import org.tmatesoft.sqljet.core.table.ISqlJetBusyHandler;
import org.tmatesoft.sqljet.core.table.ISqlJetOptions;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDefaultBusyHandler;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public class SqlJetEngine {

    private static final String TRANSACTION_ALREADY_STARTED = "Transaction already started";

    private static final Set<SqlJetBtreeFlags> READ_FLAGS = Collections.unmodifiableSet(SqlJetUtility
            .of(SqlJetBtreeFlags.READONLY));
    private static final Set<SqlJetFileOpenPermission> READ_PERMISSIONS = Collections.unmodifiableSet(SqlJetUtility
            .of(SqlJetFileOpenPermission.READONLY));
    private static final Set<SqlJetBtreeFlags> WRITE_FLAGS = Collections.unmodifiableSet(SqlJetUtility.of(
            SqlJetBtreeFlags.READWRITE, SqlJetBtreeFlags.CREATE));
    private static final Set<SqlJetFileOpenPermission> WRITE_PREMISSIONS = Collections.unmodifiableSet(SqlJetUtility
            .of(SqlJetFileOpenPermission.READWRITE, SqlJetFileOpenPermission.CREATE));

    protected boolean writable;
    protected ISqlJetDbHandle dbHandle;
    protected ISqlJetBtree btree;
    protected boolean open = false;
    protected File file;

    private boolean transaction;
    private SqlJetTransactionMode transactionMode;

    /**
     * 
     */
    public SqlJetEngine(final File file, final boolean writable) {
        this.writable = writable;
        this.file = file;
    }

    /**
     * @return database file this engine is created for.
     */
    public File getFile() {
        return this.file;
    }

    /**
     * Check write access to data base.
     * 
     * @return true if modification is allowed
     */
    public boolean isWritable() throws SqlJetException {
        return writable;
    }

    /**
     * Checks is database open.
     * 
     * @return true if database is open.
     */
    public boolean isOpen() {
        return open;
    }

    protected void checkOpen() throws SqlJetException {
        if (!isOpen())
            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Database closed");
    }

    /**
     * <p>
     * Opens connection to database. It does not create any locking on database.
     * First lock will be created when be called any method which requires real
     * access to options or schema.
     * </p>
     * 
     * @throws SqlJetException
     *             if any trouble with access to file or database format.
     */
    public synchronized void open() throws SqlJetException {
        if (!open) {
            dbHandle = new SqlJetDbHandle();
            dbHandle.setBusyHandler(new SqlJetDefaultBusyHandler());
            btree = new SqlJetBtree();
            final Set<SqlJetBtreeFlags> flags = EnumSet.copyOf(writable ? WRITE_FLAGS : READ_FLAGS);
            final Set<SqlJetFileOpenPermission> permissions = EnumSet.copyOf(writable ? WRITE_PREMISSIONS
                    : READ_PERMISSIONS);
            final SqlJetFileType type = (file != null ? SqlJetFileType.MAIN_DB : SqlJetFileType.TEMP_DB);
            btree.open(file, dbHandle, flags, type, permissions);
            
            // force readonly.
            ISqlJetFile file = btree.getPager().getFile();
            if (file != null) {
                Set<SqlJetFileOpenPermission> realPermissions = btree.getPager().getFile().getPermissions();
                writable = realPermissions.contains(SqlJetFileOpenPermission.READWRITE);
            }
            open = true;
        } else {
            throw new SqlJetException(SqlJetErrorCode.MISUSE, "Database is open already");
        }
    }

    public Object runSynchronized(ISqlJetEngineSynchronized op) throws SqlJetException {
        checkOpen();
        dbHandle.getMutex().enter();
        try {
            return op.runSynchronized(this);
        } finally {
            dbHandle.getMutex().leave();
        }
    }

    /**
     * Close connection to database. It is safe to call this method if database
     * connections is closed already.
     * 
     * @throws SqlJetException
     *             it is possible to get exception if there is actvie
     *             transaction and rollback did not success.
     */
    public void close() throws SqlJetException {
        if (open) {
            runSynchronized(new ISqlJetEngineSynchronized() {
                public Object runSynchronized(SqlJetEngine engine) throws SqlJetException {
                    if (btree != null) {
                        btree.close();
                        btree = null;
                        open = false;
                    }
                    return null;
                }
            });
            if (!open) {
                dbHandle = null;
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (open) {
                close();
            }
        } finally {
            super.finalize();
        }
    }

    /**
     * Reads database schema and options.
     * 
     * @throws SqlJetException
     */
    protected void readSchema() throws SqlJetException {
        runSynchronized(new ISqlJetEngineSynchronized() {
            public Object runSynchronized(SqlJetEngine engine) throws SqlJetException {
                btree.enter();
                try {
                    dbHandle.setOptions(new SqlJetOptions(btree, dbHandle));
                    btree.setSchema(new SqlJetSchema(dbHandle, btree));
                } finally {
                    btree.leave();
                }
                return null;
            }
        });
    }

    /**
     * Returns database options.
     * 
     * @return options of this database.
     */
    public ISqlJetOptions getOptions() throws SqlJetException {
        checkOpen();
        if (null == btree.getSchema()) {
            readSchema();
        }
        return dbHandle.getOptions();
    }

    /**
     * Refreshes database schema.
     */
    public void refreshSchema() throws SqlJetException {
        if (null == btree.getSchema() || !getOptions().verifySchemaVersion(false)) {
            readSchema();
        }
    }

    protected SqlJetSchema getSchemaInternal() throws SqlJetException {
        checkOpen();
        refreshSchema();
        return btree.getSchema();
    }

    /**
     * Get busy handler.
     * 
     * @return the busy handler.
     */
    public ISqlJetBusyHandler getBusyHandler() {
        return dbHandle.getBusyHandler();
    }

    /**
     * Set busy handler. Busy handler treats situation when database is locked
     * by other process or thread.
     * 
     * @param busyHandler
     *            the busy handler.
     */
    public void setBusyHandler(ISqlJetBusyHandler busyHandler) {
        dbHandle.setBusyHandler(busyHandler);
    }

    /**
     * Retruns threading synchronization mutex.
     * 
     * @return Semaphore instance used to synchronize database access from
     *         multiple threads within the same process.
     */
    public ISqlJetMutex getMutex() {
        return dbHandle.getMutex();
    }

    /**
     * Set cache size (in count of pages).
     * 
     * @param cacheSize
     *            the count of pages which can hold cache.
     */
    public void setCacheSize(final int cacheSize) throws SqlJetException {
        checkOpen();
        runSynchronized(new ISqlJetEngineSynchronized() {
            public Object runSynchronized(SqlJetEngine engine) throws SqlJetException {
                btree.setCacheSize(cacheSize);
                return null;
            }
        });
    }

    /**
     * Get cache size (in count of pages).
     * 
     * @return the count of pages which can hold cache.
     */
    public int getCacheSize() throws SqlJetException {
        checkOpen();
        refreshSchema();
        return (Integer) runSynchronized(new ISqlJetEngineSynchronized() {
            public Object runSynchronized(SqlJetEngine engine) throws SqlJetException {
                return btree.getCacheSize();
            }
        });
    }

    /**
     * Returns true if a transaction is active.
     * 
     * @return true if there is an active running transaction.
     */
    public boolean isInTransaction() {
        return transaction;
    }

    public SqlJetTransactionMode getTransactionMode() {
        return transactionMode;
    }

    /**
     * Begin transaction.
     * 
     * @param mode
     *            transaction's mode.
     */
    public void beginTransaction(final SqlJetTransactionMode mode) throws SqlJetException {
        checkOpen();
        runSynchronized(new ISqlJetEngineSynchronized() {
            public Object runSynchronized(SqlJetEngine engine) throws SqlJetException {
                refreshSchema();
                if (transaction) {
                    throw new SqlJetException(SqlJetErrorCode.MISUSE, TRANSACTION_ALREADY_STARTED);
                } else {
                    btree.beginTrans(mode);
                    transaction = true;
                    transactionMode = mode;
                    return null;
                }
            }
        });
    }

    /**
     * Commits transaction.
     * 
     */
    public void commit() throws SqlJetException {
        checkOpen();
        runSynchronized(new ISqlJetEngineSynchronized() {
            public Object runSynchronized(SqlJetEngine engine) throws SqlJetException {
                if (transaction) {
                    btree.closeAllCursors();
                    btree.commit();
                    transaction = false;
                    transactionMode = null;
                } else {
                    throw new SqlJetException(SqlJetErrorCode.MISUSE, "Transaction wasn't started");
                }
                return null;
            }
        });
    }

    /**
     * Rolls back transaction.
     * 
     */
    public void rollback() throws SqlJetException {
        checkOpen();
        runSynchronized(new ISqlJetEngineSynchronized() {
            public Object runSynchronized(SqlJetEngine engine) throws SqlJetException {
                if (transaction) {
                    try {
                        btree.closeAllCursors();
                        btree.rollback();
                    } finally {
                        transaction = false;
                        transactionMode = null;
                    }
                } else {
                    throw new SqlJetException(SqlJetErrorCode.MISUSE, "Transaction wasn't started");
                }
                return null;
            }
        });
    }

    /**
     * Runs transaction.
     * 
     * @param op
     *            transaction's body (closure).
     * @param mode
     *            transaction's mode.
     * @return result of
     *         {@link ISqlJetTransaction#run(org.tmatesoft.sqljet.core.table.SqlJetDb)}
     *         call.
     * @throws SqlJetException
     */
    protected Object runEngineTransaction(final ISqlJetEngineTransaction op, final SqlJetTransactionMode mode)
            throws SqlJetException {
        checkOpen();
        return runSynchronized(new ISqlJetEngineSynchronized() {
            public Object runSynchronized(SqlJetEngine engine) throws SqlJetException {
                if (transaction) {
                    if (mode != transactionMode && transactionMode == SqlJetTransactionMode.READ_ONLY) {
                        throw new SqlJetException(SqlJetErrorCode.MISUSE, TRANSACTION_ALREADY_STARTED);
                    } else {
                        return op.run(SqlJetEngine.this);
                    }
                } else {
                    beginTransaction(mode);
                    boolean success = false;
                    try {
                        final Object result = op.run(SqlJetEngine.this);
                        btree.commit();
                        success = true;
                        return result;
                    } finally {
                        if (!success) {
                            btree.rollback();
                        }
                        transaction = false;
                        transactionMode = null;
                    }
                }
            }

        });
    }

}