/*********************************************************************
MIT License

Copyright (c) 2024-2025 PAM-Exchange

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*********************************************************************/
/*
TO-DO
- wipe memory after usage
*/

package ch.pam_exchange.securetomcat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.catalina.startup.Bootstrap;
import org.apache.catalina.startup.CatalinaProperties;
import org.apache.tomcat.util.IntrospectionUtils.PropertySource;

import com.cloakware.cspm.client.CSPMClient;

import ch.pam_exchange.securetomcat.PAM;
import ch.pam_exchange.securetomcat.PAMCredentialHandler;

public class PAM implements PropertySource {

	private static final Boolean verifyCallstack = true; // change to false to omit callstack verification
	private static final Boolean verifyFilelist = true; // change to false to omit filelist verification
	private static final Boolean strictChecking = true; // change to true for production

	// private final Boolean filelistEmbedded= true; // true: filelist hash is
	// embedded, false: filelist hash from PAM
	// private final String FILELIST_HASH=
	// "c11fdcc11562ecdb0cd8d4b0b6e51d49872a310b29c4cb2f75b0191b1ee66f57";

	private static final String PROPERTY_CALLSTACK_ALIAS = "pam.callstack.alias";
	private static final String PROPERTY_KEYSTORE_ALIAS = "pam.keystore.alias";
	private static final String PROPERTY_FILELIST_ALIAS = "pam.filelist.alias";
	private static final String PROPERTY_FILELIST_NAME = "pam.filelist.name";
	private static final String MSG_ERR_PROPERTY_NOT_FOUND = "*** ERROR - Property not found ***";
	private static final String MSG_ERR_NOT_ALLOWED = "*** ERROR - Call to getPassword is not allowed ***";
	private static final String LOGGER_NAME = "ch.pam_exchange.securetomcat";
	private final Logger LOGGER = Logger.getLogger(LOGGER_NAME);
	private static final String jarSelfKnown = System.getProperty("catalina.base").replace("\\", "/") + "/lib/secureTomcat.jar";
	
	private final Random rnd = new Random();
	private Boolean isAllowed = false;
	private final Boolean isWindows = System.getProperty("os.name").toLowerCase().indexOf("win") >= 0;

