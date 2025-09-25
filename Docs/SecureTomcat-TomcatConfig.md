# Tomcat Configuration


## Keystore

The private key required by the TLS server is stored in a Java keystore. Password to the keystore is initially known when the key-pair is generated. However, the password for accessing the keystore is managed and rotated by PAM.  
See the repo [SymantecPAM-KeystoreFile](https://github.com/pam-exchange/SymantecPAM-KeystoreFile) for details.

Generating the key-pair and the keystore can be done in many ways. Here is an example generating a key-pair and a self-signed certificate.

```
set BASENAME=Tomcat-WebServer
set PASSWORD=DjsQPjMrFP5zQrKXAsED66LTenz3l3xiPojk572BXBDc6HsXoJL9J5gzLW33Pevt

set CERTFILE=%BASENAME%.crt
set KEYSTORE=%BASENAME%.keystore
set ALIAS=%BASENAME%

keytool -genkey -alias %ALIAS% -keyalg RSA -keysize 4096 -dname "CN=keystore, OU=PAM Test, O=PAM-Exchange, C=CH" -keypass %PASSWORD% -storepass %PASSWORD% -keystore %KEYSTORE% -validity 3650
keytool -importkeystore -srckeystore %KEYSTORE% -srcstorepass %PASSWORD% -destkeystore %KEYSTORE% -deststorepass %PASSWORD% -deststoretype pkcs12 
keytool -exportcert -alias %ALIAS% -keystore %KEYSTORE% -keypass %PASSWORD% -storepass %PASSWORD% -rfc -file %CERTFILE%
```

Run the commands from a Windows command prompt and the keystore is generated. In the setup here the keystore file is copied to the directory `$CATALINA_HOME/conf/Catalina-Keystore`. This directory is configured as a network share allowing the KeystoreFile connector access and ability to change password of the keystore file.

## Catalina - catalina.bat

Additional Java options are required when starting Tomcat. Edit the file `$CATALINA_HOME/bin/catalina.bat` and add the following to the file (around line 352).

```
set JAVA_OPTS=--enable-native-access=ALL-UNNAMED -Djava.library.path=c:\cspm\cloakware\cspmclient\lib %JAVA_OPTS%
```

![catalina.bat](/Docs/images/SecureTomcat-catalina.bat.png)


## Catalina - catalina.properties

A key property is the property `org.apache.tomcat.util.digester.PROPERTY_SOURCE`, which tells Tomcat which method is used when fetching properties. This is exactly the class implementing the `getProperty` method. Other properties used are PAM TargetAccount aliases and a filename and a time-to-live for encrypted messages. 

Edit the `$CATALINA_HOME/conf/catalina.properties` file and add the following to the end:

```
# PAM Access Tomcat Keystore
org.apache.tomcat.util.digester.PROPERTY_SOURCE=ch.pam_exchange.securetomcat.PAM

pam.keystore.alias=SecureTomcat-keystore
pam.callstack.alias=SecureTomcat-callstack
pam.filelist.alias=SecureTomcat-filelist
pam.filelist.name=c:/opt/apache-tomcat-10.1.44/conf/filelist.sha256
pam.jwt.alias=SecureTomcat-json-web-token
pam.jwt.time.window=500
```

- The property `org.apache.tomcat...` is a reference to the method used to override the default Tomcat `getProperty` method. The method is implemented in the library file `secureTomcat.jar`.
- The `pam.keystore.alias` is the alias for the PAM account holding the password for the keystore.
- The property `pam.callstack.alias` is the alias for the PAM account holding the callstack hash.
- The property `pam.filelist.alias` is the alias for the PAM account holding the filelist hash.
- The property `pam.filelist.name` is the filename where the filelist is found. Keep in mind tha backslash must be written as “\\”.
- The property `pam.jwt.alias` is the alias for the PAM account holding the JWT encryption passphrase.


## Catalina - server.xml

The file `$CATALINA_HOME/conf/server.xml` configure Tomcat to enable HTTPS inbound connection. This is where the password for a Java keystore containing the TLS private key is required. When fetching the password for the keystore the new `getProperty` method is called and magic happens. The server.xml should not have other HTTPS definitions on diffrent ports configured.

Enabling HTTPS on port 8443 can be configured like this:

```
<!-- Define a SSL Coyote HTTP/1.1 Connector on port 8443 -->
<Connector
  protocol="org.apache.coyote.http11.Http11NioProtocol"
  port="8443"
  maxThreads="200"
  scheme="https" 
  secure="true" 
  SSLEnabled="true"
  clientAuth="false" 
  >
  <SSLHostConfig protocols="TLSv1.2,TLSv1.3">
    <Certificate
      certificateKeystoreFile="${catalina.home}/conf/Catalina-Keystore/Tomcat-WebServer.keystore"
      certificateKeystorePassword="${pam.keystore.alias}"
      certificateKeyAlias="Tomcat-WebServer"
      type="RSA"
    />
  </SSLHostConfig>
</Connector>
```

The key properties are `certificateKeystorePassword="${pam.keystore.alias}"` along with the setting `org.apache.tomcat.util.digester.PROPERTY_SOURCE` in `catalina.properties`. The combination of the two will direct the call to **getProperty** to the code in the SecureTomcat library.


## Catalina - logging.properties

Add logging to the `$CATALINA_HOME/conf/logging.properties` file. The logging can be to the console or to catalina.log file.

```
#
# Target Connectors
#
ch.pam_exchange.securetomcat.level= FINE
ch.pam_exchange.securetomcat.handlers= java.util.logging.ConsoleHandler
#ch.pam_exchange.securetomcat.handlers= 1catalina.org.apache.juli.AsyncFileHandler

ch.pam_exchange.pam_tc.filecopy.api.level = FINE
ch.pam_exchange.pam_tc.filecopy.api.handlers= java.util.logging.ConsoleHandler
ch.pam_exchange.pam_tc.keystorefile.api.level = FINE
ch.pam_exchange.pam_tc.keystorefile.api.handlers= java.util.logging.ConsoleHandler
```

Log levels and handlers for classes **filecopy** and **keystore** are enabling logging from the keystore target connector.

## SecureTomcat - pam.filelist

The filelist is a list of files included in the integrity validation. It **must** include the filelist itself and the `secureTomcat.jar` file. If they are not found in the filelist it is seen as invalid. Other important files are `catalina.properties` and `server.xml`. It may include other configuration files, executables and libraries. The location and filename of the filelist is configured as the property`pam.filelist.name` in `catalina.properties`. 

The scripts `generate-filelist` can be used to expand a filelist to individual files in directories. When the filelist is generated it will contain a hash for each individual file. The hashes of the individual files are ignored in SecureTomcat and it can be deleted.

Filelist example

```
C:/opt/apache-tomcat-10.1.44/conf/SecureTomcat.filelist
C:/opt/apache-tomcat-10.1.44/lib/secureTomcat.jar

C:/opt/apache-tomcat-10.1.44/conf/catalina.policy
C:/opt/apache-tomcat-10.1.44/conf/catalina.properties
C:/opt/apache-tomcat-10.1.44/conf/context.xml
C:/opt/apache-tomcat-10.1.44/conf/server.xml
C:/opt/apache-tomcat-10.1.44/conf/tomcat-users.xml

C:/opt/apache-tomcat-10.1.44/bin/*.jar
C:/opt/apache-tomcat-10.1.44/bin/startup.bat
C:/opt/apache-tomcat-10.1.44/bin/catalina.bat

C:/opt/apache-tomcat-10.1.44/lib/catalina.jar
C:/opt/apache-tomcat-10.1.44/lib/tomcat-util.jar

C:/opt/apache-tomcat-10.1.44/lib/cpaspiffadaptor64.dll
C:/opt/apache-tomcat-10.1.44/lib/cspmclient.jar
C:/opt/apache-tomcat-10.1.44/lib/cspminterface64.dll
C:/opt/apache-tomcat-10.1.44/lib/cwjcafips.dll
C:/opt/apache-tomcat-10.1.44/lib/cwjcafips.jar

C:/opt/apache-tomcat-10.1.44/lib/jose4j-0.9.6.jar
C:/opt/apache-tomcat-10.1.44/lib/json-simple-1.1.1.jar
C:/opt/apache-tomcat-10.1.44/lib/slf4j-api-2.0.12.jar
C:/opt/apache-tomcat-10.1.44/lib/slf4j-simple-2.0.12.jar

C:/opt/apache-tomcat-10.1.44/webapps/echoApp.war
C:/opt/apache-tomcat-10.1.44/webapps/echoApp/
C:/opt/apache-tomcat-10.1.44/webapps/manager/

C:/opt/OpenJDK/jdk-25/conf/security/java.security
```

After running the `generate-filelist` using this as template, the files verified as available and directories are expanded to include all files. The output filename for the final filelist ca be `SecureTomcat.filelist` or what is configured in the property file. If using the Powershell script, the option `-NoHash` will just validate filenames and expand directories.

The final filelist must be copied to the location and name as specified in the catalina property `pam.filelist.name`.


## External library files

External library files (seen from Tomcat) needed are:

- cpaspiffadaptor64.dll
- cspmclient.jar
- cspminterface64.dll
- cwjcafips.dll
- cwjcafips.jar
- jose4j-0.9.6.jar
- json-simple-1.1.1.jar
- slf4j-api-2.0.12.jar
- slf4j-simple-2.0.12.jar

These are A2A client libraries and libraries from external sources. They are found in A2A Client installation path and in Maven repositories. The files can be copied manually to `$CATALINA_HOME/lib` or will be copied when running `ant deploy`.


# Tomcat Management Console

The aim with the setup here is to have the account password for the tomcat management console stored and rotated by PAM. Furthermore, when setting up PAM users having permissions to login to the management console can do this directly from their PAM Access page without knowing the password.

To archive this some changes are required in tomcat configuration files.

## server.xml

When using PAM to access the management console of Tomcat an additional change in **server.xml** is required.
Locate the section with `<Engine name="Catalina" ...`. Add or replace the **CredentialHandler** for the **UserDatabaseRealm**.

```
<Realm className="org.apache.catalina.realm.UserDatabaseRealm" resourceName="UserDatabase">
     <CredentialHandler className="ch.pam_exchange.securetomcat.PAMCredentialHandler"/>
</Realm>
```
The PAMCredentialHandler method is part of the `SecureTomcat.jar` library.


## tomcat-user.xml

This update is only required if PAM is used to automate loginthe management console of Tomcat.

Edit the file `$CATALINA_HOME/conf/tomcat-users.xml`. Change teh username and password such that the password matches the account alias defined in PAM. The value used here is `SecureTomcat-Administrator`. The credential handler will use the alias and fetch the real password from PAM.

Edit the file `$CATALINA_HOME/conf/tomcat-users.xml`.

```
<tomcat-users xmlns="http://tomcat.apache.org/xml"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://tomcat.apache.org/xml tomcat-users.xsd"
              version="1.0">
  <role rolename="manager-gui"/>
  <user username="tomcat" password="SecureTomcat-Administrator" roles="manager-gui"/>
</tomcat-users>
```

The password `SecureTomcat-Administrator` is the PAM alias for the target account 'tomcat'.


## context.xml

Default setting when installing Tomcat is that the manager application can only be called from localhost. 

To change this, edit the file `$CATALINA_HOME/webapps/manager/META-INF/context.xml`. Locate the section `<Context antiResourceLocking ...` and change the allowed addresses to `.*` or a more restrictive access. 

![context.xml](/Docs/images/SecureTomcat-Context.png)

