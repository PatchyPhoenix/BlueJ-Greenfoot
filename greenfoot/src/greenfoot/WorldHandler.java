package greenfoot;

import greenfoot.event.WorldCreationListener;
import greenfoot.gui.DragGlassPane;
import greenfoot.gui.DragListener;
import greenfoot.gui.DropTarget;
import greenfoot.gui.WorldCanvas;
import greenfoot.gui.classbrowser.ClassView;
import greenfoot.gui.classbrowser.SelectionManager;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import rmiextension.ObjectTracker;
import rmiextension.wrappers.RObject;

/**
 * The worldhandler handles the connection between the GreenfootWorld and the
 * WorldCanvas.
 * 
 * @author Poul Henriksen
 * @version $Id: WorldHandler.java 3166 2004-11-25 02:36:34Z davmac $
 */
public class WorldHandler
    implements MouseListener, KeyListener, DropTarget, DragListener
{
    private transient final static Logger logger = Logger.getLogger("greenfoot");
    private GreenfootWorld world;
    private WorldCanvas worldCanvas;
    private SelectionManager classSelectionManager;
    private JLabel worldTitle = new JLabel();
    private int delay;
    private boolean isQuickAddActive;
    
    // where did the the drag/drop operation begin?
    private int dragBeginX;
    private int dragBeginY;
    
    /**
     * Whether the object was dropped, or more specifically, whether it
     * does not need to be replaced if the drop is cancelled.
     */
    private boolean objectDropped = true; // true if the object was dropped
    
    /**
     * Creates a new worldHandler and sets up the connection between worldCanvas
     * and world
     * 
     * @param worldCanvas
     * @param world
     */
    public WorldHandler(WorldCanvas worldCanvas, GreenfootWorld world)
    {

        this.worldCanvas = worldCanvas;
        worldCanvas.addMouseListener(this);
        worldCanvas.addKeyListener(this);
        worldCanvas.setDropTargetListener(this);
        try {
            Greenfoot.getInstance().addInvocationListener(new WorldCreationListener(this));
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
        installNewWorld(world);
        DragGlassPane.getInstance().addKeyListener(this);

    }

    /**
     * Sets the selection manager.
     * 
     * 
     * @param selectionManager
     */
    public void setSelectionManager(SelectionManager selectionManager)
    {
        this.classSelectionManager = selectionManager;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
     */
    public void mouseClicked(MouseEvent e)
    {
        if (e.getClickCount() > 1 && ((e.getModifiers() & MouseEvent.BUTTON1_DOWN_MASK) != 0)) {
            //GreenfootObject gObject = getObject(e.getX(), e.getY());
            //RObject rObject = ObjectTracker.instance().getRObject(gObject);
            //TODO: inspect rObject

        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
     */
    public void mousePressed(MouseEvent e)
    {
        maybeShowPopup(e);
        if (SwingUtilities.isLeftMouseButton(e)) {
            GreenfootObject go = getObject(e.getX(), e.getY());
            if (go != null) {
                int dragOffsetX = go.getX() - e.getX();
                int dragOffsetY = go.getY() - e.getY();
                dragBeginX = go.getX();
                dragBeginY = go.getY();
                objectDropped = false;
                DragGlassPane.getInstance().startDrag(go, dragOffsetX, dragOffsetY, this);
                worldCanvas.removeMouseListener(this);
                worldCanvas.addMouseMotionListener(DragGlassPane.getInstance());
                worldCanvas.addMouseListener(DragGlassPane.getInstance());
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
     */
    public void mouseReleased(MouseEvent e)
    {
        maybeShowPopup(e);
        
    }

    private boolean maybeShowPopup(MouseEvent e)
    {
        if (e.isPopupTrigger()) {

            GreenfootObject obj = getObject(e.getX(), e.getY());
            if (obj != null) {
                JPopupMenu menu = ObjectTracker.instance().getJPopupMenu(obj, e);
                menu.setVisible(true);
            }
            return true;

        }
        return false;
    }

    /**
     * TODO: this method should be removed when it is posisble to select among
     * multiple objects from a popup menu.
     * 
     * @param x
     * @param y
     * @return
     */
    private GreenfootObject getObject(int x, int y)
    {
        Collection objectsThere = world.getObjectsAt(x, y);
        if (objectsThere.size() < 1) {
            return null;
        }

        Iterator iter = objectsThere.iterator();
        GreenfootObject go = null;
        while (iter.hasNext()) {
            go = (GreenfootObject) iter.next();
        }
        return go;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
     */
    public void mouseEntered(MouseEvent e)
    {
        worldCanvas.requestFocusInWindow();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
     */
    public void mouseExited(MouseEvent e)
    {
        
    }

    /**
     * Request a repaints of world
     */
    public void repaint()
    {
        worldCanvas.repaint();
    }

    /**
     * Provides an Iterator to all the things in the world. This iterator is not
     * updated on calls to the worlds add/remove methods. So it is safe to
     * add/remove objects in the world while iterating.
     *  
     */
    public Iterator getGreenfootObjects()
    {
        return world.getObjects();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.awt.event.KeyListener#keyTyped(java.awt.event.KeyEvent)
     */
    public void keyTyped(KeyEvent e)
    {
        logger.info("KEY TYPED");
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent)
     */
    public void keyPressed(KeyEvent e)
    {
        isQuickAddActive = e.isShiftDown();
        // quickAddIfActive();
    }

    private boolean quickAdd()
    {
        if (isQuickAddActive) {
            ClassView cls = (ClassView) classSelectionManager.getSelected();
            if (cls != null) {
                //Object selected = classSelectionManager.getSelected();
                //ClassView classView = (ClassView) selected;
                Object object = cls.createInstance();

                if (object instanceof GreenfootObject) {
                    GreenfootObject go = (GreenfootObject) object;
                    int dragOffsetX = -go.getImage().getIconWidth() / 2;
                    int dragOffsetY = -go.getImage().getIconHeight() / 2;
                    DragGlassPane.getInstance().startDrag(go, dragOffsetX, dragOffsetY, this);
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent)
     */
    public void keyReleased(KeyEvent e)
    {
        isQuickAddActive = e.isShiftDown();
        // DragGlassPane.getInstance().endDrag();
        worldCanvas.requestFocus();
    }

    /**
     * Sets a new world.
     * 
     * @param world
     */
    public void setWorld(GreenfootWorld world)
    {
        installNewWorld(world);
    }

    private void installNewWorld(GreenfootWorld world)
    {
        this.world = world;

        if (world != null) {
            world.setDelay(delay);
            world.addObserver(worldCanvas);
            worldTitle.setText(world.getClass().getName());
        }
        worldCanvas.setWorld(world); //TODO consider remoivng this and only
                                     // rely on observer
        worldTitle.setEnabled(true);
        worldTitle.setHorizontalAlignment(SwingConstants.CENTER);
        MouseListener listeners[] = worldTitle.getMouseListeners();
        for (int i = 0; i < listeners.length; i++) {
            MouseListener listener = listeners[i];
            worldTitle.removeMouseListener(listener);
        }

        worldTitle.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e)
            {
                if (e.isPopupTrigger()) {
                    JPopupMenu menu = ObjectTracker.instance().getJPopupMenu(WorldHandler.this.world, e);
                    menu.setVisible(true);
                }
            }

            public void mousePressed(MouseEvent e)
            {
                if (e.isPopupTrigger()) {
                    JPopupMenu menu = ObjectTracker.instance().getJPopupMenu(WorldHandler.this.world, e);
                    menu.setVisible(true);
                }
            }
        });

    }

    public void installNewWorld(RObject rWorld)
    {
        Object world = ObjectTracker.instance().getRealObject(rWorld);
        installNewWorld((GreenfootWorld) world);

    }

    /**
     * @return
     */
    public Component getWorldTitle()
    {
        return worldTitle;
    }

    public WorldCanvas getWorldCanvas()
    {
        return worldCanvas;
    }

    /**
     *  
     */
    public void delay()
    {
        world.delay();
    }

    /**
     * @param delay
     */
    public void setDelay(int delay)
    {
        this.delay = delay;
        if (world != null) {
            world.setDelay(delay);
        }
    }

    public boolean drop(Object o, Point p)
    {
        if (o instanceof GreenfootObject) {
            GreenfootObject go = (GreenfootObject) o;
            world.addObject(go);
            go.setLocation((int) p.getX(),(int) p.getY());
            // quickAddIfActive();
            objectDropped = true;
            return true;
        }
        else {
            return false;
        }
    }

    public boolean drag(Object o, Point p)
    {
        if (o instanceof GreenfootObject) {
            GreenfootObject go = (GreenfootObject) o;
            world.addObject(go);
            go.setLocation((int) p.getX(),(int) p.getY());
            return true;
        }
        else {
            return false;
        }
    }

    public void dragEnded(Object o)
    {
        if (o instanceof GreenfootObject) {
            GreenfootObject go = (GreenfootObject) o;
            world.removeObject(go);
        }
    }
    
    public void dragFinished(Object o)
    {
        // restore listeners
        if (! objectDropped || ! isQuickAddActive || ! quickAdd()) {
            DragGlassPane drag = DragGlassPane.getInstance();
            worldCanvas.removeMouseListener(drag);
            worldCanvas.removeMouseMotionListener(drag);
            worldCanvas.addMouseListener(this);
        }
        
        // if the operation was cancelled, add the object back into the world
        // at its original position
        if (! objectDropped && o instanceof GreenfootObject) {
            GreenfootObject go = (GreenfootObject) o;
            go.setLocation(dragBeginX, dragBeginY);
            world.addObject(go);
            objectDropped = true;
        }
    }
}