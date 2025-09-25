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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import javax.net.ssl.*;

import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.keys.AesKey;

//import ch.pamio.PAM;
import com.cloakware.cspm.client.CSPMClient;

public class MessageEncode {

	public static final String PROPERTY_JWT_ALIAS= "SecureTomcat-MessageEncode";
	public static final String PROPERTY_TOMCAT_SERVER= "192.168.242.31:8443";
	
	private static Random rnd= new Random();

    public static void main(String[] args) {

    	System.out.println("--------------\njweEncode\n--------------");

    	String jweData="";
    	String userData= String.join(" ", args);

        if (userData.length() == 0) {
        	userData= "{\"id\":100,\"message\":\"Hello World\"}";
        }
        System.out.println("[JWE] userData= -->"+userData+"<--");
    	
    	try {
    		
            System.out.println("\n[JWE] Create encrypted string");
            
    		String sharedSecret= getA2APassword(PROPERTY_JWT_ALIAS, true);
			if (sharedSecret.startsWith("***")) {
				System.out.println("[JWE] A keystore password not available: '"+sharedSecret+"'");
				return;
			}
    		
	        // Generate symmetric 256 bit using SHA256.
	        MessageDigest sha= MessageDigest.getInstance("SHA-256");
	        // Use first 128 bits as AES key
	        byte[] sharedKey= sha.digest(sharedSecret.getBytes("UTF-8"));
	        Key key = new AesKey( Arrays.copyOfRange( sharedKey, 0, 16) );
	        
	        // Create the Claims, which will be the content of the JWT
	        JwtClaims claims = new JwtClaims();
	        claims.setIssuer("PAM");  						// who creates the token and signs it
	        claims.setIssuedAtToNow();  					// when the token was issued/created (now)
	        claims.setExpirationTimeMinutesInTheFuture(1); 	// time when the token will expire (3 minutes from now)
	        claims.setNotBeforeMinutesInThePast(1); 		// time before which the token is not yet valid
	        claims.setStringClaim("userData", userData);
	        claims.setGeneratedJwtId(); 					// a unique identifier for the token
	        System.out.println("[JWE] claims: "+claims.toString());
	        
	        JsonWebEncryption jwe = new JsonWebEncryption();
	        
	        jwe.setPayload(claims.toJson());
	        System.out.println("[JWE] payload: "+jwe.getPayload());
	        
	        jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.A128KW);
	        jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256);
	        jwe.setKey(key);
	        jweData= jwe.getCompactSerialization();
	        System.out.println("[JWE] jweData: " + jweData);
	        
	        // --- decrypt ---
	        System.out.println("\n[JWE] Test decryption");
	        jwe = new JsonWebEncryption();
	        jwe.setAlgorithmConstraints(new AlgorithmConstraints(ConstraintType.PERMIT, KeyManagementAlgorithmIdentifiers.A128KW));
	        jwe.setContentEncryptionAlgorithmConstraints(new AlgorithmConstraints(ConstraintType.PERMIT, ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256));
	        jwe.setKey(key);
	        jwe.setCompactSerialization(jweData);
	        System.out.println("[JWE] plain: " + jwe.getPayload());

    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	System.out.println("\n[JWE] Send jweData to Tomcat server");
        try {
			long ts1= new Date().getTime();
            String body = httpsPost("https://"+PROPERTY_TOMCAT_SERVER+"/echoApp/EchoApp", jweData);
			long ts2= new Date().getTime();
			System.out.println("[JWE] httpsPost time: " + (ts2-ts1) + " mS");
            System.out.println("[JWE] response: -->"+body+"<--");
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }
    	
	/*
	 * Use A2A Client to fetch password for an alias having 
	 * bypassCache=false
	 */
    public static String getA2APassword(String alias) 
	{
		return getA2APassword(alias,false);
	}
	
