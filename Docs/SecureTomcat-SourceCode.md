# Source code

The structure of the source code is as shown below. Above there are source files to create a new keystore and to generate a filelist.

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


The three sub-projects are `SampleClient`, `SampleServer` and `SecureTomcat`. 

## SecureTomcat

This is where the core of securing Tomcat lives. There are three java sources, which is compiled and packaged to `secureTomcat.jar`.

- **PAM.java**  
This source is used to bootstrap security and contains the class registered as an application/script in PAM. It contains the method `getProperty` used when Tomcat is starting. It also contains a method used in Message.java when fetching encryption key used by the sampleClient. In this source the pass phrase or decryption key for accessing the keystore is fetched from PAM using A2A. The PAM class is validating the call stack to ensure that it is only called by Tomcat and is also validating the hash of a filelist. Both hashes are stored in PAM and will only be available if the integrity of the class is validated in PAM.<br><br>
In the PAM.java source code there are some flags which must be set before building the secureTomcat.jar file. Change the flags accordingly.<br><br>
![PAM.java flags](/Docs/images/SecureTomcat-PAM.java.png)<br><br>If `strictChecking` is **false** passwords retrieved from PAM will appear in the logs. If `strictChecking` is **true** passwords will not appear in the logs. Furthermore, the password for the keystore is not released if callstack and filelist integrity validaty is failing (unless they are set to **false**).

- **Message.java**  
This source is used when the `echoApp` in the SampleServer is responding to messages sent from the SampleClient application. The class Message is part of the `secureTomcat.jar` and its integrity is validated indirectly by the PAM class. The Message class uses the PAM method defined in PAM.java. This file is the registered class in PAM and the A2A client is responsible for integrity validation. The file integrity validation has securetomcat.jar as one of the files validated and indirectly the Message.class and all files in echoApp are validated.<br><br>The Message class is receiving an encrypted JWT message from a client and will fetch the encryption key from PAM using the A2A client. When the decryption key is available the JWT message is decrypted and the plain text is returned to the calling application (echoApp). 

- **PAMCredentialHandler.java**  
This source is used when fetching a password for user login to the Tomcat management page. The class PAMCredentialHandler is part of the secureTomcat.jar and its integrity is validated indirectly by the PAM class.<br><br>The method is used when Tomcat authenticate to the management application in Tomcat.


## SampleServer

The EchoApp.java source is an example of an application which fetches a symmetric decryption key, which is updated in PAM. For demonstration purpose the only thing the application does is to decrypt the message received and send back the plain text to the caller (the sampleClient). It will call the an `a2a.getCredential` method from the PAM class above.


## SampleClient

This is a simple client application used to fetch an encryption key from PAM, encrypt the command line message and encode it as a JWT message. The encrypted message is sent to the echoApp, where it is decrypted and the plain text message is returned as response to the HTTPS POST message.<br><br>The application does nothing fancy except showcasing how an encryption key can be fetched from PAM and used to encrypt a message. 

