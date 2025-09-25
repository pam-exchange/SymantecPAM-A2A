set BASENAME=Python-WebServer

set CERTFILE=%BASENAME%.crt
set KEYSTORE=%BASENAME%.keystore

set ALIAS=%BASENAME%
set PASSWORD=DjsQPjMrFP5zQrKXAsED66LTenz3l3xiPojk572BXBDc6HsXoJL9J5gzLW33Pevt

keytool -genkey -alias %ALIAS% -keyalg RSA -keysize 4096 -dname "CN=keystore, OU=PAM Test, O=PAM-Exchange, C=CH" -keypass %PASSWORD% -storepass %PASSWORD% -keystore %KEYSTORE% -validity 3650
keytool -importkeystore -srckeystore %KEYSTORE% -srcstorepass %PASSWORD% -destkeystore %KEYSTORE% -deststorepass %PASSWORD% -deststoretype pkcs12 
keytool -exportcert -alias %ALIAS% -keystore %KEYSTORE% -keypass %PASSWORD% -storepass %PASSWORD% -rfc -file %CERTFILE%
