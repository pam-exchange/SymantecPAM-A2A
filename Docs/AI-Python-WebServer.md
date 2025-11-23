# Python Web Server Using A2A

This example demonstrates a Python web server that uses a keystore to store the private key required for HTTPS.  
The password for the keystore is retrieved from Symantec PAM using A2A.  
In the A2A configuration, integrity validation is enabled so that PAM verifies the integrity of the application before releasing the password.  
For Python applications, the “application” is the Python source file that invokes the A2A client.

The goal of this example is to validate not only the Python source file that performs the A2A request, but also a set of additional files whose integrity must be verified.  
All files listed in the integrity configuration are validated before the keystore password is requested from PAM.

In summary, two security checks must succeed before the keystore password is released:

1. **Integrity of the Python source file** that performs the A2A call.  
   This file is responsible for validating additional files.

2. **Integrity of supporting files** that do not directly call A2A.  
   These files are checked by the Python source file, and PAM validates the integrity of the checker itself.

# Configuration of the Web Server

## Filelist

To define the files that must be validated at application startup, use the `generate-filelist` tool, available for PowerShell, Python, and Bash.  
This tool reads a filelist template—containing references to files and directories—and produces a new file containing the full, expanded list of all individual filenames to be validated.

A filelist template may look like the following:


```
.\log_config.py
.\pkcs12_loader.py
.\request_handlers.py
.\__pycache__\log_config.*
.\__pycache__\pkcs12_loader.*
.\__pycache__\request_handlers.*

C:\opt\Python313\Lib\http
C:\opt\Python313\Lib\logging
C:\opt\Python313\Lib\site-packages\cryptography\

C:\opt\Python313\Lib\hashlib.py
C:\opt\Python313\Lib\__pycache__\hashlib.*

C:\opt\Python313\Lib\shlex.py
C:\opt\Python313\Lib\__pycache__\shlex.*

C:\opt\Python313\Lib\ssl.py
C:\opt\Python313\Lib\__pycache__\ssl.*

C:\opt\Python313\Lib\subprocess.py
C:\opt\Python313\Lib\__pycache__\subprocess.*

C:\opt\Python313\Lib\tempfile.py
C:\opt\Python313\Lib\__pycache__\tempfile.*

```

Note that the source file `webserver.py` is the main program for the web server and **must not** be included in the filelist.  
PAM performs the integrity validation of this file directly.

After running the `generate-filelist` tool, a new filelist is produced that contains the individual filenames for all files referenced in the template filelist.

```
1f4a73b1570489289ca565c5f25ae7d64b7ea5a6c2c01f3fd768daf086ea74bf *C:\opt\Python-WebServer\__pycache__\log_config.cpython-313.pyc
12d0dda1c6163c24ed437cb72134616d692329b97ba89414fe6b93fa1ad629cc *C:\opt\Python-WebServer\__pycache__\pkcs12_loader.cpython-313.pyc
f36ec3530205a34ebcb6cd4398ed63a73a85993cff2b5a78845227103885d2d6 *C:\opt\Python-WebServer\__pycache__\request_handlers.cpython-313.pyc
66abf6ce7ad328f42b63f6a2c9eae5cf966fbd486b8cbdea261b6ad54441b26f *C:\opt\Python-WebServer\log_config.py
7239a5b1b98ffd07e39aa20eacefaaf27a36461fb9f4aa5c68644a41a06fb951 *C:\opt\Python-WebServer\pkcs12_loader.py
e197d7198ac9f2bfde9e9812e0e2b13116fd962cabed68b7a565d1d141561fec *C:\opt\Python-WebServer\request_handlers.py
2de3db09da281b16141d32909dc39e30e48caf8509eea73e7bb80616965f1447 *C:\opt\Python313\Lib\__pycache__\hashlib.cpython-313.opt-1.pyc
8b0320b779ee32bf91b56646f75aa291364c553d3b086772c1f20af1d1feed84 *C:\opt\Python313\Lib\__pycache__\hashlib.cpython-313.opt-2.pyc
...
61ecba35d155d4c3e3a29db8323fd57a78c4a60de451a328a5258e2b401b781b *C:\opt\Python313\Lib\site-packages\cryptography\x509\ocsp.py
054ce05d71955a296406474a3d39bd478a84944f600061e07583cc640a7b3c9a *C:\opt\Python313\Lib\site-packages\cryptography\x509\oid.py
811d82d9cf97650b5b959853e53e6f8d228eb426fbe1e7f66a53d59847301653 *C:\opt\Python313\Lib\site-packages\cryptography\x509\verification.py
0541f40c9be1ab6381e0b3577a7d4c433991535ee844af4fcf4af03e2ed498b5 *C:\opt\Python313\Lib\ssl.py
970207fdd712c92f7dc14d1623d2574f7e0910ceb0b5c37652a7a0850f35a396 *C:\opt\Python313\Lib\subprocess.py
9bd059599988556e2737d974bad655943b2c11285a4342643475e214654b57c5 *C:\opt\Python313\Lib\tempfile.py
```

