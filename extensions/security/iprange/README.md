# IP Range Authentication Realm

To enable IPRange authentication you need to make sure that the file /db/system/security/config.xml content something similar to that below

```
<security-manager xmlns="http://exist-db.org/Configuration" ...
	...
    <realm id="IPRange">
    </realm>
	...
</security-manager>
```
