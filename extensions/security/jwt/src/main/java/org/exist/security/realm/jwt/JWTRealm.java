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

import java.util.Map;

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
        DecodedJWT jwt = verifier.verify(accountName);
        final Map<String, Claim> claims = jwt.getClaims();
        return null;
    }

    @Override
    public String getId() {
        return ID;
    }
}
