package org.variantsync.diffdetective.analysis.strategies;

import org.apache.commons.io.output.CloseShieldOutputStream;
import org.tinylog.Logger;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.diffdetective.diff.git.CommitDiff;
import org.variantsync.diffdetective.util.IO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Collects the linegraph representations generated by an analysis and exports them once a certain threshold of
 * representations has been stored.
 * The default value is 100, meaning that a linegraph file is written for every 100 processed commits.
 * @author Paul Bittner
 */
public class AnalyzeAndExportIncrementally extends AnalysisStrategy {
    /**
     * Default value for the amount of commits whose linegraph representations should be exported together.
     */
    public static final int DEFAULT_NUMBER_OF_COMMITS_TO_EXPORT_AT_ONCE = 100;
    private final int commitsToExportAtOnce;

    private ByteArrayOutputStream lineGraphDestination;
    private int collectedCommits;

    /**
     * Creates a new strategy that collects the linegraph representations of the given amount of commits and then
     * exports them together.
     * @param numberOfCommitsToExportAtOnce Amount of commits whose linegraph representations should be exported together.
     */
    public AnalyzeAndExportIncrementally(int numberOfCommitsToExportAtOnce) {
        this.commitsToExportAtOnce = numberOfCommitsToExportAtOnce;
    }

    /**
     * Creates a new strategy with the default value of commits to export together.
     * @see AnalyzeAndExportIncrementally#DEFAULT_NUMBER_OF_COMMITS_TO_EXPORT_AT_ONCE
     * @see AnalyzeAndExportIncrementally#AnalyzeAndExportIncrementally(int)
     */
    public AnalyzeAndExportIncrementally() {
        this(DEFAULT_NUMBER_OF_COMMITS_TO_EXPORT_AT_ONCE);
    }

    @Override
    public void start(Repository repo, Path outputPath) {
        super.start(repo, outputPath);

        IO.tryDeleteFile(outputPath);

        lineGraphDestination = new ByteArrayOutputStream();
        collectedCommits = 0;
    }

    @Override
    public OutputStream onCommit(CommitDiff commit) {
        ++collectedCommits;
        if (collectedCommits > commitsToExportAtOnce) {
            flush();
            collectedCommits = 0;
        }

        // lineGraphDestination is reused for all commits. CloseShieldOutputStream ensures that it
        // isn't closed after processing this commit.
        return new CloseShieldOutputStream(lineGraphDestination);
    }

    @Override
    public void end() {
        flush();
    }

    /**
     * Appends the given linegraph string at the end of the given file.
     * @param outputPath File to which the linegraph string should be appended.
     * @param linegraph String to append to the given file.
     */
    private void flush() {
        try (var output = IO.newBufferedOutputStream(outputPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            lineGraphDestination.writeTo(output);
            lineGraphDestination.reset();
        } catch (IOException exception) {
            Logger.error(exception);
        }
    }
}
