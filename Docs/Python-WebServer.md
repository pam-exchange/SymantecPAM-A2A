# Python WebServer using A2A 

The example here is a Python web server application using a keystore for the private key required when listening on HTTPS. 

The password for the keystore is fetched from Symantec PAM. Security in the A2A definition in PAM is set to validate integrity of the application before releasing the password. The application or script when using Python is the Python source code file making the call to the A2A client. 

The aim with the example here is verifying not only the source code from where the call to A2A Client is made, but also showing a setup where additional files are integrity protected. Validation of integrity of all files is performed and tested before the password for the keystore is requested from PAM. 

Alas, there are two security checks that must be met before releasing the keystore password to the application. 

1) Integrity of the Python source code from where the call to A2A client is made. This source code will also validate integrity of other files. 

2) Integrity of files not making calls to A2A. The checking is done from hte source code making the A2A call, thus integrity of the validation of other files is validated by PAM. 

# Configuration of web server

## Filelist 

To create a list of files which are validated at startup of the application, the program `generate-filelist`is available for Powershell, Python and bash. The program will read a filelist with instructions which files are included in the integrity validation and create a new file with a list of all filenames for the individual files. 

A filelist template can be as follows: 

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

Note the source file `webserver.py` is the main program for the web server and **must not** be included in the filelist. The integrity of this file is done by PAM. 

After running the `generate-filelist` program a new filelist is generated with individual filenames for all files found from the template filelist 


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

The resulting filelist is showing the sha256 hash of the file. As a final message is the sha256 hash of the generated file itself. This combined hash value must be changed in the Python source file `webserver.py` of the web server application file. 

![WebServer constants](/Docs/images/Python-webserver-constants.png)

Other constants are (relative) filenames for keystore and generate filelist, account alias in PAM with keystore password and the integrity value for the generated filelist. Keep in mind that the source code file for the web server is protected by PAM and must not be part of the filelist. The filelist is naming other files not directly protected by PAM but by the startup mechanism of the main program.

## Keystore file

The keystore file is located in the directory `keystore`. In this repo there is a script `Create-Keystore.cmd` available. It will create a keystore with a known password. The setup in PAM will change the password to a random value.

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

Different parameters for generating the key pair can be used.

## Network share

The setup used here uses a custom connector for keystore files. The web server application is running in Windows and access to the keystore file is granted using a network share. The account used when configuring the target account must have read/write access to this network share.


# Configuration in PAM

The configuration PAM is straight forward and consists of a PCP, PVP, TargetApplication, TargetAccount, A2A Script and A2A Mapping.

The setup used here uses the application type Keystore. This application type is build using a Custom Connector and is found in the [Keystore](https://github.com/pam-exchange/SymantecPAM-KeystoreFile) repo.

## Password Composition Policy

The PCP used here is without special characters and is used to contain the encryption key of a keystore file.

![Password Composition Policy](/Docs/images/Python-PCP-KeystoreFilePassword.png)

## Password Viewing Policy

The target account for the keystore should not be used interactively. the PVP used is asking for justification if it is viewed or used for sessions. Email notification and dual apporval can be used too.

![Password View Policy](/Docs/images/Python-PVP-KeystoreFileAPI.png)

## Target Application

The target application is using the application type Keystore. The TCF connector for this application type is available in the [Keystore](https://github.com/pam-exchange/SymantecPAM-KeystoreFile) repo. It is possible to use a Generic application type and manually keep the password of used on the keystore file and available in PAM sync.

![Target Application - #1](/Docs/images/Python-TargetApplication-Keystore-1.png)
![Target Application - #2](/Docs/images/Python-TargetApplication-Keystore-2.png)

## Target Account

The target account is the password for the keystore. The account type is A2A and the alias used here is used in the `webserver.py` source code.

![Target Account - #1](/Docs/images/Python-TargetAccount-Python-WebServer-1.png)
![Target Account - #2](/Docs/images/Python-TargetAccount-Python-WebServer-2.png)

## A2A Script

The script or program used here is the Python source file `webserver.py`. An A2A Script is created specifying the request server, location of the script, and more importent the hash of the script. This is fetched manually in the PAM GUI.

![A2A Script](/Docs/images/Python-A2A-script-webserver.png)

## A2A Mapping

The A2A mapping is the authorization for a script to gain access to an account password. In the setting here, the authorization does require validation of execution path, file path and most important the integrity validation. 

![A2A Mapping](/Docs/images/Python-A2A-mapping-webserver.png)


# Running the application

The application is a simple HTTPS web server and does nothing fancy. The purpose is to demonstrate a setup where Python source and compiled files are integrity validated before releasing password to a keystore file.

![Running WebServer - Console](/Docs/images/Python-webserver-run.png)

![Running WebServer - Hello](/Docs/images/Python-webserver-hello.png)

















