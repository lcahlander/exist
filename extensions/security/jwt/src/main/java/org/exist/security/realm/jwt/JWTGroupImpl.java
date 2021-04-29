package org.exist.security.realm.jwt;

import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.security.AbstractRealm;
import org.exist.security.Account;
import org.exist.security.internal.GroupImpl;
import org.exist.storage.DBBroker;

import java.util.List;

public class JWTGroupImpl extends GroupImpl {
    public JWTGroupImpl(AbstractRealm realm, Configuration configuration) throws ConfigurationException {
        super(realm, configuration);
    }

    public JWTGroupImpl(DBBroker broker, AbstractRealm realm, int id, String name) throws ConfigurationException {
        super(broker, realm, id, name);
    }

    public JWTGroupImpl(DBBroker broker, AbstractRealm realm, int id, String name, List<Account> managers) throws ConfigurationException {
        super(broker, realm, id, name, managers);
    }
}