The resulting filelist includes the SHA-256 hash of each file.  
At the end of the file, it also includes the SHA-256 hash of the filelist itself.  
This final combined hash value must be updated in the `webserver.py` source file of the web server application.

![WebServer constants](/Docs/images/Python-webserver-constants.png)

Other constants include the (relative) filenames for the keystore and the generated filelist, the PAM account alias used to retrieve the keystore password, and the integrity value for the generated filelist.  
Remember that the web server’s source code file is protected directly by PAM and must **not** be included in the filelist.  
The filelist describes additional files that are not protected by PAM but whose integrity is validated at application startup.

## Keystore file

The keystore file is stored in the `keystore` directory.  
This repository includes a script named `Create-Keystore.cmd` that generates a keystore with a known password.  
PAM will later rotate the password to a random value during normal operation.

```
set BASENAME=Python-WebServer

set CERTFILE=%BASENAME%.crt
set KEYSTORE=%BASENAME%.keystore

set ALIAS=%BASENAME%
set PASSWORD=DjsQPjMrFP5zQrKXAsED66LTenz3l3xiPojk572BXBDc6HsXoJL9J5gzLW33Pevt

keytool -genkey -alias %ALIAS% -keyalg RSA -keysize 4096 -dname "CN=keystore, OU=PAM Test, O=PAM-Exchange, C=CH" -keypass %PASSWORD% -storepass %PASSWORD% -keystore %KEYSTORE% -validity 3650
keytool -importkeystore -srckeystore %KEYSTORE% -srcstorepass %PASSWORD% -destkeystore %KEYSTORE% -deststorepass %PASSWORD% -deststoretype pkcs12 
keytool -exportcert -alias %ALIAS% -keystore %KEYSTORE% -keypass %PASSWORD% -storepass %PASSWORD% -rfc -file %CERTFILE%
```

Different parameters for generating the key pair may be used.

## Network share

This setup uses a custom connector for keystore files.  
The web server application runs on Windows, and access to the keystore file is provided through a network share.  
The account configured as the target account in PAM must have read/write permissions on this share.

# Configuration in PAM

The PAM configuration is straightforward and consists of the following components:  
PCP, PVP, Target Application, Target Account, A2A Script, and A2A Mapping.

This setup uses the **Keystore** application type, which is built using a custom connector available in the  
[Keystore](https://github.com/pam-exchange/SymantecPAM-KeystoreFile) repository.

## Password Composition Policy

The PCP used here excludes special characters and is designed to hold the encryption key for the keystore file.

![Password Composition Policy](/Docs/images/Python-PCP-KeystoreFilePassword.png)

## Password Viewing Policy

The keystore’s target account should not be used interactively.  
The PVP requires justification when the password is viewed or used for sessions.  
Email notifications and dual approval may also be applied.

![Password View Policy](/Docs/images/Python-PVP-KeystoreFileAPI.png)

## Target Application

The target application uses the **Keystore** application type.  
The corresponding TCF connector is available in the  
[Keystore](https://github.com/pam-exchange/SymantecPAM-KeystoreFile) repository.

It is also possible to use the *Generic* application type, but this would require manually synchronizing the keystore password in PAM.

![Target Application - #1](/Docs/images/Python-TargetApplication-Keystore-1.png)  
![Target Application - #2](/Docs/images/Python-TargetApplication-Keystore-2.png)

## Target Account

The target account represents the password for the keystore.  
The account type is **A2A**, and the alias configured here is referenced in the `webserver.py` source code.

![Target Account - #1](/Docs/images/Python-TargetAccount-Python-WebServer-1.png)  
![Target Account - #2](/Docs/images/Python-TargetAccount-Python-WebServer-2.png)

## A2A Script

The A2A Script corresponds to the Python source file `webserver.py`.  
The script definition specifies the request server, script location, and—most importantly—the script’s hash, which must be added manually via the PAM GUI.

![A2A Script](/Docs/images/Python-A2A-script-webserver.png)

## A2A Mapping

The A2A Mapping grants the script authorization to retrieve the account password.  
In this setup, the mapping requires validation of the execution path, file path, and—critically—file integrity.

![A2A Mapping](/Docs/images/Python-A2A-mapping-webserver.png)

# Running the application

The application is a simple HTTPS web server.  
Its purpose is to demonstrate a configuration where both Python source files and supporting files are integrity-validated before the keystore password is released.

![Running WebServer - Console](/Docs/images/Python-webserver-run.png)

![Running WebServer - Hello](/Docs/images/Python-webserver-hello.png)


