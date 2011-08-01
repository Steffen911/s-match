package it.unitn.disi.smatch.loaders.mapping;

import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Version with an iterator.
 *
* @author <a rel="author" href="http://autayeu.com">Aliaksandr Autayeu</a>
 */
public class PlainMappingLoaderIt extends PlainMappingLoader {

    private static final Logger log = Logger.getLogger(PlainMappingLoaderIt.class);

    @Override
    protected HashMap<String, INode> createHash(IContext context) {
        HashMap<String, INode> result = new HashMap<String, INode>();

        int nodeCount = 0;
        for (Iterator<INode> i = context.getNodes(); i.hasNext();) {
            INode node = i.next();
            result.put(getNodePathToRoot(node), node);
            nodeCount++;
        }

        if (log.isEnabledFor(Level.INFO)) {
            log.info("Created hash for " + nodeCount + " nodes...");
        }

        return result;
    }

}
