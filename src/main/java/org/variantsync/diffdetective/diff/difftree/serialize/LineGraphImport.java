package org.variantsync.diffdetective.diff.difftree.serialize;

import org.variantsync.diffdetective.diff.difftree.*;
import org.variantsync.diffdetective.util.Assert;
import org.variantsync.diffdetective.util.FileUtils;
import org.variantsync.functjonal.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * Import patches from line graphs.
 *
 * @author Kevin Jedelhauser, Paul Maximilian Bittner
 */
public class LineGraphImport {
    //    public static Map<CodeType, Integer> countRootTypes = new HashMap<>();

    /**
     * Transforms a line graph stored in a file into a list of {@link DiffTree DiffTrees}.
     *
     * @return All {@link DiffTree DiffTrees} contained in the line graph
     */
    public static List<DiffTree> fromFile(final Path path, final DiffTreeLineGraphImportOptions options) throws IOException {
        Assert.assertTrue(Files.isRegularFile(path));
        Assert.assertTrue(FileUtils.isLineGraph(path));
        try (BufferedReader input = Files.newBufferedReader(path)) {
            return fromLineGraph(input, options);
        }
    }
	
	/**
	 * Transforms a line graph into a list of {@link DiffTree DiffTrees}.
	 * 
	 * @return All {@link DiffTree DiffTrees} contained in the line graph
	 */
	public static List<DiffTree> fromLineGraph(final BufferedReader lineGraph, final DiffTreeLineGraphImportOptions options) throws IOException {
		// All DiffTrees read from the line graph
		List<DiffTree> diffTreeList = new ArrayList<>();
		
		// All DiffNodes of one DiffTree for determining the root node
		List<DiffNode> diffNodeList = new ArrayList<>();
		
		// A hash map of DiffNodes
		// <id of DiffNode, DiffNode>
		HashMap<Integer, DiffNode> diffNodes = new HashMap<>();

		// The previously read DiffTree
		String previousDiffTreeLine = "";
		
		// Read the entire line graph 
		String ln;
		while ((ln = lineGraph.readLine()) != null) {
			if (ln.startsWith(LineGraphConstants.LG_TREE_HEADER)) {
				// the line represents a DiffTree
				
				if (!diffNodeList.isEmpty()) {
					DiffTree curDiffTree = parseDiffTree(previousDiffTreeLine, diffNodeList, options); // parse to DiffTree
					diffTreeList.add(curDiffTree); // add newly computed DiffTree to the list of all DiffTrees
					
					// Remove all DiffNodes from list
					diffNodeList.clear();
					diffNodes.clear();	
				} 
				previousDiffTreeLine = ln;
			} else if (ln.startsWith(LineGraphConstants.LG_NODE)) {
				// the line represents a DiffNode
				
				// parse node from input line
				final Pair<Integer, DiffNode> idAndNode = options.nodeFormat().fromLineGraphLine(ln);
			
				// add DiffNode to lists of current DiffTree
				diffNodeList.add(idAndNode.second());
				diffNodes.put(idAndNode.first(), idAndNode.second());
				
			} else if (ln.startsWith(LineGraphConstants.LG_EDGE)) {
				// the line represent a connection with two DiffNodes
				options.edgeFormat().connect(ln, diffNodes);
			} else if (!ln.isBlank()) {
				// ignore blank lines and throw an exception otherwise
				String errorMessage = String.format(
						"Line graph syntax error. Expects: \"%s\" (DiffTree), \"%s\" (DiffNode), \"%s\" (edge) or a blank space (delimiter). Faulty input: \"%s\".", 
						LineGraphConstants.LG_TREE_HEADER,
						LineGraphConstants.LG_NODE,
						LineGraphConstants.LG_EDGE,
						ln);
				throw new IllegalArgumentException(errorMessage);
			}
		}

		if (!diffNodeList.isEmpty()) {
			DiffTree curDiffTree = parseDiffTree(previousDiffTreeLine, diffNodeList, options); // parse to DiffTree
			diffTreeList.add(curDiffTree); // add newly computed DiffTree to the list of all DiffTrees
		}
		
		// return all computed DiffTrees.
		return diffTreeList;
	}
	
	/**
	 * Generates a {@link DiffTree} from given parameters.
	 * 
	 * @param lineGraph The line graph line to be parsed
	 * @param diffNodeList The list of {@link DiffNode DiffNodes}
	 * @param options {@link DiffTreeLineGraphImportOptions}
	 * @return {@link DiffTree}
	 */
	private static DiffTree parseDiffTree(final String lineGraph, final List<DiffNode> diffNodeList, final DiffTreeLineGraphImportOptions options) {
		final DiffTreeSource diffTreeSource = options.treeFormat().fromLineGraphLine(lineGraph);
		// Handle trees and graphs differently
		if (options.graphFormat() == GraphFormat.DIFFGRAPH) {
			// If you should interpret the input data as DiffTrees, always expect a root to be present. Parse all nodes (v) to a list of nodes. Search for the root. Assert that there is exactly one root.
			Assert.assertTrue(diffNodeList.stream().noneMatch(DiffNode::isRoot)); // test if it’s not a tree
			return DiffGraph.fromNodes(diffNodeList, diffTreeSource);
		} else if (options.graphFormat() == GraphFormat.DIFFTREE) {
			// If you should interpret the input data as DiffTrees, always expect a root to be present. Parse all nodes (v) to a list of nodes. Search for the root. Assert that there is exactly one root.
            DiffNode root = null;
            for (final DiffNode v : diffNodeList) {
                if (DiffGraph.hasNoParents(v)) {
                    // v is root candidate
                    if (root != null) {
                        throw new RuntimeException("Not a DiffTree but a DiffGraph: Got more than one root! Got \"" + root + "\" and \"" + v + "\"!");
                    }
                    if (v.codeType == CodeType.IF || v.codeType == CodeType.ROOT) {
                        root = v;
                    } else {
                        throw new RuntimeException("Not a DiffTree but a DiffGraph: The node \"" + v + "\" is not labeled as ROOT or IF but has no parents!");
                    }
                }
            }

            if (root == null) {
                throw new RuntimeException("Not a DiffTree but a DiffGraph: No root found!");
            }

//            countRootTypes.merge(root.codeType, 1, Integer::sum);

			return new DiffTree(root, diffTreeSource);
		} else {
			throw new RuntimeException("Unsupported GraphFormat");
		}
	}
}