package greenfoot.gui.export;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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

    /**
     * Must be called from the swing event thread.
     */
    public void setImageCanvas(ImageEditCanvas imageCanvas)
    {
        this.imageCanvas = imageCanvas;
        buildUI();
    }

    private void buildUI()
    {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        imageCanvas.addMouseMotionListener(this);
        imageCanvas.addMouseListener(this);
        imageCanvas.addMouseWheelListener(this);
        imageCanvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        int min = (int) (imageCanvas.getMinimumScale() * 100);
        int max = 100;
        zoomSlider = new JSlider(JSlider.VERTICAL, min, max, (int) (imageCanvas.getScale() * 100));

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

        add(Box.createHorizontalGlue());
        Box border = new Box(BoxLayout.LINE_AXIS);
        border.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        border.add(imageCanvas);
        add(border);
        add(zoomSlider);
        add(Box.createHorizontalGlue());

    }

    public void mouseDragged(MouseEvent e)
    {
        if (e.getButton() == MouseEvent.BUTTON1) {
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

}
