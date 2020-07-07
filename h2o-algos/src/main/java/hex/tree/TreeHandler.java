package hex.tree;

import hex.Model;
import hex.ModelCategory;
import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.algos.tree.SharedTreeGraphConverter;
import hex.schemas.TreeV3;
import water.Keyed;
import water.MemoryManager;
import water.api.Handler;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Handling requests for various model trees
 */
public class TreeHandler extends Handler {
    private static final int NO_CHILD = -1;

    public TreeV3 getTree(final int version, final TreeV3 args) {

        if (args.tree_number < 0) {
            throw new IllegalArgumentException("Invalid tree number: " + args.tree_number + ". Tree number must be >= 0.");
        }

        final Keyed possibleModel = args.model.key().get();
        if (possibleModel == null) throw new IllegalArgumentException("Given model does not exist: " + args.model.key().toString());

        else if (!(possibleModel instanceof SharedTreeModel) && !(possibleModel instanceof SharedTreeGraphConverter)) {
            throw new IllegalArgumentException("Given model is not tree-based.");
        }
        final SharedTreeSubgraph sharedTreeSubgraph;

        if (possibleModel instanceof SharedTreeGraphConverter) {
            final SharedTreeGraphConverter treeBackedModel = (SharedTreeGraphConverter) possibleModel;
            final SharedTreeGraph sharedTreeGraph = treeBackedModel.convert(args.tree_number, args.tree_class);
            assert sharedTreeGraph.subgraphArray.size() == 1;
            sharedTreeSubgraph = sharedTreeGraph.subgraphArray.get(0);

            if (! ((Model)possibleModel)._output.isClassifier()) {
                args.tree_class = null; // Class may not be provided by the user, should be always filled correctly on output. NULL for regression.
            }
        } else {
            final SharedTreeModel model = (SharedTreeModel) possibleModel;
            final SharedTreeModel.SharedTreeOutput sharedTreeOutput = (SharedTreeModel.SharedTreeOutput) model._output;
            final int treeClass = getResponseLevelIndex(args.tree_class, sharedTreeOutput);
            sharedTreeSubgraph = model.getSharedTreeSubgraph(args.tree_number, treeClass);
            // Class may not be provided by the user, should be always filled correctly on output. NULL for regression.
            args.tree_class = sharedTreeOutput.isClassifier() ? sharedTreeOutput.classNames()[treeClass] : null;
        }


        final TreeProperties treeProperties = convertSharedTreeSubgraph(sharedTreeSubgraph);

        args.left_children = treeProperties._leftChildren;
        args.right_children = treeProperties._rightChildren;
        args.descriptions = treeProperties._descriptions;
        args.root_node_id = sharedTreeSubgraph.rootNode.getNodeNumber();
        args.thresholds = treeProperties._thresholds;
        args.features = treeProperties._features;
        args.nas = treeProperties._nas;
        args.levels = treeProperties.levels;
        args.predictions = treeProperties._predictions;
        args.language_tree_representation = treeProperties._languageTreeRepresentation;
        args.language_path_representations = treeProperties._languagePathsRepresentations;

        return args;
    }


    private static String getLanguageRepresentation(SharedTreeSubgraph sharedTreeSubgraph) {
        return getNodeRepresentation(sharedTreeSubgraph.rootNode, "", 0);
    }

    private static String getNodeRepresentation(SharedTreeNode node, String languageRepresentation, int padding) {
        if (node.getLeftChild() != null || node.getRightChild() != null) {
            if (padding != 0)
                languageRepresentation += getNewPaddedLine(padding);
            languageRepresentation += getConditionLine(node);
            if (node.getRightChild() != null) {
                languageRepresentation += getNewPaddedLine(padding);
                languageRepresentation = getNodeRepresentation(node.getRightChild(), languageRepresentation, padding + 1);
            }
            languageRepresentation += getNewPaddedLine(padding);
            languageRepresentation += getElseLine(node);
            if (node.getLeftChild() != null) {
                languageRepresentation += getNewPaddedLine(padding);
                languageRepresentation = getNodeRepresentation(node.getLeftChild(), languageRepresentation, padding + 1);
            }
            languageRepresentation += getNewPaddedLine(padding);
            languageRepresentation += "}";
        } else {
            if (Float.compare(node.getPredValue(),Float.NaN) != 0) {
                languageRepresentation += getNewPaddedLine(padding);
                languageRepresentation += "Predicted value: " + node.getPredValue();
                languageRepresentation += getNewPaddedLine(padding);
            }
        }
        return languageRepresentation;
    }

