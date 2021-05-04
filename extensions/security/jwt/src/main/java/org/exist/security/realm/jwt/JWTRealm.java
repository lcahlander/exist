/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2021 The eXist-db Authors
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
package org.exist.security.realm.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.*;
import org.exist.security.*;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;

import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 *
 */
@ConfigurationClass("realm") //TODO: id = JWT
public class JWTRealm extends AbstractRealm {

    private static final Logger LOG = LogManager.getLogger(JWTRealm.class);
    private static JWTRealm instance = null;

    @ConfigurationFieldAsAttribute("id")
    final public static String ID = "JWT";

    @ConfigurationFieldAsAttribute("version")
    public static final String version = "1.0";

    @ConfigurationFieldAsElement("context")
    protected JWTContextFactory jwtContextFactory;

    protected DecodedJWT decodedJWT;

    public JWTRealm(final SecurityManagerImpl sm, final Configuration config) {
        super(sm, config);
        instance = this;
    }

    protected JWTContextFactory ensureContextFactory() {
        if (this.jwtContextFactory == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No JWTContextFactory specified - creating a default instance.");
            }
            this.jwtContextFactory = new JWTContextFactory(configuration);
        }
        return this.jwtContextFactory;
    }

    static JWTRealm getInstance() { return instance; }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void start(final DBBroker broker, final Txn transaction) throws EXistException {
        super.start(broker, transaction);
    }


    @Override
    public boolean deleteAccount(Account account) throws PermissionDeniedException, EXistException, ConfigurationException {
        return false;
    }

    @Override
    public boolean deleteGroup(Group group) throws PermissionDeniedException, EXistException, ConfigurationException {
        return false;
    }

    @Override
    public Subject authenticate(String accountName, Object credentials) throws AuthenticationException {
        Algorithm algorithmHS = Algorithm.HMAC256("secret");
        JWTVerifier verifier = JWT.require(algorithmHS).withIssuer("auth0").build();
        decodedJWT = verifier.verify(accountName);
        final String name1 = this.jwtContextFactory.getAccount().getSearchAttribute(JWTAccount.JWTPropertyKey.valueOf("name"));
        String name = ((Claim) decodedJWT.getClaim(name1)).asString();
        final AbstractAccount account = (AbstractAccount) getAccount(name);
        return (Subject) account;
    }
    @Override
    public final synchronized Account getAccount(String name) {

        //first attempt to get the cached account
        final Account acct = super.getAccount(name);

        if (acct != null) {

            if (LOG.isDebugEnabled()) {
                LOG.debug("Cached used.");
            }

            updateGroupsInDatabase(this.decodedJWT, acct);

            return acct;
        } else {
            return createAccountInDatabase(this.decodedJWT, name);
        }

    }

    private void updateGroupsInDatabase(DecodedJWT decodedJWT, Account acct) {
    }

    private Account createAccountInDatabase(DecodedJWT decodedJWT, String name) {
        final String givenNameProperty = this.jwtContextFactory.getAccount().getMetadataSearchAttribute(AXSchemaType.FIRSTNAME);
        final String familyNameProperty = this.jwtContextFactory.getAccount().getMetadataSearchAttribute(AXSchemaType.LASTNAME);
        String givenName = ((Claim) decodedJWT.getClaim(givenNameProperty)).asString();
        String familyName = ((Claim) decodedJWT.getClaim(familyNameProperty)).asString();
        final List<String> groups = ((Claim) decodedJWT.getClaim("groups")).asList(String.class);
        return null;
    }
}
