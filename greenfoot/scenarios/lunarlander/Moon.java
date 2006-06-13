import greenfoot.*;  // (World, Actor, GreenfootImage, and Greenfoot)

import java.awt.Color;

/**
 * A moon.
 * 
 * @author Poul Henriksen
 * @version 1.0.1
 */
public class Moon extends World
{
    /** Gravity on the moon */
    private double gravity = 1.6;
    
    /** Color of the landing platform */
    private Color landingColor = Color.WHITE;
    
    /** Color of the space */
    private Color spaceColor = Color.BLACK;
    
    public Moon() {
        super(600,600,1);
        setBackground("moon.png");
        addObject(new Lander(), 326, 100);
        //Force the explosion to initialise the images
        //We do this in a thread to make it load in the background
        new Thread() {
            public void run() {
                Explosion.initialiseImages();
            }
        }.start();
    }
    
    /** 
     * Gravity on the moon  
     *
     */
    public double getGravity()  {
        return gravity;
    }
    
    
    /**
     * Color of the landing platform 
     * 
     */
    public Color getLandingColor() {
        return landingColor;
    }    
    
    
    /**
     * Color of the space 
     * 
     */
    public Color getSpaceColor() {
        return spaceColor;
    }
 
}