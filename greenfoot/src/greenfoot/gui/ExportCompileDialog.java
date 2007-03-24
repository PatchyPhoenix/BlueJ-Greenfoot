package greenfoot.gui;

import greenfoot.actions.CompileAllAction;
import greenfoot.core.GProject;
import greenfoot.event.CompileListener;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import rmiextension.wrappers.event.RCompileEvent;

import bluej.BlueJTheme;
import bluej.utility.DialogManager;
import bluej.utility.EscapeDialog;

/**
 * Dialog to be used when the project is not compiled and an export is
 * attempted. The dialog will ask the user to compile all classes. 
 * 
 * @author Poul Henriksen
 * 
 */
public class ExportCompileDialog extends EscapeDialog implements CompileListener
{
    private String helpLine = "Not all the classes in the project are compiled. To export, all classes must be compiled. To continue with the export, compile the classes now.";
    private boolean ok;
    private GProject project;
    
    /**
     * Creates a new dialog. This dialog should listen for compile events.
     * @param parent
     * @param project
     */
    public ExportCompileDialog(Frame parent, GProject project)
    {
        super(parent, "Project not compiled.", true);
        makeDialog();
        this.project = project;
    }
    

    /**
     * Show this dialog and return true if everything was compiled, false otherwise.
     */
    public boolean display()
    {
        ok = false;
        setVisible(true);  // returns after Compiled All or Cancel, which set 'ok'
        return ok;
    }
    
    /**
     * Create the dialog interface.
     */
    private void makeDialog()
    {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel();
        {
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
            mainPanel.setBorder(BlueJTheme.dialogBorder);

            WrappingMultiLineLabel helpText = new WrappingMultiLineLabel(helpLine, 60);
            mainPanel.add(helpText);


            mainPanel.add(Box.createVerticalStrut(BlueJTheme.dialogCommandButtonsVertical));


            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            {
                buttonPanel.setAlignmentX(LEFT_ALIGNMENT);

                JButton compileButton = new JButton(CompileAllAction.getInstance());

                JButton cancelButton = BlueJTheme.getCancelButton();
                cancelButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent evt) { doCancel(); }                
                });

                buttonPanel.add(compileButton);
                buttonPanel.add(cancelButton);

                getRootPane().setDefaultButton(compileButton);
            }

            mainPanel.add(buttonPanel);
        }

        getContentPane().add(mainPanel);
        pack();

        DialogManager.centreDialog(this);
    }
    

    /**
     * Close action when Cancel is pressed.
     */
    private void doCancel()
    {
        ok = false;
        dispose();
    }
    
    
    /**
     * Close action when everything is compiled.
     */
    private void doOk()
    {
        ok = true;
        dispose();        
    }
    
    public void compileError(RCompileEvent event)
    {
    }
    
    public void compileFailed(RCompileEvent event)
    {
        doCancel();
    }
    public void compileStarted(RCompileEvent event)
    {

    }
    public void compileSucceeded(RCompileEvent event)
    {
        if(project.isCompiled()) {
            doOk();
        }
    }
    public void compileWarning(RCompileEvent event)
    {
        
    }
}
