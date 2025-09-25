# Message Encode

As an example showing how encrypted messages can be handled in Tomcat, there are two components available. It is a sampleServer and a sampleClient. The sampleServer is the Tomcat application `echoApp`. The application will receive a HTTPS POST message with an encrypted JWT message. When received it will fetch the encryption key in PAM, decrypt the message and return the plain text as a response to the caller. 

To generate the encrypted nessage the sampleClient is fetching the encryption key from PAM, encrypt a message, package it into a JWT message and send it to the `echoApp` application. The plain text response is shown to the user.

## sampleClient

The sampleClient is packaged as a zip-file using `ant build` (top-level). The zip-file will contain all necessary files except the A2A client itself. Unpack the zip-file to a directory of your choice and run the `sampleClient.bat` script. 

![sampleClient](/Docs/images/SecureTomcat-SampleClient-1.png)

## sampleServer - echoApp

In Tomcat the application `echoApp` will receive the encrypted message, fetch the encryption key, decrypt the message and return the plain text message to the sampleClient.

![sampleServer](/Docs/images/SecureTomcat-SampleClient-2.png)


# PAM setup

This is described in details in the section [MessageEncode](./SecureTomcat-PAMConfig.md#MessageEncode).
