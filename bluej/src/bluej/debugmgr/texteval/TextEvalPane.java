package bluej.debugmgr.texteval;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.text.*;

import org.syntax.jedit.tokenmarker.JavaTokenMarker;

import bluej.BlueJEvent;
import bluej.Config;
import bluej.debugger.DebuggerObject;
import bluej.debugmgr.*;
import bluej.debugmgr.inspector.ObjectInspector;
import bluej.editor.moe.BlueJSyntaxView;
import bluej.editor.moe.MoeSyntaxDocument;
import bluej.editor.moe.MoeSyntaxEditorKit;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.testmgr.record.InvokerRecord;
import bluej.utility.Debug;
import bluej.utility.JavaNames;

/**
 * A modified editor pane for the text evaluation area.
 * The standard JEditorPane is adjusted to take the tag line to the left into
 * account in size computations.
 * 
 * @author Michael Kolling
 * @version $Id: TextEvalPane.java 3341 2005-04-08 04:12:53Z bquig $
 */
public class TextEvalPane extends JEditorPane 
    implements ResultWatcher, MouseMotionListener
{
    // The cursor to use while hovering over object icon
    private static final Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
    private static final Cursor objectCursor = new Cursor(Cursor.HAND_CURSOR);
    private static final Cursor textCursor = new Cursor(Cursor.TEXT_CURSOR);
    
    private static final String nullLabel = Config.getString("debugger.null");

    private PkgMgrFrame frame;
    private MoeSyntaxDocument doc;  // the text document behind the editor pane
    private String currentCommand = "";
    private IndexHistory history;
    private Invoker invoker = null;
    private boolean firstTry;
    private boolean wrappedResult;
    private boolean mouseInTag = false;
    private boolean mouseOverObject = false;
    private boolean busy = false;
    private Action softReturnAction;

    public TextEvalPane(PkgMgrFrame frame)
    {
        super();
        this.frame = frame;
        setEditorKit(new MoeSyntaxEditorKit(true));
        doc = (MoeSyntaxDocument) getDocument();
        doc.setTokenMarker(new JavaTokenMarker());
        defineKeymap();
        clear();
        history = new IndexHistory(20);
        addMouseMotionListener(this);
        setCaret(new TextEvalCaret());
        setAutoscrolls(false);          // important - dragging objects from this component
                                        // does not work correctly otherwise
    }
    
    public Dimension getPreferredSize() 
    {
        Dimension d = super.getPreferredSize();
        d.width += BlueJSyntaxView.TAG_WIDTH + 8;  // bit of empty space looks nice
        return d;
    }
    
    /**
     * Make sure, when we are scrolling to follow the caret,
     * that we can see the tag area as well.
     */
    public void scrollRectToVisible(Rectangle rect)
    {
        super.scrollRectToVisible(new Rectangle(rect.x - (BlueJSyntaxView.TAG_WIDTH + 4), rect.y,
                rect.width + BlueJSyntaxView.TAG_WIDTH + 4, rect.height));
    }
    
    /**
     * Clear all text in this text area.
     */
    public void clear()
    {
        setText(" ");
        caretToEnd();
    }

    /**
     * Paste the contents of the clipboard.
     */
    public void paste()
    {
        ensureLegalCaretPosition();
        super.paste();
    }

    /**
     * This is called when we get a 'paste' action (since we are handling 
     * ordinary key input differently with the InsertCharacterAction.
     * So: here we assume that we have a potential multi-line paste, and we
     * want to treat it accordingly (as multi-line input).
     */
    public void replaceSelection(String content)
    {
        ensureLegalCaretPosition();

        String[] lines = content.split("\n");
        super.replaceSelection(lines[0]);
        for(int i=1; i< lines.length;i++) {
            softReturnAction.actionPerformed(null);
            super.replaceSelection(lines[i]);
        }
    }
    
    //   --- ResultWatcher interface ---

    /**
     * An invocation has completed - here is the result.
     * If the invocation has a void result (note that is a void type), result == null.
     */
    public void putResult(final DebuggerObject result, final String name, final InvokerRecord ir)
    {
        currentCommand = "";
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                frame.getObjectBench().addInteraction(ir);
                
                append(" ");
                if (result != null) {
                    //Debug.message("type:"+result.getFieldValueTypeString(0));
                    
                    String resultString = result.getFieldValueString(0);
                    
                    if(resultString.equals(nullLabel)) {
                        output(resultString);
                    }
                    else {
                        String resultType;
                        boolean isObject = result.instanceFieldIsObject(0);
                        
                        if(isObject) {
                            resultType = result.getFieldObject(0).getStrippedGenClassName();
                            objectOutput(resultString + "   (" + resultType + ")", 
                                    new ObjectInfo(result.getFieldObject(0), ir));
                        }
                        else {
                            resultType = JavaNames.stripPrefix(result.getFieldValueTypeString(0));
                            output(resultString + "   (" + resultType + ")");
                        }
                    }            
                    BlueJEvent.raiseEvent(BlueJEvent.METHOD_CALL, resultString);
                } 
                else {
                    BlueJEvent.raiseEvent(BlueJEvent.METHOD_CALL, null);
                }
                setEditable(true);    // allow next input
                busy = false;
            }
        });
    }
    
    /**
     * An invocation has failed - here is the error message
     */
    public void putError(final String message)
    {
        if(firstTry) {
            // append("   --error, first try: " + message + "\n");
            if (wrappedResult) {
                // We thought we knew what the result type should be, but there
                // was a compile time error. So try again, assuming that we
                // got it wrong.
                wrappedResult = false;
                invoker = new Invoker(frame, currentCommand, TextEvalPane.this, "");
            }
            else {
                firstTry = false;
                invoker.tryAgain();
            }
        }
        else {
            currentCommand = "";
            EventQueue.invokeLater(new Runnable() {
                public void run()
                {
                    append(" ");
                    error(message);
                    setEditable(true);    // allow next input
                    busy = false;
                }
            });
        }
    }
    
    /**
     * A watcher shuold be able to return information about the result that it
     * is watching. This may be used to display extra information 
     * (about the expression that gave the result) when the result is shown.
     * Unused for text eval expressions.
     * 
     * @return An object with information on the expression
     */
    public ExpressionInformation getExpressionInformation()
    {
        return null;
    }

    //   --- end of ResultWatcher interface ---
    
    /**
     * We had a click in the tag area. Handle it appropriately.
     * Specifically: If the click (or double click) is on an object, then
     * start an object drag (or inspect).
     * @param pos   The text position where we got the click.
     * @param clickCount  Number of consecutive clicks
     */
    public void tagAreaClick(int pos, int clickCount)
    {
        ObjectInfo objInfo = objectAtPosition(pos);
        if(objInfo != null) {
            if(clickCount == 1) {
                DragAndDropHelper dnd = DragAndDropHelper.getInstance();
                dnd.startDrag(this, frame, objInfo.obj, objInfo.ir);
            }
            else if(clickCount == 2) {   // double click
                inspectObject(objInfo);
            }
        }
    }
    
    /**
     * Inspect the given object.
     * This is done with a delay, because we are in the middle of a mouse click,
     * and focus gets weird otherwise.
     */
    private void inspectObject(TextEvalPane.ObjectInfo objInfo)
    {
        final TextEvalPane.ObjectInfo oi = objInfo;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                frame.getProject().getInspectorInstance(oi.obj, null, frame.getPackage(), oi.ir, frame);
            }
        });
    }

    /**
     * Write a (non-error) message to the text area.
     * @param s The message
     */
    private void output(String s)
    {
        try {
            doc.insertString(doc.getLength(), s, null);
            markAs(TextEvalSyntaxView.OUTPUT, Boolean.TRUE);
        }
        catch(BadLocationException exc) {
            Debug.reportError("bad location in terminal operation");
        }
    }
    
    /**
     * Write a (non-error) message to the text area.
     * @param s The message
     */
    private void objectOutput(String s, ObjectInfo objInfo)
    {
        try {
            doc.insertString(doc.getLength(), s, null);
            markAs(TextEvalSyntaxView.OBJECT, objInfo);
        }
        catch(BadLocationException exc) {
            Debug.reportError("bad location in terminal operation");
        }
    }
    
    /**
     * Write an error message to the text area.
     * @param s The message
     */
    private void error(String s)
    {
        try {
            doc.insertString(doc.getLength(), "Error: " + s, null);
            markAs(TextEvalSyntaxView.ERROR, Boolean.TRUE);
        }
        catch(BadLocationException exc) {
            Debug.reportError("bad location in terminal operation");
        }
    }
    
    /**
     * Append some text to this area.
     * @param s The text to append.
     */
    private void append(String s)
    {
        try {
            doc.insertString(doc.getLength(), s, null);
            caretToEnd();
        }
        catch(BadLocationException exc) {
            Debug.reportError("bad location in terminal operation");
        }
    }
    
    /**
     * Move the caret to the end of the text.
     */
    private void caretToEnd() 
    {
        setCaretPosition(doc.getLength());
    }

    /**
     * Ensure that the caret position (including the whole
     * selection, if any) is within the editale area (the last 
     * line of text). If it isn't, adjust it so that it is.
     */
    private void ensureLegalCaretPosition()
    {
        Caret caret = getCaret();
        boolean dotOK = isLastLine(caret.getDot());
        boolean markOK = isLastLine(caret.getMark());
        
        if(dotOK && markOK)     // both in last line - no problem
            return;
        
        if(!dotOK && !markOK) { // both not in last line - append at end
            setCaretPosition(getDocument().getLength());
        }
        else {                  // selection reaches into last line
            caret.setDot(Math.max(caret.getDot(), caret.getMark()));
            caret.moveDot(startOfLastLine());
        }
    }
    
    /**
     * Check whether the given text position is within the area
     * intended for editing (the last line).
     * 
     * @param pos  The position to be checked
     * @return  True if this position is within the last text line.
     */
    private boolean isLastLine(int pos)
    {
        return pos >= startOfLastLine();
    }
    
    /**
     * Return the text position of the start of the last text line
     * (the start of the area editable by the user).
     * 
     * @return  The position of the start of the last text line.
     */
    private int startOfLastLine()
    {
        AbstractDocument doc = (AbstractDocument) getDocument();
        Element line = doc.getParagraphElement(doc.getLength());
        return line.getStartOffset() + 1;  // ignore space at front
    }
    
    /**
     * Get the text of the current line (the last line) of this area.
     * @return The text of the last line.
     */
    private String getCurrentLine()
    {
        Element line = doc.getParagraphElement(doc.getLength());
        int lineStart = line.getStartOffset() + 1;  // ignore space at front
        int lineEnd = line.getEndOffset() - 1;      // ignore newline char
        
        try {
            return doc.getText(lineStart, lineEnd-lineStart);
        }
        catch(BadLocationException exc) {
            Debug.reportError("bad location in text eval operation");
            return "";
        }
    }
    
    /**
     * Return the current column number.
     */
    private int getCurrentColumn()
    {
        Caret caret = getCaret();
        int pos = Math.min(caret.getMark(), caret.getDot());
        return getColumnFromPosition(pos);
    }

    /**
     * Return tha column for a given position.
     */
    private int getColumnFromPosition(int pos)
    {
        int lineStart = doc.getParagraphElement(pos).getStartOffset();
        return (pos - lineStart);       
    }
    
    /**
     * Mark the last line of the text area as output. and start a new 
     * line after that one.
     */
    private void markAs(String flag, Object value)
    {
        append("\n ");          // ensure space at the beginning of every line
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(flag, value);
        doc.setParagraphAttributes(doc.getLength()-2, a);
        repaint();
    }
    
    /**
     * Mark the current line of the text area as output.
     */
    private void markCurrentAs(String flag, Object value)
    {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(flag, value);
        doc.setParagraphAttributes(doc.getLength(), a);
    }
    
     /**
     * Replace the text of the current line with some new text.
     * @param s The new text for the line.
     */
    private void replaceLine(String s)
    {
        Element line = doc.getParagraphElement(doc.getLength());
        int lineStart = line.getStartOffset() + 1;  // ignore space at front
        int lineEnd = line.getEndOffset() - 1;      // ignore newline char
        
        try {
                doc.replace(lineStart, lineEnd-lineStart, s, null);
        }
        catch(BadLocationException exc) {
            Debug.reportError("bad location in text eval operation");
        }
    }
    
    /**
     * Return the object stored with the line at position 'pos'.
     * If that line does not have an object, return null.
     */
    private ObjectInfo objectAtPosition(int pos)
    {
        Element line = getLineAt(pos);
        return (ObjectInfo) line.getAttributes().getAttribute(TextEvalSyntaxView.OBJECT);
    }

    /**
     *  Find and return a line by text position
     */
    private Element getLineAt(int pos)
    {
        return doc.getParagraphElement(pos);
    }

    /**
     * Check whether a given point on screen is over an object icon.
     */
    private boolean pointOverObjectIcon(int x, int y)
    {
        int pos = getUI().viewToModel(this, new Point(x, y));
        ObjectInfo objInfo = objectAtPosition(pos);
        return objInfo != null;        
    }
    
    // ---- MouseMotionListener interface: ----
    
    public void mouseDragged(MouseEvent evt) {}

    /**
     * When the mouse is moved, check whether we should change the 
     * mouse cursor.
     */
    public void mouseMoved(MouseEvent evt) 
    {
        int x = evt.getX();
        int y = evt.getY();
        
        if(mouseInTag) {
            if(x > BlueJSyntaxView.TAG_WIDTH) {    // moved out of tag area
                setCursor(textCursor);
                mouseInTag = false;
            }
            else 
                setTagAreaCursor(x, y);
        }
        else {
            if(x <= BlueJSyntaxView.TAG_WIDTH) {   // moved into tag area
                setCursor(defaultCursor);
                mouseOverObject = false;
                setTagAreaCursor(x, y);
                mouseInTag = true;
            }
        }
    }

    /**
     * Set the mouse cursor for the tag area. 
     */
    private void setTagAreaCursor(int x, int y)
    {
        if(pointOverObjectIcon(x, y) != mouseOverObject) {  // entered or left object
            mouseOverObject = !mouseOverObject;
            if(mouseOverObject)
                setCursor(objectCursor);
            else
                setCursor(defaultCursor);
        }        
    }

    // ---- end of MouseMotionListener interface ----

    /**
     * Set the keymap for this text area. Especially: take care that cursor 
     * movement is restricted so that the cursor remains in the last line,
     * and interpret Return keys to evaluate commands.
     */
    private void defineKeymap()
    {
        Keymap newmap = JTextComponent.addKeymap("texteval", getKeymap());

        Action action = new InsertCharacterAction();
        newmap.setDefaultAction(action);

        action = new ExecuteCommandAction();
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), action);

        softReturnAction = new ContinueCommandAction();
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Event.SHIFT_MASK), softReturnAction);

        action = new BackSpaceAction();
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), action);
        
        action = new CursorLeftAction();
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), action);
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_KP_LEFT, 0), action);

        action = new HistoryBackAction();
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), action);
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, 0), action);

        action = new HistoryForwardAction();
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), action);
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, 0), action);

        action = new TransferFocusAction(true);
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), action);

        action = new TransferFocusAction(false);
        newmap.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, Event.SHIFT_MASK), action);

        setKeymap(newmap);
    }
    
    // ======= Actions =======
    
    final class InsertCharacterAction extends AbstractAction {

        /**
         * Create a new action object. This action executes the current command.
         */
        public InsertCharacterAction()
        {
            super("InsertCharacter");
        }
        
        /**
         * Insert a character into the text.
         */
        final public void actionPerformed(ActionEvent event)
        {
            if(!isEditable())
                return;
            
            String s = event.getActionCommand();  // will always be length 1
            if(s.charAt(0) != '\n') {             // bug workaround: enter goes through default
                                                  //  action as well as set action
                replaceSelection(s);
            }
        }
    }

    final class ExecuteCommandAction extends AbstractAction {

        /**
         * Create a new action object. This action executes the current command.
         */
        public ExecuteCommandAction()
        {
            super("ExecuteCommand");
        }
        
        /**
         * Execute the text of the current line in the text area as a Java command.
         */
        final public void actionPerformed(ActionEvent event)
        {
            if (busy)
                return;
            
            String line = getCurrentLine();
            currentCommand = (currentCommand + line).trim();
            if(currentCommand.length() != 0) {
                       
                history.add(line);
                append("\n");      // ensure space at the beginning of every line, because
                                    // line properties do not work otherwise
                markCurrentAs(TextEvalSyntaxView.OUTPUT, Boolean.TRUE);
                firstTry = true;
                setEditable(false);    // don't allow input while we're thinking
                busy = true;
                TextParser tp = new TextParser(frame.getProject().getLocalClassLoader(), frame.getPackage().getQualifiedName(), frame.getObjectBench());
                String retType = tp.parseCommand(currentCommand);
                wrappedResult = (retType != null && retType.length() != 0);
                invoker = new Invoker(frame, currentCommand, TextEvalPane.this, retType);
            }
            else {
                markAs(TextEvalSyntaxView.OUTPUT, Boolean.TRUE);
            }
        }
    }

    final class ContinueCommandAction extends AbstractAction {

        /**
         * Create a new action object. This action reads the current
         * line as a start for a new command and continues reading the 
         * command in the next line.
         */
        public ContinueCommandAction()
        {
            super("ContinueCommand");
        }
        
        /**
         * Read the text of the current line in the text area as the
         * start of a Java command and continue reading in the next line.
         */
        final public void actionPerformed(ActionEvent event)
        {
            if (busy)
                return;
            
            String line = getCurrentLine();
            currentCommand += line + " ";
            history.add(line);
            markAs(TextEvalSyntaxView.CONTINUE, Boolean.TRUE);
        }
    }

    final class BackSpaceAction extends AbstractAction {

        /**
         * Create a new action object.
         */
        public BackSpaceAction()
        {
            super("BackSpace");
        }
        
        /**
         * Perform a backspace action.
         */
        final public void actionPerformed(ActionEvent event)
        {
            if (busy)
                return;
            
            if(getCurrentColumn() > 1) {
                try {
                    if(getSelectionEnd() == getSelectionStart()) { // no selection
                        doc.remove(getCaretPosition()-1, 1);
                    }
                    else {
                        replaceSelection("");
                    }
                }
                catch(BadLocationException exc) {
                    Debug.reportError("bad location in text eval operation");
                }
            }
        }
    }

    final class CursorLeftAction extends AbstractAction {

        /**
         * Create a new action object.
         */
        public CursorLeftAction()
        {
            super("CursorLeft");
        }

        /**
         * Move the cursor left (if allowed).
         */
        final public void actionPerformed(ActionEvent event)
        {
            if (busy)
                return;
            
            if(getCurrentColumn() > 1) {
                Caret caret = getCaret();
                caret.setDot(caret.getDot() - 1);
            }
        }
    }

    final class HistoryBackAction extends AbstractAction {

        /**
         * Create a new action object.
         */
        public HistoryBackAction()
        {
            super("HistoryBack");
        }
        
        /**
         * Set the current line to the previous input history entry.
         */
        final public void actionPerformed(ActionEvent event)
        {
            if (busy)
                return;
            
            String line = history.getPrevious();
            if(line != null) {
                replaceLine(line);
            }
        }

    }

    final class HistoryForwardAction extends AbstractAction {

        /**
         * Create a new action object.
         */
        public HistoryForwardAction()
        {
            super("HistoryForward");
        }
        
        /**
         * Set the current line to the next input history entry.
         */
        final public void actionPerformed(ActionEvent event)
        {
            if (busy)
                return;
            
            String line = history.getNext();
            if(line != null) {
                replaceLine(line);
            }
        }

    }

    final class TransferFocusAction extends AbstractAction {
        private boolean forward;
        /**
         * Create a new action object.
         */
        public TransferFocusAction(boolean forward)
        {
            super("TransferFocus");
            this.forward = forward;
        }
        
        /**
         * Transfer the keyboard focus to another component.
         */
        final public void actionPerformed(ActionEvent event)
        {
            if(forward)
                transferFocus();
            else
                transferFocusBackward();
        }

    }    

    final class ObjectInfo {
        DebuggerObject obj;
        InvokerRecord ir;
        
        /**
         * Create an object holding information about an invocation.
         */
        public ObjectInfo(DebuggerObject obj, InvokerRecord ir) {
            this.obj = obj;
            this.ir = ir;
        }
    }
    

}
