# JSON Web Token Authentication Realm

To enable JWT authentication you need to make sure that the file /db/system/security/config.xml content something similar to that below

```
<security-manager xmlns="http://exist-db.org/Configuration" ...
	...
    <realm id="JWT">
        <context>
            <domain>...</domain>
            <account>
                <property>...</property>
                <metadata-property>..</metadata-property>
                <whitelist><principal>..</principal><principal>..</principal></whitelist>
                <blacklist><principal>..</principal><principal>..</principal></blacklist>
            </account>
            <group>
                <claim>...</claim>
                <property>...</property>
                <metadata-property>..</metadata-property>
                <dba><principal>..</principal><principal>..</principal></dba>
                <whitelist><principal>..</principal><principal>..</principal></whitelist>
                <blacklist><principal>..</principal><principal>..</principal></blacklist>
            </group>
        </context>
    </realm>
	...
</security-manager>
```
