# Tomcat Management Application
PAM can automate login to the Tomcat Manager console. PAM-related settings are described here; Tomcat changes are outlined in the *Tomcat Management Console* documentation. The default `manager` application must be available.

# PAM Configuration

Two items must be configured: credential management and automated login.

## Target Application
Create a Target Application of type **API key** using the password policy **ApiKey-dynamic** with a 1-day age limit.
![TargetApplication - Administrator](/Docs/images/SecureTomcat-TargetApplication-Administrator.png)

## Target Account
Create a Target Account with username `tomcat` and alias `SecureTomcat-Administrator`. These values must also exist in `tomcat-users.xml`.
![TargetAccount - Administrator](/Docs/images/SecureTomcat-TargetAccount-Administrator.png)

## TCP Service for Tomcat Management
Tomcat Manager uses HTTP authentication, so credentials are entered separately. Create a new TCP/UDP service **SecureTomcat Manager**.
- Auto Login Method: **Symantec PAM HTTP Web SSO**
- Launch URL: `https://<Local IP>:<First Port>/manager/html`
- Access list: `*` (or restrict to specific desktops)
![TCP Service - Tomcat Management](/Docs/images/SecureTomcat-TCP-Service.png)

## Device – AppServer
On the AppServer device, add the newly created TCP service.
![Device - Tomcat Management #2](/Docs/images/SecureTomcat-Device-AppServer-2.png)

## Access Policy
Update the access policy for the AppServer and add the **SecureTomcat Manager** service. Assign the `tomcat` credentials to this policy.
![Access Policy #1](/Docs/images/SecureTomcat-AccessPolicy-1.png)
![Access Policy #2](/Docs/images/SecureTomcat-AccessPolicy-2.png)

# Accessing Tomcat Management
Users will see a Web Portal entry named **SecureTomcat Manager**.
![Access Permissions #1](/Docs/images/SecureTomcat-Access-1.png)

When launched, PAM opens the URL and automatically injects the username and password into the Tomcat Manager login.
![Access Permissions #1](/Docs/images/SecureTomcat-Access-2.png)

In `catalina.log`, the CredentialHandler retrieves the password for the alias from PAM and confirms the injected password. Users never type the password themselves.
![Access Permissions #1](/Docs/images/SecureTomcat-Access-3.png)