	public PAM() {
		try {
			String callerClass = Thread.currentThread().getStackTrace()[2].getClassName();
			LOGGER.fine("callerClass= " + callerClass);

			if ("ch.pam_exchange.securetomcat.Message".equals(callerClass)) {
				// Verify that the jar file of the calling class is the known "secureTomcat.jar"
				String jarSelfName = PAMCredentialHandler.class.getProtectionDomain().getCodeSource().getLocation().toString().replace("file:/", "");
				if (!isWindows) {
					jarSelfName = "/" + jarSelfName;
				}
				LOGGER.fine("PAM instance, jarSelfName= '" + jarSelfName + "'");
				isAllowed = jarSelfKnown.equals(jarSelfName);
				LOGGER.fine("callerClass= '" + callerClass + "', isAllowed=" + isAllowed);
				if (!isAllowed) {
					LOGGER.severe("PAM instance, known JAR: '" + jarSelfKnown + "', self JAR: '" + jarSelfName + "'");
				}
				return;
			}

			if ("sun.reflect.NativeConstructorAccessorImpl".equals(callerClass)
					|| "jdk.internal.reflect.NativeConstructorAccessorImpl".equals(callerClass)
					|| "jdk.internal.reflect.DirectConstructorHandleAccessor".equals(callerClass)
					) {
				// Java 1.8 - sun.reflect.NativeConstructorAccessorImpl
				// JDK 11 - jdk.internal.reflect.NativeConstructorAccessorImpl
				// OpenJDK 25 - jdk.internal.reflect.DirectConstructorHandleAccessor
				isAllowed = true;
				LOGGER.fine("callerClass= '" + callerClass + "', isAllowed=" + isAllowed);
				return;
			}

			if ("ch.pam_exchange.securetomcat.PAMCredentialHandler".equals(callerClass)) {
				String jarSelfName = PAMCredentialHandler.class.getProtectionDomain().getCodeSource().getLocation().toString().replace("file:/", "");
				if (!isWindows) {
					jarSelfName = "/" + jarSelfName;
				}
				LOGGER.fine("PAM instance, jarSelfName=" + jarSelfName);
				isAllowed = jarSelfKnown.equals(jarSelfName);
				LOGGER.fine("callerClass= '" + callerClass + "', isAllowed=" + isAllowed);
				if (!isAllowed) {
					LOGGER.severe("PAM instance, known JAR: '" + jarSelfKnown + "', self JAR: '" + jarSelfName + "'");
				}
				return;
			}
			LOGGER.warning("PAM instance invalid, callerClass= '" + callerClass + "'");
		} 
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	@Override
	public String getProperty(String arg0) {

		if (PROPERTY_KEYSTORE_ALIAS.equals(arg0)) {

			try {
				if (verifyCallstack) {
					/*
					 * verify call stack is as expected
					 */
					LOGGER.fine("Verify callstack");

					String callstackAlias = CatalinaProperties.getProperty(PROPERTY_CALLSTACK_ALIAS);
					if (callstackAlias == null) {
						LOGGER.severe("Property '" + PROPERTY_CALLSTACK_ALIAS + "' not found");
						return null;
					}
					LOGGER.fine("callstackAlias= " + callstackAlias);

					String expectedCallstackHash = getPassword(callstackAlias);
					LOGGER.fine("expectedCallstackHash= " + expectedCallstackHash);

					Boolean callstackOK = checkCallstack(expectedCallstackHash, strictChecking);
					LOGGER.fine("callstackOK= " + callstackOK);

					if (!callstackOK) {
						// callstack does not match
						LOGGER.severe("callstack not OK. Password for keystore is not released");
						LOGGER.severe("callstack alias: " + callstackAlias);
						return null;
					}
					LOGGER.info("callstack verify - OK");
				} 
				else {
					LOGGER.warning("callstack not verified");
				}

				if (verifyFilelist) {
					/*
					 * get list of files to check from FILELIST calculate hash of all files and
					 * compare with expected value retrieved from A2A.
					 */
					LOGGER.fine("verify filelist");

					String filelistName = CatalinaProperties.getProperty(PROPERTY_FILELIST_NAME);
					if (filelistName == null) {
						LOGGER.severe("Property '" + PROPERTY_FILELIST_NAME + "' not found");
						return null;
					}
					LOGGER.fine("filelistName= " + filelistName);

					String filelistAlias = CatalinaProperties.getProperty(PROPERTY_FILELIST_ALIAS);
					if (filelistAlias == null) {
						LOGGER.severe("Property '" + PROPERTY_FILELIST_ALIAS + "' not found");
						return null;
					}
					LOGGER.fine("filelistAlias= " + filelistAlias);

					String expectedFilelistHash = "";
					expectedFilelistHash = getPassword(filelistAlias);
					LOGGER.fine("expectedFilelistHash= " + expectedFilelistHash);

					Boolean filelistOK = checkFilelist(filelistName, expectedFilelistHash, strictChecking);
					LOGGER.fine("filelistOK= " + filelistOK);

					if (!filelistOK) {
						// filelist hash does not match
						LOGGER.severe("filelist hash not OK. Password for keystore is not released");
						LOGGER.severe("filelist alias: " + filelistAlias);
						LOGGER.severe("filelistName: " + filelistName);
						return null;
					}
					LOGGER.info("filelist verify - OK");
				} else {
					LOGGER.warning("filelist not verified");
				}

				/*
				 * passed callstack and files check, now get the alias for the property
				 * (keystore.alias) and retrieve the password via A2A call from PAM.
				 */
				String keystoreAlias = CatalinaProperties.getProperty(PROPERTY_KEYSTORE_ALIAS);
				if (keystoreAlias == null) {
					LOGGER.severe("Property '" + PROPERTY_KEYSTORE_ALIAS + "' not found");
					return null;
				}
				LOGGER.fine("keystoreAlias= " + keystoreAlias);

				String password = getPassword(keystoreAlias);
				if (password.startsWith("***")) {
					LOGGER.severe("A keystore password not available: '" + password + "'");
					return null;
				}
				LOGGER.info("A keystore password found - OK");
				return password;

			} 
			catch (Exception e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
				return null;
			}
		}
		return CatalinaProperties.getProperty(arg0);
	}

	/*
	 * Use A2A Client to fetch password for an alias
	 */
	public String getPassword(String alias) {
		return getPassword(alias, false);
	}

	public String getPassword(String alias, Boolean bpc) {
		if (!isAllowed) {
			LOGGER.severe("Call to getPassword is not allowed");
			return MSG_ERR_NOT_ALLOWED;
		}

		CSPMClient a2a = new CSPMClient();
		String a2aStatusCode = "999";

		try {
			for (int i = 0; i < 10; i++) {
				LOGGER.fine("alias='" + alias + "', try A2A with bypassCache=" + bpc);
				a2a.retrieveCredentials(alias, bpc);
				a2aStatusCode = a2a.getStatusCode();
				LOGGER.info("alias='" + alias + "', a2aStatusCode=" + a2aStatusCode);

				if ("400".equals(a2aStatusCode)) {
					String a2aUsername = a2a.getUserId();
					LOGGER.fine("Username: " + a2aUsername);
					String a2aPassword = a2a.getPassword();
					if (!strictChecking) LOGGER.fine("Password: " + a2aPassword);
					return a2aPassword;
				}
				if ("401".equals(a2aStatusCode)) {
					// Sometimes a2a.retrieveCredentials returns 401, even
					// when nothing is wrong. Wait a bit, then try again.
					// Stop after X attempts
					LOGGER.warning("A2A got 401, wait and try again");
					Thread.sleep(250);
					continue;
				}
				break; // neither 400 nor 401 returned, leave loop and handle error.
			}
			LOGGER.severe("A2A alias: " + alias);
			LOGGER.severe("A2A return code: " + a2aStatusCode + " - " + a2a.getMessage());
			return "*** not available - rc=" + a2aStatusCode + " *** (" + rnd.nextInt(65536) + ")";
		} 
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		} 
		finally {
			// cleanup memory, not really good enough
			LOGGER.fine("Cleanup");
			a2a = null;
			System.gc();
		}
		LOGGER.severe("A2A password not available");
		return "*** Not available *** (" + rnd.nextInt(65536) + ")";
	}

