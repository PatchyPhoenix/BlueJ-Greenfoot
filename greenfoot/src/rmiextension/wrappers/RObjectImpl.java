package rmiextension.wrappers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import bluej.debugmgr.Invoker;
import bluej.debugmgr.objectbench.ObjectWrapper;
import bluej.extensions.*;
import bluej.extensions.ClassNotFoundException;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.views.MethodView;
import bluej.views.View;

/**
 * @author Poul Henriksen
 * @version $Id: RObjectImpl.java 3234 2004-12-12 23:59:56Z davmac $
 */
public class RObjectImpl extends UnicastRemoteObject
    implements RObject
{
    /**
     * @throws RemoteException
     */
    protected RObjectImpl()
        throws RemoteException
    {
        super();
    }

    public RObjectImpl(BObject bObject)
        throws RemoteException
    {
        this.bObject = bObject;
        if (bObject == null) {
            throw new NullPointerException("Argument can't be null");
        }
    }

    BObject bObject;

    /**
     * @param instanceName
     * @throws ProjectNotOpenException
     * @throws PackageNotFoundException
     */
    public void addToBench(String instanceName)
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        bObject.addToBench(instanceName);
    }

    /**
     * @return
     * @throws ProjectNotOpenException
     * @throws ClassNotFoundException
     */
    public RClass getRClass()
        throws ProjectNotOpenException, ClassNotFoundException, RemoteException
    {
        BClass wrapped = bObject.getBClass();
        RClass wrapper = WrapperPool.instance().getWrapper(wrapped);
        return wrapper;

    }

    /**
     * @return
     */
    public String getInstanceName()
        throws RemoteException
    {
        return bObject.getInstanceName();
    }

    /**
     * @return
     * @throws ProjectNotOpenException
     * @throws PackageNotFoundException
     */
    public RPackage getPackage()
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        BPackage wrapped = bObject.getPackage();
        RPackage wrapper = WrapperPool.instance().getWrapper(wrapped);
        return wrapper;

    }

    /**
     * @throws ProjectNotOpenException
     * @throws PackageNotFoundException
     */
    public void removeFromBench()
        throws ProjectNotOpenException, PackageNotFoundException, RemoteException
    {
        bObject.removeFromBench();
    }

    // no longer needed
//    public MenuSerializer getMenu()
//        throws RemoteException
//    {
//        JPopupMenu menu = (JPopupMenu) bObject.getMenu();
//        return new MenuSerializer(menu);
//    }
    
    public String invokeMethod(String method, String [] argTypes, String [] argVals)
        throws RemoteException
    {
        try {
            // First find the method. The existing extension mechanism makes
            // this pretty much impossible; BClass only allows searching for
            // Declared methods - we want a method that may have been declared
            // in a super class.
            
            // TODO: add to extension mechanism(?).
            // For the moment, cheat and use reflection.
            
            Class BObjectClass = BObject.class;
            Field oWrapperField = BObjectClass.getDeclaredField("objectWrapper");
            oWrapperField.setAccessible(true);
            ObjectWrapper ow = (ObjectWrapper) oWrapperField.get(bObject);

            // Debug.message("Calling method: " + method + " on object: " + ow.getName());
            String className = ow.getObject().getClassName();
            PkgMgrFrame pmf = ow.getFrame();
            Class oClass = ow.getPackage().loadClass(className);
            
            // can't just use getMethods() as that doesn't give us package-private
            // methods, sigh...
            View mClassView = View.getView(oClass);
            MethodView theMethod = null;
            
            classLoop:
            while (mClassView != null) {
                MethodView [] methods = mClassView.getDeclaredMethods();
                findMethod:
                for (int i = 0; i < methods.length; i++) {
                    // This method is not the one we're looking for if it's
                    // private, has a different name, or a different number
                    // of parameters
                    if ((methods[i].getModifiers() & Modifier.PRIVATE) != 0)
                        continue;
                    if (! methods[i].getName().equals(method))
                        continue;
                    if (methods[i].getParameterCount() != argTypes.length)
                        continue;
                    
                    // ... or if any of the parameters are different
                    Class [] params = methods[i].getParameters();
                    for (int j = 0; j < params.length; j++) {
                        if (! params[j].getName().equals(argTypes[j]))
                            continue findMethod;
                    }
                    
                    // we've found the right method
                    theMethod = methods[i];
                    break classLoop;
                }
                
                // try the super class
                mClassView = mClassView.getSuper();
            }
            
            //if (theMethod == null)
            //    Debug.message("method not found.");
            
            if (theMethod != null) {
                
                // invoke the located method 
                RObjectResultWatcher watcher = new RObjectResultWatcher();
                Invoker invoker = new Invoker(pmf, theMethod, ow, watcher);
                synchronized (watcher) {
                    invoker.invokeDirect(argVals);
                    try {
                        watcher.wait();
                    }
                    catch (InterruptedException ie) {}
                }
                
                if (watcher.errorMsg != null) {
                    // some error occurred
                    return "!" + watcher.errorMsg;
                }
                else {
                    if (watcher.resultObj == null)
                        return null;
                    
                    ObjectWrapper newOw = ObjectWrapper.getWrapper(pmf, pmf.getObjectBench(), watcher.resultObj, "result");
                    pmf.getObjectBench().addObject(newOw);
                    pmf.getPackage().getDebugger().addObject(newOw.getName(), newOw.getObject());
                    //BObject newBObject = bObject.getPackage().getObject(newOw.getName());
                    //WrapperPool.instance().getWrapper(newBObject);
                    //new RObjectImpl(newBObject);
                    return newOw.getName();
                }
            }
            
        }
        //catch (PackageNotFoundException pnfe) {}
        //catch (ProjectNotOpenException pnoe) {}
        catch (NoSuchFieldException nsfe) {
            nsfe.printStackTrace();
        }
        catch (IllegalAccessException iae) {
            iae.printStackTrace();
        }
        return "Internal error.";
    }

}