/*
 * Utility.java
 *
 * Created on July 21, 2007, 11:44 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package sagetcpserver.utils;

/**
 *
 * @author Rob
 */
public class SageLogger {
    
    private static final String MSG_PREFIX = "SageTCPServer";
    
    public static boolean ShowDebug = false;
    
    private String LocalName;
    
    private boolean HasLocalName = false;
    
    public SageLogger() {}
    
    /** Creates a new instance of Utility */
    public SageLogger(String localName) {
        
        LocalName = localName;
        HasLocalName = true;
    }
    
    public void Message(String message)
    {
        String messageHeader = "[[" + MSG_PREFIX;
        
        if (HasLocalName)
        {
            messageHeader += "-" + LocalName;
        }
        messageHeader += "]]: ";
        
        System.out.println (messageHeader + message);
        System.out.flush();
            
    }
    
    public void Debug(String message)
    {
        if (ShowDebug)
        {
            Message(message);
            
        }
        
    }
    
    
    public void Error(Throwable t)
    {
        String messageHeader = "[[" + MSG_PREFIX;
        
        if (HasLocalName)
        {
            messageHeader += "-" + LocalName;
        }
        messageHeader += "]]: EXCEPTION " + t.toString() + " AT ";
        
        System.out.println(messageHeader + t.getStackTrace()[0] + "\n");
        System.out.println (t.getStackTrace()[1] + "\n");
        System.out.println (t.getStackTrace()[2] + "\n");
        System.out.flush();
            
    }     
}