	/*
	 * Calculate a sha256 on a string
	 */
	private String sha256String(String source) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] mdbytes = md.digest(source.getBytes("UTF-8"));
			return convertToHex(mdbytes);
		} 
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		LOGGER.severe("[SHA] *** hash not available ***");
		return "*** hash not available *** (" + rnd.nextInt(65536) + ")";
	}

	/*
	 * Calculate a sha256 of a file
	 */
	private String sha256File(String filename) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			FileInputStream fis = new FileInputStream(filename);
			byte[] dataBytes = new byte[1024];
			int nread = 0;
			while ((nread = fis.read(dataBytes)) != -1) {
				md.update(dataBytes, 0, nread);
			}
			return convertToHex(md.digest());
		} 
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		LOGGER.severe("[SHA] *** hash not available ***");
		return "*** hash not available *** (" + rnd.nextInt(65536) + ")";
	}

	/*
	 * Convert byte array to hex string
	 */
	private String convertToHex(byte[] bytes) {
		StringBuffer hex = new StringBuffer();
		for (int i = 0; i < bytes.length; i++) {
			hex.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
		}
		return hex.toString();
	}

	/*
	 * retrieve filenames from file and calculate hash of each file. Calculate a
	 * hash of the combined list and test if it matches the expected hash.
	 */
	private Boolean checkFilelist(String filename, String expectedHash) {
		return checkFilelist(filename, expectedHash, true);
	}

	private Boolean checkFilelist(String filename, String expectedHash, Boolean strict) {
		LOGGER.fine("filename= " + filename);
		LOGGER.fine("expected hash= " + expectedHash);
		LOGGER.fine("strict checking= " + strict);

		String jarSelfName = PAM.class.getProtectionDomain().getCodeSource().getLocation().toString().replace("file:/","");
		if (!isWindows) {
			jarSelfName = "/" + jarSelfName;
		}
		LOGGER.fine("jarSelf= " + jarSelfName);

		ArrayList<String> filelist = new ArrayList<String>();
		Boolean filelistSelf = false;
		Boolean jarSelf = false;

		try {
			File file = new File(filename);
			filename = file.getAbsolutePath().replace("\\", "/"); // convert "\" to "/" as seperator
			LOGGER.fine("filename (normalized): " + filename);

			FileReader fileReader = new FileReader(file);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			StringBuffer stringBuffer = new StringBuffer();
			String line;

			// "hash *filename" or "filename"
			Pattern pattern= Pattern.compile("^([^\\s]+) \\*(.+)$|^(.+)$");
			
			while ((line = bufferedReader.readLine()) != null) {
				line= line.trim();

				Matcher matcher= pattern.matcher(line);
				if (matcher.matches()) {
					if (matcher.group(1) != null && matcher.group(2) != null) {
						// Matches: hashvalue *filename
						line= matcher.group(2).trim();
					} else if (matcher.group(3) != null) {
						// Matches: just filename
						line= matcher.group(3).trim();
					}
				}
				
				if (!line.isEmpty()) {
					line = line.replace("\\", "/"); // convert "\" to "/" as seperator
					filelist.add(line);
					LOGGER.fine("filelist line: " + line);
					if (isWindows) {
						if (filename.equalsIgnoreCase(line)) {filelistSelf = true;}
						if (jarSelfName.equalsIgnoreCase(line)) {jarSelf = true;}
					} 
					else {
						if (filename.equals(line)) {filelistSelf = true;}
						if (jarSelfName.equals(line)) {jarSelf = true;}
					}
				}
			}
			fileReader.close();
		} 
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			return false;
		}

		if (!filelistSelf) {
			LOGGER.severe("Filelist filename '" + filename + "' not included in filelist");
			if (strict) {
				return false;
			}
			else {
				LOGGER.info("Strict validation is off. Proceed anyway");
			}
		}
		if (!jarSelf) {
			LOGGER.severe("JAR filename '" + jarSelfName + "' not included in filelist");
			if (strict) {
				return false;
			}
			else {
				LOGGER.info("Strict validation is off. Proceed anyway");
			}
		}

		// sort list alphabetically
		//Collections.sort(filelist);

		try {
			// calculate hash of each file and build combined list (sha256+filename)
			String combined = "";
			for (String f : filelist) {
				String shafile = sha256File(f);
				LOGGER.fine(shafile + " " + f);
				if (combined.length() != 0) {
					combined += ", ";
				}
				combined += shafile + " " + f;
			}
			String actualHash = sha256String(combined);

			if (!actualHash.equals(expectedHash)) {
				LOGGER.severe("Verifying filelist failed");
				LOGGER.severe("combined: " + combined);
				LOGGER.severe("expected: " + expectedHash);
				LOGGER.severe("actual: " + actualHash);
				if (strict) {
					return false;
				}
				else {
					LOGGER.info("Strict validation is off. Proceed anyway");
				}
			}
			return true;
		} 
		catch (Exception e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			return false;
		}
	}

	/*
	 * get callstack and verify with expected hash
	 */
	private Boolean checkCallstack(String expectedHash) {
		return checkCallstack(expectedHash, true);
	}

	/*
	 * get callstack and verify with expected hash
	 */
	private Boolean checkCallstack(String expectedHash, Boolean strict) {
		LOGGER.fine("expected hash= " + expectedHash);
		LOGGER.fine("strict checking= " + strict);

		String callstack = "";
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		LOGGER.fine("stackTraceElements.length= " + stackTraceElements.length);
		for (int i = 1; i < stackTraceElements.length; i++) {
			
			// The classname 'org.apache.catalina.startup.Bootstrap' changes 
			// between startups. Ignore these elements of this class
			
			StackTraceElement ste = stackTraceElements[i];
			String classname = ste.getClassName();
			String methodName = ste.getMethodName();
			int lineNumber = ste.getLineNumber();
			
			if ("org.apache.catalina.startup.Bootstrap".equals(classname)) {
				LOGGER.fine("callstack: " + classname + "." + methodName + ":" + lineNumber+" - (ignore)");
			} 
			else {
				LOGGER.fine("callstack: " + classname + "." + methodName + ":" + lineNumber);
				if (callstack.length() != 0) {
					callstack += ", ";
				}
				callstack += classname + "." + methodName + ":" + lineNumber;
			}
		}

		String actualHash = sha256String(callstack);
		if (!actualHash.equals(expectedHash)) {
			LOGGER.severe("Verifying callstack failed");
			LOGGER.severe("callstack: " + callstack);
			LOGGER.severe("expected: " + expectedHash);
			LOGGER.severe("actual: " + actualHash);
			if (strict) {
				return false;
			} 
			else {
				LOGGER.info("Strict validation is off. Proceed anyway");
			}
		}
		return true;
	}
}
