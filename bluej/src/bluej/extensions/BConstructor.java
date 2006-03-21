package bluej.extensions;


import bluej.debugger.*;
import bluej.debugmgr.objectbench.*;
import bluej.pkgmgr.*;
import bluej.views.*;

/**
 * A wrapper for a constructor of a BlueJ class.
 * Behaviour is similar to reflection API.
 *
 * @version    $Id: BConstructor.java 3853 2006-03-21 20:17:53Z iau $
 */

/*
 * The problem of consistency here is quite subtle.....
 * I could try to get a kind of id for a ConstructorView and then try to get it back
 * when I need it, but really, the gain is almost nil.
 * What I will do is to have an Identifier with Project,Package,Class given and before doing
 * anythink I will check with it. If everything is still there it should be OK.
 * In any case, it it goes wrong we will get an invoker exception !
 *
 * Author Damiano Bolla, University of Kent at Canterbury, 2003,2004
 */
public class BConstructor
{
    private Identifier parentId;
    private ConstructorView bluej_view;


    /**
     * Constructor.
     * It is duty of the caller to make shure that the parent is valid.
     *
     * @param  aParentId  Description of the Parameter
     * @param  i_view     Description of the Parameter
     */
    BConstructor(Identifier aParentId, ConstructorView i_view)
    {
        parentId = aParentId;
        bluej_view = i_view;
    }


    /**
     * Tests if this constructor matches the given signature.
     *
     * @param  parameter  Description of the Parameter
     * @return            true if it does, false otherwise.
     */
    public boolean matches(Class[] parameter)
    {
        Class[] thisArgs = bluej_view.getParameters();

        // An empty array is equivalent to a null array
        if (thisArgs != null && thisArgs.length <= 0) {
            thisArgs = null;
        }
        if (parameter != null && parameter.length <= 0) {
            parameter = null;
        }

        // If both are null the we are OK
        if (thisArgs == null && parameter == null) {
            return true;
        }

        // If ANY of them is null we are in trouble now. (They MUST be both NOT null)
        if (thisArgs == null || parameter == null) {
            return false;
        }

        // Now I know that BOTH are NOT empty. They MUST be the same length
        if (thisArgs.length != parameter.length) {
            return false;
        }

        for (int index = 0; index < thisArgs.length; index++) {
            if (!thisArgs[index].isAssignableFrom(parameter[index])) {
                return false;
            }
        }

        return true;
    }


    /**
     * Returns the parameters of this constructor.
     * Similar to reflection API.
     *
     * @return    The parameterTypes value
     */
    public Class[] getParameterTypes()
    {
        return bluej_view.getParameters();
    }


    /**
     * Creates a new instance of the object described by this constructor.
     * Similar to reflection API. Note that this method should not be called from the AWT/Swing event-dispatching thread.
     *
     * @param  initargs                      Description of the Parameter
     * @return                               Description of the Return Value
     * @throws  ProjectNotOpenException      if the project to which this constructor belongs has been closed by the user.
     * @throws  PackageNotFoundException     if the package to which this constructor belongs has been deleted by the user.
     * @throws  InvocationArgumentException  if the <code>initargs</code> don't match the constructor's arguments.
     * @throws  InvocationErrorException     if an error occurs during the invocation.
     */
    public BObject newInstance(Object[] initargs)
             throws ProjectNotOpenException, PackageNotFoundException,
            InvocationArgumentException, InvocationErrorException
    {
        PkgMgrFrame pkgFrame = parentId.getPackageFrame();

        DirectInvoker invoker = new DirectInvoker(pkgFrame, bluej_view);
        DebuggerObject result = invoker.invokeConstructor(initargs);

        if (result == null) {
            return null;
        }

        String resultName = invoker.getResultName();
        PkgMgrFrame pmf = parentId.getPackageFrame();

        ObjectWrapper wrapper = ObjectWrapper.getWrapper(pmf, pmf.getObjectBench(), result, resultName);

        return new BObject(wrapper);
    }


    /**
     *  Description of the Method
     *
     * @return    Description of the Return Value
     */
    public String toString()
    {
        if (bluej_view != null) {
            return "BConstructor: " + bluej_view.getLongDesc();
        }
        else {
            return "BConstructor: ";
        }
    }
}
