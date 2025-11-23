# SymantecPAM A2A␤

This repository demonstrates several ways to use the Symantec PAM Application-to-Application (A2A) client.␤
The A2A client actively fetches credentials from PAM and can cache them locally for faster responses and resilience during network outages.␤

Jump directly to project details:␤
- [Database-A2A](/Docs/Database-A2A.md)␤
- [Python-WebServer](/Docs/Python-WebServer.md)␤
- [SecureTomcat-WebServer](/Docs/SecureTomcat.md)␤

A short introduction to how the A2A client works, its credential cache, and integrity validation.␤

## How does it work?␤

The A2A Client is part of Symantec PAM. The server running the A2A Client is called the *request server*.␤
When the A2A Client starts for the first time, it announces itself to PAM using HTTPS. PAM checks the sender’s IP address and looks up its DNS name.␤
- If the request server already exists as a device in PAM, A2A usage is added to it.␤
- If the DNS lookup fails or maps to a different address, PAM creates a new device entry.␤

After registration, PAM generates a unique symmetric key for the A2A Client and returns it in the HTTPS response. PAM uses this key for encrypted communication to the A2A Client.␤
PAM initiates communication to the A2A Client on **port 28088**, so firewalls must allow inbound traffic on this port.␤

### Target Account Alias␤

Each credential retrieved from PAM corresponds to a *Target Account* configured for A2A.␤
The account has a unique alias that identifies the Target Account, Application, and Server.␤
Credentials may include more than a password—for example, database connection details used by the database example.␤

### Cached credentials␤

The A2A Client maintains an encrypted cache to store credentials after a successful request.␤
Cache behavior can be configured per account in PAM. Using the cache is recommended because it reduces load and improves performance.␤

Each cache entry has a TTL (time-to-live). After it expires, the client fetches fresh credentials from PAM.␤
If credentials change, PAM sends a cache-invalidate notification to the A2A Client on port **28088**.␤
If credentials are missing or marked invalid, the client requests them directly from PAM.␤

### Application integrity␤

The A2A Client validates the integrity of the application requesting credentials.␤
It calculates a hash of the executable or script file making the request (e.g., PowerShell, KSH, Perl, Python, or compiled binaries like C/C++ or Java).␤

PAM can require that the integrity hash of the calling application match the one registered for the request server.␤
If the hashes do not match, credentials are not released.␤

Note: PAM normally supports integrity validation of a single script file or Java class. The examples here extend this concept to multiple files.␤

# Examples in the repo␤

Three example programs are included:␤

1. **Credential caching and handling stale credentials**  
   A Perl and Python example showing how to fetch cached credentials for a SQL Server database, retry using fresh credentials if the connection fails, and handle stale cache scenarios.  
   See [Database-A2A](/Docs/Database-A2A.md).␤

2. **Integrity validation for multiple files in a Python web server**  
   A small HTTPS web server that validates the integrity of several files at startup. Only if validation succeeds is the HTTPS key released and the server started.  
   See [Python-WebServer](/Docs/Python-WebServer.md).␤

3. **Integrity validation for an Apache Tomcat server**  
   A Java example showing how a registered class validates the caller, checks file integrity, and releases a keystore password only when validation succeeds.  
   It also demonstrates simple encrypted message handling using keys stored in PAM.  
   See [SecureTomcat-WebServer](/Docs/SecureTomcat.md).␤
