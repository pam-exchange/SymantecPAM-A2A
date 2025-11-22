# Tomcat Configuration

## Keystore

The private key required by the TLS server is stored in a Java keystore.  
The keystore password is initially known when the key pair is generated; however, the password used to access the keystore is later managed and rotated by PAM.  
See the repository [SymantecPAM-KeystoreFile](https://github.com/pam-exchange/SymantecPAM-KeystoreFile) for details.

A key pair and a self-signed certificate can be generated in many ways.  
The following example shows how to generate both using the Windows command prompt:



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

The file `$CATALINA_HOME/conf/server.xml` configures Tomcat to enable HTTPS inbound connections.  
This is also the location where Tomcat needs access to the Java keystore that contains the TLS private key.  
When Tomcat resolves the keystore password, it invokes the custom `getProperty` method, which retrieves the password through the SecureTomcat integration.

Ensure that no additional HTTPS connectors are defined on other ports, as multiple conflicting HTTPS configurations may cause startup failures.

HTTPS on port 8443 can be enabled with a configuration similar to the following:


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

The filelist is a list of files included in the integrity validation process.  
It **must** include both the filelist filename itself and the `secureTomcat.jar` file.  
If either of these entries is missing, the filelist is considered invalid.  
Other important files that should typically be included are `catalina.properties` and `server.xml`.  
Additional configuration files, executables, and libraries may also be added as needed.

The location and filename of the filelist are defined using the property `pam.filelist.name` in `catalina.properties`.

The script `generate-filelist` can be used to expand directories into a list of individual files.  
When the filelist is generated, it will contain a hash for each file.  
These per-file hashes are not used by SecureTomcat and may be removed if desired.

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

After running `generate-filelist` with this template, the script verifies that the listed files exist and expands any directories to include all files within them.  
The output filename for the final filelist can be `SecureTomcat.filelist` or any name specified in the configuration properties.  
If you use the PowerShell version of the script, the `-NoHash` option will expand directories and validate filenames without generating hashes.

The final filelist must then be copied to the location and filename specified by the `pam.filelist.name` property in `catalina.properties`.


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

These libraries include A2A client components as well as third-party dependencies.  
They are available in the A2A Client installation directory and in Maven repositories.  
The required files can either be copied manually into `$CATALINA_HOME/lib` or will be copied automatically when running `ant deploy`.

# Tomcat Management Console

The objective of this setup is to ensure that the account password for the Tomcat Management Console is stored and rotated by PAM.  
In addition, PAM users who have permission to access the management console can sign in directly from their PAM Access page without needing to know the actual password.

To achieve this, several modifications to Tomcat configuration files are required.

## server.xml

When enabling PAM access to the Tomcat Management Console, an additional modification to **server.xml** is necessary.  
Locate the section containing `<Engine name="Catalina" ...` and add or replace the **CredentialHandler** for the **UserDatabaseRealm**:

```
<Realm className="org.apache.catalina.realm.UserDatabaseRealm" resourceName="UserDatabase">
     <CredentialHandler className="ch.pam_exchange.securetomcat.PAMCredentialHandler"/>
</Realm>
```

The PAMCredentialHandler class is provided by the `SecureTomcat.jar` library.

## tomcat-user.xml

This update is only required when PAM is used to automate login to the Tomcat Management Console.

Edit the file `$CATALINA_HOME/conf/tomcat-users.xml` and modify the username and password so that the password field contains the PAM account alias.  
In this example, the alias is `SecureTomcat-Administrator`.  
The credential handler will use this alias to retrieve the actual password from PAM.

Edit `$CATALINA_HOME/conf/tomcat-users.xml` as follows:

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

By default, a fresh Tomcat installation restricts access to the Manager application so that it can only be accessed from `localhost`.

To modify this behavior, edit the file: `$CATALINA_HOME/webapps/manager/META-INF/context.xml`.  
Locate the `<Context antiResourceLocking ...>` section and update the allowed address pattern to `.*` (allow all) or to a more restrictive, controlled pattern that matches your security requirements.

![context.xml](/Docs/images/SecureTomcat-Context.png)

