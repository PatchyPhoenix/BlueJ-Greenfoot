/*
 This file is part of the BlueJ program. 
 Copyright (C) 2012,2013,2014,2016,2019  Michael Kolling and John Rosenberg
 
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
package bluej.prefmgr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bluej.pkgmgr.Project;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import bluej.Config;
import bluej.Theme;
import bluej.utility.javafx.JavaFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * "Interface" preference panel. Settings for what to show (teamwork, testing tools etc)
 * and interface language.
 * 
 * @author Davin McCall
 */
@OnThread(Tag.FXPlatform)
public class InterfacePanel extends VBox
        implements PrefPanelListener
{
    private ArrayList<String> allLangsInternal;
    private ComboBox langDropdown;
    
    private CheckBox accessibility;
    
    private CheckBox toggleTestNewsMode;

    private ArrayList<String> allThemes;
    private ComboBox<Theme> theme;
    private Node themeSetting;
    
    public InterfacePanel()
    {
        JavaFXUtil.addStyleClass(this, "prefmgr-pref-panel");

        List<Node> langPanel = new ArrayList<>();
        {
            
            {
                
                
                allLangsInternal = new ArrayList<String>();
                List<String> allLangsReadable = new ArrayList<String>();
                
                for (int i = 1; ; i++) {
                    String langString = Config.getPropString("bluej.language" + i, null);
                    if (langString == null) {
                        break;
                    }
                    
                    // The format of a language string is:
                    //    internal-name:display-name:iso3cc
                    // The iso3cc (ISO country code) is optional.
                    
                    int colonIndex = langString.indexOf(':');
                    if (colonIndex == -1) {
                        continue; // don't understand this one
                    }
                    
                    int secondColon = langString.indexOf(':', colonIndex + 1);
                    if (secondColon == -1) {
                        secondColon = langString.length();
                    }
                    
                    allLangsInternal.add(langString.substring(0, colonIndex));
                    allLangsReadable.add(langString.substring(colonIndex + 1, secondColon));
                }
                
                if (allLangsInternal.isEmpty()) {
                    // Guard against modified or corrupted bluej.defs file
                    allLangsInternal.add(Config.language);
                    allLangsReadable.add(Config.language);
                }
                
                String [] langs = new String[allLangsReadable.size()];
                allLangsReadable.toArray(langs);

                langDropdown = new ComboBox(FXCollections.observableArrayList(langs));
            }
            langPanel.add(PrefMgrDialog.labelledItem("prefmgr.interface.language", langDropdown));
            
            Label t = new Label(Config.getString("prefmgr.interface.language.restart"));
            langPanel.add(t);
        }
        getChildren().add(PrefMgrDialog.headedVBox("prefmgr.interface.language.title", langPanel));        
        
        accessibility = new CheckBox(Config.getString("prefmgr.accessibility.support"));
        getChildren().add(PrefMgrDialog.headedVBox("prefmgr.accessibility.title", Arrays.asList(accessibility)));

        allThemes = new ArrayList<String>();
        allThemes.add("light");
        allThemes.add("dark");
        ObservableList<Theme> themePoss = FXCollections.observableArrayList(Theme.light, Theme.dark);
        theme = new ComboBox<Theme>(themePoss);
        themeSetting = PrefMgrDialog.labelledItem("Theme", theme);
        Label t = new Label(Config.getString("You will need to restart BlueJ for the theme setting to take effect."));
        getChildren().add(PrefMgrDialog.headedVBox("Appearance", Arrays.asList(themeSetting, t)));

        // Hide this checkbox so that it is only revealed if you mouse-over while holding shift:
        toggleTestNewsMode = new CheckBox("Switch to testing mode for news display (after restart)");
        toggleTestNewsMode.setOpacity(0);
        toggleTestNewsMode.setOnMouseMoved(e -> {
            if (e.isShiftDown())
            {
                toggleTestNewsMode.setOpacity(1.0);
            }
        });
        getChildren().add(toggleTestNewsMode);
    }
    
    @Override
    public void beginEditing(Project project)
    {
        String currentLang = Config.getPropString("bluej.language", "english");
        int curLangIndex = allLangsInternal.indexOf(currentLang);
        if (curLangIndex == -1) {
            curLangIndex = 0;
        }
        langDropdown.getSelectionModel().select(curLangIndex);

        String currentTheme = Config.getPropString("bluej.theme", "light");
        int curThemeIndex = allThemes.indexOf(currentTheme);
        if(curThemeIndex == -1)
            curThemeIndex = 0;
        theme.getSelectionModel().select(curThemeIndex);
        
        accessibility.setSelected(PrefMgr.getFlag(PrefMgr.ACCESSIBILITY_SUPPORT));
        
        toggleTestNewsMode.setSelected(PrefMgr.getFlag(PrefMgr.NEWS_TESTING));
        if (toggleTestNewsMode.isSelected())
        {
            // Show it to begin with:
            toggleTestNewsMode.setOpacity(1.0);
        }
    }

    @Override
    public void commitEditing(Project project)
    {
        Config.putPropString("bluej.language", allLangsInternal.get(langDropdown.getSelectionModel().getSelectedIndex()));
        
        Config.putPropString("bluej.theme", allThemes.get(theme.getSelectionModel().getSelectedIndex()));

        PrefMgr.setFlag(PrefMgr.ACCESSIBILITY_SUPPORT, accessibility.isSelected());

        // Only counts as selected if selected and visible:
        boolean testNewsMode = toggleTestNewsMode.isSelected() && toggleTestNewsMode.getOpacity() == 1.0;
        if (testNewsMode && !PrefMgr.getFlag(PrefMgr.NEWS_TESTING))
        {
            // Also blank last-seen date so they see the latest testing news:
            Config.putPropString(Config.MESSAGE_LATEST_SEEN, "");
        }
        PrefMgr.setFlag(PrefMgr.NEWS_TESTING, testNewsMode);
    }
    
    @Override
    public void revertEditing(Project project)
    {
    }
}
