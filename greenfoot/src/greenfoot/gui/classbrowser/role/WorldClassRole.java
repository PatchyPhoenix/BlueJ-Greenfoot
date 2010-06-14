/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009  Poul Henriksen and Michael Kolling 
 
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
package greenfoot.gui.classbrowser.role;

import greenfoot.actions.SaveWorldAction;
import greenfoot.actions.SelectImageAction;
import greenfoot.actions.ShowApiDocAction;
import greenfoot.core.GClass;
import greenfoot.core.GProject;
import greenfoot.platforms.ide.WorldHandlerDelegateIDE;

import javax.swing.JPopupMenu;

import bluej.Config;

/**
 * @author Poul Henriksen
 * @version $Id: WorldClassRole.java 7761 2010-06-14 13:11:58Z nccb $
 */
public class WorldClassRole extends ImageClassRole
{
    private String template = "worldclass.tmpl";
    private WorldHandlerDelegateIDE ide;

    public WorldClassRole(GProject project, WorldHandlerDelegateIDE ide)
    {
    	super(project);
    	this.ide = ide;
    }
    
    @Override
    public String getTemplateFileName()
    {
        return template;
    }
    
    /* (non-Javadoc)
     * @see greenfoot.gui.classbrowser.role.ClassRole#addPopupMenuItems(javax.swing.JPopupMenu, boolean)
     */
    public void addPopupMenuItems(JPopupMenu menu, boolean coreClass)
    {
        if (! coreClass) {
            menu.add(createMenuItem(new SelectImageAction(classView, this)));
            GClass lastWorld = ide.getLastWorldGClass();
            if (lastWorld.equals(gClass)) {
                menu.add(createMenuItem(ide.getSaveWorldAction()));
            }
        }
        else {
            menu.add(createMenuItem(new ShowApiDocAction(Config.getString("show.apidoc"), "greenfoot/World.html")));
        }
    }

}
