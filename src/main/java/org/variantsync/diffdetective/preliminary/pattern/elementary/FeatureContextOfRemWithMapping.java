package org.variantsync.diffdetective.preliminary.pattern.elementary;

import org.prop4j.Node;
import org.variantsync.diffdetective.diff.LineRange;
import org.variantsync.diffdetective.diff.difftree.DiffNode;
import org.variantsync.diffdetective.preliminary.pattern.Pattern;
import org.variantsync.diffdetective.editclass.proposed.ProposedEditClasses;
import org.variantsync.diffdetective.preliminary.analysis.data.PatternMatch;
import org.variantsync.diffdetective.preliminary.evaluation.FeatureContext;
import org.variantsync.diffdetective.preliminary.pattern.FeatureContextReverseEngineering;

import static org.variantsync.diffdetective.diff.difftree.Time.BEFORE;

@Deprecated
public final class FeatureContextOfRemWithMapping implements FeatureContextReverseEngineering<DiffNode> {
    @Override
    public Pattern<DiffNode> getPattern() {
        return ProposedEditClasses.RemWithMapping;
    }

    @Override
    public PatternMatch<DiffNode> createMatch(DiffNode codeNode) {
        final Node fm = codeNode.getParent(BEFORE).getFeatureMapping(BEFORE);
        final LineRange diffLines = codeNode.getLinesInDiff();

        return new PatternMatch<>(this,
                diffLines.getFromInclusive(), diffLines.getToExclusive(), fm
        );
    }

    @Override
    public FeatureContext[] getFeatureContexts(PatternMatch<DiffNode> patternMatch) {
        return new FeatureContext[]{
                new FeatureContext(null),
                new FeatureContext(patternMatch.getFeatureMappings()[0], true)
        };
    }
}
