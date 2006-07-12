//
// This class is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation.
//
// This class is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.

import greenfoot.*;

import java.util.WeakHashMap;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.image.FilteredImageSource;
import java.awt.image.RGBImageFilter;
import java.awt.MediaTracker;
import java.awt.Image;
import java.awt.Component;


/**
 * A colored image. The image is "tinted" with a specific color - in effect changing the color of an image.
 * 
 * @author Poul Henriksen
 */
public class ColoredImage extends GreenfootImage
{
    /** The current color used to tint this image*/
    private Color color;
    
    /** The original untinted image */
    private GreenfootImage org;    
    
    /** Pool to hold images for actors, to avoid recreating images all the time. Actor->ColoredImage */
    private static WeakHashMap pool = new WeakHashMap();
    
    /**
     * Gets a colored version of the image. If the image is already colored, it will just change the color on that instance.
     */
    public static ColoredImage getImage(GreenfootImage image, Color newColor) 
    {        
        if(! (image instanceof ColoredImage)) {
            image = new ColoredImage(image, newColor);
        }
        ColoredImage colorImage = (ColoredImage) image;
        colorImage.changeColor(newColor);
        return colorImage;
    }
    
    /**
     * Returns a colored version of the image for an actor, using the actors getImage() method.
     */
    public static ColoredImage getImage(Actor actor, GreenfootImage image, Color newColor) {
        ColoredImage colorImage = (ColoredImage) pool.get(actor);
        if(colorImage == null) {
            colorImage = getImage(image, newColor);
            pool.put(actor, colorImage);
        }
        colorImage.changeColor(newColor);
        return colorImage;
    }

    /**
     * Create a new colored image.
     */
    public ColoredImage(GreenfootImage org, Color color)
    {
        super(org.getWidth(), org.getHeight());
        this.org=org;          
    }
    
    /**
     * Get the color this image has been tinted with.
     */
    public Color getColor() {
        return this.color;
    }    
        
    /**
     * Change the color of this image.
     */
    public void changeColor(Color newColor) 
    {
        if(newColor != color)  {
            color = newColor;            
            tintImage();
        }        
    }

    /**
     * Tint the image with the color.
     */
    private void tintImage()
    {
        Image orgImg = org.getAwtImage();
        FilteredImageSource src = new FilteredImageSource(orgImg.getSource(), new TintFilter(color));
        Image newImg = Toolkit.getDefaultToolkit().createImage(src);
        Image thisImg = getAwtImage();
        thisImg.getGraphics().drawImage(newImg, 0,0, null);        
        MediaTracker tracker = new MediaTracker(new Component() {});        
        tracker.addImage(thisImg , 0);
        try {
            tracker.waitForID(0);
            tracker.removeImage(thisImg );
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }   
    
    /**
     * An image filter class that tints colors based on the tint provided to the
     * constructor (the color of an object).
     */
    private static class TintFilter extends RGBImageFilter
    {
       private int tintR, tintG, tintB;

       /** Constructs an image filter for tinting colors in an image. * */
       public TintFilter(Color color)
       {
          canFilterIndexColorModel = true;
          int rgb = color.getRGB();
          tintR = (rgb >> 16) & 0xff;
          tintG = (rgb >> 8) & 0xff;
          tintB = rgb & 0xff;
       }

       public int filterRGB(int x, int y, int argb)
       {
          // Separate pixel into its RGB coomponents.
          int alpha = (argb >> 24) & 0xff;
          int red = (argb >> 16) & 0xff;
          int green = (argb >> 8) & 0xff;
          int blue = argb & 0xff;

          // Use NTSC/PAL algorithm to convert RGB to grayscale.
          int lum = (int) (0.2989 * red + 0.5866 * green + 0.1144 * blue);

          // Interpolate along spectrum black->white with tint at midpoint
          double scale = Math.abs((lum - 128) / 128.0); // absolute distance
                                                          // from midpt
          int edge = lum < 128 ? 0 : 255; // going towards white or black?
          red = tintR + (int) ((edge - tintR) * scale); // scale from midpt to
                                                          // edge
          green = tintG + (int) ((edge - tintG) * scale);
          blue = tintB + (int) ((edge - tintB) * scale);
          return (alpha << 24) | (red << 16) | (green << 8) | blue;
       }

    }

}