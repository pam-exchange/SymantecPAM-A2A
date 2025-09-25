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

package ch.pam_exchange.securetomcat;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.catalina.CredentialHandler;
import org.apache.tomcat.util.res.StringManager;

import ch.pam_exchange.securetomcat.PAM;

/**
 * A {@link CredentialHandler} that provides support for PAM A2A.
 *
 */
public class PAMCredentialHandler implements CredentialHandler {

	private static final String LOGGER_NAME= "ch.pam_exchange.secureTomcat";
	private final Logger LOGGER=Logger.getLogger(LOGGER_NAME);

	@Override
	public boolean matches(String inputCredentials, String alias) {
		PAM pam= new PAM();	
		LOGGER.info("alias= '"+alias+"'");
		String pwd;
		try {
			pwd= pam.getPassword(alias);
			if (pwd.startsWith("***")) {
				LOGGER.severe("A password not available: '"+pwd+"'");
				return false;
			}
			LOGGER.info("A password found - OK");
			if ( pwd.equals(inputCredentials) ) {
				LOGGER.info("Password matches");
				return true;
			}
			LOGGER.warning("Password does not match");
		} 
		catch (Exception e) 
		{
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		finally {
			LOGGER.fine("Cleanup");
			pwd= null;
			pam= null;
			System.gc();
		}
		return false;
	}

	@Override
	public String mutate(String inputCredentials) {
		LOGGER.info("inputCredentials= "+inputCredentials);
		return inputCredentials;
	}
}