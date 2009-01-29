/**
 * Page.java
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
package org.tmatesoft.sqljet.core.internal.pager;

import static org.tmatesoft.sqljet.core.SqlJetException.assertion;

import java.util.BitSet;
import java.util.EnumSet;

import org.tmatesoft.sqljet.core.ISqlJetFile;
import org.tmatesoft.sqljet.core.ISqlJetPage;
import org.tmatesoft.sqljet.core.ISqlJetPager;
import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetPageFlags;
import org.tmatesoft.sqljet.core.SqlJetPagerFlags;
import org.tmatesoft.sqljet.core.SqlJetPagerJournalMode;
import org.tmatesoft.sqljet.core.SqlJetUtility;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public class SqlJetPage implements ISqlJetPage {

    byte[] pData; /* Content of this page */
    byte[] pExtra; /* Extra content */
    SqlJetPage pDirty; /* Transient list of dirty pages */
    int pgno; /* Page number for this page */
    SqlJetPager pPager; /* The pager this page is part of */
    long pageHash; /* Hash of page content */
    EnumSet<SqlJetPageFlags> flags = EnumSet.noneOf(SqlJetPageFlags.class); /*
                                                                             * PGHDR
                                                                             * flags
                                                                             * defined
                                                                             * below
                                                                             */
    /**********************************************************************
     ** Elements above are public. All that follows is private to pcache.c and
     * should not be accessed by other modules.
     */
    int nRef; /* Number of users of this page */
    SqlJetPageCache pCache; /* Cache that owns this page */
    byte[][] apSave = new byte[2][]; /* Journal entries for in-memory databases */
    /**********************************************************************
     ** Elements above are accessible at any time by the owner of the cache
     * without the need for a mutex. The elements that follow can only be
     * accessed while holding the SQLITE_MUTEX_STATIC_LRU mutex.
     */
    SqlJetPage pNextHash, pPrevHash; /* Hash collision chain for PgHdr.pgno */
    SqlJetPage pNext, pPrev; /* List of clean or dirty pages */
    SqlJetPage pNextLru, pPrevLru; /* Part of global LRU list */

    /**
     * 
     */
    SqlJetPage(int szPage, int szExtra) {
        pData = new byte[szPage];
        pExtra = new byte[szExtra];
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#dontRollback()
     */
    public void dontRollback() throws SqlJetException {

        assertion(pPager.state.compareTo(SqlJetPagerState.RESERVED) >= 0);

        /*
         * If the journal file is not open, or DontWrite() has been called on
         * this page (DontWrite() sets the alwaysRollback flag), then this
         * function is a no-op.
         */
        if (!pPager.journalOpen || SqlJetUtility.bitSetTest(pPager.pagesAlwaysRollback, pgno)
                || pgno > pPager.origDbSize) {
            return;
        }

        assertion(!pPager.memDb); /*
                                   * For a memdb, pPager->journalOpen is always
                                   * 0
                                   */

        if (flags.contains(SqlJetPageFlags.IN_JOURNAL)) {
            return;
        }

        /*
         * If SECURE_DELETE is disabled, then there is no way that this routine
         * can be called on a page for which sqlite3PagerDontWrite() has not
         * been previously called during the same transaction. And if
         * DontWrite() has previously been called, the following conditions must
         * be met.
         * 
         * (Later:) Not true. If the database is corrupted by having duplicate
         * pages on the freelist (ex: corrupt9.test) then the following is not
         * necessarily true:
         */

        assertion(pPager.pagesInJournal != null);
        pPager.pagesInJournal.set(pgno);
        flags.add(SqlJetPageFlags.IN_JOURNAL);
        flags.remove(SqlJetPageFlags.NEED_READ);

        if (pPager.stmtInUse) {
            assertion(pPager.stmtSize >= pPager.origDbSize);
            pPager.pagesInStmt.set(pgno);
        }

        // PAGERTRACE3("DONT_ROLLBACK page %d of %d\n", pPg->pgno,
        // PAGERID(pPager));
        // IOTRACE(("GARBAGE %p %d\n", pPager, pPg->pgno))

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#dontWrite()
     */
    public void dontWrite() throws SqlJetException {

        if (pPager.memDb || pgno > pPager.origDbSize) {
            return;
        }

        if (pPager.pagesAlwaysRollback == null) {
            assertion(pPager.pagesInJournal);
            pPager.pagesAlwaysRollback = new BitSet(pPager.origDbSize);
        }
        pPager.pagesAlwaysRollback.set(pgno);
        if (flags.contains(SqlJetPageFlags.DIRTY) && !pPager.stmtInUse) {
            assertion(pPager.state.compareTo(SqlJetPagerState.SHARED) >= 0);
            if (pPager.dbSize == pgno && pPager.origDbSize < pPager.dbSize) {
                /*
                 * If this pages is the last page in the file and the file has
                 * grown during the current transaction, then do NOT mark the
                 * page as clean. When the database file grows, we must make
                 * sure that the last page gets written at least once so that
                 * the disk file will be the correct size. If you do not write
                 * this page and the size of the file on the disk ends up being
                 * too small, that can lead to database corruption during the
                 * next transaction.
                 */
            } else {
                // PAGERTRACE3("DONT_WRITE page %d of %d\n", pPg->pgno,
                // PAGERID(pPager));
                // IOTRACE(("CLEAN %p %d\n", pPager, pPg->pgno))
                flags.add(SqlJetPageFlags.DONT_WRITE);
                pageHash = pPager.pageHash(this);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#getData()
     */
    public byte[] getData() throws SqlJetException {
        assertion(nRef > 0);
        return pData;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#getExtra()
     */
    public byte[] getExtra() {
        return (pPager != null ? pExtra : null);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#move(int, boolean)
     */
    public void move(int pageNumber, boolean isCommit) throws SqlJetException {

        SqlJetPage pPgOld; /* The page being overwritten. */
        int needSyncPgno = 0;

        assertion(nRef > 0);

        // PAGERTRACE5("MOVE %d page %d (needSync=%d) moves to %d\n",
        // PAGERID(pPager), pPg->pgno, (pPg->flags&PGHDR_NEED_SYNC)?1:0, pgno);
        // IOTRACE(("MOVE %p %d %d\n", pPager, pPg->pgno, pgno))

        pPager.getContent(this);

        /*
         * If the journal needs to be sync()ed before page pPg->pgno can be
         * written to, store pPg->pgno in local variable needSyncPgno.
         * 
         * If the isCommit flag is set, there is no need to remember that the
         * journal needs to be sync()ed before database page pPg->pgno can be
         * written to. The caller has already promised not to write to it.
         */
        if (flags.contains(SqlJetPageFlags.NEED_SYNC) && !isCommit) {
            needSyncPgno = pgno;
            assertion(flags.contains(SqlJetPageFlags.IN_JOURNAL) || pgno > pPager.origDbSize);
            assertion(flags.contains(SqlJetPageFlags.DIRTY));
            assertion(pPager.needSync);
        }

        /*
         * If the cache contains a page with page-number pgno, remove it from
         * its hash chain. Also, if the PgHdr.needSync was set for page pgno
         * before the 'move' operation, it needs to be retained for the page
         * moved there.
         */
        flags.remove(SqlJetPageFlags.NEED_SYNC);
        flags.remove(SqlJetPageFlags.IN_JOURNAL);
        pPgOld = (SqlJetPage) pPager.lookup(pgno);
        assertion(pPgOld == null || pPgOld.nRef == 1);
        if (pPgOld != null) {
            if (pPgOld.flags.contains(SqlJetPageFlags.NEED_SYNC))
                flags.add(SqlJetPageFlags.NEED_SYNC);
        }
        if (SqlJetUtility.bitSetTest(pPager.pagesInJournal, pgno)) {
            assertion(!pPager.memDb);
            flags.add(SqlJetPageFlags.IN_JOURNAL);
        }

        pPager.pageCache.move(this, pgno);

        if (pPgOld != null) {
            pPager.pageCache.move(pPgOld, 0);
            pPager.pageCache.release(pPgOld);
        }

        pPager.pageCache.makeDirty(this);
        pPager.dirtyCache = true;
        pPager.dbModified = true;

        if (needSyncPgno != 0) {
            /*
             * If needSyncPgno is non-zero, then the journal file needs to be
             * sync()ed before any data is written to database file page
             * needSyncPgno. Currently, no such page exists in the page-cache
             * and the "is journaled" bitvec flag has been set. This needs to be
             * remedied by loading the page into the pager-cache and setting the
             * PgHdr.needSync flag.
             * 
             * If the attempt to load the page into the page-cache fails, (due
             * to a malloc() or IO failure), clear the bit in the pInJournal[]
             * array. Otherwise, if the page is loaded and written again in this
             * transaction, it may be written to the database file before it is
             * synced into the journal file. This way, it may end up in the
             * journal file twice, but that is not a problem.
             * 
             * The sqlite3PagerGet() call may cause the journal to sync. So make
             * sure the Pager.needSync flag is set too.
             */
            SqlJetPage pPgHdr;
            assertion(pPager.needSync);
            try {
                pPgHdr = (SqlJetPage) pPager.getPage(needSyncPgno);
            } catch (SqlJetException e) {
                if (pPager.pagesInJournal != null && needSyncPgno <= pPager.origDbSize) {
                    pPager.pagesInJournal.clear(needSyncPgno);
                }
                throw e;
            }

            pPager.needSync = true;
            assertion(!pPager.noSync && !pPager.memDb);
            pPgHdr.flags.add(SqlJetPageFlags.NEED_SYNC);
            pPgHdr.flags.add(SqlJetPageFlags.IN_JOURNAL);
            pPager.pageCache.makeDirty(pPgHdr);
            pPgHdr.unref();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#ref()
     */
    public void ref() throws SqlJetException {
        assertion(nRef > 0);
        nRef++;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#unref()
     */
    public void unref() throws SqlJetException {
        try {
            pPager.pageCache.release(this);
        } finally {
            pPager.unlockIfUnused();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#write()
     */
    public void write() throws SqlJetException {

        int nPagePerSector = (pPager.sectorSize / pPager.pageSize);

        if (!pPager.memDb && nPagePerSector > 1) {

            int nPageCount; /* Total number of pages in database file */
            int pg1; /* First page of the sector pPg is located on. */
            int nPage; /* Number of pages starting at pg1 to journal */
            int ii;
            boolean needSync = false;

            /*
             * Set the doNotSync flag to 1. This is because we cannot allow a
             * journal header to be written between the pages journaled by this
             * function.
             */
            assertion(!pPager.doNotSync);
            pPager.doNotSync = true;

            /*
             * This trick assumes that both the page-size and sector-size are an
             * integer power of 2. It sets variable pg1 to the identifier of the
             * first page of the sector pPg is located on.
             */
            pg1 = ((pgno - 1) & ~(nPagePerSector - 1)) + 1;

            nPageCount = pPager.getPageCount();
            if (pgno > nPageCount) {
                nPage = (pgno - pg1) + 1;
            } else if ((pg1 + nPagePerSector - 1) > nPageCount) {
                nPage = nPageCount + 1 - pg1;
            } else {
                nPage = nPagePerSector;
            }
            assertion(nPage > 0);
            assertion(pg1 <= pgno);
            assertion((pg1 + nPage) > pgno);

            for (ii = 0; ii < nPage; ii++) {
                int pg = pg1 + ii;
                SqlJetPage pPage;
                if (pg == pgno || !SqlJetUtility.bitSetTest(pPager.pagesInJournal, pg)) {
                    if (pg != pPager.PAGER_MJ_PGNO()) {
                        pPage = (SqlJetPage) pPager.getPage(pg);
                        pPage.doWrite();
                        if (pPage.flags.contains(SqlJetPageFlags.NEED_SYNC)) {
                            needSync = true;
                        }
                        pPage.unref();
                    }
                } else if ((pPage = (SqlJetPage) pPager.lookup(pg)) != null) {
                    if (pPage.flags.contains(SqlJetPageFlags.NEED_SYNC)) {
                        needSync = true;
                    }
                    pPage.unref();
                }
            }

            /*
             * If the PgHdr.needSync flag is set for any of the nPage pages
             * starting at pg1, then it needs to be set for all of them. Because
             * writing to any of these nPage pages may damage the others, the
             * journal file must contain sync()ed copies of all of them before
             * any of them can be written out to the database file.
             */
            if (needSync) {
                assertion(!pPager.memDb && !pPager.noSync);
                for (ii = 0; ii < nPage && needSync; ii++) {
                    SqlJetPage pPage = (SqlJetPage) pPager.lookup(pg1 + ii);
                    if (pPage != null)
                        pPage.flags.add(SqlJetPageFlags.NEED_SYNC);
                    pPage.unref();
                }
                assertion(pPager.needSync);
            }

            assertion(pPager.doNotSync);
            pPager.doNotSync = false;

        } else {
            doWrite();
        }
    }

    /**
     * Mark a data page as writeable. The page is written into the journal if it
     * is not there already. This routine must be called before making changes
     * to a page.
     * 
     * The first time this routine is called, the pager creates a new journal
     * and acquires a RESERVED lock on the database. If the RESERVED lock could
     * not be acquired, this routine returns SQLITE_BUSY. The calling routine
     * must check for that return value and be careful not to change any page
     * data until this routine returns SQLITE_OK.
     * 
     * If the journal file could not be written because the disk is full, then
     * this routine returns SQLITE_FULL and does an immediate rollback. All
     * subsequent write attempts also return SQLITE_FULL until there is a call
     * to sqlite3PagerCommit() or sqlite3PagerRollback() to reset.
     * 
     */
    private void doWrite() throws SqlJetException {

        /*
         * Check for errors
         */
        if (pPager.errCode != null) {
            throw new SqlJetException(pPager.errCode);
        }
        if (pPager.readOnly) {
            throw new SqlJetException(SqlJetErrorCode.PERM);
        }

        assertion(!pPager.setMaster);

        /*
         * If this page was previously acquired with noContent==1, that means we
         * didn't really read in the content of the page. This can happen (for
         * example) when the page is being moved to the freelist. But now we are
         * (perhaps) moving the page off of the freelist for reuse and we need
         * to know its original content so that content can be stored in the
         * rollback journal. So do the read at this time.
         */
        pPager.getContent(this);

        /*
         * Mark the page as dirty. If the page has already been written to the
         * journal then we can return right away.
         */
        pCache.makeDirty(this);
        if (flags.contains(SqlJetPageFlags.IN_JOURNAL) && (pageInStatement() || !pPager.stmtInUse)) {
            pPager.dirtyCache = true;
            pPager.dbModified = true;
        } else {
            /*
             * If we get this far, it means that the page needs to be written to
             * the transaction journal or the ckeckpoint journal or both.
             * 
             * First check to see that the transaction journal exists and create
             * it if it does not.
             */
            assertion(pPager.state != SqlJetPagerState.UNLOCK);
            pPager.begin(false);
            assertion(pPager.state.compareTo(SqlJetPagerState.RESERVED) >= 0);
            if (!pPager.journalOpen && pPager.useJournal && pPager.journalMode != SqlJetPagerJournalMode.OFF) {
                pPager.openJournal();
            }
            pPager.dirtyCache = true;
            pPager.dbModified = true;

            /*
             * The transaction journal now exists and we have a RESERVED or an
             * EXCLUSIVE lock on the main database file. Write the current page
             * to the transaction journal if it is not there already.
             */
            if (!flags.contains(SqlJetPageFlags.IN_JOURNAL) && (pPager.journalOpen || pPager.memDb)) {
                if (pgno <= pPager.origDbSize) {
                    if (pPager.memDb) {
                        // PAGERTRACE3("JOURNAL %d page %d\n", PAGERID(pPager),
                        // pPg->pgno);
                        pPager.pageCache.preserve(this, false);
                    } else {

                        /*
                         * We should never write to the journal file the page
                         * that contains the database locks. The following
                         * assert verifies that we do not.
                         */
                        assertion(pgno != pPager.PAGER_MJ_PGNO());

                        /*
                         * An error has occured writing to the journal file. The
                         * transaction will be rolled back by the layer above.
                         */
                        int cksum = pPager.cksum(pData);
                        pPager.write32bits(pPager.jfd, pPager.journalOff, pgno);
                        try {
                            pPager.jfd.write(pData, pPager.pageSize, pPager.journalOff + 4);
                        } finally {
                            pPager.journalOff += pPager.pageSize + 4;
                        }
                        try {
                            pPager.write32bits(pPager.jfd, pPager.journalOff, cksum);
                        } finally {
                            pPager.journalOff += 4;
                        }
                        // IOTRACE(("JOUT %p %d %lld %d\n", pPager, pPg->pgno,
                        // pPager->journalOff, pPager->pageSize));
                        // PAGER_INCR(sqlite3_pager_writej_count);
                        // PAGERTRACE5("JOURNAL %d page %d needSync=%d hash(%08x)\n",
                        // PAGERID(pPager), pPg->pgno,
                        // ((pPg->flags&PGHDR_NEED_SYNC)?1:0),
                        // pager_pagehash(pPg));

                        pPager.nRec++;
                        assertion(pPager.pagesInJournal != null);
                        SqlJetUtility.bitSetTest(pPager.pagesInJournal, pgno);
                        if (!pPager.noSync) {
                            flags.add(SqlJetPageFlags.NEED_SYNC);
                        }
                        if (pPager.stmtInUse) {
                            pPager.pagesInStmt.set(pgno);
                        }
                    }
                } else {
                    if (!pPager.journalStarted && !pPager.noSync) {
                        flags.add(SqlJetPageFlags.NEED_SYNC);
                    }
                    // PAGERTRACE4("APPEND %d page %d needSync=%d\n",
                    // PAGERID(pPager), pPg->pgno,
                    // ((pPg->flags&PGHDR_NEED_SYNC)?1:0));
                }
                if (flags.contains(SqlJetPageFlags.NEED_SYNC)) {
                    pPager.needSync = true;
                }
                flags.add(SqlJetPageFlags.IN_JOURNAL);
            }

            /*
             * If the statement journal is open and the page is not in it, then
             * write the current page to the statement journal. Note that the
             * statement journal format differs from the standard journal format
             * in that it omits the checksums and the header.
             */
            if (pPager.stmtInUse && !pageInStatement() && pgno <= pPager.stmtSize) {
                assertion(flags.contains(SqlJetPageFlags.IN_JOURNAL) || pgno > pPager.origDbSize);
                if (pPager.memDb) {
                    pPager.pageCache.preserve(this, true);
                    // PAGERTRACE3("STMT-JOURNAL %d page %d\n", PAGERID(pPager),
                    // pPg->pgno);
                } else {
                    long offset = pPager.stmtNRec * (4 + pPager.pageSize);
                    pPager.write32bits(pPager.stfd, offset, pgno);
                    pPager.stfd.write(pData, pPager.pageSize, offset + 4);
                    // PAGERTRACE3("STMT-JOURNAL %d page %d\n", PAGERID(pPager),
                    // pPg->pgno);
                    pPager.stmtNRec++;
                    assertion(pPager.pagesInStmt);
                    pPager.pagesInStmt.set(pgno);
                }
            }
        }

        /*
         * Update the database size and return.
         */
        assertion(pPager.state.compareTo(SqlJetPagerState.SHARED) >= 0);
        if (pPager.dbSize < pgno) {
            pPager.dbSize = pgno;
            if (!pPager.memDb && pPager.dbSize == ISqlJetFile.PENDING_BYTE / pPager.pageSize) {
                pPager.dbSize++;
            }
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#getFlags()
     */
    public EnumSet<SqlJetPageFlags> getFlags() {
        return flags;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#getHash()
     */
    public long getHash() {
        return pageHash;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#getPager()
     */
    public ISqlJetPager getPager() {
        return pPager;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#setFlags(java.util.EnumSet)
     */
    public void setFlags(EnumSet<SqlJetPageFlags> flags) {
        this.flags = flags;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#setHash(long)
     */
    public void setHash(long hash) {
        pageHash = hash;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.tmatesoft.sqljet.core.ISqlJetPage#setPager(org.tmatesoft.sqljet.core
     * .ISqlJetPager)
     */
    public void setPager(ISqlJetPager pager) {
        pPager = (SqlJetPager)pager;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#getPageNumber()
     */
    public int getPageNumber() {
        return pgno;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#setPageNumber(long)
     */
    public void setPageNumber(int pageNumber) {
        pgno = pageNumber;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#getNext()
     */
    public ISqlJetPage getNext() {
        return pNext;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.tmatesoft.sqljet.core.ISqlJetPage#getPrev()
     */
    public ISqlJetPage getPrev() {
        return pPrev;
    }

    /*
     * Return true if pagepPg has already been written to the statement journal
     * (or statement snapshot has been created, ifpPg is part of an in-memory
     * database).
     */
    boolean pageInStatement() {
        if (pPager.memDb) {
            return apSave[1] != null;
        } else {
            return SqlJetUtility.bitSetTest(pPager.pagesInStmt, pgno);
        }
    }

}