package bluej.debugger.jdi;

import java.util.HashMap;
import java.util.Map;

import bluej.utility.JavaNames;

import com.sun.jdi.*;

/*
 * A DebuggerObject with support for java 1.5 - generics etc.
 * @author Davin McCall
 */
public class JdiObject15 extends JdiObject {

    // private JdiGenType genericType = null; // the generic type, as best as
                // we can deduce it. This should be null for a raw or non-
                // generic type.
    private Map genericParams = null; // Map of parameter names to types
    
    /**
     *  Factory method that returns instances of JdiObjects.
     *
     *  @param  obj  the remote object this encapsulates.
     *  @return      a new JdiObject or a new JdiArray object if
     *               remote object is an array
     */
    public static JdiObject getDebuggerObject(ObjectReference obj)
    {
        if (obj instanceof ArrayReference) {
            return new JdiArray((ArrayReference) obj);
        } else {
            return new JdiObject15(obj);
        }
    }
    
    /**
     * Get a JdiObject from a field. 
     * @param obj    Represents the value of the field.
     * @param field  The field.
     * @param parent The parent object containing the field.
     * @return
     */
    public static JdiObject getDebuggerObject(ObjectReference obj, Field field, JdiObject15 parent)
    {
        if (obj instanceof ArrayReference) {
            return new JdiArray((ArrayReference) obj);
        } else {
            if( jvmSupportsGenerics )
                return new JdiObject15(obj, field, (JdiObject15)parent);
            else
                return new JdiObject15(obj);
        }
    }
    
    /**
     * Constructor. 
     */
    protected JdiObject15() {
        super();
    }

    /**
     *  Constructor is private so that instances need to use getJdiObject
     *  factory method.
     *
     *  @param  obj  the remote debugger object (Jdi code) this encapsulates.
     */
    private JdiObject15(ObjectReference obj)
    {
        this.obj = obj;
        getRemoteFields();
    }

    /**
     * Private constructor. Construct from a given object reference using the
     * generic signature of a field and the parent object.
     * @param obj     The object to represent
     * @param field   The field to extract the generic signature from
     * @param parent  The parent object to get type information from
     */
    private JdiObject15(ObjectReference obj, Field field, JdiObject15 parent)
    {
        this.obj = obj;
        getRemoteFields();
        if( field.genericSignature() != null ) {
            JdiGenType genericType = JdiGenType.fromField(field, parent);
            ClassLoaderReference cl = obj.referenceType().classLoader();
            genericParams = genericType.mapToDerived(obj.referenceType(), new JdiClassSource(obj.virtualMachine(), cl));
        }
    }
    
    /**
     * Get a mapping of the type parameter names for this objects class to the
     * actual type, for all parameters where some information is known. May
     * return null.
     * 
     * @return a Map (String:JdiGenType) of type parameter names to types
     */
    public Map getGenericParams()
    {
        Map r = null;
        if( genericParams != null ) {
            r = new HashMap();
            r.putAll(genericParams);
        }
        return r;
    }
    
    public String getGenClassName()
    {
        if (obj == null)
            return "";
        if( genericParams != null )
            return JdiGenType.fromClassSignature((ClassType)obj.referenceType(), genericParams).toString();
        else
            return getClassName();
    }
    
    public String getStrippedGenClassName()
    {
        if( obj == null )
            return "";
        if( genericParams != null )
            return JdiGenType.fromClassSignature((ClassType)obj.referenceType(), genericParams).toString(true);
        else
            return JavaNames.stripPrefix(getClassName());
    }
    
    /**
     * Get the ClassType (com.sun.jdi.ClassType) object representing the class
     * of this remote object.
     * @return  the remote object's class type
     */
    public ClassType getClassType()
    {
        return (ClassType)obj.referenceType();
    }
}
