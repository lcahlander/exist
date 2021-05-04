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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.http.servlets.ExistExtensionServlet;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.xquery.XQueryContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 *
 */
public class JWTServlet extends HttpServlet implements ExistExtensionServlet {

    protected final static Logger LOG = LogManager.getLogger(JWTServlet.class);

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        // Get reverse proxy header when available, otherwise use regular IP address
        String authorization = request.getHeader("Authorization");
        // there may be a comma-separated chain of proxies
        if(authorization != null && !authorization.isEmpty()) {

        }

        LOG.info("Detected JSON Web Token {}", authorization);

        String jsonResponse = "{\"fail\":\"JSON Web Token not authenticated\"}";

        final SecurityManager securityManager = JWTRealm.getInstance().getSecurityManager();
        try {
            final Subject user = securityManager.authenticate(authorization, authorization);

            if (user != null) {
                LOG.info("JWTServlet user {} found", user.getUsername());

                // Security check
                if (user.hasDbaRole()) {
                    LOG.error("User {} has DBA rights, will not be authorized", user.getUsername());
                    return;
                }


                final HttpSession session = request.getSession();
                // store the user in the session
                if (session != null) {
                    jsonResponse = "{\"user\":\"" + user.getUsername() + "\",\"isAdmin\":\"" + user.hasDbaRole() + "\"}";
                    LOG.info("JWTServlet setting session attr " + XQueryContext.HTTP_SESSIONVAR_XMLDB_USER);
                    session.setAttribute(XQueryContext.HTTP_SESSIONVAR_XMLDB_USER, user);
                } else {
                    LOG.info("JWTServlet session is null");
                }

            } else {
                LOG.error("JWTServlet user not found");
            }
        } catch (AuthenticationException e) {
            e.printStackTrace();
        } finally {
            response.setContentType("application/json");
            final PrintWriter out = response.getWriter();
            out.print(jsonResponse);
            out.flush();
        }

    }

    @Override
    public String getServletInfo() {
        return "JSON Web Tokens filter";
    }


    /**
     * Get path for servlet
     *
     * @return Path of servlet
     */
    @Override
    public String getPathSpec() {
        return "/jwt";
    }
}
