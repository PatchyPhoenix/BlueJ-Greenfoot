package bluej.extensions;

import bluej.Config;
import bluej.debugmgr.objectbench.ObjectWrapper;
import bluej.extensions.event.ApplicationEvent;
import bluej.extensions.event.ApplicationListener;
import bluej.extensions.event.CompileEvent;
import bluej.extensions.event.CompileListener;
import bluej.extensions.event.ExtensionEvent;
import bluej.extensions.event.ExtensionEventListener;
import bluej.extensions.event.InvocationEvent;
import bluej.extensions.event.InvocationListener;
import bluej.extensions.event.PackageEvent;
import bluej.extensions.event.PackageListener;
import bluej.extmgr.ExtensionWrapper;
import bluej.extmgr.PrefManager;
import bluej.pkgmgr.Package;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;
import bluej.pkgmgr.target.ClassTarget;
import java.awt.Frame;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import javax.swing.JMenuItem;

/**
 * A proxy object which provides services to BlueJ extensions.
 * From this class
 * an extension can obtain the projects and packages which BlueJ is currently displayng
 * and the classes and objects they contain. Fields and methods of these objects
 * can be inspected and invoked using an API based on Java's reflection API.
 *
 * Every effort has been made to retain the logic of the Reflection API and to provide
 * methods that behave in a very similar way.
 *
 * <PRE>
 * BlueJ
 *   |
 *   +---- BProject
 *             |
 *             +---- BPackage
 *                      |
 *                      +--------- BClass
 *                      |            |
 *                      +- BObject   + BConstructor
 *                                   |      |
 *                                   |      +- BObject
 *                                   |
 *                                   +---- BMethod
 *                                   |      |
 *                                   |      +- BObject
 *                                   |
 *                                   +---- BField
 *
 * </PRE>
 * Attempts to invoke methods on a BlueJ object made by an extension
 * after its <code>terminate()</code> method has been called will result
 * in an (unchecked) <code>ExtensionUnloadedException</code> being thrown.
 *
 * @version    $Id: BlueJ.java 2239 2003-10-30 11:14:59Z damiano $
 */

/*
 * Author Clive Miller, University of Kent at Canterbury, 2002
 * Author Damiano Bolla, University of Kent at Canterbury, 2003
 */
public class BlueJ
{
    private final ExtensionWrapper myWrapper;
    private final PrefManager prefManager;

    private PreferenceGenerator currentPrefGen = null;
    private MenuGenerator currentMenuGen = null;
    private Properties localLabels;

    private ArrayList eventListeners;
    // This is the queue for the whole of them
    private ArrayList applicationListeners;
    private ArrayList packageListeners;
    private ArrayList compileListeners;
    private ArrayList invocationListeners;


    /**
     * Constructor for a BlueJ proxy object.
     * See the ExtensionBridge class
     *
     * @param  aWrapper      Description of the Parameter
     * @param  aPrefManager  Description of the Parameter
     */
    BlueJ(ExtensionWrapper aWrapper, PrefManager aPrefManager)
    {
        myWrapper = aWrapper;
        prefManager = aPrefManager;

        eventListeners = new ArrayList();
        applicationListeners = new ArrayList();
        packageListeners = new ArrayList();
        compileListeners = new ArrayList();
        invocationListeners = new ArrayList();

        /* I do NOT want lazy initialization otherwise I may try to load it
         * may times just because I cannof find anything.
         * Or having state variables to know I I did load it but had nothing found
         */
        localLabels = myWrapper.getLabelProperties();
    }



    /**
     * Opens a project.
     *
     *
     * @param  directory  Where the project is stored.
     * @return            the BProject that describes the newly opened project or null if it cannot be opened.
     */
    public BProject openProject(File directory)
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        // Yes somebody may just call it with null, for fun..
        if (directory == null)
            return null;

        Project openProj = Project.openProject(directory.getAbsolutePath());
        if (openProj == null)
            return null;

        Package pkg = openProj.getPackage(openProj.getInitialPackageName());
        if (pkg == null)
            return null;

