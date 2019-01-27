/*
 * Message.java
 *
 * Created on July 22, 2007, 2:04 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package sagetcpserver.messages;

import sagetcpserver.utils.SageLogger;

/**
 *
 * @author Rob
 */


public class Message {
    
    
    private MessageType type;
    private String data = "";
    
    private SageLogger logger = new SageLogger("Message");

    private static final String SEPARATOR_CHAR = ":";
    
    /** Creates a new instance of Message */
    
    public Message() {

    }
    
    public Message(MessageType mt)
    {
        type = mt;
    }

    public Message(MessageType mt, String d)
    {
        type = mt;
        data = d;
    }

    @Override
    public String toString()
    {        
        StringBuilder message = new StringBuilder(type.getPrefix());
        
        
        if (data.trim().length() > 0)
        {
            message.append(SEPARATOR_CHAR);
            message.append(data);
        }
        
        return message.toString();
                
    }
    
    public void fromString(String input)
    {
        int delimIndex = input.indexOf(SEPARATOR_CHAR);

        if (delimIndex < 0)
        {
            type = MessageType.fromPrefix(input);
            data = "";
        }
        else
        {
            String pref = input.substring(0, delimIndex);
            
            if (pref == null || pref.isEmpty())
            {
                logger.Debug("Could not create message type from prefix: " + pref);
            }
            
            type = MessageType.fromPrefix(pref);

            if (type != null) data = input.substring(delimIndex + 1);
            else logger.Debug("Error trying to parse: " + input);
        }
    }
    
    
    public MessageType getType()
    {
        return type;
    }
    
    public String getData()
    {
        return data;
    }
    
}
