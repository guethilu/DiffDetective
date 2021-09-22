package diff.difftree.transform;

import diff.difftree.CodeType;
import diff.difftree.DiffNode;
import diff.difftree.DiffTree;
import diff.difftree.DiffType;
import diff.difftree.traverse.DiffTreeTraversal;
import org.prop4j.And;
import org.prop4j.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;

/**
 * https://scryfall.com/card/2xm/308/wurmcoil-engine
 */
public class CollapseNestedNonEditedMacros implements DiffTreeTransformer {
    private final List<Stack<DiffNode>> chainCandidates = new ArrayList<>();
    private final List<Stack<DiffNode>> chains = new ArrayList<>();

    @Override
    public List<Class<? extends DiffTreeTransformer>> getDependencies() {
        return List.of(CutNonEditedSubtrees.class);
    }

    @Override
    public void transform(final DiffTree diffTree) {
        // find all chains
        diffTree.traverse(this::findChains);

        // Ignore unfinished chainCandidates.
        // For all these chains, no end was found, so they should not be extracted.
        chainCandidates.clear();

        // All found chains should at least have size 2.
        for (final Stack<DiffNode> chain : chains) {
            assert chain.size() >= 2;
        }

        // collapse all found chains
        for (final Stack<DiffNode> chain : chains) {
            collapseChain(chain);
        }

        // cleanup
        chains.clear();
    }

    private void finalize(Stack<DiffNode> chain) {
        chainCandidates.remove(chain);
        chains.add(chain);
    }

    private void findChains(DiffTreeTraversal traversal, DiffNode subtree) {
        if (subtree.isNon() && subtree.isMacro()) {
            if (isHead(subtree)) {
                final Stack<DiffNode> s = new Stack<>();
                s.push(subtree);
                chainCandidates.add(s);
            } else if (inChainTail(subtree)) {
                final DiffNode parent = subtree.getBeforeParent(); // == after parent

                Stack<DiffNode> pushedTo = null;
                for (final Stack<DiffNode> s : chainCandidates) {
                    if (s.peek() == parent) {
                        s.push(subtree);
                        pushedTo = s;
                        break;
                    }
                }

                if (pushedTo != null && isEnd(subtree)) {
                    finalize(pushedTo);
                }
            }
        }

        traversal.visitChildrenOf(subtree);
    }

    private static void collapseChain(Stack<DiffNode> chain) {
        final Collection<DiffNode> children = chain.peek().removeChildren();
        final ArrayList<Node> featureMappings = new ArrayList<>(chain.size());

        DiffNode lastPopped = null;
        assert !chain.isEmpty();
        while (!chain.isEmpty()) {
            lastPopped = chain.pop();

            switch (lastPopped.codeType) {
                case IF ->
                    featureMappings.add(lastPopped.getAfterFeatureMapping());
                case ELSE, ELIF -> {
                    featureMappings.add(lastPopped.getAfterFeatureMapping());
                    // Pop all previous ELIF cases and the final IF (if present) as we accounted
                    // for their features mappings already.
                    while (!lastPopped.isIf() && !chain.isEmpty()) {
                        lastPopped = chain.pop();
                    }
                }
                case ROOT, CODE ->
                    throw new RuntimeException("Unexpected code type " + lastPopped.codeType + " within macro chain!");
                case ENDIF -> {}
            }
        }

        final DiffNode beforeParent = lastPopped.getBeforeParent();
        final DiffNode afterParent = lastPopped.getAfterParent();

        final DiffNode merged = new DiffNode(
                DiffType.NON, CodeType.IF,
                lastPopped.getFromLine(), lastPopped.getToLine(),
                new And(featureMappings.toArray()),
                "$Collapsed Nested Annotations$");

        lastPopped.drop();
        merged.addChildren(children);
        merged.addBelow(beforeParent, afterParent);
    }

    /**
     * @return True iff at least one child of was edited.
     */
    private static boolean anyChildEdited(DiffNode d) {
        return d.getChildren().stream().anyMatch(c -> !c.isNon());
    }

    /**
     * @return True iff no child of was edited.
     */
    private static boolean noChildEdited(DiffNode d) {
        return d.getChildren().stream().allMatch(DiffNode::isNon);
    }

    private static boolean hasExactlyOneChild(DiffNode d) {
        return d.getChildren().size() == 1;
    }

    /**
     * @return True iff d is in the tail of a chain.
     */
    private static boolean inChainTail(DiffNode d) {
        return d.getBeforeParent() == d.getAfterParent() && hasExactlyOneChild(d.getBeforeParent());
    }

    /**
     * @return True iff d is the head of a chain.
     */
    private static boolean isHead(DiffNode d) {
        return (!inChainTail(d) || d.getBeforeParent().isRoot()) && !isEnd(d);
    }

    /**
     * @return True iff d is the end of a chain and any chain ending at d has to end.
     */
    private static boolean isEnd(DiffNode d) {
        return inChainTail(d) && (anyChildEdited(d) || !hasExactlyOneChild(d));
    }

    @Override
    public String toString() {
        return "CollapseNestedNonEditedMacros";
    }
}
