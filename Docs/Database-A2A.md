# Database A2A

This A2A application example is using a database connection fetching information from a table. The examples are not advanced database applications, but with the aim showing how the A2A client and PAM can be used when establishing connection to a database without having hardcoded credentials and passwords in the script. 

There are three example scripts for Perl and Python available

- **mssql-Hardcoded.pl/.py**  
This is an example using credentials hardcoded in the script. This is bad as the script must be updated everything credentials are changed or updated. It is especially bad when credentials are changed automatically.

- **mssql-Simple.pl/.py**  
This is an example using the A2A client to eliminate hardcoded credentials in the script. The example does not handle a stale cache scenario.

- **mssql-CacheCheck.pl/.py**  
This is an example using the A2A to eliminate hardcoded credentials in the script **and** to handle stale cache scenario. If the connection is failing the script will request credentials again, but this time it request the A2A client to ignore or bypass the cache and fetch credentials directly from PAM.

Furthermore, there are some SQL scripts available to setup a HR database used in the above Perl/Python scripts. 


## A2A Client and cache

The A2A client has a built-in cache for credentials retrieved from PAM. when an application request credentials from PAM the A2A client will first look in the cache and return these if they exist and are not marked as invalid. When credentials are served from the cache the response time is ~200 mS to return credentials to the application. If they are fetched from PAM it can be several seconds depending on network topology, processing load in PAM and many other factors. The A2A cache can also serve as a contingency mechanism if a connection to PAM is unavailable, but you can still retrieve credentials for the application usage. 

The stale cache scenario is when a credential update (password change) is done in PAM, PAM will send a cache expire notification to all A2A clients using the credential. However, there is naturally some latency from the update to notification has been received or the notification may get lost all together. Regardless of why cached credentials in the A2A client are incorrect, the application must handle the scenario. 

![Cache handling](/Docs/images/A2A-CachedCredentials-ProcessFlow.png)

# Tool chain

The following tools are used when building the SecureTomcat libraries and applications.

- Microsoft Windows Server 2022 Standard
- Microsoft SQL Server 2022 (RTM) - 16.0.1000.6 (Developer Edition) 
- Strawberry Perl version v5.40.2, built for MSWin32-x64-multi-thread
- Python 3.13.7
- Symantec PAM 4.2.3
- Symantec PAM A2A client 4.12.3.134 (from PAM 4.2.3)

# PAM configuration

The following is showing some of the details in the PAM configuration for account, application and other settings for the database example. Not all details are shown here, but there are images of the various configuration available in the repo.

## TargetApplication - MSSQL type

The target application for the Microsoft SQL database is filled out using a Password Composition Policy for databases and an instance name. In PAM 4.2.3 (current version) there is a possible bug in the GUI and it is not possible to specify an instance name. Instead the database user is setup in the default database being 'HR'. 

![TargetApplication - MSSQL #1](/Docs/images/Database-A2A-TargetApplication-1.png)
![TargetApplication - MSSQL #2](/Docs/images/Database-A2A-TargetApplication-2.png)

## TargetAccount - HR6423150

The target account is defined as an A2A type account with alias `mssql-hr`. This alias is used in the Perl/Python script when requesting credentials. The account is setup such that it uses a master account to change password. The password view policy (PVP) is defined such that when used or viewed it will require dual authorization and it will send an e-mail notification. The PVP defined for the account only apply when the account is used through the GUI. It does not apply when credentials are retrieved through the A2A Client.

![TargetAccount](/Docs/images/Database-A2A-TargetAccount-1.png)

## A2A script and mapping

There are two scripts defined. One script for both of the Perl/Python source files using the A2A client. For each of the scripts there is a mapping authorizing the A2A client permissions to retrieve credentials for the alias `mssql-hr`, which is the database target account.

![Request Script - mssql-CacheCheck](/Docs/images/Database-A2A-Script-CacheCheck.png)

> [!NOTE]
> A Python script uses the type `Other`.


![Authorization - mssql-hr](/Docs/images/Database-A2A-Mapping-CacheCheck.png)

# A2A client and local cache

Handling the cache update is the main difference between the `mssql-Simple.pl` and `mssql-CacheCheck.pl` scripts. To see the effect of the cache handling a firewall rule blocking inbound communication on port 28888 is defined and will stop cache expire notifications from PAM to arrive in the A2A client.

![Local firewall #1](/Docs/images/Database-A2A-Firewall-1.png)
![Local firewall #2](/Docs/images/Database-A2A-Firewall-2.png)

Running the `mssql-Simple.pl` script will fetch the credentials and connect to the database. However, enabling the firewall rule will stop an **invalid credentials notification** from PAM, and credentials in the cache are incorrect. This is seen in the second run

![mssql-Simple](/Docs/images/Database-A2A-Perl-Simple.png)

Running the `mssql-CacheCheck.pl` is different and it will first fetch credentials using the cache. If the connection to the database is failing, it will fetch the credentials again, but this time using `bypasscache=true` in the A2A call. This will fetch the correct database credentials directly from PAM and the database connection is successfull.

![mssql-CacheCheck](/Docs/images/Database-A2A-Perl-CacheCheck.png)

