# PAM Configuration␤
This section describes how to configure PAM to support all SecureTomcat use cases.␤

# Tomcat/Catalina Application␤

## Password View Policy␤
This PVP is used for A2A-related accounts. It discourages users from viewing or manually using passwords. Dual Authorization and email notifications are recommended. The policy does **not** apply to A2A client credential requests.␤
![Password View Policy](/Docs/images/SecureTomcat-PVP.png)␤

## Password Composition Policy – SHA-256␤
Two target accounts store integrity values. This PCP accepts lowercase SHA-256 hash values.␤
![Password Composition Policy - SHA-256](/Docs/images/SecureTomcat-PCP-SHA256.png)␤

## TargetApplication – SHA-256␤
These target accounts use a generic application type and the SHA-256 PCP. The target device is the Tomcat server.␤
![TargetApplication - SHA-256 hash](/Docs/images/SecureTomcat-TargetApplication-SHA256.png)␤

## TargetAccount – filelist and callstack␤
Two accounts store integrity hashes for the call stack and file list.␤
![TargetAccount - Callstack](/Docs/images/SecureTomcat-TargetAccount-Callstack.png)␤
![TargetAccount - Filelist](/Docs/images/SecureTomcat-TargetAccount-Filelist.png)␤

## Script – PAM␤
The SecureTomcat script is the Java class `ch.pam_exchange.securetomcat.PAM` inside `secureTomcat.jar`.␤
![Script - PAM class](/Docs/images/SecureTomcat-Script-PAM.png)␤

## TargetGroup – SecureTomcat␤
A static target group is created to avoid individual mappings. All accounts required by the PAM class are included.␤
![TargetGroup - SecureTomcat](/Docs/images/SecureTomcat-TargetGroup.png)␤

## Mapping – SecureTomcat␤
This mapping authorizes the SecureTomcat PAM class to access all accounts in the group.␤
![Authorization mapping - SecureTomcat](/Docs/images/SecureTomcat-Mapping-PAM.png)␤

> [!NOTE]␤
> The group includes the keystore password, the Tomcat management user password, and the MessageEncode encryption key.␤

# Keystore␤
Tomcat enables HTTPS at startup using a keystore containing a private key. The keystore password is stored as a target account. See the repository *SymantecPAM-KeystoreFile* for details.␤

## TargetApplication – Keystore␤
![TargetApplication - Keystore #1](/Docs/images/SecureTomcat-TargetApplication-Keystore-1.png)␤
![TargetApplication - Keystore #2](/Docs/images/SecureTomcat-TargetApplication-Keystore-2.png)␤

## TargetAccount – Keystore␤
A domain-joined Windows server hosts the keystore on a network share. The account `KeystoreUpdate` has read/write access.␤
![TargetAccount - Keystore #1](/Docs/images/SecureTomcat-TargetAccount-Keystore-1.png)␤
![TargetAccount - Keystore #2](/Docs/images/SecureTomcat-TargetAccount-Keystore-2.png)␤

# MessageEncode␤
One target account stores the encryption/decryption key used by both `sampleClient` and `echoApp`.␤

## Password Composition Policy – MessageEncode␤
Defines the password rules for the key that will be hashed and used for JWT encryption/decryption.␤
![Encryption keys - MessageEncode](/Docs/images/SecureTomcat-PCP-MessageEncode.png)␤

## TargetApplication – MessageEncode␤
The application is of type API Key and assigned to the AppServer.␤
![TargetApplication - MessageEncode](/Docs/images/SecureTomcat-TargetApplication-MessageEncode.png)␤

## TargetAccount – MessageEncode␤
The stored password is hashed and used as the encryption/decryption key.␤
![TargetAccount - MessageEncode](/Docs/images/SecureTomcat-TargetAccount-MessageEncode.png)␤

## Script – MessageEncode␤
The script is registered on the server running the sampleClient.␤
![Script - sampleClient](/Docs/images/SecureTomcat-Script-MessageEncode.png)␤

> [!NOTE]␤
> The sampleClient uses loose Java class files. If packaged as a JAR, the correct script registration would apply.␤

A separate script for `echoApp` is not required because it uses the main PAM class.␤

## Mapping – MessageEncode␤
`echoApp` is covered through the SecureTomcat group mapping. Integrity validation is **not** enabled.␤
![Authorization mapping - sampleClient](/Docs/images/SecureTomcat-Mapping-MessageEncode.png)␤
