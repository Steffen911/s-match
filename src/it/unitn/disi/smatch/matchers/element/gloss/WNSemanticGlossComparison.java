package it.unitn.disi.smatch.matchers.element.gloss;

import it.unitn.disi.smatch.MatchManager;
import it.unitn.disi.smatch.matchers.element.ISenseGlossBasedElementLevelSemanticMatcher;
import it.unitn.disi.smatch.oracles.ILinguisticOracle;
import it.unitn.disi.smatch.oracles.ISynset;
import it.unitn.disi.smatch.oracles.IWordNetMatcher;

import java.util.StringTokenizer;

/**
 * implements WNSemanticGlossComparison matcher
 * see Element Level Semantic matchers paper for more details
 *
 * @author Mikalai Yatskevich mikalai.yatskevich@comlab.ox.ac.uk
 * @author Aliaksandr Autayeu avtaev@gmail.com
 */
public class WNSemanticGlossComparison extends BasicGlossMatcher implements ISenseGlossBasedElementLevelSemanticMatcher {
    private static ILinguisticOracle ILO = null;
    private static IWordNetMatcher IWNM = null;

    public WNSemanticGlossComparison() {
        super();
        ILO = MatchManager.getLinguisticOracle();
        IWNM = MatchManager.getIWNMatcher();
    }

    public char match(ISynset source, ISynset target) {
        int Equals = 0;
        int moreGeneral = 0;
        int lessGeneral = 0;
        int Opposite = 0;
        String sSynset = source.getGloss();
        String tSynset = target.getGloss();
        StringTokenizer stSource = new StringTokenizer(sSynset, " ,.\"'()");
        String lemmaS, lemmaT;
        int counter = 0;
        while (stSource.hasMoreTokens()) {
            StringTokenizer stTarget = new StringTokenizer(tSynset, " ,.\"'()");
            lemmaS = stSource.nextToken();
            if (MatchManager.meaninglessWords.indexOf(lemmaS) == -1)
                while (stTarget.hasMoreTokens()) {
                    lemmaT = stTarget.nextToken();
                    if (MatchManager.meaninglessWords.indexOf(lemmaT) == -1) {
                        if (isWordLessGeneral(lemmaS, lemmaT))
                            lessGeneral++;
                        else if (isWordMoreGeneral(lemmaS, lemmaT))
                            moreGeneral++;
                        else if (isWordSynonym(lemmaS, lemmaT))
                            Equals++;
                        else if (isWordOpposite(lemmaS, lemmaT))
                            Opposite++;
                    }
                }
        }
        return getRelationFromInts(lessGeneral, moreGeneral, Equals, Opposite);
    }

    /**
     * decide which relation to return
     *
     * @param lg
     * @param mg
     * @param syn
     * @param opp
     * @return
     */
    private char getRelationFromInts(int lg, int mg, int syn, int opp) {
        if ((lg >= mg) && (lg >= syn) && (lg >= opp) && (lg > 0))
            return MatchManager.LESS_GENERAL_THAN;
        if ((mg >= lg) && (mg >= syn) && (mg >= opp) && (mg > 0))
            return MatchManager.MORE_GENERAL_THAN;
        if ((syn >= mg) && (syn >= lg) && (syn >= opp) && (syn > 0))
            return MatchManager.LESS_GENERAL_THAN;
        if ((opp >= mg) && (opp >= syn) && (opp >= lg) && (opp > 0))
            return MatchManager.LESS_GENERAL_THAN;
        return MatchManager.IDK_RELATION;
    }
}
