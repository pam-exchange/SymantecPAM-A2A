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

package ch.pam_exchange.securetomcat.echoApp;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;
import java.io.BufferedReader;

/*
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
*/

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebInitParam;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import ch.pam_exchange.securetomcat.Message;

/**
 * Servlet implementation class FirstServlet
 */
@WebServlet(description = "EchoApp", urlPatterns = { "/EchoApp" , "/EchoApp.do"}, initParams = {@WebInitParam(name="id",value="1"),@WebInitParam(name="name",value="pam")})
public class EchoApp extends HttpServlet {
	private static final long serialVersionUID = 1L;
	public static final String HTML_START= "<html><body>";
	public static final String HTML_END= "</body></html>";
	public static final String MSG_ERR_TOKEN_EXPIRED= "*** ERROR - Token has expired ***";
	public static final String MSG_ERR_INVALID_DATA= "*** ERROR - Invalid data ***";
	public static final String MSG_ERR_INTEGRITY= "*** ERROR - Message integrity invalid ***";
	public static final String MSG_ERR_EXCEPTION= "*** ERROR - Exception ***";
	
	private static final String LOGGER_NAME= "ch.pam_exchange.secureTomcat";
	private final Logger LOGGER=Logger.getLogger(LOGGER_NAME);
	private int callCnt;
	private Message msg= new Message();

	public void init() throws ServletException {
		callCnt= 0;
	} 
 
    /**
     * @see HttpServlet#HttpServlet()
     */
    public EchoApp() {
        super();
        // TODO Auto-generated constructor stub
    } 
   
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		PrintWriter out = response.getWriter();
		Date date = new Date();
		String servletName= getServletName();
		
		out.println(HTML_START);
		
		out.println("<h2>Hi There!</h2><br/><h3>Date= "+date +"</h3>");
 		out.println("<br/>ServletName= "+servletName);
		out.println(HTML_END);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		StringBuilder userData= new StringBuilder();
	    try(BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(request.getInputStream()))) {
	        char[] charBuffer = new char[8192];
	        int bytesRead;
	        while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
	            userData.append(charBuffer, 0, bytesRead);
	        }
	    }
	    LOGGER.info("userData= "+userData);
	    String plain= msg.jwtDecode(userData.toString());
	    LOGGER.fine("plaintext= "+plain);
	    LOGGER.info("cnt= "+msg.getCnt());
	    System.gc();
	    
	    response.setContentType("text/html");
	    PrintWriter out = response.getWriter();
	    out.append(plain);
	    out.close();	    
	} 
}
