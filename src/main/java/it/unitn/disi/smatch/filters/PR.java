package it.unitn.disi.smatch.filters;

import it.unitn.disi.common.components.ConfigurableException;
import it.unitn.disi.smatch.data.mappings.IContextMapping;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.loaders.mapping.IMappingLoader;
import it.unitn.disi.smatch.loaders.mapping.MappingLoaderException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.text.DecimalFormat;
import java.util.Properties;

/**
 * Computes precision and recall using positive and negative parts of the golden standard. Needs the
 * following configuration parameters:
 * <p/>
 * mappingLoader - an instance of IMappingLoader
 * <p/>
 * mappings - locations of max 2 mappings, separated with semicolon, positive comes first, negative comes second. It is
 * possible to specify only positive mapping. 
 * <p/>
 * <p/>
 * For theories behind this way of calculating precision and recall check out TaxME2 paper:
 * http://eprints.biblio.unitn.it/archive/00001345/
 * A Large Scale Dataset for the Evaluation of Ontology Matching Systems by
 * Giunchiglia, Fausto and Yatskevich, Mikalai and Avesani, Paolo and Shvaiko, Pavel
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class PR extends BaseFilter {

    private static final Logger log = Logger.getLogger(PR.class);

    private static final String MAPPING_LOADER_KEY = "mappingLoader";
    protected IMappingLoader mappingLoader = null;

    private static final String MAPPINGS_KEY = "mappings";
    protected String[] mappingLocations = null;
    @SuppressWarnings("unchecked")
    protected IContextMapping<INode>[] filterMappings = (IContextMapping<INode>[]) new IContextMapping[2];

    @Override
    public boolean setProperties(Properties newProperties) throws ConfigurableException {
        Properties oldProperties = new Properties();
        oldProperties.putAll(properties);

        boolean result = super.setProperties(newProperties);
        if (result) {
            if (newProperties.containsKey(MAPPING_LOADER_KEY)) {
                mappingLoader = (IMappingLoader) configureComponent(mappingLoader, oldProperties, newProperties, "mapping loader", MAPPING_LOADER_KEY, IMappingLoader.class);
            } else {
                final String errMessage = "Cannot find configuration key " + MAPPING_LOADER_KEY;
                log.error(errMessage);
                throw new ConfigurableException(errMessage);
            }

            if (newProperties.containsKey(MAPPINGS_KEY)) {
                mappingLocations = newProperties.getProperty(MAPPINGS_KEY).split(";");
            } else {
                final String errMessage = "Cannot find configuration key " + MAPPINGS_KEY;
                log.error(errMessage);
                throw new ConfigurableException(errMessage);
            }
        }
        return result;
    }

    public IContextMapping<INode> filter(IContextMapping<INode> mapping) throws MappingFilterException {
        //load the mapping
        try {
            log.debug("Loading positive mapping...");
            filterMappings[0] = mappingLoader.loadMapping(mapping.getSourceContext(), mapping.getTargetContext(), mappingLocations[0]);
            log.debug("Loaded positive mapping...");

            if (1 < mappingLocations.length) {
                log.debug("Loading negative mapping...");
                filterMappings[1] = mappingLoader.loadMapping(mapping.getSourceContext(), mapping.getTargetContext(), mappingLocations[1]);
                log.debug("Loaded negative mapping...");
            }
        } catch (MappingLoaderException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new MappingFilterException(errMessage, e);
        }

        long posSize = filterMappings[0].size();

        filterMappings[0].retainAll(mapping);
        if (1 < mappingLocations.length) {
            filterMappings[1].retainAll(mapping);
        }

        long posTruePositiveSize = filterMappings[0].size();
        double p = 0;
        double r = 0;

        if (1 < mappingLocations.length) {
            long negTruePositiveSize = filterMappings[1].size();
            if (0 < (posTruePositiveSize + negTruePositiveSize) && 0 < posSize) {
                if (log.isEnabledFor(Level.INFO)) {
                    log.info("positive true positive:\t" + posTruePositiveSize);
                    log.info("negative true positive:\t" + negTruePositiveSize);
                }
                p = posTruePositiveSize / (double) (posTruePositiveSize + negTruePositiveSize);
                r = posTruePositiveSize / (double) posSize;
            }
        } else {
            if (0 < mapping.size() && 0 < posSize) {
                if (log.isEnabledFor(Level.INFO)) {
                    log.info("positive true positive:\t" + posTruePositiveSize);
                }
                p = posTruePositiveSize / (double) mapping.size();
                r = posTruePositiveSize / (double) posSize;
            }
        }

        DecimalFormat df = new DecimalFormat("00.0000%");
        if (log.isEnabledFor(Level.INFO)) {
            log.info("Precision:\t" + df.format(p));
            log.info("Recall   :\t" + df.format(r));
            if (0 != posTruePositiveSize) {
                log.info("F-Measure:\t" + df.format((2 * p * r) / (p + r)));
            } else {
                log.info("F-Measure:\t" + df.format(0));
            }
        }

        return mapping;
    }
}
