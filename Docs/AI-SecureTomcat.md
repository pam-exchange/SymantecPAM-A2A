# Secure Tomcat using A2A

The `SecureTomcat` project demonstrates how Symantec PAM and its A2A Client can secure critical parts of a Tomcat application server.
It highlights advanced A2A usage and shows how PAM can provide secure access to Tomcat applications without storing passwords in configuration files.

If you want to jump directly to key sections:
- [Tool Chain](/Docs/SecureTomcat-ToolChain.md#section-Tool-chain-and-environment)
- [Source Code](/Docs/SecureTomcat-SourceCode.md)
- [Tomcat Configuration](/Docs/SecureTomcat-TomcatConfig.md#section-Configuration)
- [PAM Configuration](/Docs/SecureTomcat-PAMConfig.md)
- [Tomcat Startup](/Docs/SecureTomcat-Startup.md)
- [Encrypted Messages](/Docs/SecureTomcat-Messages.md)
- [Tomcat Management Login](/Docs/SecureTomcat-Login.md#section-Tomcat-Management-Application)

The project shows how to run Tomcat as a TLS server while protecting the keystore password, secure client-to-server encrypted messages, and avoid hardcoded admin passwords.

Tomcat is configured to accept only HTTPS connections. It uses a public/private key pair and a certificate for identification.

Normally, the private key is stored in a Java keystore and unlocked with a password stored in a configuration file. This may be acceptable in some environments, but here the goal is to eliminate stored passwords entirely.

Instead of storing the keystore password, a PAM Target Account alias is used. The A2A client retrieves the password only after validating the environment.
When Java calls the A2A client, PAM validates the registered Java class and checks its integrity hash. The class itself also performs additional checks: call-stack validation and integrity verification of a configured file list. Only if all checks pass is the keystore password released.

This is similar to the `Python WebServer` example, but here the integrity hash is stored in PAM, and extra call-stack validation prevents unauthorized applications from requesting the password.

The project also includes a sample client and server demonstrating encrypted messaging using a symmetric key stored and rotated in PAM.

Finally, the project shows how Tomcat admin passwords can be stored and rotated in PAM and injected dynamically at login, eliminating the need to store passwords in configuration files.

# Secure Tomcat Startup

This section describes how Tomcat security and integrity checks are performed during startup.

## A general Tomcat environment

A typical Tomcat environment is Java-based. The application server runs on an operating system, and applications run within Tomcat.
If HTTPS is the only inbound protocol, Tomcat must open its keystore during startup. If the keystore cannot be opened, Tomcat will not listen on the HTTPS port.

![Tomcat Environment](/Docs/images/SecureTomcat-Environment-1.png)

## Protecting the keystore password

For TLS, Tomcat requires access to its private key and X.509 certificate. During the TLS handshake, clients authenticate the server using this certificate.

The private key is normally stored in a Java keystore, and the password to unlock it is stored in a configuration file. Even with restricted filesystem access, someone with access could clone the server and impersonate it.

To reduce this risk, the keystore password is stored in PAM and fetched only if the Tomcat environment is authenticated and authorized.

The A2A workflow validates:
- the host running the A2A client,
- the Java class requesting the password, and
- the integrity of the class file.

The Java class also validates the call stack and verifies integrity of files in a defined file list. If any check fails, the keystore password is not retrieved.

![Keystore access](/Docs/images/SecureTomcat-KeystoreAccess.png)

A custom Java method, `getProperty`, is introduced and used by Tomcat instead of the default property lookup. Tomcat calls `getProperty` when retrieving the keystore password, triggering the integrity and call-stack checks.

![Secured Tomcat environment](/Docs/images/SecureTomcat-Environment-2.png)

# Message Encryption and Decryption

The example also demonstrates decrypting JWT messages using a symmetric key stored and managed in PAM. The sample application (`echoApp`) receives an encrypted JWT, decrypts it, validates integrity, and returns the plaintext.

![Secured Tomcat environment](/Docs/images/SecureTomcat-Messages.png)

## Client sending a message

1. The client fetches a password from PAM using A2A.
2. The password is converted into a symmetric Key-Encryption Key (KEK).
3. A random Data-Encryption Key (DEK) is generated.
4. The DEK creates an HMAC and encrypts the message.
5. The DEK is encrypted with the KEK.
6. All components are packaged into a JWT and sent to the Tomcat server over HTTPS.

## Server receiving a message

1. Tomcat fetches the same PAM password using A2A and converts it into the KEK.
2. The DEK is decrypted.
3. The message is decrypted.
4. Integrity is verified, and the plaintext payload becomes available.

# Tomcat Login

The project also shows how to log into the Tomcat Manager application using passwords stored and rotated in PAM.

Typically, Tomcat users authenticate using credentials stored in `tomcat-users.xml`, possibly hashed. This works for many administrators, but still requires storing password data.

In this setup, the connection to Tomcat is made through PAM, which injects the password directly into the login flow—whether the login uses an HTML form or HTTP Basic Authentication.

PAM manages and rotates the password automatically. The `SecureTomcat` Java module retrieves the password via A2A and validates it without storing anything in configuration files.

![Secured Tomcat environment](/Docs/images/SecureTomcat-Login.png)

Remember that other authentication methods for Tomcat applications also exist.
