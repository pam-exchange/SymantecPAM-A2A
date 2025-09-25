# SymantecPAM A2A

This repository is about using Symantec PAM Application to Application setup in different ways. The Symantec PAM uses an active A2A client, which can cache fetched credentials locally allowing faster response times and resiliance if there are network outages. 

Jump directly to project details:

- [Database-A2A](/Docs/Database-A2A.md)
- [Python-WebServer](/Docs/Python-WebServer.md)
- [SecureTomcat-WebServer](/Docs/SecureTomcat.md)

A bit about how the A2A client is working, cached credentials and integrity validation.


## How does it work?

The A2A Client is part of Symantec PAM. The system where the A2A Client is running is called a Request server. When the A2A client is installed and started first time, it will announce itself to PAM using a HTTPS connection. PAM will lookup the IP address of the sender (request server) and find the DNS name for the system. If it is already registered as a device in PAM, the A2A usage is added to the device. Keep in mind that the hostname of the request server is the address of the device. If the DNS lookup does not resolve to a hostname or if the device is registered using a different address, then a new device is created in PAM. 

When the request server is registered, a unique symmetric key is generated for this A2A Client. The key is sent back to the A2A Client as a response to the initial HTTPS request. Communication from PAM to the A2A Client will encrypt the payload using this symmetric key. It is possible to initiate a key change from PAM. Communication from PAM to the A2A client uses port 28088. Messages initiated from PAM to the A2A client uses this port and firewall opening must permit this port for inbound connections. 

### Target Account Alias

When fetching credentials from PAM the target account is registered as an A2A account. Part of the Target Account setup is an alias for the credentials. The alias is unique in PAM and is used to identify the Target Account, and also the Target Application and Target Server. 

Credentials can be more than just a password. The database example will fetch all necessary database connection information from the account, application and server, such that it can establish a connection to a database.


### Cached credentials

The A2A client has an encrypted cache and it will store credentials when a request for credentials is successfully retrieved from PAM. At the account definition in PAM it is possible to change the cache behaviour for the specific account. It is recommended to use the cache whenever possible as this will speed up response time and off-load communication to PAM. 

A cache entry has a time to live. After the TTL a request for credentials will be sent to PAM. If there is a change in credentials in PAM, PAM will send a invalidate cache notification to the A2A client. This notifiction is sent to the A2A client at port 28088. When an application requests credentials and they do not exist in cache or if they are marked as invalid, then the request for credentials is sent to PAM.

### Application integrity

Another key functionality with the A2A client is the integrity validation of the application requesting credentials from PAM. In principle the A2A client will create a hash value of the application executable calling the A2A client. Depending on the programming or scripting language the application is the file with script source (Powershell, KSH, Perl, Python) or the compiled binary (C/C++, Java) executable. 

In PAM the authorization for a request server (A2A client) to fetch credentials can include the registered application integrity value. Only if the integrity value of the calling application matches what is registered as authorized will the credentials be released. I.e. changes to the application will invalidate the authorization and credentials will not be released to the application. 

It is worth to mention that application integrity is only one (1) script file or one Java class. The examples shown here will go beyond this limitation and will allow checking integrity validation of multiple files.

# Examples in the repo

There are three (3) example programs available here.

1) Credential caching and how to deal with stale cache. This is an example using a database connection to a Microsoft SQL database. The example is a Perl script implementing a mechanism where the credentials are fetched from A2A client cache and if the connection is failing, same credentials are fetched directly from PAM. The example is available for both Perl and for Python.
</br>See [Database-A2A](/Docs/Database-A2A.md) for details.

2) Integrity validation of multiple files using a Python script. The Python script is implementing a simple web server where integrity is check on multiple files during startup. If the integrity check correct the encryption key for HTTPS server is released and the Web server is started. If the integrity validation fails, the password to the keystore is not released and HTTPS is not started.
</br>See [Python-WebServer](/Docs/Python-WebServer.md) for details.

3) Integrity validation of multiple files for an Apache Tomcat application server. This is a Java example and the class registered as authorized in PAM is validating that the caller to the Java class is as expected, that integrity of files are as expected and only then releasing the password for a keystore required for HTTPS operation.</br></br>The example also shows how message encryption can be handled where the encryption key is stored and managed in PAM. There is a sample client application and a server side responder, where the responder decrypt the message encrypted by the client and returns the decrypted message to the calling application. Nothing fancy, just an example showing how encrypted messages can be received by a Tomcat application.
</br>See [SecureTomcat-WebServer](/Docs/SecureTomcat.md) for details.

