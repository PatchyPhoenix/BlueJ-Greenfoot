package bluej.pkgmgr;

import bluej.Config;
import bluej.BlueJEvent;
import bluej.BlueJEventListener;
import bluej.pkgmgr.Package;
import bluej.utility.Utility;
import bluej.utility.Debug;
import bluej.utility.DialogManager;
import bluej.utility.JavaNames;
import bluej.debugger.*;
import bluej.classmgr.*;
import bluej.views.View;

import java.util.*;
import java.io.File;
import java.io.IOException;

/**
 * A BlueJ Project.
 *
 * @author  Michael Kolling
 * @author  Axel Schmolitzky
 * @author  Andrew Patterson
 * @version $Id: Project.java 583 2000-06-26 01:51:17Z mik $
 */
public class Project
    implements BlueJEventListener
{
    // static fields

    /**
     * collection of all open projects. the canonical name of the project
     * directory is used as the key.
     */
    private static Map projects = new HashMap();

    /**
     * Open a BlueJ project.
     * @return The Project representing the BlueJ project
     *          that has this directory within it or
     *          null if there were no bluej.pkg files in the
     *          specified directory.
     */
    public static Project openProject(String projectPath)
    {
        String startingPackageName;
        File projectDir, startingDir;

        try {
             startingDir = new File(projectPath).getCanonicalFile();
        }
        catch(IOException ioe)
        {
            Debug.message("could not resolve directory " + projectPath);
            ioe.printStackTrace();
            // give a proper user message here
            return null;
        }

        // if there is an existing bluej package file here we
        // need to find the root directory of the project
        // (and while we are at it we will construct the qualified
        //  package name to let us open the PkgMgrFrame at the
        //  right point)
        if (Package.isBlueJPackage(startingDir)) {
            File curDir = startingDir;
            File lastDir = null;

            startingPackageName = "";

            while(curDir != null && Package.isBlueJPackage(curDir)) {
                if(lastDir != null)
                    startingPackageName = "." + lastDir.getName() +
                                          startingPackageName;

                lastDir = curDir;
                curDir = curDir.getParentFile();
            }

            if(startingPackageName.length() > 0)
                if(startingPackageName.charAt(0) == '.')
                    startingPackageName = startingPackageName.substring(1);

            // lastDir is now the directory holding the topmost bluej
            // package file in the directory heirarchy

            projectDir = lastDir;
        }
        else {
            Debug.message("no BlueJ package file found in directory " + startingDir);
            return null;
        }

        // check whether it already exists
        Project proj = (Project)projects.get(projectDir);

        if(proj == null) {
            proj = new Project(projectDir);
            projects.put(projectDir, proj);
        }

        proj.initialPackageName = startingPackageName;
        return proj;
    }

    /**
     * Remove a project from the collection of currently open projects.
     */
    public static void closeProject(Project project)
    {
        BlueJEvent.removeListener(project);
        projects.remove(project.getProjectDir().getPath());
    }


    public static boolean createNewProject(String projectPath)
    {
        if (projectPath != null) {

            // check whether name is already in use
            File dir = new File(projectPath);
            if(dir.exists()) {
                DialogManager.showError(null, "directory-exists");
                return false;
            }

            if(dir.mkdir()) {
                File newpkgFile = new File(dir, Package.pkgfileName);
                File newreadmeFile = new File(dir, Package.readmeName);

                try {
                    if(newpkgFile.createNewFile()) {
                        newreadmeFile.createNewFile();
                        return true;
                    }
                }
                catch(IOException ioe)
                {
                }
            }
        }
        return false;
    }

    /* ------------------- end of static declarations ------------------ */

    // instance fields

    /** the path of the project directory. */
    private File projectDir;

    /** collection of open packages in this project
      (indexed by the qualifiedName of the package).
       The unnamed package ie root package of the package tree
       can be obtained by retrieving "" from this collection */
    private Map packages;

    /** a ClassLoader for the local virtual machine */
    private ClassLoader loader;

    /** a ClassLoader for the remote virtual machine */
    private DebuggerClassLoader debuggerLoader;

    /** when a project is opened, the user may specify a
       directory deep into the projects directory structure.
       BlueJ will correctly find the top of this package
       heirarchy but the bit of the name left over will be
       put into this variable
       ie if opening /home/user/foo/com/sun where
       /home/user/foo is the project directory, this variable
       will be set to com.sun */
    private String initialPackageName = "";

    /** the documentation generator for this project. */
    private DocuGenerator docuGenerator;

    /* ------------------- end of field declarations ------------------- */

    /**
     * Construct a project in the directory projectDir.
     * This must contain the root bluej.pkg of a nested package
     * (this should by its nature be the unnamed package).
     */
    private Project(File projectDir)
    {
        if(projectDir == null)
            throw new NullPointerException();

        this.projectDir = projectDir;

        packages = new TreeMap();
        packages.put("", new Package(this));

        BlueJEvent.addListener(this);

        docuGenerator = new DocuGenerator(this);
    }

    /**
     * Return the name of the project.
     */
    public String getProjectName()
    {
        return projectDir.getName();
    }

    /**
     * Return the location of the project.
     */
    public File getProjectDir()
    {
        return projectDir;
    }

    /**
     * A string which uniquely identifies this project
     */
    public String getUniqueId()
    {
        return "BJID" + getProjectDir().getPath();
    }

    /**
     *
     */
    public String getInitialPackageName()
    {
        return initialPackageName;
    }

    /**
     * Return a package from the project.
     * This will construct the package
     * if need be or return it from the cache
     * if already existing. All parent packages
     * on the way to the root of the package tree will
     * also be constructed.
     *
     * @param qualifiedName the package name to fetch in dot qualified
     *                      notation ie java.util or "" for unnamed package
     */
    public Package getPackage(String qualifiedName)
    {
        Package existing = (Package) packages.get(qualifiedName);

        if (existing != null)
            return existing;

        if (qualifiedName.length() > 0) // should always be true (the unnamed package
                                        // always exists in the package collection)
        {
            Package pkg = new Package(this, JavaNames.getBase(qualifiedName),
                                    getPackage(JavaNames.getPrefix(qualifiedName)));

            packages.put(qualifiedName, pkg);

            return pkg;
        }

        throw new IllegalStateException("Project.getPackage()");
    }

    /**
     * Get the names of all packages in this project.
     * @return an array of String containing the fully qualified names
     * of the packages in this project.
     */
    public List getPackageNames()
    {
        // CURRENTLY THIS IMPLEMENTATION JUST RETURNS THE OPENED PACKAGES!
        // TBD: RETURN ALL PACKAGES

        return new ArrayList(packages.keySet());
    }

    /**
     * Generate documentation for the whole project.
     * @return "" if everything was alright, an error message otherwise.
     */
    public String generateDocumentation()
    {
        return docuGenerator.generateProjectDocu();
    }


    /**
     * Save all open packages of this project.
     */
    public void saveAll()
    {
        PkgMgrFrame[] frames = PkgMgrFrame.getAllProjectFrames(this);

        for(int i=0; i< frames.length; i++) {
            frames[i].doSave();
        }
    }

    /**
     * Reload all constructed package of this project.
     *
     * This function is used after a major change to the contents
     * of the project directory ie an import.
     */
    public void reloadAll()
    {
        Iterator i = packages.values().iterator();

        while(i.hasNext()) {
            Package pkg = (Package) i.next();

            pkg.reload();
        }
    }

    /**
     * Get the ClassLoader for this project.
     * The ClassLoader load classes on the local VM.
     */
    private synchronized ClassLoader getLocalClassLoader()
    {
        if(loader == null)
            loader = ClassMgr.getLoader(getProjectDir());

        return loader;
    }

    /**
     * Removes the current classloader, and removes
     * references to classes loaded by it (this includes removing
     * the objects from all object benches of this project).\
     * Should be run whenever a source file changes
     */
    synchronized void removeLocalClassLoader()
    {
       if(loader != null) {
            // remove bench objects for all frames in this project
            PkgMgrFrame[] frames = PkgMgrFrame.getAllProjectFrames(this);

            for(int i=0; i< frames.length; i++)
                frames[i].getObjectBench().removeAll(getUniqueId());

            // remove views for classes loaded by this classloader
            View.removeAll(loader);

            loader = null;
        }
    }

    /**
     * Get the DebuggerClassLoader for this package.
     * The DebuggerClassLoader load classes on the remote VM
     * (the machine used for user code execution).
     */
    public synchronized DebuggerClassLoader getRemoteClassLoader()
    {
        if(debuggerLoader == null)
            debuggerLoader = Debugger.debugger.createClassLoader
                                                (getUniqueId(), getProjectDir().getPath());
        return debuggerLoader;
    }

    /**
     * Removes the remote VM classloader.
     * Should be run whenever a source file changes.
     */
    synchronized void removeRemoteClassLoader()
    {
        if(debuggerLoader != null) {
            Debugger.debugger.removeClassLoader(debuggerLoader);
            debuggerLoader = null;
        }
    }

    /**
     * Loads a class using the current classLoader
     */
    public Class loadClass(String className)
    {
        try {
            return getLocalClassLoader().loadClass(className);
        } catch(ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Return a string representing the classpath for this project.
     */
    public String getClassPath()
    {
        // construct a class path out of all our class path entries and
        // our current class directory
        StringBuffer c = new StringBuffer();

        Iterator i = ClassMgr.getClassMgr().getAllClassPathEntries();

        while(i.hasNext()) {
            ClassPathEntry cpe = (ClassPathEntry)i.next();

            c.append(cpe.getPath());
            c.append(File.pathSeparator);
        }

        c.append(getProjectDir().getPath());    // for classes in current project

        return c.toString();
    }

    /**
     * Convert a filename into a fully qualified Java name.
     * Returns null if the file is outside the project
     * directory.
     *
     * The behaviour of this function is not guaranteed if
     * you pass in a directory name. It is meant for filenames
     * like /foo/bar/p1/s1/TestName.java
     *
     * An example of its use is if your project was in the
     * directory /foo/bar and you passed in
     * /foo/bar/p1/s1/TestName.java the function would
     * return p1.s1.TestName
     */
    public String convertPathToPackageName(String pathname)
    {
        try {
            File pathfile = new File(pathname).getCanonicalFile();
            File parent = null;
            String name = "";
            int firstDot;

            while((parent = pathfile.getParentFile()) != null) {
                if(pathfile.equals(getProjectDir())) {
                    return name;
                }

                if (name == "") {
                    name = pathfile.getName();

                    if((firstDot = name.indexOf('.')) >= 0) {
                        name = name.substring(0, firstDot);
                    }
                }
                else {
                    name = pathfile.getName() + "." + name;
                }

                pathfile = parent;
            }
        }
        catch(IOException ioe) { }

        return null;
    }

    // ---- BlueJEventListener interface ----

    /**
     *  A BlueJEvent was raised. Check whether it is one that we're interested
     *  in.
     */
    public void blueJEvent(int eventId, Object arg)
    {
        DebuggerThread thread;

        switch(eventId) {
        case BlueJEvent.BREAKPOINT:
            thread = (DebuggerThread)arg;
            if(thread.getParam() == this)
                hitBreakpoint(thread);
            break;
        case BlueJEvent.HALT:
            thread = (DebuggerThread)arg;
            if(thread.getParam() == this)
                hitHalt(thread);
            break;
        case BlueJEvent.CONTINUE:
            thread = (DebuggerThread)arg;
            if(thread.getParam() == this)
                executionContinued();
            break;
        case BlueJEvent.SHOW_SOURCE:
            thread = (DebuggerThread)arg;
            if(thread.getParam() == this)
                showSourcePosition(thread, false);
            break;
        }
    }

    // ---- end of BlueJEventListener interface ----

    /**
     * executionContinued - indicate in the interface that the machine
     *  is executing again.
     */
    private void executionContinued()
    {
//         pkg.removeStepMarks();
//         progressButton.setIcon(workingIcon);
    }

    /**
     * hitBreakpoint - A breakpoint in this package was hit.
     */
    private void hitBreakpoint(DebuggerThread thread)
    {
        String className = thread.getClass(0);  // fully qualified
        String packageName = JavaNames.getPrefix(className);

        getPackage(packageName).hitBreakpoint(thread);
    }

    /**
     * hitHalt - execution stopped interactively or after a step.
     */
    private void hitHalt(DebuggerThread thread)
    {
        String className = thread.getClass(0);  // fully qualified
        String packageName = JavaNames.getPrefix(className);

        getPackage(packageName).hitHalt(thread);
    }

    /**
     * showSourcePosition - The debugger display needs updating.
     */
    private void showSourcePosition(DebuggerThread thread,
                                    boolean updateDebugger)
    {
        String className = thread.getClass(0);  // fully qualified
        String packageName = JavaNames.getPrefix(className);

        getPackage(packageName).showSourcePosition(thread, updateDebugger);
    }

}
