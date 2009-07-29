/**
 * IndexOrderTest.java
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

import java.util.Random;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.sqljet.core.AbstractNewDbTest;
import org.tmatesoft.sqljet.core.SqlJetException;

/**
 * @author TMate Software Ltd.
 * @author Sergey Scherbina (sergey.scherbina@gmail.com)
 * 
 */
public class IndexOrderTest extends AbstractNewDbTest {

    private ISqlJetTable table;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        super.setUp();
        db.runWriteTransaction(new ISqlJetTransaction() {
            public Object run(SqlJetDb db) throws SqlJetException {

                db.createTable("create table t(a integer primary key, b integer, c integer)");
                db.createIndex("create index b on t(b)");
                db.createIndex("create index cb on t(c,b)");
                db.createIndex("create index cd on t(c desc)");
                table = db.getTable("t");

                Random r = new Random();
                for (int i = 10; i > 0; i--) {
                    table.insert(null, i, r.nextLong());
                }

                return null;
            }
        });

    }

    @Test
    public void defaultOrder() throws SqlJetException {
        long l = Long.MAX_VALUE;
        final ISqlJetCursor c = table.open();
        try {
            for (c.first(); !c.eof(); c.next()) {
                final long b = c.getInteger("b");
                Assert.assertNotNull(b);
                Assert.assertTrue(l > b);
                l = b;
            }
        } finally {
            c.close();
        }
    }

    @Test
    public void order() throws SqlJetException {
        long l = Long.MIN_VALUE;
        final ISqlJetCursor c = table.order("b");
        try {
            for (c.first(); !c.eof(); c.next()) {
                final long b = c.getInteger("b");
                Assert.assertNotNull(b);
                Assert.assertTrue(l < b);
                l = b;
            }
        } finally {
            c.close();
        }
    }

    @Test
    public void orderMulti() throws SqlJetException {
        long l = Long.MIN_VALUE;
        final ISqlJetCursor c = table.order("cb");
        try {
            for (c.first(); !c.eof(); c.next()) {
                final long f = c.getInteger("c");
                Assert.assertNotNull(f);
                Assert.assertTrue(l < f);
                l = f;
            }
        } finally {
            c.close();
        }
    }

    @Test
    public void orderDesc() throws SqlJetException {
        long l = Long.MAX_VALUE;
        final ISqlJetCursor c = table.order("cd");
        try {
            for (c.first(); !c.eof(); c.next()) {
                final long f = c.getInteger("c");
                Assert.assertNotNull(f);
                Assert.assertTrue(l > f);
                l = f;
            }
        } finally {
            c.close();
        }
    }

}
