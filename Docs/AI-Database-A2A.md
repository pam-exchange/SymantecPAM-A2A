# Database A2A

This example demonstrates how to use an A2A application to retrieve database credentials securely from PAM.
The goal is to show how to establish a database connection **without hardcoded usernames or passwords** in scripts.

There are three Perl and Python examples:

- **mssql-Hardcoded.pl/.py**  
  Demonstrates hardcoded credentials in a script. This is insecure and requires script updates whenever credentials change, especially when passwords rotate automatically.

- **mssql-Simple.pl/.py**  
  Demonstrates retrieving credentials through the A2A client. This removes hardcoded credentials but does **not** handle stale cache situations.

- **mssql-CacheCheck.pl/.py**  
  Demonstrates retrieving credentials through the A2A client **and** handling stale cache. If the initial connection fails, the script retries with `bypasscache=true` to force a direct request from PAM.

SQL scripts are also included to set up an HR database used in these examples.

## A2A Client and Cache

The A2A client maintains an encrypted credential cache. When an application requests credentials, the client first checks the cache and returns values if they exist and are not invalidated.
Retrieving from cache takes roughly **200 ms**, while fetching from PAM may take several seconds depending on network topology, PAM load, and other factors.

The cache also serves as a fallback mechanism if PAM is temporarily unreachable.

A *stale cache* occurs when PAM updates a credential (e.g., password rotation). PAM sends cache-expire notifications to all A2A clients using that credential. These notifications may be delayed or lost. Regardless of the cause, applications must handle incorrect cached credentials gracefully.

![Cache handling](/Docs/images/A2A-CachedCredentials-ProcessFlow.png)

# Tool Chain

The following tools were used to build and test the Database A2A examples:
- Microsoft Windows Server 2022 Standard
- Microsoft SQL Server 2022 (Developer Edition)
- Strawberry Perl v5.40.2 (MSWin32-x64-multi-thread)
- Python 3.13.7
- Symantec PAM 4.2.3
- Symantec PAM A2A Client 4.12.3.134

# PAM Configuration

This section summarizes the relevant PAM configuration for the database example. Screenshots of the configuration are available in the repository.

## TargetApplication – MSSQL

The Target Application is configured using a database Password Composition Policy and an instance name.
In PAM 4.2.3, a GUI issue prevents specifying an instance name directly, so the database user is configured in the default `HR` database instead.

![TargetApplication - MSSQL #1](/Docs/images/Database-A2A-TargetApplication-1.png)
![TargetApplication - MSSQL #2](/Docs/images/Database-A2A-TargetApplication-2.png)

## TargetAccount – HR6423150

The Target Account is configured as an A2A type with alias `mssql-hr`. This alias is used by the Perl/Python scripts when requesting credentials.
The account uses a master account for password rotation. Its Password View Policy (PVP) requires dual authorization and email notification when viewed through the GUI.
PVP does **not** apply when credentials are retrieved via the A2A Client.

![TargetAccount](/Docs/images/Database-A2A-TargetAccount-1.png)

## A2A Script and Mapping

Two scripts are defined—one for each Perl/Python A2A-based example.
Each script has a mapping that authorizes the A2A client to retrieve credentials for the alias `mssql-hr`.

![Request Script - mssql-CacheCheck](/Docs/images/Database-A2A-Script-CacheCheck.png)

> [!NOTE]  
> Python scripts use the type **“Other”** in PAM.

![Authorization - mssql-hr](/Docs/images/Database-A2A-Mapping-CacheCheck.png)

# A2A Client and Local Cache

The key difference between `mssql-Simple.pl` and `mssql-CacheCheck.pl` is how they respond to stale cache data.
To demonstrate this, a firewall rule is configured to block inbound traffic on port **28888**, preventing cache-expire notifications from reaching the A2A client.

![Local firewall #1](/Docs/images/Database-A2A-Firewall-1.png)
![Local firewall #2](/Docs/images/Database-A2A-Firewall-2.png)

Running `mssql-Simple.pl` successfully retrieves cached credentials and connects once. With the firewall rule enabled, the invalid-credential notification does not arrive, so cached credentials become stale and the second run fails.

![mssql-Simple](/Docs/images/Database-A2A-Perl-Simple.png)

Running `mssql-CacheCheck.pl` behaves differently:
- It first attempts to connect using cached credentials.
- If the connection fails, it retries with `bypasscache=true`, forcing a direct fetch from PAM.
- The correct credentials are retrieved, and the database connection succeeds.

![mssql-CacheCheck](/Docs/images/Database-A2A-Perl-CacheCheck.png)
