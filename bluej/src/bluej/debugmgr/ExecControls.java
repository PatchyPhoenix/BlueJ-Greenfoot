package bluej.debugmgr;

import java.awt.*;
import java.awt.event.*;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;

import bluej.*;
import bluej.debugger.*;
import bluej.debugmgr.inspector.ObjectInspector;
import bluej.pkgmgr.Project;
import bluej.utility.Debug;

/**
 * Window for controlling the debugger
 *
 * @author  Michael Kolling
 * @version $Id: ExecControls.java 2054 2003-06-24 12:53:46Z mik $
 */
public class ExecControls extends JFrame
    implements ActionListener, ListSelectionListener, TreeSelectionListener, TreeModelListener
{
    private static final String windowTitle =
        Config.getString("debugger.execControls.windowTitle");
    private static final String stackTitle =
        Config.getString("debugger.execControls.stackTitle");
    private static final String staticTitle =
        Config.getString("debugger.execControls.staticTitle");
    private static final String instanceTitle =
        Config.getString("debugger.execControls.instanceTitle");
    private static final String localTitle =
        Config.getString("debugger.execControls.localTitle");
    private static final String threadTitle =
        Config.getString("debugger.execControls.threadTitle");
    private static final String updateText =
        Config.getString("debugger.execControls.updateText");
    private static final String closeText =
        Config.getString("close");
    private static final String systemThreadText =
        Config.getString("debugger.execControls.systemThreads");
    private static final String haltButtonText =
        Config.getString("debugger.execControls.haltButtonText");
    private static final String stepButtonText =
        Config.getString("debugger.execControls.stepButtonText");
    private static final String stepIntoButtonText =
        Config.getString("debugger.execControls.stepIntoButtonText");
    private static final String continueButtonText =
        Config.getString("debugger.execControls.continueButtonText");
    private static final String terminateButtonText =
        Config.getString("debugger.execControls.terminateButtonText");



    private static String[] empty = new String[0];

    // === instance ===

	// the display for the list of active threads
    //private JList threadList;
	//private List threads;
	private JTree threadTree; 
	private DebuggerThreadTreeModel threadModel;
	
    
    private JList stackList, staticList, instanceList, localList;
    private JButton stopButton, stepButton, stepIntoButton, continueButton,
        terminateButton;
    private JButton closeButton;
	private CardLayout cardLayout;
	private JPanel flipPanel;
	
	// the Project that owns this debugger
    private Project project;

	//	the debug machine this control is looking at
    private Debugger debugger = null;				

	// the thread currently selected
	private DebuggerThread selectedThread;
	
    private DebuggerClass currentClass;	    // the current class for the
                                            //  selected stack frame
    private DebuggerObject currentObject;	// the "this" object for the
                                            //  selected stack frame
    private int currentFrame = 0;		// currently selected frame

    public ExecControls(Project project, Debugger debugger)
    {
        super(windowTitle);

		if (debugger == null)
			throw new NullPointerException();
			
		this.project = project;
		this.debugger = debugger;

        //threads = new ArrayList();
        createWindow();
    }

	/**
	 * Show or hide the exec control window.
	 */
	public void showHide(boolean show)
	{
		setVisible(show);
	}

	
    // ----- ActionListener interface -----

    public void actionPerformed(ActionEvent event)
    {
        Object obj = event.getSource();

		if(obj == terminateButton) {
			project.restartVM();
			return;
		}
        if(obj == closeButton) {
            setVisible(false);
        }

		// All the buttons after this require a selected
		// thread. If no thread selected, exit now.
		if (selectedThread == null)
			return;

        if (obj == stopButton) {
			clearThreadDetails();
			if (!selectedThread.isSuspended()) {
				selectedThread.halt();
			}
        }
        if (obj == continueButton) {
			clearThreadDetails();
			if (selectedThread.isSuspended()) {
				selectedThread.cont();
			}
        }
        if (obj == stepButton) {
			clearThreadDetails();
			if (selectedThread.isSuspended()) {
            	selectedThread.step();
			}
        }
		if (obj == stepIntoButton) {
			clearThreadDetails();
			if (selectedThread.isSuspended()) {
	            selectedThread.stepInto();
			}
		}
    }

    // ----- ListSelectionListener interface -----

    /**
     *  A list item was selected. This can be either in the thread list,
     *  the stack list, or one of the variable lists.
     */
    public void valueChanged(ListSelectionEvent event)
    {
        if(event.getValueIsAdjusting())  // ignore mouse down, dragging, etc.
            return;

        Object src = event.getSource();

        if(src == stackList) {
            selectStackFrame(stackList.getSelectedIndex());
        }

        // ststicList, instanceList and localList are ignored - single click
        // doesn't do anything
    }

    // ----- end of ListSelectionListener interface -----

	/**
	 * A tree item was selected.
	 */
	public void valueChanged(TreeSelectionEvent event)
	{
		Object src = event.getSource();
		
		if(src == threadTree) {
			clearThreadDetails();

			DefaultMutableTreeNode node =
			 (DefaultMutableTreeNode) threadTree.getLastSelectedPathComponent();

			// check for "unselecting" a node
			// (happens when the VM is restarted)
			if (!event.isAddedPath())
				unselectThread();
				
			if (node == null)
				return;

			DebuggerThread dt = threadModel.getNodeAsDebuggerThread(node);        

			if (dt != null)
				setSelectedThread(dt);
			else
				unselectThread();
		}
	}
	
	public void treeNodesChanged(TreeModelEvent e)
	{
		Object nodes[] = e.getChildren();

		for(int i=0; i<nodes.length; i++) {
			if (nodes[i] == null || selectedThread == null)
				return;
			
			if (selectedThread.equals(threadModel.getNodeAsDebuggerThread(nodes[i])))
				setSelectedThread(selectedThread);
		}	
	}
	
	public void treeNodesInserted(TreeModelEvent e) { }
	public void treeNodesRemoved(TreeModelEvent e) { }
	public void treeStructureChanged(TreeModelEvent e) { }
	
		
    public void listDoubleClick(MouseEvent event)
    {
        Component src = event.getComponent();

        if(src == staticList && staticList.getSelectedIndex() >= 0) {
            viewStaticField(staticList.getSelectedIndex());
        }
        else if(src == instanceList && instanceList.getSelectedIndex() >= 0) {
            viewInstanceField(instanceList.getSelectedIndex());
        }
        else if(src == localList && localList.getSelectedIndex() >= 0) {
            viewLocalVar(localList.getSelectedIndex());
        }
    }

	private void unselectThread()
	{
		selectedThread = null;
		stopButton.setEnabled(false);
		stepButton.setEnabled(false);
		stepIntoButton.setEnabled(false);
		continueButton.setEnabled(false);

		cardLayout.show(flipPanel, "blank");
	}
	
	/**
	 * Selects a thread for display of its details.
	 * 
	 * If the thread is already selected, this method
	 * will ensure that the status details are up to date.
	 * 
	 * @param  dt  the thread to hilight in the thread
	 *             tree and whose status we want to display.
	 */
	public void selectThread(DebuggerThread dt)
	{
		TreePath tp = threadModel.findNodeForThread(dt);
		
		if (tp != null) {
			threadTree.clearSelection();
			threadTree.addSelectionPath(tp);
		}
		else {
			Debug.message("Thread " + dt + " no longer available for selection");
		}
	}

	/**
	 * Set our internally selected thread and update the
	 * UI to reflect its status.
	 * 
	 * It is currently true that this thread will be
	 * selected in the tree view before this method is called.
	 * At the moment, this method does not rely on this fact
	 * but if the method is changed _to_ rely on it, this
	 * comment should be fixed.
	 * 
	 * @param dt  the thread to select
	 */
	private void setSelectedThread(DebuggerThread dt)
	{
		selectedThread = dt;
		
		boolean isSuspended = selectedThread.isSuspended();
		
		stopButton.setEnabled(!isSuspended);
		stepButton.setEnabled(isSuspended);
		stepIntoButton.setEnabled(isSuspended);
		continueButton .setEnabled(isSuspended);
		terminateButton.setEnabled(true);

		cardLayout.show(flipPanel, isSuspended ? "split" : "blank");

		setThreadDetails();		
	}

    private void setThreadDetails()
    {
        stackList.setFixedCellWidth(-1);
        List stack = selectedThread.getStack();
        if(stack.size() > 0) {
            stackList.setListData(stack.toArray(new Object[0]));
			// show details of top frame
			setStackFrameDetails(0);
        }
    }

    private void clearThreadDetails()
    {
        stackList.setListData(empty);
        staticList.setListData(empty);
        instanceList.setListData(empty);
        localList.setListData(empty);
    }

    private void selectStackFrame(int index)
    {
        if (index >= 0) {
            setStackFrameDetails(index);
            selectedThread.setSelectedFrame(index);

			project.debuggerEvent(new DebuggerEvent(this, DebuggerEvent.THREAD_SHOWSOURCE, selectedThread));
            currentFrame = index;
        }
    }

    private void setStackFrameDetails(int frameNo)
    {
        currentClass = selectedThread.getCurrentClass(frameNo);
        currentObject = selectedThread.getCurrentObject(frameNo);
        if(currentClass != null) {
            staticList.setFixedCellWidth(-1);
            staticList.setListData(
               currentClass.getStaticFields(false).toArray(new Object[0]));
        }
        if(currentObject != null) {
            instanceList.setFixedCellWidth(-1);
            instanceList.setListData(
               currentObject.getInstanceFields(false).toArray(new Object[0]));
        }
        if(selectedThread != null) {
            localList.setFixedCellWidth(-1);
            localList.setListData(
              selectedThread.getLocalVariables(frameNo).toArray(new Object[0]));
        }
    }

    private void viewStaticField(int index)
    {
        if(currentClass.staticFieldIsObject(index)) {
            ObjectInspector viewer = ObjectInspector.getInstance(false,
                                          currentClass.getStaticFieldObject(index),
                                          null, null, null, this);
        }
    }

    private void viewInstanceField(int index)
    {
        if(currentObject.instanceFieldIsObject(index)) {
            ObjectInspector viewer = ObjectInspector.getInstance(false,
                                          currentObject.getInstanceFieldObject(index),
                                          null, null, null, this);
        }
    }

    private void viewLocalVar(int index)
    {
        if(selectedThread.varIsObject(currentFrame, index)) {
            ObjectInspector viewer = ObjectInspector.getInstance(false,
                           selectedThread.getStackObject(currentFrame, index),
                           null, null, null, this);
        }
    }

    /**
     * Create and arrange the GUI components.
     */
    private void createWindow()
    {
    	setIconImage(BlueJTheme.getIconImage());
    	
        JPanel contentPane = (JPanel)getContentPane();  // has BorderLayout by default
        contentPane.setLayout(new BorderLayout(6,6));
        contentPane.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

        // Create the control button panel

        JPanel buttonBox = new JPanel();
        {
			buttonBox.setLayout(new GridLayout(1,0));

			Insets margin = new Insets(0, 0, 0, 0);
			stopButton = addButton("image.stop", haltButtonText, buttonBox, margin);
			stepButton = addButton("image.step", stepButtonText, buttonBox, margin);
			stepIntoButton = addButton("image.step_into", stepIntoButtonText, buttonBox, margin);
			continueButton = addButton("image.continue", continueButtonText, buttonBox, margin);
			terminateButton = addButton("image.terminate", terminateButtonText, buttonBox, margin);
        }

        contentPane.add(buttonBox, BorderLayout.SOUTH);

		// create a mouse listener to monitor for double clicks
		MouseListener mouseListener = new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					listDoubleClick(e);
				}
			}
		};

		// create static variable panel
		JScrollPane staticScrollPane = new JScrollPane();
		{
			staticList = new JList(new DefaultListModel());
			{
				staticList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				staticList.addListSelectionListener(this);
				staticList.setVisibleRowCount(3);
				staticList.setFixedCellWidth(150);
				staticList.addMouseListener(mouseListener);
			}
			staticScrollPane.setViewportView(staticList);
			staticScrollPane.setColumnHeaderView(new JLabel(staticTitle));
		}

        // create instance variable panel
		JScrollPane instanceScrollPane = new JScrollPane();
    	{
			instanceList = new JList(new DefaultListModel());
    		{
				instanceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				instanceList.addListSelectionListener(this);
				instanceList.setVisibleRowCount(4);
				instanceList.setFixedCellWidth(150);
				instanceList.addMouseListener(mouseListener);
    		}
			instanceScrollPane.setViewportView(instanceList);
			instanceScrollPane.setColumnHeaderView(new JLabel(instanceTitle));
    	}

        // create local variable panel
		JScrollPane localScrollPane = new JScrollPane();
    	{
			localList = new JList(new DefaultListModel());
			{
				localList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				localList.addListSelectionListener(this);
				localList.setVisibleRowCount(4);
				localList.setFixedCellWidth(150);
				localList.addMouseListener(mouseListener);
			}
			localScrollPane.setViewportView(localList);
			localScrollPane.setColumnHeaderView(new JLabel(localTitle));
    	}

        // Create variable display area

        JSplitPane innerVarPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                                 staticScrollPane, instanceScrollPane);
        innerVarPane.setDividerSize(6);

        JSplitPane varPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                            innerVarPane, localScrollPane);
        varPane.setDividerSize(6);

        // Create stack listing panel

        stackList = new JList(new DefaultListModel());
        stackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stackList.addListSelectionListener(this);
        stackList.setFixedCellWidth(150);
        JScrollPane stackScrollPane = new JScrollPane(stackList);
        stackScrollPane.setColumnHeaderView(new JLabel(stackTitle));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                              stackScrollPane, varPane);
        splitPane.setDividerSize(6);

        // Create thread panel
        JPanel threadPanel = new JPanel(new BorderLayout());
        

		MouseListener treeMouseListener = new MouseAdapter() {
			 public void mousePressed(MouseEvent e) {
				 TreePath selPath = threadTree.getPathForLocation(e.getX(), e.getY());
				 if(selPath != null) {
					DefaultMutableTreeNode node =
					 (DefaultMutableTreeNode) selPath.getLastPathComponent();

					if (node != null) {
						DebuggerThread dt = threadModel.getNodeAsDebuggerThread(node);        

						if (dt != null)
							setSelectedThread(dt);				 	
					}
				 }
			 }
		 };
		 
		threadModel = (DebuggerThreadTreeModel) debugger.getThreadTreeModel();
		threadModel.addTreeModelListener(this);
		
		threadTree = new JTree(threadModel);
		{
			threadTree.addTreeSelectionListener(this);		       
			threadTree.addMouseListener(treeMouseListener);
			threadTree.getSelectionModel().
						setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
			threadTree.setVisibleRowCount(8);
			threadTree.setShowsRootHandles(false);
			threadTree.setRootVisible(false);
		}
										        
        JScrollPane threadScrollPane = new JScrollPane(threadTree);
        threadScrollPane.setColumnHeaderView(new JLabel(threadTitle));
        threadPanel.add(threadScrollPane, BorderLayout.CENTER);


        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

        closeButton = new JButton(closeText);
        closeButton.addActionListener(this);
        buttonPanel.add(closeButton);
        makeButtonNotGrow(closeButton);

        buttonPanel.add(Box.createVerticalGlue());

        threadPanel.add(buttonPanel, BorderLayout.EAST);

		flipPanel = new JPanel();
		{
			flipPanel.setLayout(cardLayout = new CardLayout());
   
			flipPanel.add(splitPane, "split");
			JPanel tempPanel = new JPanel();
            JLabel infoLabel = new JLabel("<html><center>Thread is running.<br>Threads must be stopped to view details.</html>");
            infoLabel.setForeground(Color.gray);
			tempPanel.add(infoLabel);
			flipPanel.add(tempPanel, "blank");
		}

        JSplitPane mainPanel = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                              threadPanel, flipPanel);

        mainPanel.setDividerSize(6);

        contentPane.add(mainPanel, BorderLayout.CENTER);

        // Close Action when close button is pressed
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event){
                Window win = (Window)event.getSource();
                win.setVisible(false);
            }
        });

        // save position when window is moved
        addComponentListener(new ComponentAdapter() {
            public void componentMoved(ComponentEvent event){
                Config.putLocation("bluej.debugger", getLocation());
            }
        });

        setLocation(Config.getLocation("bluej.debugger"));

        pack();

    }

    private void makeButtonNotGrow(JButton button)
    {
        Dimension pref = button.getMinimumSize();
        pref.width = Integer.MAX_VALUE;
        button.setMaximumSize(pref);
    }

    /**
     * Create a text & image button and add it to a panel.
     *
     * @param imgRsrcName    The name of the image resource for the button.
     * @param panel          The panel to add the button to.
     * @param margin         The margin around the button.
     */
    private JButton addButton(String imgRsrcName, String buttonText, JPanel panel, Insets margin)
    {
        JButton button;
        button = new JButton(buttonText, Config.getImageAsIcon(imgRsrcName));
        button.setVerticalTextPosition(AbstractButton.BOTTOM);
        button.setHorizontalTextPosition(AbstractButton.CENTER);
        button.setEnabled(false);

        //button.setMargin(margin);
        button.addActionListener(this);
        panel.add(button);
        return button;
    }
}
