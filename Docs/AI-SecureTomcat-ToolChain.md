# Tool Chain and Environment

The following tools are used when building the SecureTomcat libraries and applications:

- OpenJDK Version 25 (build 25+36-3489)
- Apache Ant 1.10.15
- Apache Tomcat 10.1.44
- Symantec PAM 4.2.3
- Symantec PAM A2A Client 4.12.3.134 (from PAM 4.2.3)

Clone or download the project files.  
Create a `./lib` directory and place the required external libraries in this folder.  
The external libraries used are:

- jose4j-0.9.6.jar  
- json-simple-1.1.1.jar  
- slf4j-api-2.0.12.jar  
- slf4j-simple-2.0.12.jar  

In addition to these third-party libraries, **SecureTomcat** depends on libraries from Tomcat and from the A2A Client.  
See the Ant build scripts for details on all required components.

To compile the project, all components must be available on the build server.  
You can manually copy the necessary JARs and native `.so`/`.dll` files, but installing the full environments is usually easier and less error-prone.

**Note:**  
Oracle Java 17 can be used, but you must use the older A2A Client from PAM 4.2.0 and modify the `java.security` file accordingly.

## ant – build.properties

The configuration file `./build.properties` defines several top-level properties.  
The most important are:

- `cspm.home`  
- `catalina.home`

These properties specify the locations where external libraries and components are located.

```
spm.home=c:/cspm/cloakware/cspmclient
catalina.home=c:/opt/apache-tomcat-10.1.44

version=2.6.2

compile.debug=true
compile.deprecation=true
compile.optimize=true
compile.target=25
compile.source=25
compile.compiler=javac10+
```

To compile, build and package all project files run the command `ant build`. Finally, run `and deploy` to publish and push files to directories (./lib and ./webapps) in `$CATALINA_HOME`.

## SecureTomcat - PAM.java

When starting Tomcat and the SecureTomcat library it can operate in `strict` or `detect and ignore` mode. This is controlled in the source code file `ch/pam_exchange/securetomcat/PAM.java`. In the beginning of the file there are three constants defined. 

```
private static final Boolean verifyCallstack = true;
private static final Boolean verifyFilelist = true;
private static final Boolean strictChecking = true;
```

The `verifyCallstack` will control if callstack validation is performed.
The `verifyFilelist` will control if integrity of files in a filelist is performed.
The `strictChecking`is controlling if incorrect hashes (expected and actual) will prevent requesting the keystore password from PAM. If set to **false** a mismatch of hashes is visible in the catalina.log file, but otherwise ignored. Passwords retrieved from PAM are also visible in the log file. When set to **true** both the callstack and file hashes must match before requesting the keystore password. Also when set to **true** keystore password will not be visible in the log file.

These settings are fixed when the source is compiled and cannot be changed at runtime.

## Building and deploy

From the top project file `./SecureTomcat` run `ant build`. This will compile and package all files. 

Run `ant deploy` to build and deploy the files to the Apache Tomcat directories. This will copy the file `SecureTomcat.jar` and necessary run-time files from A2A Client to `$CATALINA_HOME/lib` and will copy the sampleServer application `echoApp.jar` to `$CATALINE_HOME/webapps`. It will also compule and package a zip file in the `sampleClient` directory. The zip file contains a client application for sending encrypted messages to the `echoApp`.

Building and deploying the libraries and applications will not make any changes to CATALINA configuration files. 


# A2A Client and Java

The example integration with Apache Tomcat uses Java.  
It is possible to use A2A functionality directly from Java applications, but matching the correct Java version with the correct A2A Client version can be challenging.  
The root cause is the A2A Client library `cwjcafips.jar`. It is loaded whenever a Java application invokes A2A functionality. This JAR is digitally signed, and Java must successfully verify the signature before the application is allowed to receive passwords.

The original A2A Client (dating back before 2007) was signed using algorithms that are considered obsolete by modern cryptographic standards.  
The signature was updated in PAM 4.2.3 (and partially in 4.2.1 / 4.2.2), making those A2A Client versions compatible with current Java runtimes.  
Versions from PAM 4.2.0 and earlier still use outdated signing algorithms. They can be used with Java, but only if the `java.security` configuration is modified to relax algorithm restrictions.

It is **strongly recommended** to test combinations of Java runtime versions and A2A Client versions, as mismatches may prevent password retrieval or cause inconsistent behavior.

## OpenJDK version 25

OpenJDK 25 works out-of-the-box with A2A Client 4.12.3.134 (PAM 4.2.3) without any tweaks or changes to Java configuration files.  
SecureTomcat libraries and applications are built and tested using this Java version.

## Oracle Java 17

Oracle Java 17.0.16 is **not** compatible with A2A Client 4.12.3.134 (from PAM 4.2.3).  
Although PAM reports that the password was delivered to the A2A Client and then to the application, the Java application receives `null`.

To use Java 17.0.16, you must revert to A2A Client 4.12.3.123 (from PAM 4.2.0).  
However, this version is signed using an outdated algorithm that modern Java releases block by default.  
As a result, you must update the `java.security` file to relax the algorithm restrictions.

A2A Client 4.12.3.134 (PAM 4.2.3) uses newer signing algorithms, but the Root CA for the certificate is not trusted by Oracle Java, which prevents proper operation.

Disabling algorithm validation in Java introduces a security risk and must be evaluated carefully based on the environment.  
To enable A2A Client 4.12.3.x with Oracle Java 17, edit the file `<JDK17-path>\conf\security\java.security`
Locate the property `jdk.jar.disabledAlgorithms` and modify it so that the restriction is inactive.  
Removing the date constraint is typically sufficient.

Other combinations of A2A Client and Oracle Java versions may behave differently, and testing is recommended.

