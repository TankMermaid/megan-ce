/*
 *  Copyright (C) 2016 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package megan.samplesviewer;

import jloda.gui.MenuConfiguration;
import jloda.util.ProgramProperties;
import megan.classification.data.ClassificationCommandHelper;

/**
 * configuration for menu and toolbar
 * Daniel Huson, 7.2010
 */
public class GUIConfiguration {

    /**
     * get the menu configuration
     *
     * @return menu configuration
     */
    public static MenuConfiguration getMenuConfiguration() {
        MenuConfiguration menuConfig = new MenuConfiguration();
        menuConfig.defineMenuBar("File;Edit;Attributes;Samples;Layout;Window;Help;");

        menuConfig.defineMenu("File", "New...;|;Open...;@Open Recent;|;Open From Server...;|;Compare...;|;Import From BLAST...;@Import;|;Save As...;|;"
                + "Export Image...;Export Legend...;@Export;|;Page Setup...;Print...;|;Properties...;|;Close;|;Quit;");

        menuConfig.defineMenu("Open Recent", ";");
        menuConfig.defineMenu("Export", "CSV Format...;BIOM1 Format...;STAMP Format...;|;Metadata...;|;Reads...;Matches...;Alignments...;|;MEGAN Summary File...;");
        menuConfig.defineMenu("Import", "Import CSV Format...;Import BIOM1 Format...;|;Import Metadata...;");

        menuConfig.defineMenu("Edit", "Samples Viewer Cut;Samples Viewer Copy;Samples Viewer Paste;Samples Viewer Paste By Attribute;|;Select All;Select None;Select Similar;From Previous Window;|;Find...;Find Again;|;Colors...;");

        menuConfig.defineMenu("Attributes", getAttributeColumnHeaderPopupConfiguration());

        menuConfig.defineMenu("Samples", "Rename Sample...;|;Move Up;Move Down;|;Extract Samples...;|;Compute Core Biome...;Compute Total Biome...;|;Compute Rare Biome...;Compute Shared Biome...;|;Open RMA File...;");

        menuConfig.defineMenu("Window", "Reset Window Location;Set Window Size...;|;Message Window...;|;" +
                "Inspector Window...;|;Main Viewer...;" + ClassificationCommandHelper.getOpenViewerMenuString() + "|;Samples Viewer...;Groups Viewer...;|;");

        menuConfig.defineMenu("Help", "About...;How to Cite...;|;Community Website...;Reference Manual...;" + ProgramProperties.getIfEnabled("usingInstall4j", "|;Check For Updates...;"));
        return menuConfig;
    }

    /**
     * gets the toolbar configuration
     *
     * @return toolbar configuration
     */
    public static String getToolBarConfiguration() {
        return "Open...;|;Find...;|;Colors...;|;Group Nodes;Ungroup All;|;Main Viewer...;"
                + ClassificationCommandHelper.getOpenViewerMenuString();
    }

    /**
     * gets the column header configuration
     *
     * @return configuration
     */
    public static String getAttributeColumnHeaderPopupConfiguration() {
        return "Rename Attribute...;|;Sort Increasing;Sort Decreasing;|;Move Left;Move Right;|;New Column...;Delete Column(s)...;|;Hide;Unhide;|;Use to Color Samples;Use to Shape Samples;Use to Label Samples;Use to Group Samples;|;" +
                "Compare Relative...;Compare Absolute...;";
    }

    public static String getSampleRowHeaderPopupConfiguration() {
        return "Move Up;Move Down;|;Rename Sample...;|;Open RMA File...;|;Set Shape...;"; // we add the color menu item after these
    }
    /**
     * gets main popup configuration
     *
     * @return configuration
     */
    public static String getMainPopupConfiguration() {
        return "|;Select All;Select None;Select Similar;From Previous Window;";
    }
}
