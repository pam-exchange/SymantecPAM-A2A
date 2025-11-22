# Message Encode␤
This example demonstrates handling encrypted messages in Tomcat using two components: **sampleServer** and **sampleClient**.␤
- **sampleServer** is the Tomcat application `echoApp`. It receives an HTTPS POST request containing an encrypted JWT, retrieves the encryption key from PAM, decrypts the message, and returns the plain text.␤
- **sampleClient** retrieves the same encryption key from PAM, encrypts a message, wraps it in a JWT, sends it to `echoApp`, and displays the plain text response.␤

## sampleClient␤
The client is packaged using `ant build` and delivered as a ZIP file. It includes everything except the A2A client. Unpack it and run `sampleClient.bat`.␤
![sampleClient](/Docs/images/SecureTomcat-SampleClient-1.png)␤

## sampleServer – echoApp␤
The `echoApp` application receives the encrypted JWT, retrieves the encryption key from PAM, decrypts the content, and returns the resulting plain text.␤
![sampleServer](/Docs/images/SecureTomcat-SampleClient-2.png)␤

# PAM Setup␤
Detailed configuration steps are provided in the *MessageEncode* section of the PAM setup guide.␤
