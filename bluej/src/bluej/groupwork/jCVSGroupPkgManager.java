package bluej.groupwork;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.*;

import bluej.pkgmgr.*;
import bluej.pkgmgr.Package;
import bluej.utility.Debug;
import bluej.utility.SortedProperties;
import bluej.utility.DialogManager;
import bluej.Config;

import com.ice.cvsc.*;
import com.ice.jcvsii.*;
import com.ice.pref.UserPrefs;
import com.ice.util.AWTUtilities;

/**
** This Class implements the Interface GroupPkgManager using jCVS 
**
** @author Markus Ostman, but some of the code structures in here
** are based on jCVS sourcecode (Tim Endres), modified to fit Bluej purposes.
**
**/
public final class jCVSGroupPkgManager     
    implements GroupPkgManager
{
    // public static variables
    //./temp the manager object itself. This is not used
    //but maybe we should just work with a static manager
    //public static jCVSGroupPkgManager groupPkgMgr=new jCVSGroupPkgManager(); 
    
    // private variables
    private CVSProject project;
    private InfoDialog info;
    private Properties props;
    //Global collection of conflicting files
    //this might be useful for other files as well
    //This information should probably be stored in the group info
    //since there might be more than one project but there should 
    //maybe just be one manager object?
    private Collection conflictFiles;
    private Collection addedClasses;
    private Collection removedClasses;
    //the system dependent line separator
    private final String endOfLine = "\n";
    //Sync sync = new Sync();
    //CVS variables
    private boolean traceReq = false;
    private boolean traceResp = false;
    private boolean traceProc = false;
    private boolean traceTCP = false;

    // =========================== PUBLIC METHODS ===========================
    //./temp: Is this something we need?
    public jCVSGroupPkgManager()
    {	
        this.loadPrefs();
        //In case we start using these Collections in several threads
        //we need to synchronize them.
        this.conflictFiles = Collections.synchronizedList(new ArrayList());
        this.addedClasses = Collections.synchronizedList(new ArrayList());
        this.removedClasses = Collections.synchronizedList(new ArrayList());

        //this would probably cause problems on NT
//         try{
//             endOfLine = System.getProperty("line.separator");
//         }catch (SecurityException se){
//             Debug.reportError("couldn't access system property: "+se);
//         }
    }

    /**
     * Open a Group package
     * The method is inherited from GroupPkgManager
     * Currently this method is just a test site for new functions
     *
     * @param
     * @param frame  The current Bluej frame
     * @returns  a Group Package or null if someting went wrong
     */
    public void openGrpPackage(PkgMgrFrame frame )
    {
	ConflictResolver res=new ConflictResolver();
        //String pkgfile=frame.getPackage().getDirName();
        res.resolveConflict("/home/markus/bluejPrototype/resolving/test.pkg");
    }

    /**
     * Checkout a local copy of a Group package from the Master source 
     * The method is inherited from GroupPkgManager
     * @param 
     * @param frame  The current Bluej frame
     * @returns  a Group Package or null if something went wrong
     */
    public void checkoutPackage(PkgMgrFrame frame )
    {
	GroupWorkDialog dialog=new GroupWorkDialog(frame);
	if(dialog.displayCheckout())
	    ;
    }

    /**
     * Creates a new module in the Repository using a specified local package
     *
     * Depending on settings in group.defs this method behaves differently.
     * With pkg info:
     * The local package temporarily changes into a group package, 
     * gets imported to the repository and then gets overwritten by a 
     * checked out version of the new group package.
     * Without pkg info:
     * The Java source files are imported to the Repository.
     *  
     * 
     * The method is inherited from GroupPkgManager
     * @param 
     * @param frame  The current Bluej frame
     * @returns  void
     */
    public void importPackage(PkgMgrFrame frame )
    {
        frame.setStatus(Config.getString("groupwork.importing"));
        GroupWorkDialog dialog=new GroupWorkDialog(frame);
        Importer importer=new Importer(frame);
        Package pkg = frame.getPackage();
        
        //Depending on the settings in group.defs the import is 
        //performed differently.
        if (props.getProperty("group.usesPkgInfo", "false").equals("true")){
	    //Before import we must turn this package into a group package
            //./temp this should probably be done further down the track 
	    //in case of a cancel
             pkg.turnIntoGroupPackage(true);

             //And Save the change, note that we are saving using dirName
             //by default, can be relative.
             if(pkg.save()){
                 //./temp What if the import doesn't work?
                 importer.performImport();
                 
                 //If the import was canceled, we restore the project 
                 //and abort the rest.
                 if(importer.getCancel()){
                     pkg.turnIntoGroupPackage(false);
                     pkg.save();
                     frame.clearStatus();
                     frame.repaint();
                     return;
                 }

                 //Need to get some information from the Package
                 //before we remove it from the Frame
                 String baseDir = frame.getPackage().getBaseDir();
                 
                 //Define the module in the repository
                 if(defineModule(importer.getPassword(),
                                 importer.getUserName(),
                                 importer.getModule(), baseDir, 
                                 importer.getGroupName(), frame)){

                     //close the package
                     //Must be careful with the order in which this is
                     //done so that nothing important gets overwritten
                     frame.removePackage();
                     
                     //Checkout the newly created project
                     dialog.getCheckOutPanel().doCheckOut
                         (importer.getPassword(),
                          importer.getUserName(),
                          importer.getModule(),
                          importer.getGroupName(),
                          baseDir, frame, true);
                 }
                 else{
                     pkg.turnIntoGroupPackage(false);
                     pkg.save();
                     frame.clearStatus();
                     frame.repaint();
                     return; 
                 }
             }
             else
                 Debug.reportError("Could not save Package");
        }
        else{
            if(pkg.save()){
                //./temp this is not set up properly
                importer.performImport();
                DialogManager.showText(frame,
                                       "Performing Import");

                //Need to get some information from the Package
                //before we remove it from the Frame
                String baseDir = frame.getPackage().getBaseDir();
                String module = importer.getModule();

                //close the package
                //Must be careful with the order in which this is
                //done so that nothing important gets overwritten
                frame.removePackage();

                //Checkout the newly created project
                //Checkout to directory where project is located.
                //Imported files will be overwritten. 
                dialog.getCheckOutPanel().doCheckOut(importer.getPassword(),
                                                     importer.getUserName(),
                                                     module, 
                                                     importer.getGroupName(),
                                                     baseDir, frame,
                                                     true);
            }
            else
                Debug.reportError("Could not save Package");
        }
    }//End importPackage

    /**
     * Performs an update from the repository 
     * 
     * The method stores the project password and local directory 
     * temporarily and then closes the current package.
     * The update is performed and the updated package is opened. 
     * @param frame  The current Frame   
     * @param implicit   was this an implicit call or not
     *
     * @returns True if successful, false if not
     */
    public boolean updatePkg(PkgMgrFrame frame, boolean implicit )
    {
        frame.setStatus(Config.getString("groupwork.updating"));
        //before we store the password we must make sure it is there.
        frame.getPackage().getGroupInfo().verifyLogin();
	this.project=frame.getPackage().getGroupInfo().getProject();
	String passw=this.project.getPassword();
        frame.getPackage().save();
	String pkgDir=frame.getPackage().getDirName();
        SortedProperties propsLocal = loadPkgFile(pkgDir);
        Debug.message("jCVSGrpPkgMgr,140:" + pkgDir);

        //This only needs to be done if we are using the pkg file.
        if(props.getProperty("group.usesPkgInfo","false").equals("true")){

            //After loading the .pkg file we remove it to avoid conflicts
            File pkg = new File(pkgDir + File.separator + "bluej.pkg");
            try{
                if(pkg.delete())
                    Debug.message("Deleting pkg file before update");
            }catch(SecurityException se){
                Debug.reportError("Error deleting pkg file" + 
                                  ": " + se);
            }
        }
        //Was this the thing that caused the bug when everything "froze"
        //this should probably be done still, somewhere.
        //if(!implicit)
        //  frame.removePackage();
	if(this.performCommand(frame, UPDATE, null, false)){
            if(props.getProperty("group.usesPkgInfo","false").equals("true")){

                GroupJobQueue.getJobQueue().addJob("update pkgfile",
                                                 this.new UpdatePkgRunner(pkgDir, passw, frame, propsLocal), this.new JobMonitor());
            }
            else{
                GroupJobQueue.getJobQueue().addJob("update pkgfile", 
                                                 this.new UpdatePkgRunner(pkgDir, passw, frame, propsLocal), this.new JobMonitor());
            }
            return true;
	}
	else{
	    Debug.message("JCVSGrpPkgMgr,line147: trouble updating");
	    return false;
	}
    }//End updatePkg

    /**
     * Performs a commit to the repository 
     *  
     * @param frame  The current Frame   
     *
     * @returns True if successful, false if not
     */
    public boolean commitPkg(PkgMgrFrame frame)
    {
        // Check that they realise that this will change the repository.
	//int answer = DialogManager.askQuestion(frame, "really-commit");
        //String logMsg = DialogManager.askString(frame, "ask-logmsg");
        // if they agree
        if(frame.getPackage().save()){
            //Save, othervise changes to the pkg file will not be committed
            //frame.getPackage().save();
            frame.setStatus(Config.getString("groupwork.committing"));
            if(this.performCommand(frame, COMMIT, null, false)){
                return true;
            }
            else{
                Debug.message("JCVSGrpPkgMgr,line165: trouble committing");
                return false;
            }
        }
        else
            return false;
    }

    /**
     * Adds a Class to the package. 
     *
     * This is only a local change so in order to change the Repository
     * a commit must be performed
     * The method is inherited from GroupPkgManager
     * 
     * @param frame  The current Frame   
     * @param classFilePath  The file path of the added class 
     * @returns True if successful, false if not
     */
    public boolean addClass(PkgMgrFrame frame, String classFilePath)
    {
        //Get the CVS Project of the package
	this.project=frame.getPackage().getGroupInfo().getProject();
        
	//Create the new entry. It is crucial that all the paths
        //are set correctly 
        CVSEntryVector entries = new CVSEntryVector();
	entries.appendEntry(createAddEntry(classFilePath, 
                                           frame.getPackage().getDirName(),
                                           this.project.getRepository()));

	//Perform the command
	if(this.performCommand(frame, ADD, entries, false)){
	    return true;
	}
	else{
	    Debug.message("JCVSGrpPkgMgr,line178: trouble adding");
	    return false;
	}
    }

    /**
     * Removes a Class from the package. 
     *
     * This is only a local change so in order to change the Repository
     * a commit must be performed.
     * The method is inherited from GroupPkgManager
     * 
     * @param frame  The current Frame   
     * @param classFilePath  The file path of the removed class 
     * @returns True if successful, false if not
     */
    public boolean removeClass(PkgMgrFrame frame, String classFilePath)
    {
        //Get the CVS Project of the package 
	this.project=frame.getPackage().getGroupInfo().getProject();

	//Create the new entry. It is crucial that all the paths
        //are set correctly 
        CVSEntryVector entries = new CVSEntryVector();
	entries.appendEntry(findRemoveEntry(classFilePath));

	//Perform the command
	if(this.performCommand(frame, REMOVE, entries, false)){
	    return true;
	}
	else{
	    Debug.message("JCVSGrpPkgMgr,line178: trouble removing");
	    return false;
	}
    }

    /**
     * Checks the status of the files in the Package
     *  
     * @param frame  The current Frame   
     * @param
     * @returns True if successful, false if not
     */
    public boolean pkgStatus(PkgMgrFrame frame)
    {
	if(this.performCommand(frame, STATUS, null, false)){
	    return true;
	}
	else{
	    Debug.message("GrpPkgMgr,line247: trouble with status check");
	    return false;
	}
    }

    /**
     * Displays the log messages for the files in the Package
     *  
     * @param frame  The current Frame   
     * @param
     * @returns True if successful, false if not
     */
    public boolean pkgLog(PkgMgrFrame frame)
    {
	if(this.performCommand(frame, LOG, null, false)){
	    return true;
	}
	else{
	    Debug.message("JCVSGrpPkgMgr,line247: trouble with Log");
	    return false;
	}
    }

    /**
     * Removes the local working directory in a safe way,
     * i.e. makes sure that there are no changes etc. that have not 
     * been committed. This method is not working at the moment
     *  
     * @param frame        The current Frame   
     * @param releaseFile  The file that we want to release
     *                     (project directory)
     *
     * @returns True if successful, false if not
     */
    public boolean releasePkg(PkgMgrFrame frame, File releaseFile)
    {
        //we need something here this.project = ?;
       //  CVSEntryVector entries = new CVSEntryVector();
//         CVSEntry entry = new CVSEntry();
        
        //Get all entries in the project that we want to release
	// this.project.getRootEntry().addAllSubTreeEntries
// 	    ( entries = new CVSEntryVector() );
        
        if(this.performCommand(frame, RELEASE, null, false)){
	    return true;
	}
	else{
	    Debug.message("JCVSGrpPkgMgr,line247: trouble with release");
	    return false;
	}
    }

    /**
     * Perform group command
     *
     * Performs the various group commands by building a CVS request
     * from the given command. The active project in the given Frame then 
     * performs the request.
     * The method is inherited from GroupPkgManager 
     *
     * @param frame      The current Frame
     * @param command    The command  
     * @param entries    The entries to act upon
     * @param implicit   Is this an implicit call to the method  
     * @returns True if ok
     */
    public boolean performCommand(PkgMgrFrame frame, int command,
				  CVSEntryVector entries, boolean implicit )
    {
	String cvsCommand=parseCommand(command);
	//./temp: this needs to be done more properly
	CVSArgumentVector arguments=CVSArgumentVector.parseArgumentString("-R");
	String fdbkStr;
	boolean allok = true;
	ResourceMgr rmgr = ResourceMgr.getInstance();
	CVSRequest request = new CVSRequest();	

	if(frame.getPackage().getGroupInfo() != null
           && command != CHECKOUT){
            frame.getPackage().getGroupInfo().verifyLogin();
	    this.project=frame.getPackage().getGroupInfo().getProject();
        }
	
	//./temp This is just to see what is happening 
	Debug.message("GrpPkgMgr, line213: "+project.getRootEntry());
	Debug.message("GrpPkgMgr, line214: "+project.getRepository());
	Debug.message("GrpPkgMgr, line215: "+project.getLocalRootPath());
        Debug.message("GrpPkgMgr, line217: "+cvsCommand);
	this.info = new InfoDialog(frame);
	fdbkStr = rmgr.getUIString( "project.fdbk.buildcvsreq" );
	Debug.message("jCVSGrpPkgMgr,line219: "+fdbkStr);

	request.setArguments( new CVSArgumentVector() );
	request.setGlobalArguments( new CVSArgumentVector() );
	
	request.traceRequest = this.traceReq;
	request.traceResponse = this.traceResp;
	request.traceProcessing = this.traceProc;
	request.traceTCPData = this.traceTCP;

        //./temp this is temporary moved, should be just above ( ! allok)
        allok = request.parseControlString( cvsCommand  );

        //If it is an implicit call, we don't want to see the response
        //Note! the spelling error in is not mine
        if(implicit)
            request.displayReponse = false;
        
	if ( arguments != null ){
            //./temp this is very weird, probably we will not have to worry 
            //about arguments, but if add or Log we can't have -R
            if ( "add".equals(request.getCommand()) ||
                 "log".equals(request.getCommand()) ||
                 "release".equals(request.getCommand())){
                request.parseArgumentString("");
            }
            else{
                //If the request is update then -d means create new files/dirs
                if("update".equals(request.getCommand())){
                    //request.parseArgumentString("-d");
                    Debug.message("grppkgmgr,470 "+request.getCommand());
                }
                request.appendArguments( arguments );
            }
        }
	else{
	    //./temp Here should probably some default arguments be set
            //request.parseArgumentString( argStr );
	}
	
	
	if ( ! allok )
	    {
		String[] fmtArgs =
		{ cvsCommand , request.getVerifyFailReason() };
		String msg = ResourceMgr.getInstance().getUIFormat
		    ( "project.cmdparse.failed.msg", fmtArgs );
		String title = ResourceMgr.getInstance().getUIString
		    ( "project.cmdparse.failed.title" );
		JOptionPane.showMessageDialog
		    ( frame, msg, title, JOptionPane.ERROR_MESSAGE );
	    }
	
	int portNum =
	    CVSUtilities.computePortNum
	    ( project.getClient().getHostName(),
	      project.getConnectionMethod(),
	      project.isPServer() );
	
	// Establish the request's response handler if it is
	// not to be queued.
	if ( ! request.queueResponse ){
	    request.responseHandler = project;
	}
	
	request.setPort( portNum );
	
	if ( request.redirectOutput ){
	    fdbkStr = rmgr.getUIString( "project.fdbk.canceled" );
	    Debug.message("jCVSGrpPkgMgr,line266: "+cvsCommand);
	    return true;
	}
        
	// Handle Entries selection
	// If entries is not null, use what was passed in.
	// Otherwise, fill entries according to the spec...
	if ( entries == null ){
	    if ( request.getEntrySelector() == CVSRequest.ES_NONE 
                 || command == CHECKOUT){
		entries = new CVSEntryVector();
	    }
	    else{
		//Here we select the entries 
		entries = this.getProjectEntries(request.getEntrySelector());
		
		// Special case for 'Get User File' and 
		//'Get New Files' canceling...
		int selector = request.getEntrySelector();
		if ( ( selector == CVSRequest.ES_USER
		       || selector == CVSRequest.ES_NEW )
		     && entries == null ){
		    fdbkStr = rmgr.getUIString("project.fdbk.canceled");
		    Debug.message("jCVSGrpPkgMgr,line139: "+cvsCommand);
		    return true; // REVIEW - should this be false?
		}
		
		if ( request.execInCurDir 
		     && request.getEntrySelector()
		     == CVSRequest.ES_POPUP ){
		    CVSEntry dirEnt = entries.entryAt(0);
		    if ( dirEnt != null && dirEnt.isDirectory() ){
			request.setDirEntry( entries.entryAt(0) );
		    }
		    else{
			CVSTracer.traceWithStack( "dirEnt is WRONG!" );
		    }
		}
	    }
	}
	
	if ( entries == null ){
	    String[] fmtArgs = { request.getCommand() };
	    String msg = ResourceMgr.getInstance().getUIFormat
		( "project.no.selection.msg", fmtArgs );
	    String title = ResourceMgr.getInstance().getUIString
		( "project.no.selection.title" );
	    JOptionPane.showMessageDialog
		( frame, msg, title, JOptionPane.ERROR_MESSAGE );
	    return true;
	}
	
	// Handle guarantee of a message argument...
	if ( allok ){
	    if ( request.guaranteeMsg ){
		CVSArgumentVector args = request.getArguments();
		if ( ! args.containsArgument( "-m" ) ){
		    String[] fmtArgs = { request.getCommand() };
		    String prompt = ResourceMgr.getInstance().getUIFormat
			( "project.message.required.prompt", fmtArgs );
		    
                    String msgStr=" ";
                    if(!implicit){
                        msgStr=DialogManager.askString(frame, "ask-logmsg");
                    //this.requestLogMsg
                        //(Config.getString("groupwork.log.prompt"), frame);
                    }
		    
		    if ( msgStr != null ){
			args.addElement( "-m" );
			args.addElement( msgStr );
		    }
		    else{
			allok = false;
		    }
		}
	    }
	}
	
	//  UNDONE - it would be nice to "verifyRequest" here,
	//           but it is not _fully_ built (hostname, et.al.).
	
	if ( allok ){
	    request.setEntries( entries );
	    request.setUserInterface(info);
	    
	    CVSResponse response = new CVSResponse();
	    
            //Add the CVS request to the job Queue. This ensures that
            //all requests are performed one after the other.
            GroupJobQueue.getJobQueue().addJob(request.getCommand(),
                                             this.new MyRunner(request, 
                                                               response),
                                             this.new MyMonitor(request,
                                                                response,
                                                                frame));
            Debug.message("GrpPkgMgr line 456 Add "+request.getCommand()
                          +" to the Job queue");
        }
	else{
	    //this.resetCursor();
            frame.clearStatus(); 
	    Debug.message("GroupPkgMgr,line459: not allok");
	}
	
	//displayFinalResult(frame);

	return allok;
    }

    // =================== PRIVATE METHODS & CLASSES ========================

    private class MyRunner
	implements Runnable
    {
	private CVSRequest request;
	private CVSResponse response;
	
	public
	    MyRunner( CVSRequest req, CVSResponse resp )
	{
	    this.request = req;
	    this.response = resp;
	}
	
	public void run()
	{
	    boolean fail = false;
	    
	    if ( "add".equals( this.request.getCommand() ) )
		{
		    CVSEntry entry = this.request.getEntries().entryAt( 0 );
		    Debug.message("grpPkgMgr,line385:"+entry.getFullName());
                    CVSResponse addResponse =
			project.ensureRepositoryPath
			(info,
			 entry.getFullName(), this.response );
		    
		    if ( addResponse.getStatus() != CVSResponse.OK )
			{
			    fail = true;
			    String fdbkStr =
				ResourceMgr.getInstance().getUIString
				( "project.fdbk.errcreate" );
			    
			    Debug.message("GRpPkgMgr, line324: "+ fdbkStr );
			    this.response.appendStderr
				( "An error occurred while creating '"
				  + entry.getFullName() + "'" );
			}
		    else
			{
			    CVSEntry dirEntry =
				project.getDirEntryForLocalDir
				( entry.getLocalDirectory() );
			    
			    if ( dirEntry == null )
				{
				    CVSLog.logMsg
					( "ADD FILE COULD NOT FIND PARENT DIRECTORY" );
				    CVSLog.logMsg
					( "    locaDirectory = "
					  + entry.getLocalDirectory() );
				    (new Throwable( "COULD NOT FIND THE DIRECTORY!" )).
					printStackTrace();
				    
				    fail = true;
				    String fdbkStr =
					ResourceMgr.getInstance().getUIString
					( "project.fdbk.errcreate" );
				    
				    //showFeedback( fdbkStr );
				    Debug.message("GRpPkgMgr, line351: "+ fdbkStr );
				    this.response.appendStderr
					( "An error occurred while creating '"
					  + entry.getFullName() + "'" );
				}
			    else
				{
				    this.request.setDirEntry( dirEntry );
				    //
				    // NOTE
				    // SPECIAL CASE
				    // SEE "ADD SPECIAL CASE" BELOW
				    //
				    // In this special case, the user has 
				    //selected a file using the FileDialog. 
				    //Ergo, we have no context other than its
				    // local directory. And when we were 
				    //creating it, we were not even sure if 
				    //its parent directory existed yet!
				    //
				    // However, at this point, we have 
				    //"ensureRepositoryPath()-ed" the entry's 
				    //full name. This means that the entry's 
				    //parent directory exists. This is 
				    //critical, since that directory entry has
				    // the one piece of information that we 
				    //lacked when we created the entry to add 
				    //- the repository string. But now,
				    // we can get the parent and get the 
				    //repository from it!
				    //
				    if ( request.getEntrySelector() == CVSRequest.ES_USER )
					{
					    entry.setRepository( dirEntry.getRepository() );
					}
				}
			}
		}
	    
	    if ( ! fail ){
                Debug.message("grpPkgmgr,line650 "+project.getRepository());
		project.performCVSRequest(this.request, this.response);
	    }
	}
    }
    
    private class MyMonitor
	implements GroupJob.Monitor
    {
	private CVSRequest request;
	private CVSResponse response;
	private PkgMgrFrame frame;

	public MyMonitor(CVSRequest req, CVSResponse resp, PkgMgrFrame frame)
	{
	    this.request = req;
	    this.response = resp;
            this.frame = frame;
	}
	
	public void threadStarted()
	{
	    //	actionButton.setText( "Cancel Export" );
	}
	
	public void threadCanceled()
	{
	}
	
	public void threadFinished()
	{
	    //	actionButton.setText( "Perform Export" );
	    boolean allok=(this.response.getStatus() == CVSResponse.OK);
	    if ( request.isRedirected() )
		{
		    request.endRedirection();
		}

            handleResponse(this.response, this.request, frame);
            
            if(allok){
		System.err.println( "THREAD FINISHED: pretty Nice '" );
                if(this.request.getCommand().equals("ci")){
                    if(request.displayReponse)
                        this.frame.setStatus(Config.getString
                                             ("groupwork.committingDone"));
                    this.frame.setStatusR(Config.getString
                                          ("groupwork.notChanged"));
                }
                else if(this.request.getCommand().equals("add") ||
                        this.request.getCommand().equals("remove")){
                    this.frame.setStatusR(Config.getString
                                          ("groupwork.changed"));
                }
            }
	}
    }
    

    /**
     * Parses a integer command into a valid CVS command
     *
     * The command string is of the format:
     *
     * :command:select:request:response:arguments
     * Where:
     * command   - is a valid cvs command name (e.g., 'update', 'co', 'diff')
     * select    - specifies which entries to apply command to
     * request   - is a valid cvs request specification
     * reponse   - is a valid cvs reponse handling specification
     * arguments - is the remainder of the string taken as command arguments
     *
     * @param command  The command   
     * @returns A CVS command
     */
    private String parseCommand(int command)
    {	
	String cvsCommand=" ";
	
	switch(command) 
	    {
	    case UPDATE:
		cvsCommand="update:A:EAUFP:deou:";
		break;
	    case COMMIT:
		cvsCommand="ci:a:EAUFG:deou:";
		break;
	    case ADD:
		cvsCommand="add:G:SUAFX:due:";
		break;
	    case STATUS:
		cvsCommand="status:a:EUAF:de:";
		break;
            case REMOVE:
		cvsCommand="remove:a:EAF:de:";
		break;
            case CHECKOUT:
		cvsCommand="co:N:ANP:deou:";
		break;
            case RELEASE:
		cvsCommand="release:A:AM:d:-d";
		break;
            case LOG:
		cvsCommand="log:a:EAF:d:-N";
		break;
	    default:
		Debug.reportError("GroupPkgManager: unknown command ID");
		break;
	    }
	return cvsCommand;
    }

     /**
     * Returns the entries of the active project
     *
     * @param
     * @returns
     */
    private CVSEntryVector getProjectEntries(int selector)
    {	
	int i;
	File entryFile;
	String localPath;
	CVSEntry entry = null;
	CVSEntryVector entries = new CVSEntryVector();

	
	this.project.getRootEntry().addAllSubTreeEntries(entries); 

        //If the command is update we take the top level directory
        //as the only entry. 
        //othervise we will not get changes in the form of added files.
        //This is a bit of an akward way of doing it but it works.
        //The selector .ES_ALL means to do the command on "all" and
        //you chose this selector by setting the selector string in 
        //parseCommand() to "A". 
        if (selector == CVSRequest.ES_ALL)	
            entries.appendEntry(this.project.getRootEntry().getEntryList().getEntryAt(0));
        
        
        
	if ( entries != null ){
	    for ( i = 0 ; i < entries.size() ; ++i ){
		entry = entries.entryAt(i);
                Debug.message("grpPkgMgr, line677"+entry.getName());
		entryFile = this.project.getEntryFile( entry );
		
		if ( selector == CVSRequest.ES_ALLMOD
		     || selector == CVSRequest.ES_SELMOD ){
		    if ( ! this.project.isLocalFileModified( entry ) ){
			entries.removeElementAt(i);
			--i;
		    }
		}
		else if ( selector == CVSRequest.ES_ALLLOST
			  || selector == CVSRequest.ES_SELLOST ){
		    if ( ! entryFile.exists() ){
			entries.removeElementAt(i);
			--i;
		    }
		}
		else if ( selector == CVSRequest.ES_ALLUNC
			  || selector == CVSRequest.ES_SELUNC ){
		    if ( ! entryFile.exists()
			 || this.project.isLocalFileModified( entry ) ){
			entries.removeElementAt(i);
			--i;
		    }
		}
	    }
	}
	return entries;
    }//End getProjectEntries

     /**
     * Returns the entry to be added
     *
     * @param entryName Path to the sourcefile to be added
     * @returns
     */
    private CVSEntry createAddEntry( String entryName, 
                                     String localDirectory, 
                                     String repository )
    {
        //./temp this is just an attempt to fix the string problem
        //There is redundancy in information with all these paths
        String fileName = entryName.substring(entryName.lastIndexOf(File.separator)+1);
        String dirName = localDirectory.substring(localDirectory.lastIndexOf(File.separator)+1);
        Debug.message("jCVSGrpPkgMgr,line627 "+fileName);
        Debug.message("GrpPkgMgr,line628 "+dirName);
        Debug.message("GrpPkgMgr,line628 "+repository);

	CVSEntry entry = new CVSEntry();
	
	entry.setName( fileName );
        //sometimes the slash have to be hard coded, sometimes not!
	entry.setLocalDirectory( repository+"/"+dirName );
        //entry.setLocalDirectory( dirName );
	entry.setRepository( repository );
	entry.setTimestamp( this.project.getEntryFile( entry ) );
	
	// that is a 'zero' to indicate 'New User File'
	entry.setVersion( "0" );
	
	return entry;
    }

     /**
     * Returns the entry to be removed
     *
     * @param entryPath Path to the sourcefile to be removed
     * @returns CVSEntry
     */
    private CVSEntry findRemoveEntry(String entryPath)
    {
	CVSEntry entry = null;
	CVSEntryVector entries = null;

        //Get all entries in the project
	this.project.getRootEntry().addAllSubTreeEntries
	    ( entries = new CVSEntryVector() );

        //Get the name of the file
	String entryName = entryPath.substring(entryPath.lastIndexOf(File.separator)+1); 
        Debug.message("GrpPkgMgr,line702: "+entryName);

	if ( entries != null ){
	    for (int i = 0 ; i < entries.size() ; ++i){
		entry = entries.entryAt(i);
                Debug.message("GrpPkgMgr,line702: "+entry.getName());
                if(entry.getName().equals(entryName))
                   return entry;
            }
            Debug.reportError("GrpPkgMgr,line698: trying to remove a none excisting entry");
            return null;
        }
        else{
            Debug.reportError("GrpPkgMgr,line702: No entries assoc. with project");
            return null;
        }
    }
    
    /**
     * Loads the group defaults
     *
     * @param
     * @returns
     */
    private void loadPrefs()
    {
	String propsFile = Config.sys_confdir+File.separatorChar+"group.defs";
	Debug.message("GrpPkgMgr "+propsFile);

        if (props == null) {
            // try to load the Properties for the group stuff
            try {
                FileInputStream input = new FileInputStream(propsFile);
		
                this.props = new Properties();
                this.props.load(input);
            } catch(IOException e) {
                Debug.reportError("Error loading group properties file" + 
                                  propsFile + ": " + e);
            }
	}
    }
    
    /**
     * Merges the content of two bluej.pkg files 
     *
     * The method loads the pkg file from the repository and try
     * to merge it with the local one. This method relies completely
     * on that an update is completed. The layout will be according to 
     * the repository. Later there should be support for the user to 
     * choose to keep the local layout. 
     *
     * @param pkgDir The directory where the source resides
     * @param propsLocal The local package information
     * @param frame  The current frame
     * @returns
     */
    private void mergePkgFile(String pkgDir, SortedProperties propsLocal,
                              PkgMgrFrame frame)
    {
        SortedProperties propsRepos = new SortedProperties();
        String fullPkgFile = pkgDir + File.separator + "bluej.pkg";
        
        //Try to load the bluej.pkg file that came from the repository
        propsRepos = loadPkgFile(pkgDir);

        //Set the height and width of main window as it was locally
        propsRepos.setProperty("package.window.height",
                               propsLocal.getProperty("package.window.height","450"));
        propsRepos.setProperty("package.window.width",
                               propsLocal.getProperty("package.window.width",
                                                      "512"));
        //This package must be a group package (this is probably redundant)
        propsRepos.setProperty("package.isGroupPackage","true");

        //Get the number of targets in repository
        int reposTargets = Integer.parseInt(propsRepos.getProperty("package.numTargets", "0"));
        
        //-------------------------------------------------------------
        //this should be a method
        //Transfer all information about locally added classes
        //to propsRepos. 
        String fileName="";
        String className="";
        String tempKey="";
        String targetX="?";
        for(Iterator itr=addedClasses.iterator();itr.hasNext();){
            fileName=(String)itr.next();
            className=fileName.substring(fileName.lastIndexOf(File.separator)+1,
                                         fileName.lastIndexOf('.'));
            Debug.message("grpPkgMgr,1089 "+className);

            //increase the number of Targets
            reposTargets++;
            
            //Find the key that corresponds to the class name of the 
            //locally added class, by iterating through all keys
            for(Enumeration e=propsLocal.propertyNames();e.hasMoreElements();) {
                tempKey = (String)e.nextElement();
                
                //Find the key that maps onto className but also make sure
                //that the key is of type targetX.name.
                if(propsLocal.getProperty(tempKey).equals(className) &&
                   tempKey.endsWith("name")){
                    targetX = tempKey.substring(0,tempKey.indexOf('.'));
                    Debug.message("grpPkgMgr,1110 "+targetX);
                    break; //When we find it break the loop
                }
            }
            
            //Now we can transfer all the info about targetX
            //We should also do this for Dependencies
            for(Enumeration e=propsLocal.propertyNames();e.hasMoreElements();) {
                tempKey = (String)e.nextElement();
                if(tempKey.startsWith(targetX)){
                    Debug.message("grpPkgMgr,1119 "+tempKey);
                    propsRepos.setProperty("target"+reposTargets+tempKey.substring(tempKey.indexOf('.')), propsLocal.getProperty(tempKey));
                }
            }
        }
        
        //We must clear the addedClasses collection othervise it will be 
        //checked again and again and... 
        try{
            addedClasses.clear();
        }catch(UnsupportedOperationException e){
            Debug.reportError("Error clearing Added Classes: " + e);
        }
        
        //---------------------------------------
        //End handling added files
        //--------------------------------------
        
        //-------------------------------------
        //Method: remove the source (*.java, *.class, *.ctxt)
        //for remotely removed classes.
        //----------------------------------------------
        //Get the number of local targets
        int localTargets = Integer.parseInt(propsLocal.getProperty("package.numTargets", "0"));

        //if a target is not in Repos, remove its local files
        // (*.java, *.class, *.ctxt)
        for(int i=0;i<localTargets;i++){
            if(!propsRepos.contains(propsLocal.getProperty("target"+
                                                           (i+1)+".name"))){
                Debug.message("grpPkgMgr,1161: remove source " +propsLocal.getProperty("target"+(i+1)+".name"));
                ClassTarget t = (ClassTarget)frame.getPackage().getTarget(propsLocal.getProperty("target"+(i+1)+".name"));
                t.prepareFilesForRemoval();
            }
        }

        //------------------
        //end Remove source
        //------------------

        //---------------------------------------------
        //this should probably be a method
        //Remove information about locally removed files
        for(Iterator itr=removedClasses.iterator();itr.hasNext();){
            fileName=(String)itr.next();
            className=fileName.substring(fileName.lastIndexOf(File.separator)+1,
                                         fileName.lastIndexOf('.'));
            Debug.message("grpPkgMgr,1154 "+className);
            
            
            
            //Find the key that corresponds to the class name of the 
            //locally removed class, by iterating through all keys
            //in propsRepos
            for(Enumeration e=propsRepos.propertyNames();e.hasMoreElements();) {
                tempKey = (String)e.nextElement();
                
                //Find the key that maps onto className but also make sure
                //that the key is of type targetX.name.
                if(propsRepos.getProperty(tempKey).equals(className) &&
                   tempKey.endsWith("name")){
                    targetX = tempKey.substring(0,tempKey.indexOf('.'));
                    Debug.message("grpPkgMgr,1170 "+targetX);
                    break; //When we find it break the loop
                }
            }
            
            //get the X in targetX
            int tNum=Integer.parseInt(targetX.substring(targetX.length()-1));
            
            //Now we can remove all the info about targetX. We do this by
            //shifting down the information for all targets with target 
            //number > targetX.
            //We should also do this for Dependencies
            while(tNum<reposTargets){
            
                for(Enumeration e=propsRepos.propertyNames();e.hasMoreElements();) {
                    tempKey = (String)e.nextElement();
                    if(tempKey.startsWith("target"+tNum)){
                        Debug.message("grpPkgMgr,1180 "+tempKey);
                        propsRepos.setProperty("target"+tNum+tempKey.substring(tempKey.indexOf('.')), propsRepos.getProperty("target"+(tNum+1)+tempKey.substring(tempKey.indexOf('.'))));
                        //propsRepos.remove(tempKey);
                    }
                }
                tNum++;
            }
            
            //decrease the number of Targets
            reposTargets--;
        }
        
        //We must clear the removedClasses collection othervise it will be 
        //checked again and again and... 
        try{
            removedClasses.clear();
        }catch(UnsupportedOperationException e){
            Debug.reportError("Error clearing removed Classes: " + e);
        }
        
        //---------------------------------------
        //End handling removed files
        //--------------------------------------
        
        //Set the new number of targets
        propsRepos.setProperty("package.numTargets",
                               String.valueOf(reposTargets));

        //we now want to save the pkg info
        File file = new File(fullPkgFile);
        if(file.exists()) {			// make backup of original
            file.renameTo(new File(pkgDir + File.separator + "bluej.pkh"));
        }
        
        try {
            FileOutputStream output = new FileOutputStream(file);
            propsRepos.store(output, "BlueJ project file");
        }catch(IOException e) {
            Debug.reportError("Error saving project file " + file + ": " + e);
        }
    }//End mergePkgFile
    
    /**
     * Updates the local bluej.pkg file
     *
     * The method loads the pkg file from the repository and try
     * to merge it with the local one. 
     *
     * @param pkgDir The directory where the source resides
     * @param propsLocal The local package information
     * @returns The updated properties
     */
    private SortedProperties updatePkgFile(String pkgDir,
                                          SortedProperties propsLocal)
    {
        
        SortedProperties propsDefault = new SortedProperties();
        String fullPkgFile = pkgDir + File.separator + "bluej.pkg";

        //Here we create a default pkg file from the updated source
        //It will overwrite the local one on disk
        Package.importPackage(new File(pkgDir), null, false);

        // try to load the newly created default bluej.pkg file
        //This would not be necessary if we could make importPackage()
        //hand us the default props directly
        propsDefault = loadPkgFile(pkgDir);

        //Set the height and width of main window
        propsDefault.setProperty("package.window.height",
                                 propsLocal.getProperty("package.window.height","450"));
        propsDefault.setProperty("package.window.width",
                                 propsLocal.getProperty("package.window.width","512"));
        //This package must be a group package
        propsDefault.setProperty("package.isGroupPackage","true");

        //Get the number of local targets
        int numTargets = Integer.parseInt(propsLocal.getProperty("package.numTargets", "0"));

        //For all local targets; transfer info about position and size
        //to corresponding target in default
        String tempKey;
        for(int i=0;i<numTargets;i++){
            for(Enumeration e=propsDefault.propertyNames(); e.hasMoreElements();) {
                tempKey = (String)e.nextElement();

                //Find the key that maps onto target name but also make sure
                //that the key is of type targetX.name
                if(propsDefault.getProperty(tempKey).equals(propsLocal.getProperty("target"+(i+1)+".name")) && tempKey.endsWith("name")){
                    propsDefault.setProperty(tempKey.substring(0,tempKey.indexOf('.')) + ".height", propsLocal.getProperty("target"+(i+1)+".height","50")); 
                    propsDefault.setProperty(tempKey.substring(0,tempKey.indexOf('.')) + ".width", propsLocal.getProperty("target"+(i+1)+".width","80"));
                    propsDefault.setProperty(tempKey.substring(0,tempKey.indexOf('.')) + ".x", propsLocal.getProperty("target"+(i+1)+".x","50"));
                    propsDefault.setProperty(tempKey.substring(0,tempKey.indexOf('.')) + ".y", propsLocal.getProperty("target"+(i+1)+".y","50"));
                    break; //and move on to next Target
                }
            }
        }
        //we now want to save the pkg info
        File file = new File(fullPkgFile);
        if(file.exists()) {			// make backup of original
            file.renameTo(new File(pkgDir + File.separator + "bluej.pkh"));
        }
        
        try {
            FileOutputStream output = new FileOutputStream(file);
            propsDefault.store(output, "BlueJ project file");
        }catch(IOException e) {
            Debug.reportError("Error saving project file " + file + ": " + e);
        }
        return propsDefault;
    }//End updatePkgFile

    /**
     * Defines a module in the repository by editing the 
     * CVSROOT/modules file. This makes it possible to have 
     * one local name and one repository name for a project.
     * This is to ensure uniqueness in the repository.
     *
     * @param passwd     The project password
     * @param userName   Server user name
     * @param module     Project name locally
     * @param localDir   Local working directory
     * @param groupName  Name of the project group
     * @param frame      The current Frame object
     * @param
     * @returns
     */
    private boolean defineModule(String passWord, String userName,
                                 String module, String localDir,
                                 String groupName, PkgMgrFrame frame)
    {
        //This could of course be passed in as a parameter as well.
        GroupWorkDialog dialog=new GroupWorkDialog(frame);
        //The CVS admin file we need to edit is called modules.
        //The file.separator business is tricky!
        String modules = "CVSROOT/modules";
        String modulesI = "CVSROOT"+File.separator+"modules";
        String tmp = localDir+File.separator+"tmp";
        
        dialog.getCheckOutPanel().doCheckOut(passWord, userName, modules,
                                             null, tmp, frame, false);

        //Here we wait for the checkout to finish.
        Debug.message("grppkgmgr,1319: before sync "+Thread.currentThread().getName());
        Sync.s.callWait(); //maybe this should be a timed wait?
        //After the checkout is finished we also need to give the 
        //file system time to catch up, this is a bit strange?
        synchronized(this) {
            try {
                wait(3000);
            }catch(InterruptedException e) {}
        }
        Debug.message("grppkgmgr,line1318: after wait()");
        
        //was the import & checkout successful?
        if(Sync.s.getOk()){
            Debug.message("Checkout is completed (import command)");
            //Create a file writer that appends the module definition
            //to the modules file.
            try{
                Debug.message("GrpPkgMgr,line1130 "+tmp+File.separator+modulesI);
                FileWriter out = new FileWriter(tmp+File.separator+modulesI,
                                                true);
                out.write(groupName+module+" -d "+module+" "+groupName+
                          "/"+module);
                out.close();
            } catch (IOException e){
                Debug.reportError("Could not write the modules definition");
            }

            //Initialize the group info temporarily so that we can
            //perform the commit command.
            String scrambled = CVSScramble.scramblePassword( passWord, 'A' );
            frame.getPackage().initializeGroupInfo(tmp+File.separator+"CVSROOT",
                                                   frame, scrambled);         

            //Commit the changed modules file
            if(this.performCommand(frame, COMMIT, null, true)){
                Debug.message("GrpPkgMgr,line1147: committing modules");
            }
            else{
                Debug.message("GrpPkgMgr,line1150: trouble committing modules");
            }
            //Here we remove the CVSROOT directory, preferably by
            //releasing it (CVS release)
            releasePkg(frame, null);
            //DialogManager.showText(frame,"Waiting for release");
            return true;
        }
        else{
            return false;
        }
        
    }//End DefineModules

    /**
     * Load the bluej.pkg file
     *
     * @param pkgDir The directory where the source resides
     * @returns A SortedProperties object containing package info
     */
    private SortedProperties loadPkgFile(String pkgDir)
    {
        SortedProperties propsLocal = new SortedProperties();
        String fullPkgFile = pkgDir + File.separator + "bluej.pkg";
        
        // try to load the local bluej.pkg file
        try {
            FileInputStream input = new FileInputStream(fullPkgFile);
            
            propsLocal.load(input);
        } catch(IOException e) {
            Debug.reportError("Error loading local pkg file" + 
                              fullPkgFile + ": " + e);
        }
        return propsLocal;
    }

    /*
     * Ask for a Log message before committing
     *
     */
    private String requestLogMsg(String prompt, PkgMgrFrame frame )
    {
        MessageDialog dlg = new MessageDialog( frame, true, prompt );

        dlg.setTitle(Config.getString("groupwork.logmsg.title"));
        
        dlg.show();
        
        return dlg.getMessage();
    }

    /*
     * Handle the server response, depending on request type
     * and the result string.
     *
     * The CVS server returns a response that contains information
     * about the outcome of the request. Depending on the request,
     * this information is analyzed differently.
     *
     * Update:
     * The result string contains status information about the files.
     * C file - there were conflicts in file
     * A file - file is locally added
     * R file - file is locally removed
     * error - the request was aborted.
     *
     * Commit:
     * CVSResponse.ERROR - the request was aborted.
     *
     * Status:
     * Extract the information we want from the result string and
     * display it.
     *
     * Log:
     * Extract the information we want from the result string and
     * display it.
     * 
     */
    private void handleResponse(CVSResponse response, CVSRequest request,
                                PkgMgrFrame frame)
    {
        String baseDir = frame.getPackage().getBaseDir();
        String fileName="";
        String nextToken = " ";
        StringBuffer displayString=new StringBuffer();
        StringTokenizer st = new StringTokenizer(response.getDisplayResults().trim());
        
        //ERROR, Commit handles error differently
        if(response.getStatus() == CVSResponse.ERROR &&
           !request.getCommand().equals("ci")){
            info.setText(response.getDisplayResults().trim());
            info.display(Config.getString("groupwork.error.title"));
        }
        //UPDATE
        else if(request.getCommand().equals("update")){
            while (st.hasMoreTokens()){
                nextToken = st.nextToken();
                
                if(nextToken.equals("C")){
                    //this could cause a nullpointer, but I think we're safe
                    fileName=baseDir+st.nextToken().substring(1);
                    Debug.message("GrppkgMgr,794 "+fileName);
                    conflictFiles.add(fileName);
                }
                else if(nextToken.equals("A")){
                    fileName=baseDir+st.nextToken().substring(1);
                    Debug.message("GrppkgMgr,811 "+fileName);
                    addedClasses.add(fileName);
                }
                else if(nextToken.equals("R")){
                    fileName=baseDir+st.nextToken().substring(1);
                    Debug.message("GrppkgMgr,817 "+fileName);
                    removedClasses.add(fileName);
                }
            }
        }
        //COMMIT
        else if(request.getCommand().equals("ci")){
            if(response.getStatus() == CVSResponse.ERROR){
                //Show dialog letting them know that the request
                //was aborted.
                int answer = DialogManager.askQuestion(frame, 
                                                       "need-update");
                // if they agree, update the package
                if(answer == 0){
                    updatePkg(frame, true);
                }
            }
        }
        //STATUS
        else if(request.getCommand().equals("status")){
            while (st.hasMoreTokens()){
                nextToken = st.nextToken();
                
                if(nextToken.equals("File:")){
                    nextToken = st.nextToken();
                    if(!nextToken.equals("bluej.pkg")){
                        if(nextToken.lastIndexOf('.')!=(-1)){
                            displayString.append
                                (endOfLine+" "+nextToken.substring
                                 (0, nextToken.lastIndexOf('.'))+"\t");
                        }
                        else if(st.nextToken().equals("file")){
                            nextToken = st.nextToken();
                            displayString.append
                                (endOfLine+" "+nextToken.substring
                                 (0, nextToken.lastIndexOf('.'))+"\t");
                        }
                        //this really opens up for exceptions
                        //nextToken.substring(0, nextToken.lastIndexOf('.'))
                        st.nextToken(); //this is the status token
                        nextToken = st.nextToken();
                        if(nextToken.equals("Needs") ||
                           nextToken.equals("Locally") ||
                           nextToken.equals("File")){
                            displayString.append(nextToken+" "+
                                                 st.nextToken());
                            if(nextToken.equals("File")){
                                displayString.append(" "+st.nextToken()+" "+
                                                     st.nextToken()+" "+
                                                     st.nextToken());
                            }
                        }
                        else{
                            displayString.append(nextToken);
                        }
                        displayString.append(" "+endOfLine);
                    }
                }
            }
            info.setText(displayString.toString());
            info.display(Config.getString("groupwork.status.title"));
        }
        //LOG
        else if(request.getCommand().equals("log")){
            //String previousToken = " ";
            String date="";
            String file="";
            Comparator comp = Collections.reverseOrder();
            Map dateLogMap = new TreeMap(comp);
            Map fileDateMap = new HashMap();
            List dateList = new ArrayList();
            while (st.hasMoreTokens()){
                nextToken = st.nextToken(endOfLine);//endOfLine as delimiter

                if(nextToken.startsWith("Working file:")){//get file name
                    file = nextToken.substring((nextToken.lastIndexOf("/")+1),
                                               nextToken.lastIndexOf("."));
                }
                if(!file.equals("bluej")){
                    //get date assoc with file
                    if(nextToken.startsWith("date:")){
                        date = nextToken.substring(nextToken.indexOf(" "),
                                                   nextToken.indexOf(";"));
                        //remove seconds
                        date = date.substring(0,date.lastIndexOf(":"));
                        dateLogMap.put(date, st.nextToken());
                        dateList.add(date);
                    }
                    //indicates new file
                    if(nextToken.startsWith("=======")){ 
                        fileDateMap.put(file, dateList);
                        //each file has its own dateList
                        dateList=new ArrayList();
                    }
                }
            }
            
            //Here we create a Map where a date maps onto a collection
            //of files that where committed on that date.
            Map dateFileMap = transformMap(fileDateMap, dateLogMap.keySet());

            for(Iterator itr=dateLogMap.keySet().iterator();itr.hasNext();){
                date=(String)itr.next();
                displayString.append(endOfLine+date+"\t"+
                                     dateLogMap.get(date)+" "+endOfLine+
                                     " "+dateFileMap.get(date).toString()+
                                     endOfLine);
            } 
//             for(Iterator itr=fileDateMap.keySet().iterator();itr.hasNext();){
//                 file=(String)itr.next();
//                 displayString.append(endOfLine+file+"\n"+
//                                      fileDateMap.get(file).toString()+endOfLine);
//             }
//             for(Iterator itr=dateFileMap.keySet().iterator();itr.hasNext();){
//                 date=(String)itr.next();
//                 displayString.append(endOfLine+date+"\n"+
//                                      dateFileMap.get(date).toString()+endOfLine);
//             }

            Debug.message("grpmgr,line1503: "+dateFileMap.size()+" "+dateLogMap.size());
            info.setText(displayString.toString());
            info.display(Config.getString("groupwork.log.title"));
        }
        
        Debug.message(response.getDisplayResults().trim());
    }//end handleResponse

    /*
     * This a very specialized method that transform a
     * special type of maps.
     *
     * Don't try to use this method, it only exist to 
     * make other parts of the code more readable.
     *
     */
    private Map transformMap(Map map, Collection coll)
    {
        Map transformed = new HashMap();
        String tmpString;
        String tmpKey;
        Collection tmpColl;
        Collection newColl = new ArrayList();

        for(Iterator itr = coll.iterator();itr.hasNext();){
            tmpString = (String)itr.next();
            for(Iterator it = map.keySet().iterator();it.hasNext();){
                tmpKey = (String)it.next();
                tmpColl = (Collection)map.get(tmpKey); 
                if(tmpColl.contains(tmpString)){
                    newColl.add(tmpKey);
                }
            }
            transformed.put(tmpString, newColl);
            newColl = new ArrayList();
        }
        return transformed;
    }//End transformMap
    
    private class UpdatePkgRunner 
    implements Runnable
    {
        SortedProperties propsLocal;
        //SortedProperties propsDefault;
        private String pkgDir;
        private String passw;
        private PkgMgrFrame frame;

	public UpdatePkgRunner(String pkgDir, String passw,
                               PkgMgrFrame frame, SortedProperties propsLocal)
	{
	    this.propsLocal = propsLocal;
            this.passw = passw;
            this.pkgDir = pkgDir;
            this.frame = frame;
	}
	
	public void run()
	{
            if(props.getProperty("group.usesPkgInfo", 
                                 "false").equals("true")){
                mergePkgFile(this.pkgDir, this.propsLocal, this.frame);
            }
            else{
                updatePkgFile(this.pkgDir, this.propsLocal);
            }
            frame.removePackage();
            frame.doOpenPackage(pkgDir, passw);
            
            //Here we open editor windows for all conflicting files,
            //if there are any.
            if(!conflictFiles.isEmpty()){
                for(Iterator itr=conflictFiles.iterator();itr.hasNext();){
                    frame.getPackage().hiliteString((String)itr.next(),
                                                    "<<<<<<<", "Conflicts",
                                                    false);     
                }
                
                //We must clear the conflictFiles collection othervise it  
                //will be checked again and again and... 
                try{
                    conflictFiles.clear();
                }catch(UnsupportedOperationException e){
                    Debug.reportError("Error clearing conflict files: " + e);
                }
            }
            Debug.message("GrpPkgMgr line970: updatePkg job Finished");
            frame.setStatus(Config.getString("groupwork.updatingDone"));
        }
    }//End class UpdatePkgRunner

    private class ModulesDefRunner 
    implements Runnable
    {
        private String tmp;
        private String module;
        private String modulesI;
        private String passWord;
        private PkgMgrFrame frame;

	public ModulesDefRunner(String tmp, String module,
                                String modulesI, PkgMgrFrame frame,
                                String passWord)
	{
	    this.tmp = tmp;
            this.module = module;
            this.modulesI = modulesI;
            this.frame = frame;
            this.passWord = passWord;
	}
	
	public void run()
	{
            //Create a file writer that appends the module definition
            //to the modules file.
            try{
                Debug.message("GrpPkgMgr,line1525 "+this.tmp+File.separator+this.modulesI);
                FileWriter out = new FileWriter(this.tmp+File.separator+this.modulesI,
                                                true);
                out.write("Group3"+this.module+" -d "+this.module+" Group3"+"/"+this.module);
                out.close();
            } catch (IOException e){
                Debug.reportError("Could not write the modules definition");
            }
            
            //Initialize the group info temporarily so that we can
            //perform the commit command.
            String scrambled = CVSScramble.scramblePassword( passWord, 'A' );
            frame.getPackage().initializeGroupInfo(tmp+File.separator+"CVSROOT",
                                                   frame, scrambled);
            //Commit the changed modules file
            if(performCommand(frame, COMMIT, null, true)){
                Debug.message("GrpPkgMgr,line1544: committing modules");
            }
            else{
                Debug.message("GrpPkgMgr,line1547: trouble committing modules");
            }
            
        }
    }//End class ModulesDefRunner

    private class JobMonitor
    implements GroupJob.Monitor
    {
	public JobMonitor()
	{
	}
	
	public void threadStarted()
	{
	}
	
	public void threadCanceled()
	{
	}
	
	public void threadFinished()
	{
            Debug.message("Finished job");
	}
    }
}
    







