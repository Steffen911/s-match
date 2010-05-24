package it.unitn.disi.smatch.matchers.element.gloss;

import it.unitn.disi.smatch.components.ConfigurableException;
import it.unitn.disi.smatch.data.mappings.IMappingElement;
import it.unitn.disi.smatch.matchers.element.ISenseGlossBasedElementLevelSemanticMatcher;
import it.unitn.disi.smatch.matchers.element.MatcherLibraryException;
import it.unitn.disi.smatch.oracles.ISynset;
import it.unitn.disi.smatch.oracles.LinguisticOracleException;
import org.apache.log4j.Logger;

import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Implements WNExtendedGlossComparison matcher. See Element Level Semantic matchers paper for more details.
 * <p/>
 * Accepts the following parameters:
 * <p/>
 * threshold - integer parameter, which by default equals 5.
 * <p/>
 * meaninglessWords - string parameter which indicates words to ignore. Check the source file for default value.
 *
 * @author Mikalai Yatskevich mikalai.yatskevich@comlab.ox.ac.uk
 * @author Aliaksandr Autayeu avtaev@gmail.com
 */
public class WNExtendedGlossComparison extends BasicGlossMatcher implements ISenseGlossBasedElementLevelSemanticMatcher {

    private static final Logger log = Logger.getLogger(WNExtendedGlossComparison.class);

    private static final String THRESHOLD_KEY = "threshold";
    private int threshold = 5;

    // the words which are cut off from the area of discourse
    public static final String MEANINGLESS_WORDS_KEY = "meaninglessWords";
    private String meaninglessWords = "of on to their than from for by in at is are have has the a as with your etc our into its his her which him among those against ";

    @Override
    public boolean setProperties(Properties newProperties) throws ConfigurableException {
        boolean result = super.setProperties(newProperties);
        if (result) {
            if (newProperties.containsKey(THRESHOLD_KEY)) {
                threshold = Integer.parseInt(newProperties.getProperty(THRESHOLD_KEY));
            }

            if (newProperties.containsKey(MEANINGLESS_WORDS_KEY)) {
                meaninglessWords = newProperties.getProperty(MEANINGLESS_WORDS_KEY) + " ";
            }
        }
        return result;
    }


    /**
     * Computes the relation for extended gloss matcher.
     *
     * @param source1 the gloss of source
     * @param target1 the gloss of target
     * @return synonym or IDK relation
     */
    public char match(ISynset source1, ISynset target1) throws MatcherLibraryException {
        char result = IMappingElement.IDK;
        try {
            String tExtendedGloss = getExtendedGloss(target1, 1, IMappingElement.LESS_GENERAL);
            String sExtendedGloss = getExtendedGloss(source1, 1, IMappingElement.LESS_GENERAL);
            //variations of this matcher
            StringTokenizer stSource = new StringTokenizer(tExtendedGloss, " ,.\"'();");
            String lemmaS, lemmaT;
            int counter = 0;
            while (stSource.hasMoreTokens()) {
                StringTokenizer stTarget = new StringTokenizer(sExtendedGloss, " ,.\"'();");
                lemmaS = stSource.nextToken();
                if (meaninglessWords.indexOf(lemmaS) == -1)
                    while (stTarget.hasMoreTokens()) {
                        lemmaT = stTarget.nextToken();
                        if (meaninglessWords.indexOf(lemmaT) == -1)
                            if (lemmaS.equalsIgnoreCase(lemmaT))
                                counter++;
                    }
            }
            if (counter > threshold) {
                result = IMappingElement.EQUIVALENCE;
            }
        } catch (LinguisticOracleException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new MatcherLibraryException(errMessage, e);
        }
        return result;
    }
}
