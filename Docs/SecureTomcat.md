# Secure Tomcat using A2A

The project `SecureTomcat` is about how Symantec PAM and its A2A Client can be used to secure some aspects of a Tomcat application server. It will show some advanced usage of the A2A client and how PAM can be used to provide secure access to applications in a Tomcat application server environment. 

If you just want to get started there follow the links and get directly to the details.

- [Tool Chain](/Docs/SecureTomcat-ToolChain.md#section-Tool-chain-and-environment)
- [Source code](/Docs/SecureTomcat-SourceCode.md)
- [Tomcat Configuration](/Docs/SecureTomcat-TomcatConfig.md#section-Configuration)
- [PAM Configuration](/Docs/SecureTomcat-PAMConfig.md)
- [Tomcat Startup](/Docs/SecureTomcat-Startup.md)
- [Encrypted Messages](/Docs/SecureTomcat-Messages.md)
- [Tomcat Management Login](/Docs/SecureTomcat-Login.md#section-Tomcat-Management-Application)


The example used here is showing how to setup Tomcat as a TLS server while still protecting the keystore with the private key without having a keystore password in a configuration file. Other topic covered is how  to secure messages send from a client to the Tomcat server and how to avoid hardcoded passwords for  admins of tomcat. 

Tomcat is setup as application server with inbound HTTPS as the only supported protocol. Tomcat uses a  public/private key-pair and a certificate to identify itself.  

When configuring HTTPS or rather TLS in Tomcat, the private key is typically stored in a Java keystore and access to the private key in a keystore is controlled by a password. The password is for accessing the keystore is kept in a configuration file. This may be sufficient in some environments and insufficient in others. Here it is insufficient and more work is done to protect the keystore. 

In this setup, the password is not stored in the configuration file, but a reference or alias to the target account is used. The A2A client will use the alias and fetch the password from PAM. However, before releasing the password for the keystore, there are some security checks which must be passed first. When using Java and A2A with Symantec PAM, the application registered in PAM is the Java class making a call to the A2A client. The client is calculating a hash of the class and forwarding it to PAM. If the registration and authorization of the script and hash of the script matches, the password is released. At the same time, the class registered is performing additional checks of who is calling the class (the call stack) and if files in a filelist have the correct integrity values. 

This is very similar to the `Python WebServer` example, but different in the sense that the hash of the application is not stored in the source code but in PAM itself. An additional check is the callstack validation used to prevent other application to fetch passwords without authorization. 

In the `SecureTomcat` project there is a sample server and sample client used to send encrypted messages, where the symmetric key used for encryption/decryption is kept in PAM and rotated frequently. 

Finally the `SecureTomcat` project is showing how passwords for logging into the admin console of Tomcat can be stored in PAM, rotated frequently and used without showing it to a user.


# Secure Tomcat startup

This chapter describes how security and integrity of Tomcat is handled during startup of Tomcat. 

## A general Tomcat environment

Before diving into the details, a short outline of the Tomcat environment.

A typical environment for a Tomcat application server is outlined below. It is a Java based environment. The application server, e.g. Tomcat, is running in an OS and the server application is running inside the application server environment. Assuming that the only inbound connection permitted is HTTPS (TLS), the Tomcat server always uses a Java Keystore during startup. If the keystore cannot be opened Tomcat will not listen to the HTTPS port and will not accept incoming requests.

![Tomcat Environment](/Docs/images/SecureTomcat-Environment-1.png)


## Protecting keystore password

Using TLS connections for inbound connections to a Tomcat server will require the Tomcat server having access to a private key and corresponding public X.509 certificate. When a client (e.g. a browser) is connecting to the server, the TLS hand-shake will authenticate the server. For this to work, the Tomcat server must have access to the private key matching the certificate issued to the Tomcat server. The certificate can be self-signed or signed by a trusted certificate authority.

With Tomcat the private key is typically stored in a Java keystore and the password or key needed to unlock the keystore is stored in a configuration file. When looking at the Tomcat documentation, it is always recommended to limit access to the file system of the configuration files and keystore. However, if someone does have access, it is easy to create a clone of the Tomcat server and impersonate the Tomcat server for the real one. The client (e.g. browser) will not see any difference as the certificate and private key is available and will not easily detect that it is a clone or faked Tomcat server. Assuming that the hostname in the certificate is matching the real hostname or when using self-signed certificates.

One way to mitigate the risk is to restrict access to the key required to unlock the keystore. The mechanism described here uses Symantec PAM A2A mechanism, where the password to unlock the keystore is kept in PAM and only released to a Tomcat server, if the environment is authenticated and authorized. 

The A2A mechanism is setup such that it is validating that the server running the A2A client and the script or program using A2A requesting the keystore password. If the script or program is registered and the integrity of the calling application is OK, then the password is released. This is handled by standard A2A integrity validation when releasing a password from PAM. In addition to the A2A mechanism  the Java class used when calling A2A is also performing additional checks of the callstack and validating a filelist and ingtegrity of files in the list. If these chacks are failing, the key for the keystore is not retrieved. 

![Keystore access](/Docs/images/SecureTomcat-KeystoreAccess.png)

The mechanism used is to provide a new Java method `getProperty` and let Tomcat use this method instead of the default method. The `getProperty` is called when Tomcat request the keystore password. When called the integrity of callstack and files is performed.

![Secured Tomcat environment](/Docs/images/SecureTomcat-Environment-2.png)

# Message encryption and decryption

The example is also using JTW mechanism to decrypt messages received from a client. The application is a simple echoApp which will receive an encrypted JWT message, decrypt it and send back the clear text as response. What is special here is that the symmetric key used for encryption/decryption is kept and managed in PAM. 

![Secured Tomcat environment](/Docs/images/SecureTomcat-Messages.png)

## Client sending a message

The client (sender) will fetch a password from PAM using A2A. This passphrase is converted into a symmetric key and used as a key encryption key for the message. A random data encryption key is generated and used to create a HMAC and to encrypt the message. The random data encryption key is encrypted using key encryption key fetched from PAM via an A2A call. Everything is packaged into a JWT message and send to the Tomcat web application using HTTPS.

## Server receiving a message

On the Tomcat server the process is reversed. 

The server (receiver) will fetch a password from PAM using A2A. This passphrase is converted into a symmetric key and used as a key encryption key for the message. With that in hand, the data encryption key is decrypted, then the message and finally the integrity of the message is validated, and the clear text payload is available.


# Tomcat login

Final feature of the Secure Tomcat application is login to the administration application of Tomcat but using a password stored and managed in PAM. 

When users connect to PAM, they can authenticate using local username/password hardcoded in the tomcat-users.xml file. There are mechanisms available to avoid storing user password in clear text, but to keep a hashed value of the password in the configuration file. For many administrators this is a perfect solution. 

The scenario used here the connection to Tomcat web application is established thrpugh PAM and the password is injected directly in the login page. The login page can be either an HTML form or HTTP basic authentiction. Regardless of which protocol is used, PAM is the master of the password and can automatically generate new random passwords and use them when needed. When a user (via PAM) tries to login to an application running in the Tomcat application server, PAM will inject the current password. There is nothing stored in configuration files and the `SecureTomcat` Java package will use A2A and fetch the current password and verify it.

![Secured Tomcat environment](/Docs/images/SecureTomcat-Login.png)

Keep in mind that alternatives exists for user authentication to Tomcat applications.

