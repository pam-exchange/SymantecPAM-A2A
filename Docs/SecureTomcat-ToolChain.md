# Tool chain and environment

The following tools are used when building the SecureTomcat libraries and applications.

- OpenJDK Version 25 (build 25+36-3489)
- Apache Ant 1.10.15
- Apache Tomcat 10.1.44
- Symantec PAM 4.2.3
- Symantec PAM A2A client 4.12.3.134 (from PAM 4.2.3)

Clone or download the project files. Create a **./lib** directory and download/copy the external libraries to this folder. The external libraries used are:

- jose4j-0.9.6.jar
- json-simple-1.1.1.jar
- sl4j-api-2.0.12.jar
- sl4j-simple-2.0.12.jar

In addition to these external libraries `SecureTomcat` uses libraries from Tomcat and from the A2A client. See the ant build files for details.

For compilation it is required to have all components installed on the build server. You can probably copy the necessary libraries and .so/.dll files, but it is easier to have the environment installed on the build platform.

Note: It is possible to use Oracle Java 17. However, you will need to use a previous version of A2A Client from PAM 4.2.0, and tweak java.security.

## ant - build.properties

There is a configuration file `./build.properties`, which is describing some top-level properties. Most important are `cspm.home` and `catalina.home`. These properties describes where external libraries are found.

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

The example integration wiht Apache Tomcat uses Java. It is possible to use A2A functionality from Java applications, but the version of Java and version of A2A Client can be a bit tricky to match correctly. The reason is A2A Client library `cwjcafips.jar`. It is loaded when a Java application uses the A2A functionality. The file is digitally signed and Java will verify the signature before releasing passwords to the application. The original A2A client goes way back to before 2007 and is quite old. It was digitally signed with algorithms, which are outdated by todays crypto standards.

The digital signature on the A2A client was changed with PAM 4.2.3 (possible 4.2.1 and 4.2.2) and should work with modern Java environments. A2A Client in 4.2.0 and earlier uses the outdated algorithms. You can make them with with Java, but it does require a modification of the `java.security` file.  

It is strongly recommended to test the Java run-time environment and A2A Client version, as different versions of either may or may not work as expected. 

## OpenJDK version 25

OpenJDK version 25 works out-of-the-box wiht A2A Client 4.12.3.134 (PAM 4.2.3) without tweaks or changes to Java configuration files.
This is the Java version used to build and run SecureTomcat libraries and applications.

## Oracle Java 17

Oracle Java 17.0.16 is not compatible with A2A Client 4.12.3.134 (from PAM 4.2.3), and it will not release the password to the application. You will get an OK that it is delivered from PAM to A2A Client to application, but you will get "null" as password. 

If you are using Java 17.0.16 you must revert back to A2A client 4.12.3.123 (from PAM 4.2.0). If so, you also need to update the `java.security` file.

The A2A client version 4.12.3.123 (from PAM 4.2.0) is signed using an algorithm, which by default is no longer accepted in Java. It means that it is necessary to disable the algorithm check in Java. The A2A client (v4.12.3.134) available with PAM 4.2.3 is signed by newer algorithms, but the RootCA for the signature certiifcate is not accepted by Oracle Java and it does not work correctly.

Disabling algorithm check in Java is a security risk and must be considered if this pose a significiant security threat to the environment. The necessary change to make A2A Client (version 4.12.3.x) operational using Java 17 is to edit the file `<JDK17-path>\conf\security\java.security`. Locate the setting `jdk.jar.disabledAlgorithms` and change it such that the property is inactive. It is sufficient to remove the date restriction.

Other versions of A2A Client and Oracle Java may behave differently.

