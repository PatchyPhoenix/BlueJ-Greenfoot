package bluej.testmgr;

import java.awt.Color;

import javax.swing.JProgressBar;

/**
 * A progress bar showing the green/red status.
 * 
 * @author Andrew Patterson (derived from JUnit src)
 * @version $Id: ProgressBar.java 3725 2005-12-01 01:37:08Z bquig $
 */
class ProgressBar extends JProgressBar
{
    public static final Color redBarColour = new Color(208, 16, 16);
    public static final Color greenBarColour = new Color(32, 192, 32);

    private boolean fError = false;

    public ProgressBar() 
    {
        super();
        setForeground(getStatusColor());
    }

    private Color getStatusColor()
    {
        if(fError)
            return redBarColour;
        return greenBarColour;
    }

    public void reset()
    {
        fError = false;
        setForeground(getStatusColor());
        setValue(0);
    }

    public void step(int value, boolean successful)
    {
        setValue(value);
        if(!fError && !successful) {
            fError = true;
            setForeground(getStatusColor());
        }
    }
}