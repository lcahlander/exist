# LDAP Authentication Realm

The LDAP Realm is enabled by default in the build configuration file *extensions/security/pom.xml* and include `<module>ldap</module>` in the `<modules/>` element.
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
* The `default-username` and `default-password` elements are used to communicate with the LDAP server if a non-LDAP user requests information from LDAP server.
* The `search-*` elements are mapping for names.
* The `metadata-search-attribute` elements are used for mapping LDAP account metadata onto eXist-db account metadata.
* The `whitelist` element contains the allowed groups for authentication. The `blacklist` element contains groups that are not allowed.
* The `transformation` element contains actions to be performed after first authentication.

Here is an example:

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

Here is a sample LDAP account entry in
`/db/system/security/LDAP/accounts/u113896@example.com.xml`:

```xml
<account xmlns="http://exist-db.org/Configuration" id="14">
    <!--<password></password>-->
    <!--<digestPassword></digestPassword>-->
    <group name="domain users@example.com"/>
    <group name="mm-user"/>
    <group name="mm-modeler"/>
    <group name="dba"/>
    <group name="mm-reconcile"/>
    <expired>false</expired>
    <enabled>true</enabled>
    <umask>022</umask>
    <metadata key="http://axschema.org/namePerson/last">Cahlander</metadata>
    <metadata key="http://axschema.org/namePerson/first">Loren</metadata>
    <metadata key="http://axschema.org/namePerson">Loren Cahlander</metadata>
    <metadata key="http://axschema.org/contact/email">Loren.Cahlander@example.com</metadata>
    <name>u113896@example.com</name>
</account>
```

Here is a sample LDAP group entry in `/db/system/security/LDAP/groups/domain user@example.com.xml`:

```xml
<group xmlns="http://exist-db.org/Configuration" id="16">
    <name>domain users@example.com</name>
</group>
```
