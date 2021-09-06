package util;

import de.ovgu.featureide.fm.core.analysis.cnf.generator.configuration.util.Pair;
import diff.data.CommitDiff;
import diff.data.DiffTree;
import diff.data.PatchDiff;
import diff.data.transformation.CollapseNonEditedSubtrees;
import diff.data.transformation.DiffTreeTransformer;
import org.pmw.tinylog.Logger;

import java.util.ArrayList;
import java.util.List;

public class LineGraphExport {
    public static final String TREE_NAME_SEPARATOR = "$$$";

    public enum NodePrintStyle {
        Type, Pretty, Verbose
    }
    public static record Options(
            NodePrintStyle nodePrintStyle,
            boolean skipEmptyTrees,
            List<DiffTreeTransformer> treePreProcessing) {
        public Options(NodePrintStyle nodePrintStyle) {
            this(nodePrintStyle, false, new ArrayList<>());
        }
    }

    public static Pair<DebugData, String> toLineGraphFormat(final DiffTree diffTree, final Options options) {
        for (final DiffTreeTransformer preprocessing : options.treePreProcessing()) {
            preprocessing.transform(diffTree);
        }

        if (options.skipEmptyTrees && diffTree.isEmpty()) {
            return new Pair<>(new DebugData(), "");
        }

        final DiffTreeLineGraphExporter exporter = new DiffTreeLineGraphExporter(diffTree);
        final String result = exporter.export(options);
        return new Pair<>(exporter.getDebugData(), result);
    }

    /**
     * Writes the given commitDiff in line graph format to the given StringBuilder.
     * @param commitDiff The diff to convert to line graph format.
     * @param lineGraph The string builder to write the result to.
     * @param treeCounter The number of the first diff tree to export.
     * @return The number of the next diff tree to export (updated value of treeCounter).
     */
    public static Pair<DebugData, Integer> toLineGraphFormat(final CommitDiff commitDiff, final StringBuilder lineGraph, int treeCounter, final Options options) {
        final DebugData debugData = new DebugData();

        final String hash = commitDiff.getCommitHash();
        for (final PatchDiff patchDiff : commitDiff.getPatchDiffs()) {
            if (patchDiff.isValid()) {
                //Logger.info("  Exporting DiffTree #" + treeCounter);
                final Pair<DebugData, String> patchDiffLg = toLineGraphFormat(patchDiff.getDiffTree(), options);
                debugData.mappend(patchDiffLg.getKey());

                if (!patchDiffLg.getValue().isEmpty()) {
                    lineGraph
//                        .append("t # ").append(treeCounter)
                            .append("t # ").append(patchDiff.getFileName()).append(TREE_NAME_SEPARATOR).append(hash)
                            .append(StringUtils.LINEBREAK)
                            .append(patchDiffLg.getValue())
                            .append(StringUtils.LINEBREAK)
                            .append(StringUtils.LINEBREAK);

                    ++treeCounter;
                }
            } else {
                Logger.info("  Skipping invalid patch for file " + patchDiff.getFileName() + " at commit " + hash);
            }
        }

        return new Pair<>(debugData, treeCounter);
    }
}