    private static String getNewPaddedLine(int padding) {
        String line = "\n";
        for(int i = 0; i < padding; i++) {
            line += "\t";
        }
        return line;
    }

    private static String getElseLine(SharedTreeNode node) {
        StringBuilder elseLine = new StringBuilder();
        if (Float.compare(node.getSplitValue(),Float.NaN) != 0) {
            elseLine.append("} else {");
        } else {
            SharedTreeNode leftChild = node.getLeftChild();
            elseLine.append("} else if ( ").append(node.getColName()).append(" is in [ ");
            String stringToParseInclusiveLevelsFrom = leftChild.getInclusiveLevels().toString();
            int inclusiveLevelsLength = leftChild.getInclusiveLevels().toString().length();
            if (inclusiveLevelsLength  > 2) {
                // get rid of curly braces:
                stringToParseInclusiveLevelsFrom = stringToParseInclusiveLevelsFrom.substring(1, inclusiveLevelsLength - 1);
                String[] inclusiveLevels = stringToParseInclusiveLevelsFrom.split(",");
                for (String index : inclusiveLevels) {
                    elseLine.append(node.getDomainValues()[Integer.parseInt(index.trim())] + " ");
                }
            } else {
                elseLine.append("Missing set of levels for underlying node");
            }
            elseLine.append("]) {");
        }
        return elseLine.toString();
    }

    private static String getConditionLine(SharedTreeNode node) {
        StringBuilder conditionLine;
        if (Float.compare(node.getSplitValue(),Float.NaN) != 0) {
            conditionLine = new StringBuilder("If ( " + node.getColName() + " <= " + node.getSplitValue());
            if ("RIGHT".equals(getNaDirection(node))) {
                conditionLine.append(" or ").append(node.getColName()).append(" is NaN ) {");
            } else {
                conditionLine.append(" ) {");
            }
        } else {
            conditionLine = new StringBuilder("If ( " + node.getColName() + " is in [ ");
            // get inclusive levels:
            SharedTreeNode rightChild = node.getRightChild();
            String stringToParseInclusiveLevelsFrom = rightChild.getInclusiveLevels().toString();
            int inclusiveLevelsLength = rightChild.getInclusiveLevels().toString().length();
            if (inclusiveLevelsLength  > 2) {
                // get rid of curly braces:
                stringToParseInclusiveLevelsFrom = stringToParseInclusiveLevelsFrom.substring(1, inclusiveLevelsLength - 1);
                String[] inclusiveLevels = stringToParseInclusiveLevelsFrom.split(",");
                Arrays.stream(inclusiveLevels)
                        .map(String::trim)
                        .map(Integer::parseInt)
                        .forEach(index -> conditionLine.append(node.getDomainValues()[index] + " "));
            } else {
                conditionLine.append("Missing set of levels for underlying node");
            }
            conditionLine.append("]) {");
        }
        return conditionLine.toString();
    }

    private static int getResponseLevelIndex(final String categorical, final SharedTreeModel.SharedTreeOutput sharedTreeOutput) {
        final String trimmedCategorical = categorical != null ? categorical.trim() : ""; // Trim the categorical once - input from the user

        if (! sharedTreeOutput.isClassifier()) {
            if (!trimmedCategorical.isEmpty())
                throw new IllegalArgumentException("There are no tree classes for " + sharedTreeOutput.getModelCategory() + ".");
            return 0; // There is only one tree for non-classification models
        }

        final String[] responseColumnDomain = sharedTreeOutput._domains[sharedTreeOutput.responseIdx()];
        if (sharedTreeOutput.getModelCategory() == ModelCategory.Binomial) {
            if (!trimmedCategorical.isEmpty() && !trimmedCategorical.equals(responseColumnDomain[0])) {
                throw new IllegalArgumentException("For binomial, only one tree class has been built per each iteration: " + responseColumnDomain[0]);
            } else {
                return 0;
            }
        } else {
            for (int i = 0; i < responseColumnDomain.length; i++) {
                // User is supposed to enter the name of the categorical level correctly, not ignoring case
                if (trimmedCategorical.equals(responseColumnDomain[i]))
                    return i;
            }
            throw new IllegalArgumentException("There is no such tree class. Given categorical level does not exist in response column: " + trimmedCategorical);
        }
    }

