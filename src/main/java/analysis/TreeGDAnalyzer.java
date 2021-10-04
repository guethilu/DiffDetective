package analysis;

import analysis.data.PatchDiffAnalysisResult;
import analysis.data.PatternMatch;
import diff.GitDiff;
import diff.PatchDiff;
import diff.difftree.DiffNode;
import diff.difftree.DiffTree;
import pattern.EditPattern;
import pattern.atomic.*;
import pattern.semantic.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of the GDAnalyzer using the diff tree.
 *
 * Matches atomic patterns on the code nodes and semantic patterns on the annotation nodes.
 */
public class TreeGDAnalyzer extends GDAnalyzer<DiffNode> {
    public static final EditPattern<DiffNode>[] ATOMIC_PATTERNS = new AtomicPattern[]{
            new AddWithMappingAtomicPattern(),
            new RemWithMapping(),
            new AddToPCAtomicPattern(),
            new RemFromPCAtomicPattern(),
            new WrapCodeAtomicPattern(),
            new UnwrapCodeAtomicPattern(),
            new ChangePCAtomicPattern(),
    };

    public static final EditPattern<DiffNode>[] SEMANTIC_PATTERNS = new SemanticPattern[]{
            new AddIfdefElseSemanticPattern(),
            new AddIfdefElifSemanticPattern(),
            new AddIfdefWrapElseSemanticPattern(),
            new AddIfdefWrapThenSemanticPattern(),
            new MoveElseSemanticPattern(),
    };

    @SuppressWarnings("unchecked")
    private static EditPattern<DiffNode>[] getPatterns(boolean atomic, boolean semantic){
        if(atomic && semantic){
            EditPattern<DiffNode>[] patterns = new EditPattern[ATOMIC_PATTERNS.length + SEMANTIC_PATTERNS.length];
            System.arraycopy(ATOMIC_PATTERNS, 0, patterns, 0, ATOMIC_PATTERNS.length);
            System.arraycopy(SEMANTIC_PATTERNS, 0, patterns, ATOMIC_PATTERNS.length,
                    SEMANTIC_PATTERNS.length);
            return patterns;
        }else if(atomic){
            return ATOMIC_PATTERNS;
        }else if(semantic){
            return SEMANTIC_PATTERNS;
        }
        return new EditPattern[0];
    }

    public TreeGDAnalyzer(GitDiff gitDiff, EditPattern<DiffNode>[] patterns) {
        super(gitDiff, patterns);
    }

    public TreeGDAnalyzer(GitDiff gitDiff, boolean atomic, boolean semantic) {
        this(gitDiff, getPatterns(atomic, semantic));
    }

    public TreeGDAnalyzer(GitDiff gitDiff) {
        this(gitDiff, true, true);
    }

    /**
     * Analyzes a patch using the given patterns.
     * Atomic patterns are matched on the code nodes. Semantic patterns are matched on the annotation nodes
     * @param patchDiff The PatchDiff that is analyzed
     * @return The result of the analysis
     */
    @Override
    protected PatchDiffAnalysisResult analyzePatch(PatchDiff patchDiff) {
        List<PatternMatch<DiffNode>> results = new ArrayList<>();

        DiffTree diffTree = patchDiff.getDiffTree();
        if(diffTree != null) {
            // match atomic patterns
            for (DiffNode diffNode : diffTree.computeCodeNodes()) {
                for (EditPattern<DiffNode> pattern : patterns) {
                    if(pattern instanceof AtomicPattern) {
                        pattern.match(diffNode).ifPresent(results::add);
                    }
                }
            }

            // match semantic patterns
            for (DiffNode diffNode : diffTree.computeAnnotationNodes()) {
                for (EditPattern<DiffNode> pattern : patterns) {
                    if(pattern instanceof SemanticPattern) {
                        pattern.match(diffNode).ifPresent(results::add);
                    }
                }
            }
        }else{
            results.add(new PatternMatch<>(patterns[0]));
        }
        PatchDiffAnalysisResult patchResult = new PatchDiffAnalysisResult(patchDiff);
        patchResult.addPatternMatches(results);
        return patchResult;
    }
}