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

import java.security.Key;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;

import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.keys.AesKey;
import org.jose4j.lang.IntegrityException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import ch.pam_exchange.securetomcat.PAM;

import org.apache.catalina.startup.CatalinaProperties;

public class Message {

	private static final String PROPERTY_JWT_ALIAS= "pam.jwt.alias";
	private static final String PROPERTY_JWT_TIME_WINDOW= "pam.jwt.time.window";
	private static final long DEFAULT_TIME_WINDOW= 300;		// Permitted time window (in seconds) between not-before and expire
	
	private static final String MSG_ERR_PROPERTY_NOT_FOUND= "*** ERROR - Property not found ***";
	private static final String MSG_ERR_TOKEN_EXPIRED= "*** ERROR - Token has expired ***";
	private static final String MSG_ERR_TOKEN_REPLAY= "*** ERROR - Token replay ***";
	private static final String MSG_ERR_TIME_WINDOW= "*** ERROR - Invalid time window ***";
	private static final String MSG_ERR_INVALID_DATA= "*** ERROR - Invalid data ***";
	private static final String MSG_ERR_INTEGRITY= "*** ERROR - Message integrity invalid ***";
	private static final String MSG_ERR_EXCEPTION= "*** ERROR - Exception ***";
	private static final String MSG_ERR_NOT_ALLOWED= "*** ERROR - Call to getPassword is not allowed ***";
	
	private static final String LOGGER_NAME= "ch.pamexchange.securetomcat";
	private final Logger LOGGER=Logger.getLogger(LOGGER_NAME);
	private long timeWindow= 0;
	private String jwtAlias= null;
	private PAM pam= new PAM();
	private Map<String,Long> replayMap= new HashMap<>();
	private int cnt= 0;

	public Message () {
		// Fetch alias from property
		jwtAlias= CatalinaProperties.getProperty(PROPERTY_JWT_ALIAS);
		if (jwtAlias == null) {
			LOGGER.severe("Property '"+PROPERTY_JWT_ALIAS+"' not found");
		}
		LOGGER.fine("Property '"+PROPERTY_JWT_ALIAS+"'= "+jwtAlias);
		
		// Fetch time window from property
		String timeWindowStr= CatalinaProperties.getProperty(PROPERTY_JWT_TIME_WINDOW);
		if (timeWindowStr == null) {
			LOGGER.info("Property '"+PROPERTY_JWT_TIME_WINDOW+"' not found, using default "+DEFAULT_TIME_WINDOW);
			timeWindow= DEFAULT_TIME_WINDOW;
		} else {
			LOGGER.fine("Property '"+PROPERTY_JWT_TIME_WINDOW+"'= "+timeWindowStr);
			try {
				timeWindow= Long.parseLong(timeWindowStr);
			} catch (Exception e) {
				LOGGER.info("Value of property '"+PROPERTY_JWT_TIME_WINDOW+"' invalid, using default "+DEFAULT_TIME_WINDOW);
				timeWindow= DEFAULT_TIME_WINDOW;
			}
		}
		LOGGER.fine("timeWindow= "+timeWindow);
	}
	
	public int getCnt() {
		return cnt;
	}
	
	/*
	 * Add a token to the list of tokens
	 * 
	 * true  - If the token is new
	 * false - If the token already exist
	 */
	private boolean replayAdd( String id, Long ts ) {
		if (replayMap.put(id,ts) == null) {
			LOGGER.fine("id= "+id+", ts= "+ts);
			return true;
		}
		LOGGER.severe("id= "+id+", ts= "+ts+" -- duplicate");
		return false; // replay, already exist
	}
	
	/*
	 * Remove tokens from list
	 * All tokens with value less than ts are removed
	 */
	private void replayCleanup( Long ts ) {
		LOGGER.fine("ts= "+ts);
		replayMap.entrySet().removeIf( e -> e.getValue() < ts);
	}
	
