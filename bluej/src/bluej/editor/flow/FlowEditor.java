/*
 This file is part of the BlueJ program. 
 Copyright (C) 2019  Michael Kolling and John Rosenberg

 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 

 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 

 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 This file is subject to the Classpath exception as provided in the
 LICENSE.txt file that accompanied this code.
 */
package bluej.editor.flow;

import bluej.BlueJEvent;
import bluej.BlueJEventListener;
import bluej.Config;
import bluej.compiler.CompileReason;
import bluej.compiler.CompileType;
import bluej.compiler.Diagnostic;
import bluej.debugger.DebuggerThread;
import bluej.editor.EditorWatcher;
import bluej.editor.TextEditor;
import bluej.editor.flow.FlowActions.FlowAbstractAction;
import bluej.editor.flow.FlowEditorPane.FlowEditorPaneListener;
import bluej.editor.flow.FlowEditorPane.SelectionListener;
import bluej.editor.flow.FlowErrorManager.ErrorDetails;
import bluej.editor.flow.JavaSyntaxView.ParagraphAttribute;
import bluej.editor.flow.MarginAndTextLine.MarginDisplay;
import bluej.editor.flow.StatusLabel.Status;
import bluej.editor.moe.GoToLineDialog;
import bluej.editor.moe.Info;
import bluej.editor.moe.MoeEditor;
import bluej.editor.moe.ParserMessageHandler;
import bluej.editor.moe.ScopeColorsBorderPane;
import bluej.editor.stride.FXTabbedEditor;
import bluej.editor.stride.FlowFXTab;
import bluej.editor.stride.FrameEditor;
import bluej.parser.SourceLocation;
import bluej.parser.entity.EntityResolver;
import bluej.parser.entity.JavaEntity;
import bluej.parser.nodes.MethodNode;
import bluej.parser.nodes.NodeTree.NodeAndPosition;
import bluej.parser.nodes.ParsedCUNode;
import bluej.parser.nodes.ParsedNode;
import bluej.parser.nodes.ReparseableDocument;
import bluej.parser.symtab.ClassInfo;
import bluej.pkgmgr.JavadocResolver;
import bluej.pkgmgr.Project;
import bluej.prefmgr.PrefMgr;
import bluej.prefmgr.PrefMgr.PrintSize;
import bluej.stride.framedjava.elements.CallElement;
import bluej.stride.framedjava.elements.NormalMethodElement;
import bluej.utility.Debug;
import bluej.utility.DialogManager;
import bluej.utility.Utility;
import bluej.utility.javafx.FXPlatformConsumer;
import bluej.utility.javafx.FXPlatformRunnable;
import bluej.utility.javafx.FXRunnable;
import bluej.utility.javafx.JavaFXUtil;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PopupControl;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Skin;
import javafx.scene.control.Skinnable;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebView;
import javafx.stage.PopupWindow;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.EventTarget;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class FlowEditor extends ScopeColorsBorderPane implements TextEditor, FlowEditorPaneListener, SelectionListener, BlueJEventListener
{
    // suffixes for resources
    final static String LabelSuffix = "Label";
    final static String ActionSuffix = "Action";

    private final FlowEditorPane flowEditorPane;
    private final HoleDocument document;
    private final JavaSyntaxView javaSyntaxView;
    private final FetchTabbedEditor fetchTabbedEditor;
    private final FlowFXTab fxTab = new FlowFXTab(this, "TODOFLOW Title");
    private final FlowActions actions;
    /** Watcher - provides interface to BlueJ core. May be null (eg for README.txt file). */
    private final EditorWatcher watcher;
    
    private final boolean sourceIsCode = true /*TODOFLOW*/;           // true if current buffer is code
    private final List<Menu> fxMenus;
    private boolean compilationStarted;
    private boolean requeueForCompilation;
    private boolean compilationQueued;
    private boolean compilationQueuedExplicit;
    private CompileReason requeueReason;
    private CompileType requeueType;
    private final Info info;
    private final StatusLabel saveState;          // the status label
    private FlowErrorManager errorManager = new FlowErrorManager(this, enable -> {});
    private FXTabbedEditor fxTabbedEditor;
    private boolean mayHaveBreakpoints;
    private final BooleanProperty compiledProperty = new SimpleBooleanProperty(true);
    private final BooleanProperty viewingHTML = new SimpleBooleanProperty(false); // changing this alters the interface accordingly
    private ErrorDisplay errorDisplay;
    private final BitSet breakpoints = new BitSet();
    private int currentStepLineIndex = -1;
    private ComboBox<String> interfaceToggle;
    private final WebView htmlPane;
    private String filename;                // name of file or null
    private String docFilename;             // path to javadoc html file
    private Charset characterSet;           // character set of the file
    private String windowTitle;
    private final Properties resources = Config.moeUserProps;
    // Each element is size 2: beginning (incl) and end (excl)
    private final ArrayList<int[]> findResults = new ArrayList<>();
    /**
     * list of actions that are disabled in the readme text file
     */
    private static ArrayList<String> readMeActions;

    // find functionality
    private final FindPanel finder;
    // The most recent active FindNavigator.  Returns null if there has been no search,
    // or if the document has been modified since the last search.
    private final ObjectProperty<FindNavigator> currentSearchResult = new SimpleObjectProperty<>(null);
    private String lastSearchString = "";

    /** Used to obtain javadoc for arbitrary methods */
    private final JavadocResolver javadocResolver;


    // TODOFLOW handle the interface-only case
    public boolean containsSourceCode()
    {
        return true;
    }

    // Used during testing
    public void enableParser(boolean force)
    {
        javaSyntaxView.enableParser(force);
    }

    @Override
    public boolean marginClickedForLine(int lineIndex)
    {
        return toggleBreakpointForLine(lineIndex);
    }

    // Returns true if successfully flipped, false if not.
    private boolean toggleBreakpointForLine(int lineIndex)
    {
        if (watcher.breakpointToggleEvent(lineIndex + 1, !breakpoints.get(lineIndex)) == null)
        {
            breakpoints.flip(lineIndex);
            flowEditorPane.setLineMarginGraphics(lineIndex, calculateMarginDisplay(lineIndex));
            // We also reapply scopes:
            flowEditorPane.applyScopeBackgrounds(javaSyntaxView.getScopeBackgrounds());
            return true;
        }
        return false;
    }
    
    public void toggleBreakpoint()
    {
        toggleBreakpointForLine(flowEditorPane.getDocument().getLineFromPosition(flowEditorPane.getCaretPosition()));
    }

    @Override
    public Set<Integer> getBreakpointLines()
    {
        return breakpoints.stream().mapToObj(Integer::valueOf).collect(Collectors.toSet());
    }

    @Override
    public int getStepLine()
    {
        return currentStepLineIndex;
    }

    public static interface FetchTabbedEditor
    {
        FXTabbedEditor getFXTabbedEditor(boolean newWindow);
    }
    
    class UndoManager
    {
        private final DocumentUndoStack undoStack;
        private final BooleanProperty cannotRedo = new SimpleBooleanProperty(true);
        private final BooleanProperty cannotUndo = new SimpleBooleanProperty(true);

        public UndoManager(Document document)
        {
            undoStack = new DocumentUndoStack(document);
            undoStack.setStateListener(this::updateState);
        }

        private void updateState()
        {
            cannotUndo.setValue(undoStack.canUndoCount() == 0);
            cannotRedo.setValue(undoStack.canRedoCount() == 0);
        }

        public BooleanExpression cannotUndo()
        {
            return cannotUndo;
        }
        
        public BooleanExpression cannotRedo()
        {
            return cannotRedo;
        }
        
        public void undo()
        {
            int pos = undoStack.undo();
            if (pos >= 0)
            {
                flowEditorPane.positionCaret(pos);
            }
        }
        
        public void redo()
        {
            int pos = undoStack.redo();
            if (pos >= 0)
            {
                flowEditorPane.positionCaret(pos);
            }
        }

        public void forgetHistory()
        {
            undoStack.clear();
        }
        
        public void compoundEdit(FXPlatformRunnable action)
        {
            undoStack.compoundEdit(action);
        }
    }
    
    // package-visible:
    final UndoManager undoManager;

    // TODOFLOW remove this once all its callers are implemented.
    private final class UnimplementedException extends RuntimeException {}


    public FlowEditor(FetchTabbedEditor fetchTabbedEditor, String title, EditorWatcher editorWatcher, EntityResolver parentResolver, JavadocResolver javadocResolver)
    {
        this.javadocResolver = javadocResolver;
        this.windowTitle = title;
        this.flowEditorPane = new FlowEditorPane("", this);
        this.document = flowEditorPane.getDocument();
        this.javaSyntaxView = new JavaSyntaxView(flowEditorPane, this, parentResolver);
        this.flowEditorPane.setErrorQuery(errorManager);
        this.undoManager = new UndoManager(document);
        this.fetchTabbedEditor = fetchTabbedEditor;
        this.watcher = editorWatcher;
        this.info = new Info();
        this.saveState = new StatusLabel(Status.SAVED, this, errorManager);
        this.actions = FlowActions.getActions(this);
        this.htmlPane = new WebView();
        htmlPane.visibleProperty().bind(viewingHTML);
        setCenter(new StackPane(flowEditorPane, htmlPane));
        this.interfaceToggle = createInterfaceSelector();
        interfaceToggle.setDisable(!sourceIsCode);
        Region toolbar = createToolbar(interfaceToggle.heightProperty());
        setTop(new BorderPane(null, null, interfaceToggle, null, toolbar));
        flowEditorPane.addSelectionListener(this);
        flowEditorPane.addLineDisplayListener((fromIncl, toIncl) -> {
            for (int i = fromIncl; i <= toIncl; i++)
            {
                flowEditorPane.setLineMarginGraphics(i, calculateMarginDisplay(i));
            }
            flowEditorPane.markFindResults(findResults);
        });
        Nodes.addInputMap(this, InputMap.consume(MouseEvent.MOUSE_MOVED, this::mouseMoved));
        // create menubar and menus

        fxMenus = createMenus();
        // Must update keymap after making menu to remove shortcuts which are now handled by menu:
        actions.updateKeymap();
        //fxMenus.setAll(menus);

        BorderPane bottomArea = new BorderPane();
        JavaFXUtil.addStyleClass(bottomArea, "moe-bottom-bar");

        // create panel for info/status

        //area for new find functionality
        finder=new FindPanel(this);
        finder.setVisible(false);
        BorderPane commentsPanel = new BorderPane();
        commentsPanel.setCenter(info);
        commentsPanel.setRight(saveState);
        BorderPane.setAlignment(info, Pos.TOP_LEFT);
        BorderPane.setAlignment(saveState, Pos.CENTER_RIGHT);
        JavaFXUtil.addStyleClass(commentsPanel, "moe-bottom-status-row");
        commentsPanel.styleProperty().bind(PrefMgr.getEditorFontCSS(false));

        bottomArea.setBottom(commentsPanel);
        bottomArea.setTop(finder);
        setBottom(bottomArea);

        JavaFXUtil.addChangeListenerPlatform(PrefMgr.getEditorFontSize(), s -> {
            javaSyntaxView.fontSizeChanged();
            flowEditorPane.fontSizeChanged(s.doubleValue());
        });
    }

    /**
     * Gets the resource attribute of the MoeEditor object
     */
    private String getResource(String name)
    {
        return Config.getPropString(name, null, resources);
    }

    /**
     * Create the toolbar.
     *
     * @return The toolbar component, ready made.
     */
    private Region createToolbar(DoubleExpression buttonHeight)
    {
        TilePane tilePane = new TilePane(Orientation.HORIZONTAL,
            createToolbarButton("compile", buttonHeight),
            createToolbarButton("undo", buttonHeight),
            createToolbarButton("cut", buttonHeight),
            createToolbarButton("copy", buttonHeight),
            createToolbarButton("paste", buttonHeight),
            createToolbarButton("find", buttonHeight),
            createToolbarButton("close", buttonHeight)
        );
        tilePane.setPrefColumns(tilePane.getChildren().size());
        return JavaFXUtil.withStyleClass(tilePane, "moe-top-bar-buttons");
    }

    /**
     * Create a button on the toolbar.
     *  @param key  The internal key identifying the action and label
     *  @param buttonHeight The height of the buttons
     *
     */
    private ButtonBase createToolbarButton(String key, DoubleExpression buttonHeight)
    {
        final String label = Config.getString("editor." + key + LabelSuffix);
        ButtonBase button;

        String actionName = getResource(key + ActionSuffix);
        if (actionName == null) {
            actionName = key;
        }
        FlowAbstractAction action = actions.getActionByName(actionName);

        if (action != null) {
            button = action.makeButton();
            button.setText(label);
        }
        else {
            button = new Button("Unknown");
        }

        if (action == null) {
            button.setDisable(true);
            Debug.message("Moe: action not found for button " + label);
        }

        if (isNonReadmeAction(actionName) && !sourceIsCode){
            action.setEnabled(false);
        }

        // never get keyboard focus:
        button.setFocusTraversable(false);

        // Let it resize to width of other buttons:
        button.setMaxWidth(Double.MAX_VALUE);

        button.prefHeightProperty().bind(buttonHeight);
        button.setMaxHeight(Double.MAX_VALUE);

        button.getStyleClass().add("toolbar-" + key + "-button");

        return button;
    }

    /**
     * Check whether an action is not valid for the project "readme" (i.e. if it is only
     * valid for source files).
     *
     * @param actionName String representing the action name
     * @return true if it is an action that should be disabled while editing the readme file,
     *         or false otherwise
     */
    private static boolean isNonReadmeAction(String actionName)
    {
        List<String> flaggedActions = getNonReadmeActions();
        return flaggedActions.contains(actionName);
    }

    /**
     * Get a list of actions not applicable in the readme.txt file
     */
    private static ArrayList<String> getNonReadmeActions ()
    {
        if (readMeActions == null) {
            readMeActions = new ArrayList<>();
            readMeActions.add("compile");
            readMeActions.add("autoindent");
            readMeActions.add("insert-method");
            readMeActions.add("add-javadoc");
            readMeActions.add("toggle-interface-view");
        }
        return readMeActions;
    }


    /**
     * Create a combo box for the toolbar
     */
    private ComboBox<String> createInterfaceSelector()
    {
        final String interfaceString = Config.getString("editor.interfaceLabel");
        final String implementationString = Config.getString("editor.implementationLabel");
        String[] choiceStrings = {implementationString, interfaceString};
        ComboBox<String> interfaceToggle = new ComboBox<String>(FXCollections.observableArrayList(choiceStrings));

        interfaceToggle.setFocusTraversable(false);
        JavaFXUtil.addChangeListenerPlatform(interfaceToggle.valueProperty(), v -> {
            if (v.equals(interfaceString))
                switchToInterfaceView();
            else
                switchToSourceView();
        });

        return interfaceToggle;
    }

    private void mouseMoved(MouseEvent event)
    {
        flowEditorPane.getCaretPositionForMouseEvent(event).ifPresent(pos -> showErrorPopupForCaretPos(pos, true));
    }

    /**
     * Create the editor's menu bar.
     */
    private List<Menu> createMenus()
    {
        return List.of(
                createMenu("class", "save - print - close"),
                createMenu("edit", "undo redo - cut-to-clipboard copy-to-clipboard paste-from-clipboard - indent-block deindent-block comment-block uncomment-block autoindent - insert-method add-javadoc"),
                createMenu("tools", "find find-next find-next-backward replace go-to-line - compile toggle-breakpoint - toggle-interface-view"),
                createMenu("option", "increase-font decrease-font reset-font - key-bindings preferences")
        );
    }

    /**
     * Create a single menu for the editor's menu bar. The key for the menu (as
     * defined in moe.properties) is supplied.
     */
    private Menu createMenu(String titleKey, String itemList)
    {
        MenuItem item;
        String label;

        // get menu title
        Menu menu = new Menu(Config.getString("editor." + titleKey + LabelSuffix));

        // cut menu definition into separate items
        String[] itemKeys = itemList.split(" ");

        // create menu item for each item
        for (String itemKey : itemKeys) {
            if (itemKey.equals("-")) {
                menu.getItems().add(new SeparatorMenuItem());
            } else {
                FlowAbstractAction action = actions.getActionByName(itemKey);
                if (action == null) {
                    Debug.message("Moe: cannot find action " + itemKey);
                }
                // Forbid Preferences from being added to the Options menu when using
                // Mac screen menubar, as it is already exist in the Application menu.
                else if ( !( Config.isMacOS() &&
                        titleKey.toLowerCase().equals("option") &&
                        itemKey.toLowerCase().equals("preferences") )
                )
                {
                    item = action.makeMenuItem();
                    menu.getItems().add(item);
                    label = Config.getString("editor." + itemKey + LabelSuffix);
                    item.setText(label);
                }
            }
        }
        return menu;
    }


    /**
     * Notification (from the caret) that the caret position has moved.
     */
    public void selectionChanged(int caretPos, int anchorPos)
    {
        showErrorPopupForCaretPos(caretPos, false);

        actions.userAction();
        /*TODOFLOW
        if (matchBrackets) {
            doBracketMatch();
        }

        // Only send caret moved event if we are open; caret moves while loading
        // but we don't want to send an edit event because of that:
        if (oldCaretLineNumber != getLineNumberAt(caretPos) && isOpen())
        {
            recordEdit(true);

            cancelFreshState();

            // This is a workaround to overcome a bug in RichTextFX lib,
            // which in some cases used to cause the editor to not scroll
            // to follow cursor.
            // The re-layout enforcing is inside a runAfterCurrent to avoid
            // an IllegalArgumentException caused by state inconsistency.
            JavaFXUtil.runAfterCurrent(() -> {
                ensureCaretVisible();
                layout();
            });
        }
        oldCaretLineNumber = getLineNumberAt(caretPos);
        */
    }

    private void showErrorPopupForCaretPos(int caretPos, boolean mousePosition)
    {
        ErrorDetails err = caretPos == -1 ? null : errorManager.getErrorAtPosition(caretPos);
        if (err != null)
        {
            showErrorOverlay(err, caretPos);
        }
        else
        {
            // Only hide if it was a keyboard move,
            // or it was a mouse move but there is no error at the keyboard position
            if (errorDisplay != null && (!mousePosition || !errorDisplay.details.containsPosition(caretPos)))
            {
                showErrorOverlay(null, caretPos);
            }
        }
    }

    public void requestEditorFocus()
    {
        flowEditorPane.requestFocus();
    }

    /**
     * Notify this editor that it has gained focus, either because its tab was selected or it is the
     * currently selected tab in a window that gained focus, or it has lost focus for the opposite
     * reasons.
     *
     * @param visible   true if the editor has focus, false otherwise
     */
    public void notifyVisibleTab(boolean visible)
    {
        if (visible) {
            if (watcher != null) {
                watcher.recordSelected();
            }
            checkForChangeOnDisk();
        }
        else
        {
            // Hide any error tooltip:
            showErrorOverlay(null, 0);
        }
    }

    /**
     * Show the given error overlay, or hide the existing overlay
     * @param details If non-null, show the given error details.  If null, hide existing overlay.
     * @param displayPosition The character position at which to show.
     */
    private void showErrorOverlay(ErrorDetails details, int displayPosition)
    {
        //Debug.message("Showing error at " + displayPosition + ": " + details);
        if (details != null)
        {
            if (errorDisplay == null || errorDisplay.details != details)
            {
                // First, hide existing display:
                if (errorDisplay != null)
                {
                    ErrorDisplay old = errorDisplay;
                    old.popup.hide();
                }

                Bounds pos = null;
                boolean before = false;
                try
                {
                    // First, try to get the character after the caret:
                    pos = getSourcePane().getCaretBoundsOnScreen(displayPosition).orElse(null);

                    // That may be null if caret was at end of line, in which case try character before:
                    if (pos == null)
                    {
                        pos = getSourcePane().getCaretBoundsOnScreen(displayPosition - 1).orElse(null);
                        before = true;
                    }
                }
                catch (IllegalArgumentException e)
                {
                    // Can happen if display position is out of bounds (while pending updates get flushed)
                    // Will fall through to null case below...
                }

                // If that still doesn't work, give up (may not be on screen)
                if (pos == null)
                    return;
                int xpos = (int)(before ? pos.getMaxX() : pos.getMinX());
                int ypos = (int)(pos.getMinY() + (4*pos.getHeight()/3));
                errorDisplay = new ErrorDisplay(details);
                ErrorDisplay newDisplay = errorDisplay;

                newDisplay.createPopup();
                newDisplay.popup.setAnchorLocation(PopupWindow.AnchorLocation.WINDOW_TOP_LEFT);
                newDisplay.popup.setAnchorX(xpos);
                newDisplay.popup.setAnchorY(ypos);
                newDisplay.popup.show(getWindow());

                if (watcher != null) {
                    watcher.recordShowErrorMessage(details.identifier, Collections.emptyList());
                }
            }
        }
        else if (errorDisplay != null)
        {
            ErrorDisplay old = errorDisplay;
            old.popup.hide();
            errorDisplay = null;
        }
    }


    private void checkForChangeOnDisk()
    {
        // TODOFLOW
    }

    /**
     * Schedule compilation, if any edits have occurred since last compile.
     */
    @OnThread(Tag.FXPlatform)
    public void cancelFreshState()
    {
        if (sourceIsCode && saveState.isChanged())
        {
            scheduleCompilation(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
        }

        // Save will occur as part of compilation scheduled above.
    }

    public void setParent(FXTabbedEditor parent, boolean partOfMove)
    {
        if (watcher != null)
        {
            if (!partOfMove && parent != null)
            {
                watcher.recordOpen();
            }
            else if (!partOfMove && parent == null)
            {
                watcher.recordClose();
            }

            // If we are closing, force a compilation in case there are pending changes:
            if (parent == null && saveState.isChanged())
            {
                scheduleCompilation(CompileReason.MODIFIED, CompileType.ERROR_CHECK_ONLY);
            }
        }

        this.fxTabbedEditor = parent;
    }

    /**
     * Schedule an immediate compilation for the specified reason and of the specified type.
     * @param reason  The reason for compilation
     * @param ctype   The type of compilation
     */
    private void scheduleCompilation(CompileReason reason, CompileType ctype)
    {
        if (watcher != null)
        {
            // We can collapse multiple compiles, but we cannot collapse an explicit compilation
            // (resulting class files kept) into a non-explicit compilation (result discarded).
            if (! compilationQueued )
            {
                watcher.scheduleCompilation(true, reason, ctype);
                compilationQueued = true;
            }
            else if (compilationStarted ||
                    (ctype != CompileType.ERROR_CHECK_ONLY && ! compilationQueuedExplicit))
            {
                // Either: a previously queued compilation has already started
                // Or: we have queued an error-check-only compilation, but are being asked to
                //     schedule a full (explicit) compile which keeps the resulting classes.
                //
                // In either case, we need to queue a second compilation after the current one
                // finishes. We override any currently queued ERROR_CHECK_ONLY since explicit
                // compiles should take precedence:
                if (! requeueForCompilation || ctype == CompileType.ERROR_CHECK_ONLY)
                {
                    requeueForCompilation = true;
                    requeueReason = reason;
                    requeueType = ctype;
                }
            }
        }
    }


    public List<Menu> getFXMenu()
    {
        return fxMenus; 
    }

    @Override
    public boolean showFile(String filename, Charset charset, boolean compiled, String docFilename)
    {
        this.filename = filename;
        this.docFilename = docFilename;
        this.characterSet = charset;

        boolean loaded = false;

        File file = new File(filename);
        if (filename != null) {
            setupJavadocMangler();
            try {
                // check for crash file
                String crashFilename = filename + MoeEditor.CRASHFILE_SUFFIX;
                String backupFilename = crashFilename + "backup";
                File crashFile = new File(crashFilename);
                if (crashFile.exists()) {
                    File backupFile = new File(backupFilename);
                    backupFile.delete();
                    crashFile.renameTo(backupFile);
                    DialogManager.showMessageFX(fxTabbedEditor.getWindow(), "editor-crashed");
                }

                document.replaceText(0, document.getLength(), Files.readString(file.toPath(), charset));
                setLastModified(file.lastModified());
                undoManager.forgetHistory();

                javaSyntaxView.enableParser(false);
                loaded = true;
            }
            catch (IOException ex) {
                // TODO display user-visible error
                Debug.reportError("Couldn't open file", ex);
            }
        }
        else {
            if (docFilename != null) {
                if (new File(docFilename).exists()) {
                    showInterface(true);
                    loaded = true;
                    interfaceToggle.setDisable(true);
                }
            }
        }

        if (!loaded) {
            // should exist, but didn't
            return false;
        }

        setCompileStatus(compiled);

        return true;
    }

    /**
     * Sets up the processor for loaded Javdoc.  Currently this inserts a link
     * next to a method name to allow you to jump back to the BlueJ source, if
     * there is source code available.
     */
    private void setupJavadocMangler()
    {
        JavaFXUtil.addChangeListenerPlatform(htmlPane.getEngine().documentProperty(), doc -> {
            if (doc != null)
            {
                /* Javadoc looks like this:
                <a id="sampleMethod(java.lang.String)">
                <!--   -->
                </a>
                <ul>
                <li>
                <h4>sampleMethod</h4>
                 */

                // First find the anchor.  Ignore anchors with ids that do not end in a closing bracket (they are not methods):
                NodeList anchors = doc.getElementsByTagName("a");
                for (int i = 0; i < anchors.getLength(); i++)
                {
                    org.w3c.dom.Node anchorItem = anchors.item(i);
                    org.w3c.dom.Node anchorName = anchorItem.getAttributes().getNamedItem("id");
                    if (anchorName != null && anchorName.getNodeValue() != null && anchorName.getNodeValue().endsWith(")"))
                    {
                        // Then find the ul child, then the li child of that, then the h4 child of that:
                        org.w3c.dom.Node ulNode = findHTMLNode(anchorItem, org.w3c.dom.Node::getNextSibling, n -> "ul".equals(n.getLocalName()));
                        if (ulNode == null)
                            continue;
                        org.w3c.dom.Node liNode = findHTMLNode(ulNode.getFirstChild(), org.w3c.dom.Node::getNextSibling, n -> "li".equals(n.getLocalName()));
                        if (liNode == null)
                            continue;
                        org.w3c.dom.Node headerNode = findHTMLNode(liNode.getFirstChild(), org.w3c.dom.Node::getNextSibling, n -> "h4".equals(n.getLocalName()));
                        if (headerNode != null)
                        {
                            // Make a link, and set a listener for it:
                            org.w3c.dom.Element newLink = doc.createElement("a");
                            newLink.setAttribute("style", "padding-left: 2em;cursor:pointer;");
                            newLink.insertBefore(doc.createTextNode("[Show source in BlueJ]"), null);
                            headerNode.insertBefore(newLink, null);

                            ((EventTarget) newLink).addEventListener("click", e ->
                            {
                                String[] tokens = anchorName.getNodeValue().split("[(,)]");
                                List<String> paramTypes = new ArrayList<>();
                                for (int t = 1; t < tokens.length; t++)
                                {
                                    paramTypes.add(tokens[t]);
                                }
                                focusMethod(tokens[0].equals("<init>") ? windowTitle : tokens[0], paramTypes);
                            }, false);
                        }
                    }
                }
            }
        });
    }

    /**
     * Traverses the document using the given traversal operation (next parameter),
     * until the stopWhen test returns true, beginning at the start node.  The start
     * node is not tested.  Once the traversal returns null, this method returns null.
     */
    private static org.w3c.dom.Node findHTMLNode(org.w3c.dom.Node start, UnaryOperator<org.w3c.dom.Node> next, Predicate<org.w3c.dom.Node> stopWhen)
    {
        org.w3c.dom.Node n = start;
        while (n != null)
        {
            n = next.apply(n);
            if (n != null && stopWhen.test(n))
                return n;
        }
        return null;
    }


    @Override
    public void clear()
    {
        document.replaceText(0, document.getLength(), "");
    }

    @Override
    public void insertText(String text, boolean caretBack)
    {
        int startPos = flowEditorPane.getSelectionStart();
        flowEditorPane.replaceSelection(text);
        if (caretBack)
        {
            flowEditorPane.positionCaret(startPos);
        }
    }

    @Override
    public void setSelection(SourceLocation begin, SourceLocation end)
    {
        flowEditorPane.select(document.getPosition(begin), document.getPosition(end));
    }

    @Override
    public SourceLocation getCaretLocation()
    {
        return document.makeSourceLocation(flowEditorPane.getCaretPosition());
    }

    @Override
    public void setCaretLocation(SourceLocation location)
    {
        flowEditorPane.positionCaret(document.getPosition(location));
    }

    @Override
    public SourceLocation getSelectionBegin()
    {
        return document.makeSourceLocation(flowEditorPane.getSelectionStart());
    }

    @Override
    public SourceLocation getSelectionEnd()
    {
        return document.makeSourceLocation(flowEditorPane.getSelectionEnd());
    }

    @Override
    public String getText(SourceLocation begin, SourceLocation end)
    {
        return document.getContent(document.getPosition(begin), document.getPosition(end)).toString();
    }

    @Override
    public void setText(SourceLocation begin, SourceLocation end, String newText)
    {
        document.replaceText(document.getPosition(begin), document.getPosition(end), newText);
    }

    @Override
    public SourceLocation getLineColumnFromOffset(int offset)
    {
        return document.makeSourceLocation(offset);
    }

    @Override
    public int getOffsetFromLineColumn(SourceLocation location)
    {
        return document.getPosition(location);
    }

    @Override
    public int getLineLength(int line)
    {
        return document.getLineLength(line);
    }

    @Override
    public int numberOfLines()
    {
        return document.getLineCount();
    }

    @Override
    public int getTextLength()
    {
        return document.getLength();
    }

    @Override
    public ParsedCUNode getParsedNode()
    {
        return javaSyntaxView.getParser();
    }

    @Override
    public ReparseableDocument getSourceDocument()
    {
        return javaSyntaxView;
    }

    @Override
    public void reloadFile()
    {
        throw new UnimplementedException();
    }

    @Override
    public void setEditorVisible(boolean vis, boolean openInNewWindow)
    {
        // TODOFLOW put pack the commented parts of this method.
        
        if (vis)
        {
            //checkBracketStatus();

            /*
            if (sourceIsCode && !compiledProperty.get() && sourceDocument.notYetShown)
            {
                // Schedule a compilation so we can find and display any errors:
                scheduleCompilation(CompileReason.LOADED, CompileType.ERROR_CHECK_ONLY);
            }
            */

            // Make sure caret is visible after open:
            //sourcePane.requestFollowCaret();
            //sourcePane.layout();
        }
        FXTabbedEditor fxTabbedEditor = fetchTabbedEditor.getFXTabbedEditor(false);
        /*
        if (fxTabbedEditor == null)
        {
            if (openInNewWindow)
            {
                fxTabbedEditor = defaultFXTabbedEditor.get().getProject().createNewFXTabbedEditor();
            }
            else
            {
                fxTabbedEditor = defaultFXTabbedEditor.get();
            }
        }
        else
        {
            // Checks if the editor of the selected target is already opened in a tab inside another window,
            // then do not open it in a new window unless the tab is closed.
            if (openInNewWindow && !fxTabbedEditor.containsTab(fxTab) )
            {
                fxTabbedEditor = defaultFXTabbedEditor.get().getProject().createNewFXTabbedEditor();
            }
        }
        */

        if (vis)
        {
            fxTabbedEditor.addTab(fxTab, vis, true);
        }
        fxTabbedEditor.setWindowVisible(vis, fxTab);
        if (vis)
        {
            fxTabbedEditor.bringToFront(fxTab);
            /*
            if (callbackOnOpen != null)
            {
                callbackOnOpen.run();
            }
            */

            // Allow recalculating the scopes:
            //sourceDocument.notYetShown = false;

            // Make sure caret is visible after open:
            //sourcePane.requestFollowCaret();
            //sourcePane.layout();
        }
    }

    @Override
    public boolean isOpen()
    {
        return fxTabbedEditor != null && fxTabbedEditor.isWindowVisible();
    }

    @Override
    public void save() throws IOException
    {
        // TODOFLOW don't want to save until we stop throwing exceptions everywhere...
    }

    /**
     * The editor wants to close. Do this through the EditorManager so that we
     * can be removed from the list of open editors.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void close()
    {
        cancelFreshState();

        try
        {
            save();
        }
        catch (IOException ioe)
        {
            DialogManager.showErrorTextFX(getWindow(), "Error saving source code");
        }

        doClose();
    }

    /**
     * The editor has been closed. Hide the editor window now.
     */
    public void doClose()
    {
        setEditorVisible(false, false);
        if (watcher != null)
        {
            watcher.closeEvent(this);
        }
    }
    
    @Override
    public void refresh()
    {
        throw new UnimplementedException();
    }

    @Override
    public void displayMessage(String message, int lineNumber, int column)
    {
        throw new UnimplementedException();
    }

    @Override
    public boolean displayDiagnostic(Diagnostic diagnostic, int errorIndex, CompileType compileType)
    {
        if (compileType.showEditorOnError())
        {
            setEditorVisible(true, false);
        }

        switchToSourceView();

        if (diagnostic.getStartLine() >= 0 && diagnostic.getStartLine() < document.getLineCount())
        {
            // Limit diagnostic display to a single line.
            int startPos = document.getPosition(new SourceLocation((int)diagnostic.getStartLine(), (int) diagnostic.getStartColumn()));
            int endPos;
            if (diagnostic.getStartLine() != diagnostic.getEndLine())
            {
                endPos = document.getLineEnd((int)diagnostic.getStartLine());
            }
            else
                {
                endPos = document.getPosition(new SourceLocation((int)diagnostic.getStartLine(), (int) diagnostic.getEndColumn()));
            }

            // highlight the error and the line on which it occurs
            // If error is zero-width, make it one character wide:
            if (endPos == startPos)
            {
                // By default, extend one char right, unless that would encompass a newline:
                if (endPos < getTextLength() - 1 && !document.getContent(endPos, endPos + 1).equals("\n"))
                {
                    endPos += 1;
                }
                else if (startPos > 0 && !document.getContent(startPos - 1, startPos).equals("\n"))
                {
                    startPos -= 1;
                }
            }
            errorManager.addErrorHighlight(startPos, endPos, diagnostic.getMessage(), diagnostic.getIdentifier());
        }

        return true;
    }

    private void switchToSourceView()
    {
        if (!viewingHTML.get()) {
            return;
        }
        resetMenuToolbar(true);
        viewingHTML.set(false);
        interfaceToggle.getSelectionModel().selectFirst();
        watcher.showingInterface(false);
        clearMessage();
        flowEditorPane.requestFocus();
    }

    /**
     * Switch on the javadoc interface view (it it isn't showing already). If
     * necessary, generate it first.
     */
    private void switchToInterfaceView()
    {
        if (viewingHTML.get()) {
            return;
        }
        resetMenuToolbar(false);
        //NAVIFX
        //dividerPanel.beginTemporaryHide();
        try {
            save();
            info.message(Config.getString("editor.info.loadingDoc"));
            boolean generateDoc = ! docUpToDate();

            if (generateDoc)
            {
                // interface needs to be re-generated
                info.message(Config.getString("editor.info.generatingDoc"));
                BlueJEvent.addListener(this);
                if (watcher != null) {
                    watcher.generateDoc();
                }
            }
            else
            {
                // Only bother to refresh if we're not about to generate
                // (if we do generate, we will refresh once completed)
                refreshHtmlDisplay();
            }

            interfaceToggle.getSelectionModel().selectLast();
            viewingHTML.set(true);
            watcher.showingInterface(true);
        }
        catch (IOException ioe) {
            // Could display a dialog here. However, the error message
            // (from save() call) will already be displayed in the editor
            // status bar.
        }
    }

    /**
     * A BlueJEvent was raised. Check whether it is one that we're interested in.
     */
    @Override
    public void blueJEvent(int eventId, Object arg, Project prj)
    {
        switch(eventId) {
            case BlueJEvent.DOCU_GENERATED :
                BlueJEvent.removeListener(this);
                refreshHtmlDisplay();
                break;
            case BlueJEvent.DOCU_ABORTED :
                BlueJEvent.removeListener(this);
                info.message (Config.getString("editor.info.docAborted"));
                break;
        }
    }

    /**
     * Check whether javadoc file is up to date.
     *
     * @return True is the currently existing documentation is up-to-date.
     */
    private boolean docUpToDate()
    {
        if (filename == null) {
            return true;
        }
        try {
            File src = new File(filename);
            File doc = new File(docFilename);

            if (!doc.exists() || (src.exists() && (src.lastModified() > doc.lastModified()))) {
                return false;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Refresh the HTML display.
     */
    private void refreshHtmlDisplay()
    {
        try {
            File urlFile = new File(docFilename);

            // Check if docs file exists before attempting to load it.  There is a JDK behaviour where
            // if you load a non-existent file in a webview, all future attempts to reload the page will
            // fail even once the file exists.  So the file must be present before we attempt to load.
            //
            // There is an seeming timing hazard here where we could be called just at the moment the file
            // is created but before it is finished.  In fact, we are called in one of two cases:
            //  - One is where the interface is being switched to.  This method is called only if the
            //    docs won't be regenerated, so no race hazard there.
            //  - The other case is when the doc generation has definitely finished, so again we won't be in a
            //    race with the generation:
            if (!urlFile.exists())
            {
                return;
            }

            URL myURL = urlFile.toURI().toURL();

            // We must use reload here if applicable, as that forces reloading the stylesheet.css asset
            // (which may have changed if we initially loaded docs from a version older than 4.1.0,
            // but have now regenerated them).  We compare URLs, not String versions, because you may
            // get difference between e.g. file:/Users... and file:///Users... which URL comparison
            // properly takes care of:
            String location = htmlPane.getEngine().getLocation();
            if (Objects.equals(location == null ? null : new URL(location), myURL))
            {
                htmlPane.getEngine().reload();
            }
            else
            {
                htmlPane.getEngine().load(myURL.toString());
            }

            info.message(Config.getString("editor.info.docLoaded"));
        }
        catch (IOException exc) {
            info.message (Config.getString("editor.info.docDisappeared"), docFilename);
            Debug.reportError("loading class interface failed: " + exc);
        }
    }



    /**
     * Clear the message in the info area.
     */
    public void clearMessage()
    {
        info.clear();
    }

    /**
     * This method resets the value of the menu and toolbar according to the view
     *
     * @param sourceView true if called from source view setup; false from documentation view setup
     */
    private void resetMenuToolbar(boolean sourceView)
    {
        if (sourceView)
            actions.makeAllAvailable();
        else
            actions.makeAllUnavailableExcept("close", "toggle-interface-view");
    }


    @Override
    public boolean setStepMark(int lineNumber, String message, boolean isBreak, DebuggerThread thread)
    {
        switchToSourceView();

        if (isBreak)
        {
            removeStepMark();
            currentStepLineIndex = lineNumber - 1;
            flowEditorPane.setLineMarginGraphics(currentStepLineIndex, calculateMarginDisplay(currentStepLineIndex));
            // We also reapply scopes:
            flowEditorPane.applyScopeBackgrounds(javaSyntaxView.getScopeBackgrounds());
        }

        // Scroll to the line:
        flowEditorPane.positionCaret(getOffsetFromLineColumn(new SourceLocation(lineNumber, 1)));

        // display the message

        if (message != null) {
            info.messageImportant(message);
        }

        return false;
    }

    private EnumSet<MarginDisplay> calculateMarginDisplay(int lineIndex)
    {
        EnumSet<MarginDisplay> r = EnumSet.noneOf(MarginDisplay.class);
        if (PrefMgr.getFlag(PrefMgr.LINENUMBERS))
            r.add(MarginDisplay.LINE_NUMBER);
        if (breakpoints.get(lineIndex))
            r.add(MarginDisplay.BREAKPOINT);
        if (lineIndex == currentStepLineIndex)
            r.add(MarginDisplay.STEP_MARK);
        return r;
    }

    @Override
    public void writeMessage(String msg)
    {
        info.message(msg);
    }

    /**
     * Remove the step mark (the mark that shows the current line when
     * single-stepping through code). If it is not currently displayed, do
     * nothing.
     */
    @Override
    @OnThread(Tag.FXPlatform)
    public void removeStepMark()
    {
        if (currentStepLineIndex != -1)
        {
            int oldStepLine = currentStepLineIndex;
            currentStepLineIndex = -1;
            flowEditorPane.setLineMarginGraphics(oldStepLine, calculateMarginDisplay(oldStepLine));
            // We also reapply scopes:
            flowEditorPane.applyScopeBackgrounds(javaSyntaxView.getScopeBackgrounds());
        }
    }

    /**
     * Change class name.
     *
     * @param title  new window title
     * @param filename  new file name
     */
    @Override
    public void changeName(String title, String filename, String javaFilename, String docFilename)
    {
        this.filename = filename;
        this.docFilename = docFilename;
        windowTitle = title;
        setWindowTitle();
    }

    /**
     * Set the window title to show the defined title, or else the file name.
     */
    private void setWindowTitle()
    {
        String title = windowTitle;

        if (title == null) {
            if (filename == null) {
                title = "<no name>";
            }
            else {
                title = filename;
            }
        }
        fxTab.setWindowTitle(title);
    }

    @Override
    public void setCompiled(boolean compiled)
    {
        setCompileStatus(compiled);
        if (compiled) {
            errorManager.removeAllErrorHighlights();
        }
    }

    /**
     * Toggle the editor's 'compiled' status. This affects display (left-hand margin colour)
     * and whether breakpoints can be set.
     */
    private void setCompileStatus(boolean compiled)
    {
        actions.getActionByName("toggle-breakpoint").setEnabled(compiled && viewingCode());
        compiledProperty.set(compiled);
    }

    /**
     * Return a boolean representing whether in source editing view
     */
    private boolean viewingCode()
    {
        return sourceIsCode && (!viewingHTML.get());
    }

    @Override
    public boolean compileStarted(int compilationSequence)
    {
        compilationStarted = true;
        errorManager.removeAllErrorHighlights();
        return false;
    }

    @Override
    public void compileFinished(boolean successful, boolean classesKept)
    {
        compilationStarted = false;
        if (requeueForCompilation) {
            requeueForCompilation = false;
            if (classesKept)
            {
                // If the classes were kept, that means the compilation is valid and the source
                // hasn't changed since. There is then no need for another recompile, even if
                // we thought we needed one before.
                compilationQueued = false;
            }
            else
            {
                compilationQueuedExplicit = (requeueType != CompileType.ERROR_CHECK_ONLY);
                watcher.scheduleCompilation(true, requeueReason, requeueType);
            }
        }
        else {
            compilationQueued = false;
        }

        if (classesKept)
        {
            // Compilation requested via the editor interface has completed
            if (successful)
            {
                info.messageImportant(Config.getString("editor.info.compiled"));
            }
            else
            {
                info.messageImportant(getCompileErrorLabel());
            }
        }
    }

    private String getCompileErrorLabel()
    {
        return Config.getString("editor.info.compileError").replace("$", actions.getKeyStrokesForAction("compile").stream().map(KeyCodeCombination::getDisplayText).collect(Collectors.joining(" " + Config.getString("or") + " ")));
    }

    @Override
    public void removeBreakpoints()
    {
        throw new UnimplementedException();
    }

    @Override
    public void reInitBreakpoints()
    {
        if (mayHaveBreakpoints) {
            mayHaveBreakpoints = false;
            for (int i = 1; i <= numberOfLines(); i++) {
                if (lineHasBreakpoint(i)) {
                    if (watcher != null)
                        watcher.breakpointToggleEvent(i, true);
                    mayHaveBreakpoints = true;
                }
            }
        }
    }

    private boolean lineHasBreakpoint(int i)
    {
        return document.hasLineAttribute(i, ParagraphAttribute.BREAKPOINT);
    }

    @Override
    public boolean isModified()
    {
        //TODOFLOW need to implement saving first
        return false;
    }

    @Override
    public FXRunnable printTo(PrinterJob printerJob, PrintSize printSize, boolean printLineNumbers, boolean printBackground)
    {
        throw new UnimplementedException();
    }

    @Override
    public void setReadOnly(boolean readOnly)
    {
        throw new UnimplementedException();
    }

    @Override
    public boolean isReadOnly()
    {
        throw new UnimplementedException();
    }

    @Override
    public void showInterface(boolean interfaceStatus)
    {
        interfaceToggle.getSelectionModel().select(interfaceStatus ? 1 : 0);
    }

    @Override
    public Object getProperty(String propertyKey)
    {
        throw new UnimplementedException();
    }

    @Override
    public void setProperty(String propertyKey, Object value)
    {
        throw new UnimplementedException();
    }

    @Override
    public TextEditor assumeText()
    {
        return this;
    }

    @Override
    public FrameEditor assumeFrame()
    {
        return null;
    }

    @Override
    public void insertAppendMethod(NormalMethodElement method, FXPlatformConsumer<Boolean> after)
    {
        throw new UnimplementedException();
    }

    @Override
    public void insertMethodCallInConstructor(String className, CallElement methodCall, FXPlatformConsumer<Boolean> after)
    {
        throw new UnimplementedException();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void focusMethod(String methodName, List<String> paramTypes)
    {
        focusMethod(methodName, paramTypes, new NodeAndPosition<ParsedNode>(getParsedNode(), 0, 0), 0);
    }

    private boolean focusMethod(String methodName, List<String> paramTypes, NodeAndPosition<ParsedNode> tree, int offset)
    {
        // This is a fairly naive traversal, which may find methods in inner classes rather
        // than one in the outer class; but then we don't actually pass which class we are interested in,
        // so it may be right to pick the one in the inner class anyway:
        if (tree.getNode().getNodeType() == ParsedNode.NODETYPE_METHODDEF && methodName.equals(tree.getNode().getName())
            && paramsMatch(tree.getNode(), paramTypes))
        {
            switchToSourceView();
            flowEditorPane.positionCaret(offset);
            return true;
        }
        else
        {
            for (NodeAndPosition<ParsedNode> child : (Iterable<NodeAndPosition<ParsedNode>>)(() -> tree.getNode().getChildren(0)))
            {
                if (focusMethod(methodName, paramTypes, child, offset + child.getPosition()))
                    return true;
            }
        }
        return false;
    }

    /**
     * Checks if the parameter types match the parameters of the given node,
     * if it is a method node.  (If not a method node, false is returned)
     * @param node The node which should be a MethodNode.
     * @param paramTypes Parameter types.  null matches anything.
     * @return
     */
    private boolean paramsMatch(ParsedNode node, List<String> paramTypes)
    {
        if (paramTypes == null)
            return true;
        if (node instanceof MethodNode)
        {
            MethodNode methodNode = (MethodNode)node;
            if (methodNode.getParamTypes().size() != paramTypes.size())
                return false;
            for (int i = 0; i < paramTypes.size(); i++)
            {
                JavaEntity paramType = methodNode.getParamTypes().get(i);
                if (!paramType.getName().equals(paramTypes.get(i)))
                    return false;
            }
            // If we get here, all paramTypes must have matched:
            return true;
        }
        return false;
    }

    @Override
    public void setExtendsClass(String className, ClassInfo classInfo)
    {
        throw new UnimplementedException();
    }

    @Override
    public void removeExtendsClass(ClassInfo classInfo)
    {
        throw new UnimplementedException();
    }

    @Override
    public void addImplements(String interfaceName, ClassInfo classInfo)
    {
        throw new UnimplementedException();
    }

    @Override
    public void addExtendsInterface(String interfaceName, ClassInfo classInfo)
    {
        throw new UnimplementedException();
    }

    @Override
    public void removeExtendsOrImplementsInterface(String interfaceName, ClassInfo classInfo)
    {
        throw new UnimplementedException();
    }

    @Override
    public void removeImports(List<String> importTargets)
    {
        throw new UnimplementedException();
    }

    @Override
    public void setHeaderImage(Image image)
    {
        throw new UnimplementedException();
    }

    /**
     * Implementation of "toggle-interface-view" user function. The menu has
     * already been changed - now see what it is and do it.
     */
    public void toggleInterface()
    {
        if (viewingHTML.get())
            switchToSourceView();
        else
            switchToInterfaceView();
    }
    
    @Override
    public void setLastModified(long millisSinceEpoch)
    {
        // TODOFLOW
    }

    public FlowEditorPane getSourcePane()
    {
        return flowEditorPane;
    }

    public void compileOrShowNextError()
    {
        if (watcher != null) {
            if (saveState.isChanged() || !errorManager.hasErrorHighlights())
            {
                if (! saveState.isChanged())
                {
                    if (PrefMgr.getFlag(PrefMgr.ACCESSIBILITY_SUPPORT))
                    {
                        // Pop up in a dialog:
                        DialogManager.showTextWithCopyButtonFX(getWindow(), Config.getString("pkgmgr.accessibility.compileDone"), "BlueJ");
                    }
                }
                scheduleCompilation(CompileReason.USER, CompileType.EXPLICIT_USER_COMPILE);
            }
            else
            {
                ErrorDetails err = errorManager.getNextErrorPos(flowEditorPane.getCaretPosition());
                if (err != null)
                {
                    flowEditorPane.positionCaret(err.startPos);

                    if (PrefMgr.getFlag(PrefMgr.ACCESSIBILITY_SUPPORT))
                    {
                        // Pop up in a dialog:
                        DialogManager.showTextWithCopyButtonFX(getWindow(), err.message, "BlueJ");
                    }
                }
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    public javafx.stage.Window getWindow()
    {
        return fxTabbedEditor.getWindow();
    }

    /**
     * Shows the preferences pane, and makes the given pane index (i.e. given tab index
     * in the preferences) the active showing tab.  0 is general, 1 is key bindings, and so on.
     * If in doubt, pass 0.
     */
    public void showPreferences(int paneIndex)
    {
        watcher.showPreferences(paneIndex);
    }

    /**
     * Transfers caret to user specified line number location.
     */
    public void goToLine()
    {
        final int numberOfLines = numberOfLines();
        GoToLineDialog goToLineDialog = new GoToLineDialog(fxTabbedEditor.getWindow());
        goToLineDialog.setRangeMax(numberOfLines);
        Optional<Integer> o = goToLineDialog.showAndWait();
        o.ifPresent(n -> {
            setSelection(new SourceLocation(n , 1), new SourceLocation(n, 1));
        });
    }
    
    // package visible
    void updateHeaderHasErrors(boolean hasErrors)
    {
        fxTab.setErrorStatus(hasErrors);
    }

    /**
     * Do a find forwards or backwards, and highlight all cases.
     *
     * The case after the cursor (if backwards is false) or before it (if
     * backwards is true) is given a special highlight.
     *
     * Returns null if nothing was found.  If something was found, gives
     * you back a class you can use to cycle between search results.  It
     * becomes invalid next time doFind is called, or if the document is modified.
     */
    FindNavigator doFind(String searchFor, boolean ignoreCase)
    {
        removeSearchHighlights();
        // Deselect existing selection in case it's no longer a valid search result.
        // Move back to beginning of selection:
        flowEditorPane.positionCaret(flowEditorPane.getSelectionStart());
        lastSearchString = searchFor;
        String content = document.getFullContent();

        int curPosition = 0;
        boolean finished = false;

        List<Integer> foundStarts = new ArrayList<>();

        while (!finished)
        {
            int foundPos = FindNavigator.findSubstring(content, searchFor, ignoreCase, false, curPosition);
            if (foundPos != -1)
            {
                foundStarts.add(foundPos);
                curPosition = foundPos + searchFor.length();
            }
            else
            {
                finished = true;
            }
        }
        currentSearchResult.set(foundStarts.isEmpty() ? null : new FindNavigator()
        {
            @Override
            public void highlightAll()
            {
                findResults.clear();
                findResults.addAll(Utility.mapList(foundStarts, foundPos -> new int[] {foundPos, foundPos + searchFor.length()}));
                flowEditorPane.markFindResults(findResults);
            }

            @Override
            public FindNavigator replaceCurrent(String replacement)
            {
                if (!flowEditorPane.getSelectedText().equals(searchFor))
                {
                    selectNext(true);
                }
                int pos = flowEditorPane.getSelectionStart();
                document.replaceText(pos, searchFor.length(), replacement);
                flowEditorPane.positionCaret(pos + searchFor.length());
                return doFind(searchFor, ignoreCase);
            }

            public void replaceAll(String replacement)
            {
                // Sort all the found positions in descending order, so we can replace them
                // in order without affecting the later positions in the list (earlier in file):
                foundStarts.stream().sorted(Comparator.reverseOrder()).forEach(pos ->
                    document.replaceText(pos, searchFor.length(), replacement)
                );
            }

            @Override
            public void selectNext(boolean canBeAtCurrentPos)
            {
                if (validProperty().get())
                {
                    int selStart = flowEditorPane.getSelectionStart();
                    int position = foundStarts.stream()
                        .filter(pos -> pos > selStart || (canBeAtCurrentPos && pos == selStart))
                        .findFirst()
                        .orElse(foundStarts.get(0));
                    select(position);
                }
            }

            private void select(int position)
            {
                flowEditorPane.select(position, position + searchFor.length());
            }

            @Override
            public void selectPrev()
            {
                if (validProperty().get())
                {
                    int selStart = flowEditorPane.getSelectionStart();
                    int position = Utility.streamReversed(foundStarts)
                        .filter(pos -> pos < selStart)
                        .findFirst()
                        .orElse(foundStarts.get(foundStarts.size() - 1));
                    select(position);
                }
            }

            @Override
            public BooleanExpression validProperty()
            {
                return currentSearchResult.isEqualTo(this);
            }
        });
        return currentSearchResult.get();
    }

    /**
     * Removes the selected highlights (in both the source/doc pane) 
     * Note: the other highlights such as the brackets etc remain
     */
    public void removeSearchHighlights()
    {
        flowEditorPane.markFindResults(List.of());
    }

    /**
     * Sets the find panel to be visible and if there is a selection/or previous search 
     * it starts a automatic find of what was selected in the text/or previous search. If 
     * it is the source pane then the replace button is enabled; if it is the interface pane 
     * then the replace button and replace panel are set to disabled and invisible
     */
    public void initFindPanel()
    {
        finder.displayFindPanel(flowEditorPane.getSelectedText());
    }

    /**
     * Implementation of "find-next" user function.
     */
    public void findNext(boolean backwards)
    {
        if (currentSearchResult.get() == null || !currentSearchResult.get().validProperty().get())
        {
            String search = flowEditorPane.getSelectedText();
            if (search.isEmpty())
                search = lastSearchString;
            doFind(search, true);
        }
        if (currentSearchResult.get() != null)
        {
            if (backwards)
                currentSearchResult.get().selectPrev();
            else
                currentSearchResult.get().selectNext(false);
        }
    }

    /**
     * Opens or close the replace panel (and if opening it, set the focus into
     * the find field).
     */
    protected void showReplacePanel()
    {
        if (!finder.isVisible()) {
            finder.setVisible(true);
        }
        finder.requestFindfieldFocus();
        finder.setReplaceEnabled(true);
    }

    /**
     * Populates the find field and requests focus
     */
    public void setFindTextfield(String text)
    {
        finder.populateFindTextfield(text);
    }


    private static class ErrorDisplay
    {
        @OnThread(Tag.Swing)
        private final ErrorDetails details;
        private PopupControl popup;

        public ErrorDisplay(ErrorDetails details)
        {
            this.details = details;
        }

        @OnThread(Tag.FXPlatform)
        public void createPopup()
        {
            this.popup = new PopupControl();

            Text text = new Text(ParserMessageHandler.getMessageForCode(details.message));
            TextFlow flow = new TextFlow(text);
            flow.setMaxWidth(600.0);
            JavaFXUtil.addStyleClass(text, "java-error");
            text.styleProperty().bind(PrefMgr.getEditorFontCSS(true));
            Pane p = new BorderPane(flow);
            this.popup.setSkin(new Skin<Skinnable>()
            {
                @Override
                @OnThread(Tag.FX)
                public Skinnable getSkinnable()
                {
                    return popup;
                }

                @Override
                @OnThread(Tag.FX)
                public Node getNode()
                {
                    return p;
                }

                @Override
                @OnThread(Tag.FX)
                public void dispose()
                {

                }
            });

            p.getStyleClass().add("java-error-popup");
            Config.addPopupStylesheets(p);
            //org.scenicview.ScenicView.show(this.popup.getScene());
        }
    }
}
