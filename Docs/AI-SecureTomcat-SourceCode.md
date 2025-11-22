# Source code␤
The project contains three sub-projects — **SampleClient**, **SampleServer**, and **SecureTomcat** — plus helper files for generating a keystore and a file list.␤

```
Tomcat-WebServer
│   build.properties
│   build.xml
│   filelist
│   SecureTomcat.filelist
│
├───lib
│       jose4j-0.9.6.jar
│       json-simple-1.1.1.jar
│       slf4j-api-2.0.12.jar
│       slf4j-simple-2.0.12.jar
│
├───SampleClient
│   │   build.properties
│   │   build.xml
│   │   sampleClient.bat
│   │
│   └───src
│           MessageEncode.java
│
├───SampleServer
│   │   build.properties
│   │   build.xml
│   │
│   └───src
│       └───ch
│           └───pam_exchange
│               └───securetomcat
│                   └───echoapp
│                           EchoApp.java
│
└───SecureTomcat
	│   build.properties
	│   build.xml
	│
	└───src
		└───ch
			└───pam_exchange
				└───securetomcat
						Message.java
						PAM.java
						PAMCredentialHandler.java
```


# SecureTomcat␤
This module contains the core security logic and is compiled into `secureTomcat.jar`. It includes three main classes:␤

- **PAM.java**  
  Provides the bootstrap for security. It contains:  
  - the class registered in PAM,  
  - `getProperty`, used during Tomcat startup to retrieve the keystore password,  
  - methods used by `Message.java` to obtain encryption keys.  
  The class validates the call stack and filelist integrity before PAM releases credentials. Both hashes are stored in PAM.  
  Before building, adjust the configuration flags:  
  ![PAM.java flags](/Docs/images/SecureTomcat-PAM.java.png)  
  - `strictChecking=false` → passwords may appear in logs  
  - `strictChecking=true` → passwords are hidden and keystore access is blocked if integrity checks fail  

- **Message.java**  
  Used by `echoApp` to process encrypted JWTs. The class:  
  - retrieves the encryption key from PAM via the PAM class,  
  - decrypts the JWT message,  
  - returns the plaintext to the caller.  
  Its integrity is enforced indirectly because `secureTomcat.jar` is part of the validated file list.

- **PAMCredentialHandler.java**  
  Handles retrieval of credentials for Tomcat’s management console login. Its integrity is checked indirectly through the PAM class.

# SampleServer␤
`EchoApp.java` is a minimal example that retrieves the symmetric key from PAM, decrypts the received JWT message, and returns the plaintext. It uses the `a2a.getCredential` method exposed by the PAM class.

# SampleClient␤
A simple command-line client that:  
- fetches an encryption key from PAM,  
- encrypts the input message,  
- wraps it in a JWT,  
- sends it to `echoApp` over HTTPS,  
- prints the decrypted response.  

It serves purely as a demonstration of fetching a key from PAM and encrypting a message.␤
