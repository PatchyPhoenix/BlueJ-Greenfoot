package bluej.debugger;

import java.util.List;

/**
 *  A class representing an object in the debugged VM.
 *
 *@author     Michael Kolling
 *@version    $Id: DebuggerObject.java 1991 2003-05-28 08:53:06Z ajp $
 */
public abstract class DebuggerObject
{
    /**
     *  Get the fully qualified name of the class of this object.
     *
     * @return  the fully qualified class name
     */
    public abstract String getClassName();

    /**
     *  Get the class of this object.
     *
     *  @return    The class object.
     */
    public abstract DebuggerClass getClassRef();

    /**
     *  Is an object of this class assignable to the given fully qualified type?
     *
     *@param  type  Description of Parameter
     *@return       The AssignableTo value
     */
    public abstract boolean isAssignableTo(String type);

    /**
     *  Return true if this object is an array.
     *
     *@return    The Array value
     */
    public abstract boolean isArray();

    /**
     * Return true if this object has a null value
     */
    public abstract boolean isNullObject();

    /**
     *  Return the number of static fields.
     *
     *@return    The StaticFieldCount value
     */
    public abstract int getStaticFieldCount();

    /**
     *  Return the number of object fields.
     *
     *@return    The InstanceFieldCount value
     */
    public abstract int getInstanceFieldCount();

    /**
     *  Return the name of the static field at 'slot'.
     *
     *@param  slot  The slot number to be checked
     *@return       The StaticFieldName value
     */
    public abstract String getStaticFieldName(int slot);

    /**
     *  Return the name of the object field at 'slot'.
     *
     *@param  slot  The slot number to be checked
     *@return       The InstanceFieldName value
     */
    public abstract String getInstanceFieldName(int slot);


    /**
     *  Return the object in static field 'slot'.
     *
     *@param  slot  The slot number to be returned
     *@return       The StaticFieldObject value
     */
    public abstract DebuggerObject getStaticFieldObject(int slot);

    /**
     *  Return the object in object field 'slot'.
     *
     *@param  slot  The slot number to be returned
     *@return       The InstanceFieldObject value
     */
    public abstract DebuggerObject getInstanceFieldObject(int slot);

    /**
     *  Return the object in field 'slot' (counting static and object fields).
     *
     *@param  slot  The slot number to be returned
     *@return       The FieldObject value
     */
    public abstract DebuggerObject getFieldObject(int slot);


    /**
     *  Return the object in field 'slot' (counting static and object fields).
     *
     *@param  name  Description of Parameter
     *@return       The FieldObject value
     */
    public abstract DebuggerObject getFieldObject(String name);

    /**
     *  Return the jdi object. This exposes the jdi to Inspectors.
     *  If jdi is not being used, it should return null, which is
     *  the default implementation.
     *
     *@return    The ObjectReference value
     */
    public abstract com.sun.jdi.ObjectReference getObjectReference();

    /**
     *  Return a list of strings with the description of each static field
     *  in the format "<modifier> <type> <name> = <value>".
     *
     *@param  includeModifiers  Description of Parameter
     *@return                   The StaticFields value
     */
    public abstract List getStaticFields(boolean includeModifiers);

    /**
     *  Return a list of strings with the description of each instance field
     *  in the format "<modifier> <type> <name> = <value>" or
     *  "<type> <name> = <value>", depending on the parameter.
     *
     *@param  includeModifiers  Description of Parameter
     *@return                   The InstanceFields value
     */
    public abstract List getInstanceFields(boolean includeModifiers);

    /**
     *  Return a list of strings with the description of each field
     *  (including static and instance) in the
     *  format "<modifier> <type> <name> = <value>" or
     *  "<type> <name> = <value>", depending on the parameter.
     *
     *@param  includeModifiers  Description of Parameter
     *@return                   The AllFields value
     */
    public abstract List getAllFields(boolean includeModifiers);


    /**
     *  Return true if the static field 'slot' is public.
     *
     *@param  slot  The slot number to be checked
     *@return       Description of the Returned Value
     */
    public abstract boolean staticFieldIsPublic(int slot);

    /**
     *  Return true if the object field 'slot' is public.
     *
     *@param  slot  The slot number to be checked
     *@return       Description of the Returned Value
     */
    public abstract boolean instanceFieldIsPublic(int slot);


    /**
     *  Return true if the static field 'slot' is an object (and not
     *  a simple type).
     *
     *@param  slot  The slot number to be checked
     *@return       Description of the Returned Value
     */
    public abstract boolean staticFieldIsObject(int slot);

    /**
     *  Return true if the object field 'slot' is an object (and not
     *  a simple type).
     *
     *@param  slot  The slot number to be checked
     *@return       Description of the Returned Value
     */
    public abstract boolean instanceFieldIsObject(int slot);

    /**
     *  Return true if the field 'slot' is an object (and not
     *  a simple type). Includes static and instance fields.
     *
     *@param  slot  The slot number to be checked
     *@return       Description of the Returned Value
     */
    public abstract boolean fieldIsObject(int slot);
}
