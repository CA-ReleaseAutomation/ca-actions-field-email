package com.ca.actions.field.mail;

import java.io.InputStream;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;

import com.nolio.platform.shared.api.*;

public abstract class ImapMailBase implements NolioAction {
    private static final long serialVersionUID = 1L;

    @ParameterDescriptor(name="IMAP Server", description="Mail server", out=false, in=true, nullable=false, order=51)        
    protected String server=null;
    
    @ParameterDescriptor(name="Port", description="Leave blank to use default ports (143 for IMAP, 993 for IMAPS)", out=false, in=true, nullable=true, order=52)        
    protected Integer port=null;
    
    @ParameterDescriptor(name="Use IMAPS?", description="Use IMAPS (IMAP over SSL) to connect?", out=false, in=true, nullable=true, defaultValueAsString="false", order=53)        
    protected Boolean imaps=false;
    
    @ParameterDescriptor(name="Folder", description="Blank/empty input will default to 'Inbox'", out=false, in=true, nullable=true, order=54)        
    protected String folderName=null;  
    
    @ParameterDescriptor(name="Username", description="Username", out=false, in=true, nullable=false, order=55)        
    protected String username=null;
    
    @ParameterDescriptor(name="Password", description="Password", out=false, in=true, nullable=false, order=56)        
    protected Password password;
    
    
    protected Folder folder = null;
    protected Store store = null;
    
    public ActionResult executeAction() {
    	String errorMessage = null;
    	if (server == null || server.isEmpty()) {
    		errorMessage = "IMAP Server cannot be empty";
    	} else if (username == null || username.isEmpty()) {
    		errorMessage = "Username cannot be empty";
    	} else if (password == null || password.toString().isEmpty()) {
    		errorMessage = "Password cannot be empty";
    	}
    	if (errorMessage != null) {
    		return new ActionResult(false, errorMessage);
    	}
    	
    	if (imaps == null) {
    		imaps = false;
    	}
    	if (folderName == null || folderName.isEmpty()) {
    		folderName = "Inbox";
    	}
    	 
    	if (port == null) {
    		port = imaps ? 993 : 143;
    	}

    	String protocol = imaps ? "imaps" : "imap";
    	Properties props = System.getProperties();
    	props.setProperty("mail." + protocol + ".port", port.toString());
   		props.setProperty("mail.store.protocol", protocol);
 	    
 	    try{
 	       Session session = Session.getInstance(props, null);
 	       store = session.getStore(protocol);
 	       store.connect(server, username, password.toString());
 	    } catch (Exception e) {
 	    	if (e instanceof com.sun.mail.util.MailConnectException) {
 	    		Throwable child = e.getCause();
 	    		if (child instanceof java.net.UnknownHostException) {
 	    			return new ActionResult(false, "Unknown host " + server);
 	    		} else if (child.getMessage().indexOf("Connection refused") >= 0) {
 	    			return new ActionResult(false, "Unable to connect to " + server + ":" + port);
 	    		}
 	    		return new ActionResult(false, child.getMessage());
 	    	} else if (e instanceof javax.mail.AuthenticationFailedException) {
 	    		return new ActionResult(false, "Invalid username or password");
 	    	}
 	    	return new ActionResult(false, e.getMessage());
 	    }

 	    try {
 	    	folder = store.getFolder(folderName);
 	    	folder.open(Folder.READ_WRITE);
 	    } catch (MessagingException e) {
 	    	return new ActionResult(false, "Unable to open folder: " + folderName);
 	    }
		
 	    ActionResult result = null;
		
 	    result = doWork();
 	    
    	try {
			folder.close(true);
	    	if (store != null) {
	    		store.close();
	    	}
		} catch (Exception e) {
		}

    	return result; 	    
    }
    
    protected ActionResult doWork() {
    	return new ActionResult(false, "You forgot to override this method");
    }
    
    protected String dumpPart(Part p) throws Exception {
    	String body = "";

		Object o = p.getContent();
		if (o instanceof String) {
			body = (String)o;
		} else if (o instanceof Multipart) {
		    Multipart mp = (Multipart)o;
		    int count = mp.getCount();
		    for (int i = 0; i < count; i++) {
		    	body += dumpPart(mp.getBodyPart(i));
		    }
		} else if (o instanceof InputStream) {
			System.out.println("Inputstream");
		    InputStream is = (InputStream)o;
		    int c;
		    while ((c = is.read()) != -1) {
		    	body += c;
		    }
		    is.close();
		}
		
		return body;
    }
}
