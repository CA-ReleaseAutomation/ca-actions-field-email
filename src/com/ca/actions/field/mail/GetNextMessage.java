package com.ca.actions.field.mail;

import java.text.DateFormat;

import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.search.*;

import com.nolio.platform.shared.api.*;
import com.sun.mail.imap.IMAPFolder;

/**
 * 
 * <p>Date: Jul 26, 2014</p>
 *
 * @author kouth01
 */
@ActionDescriptor(
    name = "Get Next Message",
    description = "This action retrieves an email from an IMAP server based on various filters.  The From/Subject/Body filters match simple strings anywhere in their respective fields and are not case sensisitive.",
    category="Email.Field")

public class GetNextMessage extends ImapMailBase {
    private static final long serialVersionUID = 1L;

    @ParameterDescriptor(name="From Filter", description="Matches messages based on the From address.", out=false, in=true, nullable=true, order=1) 
    private String termFrom=null;    
    
    @ParameterDescriptor(name="Subject Filter", description="Matches messages based on the Subject.", out=false, in=true, nullable=true, order=2) 
    private String termSubject=null;    

    @ParameterDescriptor(name="Body Filter", description="Matches messages based on the Body.", out=false, in=true, nullable=true, order=3) 
    private String termBody=null;    
    
    @ParameterDescriptor(name="Read Status Filter", description="Matches message based on whether they read or not read.", out=false, in=true, nullable=true, defaultValueAsString="UNREAD", order=4)     
    private ReadOptions readOption = ReadOptions.UNREAD;
    
    @ParameterDescriptor(name="Wait For Message", description="If set to false, the action will fail if no matching messages are immediately found.  If set to true, the action will wait until a matching message arrives.", out=false, in=true, nullable=true, defaultValueAsString="false", order=5) 
    private Boolean waitForMessage=false;    
    
    @ParameterDescriptor(name="Matching Message Action", description="Action to take on matching message.", out=false, in=true, nullable=true, defaultValueAsString="MARK_AS_READ", order=6) 
    private ActionOptions actionOption = ActionOptions.MARK_AS_READ;

    @ParameterDescriptor(name="Message Number", description="", out=true, in=false, order=20)
    private String messageNumber;
    
    @ParameterDescriptor(name="From", description="", out=true, in=false, order=21)
    private String messageFrom;
    
    @ParameterDescriptor(name="Subject", description="", out=true, in=false, order=22)
    private String messageSubject;    

    @ParameterDescriptor(name="Body", description="", out=true, in=false, order=23)
    private String messageBody;     
    
    @ParameterDescriptor(name="Sent Date", description="", out=true, in=false, order=24)
    private String messageSentDate;    
    
    @ParameterDescriptor(name="Received Date", description="", out=true, in=false, order=25)
    private String messageReceivedDate;        
        
    private SearchTerm searchTerm = null;
    private Message targetMessage = null;
    private String exceptionMessage = null;
    
