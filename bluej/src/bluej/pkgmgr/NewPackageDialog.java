package bluej.pkgmgr;

import bluej.*;
import bluej.Config;
import bluej.utility.JavaNames;
import bluej.utility.DialogManager;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * Dialog for creating a new Package
 *
 * @author  Justin Tan
 * @author  Michael Kolling
 * @version $Id: NewPackageDialog.java 1923 2003-04-30 06:11:12Z ajp $
 */
class NewPackageDialog extends JDialog
{
    private String newPackageName = "";

    private JTextField textFld;

    private boolean ok;		// result: which button?

	public NewPackageDialog(JFrame parent)
	{
		super(parent, Config.getString("pkgmgr.newPackage.title"), true);

		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent E)
			{
				ok = false;
				setVisible(false);
			}
		});

		JPanel mainPanel = new JPanel();
		{
			mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
			mainPanel.setBorder(BlueJTheme.dialogBorder);

			JLabel newclassTag = new JLabel(Config.getString("pkgmgr.newPackage.label"));
			{
				newclassTag.setAlignmentX(LEFT_ALIGNMENT);
			}

			textFld = new JTextField(24);
			{
				textFld.setAlignmentX(LEFT_ALIGNMENT);
			}

			mainPanel.add(newclassTag);
			mainPanel.add(textFld);
			mainPanel.add(Box.createVerticalStrut(5));

			mainPanel.add(Box.createVerticalStrut(BlueJTheme.dialogCommandButtonsVertical));

			JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			{
				buttonPanel.setAlignmentX(LEFT_ALIGNMENT);

				JButton okButton = BlueJTheme.getOkButton();
				okButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent evt) { doOK(); }        		
				});

				JButton cancelButton = BlueJTheme.getCancelButton();
				cancelButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent evt) { doCancel(); }        		
				});

				buttonPanel.add(okButton);
				buttonPanel.add(cancelButton);

				getRootPane().setDefaultButton(okButton);
			}

			mainPanel.add(buttonPanel);
		}

		getContentPane().add(mainPanel);
		pack();

		DialogManager.centreDialog(this);
	}

    /**
     * Show this dialog and return true if "OK" was pressed, false if
     * cancelled.
     */
    public boolean display()
    {
        ok = false;
        textFld.requestFocus();
        setVisible(true);
        return ok;
    }

    public String getPackageName()
    {
        return newPackageName;
    }

    /**
     * Close action when OK is pressed.
     */
    public void doOK()
    {
        newPackageName = textFld.getText().trim();

        if (JavaNames.isQualifiedIdentifier(newPackageName)) {
            ok = true;
            setVisible(false);
        }
        else {
            DialogManager.showError((JFrame)this.getParent(), "invalid-package-name");
            textFld.selectAll();
            textFld.requestFocus();
        }
    }

    /**
     * Close action when Cancel is pressed.
     */
    public void doCancel()
    {
        ok = false;
        setVisible(false);
    }
}
