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
package megan.commands.format;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graphview.GraphView;
import jloda.gui.ChooseColorDialog;
import jloda.gui.commands.CommandBase;
import jloda.gui.commands.ICommand;
import jloda.util.ResourceManager;
import jloda.util.parse.NexusStreamParser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * set color
 * Daniel Huson, 4.2011
 */
public class SetColorCommand extends CommandBase implements ICommand {

    /**
     * get the name to be used as a menu label
     *
     * @return name
     */
    public String getName() {
        return "Set Color";
    }

    /**
     * get description to be used as a tooltip
     *
     * @return description
     */
    public String getDescription() {
        return "Set the color of selected nodes and edges";
    }

    public ImageIcon getIcon() {
        return ResourceManager.getIcon("YellowSquare16.gif");
    }

    /**
     * gets the accelerator key  to be used in menu
     *
     * @return accelerator key
     */
    public KeyStroke getAcceleratorKey() {
        return null;
    }

    /**
     * parses the given command and executes it
     *
     * @param np
     * @throws java.io.IOException
     */
    @Override
    public void apply(NexusStreamParser np) throws Exception {
        np.matchIgnoreCase("set color=");
        Color color = null;
        if (np.peekMatchIgnoreCase("null"))
            np.matchIgnoreCase("null");
        else
            color = np.getColor();
        np.matchIgnoreCase(";");

        if (getViewer() instanceof GraphView) {
            boolean changed = false;
            GraphView viewer = (GraphView) getViewer();
            for (Node v : viewer.getSelectedNodes()) {
                viewer.setColor(v, color);
                changed = true;
            }
            for (Edge edge : viewer.getSelectedEdges()) {
                viewer.setColor(edge, color);
                changed = true;
            }
            if (changed) {
                viewer.repaint();
            }
        }
    }

    /**
     * action to be performed
     *
     * @param ev
     */
    public void actionPerformed(ActionEvent ev) {
        Color color = ChooseColorDialog.showChooseColorDialog(getViewer().getFrame(), "Choose color", null);

        if (color != null)
            execute("set color=" + color.getRed() + " " + color.getGreen() + " " + color.getBlue() + ";");
    }


    /**
     * is this a critical command that can only be executed when no other command is running?
     *
     * @return true, if critical
     */
    public boolean isCritical() {
        return true;
    }

    /**
     * get command-line usage description
     *
     * @return usage
     */
    @Override
    public String getSyntax() {
        return "set color={<color>|null};";
    }

    /**
     * is the command currently applicable? Used to set enable state of command
     *
     * @return true, if command can be applied
     */
    public boolean isApplicable() {
        return getViewer() instanceof GraphView &&
                (((GraphView) getViewer()).getSelectedNodes().size() > 0
                        || ((GraphView) getViewer()).getSelectedEdges().size() > 0);
    }

    /**
     * gets the command needed to undo this command
     *
     * @return undo command
     */
    public String getUndo() {
        return null;
    }
}