// Copyright (c) 2000, 2005 BlueJ Group, Deakin University
//
// This software is made available under the terms of the "MIT License"
// A copy of this license is included with this source distribution
// in "license.txt" and is also available at:
// http://www.opensource.org/licenses/mit-license.html 
// Any queries should be directed to Michael Kolling mik@bluej.org

package bluej.editor.moe;

import bluej.utility.Debug;

import java.awt.*;
import java.awt.event.*;

import javax.swing.text.*;


/**
 * A customised caret for Moe. It gets most of its bahaviour from
 * Swing's "DefaultCaret" and adds some functionality.
 *
 * @author  Michael Kolling
 */

public class MoeCaret extends DefaultCaret  
{
    private static final Color bracketHighlightColour = new Color(196, 196, 196);
    
    private static final LayeredHighlighter.LayerPainter bracketPainter = 
        new BracketMatchPainter(bracketHighlightColour);
        
    private MoeEditor editor;

    private boolean persistentHighlight = false;
    
    // matching bracket highlight holder
    private Object matchingBracketHighlight;

    /**
     * Constructs a Moe Caret
     */
    public MoeCaret(MoeEditor editor) 
    {
        super();
        this.editor = editor;
        setBlinkRate(0);
    }

    /**
     * Redefinition of caret positioning (after mouse click). Here, we
     * first check whether the click was in the tag line. If it was, we
     * toggle the breakpoint, if not we just position the caret as usual.
     */
    protected void positionCaret(MouseEvent e) 
    {
        editor.caretMoved();
        Point pt = new Point(e.getX(), e.getY());
        Position.Bias[] biasRet = new Position.Bias[1];
        int pos = getComponent().getUI().viewToModel(getComponent(), pt, biasRet);

        if (e.getX() > BlueJSyntaxView.TAG_WIDTH) {
            if(biasRet[0] == null)
                biasRet[0] = Position.Bias.Forward;
            if (pos >= 0) {
                setDot(pos); 
//                setMagicCaretPosition(null);
            }
        }
        else {
            editor.toggleBreakpoint(pos);
        }
    }

    /**
     * Tries to move the position of the caret from
     * the coordinates of a mouse event, using viewToModel(). 
     * This will cause a selection if the dot and mark
     * are different.
     *
     * @param e the mouse event
     */
    protected void moveCaret(MouseEvent e) 
    {
        if (e.getX() > BlueJSyntaxView.TAG_WIDTH) {
            super.moveCaret(e);
        }
    }
    
    /**
     * Set the dot and mark position
     */
    public void setDot(int pos)
    {
        persistentHighlight = false;
        super.setDot(pos);
    }
    
    /**
     * Set the dot position (leave the mark where it is).
     */
    public void moveDot(int pos)
    {
        persistentHighlight = false;
        super.moveDot(pos);
    }

    /**
     * Fire a state canged event.
     */
    protected void fireStateChanged()
    {
        editor.caretMoved();
        super.fireStateChanged();
    }
    
    /**
     * Target text component lost focus.
     */
    public void focusLost(FocusEvent e)
    {
        super.focusLost(e);
        if (persistentHighlight)
            setSelectionVisible(true);
    }
    
    /**
     * Set the highlight (of the selection) as persistent - that is, it won't
     * become invisible if the component loses focus. This lasts until the
     * caret position is changed.
     */
    public void setPersistentHighlight()
    {
        setSelectionVisible(true);
        persistentHighlight = true;
    }
     
    /**
     * paint matching bracket if caret is directly after a bracket  
     *
     */
    public void paintMatchingBracket()
    {
        int matchBracket = editor.getBracketMatch();
        // remove existing bracket if needed
        removeBracket();
        if(matchBracket != -1) {
            try {
                matchingBracketHighlight = getComponent().getHighlighter().addHighlight(matchBracket, matchBracket + 1, bracketPainter);
            }
            catch(BadLocationException ble) {
                Debug.reportError("bad location exception thrown");
                ble.printStackTrace();
            }
        }      
    }
    
    /**
     * remove the existing matching bracket if it exists
     */
    public void removeBracket()
    {
        if(matchingBracketHighlight != null) {
            getComponent().getHighlighter().removeHighlight(matchingBracketHighlight);
            matchingBracketHighlight = null;        
        }  
    }
    
}


