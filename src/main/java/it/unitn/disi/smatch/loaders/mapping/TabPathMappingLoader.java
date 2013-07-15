package it.unitn.disi.smatch.loaders.mapping;

import it.unitn.disi.smatch.data.mappings.IContextMapping;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.loaders.ILoader;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Loads the tab-delimited mapping. Source path (tab-delimited) \t\t relation \t\t Target path (tab-delimited)
 * <p/>
 * Needs mappingFactory configuration parameter, which should point to an instance of a class implementing
 * {@link it.unitn.disi.smatch.data.mappings.IMappingFactory} interface.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class TabPathMappingLoader extends BaseFileMappingLoader {

    private static final Logger log = Logger.getLogger(TabPathMappingLoader.class);

    @Override
    protected void process(IContextMapping<INode> mapping, IContext source, IContext target, BufferedReader reader) throws IOException {
        HashMap<String, INode> sNodes = createHash(source);
        HashMap<String, INode> tNodes = createHash(target);

        String line;
        while ((line = reader.readLine()) != null &&
                !line.startsWith("#") &&
                !line.isEmpty()) {

            INode sourceNode;
            INode targetNode;
            char rel;

            String[] tokens = line.split("\t\t");
            if (3 != tokens.length) {
                if (log.isEnabledFor(Level.WARN)) {
                    log.warn("Unrecognized mapping format: " + line);
                }
            } else {
                //tokens = left \t relation \t right
                rel = tokens[1].toCharArray()[0];

                sourceNode = sNodes.get(tokens[0]);
                if (null == sourceNode) {
                    if (log.isEnabledFor(Level.WARN)) {
                        log.warn("Could not find source node: " + tokens[0]);
                    }
                }

                targetNode = tNodes.get(tokens[2]);
                if (!tNodes.containsKey(tokens[2])) {
                    if (log.isEnabledFor(Level.WARN)) {
                        log.warn("Could not find target node: " + tokens[2]);
                    }
                }

                if ((null != sourceNode) && (null != targetNode)) {
                    mapping.setRelation(sourceNode, targetNode, rel);
                    countRelation(rel);
                    cntLoaded++;
                } else {
                    if (log.isEnabledFor(Level.WARN)) {
                        log.warn("Could not find mapping: " + line);
                    }
                }
            }

            reportProgress();
        }
    }

    /**
     * Creates hash map for nodes which contains path from root to node for each node.
     *
     * @param context a context
     * @return a hash table which contains path from root to node for each node
     */
    protected HashMap<String, INode> createHash(IContext context) {
        HashMap<String, INode> result = new HashMap<String, INode>();

        int nodeCount = 0;
        for (INode node : context.getNodesList()) {
            result.put(getPathToRoot(node), node);
            nodeCount++;
        }

        if (log.isEnabledFor(Level.INFO)) {
            log.info("Created hash for " + nodeCount + " nodes...");
        }

        return result;
    }

    /**
     * Gets the path of a node from root for hash mapping.
     *
     * @param node the interface of data structure of input node
     * @return the string of the path from root to node
     */
    private String getPathToRoot(INode node) {
        StringBuilder result = new StringBuilder(node.getNodeData().getName());
        INode curNode = node.getParent();
        while (null != curNode) {
            result.insert(0, curNode.getNodeData().getName() + "\t");
            curNode = curNode.getParent();
        }
        return result.toString();
    }

    public String getDescription() {
        return ILoader.TXT_FILES;
    }
}
