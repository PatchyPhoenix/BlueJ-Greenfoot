/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009,2011,2015,2018  Poul Henriksen and Michael Kolling
 
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

package greenfoot.guifx.export;

import bluej.Config;

import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;

/**
 * ExportPane is a superclass for all changing panes that can appear 
 * in the Export dialogue.
 *
 * @author Michael Kolling
 * @author Amjad Altadmri
 */
public abstract class ExportPane extends Tab
{
    private static final String lockText = Config.getString("export.lock.label");
    private static final String hideControlsText = Config.getString("export.controls.label");
    private static final String hideControlsDescription = Config.getString("export.controls.description");
    private static final String lockDescription = Config.getString("export.lock.description");

    protected final CheckBox lockScenario = new CheckBox(lockText);
    protected final CheckBox hideControls = new CheckBox(hideControlsText);

    /**
     * Create a an export pane for export to web pages.
     */
    public ExportPane()
    {
        lockScenario.setSelected(true);
        lockScenario.setAlignment(Pos.BASELINE_LEFT);
        lockScenario.setTooltip(new Tooltip(lockDescription));

        hideControls.setSelected(false);
        hideControls.setAlignment(Pos.BASELINE_LEFT);
        hideControls.setTooltip(new Tooltip(hideControlsDescription));

        getStyleClass().add("export-pane");
    }

    /**
     * This method will be called when this pane is activated (about to be
     * shown/visible)
     */
    public abstract void activated();
    
    /**
     * This method will be called when the user is about to export the scenario
     * with information from this pane. Will be called from the swing event
     * thread and will not publish until this method returns.
     * 
     * @return Whether to continue publishing. Continues if true, cancels if false.
     */
    public abstract boolean prePublish();  
    
    /**
     * This method will be called when the scenario has been published with the
     * information from this pane.
     * 
     * @param success Whether the publish was successfull
     */
    public abstract void postPublish(boolean success);

    /**
     * Return true if the user wants to lock the scenario.
     */
    public boolean lockScenario()
    {
        return lockScenario.isSelected();
    }
    
    /**
     * Return true if the user wants to hide the scenario controls.
     * @return true if the hide controls checkbox is selected.
     */
    public boolean hideControls()
    {
        return hideControls.isSelected();
    }

    /**
     * Add a shared style class for the tab contents.
     */
    protected void applySharedStyle()
    {
        getContent().getStyleClass().add("export-pane");
    }
}