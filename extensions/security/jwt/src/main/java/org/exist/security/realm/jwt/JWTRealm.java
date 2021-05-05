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
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        final String name1 = this.jwtContextFactory.getAccount().getSearchProperty(JWTAccount.JWTPropertyKey.valueOf("name"));
        String name = ((Claim) decodedJWT.getClaim(name1)).asString();
        final AbstractAccount account = (AbstractAccount) getAccount(name);
        return (Subject) account;
    }
    @Override
    public final synchronized Account getAccount(String name) {

        //first attempt to get the cached account
        final Account acct = super.getAccount(name);
        try {
            final DBBroker broker = getDatabase().get(Optional.of(getSecurityManager().getSystemSubject()));

            if (acct != null) {

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cached used.");
                }

                updateGroupsInDatabase(broker, this.decodedJWT, acct);

                return acct;
            } else {
                return createAccountInDatabase(broker, this.decodedJWT, name);
            }
        } catch (EXistException e) {
            e.printStackTrace();
        } catch (PermissionDeniedException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void updateGroupsInDatabase(DBBroker broker, DecodedJWT decodedJWT, Account acct) throws PermissionDeniedException, EXistException {
        final String claim = this.jwtContextFactory.getGroup().getClaim();
        final List<String> dbaList = this.jwtContextFactory.getGroup().getDbaList().getPrincipals();
        final List<String> groupNames = ((Claim) decodedJWT.getClaim(claim)).asList(String.class);
        final String[] acctGroups = acct.getGroups();

        for (final String accountGroup : acctGroups) {
            if (!groupNames.contains(accountGroup)) {
                acct.remGroup(accountGroup);
            }
        }

        for (final String groupName : groupNames) {
            if (acct.hasGroup(groupName)) {
                continue;
            }
            if (dbaList.contains(groupName)) {
                if (!acct.hasDbaRole()) {
                    acct.addGroup(getSecurityManager().getDBAGroup());
                }
            }
            final Group group = super.getGroup(groupName);

            if (group != null) {
                acct.addGroup(group);
            } else {
                final GroupAider groupAider = new GroupAider(ID, groupName);
                final Group newGroup = getSecurityManager().addGroup(broker, groupAider);
                acct.addGroup(newGroup);
            }
        }

    }

    private Account createAccountInDatabase(DBBroker broker, DecodedJWT decodedJWT, String name) throws PermissionDeniedException, EXistException {
        Account account = null;
        final String claim = this.jwtContextFactory.getGroup().getClaim();
        final List<String> jwtGroupNames = ((Claim) decodedJWT.getClaim(claim)).asList(String.class);
        final List<String> dbaList = this.jwtContextFactory.getGroup().getDbaList().getPrincipals();

        final UserAider userAider = new UserAider(ID, name);


        //store any requested metadata
        for (final AXSchemaType axSchemaType : AXSchemaType.values()) {
            final String metadataSearchProperty = this.jwtContextFactory.getAccount().getMetadataSearchProperty(axSchemaType);
            if (metadataSearchProperty != null) {
                final String s = ((Claim) decodedJWT.getClaim(metadataSearchProperty)).asString();
                if (s != null) {
                    userAider.setMetadataValue(axSchemaType, s);
                }
            }
        }

        boolean dbaNotAdded = true;

        for (final String jwtGroupName : jwtGroupNames) {
            if (dbaNotAdded && dbaList.contains(jwtGroupName)) {
                userAider.addGroup(getSecurityManager().getDBAGroup());
                dbaNotAdded = false;
            }
            final Group group = super.getGroup(jwtGroupName);

            if (group != null) {
                userAider.addGroup(group);
            } else {
                final GroupAider groupAider = new GroupAider(ID, jwtGroupName);
                final Group group1 = getSecurityManager().addGroup(broker, groupAider);
                userAider.addGroup(group1);
            }
        }

        try {
            account = getSecurityManager().addAccount(broker, userAider);
        } catch (PermissionDeniedException e) {
            e.printStackTrace();
        } catch (EXistException e) {
            e.printStackTrace();
        }

        return account;
    }

    private Group createGroupInDatabase(final DBBroker broker, final String groupname) throws AuthenticationException {
        try {
            //return sm.addGroup(instantiateGroup(this, groupname));
            return getSecurityManager().addGroup(broker, new GroupAider(ID, groupname));

        } catch (Exception e) {
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e.getMessage(), e);
        }
    }

}