	/*
	 * JWT message decode
	 */
	public String jwtDecode(String jweString) {
		String jwePayload= "";		// payload from JWE
		String userData;
		Boolean bypassCache= false;
		Key key;
		byte[] sharedKey;
		MessageDigest sha;

		cnt++;
		
		LOGGER.fine("cnt= "+cnt);
		LOGGER.fine("Property '"+PROPERTY_JWT_ALIAS+"'= "+jwtAlias);
		LOGGER.fine("timeWindow= "+timeWindow);

		if (jwtAlias == null) {
			LOGGER.severe("Property '"+PROPERTY_JWT_ALIAS+"' not available");
			return MSG_ERR_PROPERTY_NOT_FOUND;
		}
		
		// Decode message
		for (int attempt=0; attempt<1; attempt++) {
			/*
			 * Try at most twice to fetch credentials. 
			 * First using the A2A cache, second time bypass cache
			 */
			try {
				String sharedSecret= pam.getPassword( jwtAlias, bypassCache ); 
				if (sharedSecret.startsWith("***")) {
					LOGGER.severe("A password not available: '"+sharedSecret+"'");
					return null;
				}
				LOGGER.info("A password found - OK");
				
				// Generate symmetric 256 bit using SHA256.
				sha= MessageDigest.getInstance("SHA-256");
				// Use first 128 bits as AES key
				sharedKey= sha.digest(sharedSecret.getBytes("UTF-8"));
				key= new AesKey( Arrays.copyOfRange( sharedKey, 0, 16) );
				
				JsonWebEncryption jwe = new JsonWebEncryption();
				jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.A128KW);
				jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256);
				jwe.setKey(key);
				
				// --- decrypt ---
				jwe = new JsonWebEncryption();
				jwe.setAlgorithmConstraints(new AlgorithmConstraints(ConstraintType.PERMIT, KeyManagementAlgorithmIdentifiers.A128KW));
				jwe.setContentEncryptionAlgorithmConstraints(new AlgorithmConstraints(ConstraintType.PERMIT, ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256));
				jwe.setKey(key);
				jwe.setCompactSerialization(jweString);
				jwePayload= jwe.getPayload();
				LOGGER.fine("jwePayload= "+jwePayload);
				break;	// loop
				
			} catch (Exception e) {
				if (attempt==0) {
					bypassCache= true;
					LOGGER.info("Try again with bypassCache=true");
					continue; // loop
				}
				
				if (e instanceof IntegrityException) {
					LOGGER.severe("Integrity exception");
					LOGGER.log(Level.SEVERE, e.getMessage(), e);
					return MSG_ERR_INTEGRITY;
				}
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
				return MSG_ERR_EXCEPTION;
			} finally {
				// should do something better to wipe memory.
				LOGGER.fine("Cleanup");
				key= null;
				sharedKey= null;
				sha= null;
				System.gc();
			}
		}
		 
		// Parse and check message token
		try {
			JSONParser parser = new JSONParser();
			JSONObject jsonObject= (JSONObject)parser.parse(jwePayload);
			LOGGER.fine("json: "+jsonObject);
			
			Long now= new Date().getTime() / 1000;
			LOGGER.fine("now= "+now);

			// Cleanup replay list
			LOGGER.fine("Replay list cleanup: "+(now-timeWindow));
			replayCleanup(now-timeWindow);

			// Get not-before and expiery from token
			Long nbf= (Long)jsonObject.get("nbf");
			LOGGER.fine("Token nbf= "+nbf);
			Long exp= (Long)jsonObject.get("exp");
			LOGGER.fine("Token exp= "+exp);
			
			// Check for invalid time window 
			if ( (exp-nbf) > timeWindow ) {
				LOGGER.severe("Invalid time window - "+(exp-nbf)+" seconds");
				return MSG_ERR_TIME_WINDOW;
			}
			LOGGER.fine("Time window OK - "+(exp-nbf)+" seconds");
			
			// Check expired token
			if (exp<now) {
				LOGGER.severe("Token has expired");
				return MSG_ERR_TOKEN_EXPIRED;
			}
			LOGGER.fine("Token not expired");

			// Check for replay using jti and iat
			String jti= (String)jsonObject.get("jti");
			LOGGER.fine("Token jti: "+jti);

			Long iat= (Long)jsonObject.get("iat");
			LOGGER.fine("Token iat: "+iat);

			// Add token to replay list. Returns "false" if already known
			if (!replayAdd(jti,iat)) {
				LOGGER.severe("Replay token - "+jti);
				return MSG_ERR_TOKEN_REPLAY;
			}
			LOGGER.fine("Token unique (in time window)");
			
			// Get and return userData
			userData= (String)jsonObject.get("userData");
			LOGGER.info("Plaintext userData= "+userData);
			return userData;
		} catch (Exception e) {
			if (e instanceof ParseException) {
				LOGGER.severe("JSON parse exception, invalid data");
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
				return MSG_ERR_INVALID_DATA;
			}
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			return MSG_ERR_EXCEPTION;
		}
	}
	
}
