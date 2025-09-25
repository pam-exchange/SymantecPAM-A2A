# PAM Configuration

The configuration described here is about how to setup PAM, such that the use cases for Secure tomcat are covered. 

# Tomcat/Catalina Application

## Password View Policy

This PVP is used for A2A accounts. When a target account uses this PVP it should incurage the user not to view or use the password. It is recommended to configure Dual Authorization and E-mail notifications when the account password is viewed or used. The PVP does not apply to A2A clients requesting credentials.

![Password View Policy](/Docs/images/SecureTomcat-PVP.png)

## Password Composition Policy - SHA-256

Two target accounts are used to store integrity values. The PCP can accept SHA-256 values where letters in the hash are using lower case letters.

![Password Composition Policy - SHA-256](/Docs/images/SecureTomcat-PCP-SHA256.png)

## TargetApplication - SHA-256

The two target accounts are defined as a generic application type using the PCP for SHA-256 hashes. The target server of device is the server running the Tomcat application server. 

![TargetApplication - SHA-256 hash](/Docs/images/SecureTomcat-TargetApplication-SHA256.png)


## TargetAccount - filelist and callstack

Two target accounts are used for the callstack and filelist hashes. 

![TargetAccount - Callstack](/Docs/images/SecureTomcat-TargetAccount-Callstack.png)

![TargetAccount - Filelist](/Docs/images/SecureTomcat-TargetAccount-Filelist.png)

## Script - PAM

The script for SecureTomcat is the Java class `ch.pam_exchange.securetomcat.PAM` found in the `secureTomcat.jar` library.

![Script - PAM class](/Docs/images/SecureTomcat-Script-PAM.png)

## TargetGroup - SecureTomcat

Instead of creating individual authorizations or mappings allowing access to different target accounts, a static target group is created. The target accounts in the group are all used by the PAM script. 

![TargetGroup - SecureTomcat](/Docs/images/SecureTomcat-TargetGroup.png)


## Mapping - SecureTomcat

Finally, the authorization or mapping will allow PAM class in secureTomcat.jar access to the target accounts found in the target group. 

![Authorization mapping - SecureTomcat](/Docs/images/SecureTomcat-Mapping-PAM.png)

> [!NOTE]
> Note that this static target group and mapping below includes password for accessing the Keystore, password for tomcat management user, and encryption key for MessageEncode (echoApp).

# Keystore

When Tomcat starts it is enableing a HTTPS listener. For this to work, a keystore with the private key is used. The password to access the keystore is defined as a target account.
See the repo [SymantecPAM-KeystoreFile](https://github.com/pam-exchange/SymantecPAM-KeystoreFile) for details.

## TargetApplication - Keystore

![TargetApplication - Keystore #1](/Docs/images/SecureTomcat-TargetApplication-Keystore-1.png)

![TargetApplication - Keystore #2](/Docs/images/SecureTomcat-TargetApplication-Keystore-2.png)

## TargetAccount - Keystore

![TargetAccount - Keystore #1](/Docs/images/SecureTomcat-TargetAccount-Keystore-1.png)

The AppServer is a domain joined Windows server. A networkshare is created for the folder where the keystore file is located. A domain user KeystoreUpdate has read/write permissions to the network share.

![TargetAccount - Keystore #2](/Docs/images/SecureTomcat-TargetAccount-Keystore-2.png)


# MessageEncode

The encryption/decryption key for messages is stored as a target account. Both the sampleClient and the sampleServer (echoApp) will access the same encryption key, which is the target account `SecureTomcat-MessageEncode`. The target account is anchored on the AppServer.

## Password Composition Policy - MessageEncode

The PCP is defining the encryption/decryption policy for the key used by `sampleClient` and sampleServer (`echoApp`) applications. It is not the key directly which is used but a hash of a password with this composition.

![Encryption keys - MessageEncode](/Docs/images/SecureTomcat-PCP-MessageEncode.png)


## TargetApplication - MessageEncode

The target application is ApiKey type defined in the AppServer server. 

![TargetApplication - MessageEncode](/Docs/images/SecureTomcat-TargetApplication-MessageEncode.png)


## TargetAccount - MessageEncode

The target account password is the basis for the encryption/decryption key. It is not used directly but a hash of the password will be used for JWT message encoding/decoding.

![TargetAccount - MessageEncode](/Docs/images/SecureTomcat-TargetAccount-MessageEncode.png)

## Script - MessageEncode

The script defined here is for the server from where the sampleClient is running. 

![Script - sampleClient](/Docs/images/SecureTomcat-Script-MessageEncode.png)

> [!NOTE]
> The sampleClient uses the Java class files directly and the script registered in PAM does not reflect this. The sampleClient can probably be packed in a jar file, in which case the correct application is registered.

It is not necessary to create a script for the sampleServer (`echoApp`) as the A2A client is called from the PAM class covered earlier.

## Mapping - MessageEncode

The sampleServer (`echoApp`) is covered with the mapping for target group SecureTomcat.

![Authorization mapping - sampleClient](/Docs/images/SecureTomcat-Mapping-MessageEncode.png)

The mapping does **not** use integrity validation. 