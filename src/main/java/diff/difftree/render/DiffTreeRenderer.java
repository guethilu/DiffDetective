package diff.difftree.render;

import de.variantsync.functjonal.Product;
import diff.GitPatch;
import diff.PatchDiff;
import diff.difftree.DiffTree;
import diff.difftree.LineGraphConstants;
import diff.difftree.serialize.DiffTreeLineGraphExportOptions;
import diff.difftree.serialize.DiffTreeSerializeDebugData;
import diff.difftree.serialize.LineGraphExport;
import org.tinylog.Logger;
import shell.PythonCommand;
import shell.ShellException;
import shell.ShellExecutor;
import util.Assert;
import util.IO;
import util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public class DiffTreeRenderer {
    private static final Path DiffDetectiveRenderScriptPath = Path.of("linegraph", "renderLinegraph.py");
    private static final Path DiffDetectiveWorkDir = null;
    private static final Supplier<PythonCommand> DiffDetectivePythonCommand
            = () -> PythonCommand.DiffDetectiveVenvPython3(DiffDetectiveRenderScriptPath);

    private final Path workDir;
    private final Supplier<PythonCommand> pythonCommandFactory;

    private DiffTreeRenderer(final Supplier<PythonCommand> pythonCommandFactory, final Path workDir) {
        this.workDir = workDir;
        this.pythonCommandFactory = pythonCommandFactory;
    }

    public static DiffTreeRenderer WithinDiffDetective() {
        return new DiffTreeRenderer(DiffDetectivePythonCommand, DiffDetectiveWorkDir);
    }

    /**
     * Creates a renderer that operates from a third party application (i.e., a program that uses DiffDetective as a library).
     * @param pythonCommandFactory A factory to create a python command that renders the given path.
     *                             The factory thus specifies the python instance to run and the location of the render script.
     *                             DiffDetective comes with a render script in linegraph/renderLinegraph.py.
     *                             However, when invoking this method from a third party application, the location of this script is unknown.
     *                             Thus, pythonCommandFactory has to locate this script and create a PythonCommand running it.
     *                             Assume r is an absolute path to renderLinegraph.py on your file system and you want to use python3.
     *                             Then a possible a value for pythonCommandFactory would be: () -> PythonCommand.Python3(r);
     * @param workDir Working directory, to run the rendering in.
     * @return A renderer that uses the given python instance and render script to render diff trees.
     */
    public static DiffTreeRenderer FromThirdPartyApplication(final Supplier<PythonCommand> pythonCommandFactory, final Path workDir) {
        return new DiffTreeRenderer(pythonCommandFactory, workDir);
    }

    public static DiffTreeRenderer FromThirdPartyApplication(final Path relativePathFromWorkDirToDiffDetectiveSources, final Path workDir) {
        return FromThirdPartyApplication(
                () -> new PythonCommand(
                        relativePathFromWorkDirToDiffDetectiveSources.resolve(PythonCommand.DiffDetectiveVenv).toString(),
                        relativePathFromWorkDirToDiffDetectiveSources.resolve(DiffDetectiveRenderScriptPath)),
                workDir);
    }

    public boolean render(PatchDiff patchDiff, final Path directory) {
        return render(patchDiff, directory, RenderOptions.DEFAULT);
    }

    public boolean render(PatchDiff patchDiff, final Path directory, final RenderOptions options) {
        return render(patchDiff.getDiffTree(), patchDiff, directory, options);
    }

    public boolean render(final DiffTree tree, final GitPatch patch, final Path directory, final RenderOptions options) {
        final String treeAndFileName =
                patch.getFileName()
                        + LineGraphConstants.TREE_NAME_SEPARATOR
                        + patch.getCommitHash();
        return render(tree, treeAndFileName, directory, options);
    }

    public boolean render(final DiffTree tree, final String treeAndFileName, final Path directory) {
        return render(tree, treeAndFileName, directory, RenderOptions.DEFAULT);
    }

    public boolean render(final DiffTree tree, final String treeAndFileName, final Path directory, RenderOptions options) {
        final DiffTreeLineGraphExportOptions lgoptions = new DiffTreeLineGraphExportOptions(options.format(), options.treeFormat(), options.nodeFormat(), options.edgeFormat());

        final Path tempFile = directory.resolve(treeAndFileName + ".lg");

        final Product<DiffTreeSerializeDebugData, String> result = LineGraphExport.toLineGraphFormat(tree, lgoptions);
        Assert.assertNotNull(result);
        final String lg = "t # " + treeAndFileName + LineGraphConstants.TREE_NAME_SEPARATOR + "0" + StringUtils.LINEBREAK + result.second();
        try {
            IO.write(tempFile, lg);
        } catch (IOException e) {
            Logger.error("Could not render difftree " + treeAndFileName + " because:", e);
            return false;
        }

        if (renderFile(tempFile, options) && options.cleanUpTemporaryFiles()) {
            try {
                Files.delete(tempFile);
            } catch (IOException e) {
                Logger.error("Could not remove generated temp file " + tempFile + " because:", e);
            }
        }

        return false;
    }

    public boolean renderFile(final Path lineGraphFile) {
        return renderFile(lineGraphFile, RenderOptions.DEFAULT);
    }

    public boolean renderFile(final Path lineGraphFile, RenderOptions options) {
        final PythonCommand cmd = pythonCommandFactory.get();//apply(lineGraphFile);

        cmd.addArg("--nodesize").addArg(options.nodesize());
        cmd.addArg("--dpi").addArg(options.dpi());
        cmd.addArg("--edgesize").addArg(options.edgesize());
        cmd.addArg("--arrowsize").addArg(options.arrowsize());
        cmd.addArg("--fontsize").addArg(options.fontsize());
        if (!options.withlabels()) {
            cmd.addArg("--nolabels");
        }
        for (final String arg : options.extraArguments()) {
            cmd.addArg(arg);
        }
        cmd.addArg(lineGraphFile.toString());

        final ShellExecutor runner = new ShellExecutor(
                m -> System.out.println("  [RENDER] " + m),
                m -> System.err.println("  [RENDER] " + m)
        );

        try {
            Logger.debug("Running command " + cmd + (workDir != null ? "in " + workDir : ""));
            runner.execute(cmd, workDir);
        } catch (ShellException e) {
            Logger.error("Could not render linegraph file " + lineGraphFile + " because:", e);
            return false;
        }

        return true;
    }
}
