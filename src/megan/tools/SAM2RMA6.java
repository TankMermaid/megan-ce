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
package megan.tools;

import jloda.gui.commands.CommandManager;
import jloda.util.*;
import megan.classification.Classification;
import megan.classification.ClassificationManager;
import megan.classification.IdMapper;
import megan.classification.IdParser;
import megan.classification.data.ClassificationCommandHelper;
import megan.core.Document;
import megan.core.SampleAttributeTable;
import megan.main.MeganProperties;
import megan.parsers.blast.BlastFileFormat;
import megan.parsers.blast.BlastMode;
import megan.rma6.RMA6Connector;
import megan.rma6.RMA6FromBlastCreator;
import megan.util.SAMFileFilter;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * compute an RMA6 file from a SAM file generated by DIAMOND or MALT
 * Daniel Huson, 3.2012
 */
public class SAM2RMA6 {
    /**
     * merge RMA files
     *
     * @param args
     * @throws UsageException
     * @throws IOException
     */
    public static void main(String[] args) {
        try {
            ProgramProperties.setProgramName("SAM2RMA6");
            ProgramProperties.setProgramVersion(megan.main.Version.SHORT_DESCRIPTION);

            PeakMemoryUsageMonitor.start();
            (new SAM2RMA6()).run(args);
            System.err.println("Total time:  " + PeakMemoryUsageMonitor.getSecondsSinceStartString());
            System.err.println("Peak memory: " + PeakMemoryUsageMonitor.getPeakUsageString());
            System.exit(0);
        } catch (Exception ex) {
            Basic.caught(ex);
            System.exit(1);
        }
    }

