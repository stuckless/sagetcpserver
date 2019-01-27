/*
 * SageConfiguration.java
 *
 * Created on July 26, 2007, 11:50 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package sagetcpserver;

/**
 *
 * @author Rob
 */
public class SageConfiguration {
    
    enum States {PLAYING, PAUSED, STOPPED};
    
    States currentState;
    
    /** Creates a new instance of SageConfiguration */
    public SageConfiguration() {
        currentState = States.STOPPED;
        
    }
    
    void changeState(States newState)
    {
        if (currentState == States.STOPPED)
        {
            // Output full file data
        }
        
        if (newState == States.STOPPED)
        {
            // Send clear
        }
        
        currentState = newState;
    }
    
}
