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
package megan.commands.algorithms;

import jloda.gui.TwoInputOptionsPanel;
import jloda.gui.commands.ICommand;
import jloda.util.Basic;
import jloda.util.ProgramProperties;
import jloda.util.parse.NexusStreamParser;
import megan.commands.CommandBase;
import megan.fx.NotificationsInSwing;
import megan.samplesviewer.SamplesViewer;
import megan.viewer.MainViewer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Collection;

/**
 * compute biome
 * Daniel Huson, 2.2013
 */
public class CoreBiomeCommand extends CommandBase implements ICommand {
    public String getSyntax() {
        return null;
    }

    public void apply(NexusStreamParser np) throws Exception {
    }

    public boolean isCritical() {
        return true;
    }

    public void actionPerformed(ActionEvent event) {
        final Collection<String> samples;
        if (getViewer() instanceof SamplesViewer)
            samples = ((SamplesViewer) getViewer()).getSamplesTable().getSelectedSamplesInOrder();
        else if (getViewer() instanceof MainViewer)
            samples = ((MainViewer) getViewer()).getDocument().getSampleNames();
        else
            return;

        if (samples.size() > 1) {
            float sampleThresholdPercent = (float) ProgramProperties.get("CoreBiomeSampleThreshold", 50.0);
            float classThresholdPercent = (float) ProgramProperties.get("CoreBiomeClassThreshold", 1.0);

            final String[] result = TwoInputOptionsPanel.show(getViewer().getFrame(), "MEGAN - Setup Compute Core Biome", "Sample threshold (%)", "" + sampleThresholdPercent,
                    "Minimum percent of samples in which class must be present",
                    "Class threshold (%)", "" + classThresholdPercent, "Percentage of assigned reads in sample that class must achieve to be considered present in that sample");
            if (result != null) {
                if (Basic.isFloat(result[0]) && Basic.isFloat(result[1])) {

                    sampleThresholdPercent = Basic.parseFloat(result[0]);
                    ProgramProperties.put("CoreBiomeSampleThreshold", sampleThresholdPercent);
                    classThresholdPercent = Basic.parseFloat(result[1]);
                    ProgramProperties.put("CoreBiomeClassThreshold", classThresholdPercent);

                    execute("compute biome=core classThreshold=" + result[1] + " sampleThreshold=" + result[0] + " samples='" + Basic.toString(samples, "' '") + "';");
                } else
                    NotificationsInSwing.showError(getViewer().getFrame(), "Failed to parse values: " + Basic.toString(result, " "));
            }
        }
    }

    public boolean isApplicable() {
        return getViewer() instanceof MainViewer || getViewer() instanceof SamplesViewer && ((SamplesViewer) getViewer()).getSamplesTable().getNumberOfSelectedSamples() > 1;
    }

    public String getName() {
        return "Compute Core Biome...";
    }

    public ImageIcon getIcon() {
        return null;
    }

    public String getDescription() {
        return "Determine taxa and functions that appear in a majority of the samples";
    }
}