    /**
     * Converts H2O-3's internal representation of a tree in a form of {@link SharedTreeSubgraph} to a format
     * expected by H2O clients.
     *
     * @param sharedTreeSubgraph An instance of {@link SharedTreeSubgraph} to convert
     * @return An instance of {@link TreeProperties} with some attributes possibly empty if suitable. Never null.
     */
    static TreeProperties convertSharedTreeSubgraph(final SharedTreeSubgraph sharedTreeSubgraph) {
        Objects.requireNonNull(sharedTreeSubgraph);

        final TreeProperties treeprops = new TreeProperties();

        treeprops._leftChildren = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());
        treeprops._rightChildren = MemoryManager.malloc4(sharedTreeSubgraph.nodesArray.size());
        treeprops._descriptions = new String[sharedTreeSubgraph.nodesArray.size()];
        treeprops._thresholds = MemoryManager.malloc4f(sharedTreeSubgraph.nodesArray.size());
        treeprops._features = new String[sharedTreeSubgraph.nodesArray.size()];
        treeprops._nas = new String[sharedTreeSubgraph.nodesArray.size()];
        treeprops._predictions = MemoryManager.malloc4f(sharedTreeSubgraph.nodesArray.size());
        treeprops._leafNodeAssignments = new String[sharedTreeSubgraph.nodesArray.size()];
        treeprops._languagePathsRepresentations = new String[sharedTreeSubgraph.nodesArray.size()];


        // Set root node's children, there is no guarantee the root node will be number 0
        treeprops._rightChildren[0] = sharedTreeSubgraph.rootNode.getRightChild() != null ? sharedTreeSubgraph.rootNode.getRightChild().getNodeNumber() : -1;
        treeprops._leftChildren[0] = sharedTreeSubgraph.rootNode.getLeftChild() != null ? sharedTreeSubgraph.rootNode.getLeftChild().getNodeNumber() : -1;
        treeprops._thresholds[0] = sharedTreeSubgraph.rootNode.getSplitValue();
        treeprops._features[0] = sharedTreeSubgraph.rootNode.getColName();
        treeprops._nas[0] = getNaDirection(sharedTreeSubgraph.rootNode);
        treeprops.levels = new int[sharedTreeSubgraph.nodesArray.size()][];
        treeprops._languageTreeRepresentation = getLanguageRepresentation(sharedTreeSubgraph);
        treeprops._languagePathsRepresentations[0] = "Predicted value: " + sharedTreeSubgraph.rootNode.getPredValue();


        List<SharedTreeNode> nodesToTraverse = new ArrayList<>();
        nodesToTraverse.add(sharedTreeSubgraph.rootNode);
        append(treeprops._rightChildren, treeprops._leftChildren,
                treeprops._descriptions, treeprops._thresholds, treeprops._features, treeprops._nas,
                treeprops.levels, treeprops._predictions, nodesToTraverse, -1, false);
        fillLanguagePathRepresentation(treeprops, getDomainValues(sharedTreeSubgraph));

