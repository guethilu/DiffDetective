package datasets;

import datasets.custom.MarlinCPPDiffLineFormulaExtractor;
import diff.DiffFilter;
import diff.difftree.DiffNode;
import diff.difftree.parse.DiffNodeParser;
import feature.CPPAnnotationParser;
import feature.PropositionalFormulaParser;
import org.eclipse.jgit.diff.DiffEntry;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * A collection of default repository datasets.
 *
 * @author Kevin Jedelhauser, Paul Maximilian Bittner
 */
public final class DefaultRepositories {
    private static final Path DIFFDETECTIVE_DEFAULT_REPOSITORIES_DIRECTORY = Path.of("repositories");

    /// Marlin
    public static final DiffNodeParser MARLIN_ANNOTATION_PARSER = new DiffNodeParser(
            new CPPAnnotationParser(PropositionalFormulaParser.Default, new MarlinCPPDiffLineFormulaExtractor())
    );
    public static final DiffFilter STANCIULESCU_MARLIN_DIFF_FILTER = new DiffFilter.Builder()
            //.allowBinary(false)
            .allowMerge(false)
            .allowedPaths("Marlin.*")
            .blockedPaths(".*arduino.*")
            .allowedChangeTypes(DiffEntry.ChangeType.MODIFY)
            .allowedFileExtensions("c", "cpp", "h", "pde")
            .build();

    /// Linux
    public static final Predicate<DiffNode> LINUX_FEATURE_EXPRESSION_FILTER =
            node -> node.getLabel().contains("CONFIG_") ;
    public static final DiffFilter LINUX_DIFF_FILTER = new DiffFilter.Builder()
            .allowMerge(false)
//            .blockedPaths(DiffEntry.DEV_NULL) // <- I'm with stupid.
            .allowedChangeTypes(DiffEntry.ChangeType.MODIFY)
            .allowedFileExtensions("c", "h")
            .build();

    /// Vim
    public static final DiffFilter VIM_DIFF_FILTER = new DiffFilter.Builder()
            .allowMerge(false)
            .allowedChangeTypes(DiffEntry.ChangeType.MODIFY)
            .allowedFileExtensions("c", "h", "cpp")
            .build();

    private DefaultRepositories() {}

    /**
     * Instance for the default predefined Marlin repository.
     * @return Marlin repository
     */
    public static Repository stanciulescuMarlinZip(Path pathToDiffDetective) {
        final Path marlinPath = pathToDiffDetective
                .resolve(DIFFDETECTIVE_DEFAULT_REPOSITORIES_DIRECTORY)
                .resolve("Marlin_old.zip");
        return Repository
                .fromZip(marlinPath, "Marlin_old")
                .setDiffFilter(STANCIULESCU_MARLIN_DIFF_FILTER)
                .setParseOptions(new ParseOptions(MARLIN_ANNOTATION_PARSER));
    }

    /**
     * Clones Linux from Github.
     * @param localPath Path to clone linux to.
     * @return Linux repository
     */
    public static Repository createRemoteLinuxRepo(Path localPath) {
        return Repository
                .tryFromRemote(localPath, "https://github.com/torvalds/linux", "Linux")
                .orElseThrow()
                .setDiffFilter(LINUX_DIFF_FILTER)
                .setFeatureAnnotationFilter(LINUX_FEATURE_EXPRESSION_FILTER);
    }

    /**
     * Clones Busybox.
     * @param localPath Path to clone the repository to.
     * @return Busybox repository
     */
    public static Repository createRemoteBusyboxRepo(Path localPath) {
        return Repository
                .tryFromRemote(localPath, "https://git.busybox.net/busybox", "Busybox")
                .orElseThrow()
                .setDiffFilter(VIM_DIFF_FILTER);
    }

    /**
     * Clones Vim from Github.
     * @param localPath Path to clone the repository to.
     * @return Vim repository
     */
    public static Repository createRemoteVimRepo(Path localPath) {
        return Repository
                .tryFromRemote(localPath, "https://github.com/vim/vim", "Vim")
                .orElseThrow();
    }

    /**
     * Clones libssh from Gitlab.
     * @param localPath Path to clone the repository to.
     * @return libssh repository
     */
    public static Repository createRemoteLibsshRepo(Path localPath) {
        return Repository
                .tryFromRemote(localPath, "https://gitlab.com/libssh/libssh-mirror", "libssh")
                .orElseThrow();
    }
}
