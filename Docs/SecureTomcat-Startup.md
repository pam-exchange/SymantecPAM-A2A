# Tomcat startup

When Tomcat starts it will fetch the keystore password from the Java class PAM (part of securetomcat.jar). This class is registered as a script in PAM. The mapping is permitting access to credentials needed to start Tomcat. 

Key functionality is to validate integrity of run-time environment and files required for Tomcat and other parts of the environment. 

![Startup OK](/Docs/images/SecureTomcat-Startup-OK.png)

If everything goes OK, the catalina.log and/or console will show that password for the username `SecureTomcat-Keystore` has been retrieved from PAM. This is the password required to unlock the keystore with the private key needed to setup HTTPS listener in Tomcat.

## Integrity Checking

In PAM the authorizatiion of the script is including integrity validation of the PAM class (the script). A response code from A2A client of "400" is OK. Anything else is an error.

If the script integrity value registered in PAM is different from the actual integrity value calculated by the A2A client an error "436" is returned.

![Integrity failure](/Docs/images/SecureTomcat-Startup-IntegrityFailure.png)

This is easily fixed by requesting a new hash value in the script for `ch.pam_exchange.securetomcat.PAM`. Assuming communication from PAM to the A2A client is permitted, the correct hash value is updated for the script.

## Filelist integrity

At first call to the PAM method in `secureTomcat.jar` the integrity of the files in the filelist is validated and compared with the expected hash, which is fetched from PAM. The expected filelist hash is stored in a target account with alias `SecureTomcat-Filelist`. 

![Filelist actual hash](/Docs/images/SecureTomcat-Startup-Filelist-ActualHash.png)

If the expected hash is different from the actual hash, the password to the keystore is not retrieved. You can overrule this by changing the `PAM.java` such that `strictChecking` is set to **false**. If so, an incorrect filelist hash will still release the keystore password. If `strictChecking` is set to **true** the keystore password is not retrieved and HTTPS listener is not started.

![Filelist wrong hash](/Docs/images/SecureTomcat-Startup-Filelist-WrongHash.png)


## Callstack integrity

Similar to filelist integrity checking the call stack is also validated. This is actually done before the filelist hash validation and will appear first in the log. The expected hash value is stored on a target account with alias `SecureTomcat-Callstack`.  If the expected hash does not match the actual hash, the actual hash is shown in hte log and/or console.


## Strict checking

The difference of having `strictChecking` set to **true** or **false** is if it required that the callstack and filelist hashes must match the expected hashes before releasing the password for the keystore. 

![StrictCheck - false](/Docs/images/SecureTomcat-Startup-StrictCheck-False.png)

> [!IMPORTANT]
> If `strictChecking` is **false** passwords are shown in the log and/or console.

![StrictCheck - true](/Docs/images/SecureTomcat-Startup-StrictCheck-True.png)