        return treeprops;
    }

    private static void append(final int[] rightChildren, final int[] leftChildren, final String[] nodesDescriptions,
                               final float[] thresholds, final String[] splitColumns, final String[] naHandlings,
                               final int[][] levels, final float[] predictions,
                               final List<SharedTreeNode> nodesToTraverse, int pointer, boolean visitedRoot) {
        if(nodesToTraverse.isEmpty()) return;

        List<SharedTreeNode> discoveredNodes = new ArrayList<>();

        for (SharedTreeNode node : nodesToTraverse) {
            pointer++;
            final SharedTreeNode leftChild = node.getLeftChild();
            final SharedTreeNode rightChild = node.getRightChild();
            if(visitedRoot){
                fillnodeDescriptions(node, nodesDescriptions, thresholds, splitColumns, levels, predictions,
                        naHandlings, pointer);
            } else {
                StringBuilder rootDescriptionBuilder = new StringBuilder();
                rootDescriptionBuilder.append("Root node has id ");
                rootDescriptionBuilder.append(node.getNodeNumber());
                rootDescriptionBuilder.append(" and splits on column '");
                rootDescriptionBuilder.append(node.getColName());
                rootDescriptionBuilder.append("'. ");
                fillNodeSplitTowardsChildren(rootDescriptionBuilder, node);
                nodesDescriptions[pointer] = rootDescriptionBuilder.toString();
                visitedRoot = true;
            }

            if (leftChild != null) {
                discoveredNodes.add(leftChild);
                leftChildren[pointer] = leftChild.getNodeNumber();
            } else {
                leftChildren[pointer] = NO_CHILD;
            }

            if (rightChild != null) {
                discoveredNodes.add(rightChild);
                rightChildren[pointer] = rightChild.getNodeNumber();
            } else {
                rightChildren[pointer] = NO_CHILD;
            }
        }

        append(rightChildren, leftChildren, nodesDescriptions, thresholds, splitColumns, naHandlings, levels, predictions,
                discoveredNodes, pointer, true);
    }

    private static ArrayList<Integer> extractInternalIds(TreeProperties properties) {
        int nodeId = 0;
        ArrayList<Integer> nodeIds = new ArrayList<>();
        nodeIds.add(nodeId);
        for (int i = 0; i < properties._leftChildren.length; i++) {
            if (properties._leftChildren[i] != -1) {
                nodeId++;
                nodeIds.add(properties._leftChildren[i]);
                properties._leftChildren[i] = nodeId;
            }

            if (properties._rightChildren[i] != -1) {
                nodeId++;
                nodeIds.add(properties._rightChildren[i]);
                properties._rightChildren[i] = nodeId;
            }
        }
        return nodeIds;
    }

    private static void fillLanguagePathRepresentation(TreeProperties properties, String[] domainValues) {
        ArrayList<Integer> nodeIds = extractInternalIds(properties);
        nodeIds.forEach((list_path_id) -> {
            int index = nodeIds.indexOf(list_path_id);
            properties._languagePathsRepresentations[index] = fillNodePath(list_path_id, nodeIds, false, properties, domainValues);
        });
    }

    private static String fillNodePath(int nodeId, ArrayList<Integer> nodeIds, boolean valuePrinted, TreeProperties properties, String[] domainValues){
        int parentIndex = -1;
        int parentId = -1;
        String condition = "";
        String nodePathr = "";
        int currentNodeIndex = nodeIds.indexOf(nodeId);
        if (!valuePrinted) {
            // print prediction value
            nodePathr += "Predicted value: " + properties._predictions[currentNodeIndex] + "\n";
            valuePrinted = true;
            nodePathr += fillNodePath(nodeId, nodeIds, valuePrinted, properties, domainValues);
        } else {
            // print conditions leading to prediction value
            int[] leftChildren = properties._leftChildren;
            int[] rightChildren = properties._rightChildren;
            if (IntStream.of(leftChildren).anyMatch(i -> i == currentNodeIndex)) {
                // parent from right
                parentIndex = IntStream.range(0, leftChildren.length).filter(i -> leftChildren[i] == currentNodeIndex).findAny().getAsInt();
                parentId = nodeIds.get(parentIndex);
                condition = getConditionByIndex(parentIndex, "R", properties, domainValues);
            }
            if (IntStream.of(rightChildren).anyMatch(i -> i == currentNodeIndex)) {
                parentIndex = IntStream.range(0, rightChildren.length).filter(i -> rightChildren[i] == currentNodeIndex).findAny().getAsInt();
                parentId = nodeIds.get(parentIndex);
                condition = getConditionByIndex(parentIndex, "L", properties, domainValues);
            }
            if (parentIndex != -1) {
                nodePathr += "^\n";
                nodePathr += "|\n";
                nodePathr += "|\n";
                nodePathr += "|\n";
                nodePathr += condition;
                nodePathr += fillNodePath(parentId, nodeIds, valuePrinted, properties, domainValues);
            }
        }
        return nodePathr;
    }

    private static String getConditionByIndex(int index, String parentOrigin, TreeProperties properties, String[] domainValues) {
        String conditionLine;
        String nanString = " or " + properties._features[index] + " is NaN";
        boolean useNan = false;
        int targetNodeId = -1;
        if (Float.compare(properties._thresholds[index],Float.NaN) == 0) {
            conditionLine = "If ( " + properties._features[index] + " is in [";
            targetNodeId = "R".equals(parentOrigin) ? properties._leftChildren[index] : properties._rightChildren[index];
            int[] inclusiveLevels = properties.levels[targetNodeId];
            for (int level : inclusiveLevels) {
                conditionLine += domainValues[level] + " ";
            }
            conditionLine += " ])\n";
        } else {
            String sign;
            if ("R".equals(parentOrigin)) {
                sign = " > ";
                if ("LEFT".equals(properties._nas[index])) {
                    useNan = true;
                }
            } else {
                sign = " <= ";
                if ("RIGHT".equals(properties._nas[index])) {
                    useNan = true;
                }
            }
            conditionLine = "If ( " + properties._features[index] + sign + properties._thresholds[index];
            if (useNan) {
                conditionLine += nanString;
            }
            conditionLine += " )\n";
        }
        return conditionLine;
    }
    
    private static String[] getDomainValues(final SharedTreeSubgraph sharedTreeSubgraph) {
        if (sharedTreeSubgraph.rootNode.getRightChild() != null) 
            return sharedTreeSubgraph.rootNode.getRightChild().getDomainValues();
        else if (sharedTreeSubgraph.rootNode.getLeftChild() != null)
            return sharedTreeSubgraph.rootNode.getLeftChild().getDomainValues();
        else
            return null;
    }
    
    private static void fillnodeDescriptions(final SharedTreeNode node, final String[] nodeDescriptions,
                                             final float[] thresholds, final String[] splitColumns, final int[][] levels,
                                             final float[] predictions, final String[] naHandlings, final int pointer) {
        final StringBuilder nodeDescriptionBuilder = new StringBuilder();
        int[] nodeLevels = node.getParent().isBitset() ? extractNodeLevels(node) : null;
        nodeDescriptionBuilder.append("Node has id ");
        nodeDescriptionBuilder.append(node.getNodeNumber());
        if (node.getColName() != null && node.isLeaf()) {
            nodeDescriptionBuilder.append(" and splits on column '");
            nodeDescriptionBuilder.append(node.getColName());
            nodeDescriptionBuilder.append("'. ");
        } else {
            nodeDescriptionBuilder.append(" and is a terminal node. ");
        }

        fillNodeSplitTowardsChildren(nodeDescriptionBuilder, node);

        if (!Float.isNaN(node.getParent().getSplitValue())) {
            nodeDescriptionBuilder.append(" Parent node split threshold is ");
            nodeDescriptionBuilder.append(node.getParent().getSplitValue());
            nodeDescriptionBuilder.append(". Prediction: ");
            nodeDescriptionBuilder.append(node.getPredValue());
            nodeDescriptionBuilder.append(".");
        } else if (node.getParent().isBitset()) {
            nodeLevels = extractNodeLevels(node);
            nodeDescriptionBuilder.append(" Parent node split on column [");
            nodeDescriptionBuilder.append(node.getParent().getColName());
            if(nodeLevels != null) {
                nodeDescriptionBuilder.append("]. Inherited categorical levels from parent split: ");
                for (int nodeLevelsindex = 0; nodeLevelsindex < nodeLevels.length; nodeLevelsindex++) {
                    nodeDescriptionBuilder.append(node.getParent().getDomainValues()[nodeLevels[nodeLevelsindex]]);
                    if (nodeLevelsindex != nodeLevels.length - 1) nodeDescriptionBuilder.append(",");
                }
            } else {
                nodeDescriptionBuilder.append("]. No categoricals levels inherited from parent.");
            }
        } else {
            nodeDescriptionBuilder.append("Split value is NA.");
        }

        nodeDescriptions[pointer] = nodeDescriptionBuilder.toString();
        splitColumns[pointer] = node.getColName();
        naHandlings[pointer] = getNaDirection(node);
        levels[pointer] = nodeLevels;
        predictions[pointer] = node.getPredValue();
        thresholds[pointer] = node.getSplitValue();
    }

    private static void fillNodeSplitTowardsChildren(final StringBuilder nodeDescriptionBuilder, final SharedTreeNode node){
        if (!Float.isNaN(node.getSplitValue())) {
            nodeDescriptionBuilder.append("Split threshold is ");
            if (node.getLeftChild() != null) {
                nodeDescriptionBuilder.append(" < ");
                nodeDescriptionBuilder.append(node.getSplitValue());
                nodeDescriptionBuilder.append(" to the left node (");
                nodeDescriptionBuilder.append(node.getLeftChild().getNodeNumber());
                nodeDescriptionBuilder.append(")");
            }

            if (node.getLeftChild() != null) {
                if(node.getLeftChild() != null) nodeDescriptionBuilder.append(", ");
                nodeDescriptionBuilder.append(" >= ");
                nodeDescriptionBuilder.append(node.getSplitValue());
                nodeDescriptionBuilder.append(" to the right node (");
                nodeDescriptionBuilder.append(node.getRightChild().getNodeNumber());
                nodeDescriptionBuilder.append(")");
            }
            nodeDescriptionBuilder.append(".");
        } else if (node.isBitset()) {
            fillNodeCategoricalSplitDescription(nodeDescriptionBuilder, node);
        }
    }

    private static int[] extractNodeLevels(final SharedTreeNode node) {
        final BitSet childInclusiveLevels = node.getInclusiveLevels();
        final int cardinality = childInclusiveLevels.cardinality();
        if (cardinality > 0) {
            int[] nodeLevels = MemoryManager.malloc4(cardinality);
            int bitsignCounter = 0;
            for (int i = childInclusiveLevels.nextSetBit(0); i >= 0; i = childInclusiveLevels.nextSetBit(i + 1)) {
                nodeLevels[bitsignCounter] = i;
                bitsignCounter++;
            }
            return nodeLevels;
        }
        return null;
    }

    private static void fillNodeCategoricalSplitDescription(final StringBuilder nodeDescriptionBuilder, final SharedTreeNode node) {
        final SharedTreeNode leftChild = node.getLeftChild();
        final SharedTreeNode rightChild = node.getRightChild();
        final int[] leftChildLevels = extractNodeLevels(leftChild);
        final int[] rightChildLevels = extractNodeLevels(rightChild);

        if (leftChild != null) {
            nodeDescriptionBuilder.append(" Left child node (");
            nodeDescriptionBuilder.append(leftChild.getNodeNumber());
            nodeDescriptionBuilder.append(") inherits categorical levels: ");

            if (leftChildLevels != null) {
                for (int nodeLevelsindex = 0; nodeLevelsindex < leftChildLevels.length; nodeLevelsindex++) {
                    nodeDescriptionBuilder.append(node.getDomainValues()[leftChildLevels[nodeLevelsindex]]);
                    if (nodeLevelsindex != leftChildLevels.length - 1) nodeDescriptionBuilder.append(",");
                }
            }
        }

        if (rightChild != null) {
            nodeDescriptionBuilder.append(". Right child node (");
            nodeDescriptionBuilder.append(rightChild.getNodeNumber());
            nodeDescriptionBuilder.append(") inherits categorical levels: ");

            if (rightChildLevels != null) {
                for (int nodeLevelsindex = 0; nodeLevelsindex < rightChildLevels.length; nodeLevelsindex++) {
                    nodeDescriptionBuilder.append(node.getDomainValues()[rightChildLevels[nodeLevelsindex]]);
                    if (nodeLevelsindex != rightChildLevels.length - 1) nodeDescriptionBuilder.append(",");
                }
            }
        }
        nodeDescriptionBuilder.append(". ");


    }

    private static String getNaDirection(final SharedTreeNode node) {
        final boolean leftNa = node.getLeftChild() != null && node.getLeftChild().isInclusiveNa();
        final boolean rightNa = node.getRightChild() != null && node.getRightChild().isInclusiveNa();
        assert (rightNa ^ leftNa) || (rightNa == false && leftNa == false);

        if (leftNa) {
            return "LEFT";
        } else if (rightNa) {
            return "RIGHT";
        }
        return null; // No direction
    }

    public static class TreeProperties {

        public int[] _leftChildren;
        public int[] _rightChildren;
        public String[] _descriptions; // General node description, most likely to contain serialized threshold or inclusive dom. levels
        public float[] _thresholds;
        public String[] _features;
        public int[][] levels; // Categorical levels, points to a list of categoricals that is already existing within the model on the client.
        public String[] _nas;
        public float[] _predictions; // Prediction values on terminal nodes
        public String _languageTreeRepresentation;
        public String[] _leafNodeAssignments;
        public String[] _languagePathsRepresentations;

    }
}
