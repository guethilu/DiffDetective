package org.variantsync.diffdetective.analysis.strategies;

import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.diffdetective.diff.CommitDiff;
import org.variantsync.diffdetective.diff.difftree.serialize.DiffTreeLineGraphExportOptions;
import org.variantsync.diffdetective.util.IO;

import java.nio.file.Path;

public class AnalyzeAllThenExport extends AnalysisStrategy {
    private StringBuilder waitForAll;

    @Override
    public void start(Repository repo, Path outputPath, DiffTreeLineGraphExportOptions options) {
        super.start(repo, outputPath, options);
        waitForAll = new StringBuilder();
    }

    @Override
    public void onCommit(CommitDiff commit, String lineGraph) {
        waitForAll.append(lineGraph);
    }

    @Override
    public void end() {
        final String lineGraph = waitForAll.toString();
//        Logger.info("Writing file " + outputPath);
        IO.tryWrite(outputPath, lineGraph);
    }
}