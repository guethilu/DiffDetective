package org.variantsync.diffdetective.diff.difftree.parse;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.variantsync.diffdetective.datasets.Repository;
import org.variantsync.diffdetective.diff.CommitDiff;
import org.variantsync.diffdetective.diff.DiffLineNumber;
import org.variantsync.diffdetective.diff.GitDiffer;
import org.variantsync.diffdetective.diff.PatchDiff;
import org.variantsync.diffdetective.diff.difftree.DiffNode;
import org.variantsync.diffdetective.diff.difftree.DiffTree;
import org.variantsync.diffdetective.diff.difftree.DiffType;
import org.variantsync.diffdetective.diff.result.DiffError;
import org.variantsync.diffdetective.diff.result.DiffResult;
import org.variantsync.diffdetective.util.Assert;
import org.variantsync.diffdetective.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class DiffTreeParser {
    /**
     * Implementation of the diff tree algorithm.
     * This implementation has options to collapse multiple code lines into one node and to
     * discard empty lines.
     * The implementation also checks for faulty git diffs.
     *
     * @param fullDiff                  The full diff of a patch
     * @param collapseMultipleCodeLines Whether multiple consecutive code lines should be
     *                                  collapsed into a single code node
     * @param ignoreEmptyLines          Whether empty lines (no matter if they are added removed
     *                                  or remained unchanged) should be ignored
     * @return The DiffTree created from the given git diff
     */
    public static DiffResult<DiffTree> createDiffTree(
            String fullDiff,
            boolean collapseMultipleCodeLines,
            boolean ignoreEmptyLines,
            DiffNodeParser nodeParser)
    {
        try {
            return createDiffTree(new BufferedReader(new StringReader(fullDiff)), collapseMultipleCodeLines, ignoreEmptyLines, nodeParser);
        } catch (IOException e) {
            throw new AssertionError("No actual IO should be performed, because only a StringReader is used");
        }
    }

    public static DiffResult<DiffTree> createDiffTree(
            BufferedReader fullDiff,
            boolean collapseMultipleCodeLines,
            boolean ignoreEmptyLines,
            DiffNodeParser nodeParser) throws IOException
    {
        final List<DiffNode> nodes = new ArrayList<>();
        final Stack<DiffNode> beforeStack = new Stack<>();
        final Stack<DiffNode> afterStack = new Stack<>();
        final DiffLineNumber lineNo = new DiffLineNumber(0, 0, 0);
        final DiffLineNumber lastLineNo = DiffLineNumber.Copy(lineNo);

        DiffNode lastCode = null;
        final AtomicReference<DiffResult<DiffTree>> error = new AtomicReference<>();
        final BiConsumer<DiffError, String> errorPropagation = (errType, message) -> {
            if (error.get() == null) {
                error.set(DiffResult.Failure(errType, message));
            }
        };

        final MultiLineMacroParser mlMacroParser = new MultiLineMacroParser(nodeParser);

        final DiffNode root = DiffNode.createRoot();
        beforeStack.push(root);
        afterStack.push(root);

        String currentLine;
        for (int i = 0; (currentLine = fullDiff.readLine()) != null; i++) {
            final DiffType diffType = DiffType.ofDiffLine(currentLine);

            // count line numbers
            lastLineNo.set(lineNo);
            lineNo.inDiff = i + 1;
            diffType.matchBeforeAfter(() -> ++lineNo.beforeEdit, () -> ++lineNo.afterEdit);

            // Ignore line if it is empty.
            if (ignoreEmptyLines && (currentLine.isEmpty()
                    // substring(1) here because of diff symbol ('+', '-', ' ') at the beginning of a line.
                    || currentLine.substring(1).isBlank())) {
                // discard empty lines
                continue;
            }

            // check if this is a multiline macro
            final ParseResult isMLMacro;
            try {
                isMLMacro = mlMacroParser.consume(lineNo, currentLine, beforeStack, afterStack, nodes);
            } catch (IllFormedAnnotationException e) {
                return DiffResult.Failure(e);
            }

            switch (isMLMacro.type()) {
                case Success: {
                    if (lastCode != null) {
                        lastCode = endCodeBlock(lastCode, lastLineNo);
                    }
                    // This line belongs to a multiline macro and was handled, so go to the next line.
                    continue;
                }
                case Error: {
                    isMLMacro.onError(errorPropagation);
                    return error.get();
                }
                // line is not a mult-line macro so keep going (break the switch statement).
                case NotMyDuty: break;
            }

            // This gets the code type and diff type of the current line and creates a node
            // Note that the node is not yet added to the diff tree.
            final DiffNode newNode;
            try {
                newNode = nodeParser.fromDiffLine(currentLine);
            } catch (IllFormedAnnotationException e) {
                return DiffResult.Failure(e);
            }

            // collapse multiple code lines
            if (lastCode != null) {
                if (collapseMultipleCodeLines && newNode.isCode() && lastCode.diffType.equals(newNode.diffType)) {
                    lastCode.addLines(newNode.getLines());
                    continue;
                } else {
                    lastCode = endCodeBlock(lastCode, lastLineNo);
                }
            }

            newNode.getFromLine().set(lineNo);
            if (!newNode.isEndif()) {
                newNode.addBelow(beforeStack.peek(), afterStack.peek());
                nodes.add(newNode);
            }

            if (newNode.isCode()) {
                lastCode = newNode;
            } else if (newNode.isEndif()) {
                final String currentLineFinal = currentLine;
                diffType.matchBeforeAfter(beforeStack, afterStack,
                        stack -> {
                            // Set corresponding line of now closed annotation.
                            // The last block is the first one on the stack.
                            endMacroBlock(stack.peek(), lastLineNo, diffType);

                            // Pop the relevant stacks until an IF node is popped. If there were ELSEs or ELIFs between
                            // an IF and an ENDIF, they were placed on the stack and have to be popped now.
                            popIf(stack);

                            if (stack.isEmpty()) {
                                errorPropagation.accept(DiffError.ENDIF_WITHOUT_IF, "ENDIF without IF at line \"" + currentLineFinal + "\"!");
                            }
                        });
                if (error.get() != null) { return error.get(); }
            } else {
                // newNode is if, elif or else
                // push the node to the relevant stacks
                diffType.matchBeforeAfter(beforeStack, afterStack, stack ->
                        pushNodeToStack(newNode, stack, lastLineNo).onError(errorPropagation)
                );
                if (error.get() != null) { return error.get(); }
            }
        }

        if (beforeStack.size() > 1 || afterStack.size() > 1) {
            return DiffResult.Failure(DiffError.NOT_ALL_ANNOTATIONS_CLOSED);
        }

        if (lastCode != null) {
            lastCode = endCodeBlock(lastCode, lineNo);
        }

        endCodeBlock(root, lineNo);

        // Invalidate line numbers according to edits.
        // E.g. if a node was added, it had no line number before the edit.
        for (final DiffNode node : nodes) {
            node.getFromLine().as(node.diffType);
            node.getToLine().as(node.diffType);
        }

        return DiffResult.Success(new DiffTree(root));
    }

    static ParseResult pushNodeToStack(
            final DiffNode newNode,
            final Stack<DiffNode> stack,
            final DiffLineNumber lastLineNo) {
        if (newNode.isElif() || newNode.isElse()) {
            if (stack.size() == 1) {
                return ParseResult.ERROR(DiffError.ELSE_OR_ELIF_WITHOUT_IF);
            }

            if (stack.peek().isElse()) {
                return ParseResult.ERROR(DiffError.ELSE_AFTER_ELSE);
            }

            // set corresponding line of now closed annotation
            endMacroBlock(stack.peek(), lastLineNo, newNode.diffType);
        }

        stack.push(newNode);
        return ParseResult.SUCCESS;
    }

    private static DiffNode endCodeBlock(final DiffNode block, final DiffLineNumber lastLineNo) {
        block.getToLine().set(lastLineNo).add(1);
        return null;
    }

    private static void endMacroBlock(final DiffNode block, final DiffLineNumber lastLineNo, final DiffType diffTypeOfNewBlock) {
        // Add 1 because the end line is exclusive, so we have to point one behind the last line we found.
        final DiffLineNumber to = block.getToLine();
        diffTypeOfNewBlock.matchBeforeAfter(
                () -> to.beforeEdit = lastLineNo.beforeEdit + 1,
                () -> to.afterEdit = lastLineNo.afterEdit + 1);
        // Take the highest value ever set as we want to include all lines that are somehow affected by this block.
        to.inDiff = Math.max(to.inDiff, lastLineNo.inDiff + 1);
    }

    public static DiffNode popIf(final Stack<DiffNode> stack) {
        DiffNode popped;
        do {
            // Don't update line numbers of popped nodes here as this already happened.
            popped = stack.pop();
        } while (!popped.isIf() && !popped.isRoot());
        return popped;
    }

    public static CommitDiff parseCommit(Repository repo, String commitHash) throws IOException {
        final Git git = repo.getGitRepo().run();
        Assert.assertNotNull(git);
        final RevWalk revWalk = new RevWalk(git.getRepository());
        final RevCommit childCommit = revWalk.parseCommit(ObjectId.fromString(commitHash));
        final RevCommit parentCommit = revWalk.parseCommit(childCommit.getParent(0).getId());

        final CommitDiff commitDiff =
                GitDiffer.createCommitDiff(
                                git,
                                repo.getDiffFilter(),
                                parentCommit,
                                childCommit,
                                repo.getParseOptions())
                        .diff().orElseThrow();

        revWalk.close();
        return commitDiff;
    }

    public static PatchDiff parsePatch(Repository repo, String file, String commitHash) throws IOException {
        final CommitDiff commitDiff = parseCommit(repo, commitHash);

        for (final PatchDiff pd : commitDiff.getPatchDiffs()) {
            if (file.equals(pd.getFileName())) {
                return pd;
            }
        }

        Assert.fail("Did not find file \"" + file + "\" in commit " + commitHash + "!");
        return null;
    }
}
