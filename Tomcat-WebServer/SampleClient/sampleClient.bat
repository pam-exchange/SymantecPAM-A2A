@echo off
rem  This batch file runs the Example class in our directory. It simply builds the
rem command line

rem  Setup Global Variables
set CSPM_CLIENT_HOME=c:\cspm\cloakware

rem  Directory containing the class to execute (same as our directory)
set currentDir=%~dP0
set DIR_BIN=%currentDir%
set DIR_LIB=%currentDir%\lib
set DIR_CLASSES=%currentDir%\classes

rem  Name of class to execute
rem set CLASS_NAME=ch.pam_exchange.securetomcat.MessageEncode
set CLASS_NAME=MessageEncode

rem  Use invoker's preferred JVM if available. If not, use the one from PA
where java.exe >NUL
if errorlevel 1 JAVA_BINDIR=%CSPM_CLIENT_HOME%\cspmclient_thirdparty\java\bin\

rem  add our client's Java to library path to pickup our cryptography library
set LIB=%CSPM_CLIENT_HOME%\cspmclient\lib

rem  access to cspmclient, PA's FIPS library and the example program
set CLASS_PATH=.\
set CLASS_PATH=%CLASS_PATH%;%CSPM_CLIENT_HOME%\cspmclient\lib\cspmclient.jar
set CLASS_PATH=%CLASS_PATH%;%CSPM_CLIENT_HOME%\cspmclient\lib\cwjcafips.jar
set CLASS_PATH=%CLASS_PATH%;%DIR_LIB%\jose4j-0.9.6.jar
set CLASS_PATH=%CLASS_PATH%;%DIR_LIB%\slf4j-api-2.0.12.jar
set CLASS_PATH=%CLASS_PATH%;%DIR_LIB%\slf4j-simple-2.0.12.jar

rem Execute JAVA class
cd %DIR_CLASSES%
echo %JAVA_BINDIR%java -classpath %CLASS_PATH% -Djava.library.path=%LIB% %CLASS_NAME% --enable-native-access=ALL-UNNAMED %*
%JAVA_BINDIR%java -classpath %CLASS_PATH% --enable-native-access=ALL-UNNAMED -Djava.library.path=%LIB% %CLASS_NAME% %*
cd %DIR_BIN%
