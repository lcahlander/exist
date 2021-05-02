# IP Range Authentication Realm

The IPRange Realm is enabled by default in the build configuration file *extensions/security/pom.xml* and include `<module>iprange</module>` in the `<modules/>` element.
To enable IPRange authentication you need to make sure that the file /db/system/security/config.xml content something similar to that below

```xml
<security-manager xmlns="http://exist-db.org/Configuration" ...
	...
    <realm id="IPRange">
    </realm>
	...
</security-manager>
```

For the authentication of a user based on the IP address that the user is from, add accounts into `/db/system/security/iprange/accounts` in the format:

```xml
<account xmlns="http://exist-db.org/Configuration" id="22">
    <group name="emh"/>
    <expired>false</expired>
    <enabled>true</enabled>
    <umask>022</umask>
    <name>emh</name>
    <iprange>
        <start></start>
        <end></end>
    </iprange>
</account>
```

Where start and end have the IP range of where this user is coming from.