    private void ProcessMessage() throws Exception {
 	   	messageNumber = Integer.toString(targetMessage.getMessageNumber());
    	messageFrom = targetMessage.getFrom()[0].toString();
    	messageSubject = targetMessage.getSubject();
    	messageSentDate = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG).format(targetMessage.getSentDate().getTime());
    	messageReceivedDate = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG).format(targetMessage.getReceivedDate());
    	messageBody = dumpPart(targetMessage);
    	
    	switch (actionOption) {
    	case MARK_AS_READ: targetMessage.setFlag(Flags.Flag.SEEN, true); break;
    	case DELETE: targetMessage.setFlag(Flags.Flag.DELETED, true); break;
    	default: break;
    	}
    }
    
    protected ActionResult doWork() {
    	
		try {
			if (actionOption == null) {
				actionOption = ActionOptions.MARK_AS_READ;
			}
			if (readOption == null) {
				readOption = ReadOptions.UNREAD;
			}
			if (waitForMessage == null) {
				waitForMessage = false;
			}
			
			if (termFrom != null && !termFrom.isEmpty()) {
				searchTerm = new FromStringTerm(termFrom);
			}
			if (termSubject != null && !termSubject.isEmpty()) {
				SubjectTerm subjectTerm = new SubjectTerm(termSubject);
				if (searchTerm == null) {
					searchTerm = subjectTerm;
				} else {
					searchTerm = new AndTerm(searchTerm, subjectTerm);
				}
			}
			if (termBody != null && !termBody.isEmpty()) {
				BodyTerm bodyTerm = new BodyTerm(termBody);
				if (searchTerm == null) {
					searchTerm = bodyTerm;
				} else {
					searchTerm = new AndTerm(searchTerm, bodyTerm);
				}
			}			
		
			Flags seen = new Flags(Flags.Flag.SEEN);
			FlagTerm flagTerm = null;
			if (readOption != null) {
				switch (readOption) {
				case READ: flagTerm = new FlagTerm(seen, true); break;
				case UNREAD: flagTerm = new FlagTerm(seen, false); break;
				default: break;
				}
			}
			
			if (flagTerm != null) {
				if (searchTerm == null) {
					searchTerm = flagTerm;
				} else {
					searchTerm = new AndTerm(searchTerm, flagTerm);
				}				
			}		
			
			if (searchTerm != null) {
				Message messages[];
				messages = folder.search(searchTerm);
		    	switch (messages.length) {
	       		case 0: break;
	       		case 1: targetMessage = messages[0];
	       		default:
	       			if (messages[0].getSentDate().after(messages[messages.length-1].getSentDate())) {
	       				targetMessage = messages[messages.length - 1];
	       			} else {
	       				targetMessage = messages[0];
	       			}
		    	}
			} else {
				targetMessage = folder.getMessage(1);
			}
    	
	    	if (targetMessage == null && waitForMessage == false) {
	    		return new ActionResult(false, "No matching messages found");
	    	}
	    	
	    	if (targetMessage != null) {
	    		ProcessMessage();
	    		return new ActionResult(true, "Retrieve messaged from " + messageFrom + " with subject: " + messageSubject);
	    	}
	    	
	    	folder.addMessageCountListener(new MessageCountAdapter() {
    			public void messagesAdded(MessageCountEvent ev) {
    				try {
					    Message[] msgs = ev.getMessages();
					    System.out.println("here1");
					    System.out.println(msgs[0].getMessageNumber());
					    if (searchTerm == null) {
							targetMessage = folder.getMessage(msgs[0].getMessageNumber());
    					} else {
    						for (Message m : msgs) {
								if (m.match(searchTerm)) {
									targetMessage = folder.getMessage(m.getMessageNumber());
								}
    						}
					    }
    				} catch (MessagingException e) {
    					exceptionMessage = e.getMessage();
					}
    			}
    		});
    	
		    boolean supportsIdle = false;
		    try {
				if (folder instanceof IMAPFolder) {
				    IMAPFolder f = (IMAPFolder)folder;
				    f.idle();
				    supportsIdle = true;
				}
			} catch (MessagingException mex) {
				supportsIdle = false;
			}
			
		    System.out.println("supportsIdle: " + supportsIdle);
		    while (targetMessage == null && exceptionMessage == null) {
				if (supportsIdle && folder instanceof IMAPFolder) {
				    IMAPFolder f = (IMAPFolder)folder;
				    f.idle();
				} else {
				    Thread.sleep(20000); // sleep for freq milliseconds
				    // This is to force the IMAP server to send us
				    // EXISTS notifications. 
				    folder.getMessageCount();
				}
		    }
		    if (exceptionMessage != null) {
		    	return new ActionResult(false, "Unknown exception: " + exceptionMessage);
		    }
		    ProcessMessage();

		} catch (Exception e) {
			return new ActionResult(false, "Unknown exception: " + e.getMessage());
		} 
		return new ActionResult(true, "Retrieved message from " + messageFrom + " with subject: " + messageSubject);
    }
}