        // I make a new identifier out of this
        Identifier aProject = new Identifier(openProj, pkg);

        // This will make the frame if not already there.
        try {
            aProject.getPackageFrame();
        } catch (ExtensionException exc) {}

        return new BProject(aProject);
    }


    /**
     * Creates a new BlueJ project.
     *
     * @param  directory  where you want the project be placed, it must be writable.
     * @return            the newly created BProject if successful, null otherwise.
     */
    public BProject newProject(File directory)
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        String pathString = directory.getAbsolutePath();
        if (!pathString.endsWith(File.separator))
            pathString += File.separator;
        if (!Project.createNewProject(pathString))
            return null;
        return openProject(directory);
    }



    /**
     * Returns all currently open projects.
     * Returns an empty array if no projects are open.
     *
     * @return    The openProjects value
     */
    public BProject[] getOpenProjects()
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        int index;
        Iterator iter;
        Collection projects = Project.getProjects();
        BProject[] result = new BProject[projects.size()];

        for (iter = projects.iterator(), index = 0; iter.hasNext(); index++)
            result[index] = new BProject(new Identifier((Project) iter.next()));

        return result;
    }


    /**
     * Returns the currently selected package.
     * The current package is the one that is currently selected by the
     * user interface.
     * It can return null if there is no currently open package.
     *
     * @return    The currentPackage value
     */
    public BPackage getCurrentPackage()
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        // This is here and NOT into a BProject since it depends on user interface.

        PkgMgrFrame pmf = PkgMgrFrame.getMostRecent();
        // If there is nothing at all open there is no Frame open...
        if (pmf == null)
            return null;

        Package pkg = pmf.getPackage();
        // The frame may be there BUT have no package.
        if (pkg == null)
            return null;

        return new BPackage(new Identifier(pkg.getProject(), pkg));
    }


    /**
     * Returns the current frame being displayed.
     * Can be used (e.g.) as a "parent" frame for positioning modal dialogs.
     * If there is a package currently open, it's probably better to use its <code>getFrame()</code>
     * method to provide better placement.
     *
     * @return    The currentFrame value
     */
    public Frame getCurrentFrame()
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        return PkgMgrFrame.getMostRecent();
    }


    /**
     * Install a new menu generator for this extension.
     * If you want to delete a previously installed menu, then set it to null
     *
     *
     * @param  menuGen        The new menuGenerator value
     */
    public void setMenuGenerator(MenuGenerator menuGen)
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        currentMenuGen = menuGen;
    }


    /**
     * Returns the currently registered menu generator
     *
     * @return    The menuGenerator value
     */
    public MenuGenerator getMenuGenerator()
    {
        return currentMenuGen;
    }


    /**
     * Install a new preference panel for this extension.
     * If you want to delete a previously installed preference panel, then set it to null
     *
     *
     * @param  prefGen  a class instance that implements the PreferenceGenerator interface.
     */
    public void setPreferenceGenerator(PreferenceGenerator prefGen)
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        currentPrefGen = prefGen;
        prefManager.panelRevalidate();
    }


    /**
     * Returns the currently registered preference generator.
     *
     * @return    The preferenceGenerator value
     */
    public PreferenceGenerator getPreferenceGenerator()
    {
        return currentPrefGen;
    }


    /**
     * Returns the path of the <code>&lt;BLUEJ_HOME&gt;/lib</code> system directory.
     * This can be used to locate systemwide configuration files.
     * Having the directory you can then locate a file within it.
     *
     * @return    The systemLibDir value
     */
    public File getSystemLibDir()
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        return Config.getBlueJLibDir();
    }


    /**
     * Returns the path of the user configuration directory.
     * This can be used to locate user dependent information.
     * Having the directory you can then locate a file within it.
     *
     * @return    The userConfigDir value
     */
    public File getUserConfigDir()
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        return Config.getUserConfigDir();
    }


    /**
     * Returns a property from BlueJ's properties,
     * or the given default value if the property is not currently set.
     *
     *
     * @param  property  The name of the required global property
     * @param  def       The default value to use if the property cannot be found.
     * @return           the value of the property.
     */
    public String getBlueJPropertyString(String property, String def)
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        return Config.getPropString(property, def);
    }


    /**
     * Return a property associated with this extension from the standard BlueJ property repository.
     * You must use the setExtensionPropertyString to write any property that you want stored.
     * You can then come back and retrieve it using this function.
     *
     *
     * @param  property  The name of the required global property.
     * @param  def       The default value to use if the property cannot be found.
     * @return           the value of that property.
     */
    public String getExtensionPropertyString(String property, String def)
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        String thisKey = myWrapper.getSettingsString(property);
        return Config.getPropString(thisKey, def);
    }


    /**
     * Sets a property associated with this extension into the standard BlueJ property repository.
     * The property name does not need to be fully qualified since a prefix will be prepended to it.
     *
     *
     * @param  property  The name of the required global property
     * @param  value     the required value of that property.
     */
    public void setExtensionPropertyString(String property, String value)
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        String thisKey = myWrapper.getSettingsString(property);
        Config.putPropString(thisKey, value);
    }


    /**
     * Returns the language-dependent label with the given key.
     * The search order is to look first in the extension's <code>label</code> files and
     * if the requested label is not found in the BlueJ system <code>label</code> files.
     * Extensions' labels are stored in a Property format and must be jarred together
     * with the extension. The path searched is equivalent to the bluej/lib/[language]
     * style used for the BlueJ system labels. E.g. to create a set of labels which can be used
     * by English, Italian and German users of an extension, the following files would need to
     * be present in the extension's Jar file:
     * <pre>
     * lib/english/label
     * lib/italian/label
     * lib/german/label
     * </pre>
     * The files named <code>label</code> would contain the actual label key/value pairs.
     *
     * @param  key  Description of the Parameter
     * @return      The label value
     */
    public String getLabel(String key)
    {
        if (!myWrapper.isValid())
            throw new ExtensionUnloadedException();

        // If there are no label for this extension I can only return the system ones.
        if (localLabels == null)
            return Config.getString(key, key);

        // In theory there are label for this extension let me try to get them
        String aLabel = localLabels.getProperty(key, null);

        // Found what I wanted, job done.
        if (aLabel != null)
            return aLabel;

        // ok, the only hope is to get it from the system
        return Config.getString(key, key);
    }



    /**
     * Registers a listener for all the events generated by BlueJ.
     *
     * @param  listener  The feature to be added to the ExtensionEventListener attribute
     */
    public void addExtensionEventListener(ExtensionEventListener listener)
    {
        if (listener != null)
            eventListeners.add(listener);
    }


    /**
     * Removes the specified listener so that it no longer receives events.
     *
     * @param  listener  Description of the Parameter
     */
    public void removeExtensionEventListener(ExtensionEventListener listener)
    {
        if (listener != null)
            eventListeners.remove(listener);
    }


    /**
     * Registers a listener for application events.
     *
     * @param  listener  The feature to be added to the ApplicationListener attribute
     */
    public void addApplicationListener(ApplicationListener listener)
    {
        if (listener != null)
            applicationListeners.add(listener);
    }


    /**
     * Removes the specified listener so that it no longer receives events.
     *
     * @param  listener  Description of the Parameter
     */
    public void removeApplicationListener(ApplicationListener listener)
    {
        if (listener != null)
            applicationListeners.remove(listener);
    }


    /**
     * Registers a listener for package events.
     *
     * @param  listener  The feature to be added to the PackageListener attribute
     */
    public void addPackageListener(PackageListener listener)
    {
        if (listener != null)
            packageListeners.add(listener);
    }


    /**
     * Removes the specified listener so that it no longer receives events.
     *
     * @param  listener  Description of the Parameter
     */
    public void removePackageListener(PackageListener listener)
    {
        if (listener != null)
            packageListeners.remove(listener);
    }


    /**
     * Registers a listener for compile events.
     *
     * @param  listener  The feature to be added to the CompileListener attribute
     */
    public void addCompileListener(CompileListener listener)
    {
        if (listener != null)
            compileListeners.add(listener);
    }


    /**
     * Removes the specified listener so that it no longer receives events.
     *
     * @param  listener  Description of the Parameter
     */
    public void removeCompileListener(CompileListener listener)
    {
        if (listener != null)
            compileListeners.remove(listener);
    }


    /**
     * Registers a listener for invocation events.
     *
     * @param  listener  The feature to be added to the InvocationListener attribute
     */
    public void addInvocationListener(InvocationListener listener)
    {
        if (listener != null)
            invocationListeners.add(listener);
    }


    /**
     * Removes the specified listener so no that it no longer receives events.
     *
     * @param  listener  Description of the Parameter
     */
    public void removeInvocationListener(InvocationListener listener)
    {
        if (listener != null)
            invocationListeners.remove(listener);
    }


    /**
     * Dispatch this event to the listeners for the ALL events.
     *
     * @param  event  Description of the Parameter
     */
    private void delegateExtensionEvent(ExtensionEvent event)
    {
        if ( eventListeners.isEmpty()) return;
        
        List aList=Collections.unmodifiableList(eventListeners);

        for (Iterator iter = aList.iterator(); iter.hasNext(); ) {
            ExtensionEventListener eventListener = (ExtensionEventListener) iter.next();
            eventListener.eventOccurred(event);
        }
    }


    /**
     * Dispatch this event to the listeners for the Application events.
     *
     * @param  event  Description of the Parameter
     */
    private void delegateApplicationEvent(ApplicationEvent event)
    {
        if ( applicationListeners.isEmpty()) return;
        
        List aList=Collections.unmodifiableList(applicationListeners);

        for (Iterator iter = aList.iterator(); iter.hasNext(); ) {
            ApplicationListener eventListener = (ApplicationListener) iter.next();
            // Just this for the time being.
            eventListener.blueJReady(event);
        }
    }


    /**
     * Dispatch this event to the listeners for the Package events.
     *
     * @param  event  Description of the Parameter
     */
    private void delegatePackageEvent(PackageEvent event)
    {
        if ( packageListeners.isEmpty()) return;
        
        int thisEvent = event.getEvent();
        List aList=Collections.unmodifiableList(packageListeners);

        for (Iterator iter = aList.iterator(); iter.hasNext(); ) {
            PackageListener eventListener = (PackageListener) iter.next();
            if (thisEvent == PackageEvent.PACKAGE_OPENED)
                eventListener.packageOpened(event);
            if (thisEvent == PackageEvent.PACKAGE_CLOSING)
                eventListener.packageClosing(event);
        }
    }


    /**
     * Dispatch this event to the listeners for the Compile events.
     *
     * @param  event  Description of the Parameter
     */
    private void delegateCompileEvent(CompileEvent event)
    {
        if ( compileListeners.isEmpty()) return;
        
        int thisEvent = event.getEvent();
        List aList=Collections.unmodifiableList(compileListeners);

        for (Iterator iter = aList.iterator(); iter.hasNext(); ) {
            CompileListener eventListener = (CompileListener) iter.next();
            if (thisEvent == CompileEvent.COMPILE_START_EVENT)
                eventListener.compileStarted(event);
            if (thisEvent == CompileEvent.COMPILE_ERROR_EVENT)
                eventListener.compileError(event);
            if (thisEvent == CompileEvent.COMPILE_WARNING_EVENT)
                eventListener.compileWarning(event);
            if (thisEvent == CompileEvent.COMPILE_FAILED_EVENT)
                eventListener.compileFailed(event);
            if (thisEvent == CompileEvent.COMPILE_DONE_EVENT)
                eventListener.compileSucceeded(event);
        }
    }


    /**
     * Dispatch this event to the listeners for the Invocation events.
     *
     * @param  event  Description of the Parameter
     */
    private void delegateInvocationEvent(InvocationEvent event)
    {
        if ( invocationListeners.isEmpty()) return;
        
        List aList=Collections.unmodifiableList(invocationListeners);
        
        for (Iterator iter = aList.iterator(); iter.hasNext(); ) {
            InvocationListener eventListener = (InvocationListener) iter.next();
            eventListener.invocationFinished(event);
        }
    }


    /**
     * Informs any registered listeners that an event has occurred.
     * This will call the various dispatcher as needed.
     * Errors will be trapped by the caller.
     * NOTE: The return type int is a simple placeholder to use the simple return syntax
     *
     * @param  event  Description of the Parameter
     */
    void delegateEvent(ExtensionEvent event)
    {
        delegateExtensionEvent(event);
        if (event instanceof ApplicationEvent)
            delegateApplicationEvent((ApplicationEvent) event);
        else if (event instanceof PackageEvent)
            delegatePackageEvent((PackageEvent) event);
        else if (event instanceof CompileEvent)
            delegateCompileEvent((CompileEvent) event);
        else if (event instanceof InvocationEvent)
            delegateInvocationEvent((InvocationEvent) event);
    }



    /**
     * Calls the extension to get the right menu item.
     * This is already wrapped for errors in the caller.
     * It is right for it to create a new wrapped object each time.
     * We do not want extensions to share objects.
     * It is here since it can access all constructors directly.
     *
     * @param  attachedObject  Description of the Parameter
     * @return                 The menuItem value
     */
    JMenuItem getMenuItem(Object attachedObject)
    {
        if (currentMenuGen == null)
            return null;

        if (attachedObject == null) {
            JMenuItem anItem = currentMenuGen.getToolsMenuItem(null);
            if ( anItem != null ) 
                return anItem;

            // Try to use the old deprecated method.
            return currentMenuGen.getMenuItem();
        }

        if (attachedObject instanceof Package) {
            Package attachedPkg = (Package) attachedObject;
            Identifier anId = new Identifier(attachedPkg.getProject(), attachedPkg);
            return currentMenuGen.getToolsMenuItem(new BPackage(anId));
        }

        if (attachedObject instanceof ClassTarget) {
            ClassTarget aTarget = (ClassTarget) attachedObject;
            String qualifiedClassName = aTarget.getQualifiedName();
            Package attachedPkg = aTarget.getPackage();
            Identifier anId = new Identifier(attachedPkg.getProject(), attachedPkg, qualifiedClassName);
            return currentMenuGen.getClassMenuItem(new BClass(anId));
        }

        if (attachedObject instanceof ObjectWrapper) {
            ObjectWrapper aWrapper = (ObjectWrapper) attachedObject;
            return currentMenuGen.getObjectMenuItem(new BObject(aWrapper));
        }

        return null;
    }


    /**
     * Post a notification of a menu going to be display
     */
    void postMenuItem(Object attachedObject, JMenuItem onThisItem )
    {
        if (currentMenuGen == null)
            return;

        if (attachedObject == null) {
            // Only BPackages can be null when a menu is invoked
            currentMenuGen.notifyPostToolsMenu(null,onThisItem);
            return;
            }

        if (attachedObject instanceof Package) {
            Package attachedPkg = (Package) attachedObject;
            Identifier anId = new Identifier(attachedPkg.getProject(), attachedPkg);
            currentMenuGen.notifyPostToolsMenu(new BPackage(anId),onThisItem);
        }

        if (attachedObject instanceof ClassTarget) {
            ClassTarget aTarget = (ClassTarget) attachedObject;
            String qualifiedClassName = aTarget.getQualifiedName();
            Package attachedPkg = aTarget.getPackage();
            Identifier anId = new Identifier(attachedPkg.getProject(), attachedPkg, qualifiedClassName);
            currentMenuGen.notifyPostClassMenu(new BClass(anId),onThisItem);
        }

        if (attachedObject instanceof ObjectWrapper) {
            ObjectWrapper aWrapper = (ObjectWrapper) attachedObject;
            currentMenuGen.notifyPostObjectMenu(new BObject(aWrapper),onThisItem);
        }
    }




}
