package it.unitn.disi.smatch.preprocessors;

import it.unitn.disi.smatch.SMatchConstants;
import it.unitn.disi.smatch.SMatchException;
import it.unitn.disi.smatch.components.Configurable;
import it.unitn.disi.smatch.components.ConfigurableException;
import it.unitn.disi.smatch.data.ling.IAtomicConceptOfLabel;
import it.unitn.disi.smatch.data.ling.ISense;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.oracles.ILinguisticOracle;
import it.unitn.disi.smatch.oracles.ISenseMatcher;
import it.unitn.disi.smatch.oracles.LinguisticOracleException;
import it.unitn.disi.smatch.oracles.SenseMatcherException;
import it.unitn.disi.smatch.utils.SMatchUtils;
import net.sf.extjwnl.JWNL;
import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Performs all the operations related to linguistic preprocessing.
 * It also contains some heuristics to perform sense disambiguation.
 * Corresponds to Step 1 and 2 in the semantic matching algorithm.
 * <p/>
 * Needs and accepts several configuration parameters. See source file for more information.
 *
 * @author Mikalai Yatskevich mikalai.yatskevich@comlab.ox.ac.uk
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class DefaultContextPreprocessor extends Configurable implements IContextPreprocessor {

    private static final Logger log = Logger.getLogger(DefaultContextPreprocessor.class);

    // controls loading of arrays, used to skip loading before conversion
    private static final String LOAD_ARRAYS_KEY = "loadArrays";

    private static final String JWNL_PROPERTIES_PATH_KEY = "JWNLPropertiesPath";

    // contains all the multiwords in WordNet
    private static final String MULTIWORDS_FILE_KEY = "multiwordsFileName";
    private HashMap<String, ArrayList<ArrayList<String>>> multiwords = null;

    // sense matcher
    private static final String SENSE_MATCHER_KEY = "senseMatcher";
    private ISenseMatcher senseMatcher = null;

    // linguistic oracle
    private static final String LINGUISTIC_ORACLE_KEY = "linguisticOracle";
    private ILinguisticOracle linguisticOracle = null;

    private HashSet<String> unrecognizedWords = new HashSet<String>();

    // flag to output the label being translated in logs
    private final static String DEBUG_LABELS_KEY = "debugLabels";
    private boolean debugLabels = false;

    // flag to output the unrecognized words in logs
    private final static String DEBUG_UNRECOGNIZED_WORDS_KEY = "debugUnrecognizedWords";
    private boolean debugUnrecognizedWords = false;

    // the words which are cut off from the area of discourse
    private static final String MEANINGLESS_WORDS_KEY = "meaninglessWords";
    private String meaninglessWords = "of on to their than from for by in at is are have has the a as with your etc our into its his her which him among those against ";

    // the words which are treated as logical and (&)
    private static final String AND_WORDS_KEY = "andWords";
    private String andWords = " + & ^ ";

    // the words which are treated as logical or (|)
    private static final String OR_WORDS_KEY = "orWords";
    private String orWords = " and or | , ";

    // the words which are treated as logical not (~)
    private static final String NOT_WORDS_KEY = "notWords";
    private String notWords = " except non without ";

    // Number characters for linguistic preprocessing.
    private static final String NUMBER_CHARACTERS_KEY = "numberCharacters";
    private String numberCharacters = "1234567890";


    @Override
    public boolean setProperties(Properties newProperties) throws ConfigurableException {
        Properties oldProperties = new Properties();
        oldProperties.putAll(properties);

        boolean result = super.setProperties(newProperties);
        if (result) {
            boolean loadArrays = true;
            if (newProperties.containsKey(LOAD_ARRAYS_KEY)) {
                loadArrays = Boolean.parseBoolean(newProperties.getProperty(LOAD_ARRAYS_KEY));
            }

            if (newProperties.containsKey(MULTIWORDS_FILE_KEY)) {
                if (loadArrays) {
                    String multiwordFileName = newProperties.getProperty(MULTIWORDS_FILE_KEY);
                    log.info("Loading multiwords: " + multiwordFileName);
                    multiwords = readHash(multiwordFileName);
                    log.info("loaded multiwords: " + multiwords.size());
                }
            } else {
                final String errMessage = "Cannot find configuration key " + MULTIWORDS_FILE_KEY;
                log.error(errMessage);
                throw new ConfigurableException(errMessage);
            }

            if (newProperties.containsKey(SENSE_MATCHER_KEY)) {
                senseMatcher = (ISenseMatcher) configureComponent(senseMatcher, oldProperties, newProperties, "sense matcher", SENSE_MATCHER_KEY, ISenseMatcher.class);
            } else {
                final String errMessage = "Cannot find configuration key " + SENSE_MATCHER_KEY;
                log.error(errMessage);
                throw new ConfigurableException(errMessage);
            }

            if (newProperties.containsKey(LINGUISTIC_ORACLE_KEY)) {
                linguisticOracle = (ILinguisticOracle) configureComponent(linguisticOracle, oldProperties, newProperties, "linguistic oracle", LINGUISTIC_ORACLE_KEY, ILinguisticOracle.class);
            } else {
                final String errMessage = "Cannot find configuration key " + LINGUISTIC_ORACLE_KEY;
                log.error(errMessage);
                throw new ConfigurableException(errMessage);
            }

            if (newProperties.containsKey(DEBUG_LABELS_KEY)) {
                debugLabels = Boolean.parseBoolean(newProperties.getProperty(DEBUG_LABELS_KEY));
            }

            if (newProperties.containsKey(DEBUG_UNRECOGNIZED_WORDS_KEY)) {
                debugUnrecognizedWords = Boolean.parseBoolean(newProperties.getProperty(DEBUG_UNRECOGNIZED_WORDS_KEY));
            }

            if (newProperties.containsKey(MEANINGLESS_WORDS_KEY)) {
                meaninglessWords = newProperties.getProperty(MEANINGLESS_WORDS_KEY) + " ";
            }

            if (newProperties.containsKey(AND_WORDS_KEY)) {
                andWords = newProperties.getProperty(AND_WORDS_KEY) + " ";
            }

            if (newProperties.containsKey(OR_WORDS_KEY)) {
                orWords = newProperties.getProperty(OR_WORDS_KEY) + " ";
            }

            if (newProperties.containsKey(NOT_WORDS_KEY)) {
                notWords = newProperties.getProperty(NOT_WORDS_KEY) + " ";
            }

            if (newProperties.containsKey(NUMBER_CHARACTERS_KEY)) {
                numberCharacters = newProperties.getProperty(NUMBER_CHARACTERS_KEY);
            }
        }
        return result;
    }

    /**
     * Performs all preprocessing procedures as follows:
     * - linguistic analysis (each lemma is associated with the set of senses taken from the oracle).
     * - sense filtering (elimination of irrelevant to context structure senses)
     *
     * @param context context to be prepocessed
     * @throws ContextPreprocessorException ContextPreprocessorException
     */
    public void preprocess(IContext context) throws ContextPreprocessorException {
        unrecognizedWords.clear();
        // construct cLabs
        context = buildCLabs(context);
        // sense filtering
        context = findMultiwordsInContextStructure(context);
        try {
            senseFiltering(context);
        } catch (SenseMatcherException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new ContextPreprocessorException(errMessage, e);
        }

        log.debug("Unrecognized words: " + unrecognizedWords.size());        
        if (debugUnrecognizedWords) {
            TreeSet<String> sortedWords = new TreeSet<String>(unrecognizedWords);
            for (String unrecognizedWord : sortedWords) {
                log.debug("Unrecognized word: " + unrecognizedWord);
            }
        }
        unrecognizedWords.clear();
    }

    /**
     * Constructs cLabs for all nodes of the context.
     *
     * @param context context of node which cLab to be build
     * @return context with cLabs
     * @throws ContextPreprocessorException ContextPreprocessorException
     */
    private IContext buildCLabs(IContext context) throws ContextPreprocessorException {
        int counter = 0;
        int total = context.getRoot().getDescendantCount() + 1;
        int reportInt = (total / 20) + 1;//i.e. report every 5%

        for (Iterator<INode> i = context.getNodes(); i.hasNext();) {
            processNode(i.next());

            counter++;
            if ((SMatchConstants.LARGE_TREE < total) && (0 == (counter % reportInt)) && log.isEnabledFor(Level.INFO)) {
                log.info(100 * counter / total + "%");
            }
        }

        return context;
    }

    /**
     * Creates concept of a label formula.
     *
     * @param node node to process
     * @throws ContextPreprocessorException ContextPreprocessorException
     */
    private void processNode(INode node) throws ContextPreprocessorException {
        try {
            // reset old preprocessing
            node.getNodeData().setcLabFormula("");
            node.getNodeData().setcNodeFormula("");
            while (0 < node.getNodeData().getACoLCount()) {
                node.getNodeData().removeACoL(0);
            }

            int id_tok = 0;
            boolean isEmpty = true;
            String labelOfNode = node.getNodeData().getName().trim();

            if (debugLabels) {
                log.debug("preprocessing: " + labelOfNode);
            }

            labelOfNode = replacePunctuation(labelOfNode);
            labelOfNode = labelOfNode.toLowerCase();
            List<ISense> wnSense = new ArrayList<ISense>();
            if (!(("top".equals(labelOfNode) || "thing".equals(labelOfNode)) && !node.hasParent()) && (meaninglessWords.indexOf(labelOfNode + " ") == -1) && (isTokenMeaningful(labelOfNode))) {
                wnSense = linguisticOracle.getSenses(labelOfNode);
            }

            // identifiers of meaningful tokens in
            String meaningfulTokens = " ";
            // tokens of the label of node
            List<String> tokensOfNodeLabel = new ArrayList<String>();

            // is the label a WordNet entry?
            if (0 < wnSense.size()) {
                id_tok++;

                // add to list of processed labels
                tokensOfNodeLabel.add(labelOfNode);
                String lemma = linguisticOracle.getBaseForm(labelOfNode);

                // create atomic node of label
                IAtomicConceptOfLabel ACoL = createACoL(node, id_tok, labelOfNode, lemma);
                // to token ids
                meaningfulTokens = meaningfulTokens + id_tok + " ";
                // add senses to ACoL
                for (ISense sense : wnSense) {
                    ACoL.addSense(sense);
                }
                isEmpty = false;
            } else {
                // The label of node is not in WN
                // Split the label by words
                StringTokenizer lemmaTokenizer = new StringTokenizer(labelOfNode, " _()[]/'\\#1234567890");
                ArrayList<String> tokens = new ArrayList<String>();
                while (lemmaTokenizer.hasMoreElements()) {
                    tokens.add(lemmaTokenizer.nextToken());
                }

                // perform multiword recognition
                tokens = multiwordRecognition(tokens);
                // for all tokens in label
                for (int i = 0; i < tokens.size(); i++) {
                    String token = tokens.get(i).trim();
                    // if the token is not meaningless
                    if ((meaninglessWords.indexOf(token + " ") == -1) && (isTokenMeaningful(token))) {
                        // add to list of processed tokens
                        tokensOfNodeLabel.add(token);
                        id_tok++;
                        // if not logical connective
                        if ((andWords.indexOf(token) == -1) && ((orWords.indexOf(token)) == -1)
                                && ((notWords.indexOf(token)) == -1) && (!isNumber(token))) {
                            // get WN senses for token
                            if (!(("top".equals(token) || "thing".equals(token)) && !node.hasParent())) {
                                wnSense = linguisticOracle.getSenses(token);
                            } else {
                                wnSense = new ArrayList<ISense>();
                            }
                            if (0 == wnSense.size()) {
                                List<String> newTokens = complexWordsRecognition(token);
                                if (0 < newTokens.size()) {
                                    tokensOfNodeLabel.remove(tokensOfNodeLabel.size() - 1);
                                    tokensOfNodeLabel.add(newTokens.get(0));
                                    wnSense = linguisticOracle.getSenses(newTokens.get(0));
                                    tokens.remove(i);
                                    tokens.add(i, newTokens.get(0));
                                    for (int j = 1; j < newTokens.size(); j++) {
                                        String s = newTokens.get(j);
                                        tokens.add(i + j, s);
                                    }
                                }
                            }
                            String lemma = linguisticOracle.getBaseForm(token);

                            // create atomic node of label
                            IAtomicConceptOfLabel ACoL = createACoL(node, id_tok, token, lemma);
                            // mark id as meaningful
                            meaningfulTokens = meaningfulTokens + id_tok + " ";
                            // if there no WN senses
                            if (0 == wnSense.size() && !(("top".equals(labelOfNode) || "thing".equals(labelOfNode)) && !node.hasParent())) {
                                unrecognizedWords.add(token);
                            }
                            // add senses to ACoL
                            for (ISense sense : wnSense) {
                                ACoL.addSense(sense);
                            }
                            isEmpty = false;
                        }
                    }
                }
            }

            if (isEmpty) {
                String token = labelOfNode.replaceAll(" ", "_");
                id_tok++;
                // add to list of processed labels
                tokensOfNodeLabel.add(token);
                // create atomic node of label
                createACoL(node, id_tok, token, token);
                // to token ids
                meaningfulTokens = meaningfulTokens + id_tok + " ";
            }
            // build complex formula of a node
            buildComplexConcept(node, tokensOfNodeLabel, meaningfulTokens);
            node.getNodeData().setIsPreprocessed(true);
        } catch (LinguisticOracleException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new ContextPreprocessorException(errMessage, e);
        }
    }

    private IAtomicConceptOfLabel createACoL(INode node, int id, String token, String lemma) {
        IAtomicConceptOfLabel result = node.getNodeData().createACoL();
        result.setId(id);
        result.setToken(token);
        result.setLemma(lemma);
        node.getNodeData().addACoL(result);
        return result;
    }

    /**
     * Checks the token is meaningful or not.
     *
     * @param token the token
     * @return true if the token is meaningful
     */
    private boolean isTokenMeaningful(String token) {
        token = token.trim();
        return (andWords.indexOf(token) > -1) || ((orWords.indexOf(token)) > -1) || token.length() >= 3;
    }

    /**
     * Finds out if the input token is a complex word or not using WordNet. Tries to insert spaces and dash
     * between all characters and searchs for the result to be in WordNet.
     *
     * @param token token
     * @return a list which contains parts of the complex word
     * @throws ContextPreprocessorException ContextPreprocessorException
     */
    private List<String> complexWordsRecognition(String token) throws ContextPreprocessorException {
        List<String> result = new ArrayList<String>();
        try {
            List<ISense> senses = new ArrayList<ISense>();
            int i = 0;
            String start = null;
            String end = null;
            String toCheck = null;
            boolean flag = false;
            boolean multiword = false;
            while ((i < token.length() - 1) && (0 == senses.size())) {
                i++;
                start = token.substring(0, i);
                end = token.substring(i, token.length());
                toCheck = start + ' ' + end;
                senses = linguisticOracle.getSenses(toCheck);
                if (0 == senses.size()) {
                    toCheck = start + '-' + end;
                    senses = linguisticOracle.getSenses(toCheck);
                }

                if (0 < senses.size()) {
                    multiword = true;
                    break;
                } else {
                    if ((start.length() > 3) && (end.length() > 3)) {
                        senses = linguisticOracle.getSenses(start);
                        if (0 < senses.size()) {
                            senses = linguisticOracle.getSenses(end);
                            if (0 < senses.size()) {
                                flag = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (multiword) {
                result.add(toCheck);
                return result;
            }
            if (flag) {
                result.add(start);
                result.add(end);
                return result;
            }
            return result;
        } catch (LinguisticOracleException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new ContextPreprocessorException(errMessage, e);
        }
    }

    /**
     * Constructs the logical formula for the complex concept of label.
     *
     * @param node              node to build complex concept
     * @param tokensOfNodeLabel a list of tokens in the node label
     * @param meaningfulTokens  identifiers of the meaningful tokens
     */
    private void buildComplexConcept(INode node, List<String> tokensOfNodeLabel, String meaningfulTokens) {
        // label of node
        String token;
        // List of ACoLs identifiers
        List<String> vec = new ArrayList<String>();
        // formula for the complex concept
        StringBuilder formulaOfConcept = new StringBuilder();
        // logical connective
        String connective = " ";
        // brackets to add
        String bracket = "";
        // whether to insert brackets
        boolean insert;
        // how many left brackets do not have corresponding right ones
        int bracketsBalance = 0;
        // number of left brackets
        int leftBrackets = 0;
        // for each token of node label
        for (int i = 0; i < tokensOfNodeLabel.size(); i++) {
            token = (tokensOfNodeLabel.get(i));
            // If logical AND or OR
            if (andWords.indexOf(" " + token + " ") != -1 || orWords.indexOf(" " + token + " ") != -1) {
                insert = false;
                // If non first token
                if (vec != null && vec.size() > 0) {
                    // construct formula
                    if (connective.equals("")) {
                        formulaOfConcept.append(" | ").append(bracket).append(vec.toString());
                    } else {
                        formulaOfConcept.append(connective).append(bracket).append(vec.toString());
                    }
                    insert = true;
                    connective = "";
                    bracket = "";
                    vec = new ArrayList<String>();
                    leftBrackets = 0;
                }
                // If bracket
                if (token.equals("(") && bracketsBalance >= 0) {
                    connective = " & ";
                    bracket = "(";
                    bracketsBalance = bracketsBalance + 1;
                    leftBrackets = leftBrackets + 1;
                } else if (token.equals(")") && bracketsBalance > 0) {
                    if (insert) {
                        formulaOfConcept.append(")");
                    }
                    bracketsBalance = bracketsBalance - 1;
                } else {
                    connective = " | ";
                }
                // If logical not
            } else if (notWords.indexOf(" " + token + " ") != -1) {
                if (vec != null && vec.size() > 0) {
                    formulaOfConcept.append(connective).append(vec.toString());
                    vec = new ArrayList<String>();
                    connective = "";
                }
                // What to add
                if (connective.indexOf("&") != -1 || connective.indexOf("|") != -1) {
                    connective = connective + " ~ ";
                } else {
                    connective = " & ~ ";
                }
            } else {
                if (meaningfulTokens.indexOf(" " + (i + 1) + " ") != -1) {
                    // fill list with ACoL ids
                    vec.add((node.getNodeData().getId() + "." + (i + 1)));
                }
            }
        }
        // Dealing with first token of the node
        if (vec != null && vec.size() > 0) {
            //construct formula
            if (connective.indexOf("&") != -1 || connective.indexOf("|") != -1 || connective.equals(" ")) {
                formulaOfConcept.append(connective).append(bracket).append(vec.toString());
            } else {
                formulaOfConcept.append(" & ").append(vec.toString());
            }
            connective = "";
        } else {
            if (leftBrackets > 0) {
                bracketsBalance = bracketsBalance - leftBrackets;
            }
        }
        if (bracketsBalance > 0) {
            for (int i = 0; i < bracketsBalance; i++) {
                formulaOfConcept.append(")");
            }
        }
        // dealing with brackets
        String foc = formulaOfConcept.toString();
        foc = foc.replace('[', '(');
        foc = foc.replace(']', ')');
        foc = foc.replaceAll(", ", " & ");
        foc = foc.trim();
        if (foc.startsWith("&")) {
            StringTokenizer atoms = new StringTokenizer(foc, "&");
            foc = atoms.nextToken();
        }
        foc = foc.trim();
        if (foc.startsWith("|")) {
            StringTokenizer atoms = new StringTokenizer(foc, "|");
            foc = atoms.nextToken();
        }
        // bracket counters
        StringTokenizer open = new StringTokenizer(foc, "(", true);
        int openCount = 0;
        while (open.hasMoreTokens()) {
            String tmp = open.nextToken();
            if (tmp.equals("("))
                openCount++;
        }
        StringTokenizer closed = new StringTokenizer(foc, ")", true);
        while (closed.hasMoreTokens()) {
            String tmp = closed.nextToken();
            if (tmp.equals(")"))
                openCount--;
        }
        formulaOfConcept = new StringBuilder(foc);
        if (openCount > 0) {
            for (int par = 0; par < openCount; par++)
                formulaOfConcept.append(")");
        }
        if (openCount < 0) {
            for (int par = 0; par < openCount; par++)
                formulaOfConcept.insert(0, "(");
        }
        // assign formula to the node
        node.getNodeData().setcLabFormula(formulaOfConcept.toString());
    }

    /**
     * Replaces punctuation by spaces.
     *
     * @param lemma input string
     * @return string with spaces in place of punctuation
     */
    private static String replacePunctuation(String lemma) {
        lemma = lemma.replace(",", " , ");
        lemma = lemma.replace('.', ' ');
//        lemma = lemma.replace('-', ' ');
        lemma = lemma.replace('\'', ' ');
        lemma = lemma.replace('(', ' ');
        lemma = lemma.replace(')', ' ');
        lemma = lemma.replace(':', ' ');
        lemma = lemma.replace(";", " ; ");
        return lemma;
    }

    private List<ISense> checkMW(String source, String target) throws ContextPreprocessorException {
        try {
            ArrayList<ArrayList<String>> mwEnds = multiwords.get(source);
            if (mwEnds != null)
                for (ArrayList<String> strings : mwEnds) {
                    if (extendedIndexOf(strings, target, 0) > 0) {
                        return linguisticOracle.getSenses(source + " " + target);
                    }
                }
            return new ArrayList<ISense>();
        } catch (LinguisticOracleException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new ContextPreprocessorException(errMessage, e);
        }
    }

    private void enrichSensesSets(IAtomicConceptOfLabel acol, List<ISense> senses) {
        for (ISense sense : senses) {
            acol.addSense(sense);
        }
    }

    /**
     * Finds multiwords in context.
     *
     * @param context data structure of input label
     * @return context with multiwords
     * @throws ContextPreprocessorException ContextPreprocessorException
     */
    private IContext findMultiwordsInContextStructure(IContext context) throws ContextPreprocessorException {
        for (Iterator<INode> i = context.getNodes(); i.hasNext();) {
            INode sourceNode = i.next();
            // sense disambiguation within the context structure
            // for all ACoLs in the source node
            for (Iterator<IAtomicConceptOfLabel> j = sourceNode.getNodeData().getACoLs(); j.hasNext();) {
                IAtomicConceptOfLabel synSource = j.next();
                // in all descendants and ancestors
                findMultiwordsAmong(sourceNode.getDescendants(), synSource);
                findMultiwordsAmong(sourceNode.getAncestors(), synSource);
            }
        }
        return context;
    }

    private void findMultiwordsAmong(Iterator<INode> i, IAtomicConceptOfLabel synSource) throws ContextPreprocessorException {
        while (i.hasNext()) {
            INode targetNode = i.next();
            for (Iterator<IAtomicConceptOfLabel> k = targetNode.getNodeData().getACoLs(); k.hasNext();) {
                IAtomicConceptOfLabel synTarget = k.next();
                List<ISense> wnSenses = checkMW(synSource.getLemma(), synTarget.getLemma());
                enrichSensesSets(synSource, wnSenses);
                enrichSensesSets(synTarget, wnSenses);
            }
        }
    }


    /**
     * Eliminates the senses which do not suit to overall context meaning. Filters senses in two steps:
     * - filtering within node label
     * - filtering within context
     *
     * @param context context to perform sense filtering
     * @throws SenseMatcherException SenseMatcherException
     */
    private void senseFiltering(IContext context) throws SenseMatcherException {
        HashMap<IAtomicConceptOfLabel, List<ISense>> refinedSenses = new HashMap<IAtomicConceptOfLabel, List<ISense>>();

        for (Iterator<INode> i = context.getNodes(); i.hasNext();) {
            INode sourceNode = i.next();
            // if node is complex
            if (1 < sourceNode.getNodeData().getACoLCount()) {
                // for each ACoL in the node
                for (Iterator<IAtomicConceptOfLabel> j = sourceNode.getNodeData().getACoLs(); j.hasNext();) {
                    IAtomicConceptOfLabel sourceACoL = j.next();
                    // compare with all the other ACoLs in the node
                    for (Iterator<IAtomicConceptOfLabel> k = sourceNode.getNodeData().getACoLs(); k.hasNext();) {
                        IAtomicConceptOfLabel targetACoL = k.next();
                        if (!targetACoL.equals(sourceACoL)) {
                            // for each sense in source ACoL
                            for (Iterator<ISense> s = sourceACoL.getSenses(); s.hasNext();) {
                                ISense sourceSense = s.next();
                                // for each sense in target ACoL
                                for (Iterator<ISense> t = targetACoL.getSenses(); t.hasNext();) {
                                    ISense targetSense = t.next();
                                    if (senseMatcher.isSourceSynonymTarget(sourceSense, targetSense) ||
                                            senseMatcher.isSourceLessGeneralThanTarget(sourceSense, targetSense) ||
                                            senseMatcher.isSourceMoreGeneralThanTarget(sourceSense, targetSense)) {
                                        addToRefinedSenses(refinedSenses, sourceACoL, sourceSense);
                                        addToRefinedSenses(refinedSenses, targetACoL, targetSense);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // sense disambiguation within the context structure
            // for all ACoLs in the source node
            for (Iterator<IAtomicConceptOfLabel> j = sourceNode.getNodeData().getACoLs(); j.hasNext();) {
                IAtomicConceptOfLabel sourceACoL = j.next();
                List<ISense> refined = refinedSenses.get(sourceACoL);
                if (null == refined) {
                    for (Iterator<ISense> s = sourceACoL.getSenses(); s.hasNext();) {
                        ISense sourceSense = s.next();
                        // for all target nodes (ancestors and descendants)
                        senseFilteringAmong(sourceNode.getDescendants(), sourceSense, sourceACoL, refinedSenses);
                        senseFilteringAmong(sourceNode.getAncestors(), sourceSense, sourceACoL, refinedSenses);
                    }
                }
            }
        }

        // Loop on senses of the all concepts and assign to them
        // senses mark as refined on the previous step
        // If there are no refined senses save the original ones
        for (Iterator<INode> i = context.getNodes(); i.hasNext();) {
            for (Iterator<IAtomicConceptOfLabel> j = i.next().getNodeData().getACoLs(); j.hasNext();) {
                IAtomicConceptOfLabel acol = j.next();
                List<ISense> refined = refinedSenses.get(acol);
                if (null != refined) {
                    while (0 < acol.getSenseCount()) {
                        acol.removeSense(0);
                    }
                    for (ISense sense : refined) {
                        acol.addSense(sense);
                    }
                }
            }
        }
    }

    private void addToRefinedSenses(HashMap<IAtomicConceptOfLabel, List<ISense>> refinedSenses, IAtomicConceptOfLabel acol, ISense sense) {
        List<ISense> senses = refinedSenses.get(acol);
        if (null == senses) {
            senses = new ArrayList<ISense>();
        }
        senses.add(sense);
        refinedSenses.put(acol, senses);
    }

    private void senseFilteringAmong(Iterator<INode> i, ISense sourceSense, IAtomicConceptOfLabel sourceACoL, HashMap<IAtomicConceptOfLabel, List<ISense>> refinedSenses) throws SenseMatcherException {
        while (i.hasNext()) {
            INode targetNode = i.next();
            for (Iterator<IAtomicConceptOfLabel> k = targetNode.getNodeData().getACoLs(); k.hasNext();) {
                IAtomicConceptOfLabel targetACoL = k.next();
                if (null == refinedSenses.get(targetACoL)) {
                    for (Iterator<ISense> t = targetACoL.getSenses(); t.hasNext();) {
                        ISense targetSense = t.next();
                        // Check whether each sense not synonym or more general, less general then the senses of
                        // the ancestors and descendants of the node in context hierarchy
                        if ((senseMatcher.isSourceSynonymTarget(sourceSense, targetSense)) ||
                                (senseMatcher.isSourceLessGeneralThanTarget(sourceSense, targetSense)) ||
                                (senseMatcher.isSourceMoreGeneralThanTarget(sourceSense, targetSense))) {
                            addToRefinedSenses(refinedSenses, sourceACoL, sourceSense);
                            addToRefinedSenses(refinedSenses, targetACoL, targetSense);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks whether input string contains a number or not.
     *
     * @param in1 input string
     * @return false if it contains a number
     */
    private boolean isNumber(String in1) {
        for (StringTokenizer stringTokenizer = new StringTokenizer(in1, numberCharacters); stringTokenizer.hasMoreTokens();) {
            return false;
        }
        return true;
    }

    /**
     * An extension of the list indexOf method which uses approximate comparison of the words as
     * elements of the List.
     *
     * @param vec      list of strings
     * @param str      string to search
     * @param init_pos start position
     * @return position
     * @throws ContextPreprocessorException ContextPreprocessorException
     */
    private int extendedIndexOf(List<String> vec, String str, int init_pos) throws ContextPreprocessorException {
        try {
            // for all words in the input list starting from init_pos
            for (int i = init_pos; i < vec.size(); i++) {
                String vel = vec.get(i);
                // try syntactic
                if (vel.equals(str)) {
                    return i;
                } else if (vel.indexOf(str) == 0) {
                    // and semantic comparison
                    if (linguisticOracle.isEqual(vel, str)) {
                        vec.add(i, str);
                        vec.remove(i + 1);
                        return i;
                    }
                }
            }
            return -1;
        } catch (LinguisticOracleException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new ContextPreprocessorException(errMessage, e);
        }
    }

    /**
     * Takes as an input a list of words and returns the list consisting of the multiwords
     * which are in WN and can be derived from the input
     * <p/>
     * For example having [Earth, and, Atmospheric, Sciences] as the input returns
     * [Earth Sciences, and, Atmospheric, Sciences] because Earth Sciences is a WN concept
     * and Atmospheric Sciences is not a WN concept
     *
     * @param tokens input tokens
     * @return a list which contains multiwords
     * @throws ContextPreprocessorException ContextPreprocessorException
     */
    private ArrayList<String> multiwordRecognition(ArrayList<String> tokens) throws ContextPreprocessorException {
        String subLemma;
        HashMap<String, ArrayList<Integer>> is_token_in_multiword = new HashMap<String, ArrayList<Integer>>();
        for (int i = 0; i < tokens.size(); i++) {
            subLemma = tokens.get(i);
            if ((andWords.indexOf(subLemma) == -1) || (orWords.indexOf(subLemma) == -1)) {
                // if there a multiword starting with a sublemma
                if (multiwords.get(subLemma) != null) {
                    ArrayList<ArrayList<String>> entries = multiwords.get(subLemma);
                    for (ArrayList<String> mweTail : entries) {
                        boolean flag = false;
                        int co = 0;
                        // at the end co is needed to move pointer for the cases like
                        // Clupea harengus with mw Clupea harengus harengus
                        while ((co < mweTail.size()) && (extendedIndexOf(tokens, mweTail.get(co), co) > i + co)) {
                            flag = true;
                            co++;
                        }
                        if ((co > mweTail.size() - 1) && (flag)) {
                            ArrayList<Integer> positions = new ArrayList<Integer>();
                            int word_pos = tokens.indexOf(subLemma);
                            if (word_pos == -1)
                                break;
                            int multiword_pos = word_pos;
                            positions.add(word_pos);
                            boolean cont = true;
                            boolean connectives_prescendence = false;
                            int and_pos = -1;
                            for (String tok : mweTail) {
                                int old_pos = word_pos;
                                word_pos = tokens.subList(old_pos + 1, tokens.size()).indexOf(tok) + old_pos + 1;
                                if (word_pos == -1) {
                                    word_pos = extendedIndexOf(tokens, tok, old_pos);
                                    if (word_pos == -1)
                                        break;
                                }
                                if (word_pos - old_pos > 1) {
                                    cont = false;
                                    for (int r = old_pos + 1; r < word_pos; r++) {
                                        if (((andWords.indexOf(tokens.get(r))) > -1) || (orWords.indexOf(tokens.get(r)) > -1)) {
                                            and_pos = r;
                                            connectives_prescendence = true;
                                        } else {
                                            //connectives_prescendence = false;
                                        }
                                    }
                                }
                                positions.add(word_pos);
                            }
                            int removed_tokens_index_correction = 0;
                            if (cont) {
                                String multiword = "";
                                for (Integer integer : positions) {
                                    int pos = integer - removed_tokens_index_correction;
                                    multiword = multiword + tokens.get(pos) + " ";
                                    tokens.remove(pos);
                                    removed_tokens_index_correction++;
                                }
                                multiword = multiword.substring(0, multiword.length() - 1);
                                tokens.add(multiword_pos, multiword);
                            } else {
                                if (connectives_prescendence) {
                                    if (and_pos > multiword_pos) {
                                        String multiword = "";
                                        int word_distance = positions.get(positions.size() - 1) - positions.get(0);
                                        for (Integer integer : positions) {
                                            int pos = integer - removed_tokens_index_correction;
                                            if (is_token_in_multiword.get(tokens.get(pos)) == null) {
                                                ArrayList<Integer> toAdd = new ArrayList<Integer>();
                                                toAdd.add(1);
                                                toAdd.add(word_distance - 1);
                                                is_token_in_multiword.put(tokens.get(pos), toAdd);
                                            } else {
                                                ArrayList<Integer> toAdd = is_token_in_multiword.get(tokens.get(pos));
                                                int tmp = toAdd.get(0) + 1;
                                                toAdd.remove(0);
                                                toAdd.add(0, tmp);
                                                is_token_in_multiword.put(tokens.get(pos), toAdd);
                                            }
                                            multiword = multiword + tokens.get(pos) + " ";
                                        }
                                        multiword = multiword.substring(0, multiword.length() - 1);
                                        tokens.remove(multiword_pos);
                                        tokens.add(multiword_pos, multiword);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ArrayList<String> tmp = new ArrayList<String>();
        for (String s : tokens) {
            if (is_token_in_multiword.get(s) == null) {
                tmp.add(s);
            } else {
                ArrayList<Integer> toAdd = is_token_in_multiword.get(s);
                int dist_wo_ands_ors = toAdd.get(0);
                int multiword_participation = toAdd.get(1);
                if (dist_wo_ands_ors != multiword_participation)
                    tmp.add(s);
            }
        }
        return tmp;
    }

    /**
     * Create caches of WordNet to speed up matching.
     *
     * @param componentKey a key to the component in the configuration
     * @param properties   configuration
     * @throws SMatchException SMatchException
     */
    public static void createWordNetCaches(String componentKey, Properties properties) throws SMatchException {
        properties = getComponentProperties(makeComponentPrefix(componentKey, DefaultContextPreprocessor.class.getSimpleName()), properties);
        if (properties.containsKey(JWNL_PROPERTIES_PATH_KEY)) {
            // initialize JWNL (this must be done before JWNL library can be used)
            try {
                final String configPath = properties.getProperty(JWNL_PROPERTIES_PATH_KEY);
                log.info("Initializing JWNL from " + configPath);
                JWNL.initialize(new FileInputStream(configPath));
                log.info("Creating WordNet caches...");
                writeMultiwords(properties);
                log.info("Done");
            } catch (JWNLException e) {
                final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.error(errMessage, e);
                throw new SMatchException(errMessage, e);
            } catch (FileNotFoundException e) {
                final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.error(errMessage, e);
                throw new SMatchException(errMessage, e);
            }
        } else {
            final String errMessage = "Cannot find configuration key " + JWNL_PROPERTIES_PATH_KEY;
            log.error(errMessage);
            throw new SMatchException(errMessage);
        }
    }

    private static void writeMultiwords(Properties properties) throws SMatchException {
        log.info("Creating multiword hash...");
        HashMap<String, ArrayList<ArrayList<String>>> multiwords = new HashMap<String, ArrayList<ArrayList<String>>>();
        POS[] parts = new POS[]{POS.NOUN, POS.ADJECTIVE, POS.VERB, POS.ADVERB};
        for (POS pos : parts) {
            collectMultiwords(multiwords, pos);
        }
        log.info("Multiwords: " + multiwords.size());
        SMatchUtils.writeObject(multiwords, properties.getProperty(MULTIWORDS_FILE_KEY));
    }

    private static void collectMultiwords(HashMap<String, ArrayList<ArrayList<String>>> multiwords, POS pos) throws SMatchException {
        try {
            int count = 0;
            Iterator i = net.sf.extjwnl.dictionary.Dictionary.getInstance().getIndexWordIterator(pos);
            while (i.hasNext()) {
                IndexWord iw = (IndexWord) i.next();
                String lemma = iw.getLemma();
                if (-1 < lemma.indexOf(' ')) {
                    count++;
                    if (0 == count % 10000) {
                        log.info(count);
                    }
                    String[] tokens = lemma.split(" ");
                    ArrayList<ArrayList<String>> mwEnds = multiwords.get(tokens[0]);
                    if (null == mwEnds) {
                        mwEnds = new ArrayList<ArrayList<String>>();
                    }
                    ArrayList<String> currentMWEnd = new ArrayList<String>(Arrays.asList(tokens));
                    currentMWEnd.remove(0);
                    mwEnds.add(currentMWEnd);
                    multiwords.put(tokens[0], mwEnds);
                }
            }
            log.info(pos.getKey() + " multiwords: " + count);
        } catch (JWNLException e) {
            final String errMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error(errMessage, e);
            throw new SMatchException(errMessage, e);
        }
    }

    /**
     * Loads the hashmap with multiwords. The multiwords are stored in the following format:
     * Key - the first word in the multiwords
     * Value - List of Lists, which contain the other words in the all the multiwords starting with key.
     *
     * @param fileName the file name from which the hashmap will be read
     * @return multiwords hashmap
     * @throws SMatchException SMatchException
     */
    @SuppressWarnings("unchecked")
    private static HashMap<String, ArrayList<ArrayList<String>>> readHash(String fileName) throws SMatchException {
        return (HashMap<String, ArrayList<ArrayList<String>>>) SMatchUtils.readObject(fileName);
    }
}