/*
 *  Copyright (C) 2015 Daniel H. Huson
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
package megan.commands.additional;

import jloda.gui.commands.CommandBase;
import jloda.gui.commands.ICommand;
import jloda.util.Basic;
import jloda.util.ProgramProperties;
import jloda.util.ResourceManager;
import jloda.util.parse.NexusStreamParser;
import megan.algorithms.NaiveProjectionProfile;
import megan.classification.ClassificationManager;
import megan.core.*;
import megan.fx.NotificationsInSwing;
import megan.main.MeganProperties;
import megan.parsers.blast.BlastMode;
import megan.util.CallBack;
import megan.util.PopupChoice;
import megan.viewer.ClassificationViewer;
import megan.viewer.TaxonomicLevels;
import megan.viewer.gui.NodeDrawer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public class ProjectAssignmentsToRankCommand extends CommandBase implements ICommand {

    public String getSyntax() {
        return "project rank={" + Basic.toString(TaxonomicLevels.getAllMajorRanks(), "|") + "} [minPercent={number}];";
    }

    public void apply(NexusStreamParser np) throws Exception {
        np.matchIgnoreCase("project rank=");
        final String rank = np.getWordMatchesRespectingCase(Basic.toString(TaxonomicLevels.getAllNames(), " "));
        final float minPercent;
        if (np.peekMatchIgnoreCase("minPercent")) {
            np.matchIgnoreCase("minPercent=");
            minPercent = (float) np.getDouble(0, 100);
        } else
            minPercent = 0f;
        np.matchIgnoreCase(";");

        final Document doc = ((Director) getDir()).getDocument();
        final int numberOfSamples = doc.getNumberOfSamples();
        final String[] sampleNames = doc.getSampleNamesAsArray();
        final String fileName = Basic.replaceFileSuffix(doc.getMeganFile().getFileName(), "-" + rank + "-projection.megan");
        final SampleAttributeTable sampleAttributeTable = doc.getSampleAttributeTable().copy();

        final Map<Integer, Integer[]> taxonMap = NaiveProjectionProfile.compute((ClassificationViewer) getViewer(), rank, minPercent);
        final Map<Integer, Integer[]>[] sample2taxonMap = sortBySample(numberOfSamples, taxonMap);

        final Director newDir;
        final Document newDocument;
        if (ProgramProperties.isUseGUI()) {
            newDir = Director.newProject(false);
            newDir.getMainViewer().setDoReInduce(true);
            newDir.getMainViewer().setDoReset(true);
            newDocument = newDir.getDocument();
        } else {
            newDir = (Director) getDir();
            newDocument = doc;
            newDocument.clearReads();
            newDocument.getSampleAttributeTable().clear();
        }
        newDocument.getMeganFile().setFile(fileName, MeganFile.Type.MEGAN_SUMMARY_FILE);

        for (int i = 0; i < numberOfSamples; i++) {
            Map<String, Map<Integer, Integer[]>> classification2class2counts = new HashMap<>();
            classification2class2counts.put(ClassificationType.Taxonomy.toString(), sample2taxonMap[i]);
            int sampleSize = computeSize(sample2taxonMap[i]);
            newDocument.addSample(sampleNames[i], sampleSize, 0, BlastMode.Unknown, classification2class2counts);
        }

        newDocument.setNumberReads(newDocument.getDataTable().getTotalReads());
        System.err.println("Number of reads: " + newDocument.getNumberOfReads());
        newDocument.processReadHits();
        newDocument.setTopPercent(100);
        newDocument.setMinScore(0);
        newDocument.setMaxExpected(10000);
        newDocument.setMinSupportPercent(0);
        newDocument.setMinSupport(1);
        newDocument.setDirty(true);
        newDocument.getDataTable().setParameters(doc.getDataTable().getParameters());
        newDocument.getActiveViewers().addAll(newDocument.getDataTable().getClassification2Class2Counts().keySet());
        newDocument.getSampleAttributeTable().addTable(sampleAttributeTable, true, true);

        NotificationsInSwing.showInformation(String.format("Computed taxonomic profile for %,d reads", newDocument.getNumberOfReads()));

        if (ProgramProperties.isUseGUI() && newDocument.getNumberOfReads() == 0) {
            newDocument.setDirty(false);
            newDir.close();
        } else {
            newDir.getMainViewer().getFrame().setVisible(true);
            if (newDocument.getNumberOfSamples() > 1) {
                newDir.getMainViewer().getNodeDrawer().setStyle(ProgramProperties.get(MeganProperties.COMPARISON_STYLE, ""), NodeDrawer.Style.PieChart);
            }
            newDir.execute("update reprocess=true reInduce=true;collapse rank=" + rank + ";", newDir.getMainViewer().getCommandManager());
        }
    }

    /**
     * compute the size of the classification
     *
     * @param integerMap
     * @return size
     */
    private int computeSize(Map<Integer, Integer[]> integerMap) {
        int count = 0;
        for (Integer taxonId : integerMap.keySet()) {
            Integer value = integerMap.get(taxonId)[0];
            if (value != null)
                count += value;
        }
        return count;
    }

    /**
     * split into single sample tables
     *
     * @param numberOfSamples
     * @param taxonMap
     * @return single sample tables
     */
    private Map<Integer, Integer[]>[] sortBySample(int numberOfSamples, Map<Integer, Integer[]> taxonMap) {
        Map<Integer, Integer[]>[] sample2TaxonMap = new HashMap[numberOfSamples];
        for (int i = 0; i < numberOfSamples; i++) {
            sample2TaxonMap[i] = new HashMap<>();
        }
        for (Integer taxId : taxonMap.keySet()) {
            Integer[] counts = taxonMap.get(taxId);
            for (int i = 0; i < numberOfSamples; i++) {
                sample2TaxonMap[i].put(taxId, new Integer[]{counts[i]});

            }
        }
        return sample2TaxonMap;
    }

    public void actionPerformed(ActionEvent event) {
        final String[] ranks = TaxonomicLevels.getAllMajorRanks().toArray(new String[TaxonomicLevels.getAllMajorRanks().size()]);

        PopupChoice<String> popupChoice = new PopupChoice<>(ranks, null, new CallBack<String>() {
            @Override
            public void call(String choice) {
                execute("collapse rank='" + choice + "';project rank='" + choice + "' minPercent=0;");

            }
        });
        popupChoice.showAtCurrentMouseLocation(getViewer().getFrame());

    }

    public boolean isApplicable() {
        return ClassificationManager.isTaxonomy(getViewer().getClassName()) && ((Director) getDir()).getDocument().getNumberOfReads() > 0;
    }

    public String getName() {
        return "Project Assignments To Rank...";
    }

    public String getDescription() {
        return "Projects all taxonomic assignments onto a given rank";
    }

    public ImageIcon getIcon() {
        return ResourceManager.getIcon("sun/toolbarButtonGraphics/general/New16.gif");
    }

    public boolean isCritical() {
        return true;
    }

    @Override
    public KeyStroke getAcceleratorKey() {
        return KeyStroke.getKeyStroke(KeyEvent.VK_R, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
    }
}