    /**
     * run
     *
     * @param args
     * @throws UsageException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void run(String[] args) throws UsageException, IOException, ClassNotFoundException, CanceledException {
        CommandManager.getGlobalCommands().addAll(ClassificationCommandHelper.getGlobalCommands());

        final ArgsOptions options = new ArgsOptions(args, this, "Computes a MEGAN RMA (.rma) file from a SAM (.sam) file that was created by DIAMOND or MALT");
        options.setVersion(ProgramProperties.getProgramVersion());
        options.setLicense("Copyright (C) 2016 Daniel H. Huson. This program comes with ABSOLUTELY NO WARRANTY.");
        options.setAuthors("Daniel H. Huson");

        options.comment("Input");
        final String[] samFiles = options.getOptionMandatory("-i", "in", "Input SAM file[s] generated by DIAMOND or MALT (gzipped ok)", new String[0]);
        String[] readsFiles = options.getOption("-r", "reads", "Reads file(s) (fasta or fastq, gzipped ok)", new String[0]);
        final String[] metaDataFiles = options.getOption("-mdf", "metaDataFile", "Files containing metadata to be included in RMA6 files", new String[0]);

        options.comment("Output");
        String[] outputFiles = options.getOptionMandatory("-o", "out", "Output file(s), one for each input file, or a directory", new String[0]);
        boolean useCompression = options.getOption("-c", "useCompression", "Compress reads and matches in RMA file (smaller files, longer to generate", true);

        options.comment("Reads");
        final boolean hasMagnitudes = options.getOption("-mag", "magnitudes", "Reads are annotated with magnitudes", false);
        final boolean pairedReads = options.getOption("-p", "paired", "Reads are paired", false);
        final int pairedReadsSuffixLength = options.getOption("-ps", "pairedSuffixLength", "Length of name suffix used to distinguish between name of read and its mate", 0);
        options.comment("Parameters");
        final int maxMatchesPerRead = options.getOption("-m", "maxMatchesPerRead", "Max matches per read", 100);
        final boolean runClassifications = options.getOption("-class", "classify", "Run classification algorithm", true);
        final float minScore = options.getOption("-ms", "minScore", "Min score", Document.DEFAULT_MINSCORE);
        final float maxExpected = options.getOption("-me", "maxExpected", "Max expected", Document.DEFAULT_MAXEXPECTED);
        final float topPercent = options.getOption("-top", "topPercent", "Top percent", Document.DEFAULT_TOPPERCENT);
        final float minSupportPercent = options.getOption("-supp", "minSupportPercent", "Min support as percent of assigned reads (0==off)", Document.DEFAULT_MINSUPPORT_PERCENT);
        final int minSupport = options.getOption("-sup", "minSupport", "Min support", Document.DEFAULT_MINSUPPORT);
        final boolean weightedLCA = options.getOption("-wlca", "weightedLCA", "Use the weighted LCA for taxonomic assignment", Document.DEFAULT_WEIGHTED_LCA);
        final float weightedLCAPercent = (float) options.getOption("-wlp", "weightedLCAPercent", "Set the percent weight to cover", Document.DEFAULT_WEIGHTED_LCA_PERCENT);

        final String[] availableFNames = ClassificationManager.getAllSupportedClassificationsExcludingNCBITaxonomy().toArray(new String[ClassificationManager.getAllSupportedClassificationsExcludingNCBITaxonomy().size()]);
        options.comment("Functional classification:");
        String[] cNames = options.getOption("-fun", "function", "Function assignments (any of " + Basic.toString(availableFNames, " ") + ")", new String[0]);
        for (String cName : cNames) {
            if (!ClassificationManager.getAllSupportedClassifications().contains(cName))
                throw new UsageException("--function: Unknown classification: " + cName);
            if (cName.equals(Classification.Taxonomy))
                throw new UsageException("--function: Illegal argument: 'Taxonomy'");

        }

        options.comment("Classification support:");

        if (options.isDoHelp())
            cNames = availableFNames;

        final boolean parseTaxonNames = options.getOption("-tn", "parseTaxonNames", "Parse taxon names", true);
        final String gi2TaxaFile = options.getOption("-g2t", "gi2taxa", "GI-to-Taxonomy mapping file", "");
        final String acc2TaxaFile = options.getOption("-a2t", "acc2taxa", "Accession-to-Taxonomy mapping file", "");
        final String synonyms2TaxaFile = options.getOption("-s2t", "syn2taxa", "Synonyms-to-Taxonomy mapping file", "");

        String[] gi2FNames = new String[cNames.length];
        String[] acc2FNames = new String[cNames.length];
        String[] synonyms2FNames = new String[cNames.length];

        for (int i1 = 0; i1 < cNames.length; i1++) {
            String cName = cNames[i1];
            gi2FNames[i1] = options.getOption("-g2" + cName.toLowerCase(), "gi2" + cName.toLowerCase(), "GI-to-" + cName + " mapping file", "");
            acc2FNames[i1] = options.getOption("-a2" + cName.toLowerCase(), "acc2" + cName.toLowerCase(), "Accession-to-" + cName + " mapping file", "");
            synonyms2FNames[i1] = options.getOption("-s2" + cName.toLowerCase(), "syn2" + cName.toLowerCase(), "Synonyms-to-" + cName + " mapping file", "");
            // final boolean useLCA = options.getOption("-l_" + cName.toLowerCase(), "lca" + cName.toLowerCase(), "Use LCA for assigning to '" + cName + "', alternative: best hit", ProgramProperties.get(cName + "UseLCA", false));
            // ProgramProperties.put(cName + "UseLCA", useLCA);
        }

        options.comment(ArgsOptions.OTHER);
        ProgramProperties.put(IdParser.PROPERTIES_FIRST_WORD_IS_ACCESSION, options.getOption("-fwa", "firstWordIsAccession", "First word in reference header is accession number (set to 'true' for NCBI-nr downloaded Sep 2016 or later)", true));
        ProgramProperties.put(IdParser.PROPERTIES_ACCESSION_TAGS, options.getOption("-atags", "accessionTags", "List of accession tags", ProgramProperties.get(IdParser.PROPERTIES_ACCESSION_TAGS, IdParser.ACCESSION_TAGS)));
        options.done();

        final String propertiesFile;
        if (ProgramProperties.isMacOS())
            propertiesFile = System.getProperty("user.home") + "/Library/Preferences/Megan.def";
        else
            propertiesFile = System.getProperty("user.home") + File.separator + ".Megan.def";
        MeganProperties.initializeProperties(propertiesFile);

        if (minSupport > 0 && minSupportPercent > 0)
            throw new IOException("Please specify a positive value for either --minSupport or --minSupportPercent, but not for both");

        for (String fileName : samFiles) {
            Basic.checkFileReadableNonEmpty(fileName);
            if (!SAMFileFilter.getInstance().accept(fileName))
                throw new IOException("File not in SAM format: " + fileName);
        }

        for (String fileName : metaDataFiles) {
            Basic.checkFileReadableNonEmpty(fileName);
        }

        for (String fileName : readsFiles) {
            Basic.checkFileReadableNonEmpty(fileName);
        }

        if (outputFiles.length == 1) {
            if (samFiles.length == 1) {
                if ((new File(outputFiles[0]).isDirectory()))
                    outputFiles[0] = (new File(outputFiles[0], Basic.replaceFileSuffix(Basic.getFileNameWithoutPath(Basic.getFileNameWithoutZipOrGZipSuffix(samFiles[0])), ".rma6"))).getPath();
            } else if (samFiles.length > 1) {
                if (!(new File(outputFiles[0]).isDirectory()))
                    throw new IOException("Multiple files given, but given single output is not a directory");
                String outputDirectory = (new File(outputFiles[0])).getParent();
                outputFiles = new String[samFiles.length];

                for (int i = 0; i < samFiles.length; i++)
                    outputFiles[i] = new File(outputDirectory, Basic.replaceFileSuffix(Basic.getFileNameWithoutZipOrGZipSuffix(Basic.getFileNameWithoutPath(samFiles[i])), ".rma6")).getPath();
            }
        } else // output.length >1
        {
            if (samFiles.length != outputFiles.length)
                throw new IOException("Number of input and output files do not match");
        }

        if (metaDataFiles.length > 1 && metaDataFiles.length != samFiles.length) {
            throw new IOException("Number of metadata files (" + metaDataFiles.length + ") doesn't match number of SAM files (" + samFiles.length + ")");
        }

        if (readsFiles.length == 0) {
            readsFiles = new String[samFiles.length];
            for (int i = 0; i < readsFiles.length; i++)
                readsFiles[i] = "";
        } else if (readsFiles.length != samFiles.length)
            throw new IOException("Number of reads files must equal number of SAM files");

        final IdMapper taxonIdMapper = ClassificationManager.get(Classification.Taxonomy, true).getIdMapper();
        final IdMapper[] idMappers = new IdMapper[cNames.length];

        // Load all mapping files:
        if (runClassifications) {
            ClassificationManager.get(Classification.Taxonomy, true);
            taxonIdMapper.setUseTextParsing(parseTaxonNames);

            if (gi2TaxaFile.length() > 0) {
                taxonIdMapper.loadMappingFile(gi2TaxaFile, IdMapper.MapType.GI, false, new ProgressPercentage());
            }
            if (acc2TaxaFile.length() > 0) {
                taxonIdMapper.loadMappingFile(acc2TaxaFile, IdMapper.MapType.Accession, false, new ProgressPercentage());
            }
            if (synonyms2TaxaFile.length() > 0) {
                taxonIdMapper.loadMappingFile(synonyms2TaxaFile, IdMapper.MapType.Synonyms, false, new ProgressPercentage());
            }

            for (int i = 0; i < cNames.length; i++) {
                idMappers[i] = ClassificationManager.get(cNames[i], true).getIdMapper();

                if (gi2FNames[i].length() > 0)
                    idMappers[i].loadMappingFile(gi2FNames[i], IdMapper.MapType.GI, false, new ProgressPercentage());
                if (gi2FNames[i].length() > 0)
                    idMappers[i].loadMappingFile(gi2FNames[i], IdMapper.MapType.GI, false, new ProgressPercentage());
                if (acc2FNames[i].length() > 0)
                    idMappers[i].loadMappingFile(acc2FNames[i], IdMapper.MapType.Accession, false, new ProgressPercentage());
                if (synonyms2FNames[i].length() > 0)
                    idMappers[i].loadMappingFile(synonyms2FNames[i], IdMapper.MapType.Synonyms, false, new ProgressPercentage());
            }
        }

        /**
         * process each set of files:
         */
        for (int i = 0; i < samFiles.length; i++) {
            System.err.println("Current SAM file: " + samFiles[i]);
            if (i < readsFiles.length)
                System.err.println("Reads file:   " + readsFiles[i]);
            System.err.println("Output file:  " + outputFiles[i]);

            ProgressListener progressListener = new ProgressPercentage();

            final Document doc = new Document();
            doc.getActiveViewers().add(Classification.Taxonomy);
            doc.getActiveViewers().addAll(Arrays.asList(cNames));
            doc.setMinScore(minScore);
            doc.setMaxExpected(maxExpected);
            doc.setTopPercent(topPercent);
            doc.setMinSupportPercent(minSupportPercent);
            doc.setMinSupport(minSupport);
            doc.setPairedReads(pairedReads);
            doc.setPairedReadSuffixLength(pairedReadsSuffixLength);
            doc.setBlastMode(BlastMode.determineBlastModeSAMFile(samFiles[i]));
            doc.setWeightedLCA(weightedLCA);
            doc.setWeightedLCAPercent(weightedLCAPercent);

            createRMA6FileFromSAM("SAM2RMA6", samFiles[i], readsFiles[i], outputFiles[i], useCompression, doc, maxMatchesPerRead, hasMagnitudes, progressListener);

            progressListener.close();

            final RMA6Connector connector = new RMA6Connector(outputFiles[i]);
            if (false) {
                System.err.println(String.format("Total reads:   %,15d", connector.getNumberOfReads()));
                System.err.println(String.format("Total matches: %,15d ", connector.getNumberOfMatches()));

                for (String name : connector.getAllClassificationNames()) {
                    System.err.println(String.format("Class. %-13s%,10d", name + ":", connector.getClassificationSize(name)));
                }
            }

            if (metaDataFiles.length > 0) {
                try {
                    System.err.println("Saving metadata:");
                    SampleAttributeTable sampleAttributeTable = new SampleAttributeTable();
                    sampleAttributeTable.read(new FileReader(metaDataFiles[Math.min(i, metaDataFiles.length - 1)]),
                            Collections.singletonList(Basic.getFileBaseName(Basic.getFileNameWithoutPath(outputFiles[i]))), false);
                    Map<String, byte[]> label2data = new HashMap<>();
                    label2data.put(SampleAttributeTable.SAMPLE_ATTRIBUTES, sampleAttributeTable.getBytes());
                    connector.putAuxiliaryData(label2data);
                    System.err.println("done");
                } catch (Exception ex) {
                    Basic.caught(ex);
                }
            }
            progressListener.incrementProgress();
        }
    }

    /**
     * create an RMA6 file from a SAM file (generated by DIAMOND or MALT)
     *
     * @param samFile
     * @param rma6FileName
     * @param maxMatchesPerRead
     * @param progressListener  @throws CanceledException
     */
    public static void createRMA6FileFromSAM(String creator, String samFile, String queryFile, String rma6FileName, boolean useCompression, Document doc,
                                             int maxMatchesPerRead, boolean hasMagnitudes, ProgressListener progressListener) throws IOException, CanceledException {
        final RMA6FromBlastCreator rma6Creator =
                new RMA6FromBlastCreator(creator, BlastFileFormat.SAM, doc.getBlastMode(), new String[]{samFile}, new String[]{queryFile}, rma6FileName, useCompression, doc, maxMatchesPerRead, hasMagnitudes);
        rma6Creator.parseFiles(progressListener);
    }
}
