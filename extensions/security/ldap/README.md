# LDAP Authentication Realm

To enable LDAP authentication you need to make sure that the file /db/system/security/config.xml content something similar to that below 

```xml
<security-manager xmlns="http://exist-db.org/Configuration" ...
    ...
    <realm id="LDAP">
        <context>
            <url>ldap://directory.mydomain.com:389</url>
            <domain>...</domain>
            <principalPattern>...</principalPattern>            
            <search>
                <base>ou=department,dc=directory,dc=mydomain,dc=com</base>
                <default-username>some-ldap-user</default-username>
                <default-password>some-ldap-password</default-password>
				<account>
                    <search-filter-prefix>(&amp;(objectClass=user)(sAMAccountName=${account-name}))</search-filter-prefix>
                    <search-attribute>...</search-attribute>
                    <metadata-search-attribute>..</metadata-search-attribute>
                    <whitelist><principal>..</principal><principal>..</principal></whitelist>
                    <blacklist><principal>..</principal><principal>..</principal></blacklist>
                </account>
                <group>
                    <search-filter-prefix>(&amp;(objectClass=group)(sAMAccountName=${group-name}))</group-search-filter>
                    <search-attribute>...</search-attribute>
                    <metadata-search-attribute>..</metadata-search-attribute>
                    <whitelist><principal>..</principal><principal>..</principal></whitelist>
                    <blacklist><principal>..</principal><principal>..</principal></blacklist>
                </group>
            </search>
            <transformation><add-group>...</add-group></transformation>
        </context>
    </realm>
	...
</security-manager>
```

* url - the URL to your LDAP directory server.
* base - the LDAP base to use when resolving users and groups

Here is the example from the eXist-db documentation page:


```xml
<realm id="LDAP" version="1.0" principals-are-case-insensitive="true">
    <context>
        <authentication>simple</authentication>
        <url>ldap://ad.server.url.here:389</url>
        <domain>domain.here</domain>
        <search>
            <base>ou=group,dc=ad,dc=organiation-or-what-ever,dc=domain</base>
            <default-username>account@domain.here</default-username>
            <default-password>XXXXXXX</default-password>
            <account>
                <search-filter-prefix>objectClass=user</search-filter-prefix>
                <search-attribute key="objectSid">objectSid</search-attribute>
                <search-attribute key="primaryGroupID">primaryGroupID</search-attribute>
                <search-attribute key="name">sAMAccountName</search-attribute>
                <search-attribute key="dn">distinguishedName</search-attribute>
                <search-attribute key="memberOf">memberOf</search-attribute>
                <metadata-search-attribute key="http://axschema.org/namePerson/first"
                    >givenName</metadata-search-attribute>
                <metadata-search-attribute key="http://axschema.org/contact/email"
                    >mail</metadata-search-attribute>
                <metadata-search-attribute key="http://axschema.org/namePerson/last"
                    >sn</metadata-search-attribute>
                <metadata-search-attribute key="http://axschema.org/namePerson"
                    >name</metadata-search-attribute>
            </account>
            <group>
                <search-filter-prefix>objectClass=group</search-filter-prefix>
                <search-attribute key="member">member</search-attribute>
                <search-attribute key="primaryGroupToken">primaryGroupToken</search-attribute>
                <search-attribute key="objectSid">objectSid</search-attribute>
                <search-attribute key="name">sAMAccountName</search-attribute>
                <search-attribute key="dn">distinguishedName</search-attribute>
                <whitelist>
                    <principal>Domain Users</principal>
                    <principal>Users_GROUP</principal>
                </whitelist>
            </group>
        </search>
        <transformation>
            <add-group>group.users</add-group>
        </transformation>
    </context>
</realm>
```
