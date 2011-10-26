package it.unitn.disi.smatch.matchers.element;

import it.unitn.disi.common.components.Configurable;
import it.unitn.disi.common.components.ConfigurableException;
import it.unitn.disi.smatch.data.ling.ISense;
import it.unitn.disi.smatch.data.mappings.IMappingElement;
import it.unitn.disi.smatch.oracles.LinguisticOracleException;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Properties;

/**
 * Implements WNHierarchy matcher. See Element Level Semantic matchers paper for more details. Accepts depth integer
 * parameter, which by default equals 2.
 *
 * @author Mikalai Yatskevich mikalai.yatskevich@comlab.ox.ac.uk
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class WNHierarchy extends Configurable implements ISenseGlossBasedElementLevelSemanticMatcher {

    private static final Logger log = Logger.getLogger(WNHierarchy.class);

    // depth
    private static final String DEPTH_KEY = "depth";
    private int depth = 2;

    @Override
    public boolean setProperties(Properties newProperties) throws ConfigurableException {
        boolean result = super.setProperties(newProperties);
        if (result) {
            if (newProperties.containsKey(DEPTH_KEY)) {
                depth = Integer.parseInt(newProperties.getProperty(DEPTH_KEY));
            } else {
                final String errMessage = "Cannot find configuration key " + DEPTH_KEY;
                log.error(errMessage);
                throw new ConfigurableException(errMessage);
            }
        }
        return result;
    }

    /**
     * Matches two strings with WNHeirarchy matcher.
     *
     * @param source gloss of source label
     * @param target gloss of target label
     * @return synonym or IDk relation
     */
    public char match(ISense source, ISense target) throws MatcherLibraryException {
        List<ISense> sourceList = getAncestors(source, depth);
        List<ISense> targetList = getAncestors(target, depth);
        targetList.retainAll(sourceList);
        if (targetList.size() > 0)
            return IMappingElement.EQUIVALENCE;
        else
            return IMappingElement.IDK;
    }

    private List<ISense> getAncestors(ISense node, int depth) throws MatcherLibraryException {
        try {
            return node.getParents(depth);
        } catch (LinguisticOracleException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new MatcherLibraryException(errMessage, e);
        }
    }
}