package org.exist.security.realm.jwt;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationFieldAsElement;

public class JWTContextFactory implements Configurable {

    @ConfigurationFieldAsElement("domain")
    protected String domain = null;

    private Configuration configuration = null;

    public JWTContextFactory(final Configuration config) {
        configuration = Configurator.configure(this, config);
    }

    public String getDomain() {
        return domain;
    }

    // configurable methods
    @Override
    public boolean isConfigured() {
        return (configuration != null);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }
}