	/*
	 * Use A2A Client to fetch password for an alias
	 */
    public static String getA2APassword(String alias, Boolean bpc) 
	{
		CSPMClient a2a= new CSPMClient();
		String a2aStatusCode= "999";
		
        try {
			for (int i=0; i<10; i++) {
				if (bpc) {
	        		System.out.println("[PAM] Try A2A with bypassCache=true");
					a2a.retrieveCredentials(alias, "true");
				} else {
					System.out.println("[PAM] Try A2A with bypassCache=false");
					a2a.retrieveCredentials(alias, "false");
				}
				a2aStatusCode= a2a.getStatusCode();
				System.out.println("[PAM] a2aStatusCode= "+a2aStatusCode);
					
				if (a2aStatusCode.equals("400")) {
					// String a2aUsername= a2a.getUserId();
					// System.out.println("[PAM] Username: " + a2aUsername);
					String a2aPassword= a2a.getPassword();
					// System.out.println("[PAM] Password: " + a2aPassword);
					return a2aPassword;
				}
				if (a2aStatusCode.equals("401")) {
					// Sometimes a2a.retrieveCredentials returns 401, even 
					// when nothing is wrong. Wait a bit, then try again.
					// Stop after X attepmts
					System.out.println("[PAM] A2A got 401, wait and try again");
					Thread.sleep(250);
					continue;
				}
				break;	// neither 400 nor 401 returned, leave loop and handle error.
			}
			System.out.println("[PAM] A2A alias: " + alias);
			System.out.println("[PAM] A2A return code: " + a2aStatusCode+" - "+a2a.getMessage());
			return "*** not available - rc="+a2aStatusCode+" *** ("+rnd.nextInt(65536)+")";
        } catch (Exception e) { 
			//LOGGER.log(Level.SEVERE, e.getMessage(), e);
		}
		finally {
			// cleanup memory, not really good enough
			a2a= null;
		}
        System.out.println("[PAM] A2A password not available");
        return "*** Not available *** ("+rnd.nextInt(65536)+")";
    }
    
    //--------------------------------
    // HTTP stuff
    
    private static String httpsPost(String postUrl, String data) throws IOException {
        URL url = new URL(postUrl);
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        
        System.out.println("[WEB] URL: "+postUrl);
        System.out.println("[WEB] Data: "+data);
        
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setDoInput(true);
        httpsSendData(con,data);
        
        return httpsRead(con.getInputStream());
    }

    protected static void httpsSendData(HttpsURLConnection con, String data) throws IOException {
        DataOutputStream wr = null;
        try {
            wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(data);
            wr.flush();
            wr.close();
        } catch(IOException exception) {
            throw exception;
        } finally {
            httpsCloseQuietly(wr);
        }
    }

    private static String httpsRead(InputStream is) throws IOException {
        BufferedReader in = null;
        String inputLine;
        StringBuilder body;
        try {
            in = new BufferedReader(new InputStreamReader(is));

            body = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                body.append(inputLine);
            }
            in.close();

            return body.toString();
        } catch(IOException ioe) {
            throw ioe;
        } finally {
            httpsCloseQuietly(in);
        }
    }

    protected static void httpsCloseQuietly(Closeable closeable) {
        try {
            if( closeable != null ) {
                closeable.close();
            }
        } catch(IOException ex) {

        }
    }   

    static {
        // this part is needed cause TLS server has invalid SSL certificate, that cannot be normally processed by Java
        TrustManager[] trustAllCertificates = new TrustManager[]{
	        new X509TrustManager() {
	            @Override
	            public X509Certificate[] getAcceptedIssuers() {
	                return null; // Not relevant.
	            }
	
	            @Override
	            public void checkClientTrusted(X509Certificate[] certs, String authType) {
	                // Do nothing. Just allow them all.
	            }
	
	            @Override
	            public void checkServerTrusted(X509Certificate[] certs, String authType) {
	                // Do nothing. Just allow them all.
	            	for (X509Certificate cert : certs) {
		            	//System.out.println("[TLS] Certificate issuerDN: "+certs[0].getIssuerX500Principal());
		            	//System.out.println("[TLS] Certificate subjectDN: "+certs[0].getSubjectX500Principal());
		            	//System.out.println("[TLS] Certificate valid until: "+certs[0].getNotAfter().toString());
		            	System.out.println("[TLS] Certificate issuerDN: "+cert.getIssuerX500Principal());
		            	System.out.println("[TLS] Certificate subjectDN: "+cert.getSubjectX500Principal());
		            	System.out.println("[TLS] Certificate valid until: "+cert.getNotAfter().toString());
	            	}
	            }
	        }
        };
        
        HostnameVerifier trustAllHostnames = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true; // Just allow them all.
            }
        }; 
        
        try {
            System.setProperty("jsse.enableSNIExtension","false");
            SSLContext sc= SSLContext.getInstance("SSL");
            sc.init(null,trustAllCertificates,new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames);
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }        
        
    }
}
