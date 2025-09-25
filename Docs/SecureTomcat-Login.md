# Tomcat Management Application

To ensure automated login through PAM to the Tomcat management console, a few settings in PAM and Tomcat is required.
Changes or configuration in PAM is described in this document. Changes to Tomcat are described in [Tomcat Management Console](./SecureTomcat-TomcatConfig.md#Tomcat-management-console). It is assumed that the default application `manager` is avaialble on the Tomcat application server.

# PAM Configuration

There are two aspects for the PAM configuration. First is the management of the login credentials and second the automated login to the management application.

## Target Application

Create a Target Application of type API key. The Password composition Policy `ApiKey-dynamic` is configured with an age limit of 1 day.

![TargetApplication - Administrator](/Docs/images/SecureTomcat-TargetApplication-Administrator.png)

## Target Account

The username is `tomcat` has the alias `SecureTomcat-Administrator`. Both the username and alias are configured in the `tomcat-users.xml` file.

![TargetAccount - Administrator](/Docs/images/SecureTomcat-TargetAccount-Administrator.png)

## TCP service for Tomcat Management

Login to the application uses HTTP authentication, i.e. there is a seperate lopup for the login credentials. A new TCP/UDP service `SecureTomcat Manager` is created. This is later associated to the application server. In the configuration the field `Auto Login Method` uses the value `Symantec PAM HTTP Web SSO`. The launch URL must be `https://<Local IP>:<First Port>/manager/html`. Access list is set to `*` allowing any desktop the connection. This can be more restrictive and only permit dedicated desktop access to the TCP service.

![TCP Service - Tomcat Management](/Docs/images/SecureTomcat-TCP-Service.png)


## Device - AppServer

On the AppServer device add a new service and assign the TCP service just created.

![Device - Tomcat Management #2](/Docs/images/SecureTomcat-Device-AppServer-2.png)


## Access Policy

Create/edit the policy for the applicaiton server and add the service `SecureTomcat Manager` in the access policy. Assign the login credentials `tomcat` to this access policy.

![Access Policy #1](/Docs/images/SecureTomcat-AccessPolicy-1.png)

![Access Policy #2](/Docs/images/SecureTomcat-AccessPolicy-2.png)


# Accessing Tomcat Management

On the users access page the Web Portal **SecureTomcat Manager** is available.

![Access Permissions #1](/Docs/images/SecureTomcat-Access-1.png)

When selecting this Web Portal application `SecureTomcat Manager` PAM will open a browser window and enter the username `tomcat` and current password when prompted by the Tomcat Manager application.

![Access Permissions #1](/Docs/images/SecureTomcat-Access-2.png)

The log file `$CATALINA_HOME/logs/catalina.log` it is seen that the CredentialHAndler is fetching the password for the configured alias from PAM and is verifying that the value presented by the user is matching. The user is not typing the password him-/herself, as PAM is injecting the password automatically.

![Access Permissions #1](/Docs/images/SecureTomcat-Access-3.png)

