/*
 * IntervalTimer.java
 *
 * Created on August 3, 2007, 1:24 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package sagetcpserver.utils;

/**
 *
 * @author Rob
 */
public class IntervalTimer {
    
    private boolean runFirstTime = true;
    private boolean isFirstTime = true;
    
    private int counter = 0;
    
    private int intervalCount = 0;
    
    /** Creates a new instance of IntervalTimer */
    public IntervalTimer(int intervalTarget, boolean first) {
        
        runFirstTime = first;
        intervalCount = intervalTarget;
        
    }
    
    public boolean execute()
    {
        if (runFirstTime && isFirstTime)
        {
            isFirstTime = false;
            return true;
        }
        
        if (counter >= intervalCount)
        {
            counter = 0;
            return true;
            
        }
        else
        {
            counter ++;
            return false;
        }
    }
    
}
