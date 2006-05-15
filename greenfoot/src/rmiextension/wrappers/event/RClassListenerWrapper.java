package rmiextension.wrappers.event;

import java.rmi.RemoteException;

import bluej.extensions.event.ClassEvent;
import bluej.extensions.event.ClassListener;

public class RClassListenerWrapper implements ClassListener
{
    private RClassListener remoteListener;
    
    public RClassListenerWrapper(RClassListener listener)
    {
        remoteListener = listener;
    }
    
    public void classStateChanged(ClassEvent event)
    {
        try {
            remoteListener.classStateChanged(new RClassEventImpl(event));
        }
        catch (RemoteException re) {
            re.printStackTrace();
        }
    }

}
