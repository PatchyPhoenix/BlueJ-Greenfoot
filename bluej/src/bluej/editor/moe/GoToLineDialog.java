// Copyright (c) 2000, 2005 BlueJ Group, Deakin University
//
// This software is made available under the terms of the "MIT License"
// A copy of this license is included with this source distribution
// in "license.txt" and is also available at:
// http://www.opensource.org/licenses/mit-license.html 
// Any queries should be directed to Michael Kolling mik@bluej.org

package bluej.editor.moe;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.text.*;

import bluej.*;
import bluej.utility.EscapeDialog;


/**
 * Dialog for user to input a line number to traverse source file in editor
 * 
 * @author Bruce Quig
 */
public class GoToLineDialog extends EscapeDialog implements ActionListener
{
    static final String goToLineTitle = Config.getString("editor.gotoline.title");
    static final String goToLineLabel = Config.getString("editor.gotoline.label");
    static final String notNumericMessage = Config.getString("editor.gotoline.notNumericMessage");
    static final String notInRangeMessage = Config.getString("editor.gotoline.notInRangeMessage");
    static final int INVALID_NUMBER = -1;
    
    // -------- INSTANCE VARIABLES --------
    private JButton okButton;
    private JButton cancelButton;
    private JTextField lineNumberField;
    private JLabel instructionLabel;
    private JLabel messageLabel;
    private int lineNumber = INVALID_NUMBER;
    private int sizeOfClass;
    
    
    /**
     * Creates a new GoToLineDialog object.
     */
    public GoToLineDialog(Frame owner)
    {
        super(owner, goToLineTitle, true);
        makeDialog();
    }

    /**
     * Make the dialod visible.
     * @param range the number of lines of source code in source file
     */
    public void showDialog(int range)
    {
        //getRootPane().setDefaultButton(okButton);
        sizeOfClass = range;
        instructionLabel.setText(goToLineLabel + " ( 1 - " + range + " )"); 
        lineNumberField.requestFocus();
        setVisible(true);
    }

    // === Actionlistener interface ===

    /**
     * A button was pressed. Find out which one and do the appropriate thing.
     */
    public void actionPerformed(ActionEvent evt)
    {
        Object src = evt.getSource();

        if (src == okButton) {
            doOK();
        } else if (src == cancelButton) {
            doCancel();
        }
    }
    
    /**
    * Setup the dialog
    */
    private void makeDialog()
    {
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent E)
            {
                doCancel();
            }
        });
       
        
        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
   
        instructionLabel = new JLabel(goToLineLabel);
        bodyPanel.add(instructionLabel);
        bodyPanel.add(Box.createVerticalStrut(6));
   
        lineNumberField = new JTextField();
        bodyPanel.add(lineNumberField);
        bodyPanel.add(Box.createVerticalStrut(6));
        
        IntegerDocument integerDocument1 = new IntegerDocument();
        lineNumberField.setDocument(integerDocument1);
        
        messageLabel = new JLabel(" ");
        bodyPanel.add(messageLabel);
        bodyPanel.add(Box.createVerticalStrut(6));
        
        // add buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);

        okButton = BlueJTheme.getOkButton();
        okButton.addActionListener(this);

        cancelButton = BlueJTheme.getCancelButton();
        cancelButton.addActionListener(this);

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        getRootPane().setDefaultButton(okButton);
        
        bodyPanel.add(buttonPanel);
        getContentPane().add(bodyPanel, BorderLayout.CENTER);
        pack();
      
    }
    
    /**
     * When ok button is selected
     */
    private void doOK()
    {
        lineNumber = validateInput();
        clear();
        setVisible(false);   
    }
    
    /**
     * When cancel button is selected
     */
    private void doCancel()
    {
        lineNumber = INVALID_NUMBER;
        setVisible(false);   
    }
    /**
    * Clear the line number text field
    */
    private void clear()
    {
        lineNumberField.setText("");   
    }
    
    /**
    * Returns the lineNumber.
    * @return the line number entered, returns -1 if input is invalid
    */
    public int getLineNumber() 
    {
        return lineNumber;
    }
    
    /**
     * Convert input field contents to an int
     * @return int the String input value as an int 
     * representing line number
     */
    private int validateInput()
    {
        int validatedNumber = -1;
        try {
            validatedNumber = Integer.parseInt(lineNumberField.getText());
        } catch (NumberFormatException nfe) {
         	//shouldn't happen, verified at data model level
        }
        return validatedNumber;
    }
    
    /**
    * Inner class that provides the formatted (Integer only) data model
    * for the line number text field
    */
    class IntegerDocument extends PlainDocument 
    {
        /**
        * Inserts into Document model.  Checks for format and for range
        */
        public void insertString(int offset, String string, AttributeSet attributes)
            throws BadLocationException 
        {
            if (string == null) {
                return;
            } else {
                String newValue;
                int length = getLength();
                if (length == 0) {
                    newValue = string;
                } else {
                    String currentContent = getText(0, length);
                    StringBuffer currentBuffer = new StringBuffer(currentContent);
                    currentBuffer.insert(offset, string);
                    newValue = currentBuffer.toString();
                }
                try {
                    int parsedNumber = checkInputIsInteger(newValue);
                    if(checkInputRange(parsedNumber)) {
                        super.insertString(offset, string, attributes);
                        messageLabel.setText(" ");
                    }
                } catch (NumberFormatException exception) {
                    Toolkit.getDefaultToolkit().beep();
                    messageLabel.setText(notNumericMessage);
                }
            }
        }
      
        /**
        * Check that the String is an integer
        */
        private int checkInputIsInteger(String proposedValue) 
           throws NumberFormatException 
        {
            int newValue = 0;
            if (proposedValue.length() > 0) {
                newValue = Integer.parseInt(proposedValue);
            }
            return newValue;
        }
       
        /**
        * Check that the int parameter is within range, meaning that it is 
        * greater than 0 and not greater than the number of lines in the source file
        */
        private boolean checkInputRange(int parsedNumber)
        {
            return(parsedNumber > 0 && parsedNumber <= sizeOfClass);   
        }
    }
} // end class GoToLineDialog
