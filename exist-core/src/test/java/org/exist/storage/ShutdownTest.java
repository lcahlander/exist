/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.*;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.exist.samples.Samples.SAMPLES;

import org.xml.sax.SAXException;

public class ShutdownTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
	public void shutdown() throws EXistException, LockException, TriggerException, PermissionDeniedException, XPathException, IOException {
		for (int i = 0; i < 2; i++) {
			storeAndShutdown();
		}
	}
	
	public void storeAndShutdown() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

		try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            Collection test;

            try(final Txn transaction = transact.beginTransaction()) {

                test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(test);
                broker.saveCollection(transaction, test);

                // store some documents.
	            for(final String sampleName : SAMPLES.getShakespeareXmlSampleNames()) {
                    try (final InputStream is = SAMPLES.getShakespeareSample(sampleName)) {
                        final String sample = InputStreamUtil.readString(is, UTF_8);
                        final IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create(sampleName), sample);
                        assertNotNull(info);
                        test.store(transaction, broker, info, sample);
                    } catch (SAXException e) {
                        fail("Error found while parsing document: " + sampleName + ": " + e.getMessage());
                    }
                }

                final XQuery xquery = pool.getXQueryService();
                assertNotNull(xquery);
                final Sequence result = xquery.execute(broker, "//SPEECH[contains(LINE, 'love')]", null);
                assertNotNull(result);
                assertEquals(187, result.getItemCount());

                transact.commit(transaction);
            }

            try(final Txn transaction = transact.beginTransaction()) {
                broker.removeCollection(transaction, test);
                transact.commit(transaction);
            }
        }
	}
}
