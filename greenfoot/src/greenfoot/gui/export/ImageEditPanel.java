package greenfoot.gui.export;

import greenfoot.util.GraphicsUtilities;
import greenfoot.util.GreenfootUtil;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Panel that lets you manipulate an image by zooming (with slider or
 * mouse wheel) and moving (by dragging with the mouse).
 * 
 * @author Poul Henriksen
 */
public class ImageEditPanel extends JPanel
    implements MouseMotionListener, MouseListener, MouseWheelListener
{
    /** Canvas for the image we are controlling. */
    private ImageEditCanvas imageCanvas;
    /** Last position where mouse was dragged. */
    private int lastX;
    /** Last position where mouse was dragged. */
    private int lastY;

    /** Slider for zooming*/
    private JSlider zoomSlider;

    /** Width of the image view */
    private int width;
    /** Height of the image view */
    private int height;
    
    /** Label used for the slider. */
    private JLabel bigLabel;
    /** Label used for the slider. */
    private JLabel smallLabel;
    
    public ImageEditPanel(int width, int height) {
        this.width = width;
        this.height = height;
        setPreferredSize(new Dimension(width + 2, height + 2));
    }
    
    /**
     * Set the image to be manipulated.
     */
    public void setImage(BufferedImage snapShot)
    {
        if(imageCanvas == null) {
            imageCanvas = new ImageEditCanvas(width, height, snapShot);
            imageCanvas.fit();
            buildUI();
        }
        else {
            double oldMinScale = imageCanvas.getMinimumScale();
            imageCanvas.setImage(snapShot); 
            double newMinScale = imageCanvas.getMinimumScale();            
            if(Math.abs(newMinScale - oldMinScale) > .0000001 ) {
                // Only re-fit scaling if there was a change in size.
                imageCanvas.fit();
                adjustSlider();    
            } 
        }
    }

    
    private void buildUI()
    {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        imageCanvas.addMouseMotionListener(this);
        imageCanvas.addMouseListener(this);
        imageCanvas.addMouseWheelListener(this);
        imageCanvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      
        zoomSlider = new JSlider(JSlider.VERTICAL);
        zoomSlider.setOpaque(false);
        
        // Create labels for slider
        try {
            URL url = new File(GreenfootUtil.getGreenfootLogoPath()).toURI().toURL();
            BufferedImage iconImage = GraphicsUtilities.loadCompatibleImage(url);
            bigLabel = new JLabel(new ImageIcon(iconImage.getScaledInstance(-1, 15, Image.SCALE_DEFAULT)));
            smallLabel = new JLabel(new ImageIcon(iconImage.getScaledInstance(-1, 10, Image.SCALE_DEFAULT)));
            zoomSlider.setPaintLabels(true);
        }
        catch (MalformedURLException e1) {
            e1.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        
        adjustSlider();
        
        Dimension maxSize = zoomSlider.getMaximumSize();
        maxSize.height = imageCanvas.getMaximumSize().height;
        zoomSlider.setMaximumSize(maxSize);
        
        zoomSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e)
            {
                JSlider source = (JSlider) e.getSource();
                int scale = (int) source.getValue();
                imageCanvas.setScale(scale / 100.);
            }
        });
        // Panel that contains the border so that borders are not drawn on our
        // canvas, but just outside it.
        Box border = new Box(BoxLayout.LINE_AXIS);
        border.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        border.add(imageCanvas);
        
        add(Box.createHorizontalGlue());
        add(border);
        add(zoomSlider);
        add(Box.createHorizontalGlue());

    }

    private void adjustSlider()
    {
        int min = (int) (imageCanvas.getMinimumScale() * 100);
        int max = 100;
        int scale = (int) (imageCanvas.getScale() * 100);
        zoomSlider.setMinimum(min);
        zoomSlider.setMaximum(max);        
        zoomSlider.setValue(scale);
        Dictionary<Integer, JLabel> labels = new Hashtable<Integer, JLabel>();
        labels.put(zoomSlider.getMinimum(), smallLabel);
        labels.put(zoomSlider.getMaximum(), bigLabel);
        zoomSlider.setLabelTable(labels);
        zoomSlider.repaint();
    }

    public void mouseDragged(MouseEvent e)
    {
        if ( (e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0) {
            imageCanvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            int dx = e.getX() - lastX;
            int dy = e.getY() - lastY;
            imageCanvas.move(dx, dy);
            lastX = e.getX();
            lastY = e.getY();
        }
    }

    public void mouseMoved(MouseEvent e)
    {}

    public void mouseClicked(MouseEvent e)
    {}

    public void mouseEntered(MouseEvent e)
    {}

    public void mouseExited(MouseEvent e)
    {}

    public void mousePressed(MouseEvent e)
    {
        if (e.getButton() == MouseEvent.BUTTON1) {
            lastX = e.getX();
            lastY = e.getY();
        }
    }

    public void mouseReleased(MouseEvent e)
    {
        if (e.getButton() == MouseEvent.BUTTON1) {
            imageCanvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
    }

    public void mouseWheelMoved(MouseWheelEvent e)
    {
        int scroll = e.getUnitsToScroll();
        zoomSlider.setValue(zoomSlider.getValue() - scroll);
    }

    /**
     * Get the image created by this image panel or null if none exists.
     */
    public BufferedImage getImage()
    {
        if(imageCanvas == null) {
            return null;
        }
        BufferedImage newImage = GraphicsUtilities.createCompatibleImage(width, height);
        Graphics2D g = newImage.createGraphics();
        imageCanvas.paintImage(g);
        g.dispose();
        return newImage;
    }

}
