package it.unitn.disi.smatch.matchers.structure.tree;

import it.unitn.disi.smatch.components.Configurable;
import it.unitn.disi.smatch.components.ConfigurableException;
import it.unitn.disi.smatch.data.ling.IAtomicConceptOfLabel;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.matchers.structure.node.INodeMatcher;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * Base class for tree matchers. Accepts NodeMatcher configuration parameters which should implement a
 * {@link it.unitn.disi.smatch.matchers.structure.node.INodeMatcher} interface.
 *
 * @author Aliaksandr Autayeu avtaev@gmail.com
 */
public class BaseTreeMatcher extends Configurable {

    private static final String NODE_MATCHER_KEY = "NodeMatcher";
    protected INodeMatcher nodeMatcher = null;

    @Override
    public void setProperties(Properties newProperties) throws ConfigurableException {
        if (!newProperties.equals(properties)) {
            nodeMatcher = (INodeMatcher) configureComponent(nodeMatcher, properties, newProperties, "node matcher", NODE_MATCHER_KEY, INodeMatcher.class);

            properties.clear();
            properties.putAll(newProperties);
        }
    }

    /**
     * Creates a mapping acol id -> acol object. Used to resolve references from acol id in the formulas
     * to acol objects to be substituted with a DIMACS variable for a specific node matching task.
     *
     * @param c context
     * @return mapping acol id -> acol object
     */
    protected static Map<String, IAtomicConceptOfLabel> createAcolsMap(IContext c) {
        HashMap<String, IAtomicConceptOfLabel> result = new HashMap<String, IAtomicConceptOfLabel>();
        for (Iterator<INode> i = c.getRoot().getSubtree(); i.hasNext();) {
            INode node = i.next();
            for (Iterator<IAtomicConceptOfLabel> ii = node.getNodeData().getACoLs(); ii.hasNext();) {
                IAtomicConceptOfLabel acol = ii.next();
                result.put(node.getNodeData().getId() + "." + Integer.toString(acol.getId()), acol);
            }
        }
        return result;
    }
}