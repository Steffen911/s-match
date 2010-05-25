package it.unitn.disi.smatch.matchers.structure.node;

import it.unitn.disi.smatch.data.ling.IAtomicConceptOfLabel;
import it.unitn.disi.smatch.data.mappings.IContextMapping;
import it.unitn.disi.smatch.data.trees.INode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Node matcher for {@link it.unitn.disi.smatch.matchers.structure.tree.OptimizedStageTreeMatcher} for minimal links
 * matching. For comments on the code see {@link it.unitn.disi.smatch.matchers.structure.node.DefaultNodeMatcher}
 *
 * @author Aliaksandr Autayeu avtaev@gmail.com
 */
public class OptimizedStageNodeMatcher extends BaseNodeMatcher implements INodeMatcher {

    /**
     * Checks whether source node and target node are disjoint.
     *
     * @param acolMapping mapping between acols
     * @param nmtAcols    node -> list of node matching task acols
     * @param sourceACoLs mapping acol id -> acol object
     * @param targetACoLs mapping acol id -> acol object
     * @param sourceNode  interface of source node
     * @param targetNode  interface of target node
     * @return true if the nodes are in disjoint relation
     * @throws NodeMatcherException NodeMatcherException
     */
    public boolean nodeDisjoint(IContextMapping<IAtomicConceptOfLabel> acolMapping,
                                Map<INode, ArrayList<IAtomicConceptOfLabel>> nmtAcols,
                                Map<String, IAtomicConceptOfLabel> sourceACoLs, Map<String, IAtomicConceptOfLabel> targetACoLs,
                                INode sourceNode, INode targetNode) throws NodeMatcherException {
        boolean result = false;
        String sourceCNodeFormula = sourceNode.getNodeData().getcNodeFormula();
        String targetCNodeFormula = targetNode.getNodeData().getcNodeFormula();
        String sourceCLabFormula = sourceNode.getNodeData().getcLabFormula();
        String targetCLabFormula = targetNode.getNodeData().getcLabFormula();

        if (null != sourceCNodeFormula && null != targetCNodeFormula && !"".equals(sourceCNodeFormula) && !"".equals(targetCNodeFormula) &&
                null != sourceCLabFormula && null != targetCLabFormula && !"".equals(sourceCLabFormula) && !"".equals(targetCLabFormula)
                ) {
            HashMap<IAtomicConceptOfLabel, String> hashConceptNumber = new HashMap<IAtomicConceptOfLabel, String>();
            Object[] obj = mkAxioms(hashConceptNumber, nmtAcols, acolMapping, sourceNode, targetNode);
            String axioms = (String) obj[0];
            int num_of_axiom_clauses = (Integer) obj[1];

            ArrayList<ArrayList<String>> contextA = parseFormula(hashConceptNumber, sourceACoLs, sourceNode);
            ArrayList<ArrayList<String>> contextB = parseFormula(hashConceptNumber, targetACoLs, targetNode);
            String contextAInDIMACSFormat = DIMACSfromList(contextA);
            String contextBInDIMACSFormat = DIMACSfromList(contextB);

            String satProblemInDIMACS = axioms + contextBInDIMACSFormat + contextAInDIMACSFormat;
            int numberOfClauses = contextA.size() + contextB.size() + num_of_axiom_clauses;
            int numberOfVariables = hashConceptNumber.size();
            String DIMACSproblem = "p cnf " + numberOfVariables + " " + numberOfClauses + "\n" + satProblemInDIMACS;

            result = isUnsatisfiable(DIMACSproblem);
        }
        return result;
    }

    /**
     * Checks whether the source node is subsumed by the target node.
     *
     * @param acolMapping mapping between acols
     * @param nmtAcols    node -> list of node matching task acols
     * @param sourceACoLs mapping acol id -> acol object
     * @param targetACoLs mapping acol id -> acol object
     * @param sourceNode  interface of source node
     * @param targetNode  interface of target node
     * @return true if the nodes are in subsumption relation
     * @throws NodeMatcherException NodeMatcherException
     */
    public boolean nodeSubsumedBy(IContextMapping<IAtomicConceptOfLabel> acolMapping,
                                  Map<INode, ArrayList<IAtomicConceptOfLabel>> nmtAcols,
                                  Map<String, IAtomicConceptOfLabel> sourceACoLs, Map<String, IAtomicConceptOfLabel> targetACoLs,
                                  INode sourceNode, INode targetNode) throws NodeMatcherException {
        boolean result = false;
        String sourceCNodeFormula = sourceNode.getNodeData().getcNodeFormula();
        String targetCNodeFormula = targetNode.getNodeData().getcNodeFormula();
        String sourceCLabFormula = sourceNode.getNodeData().getcLabFormula();
        String targetCLabFormula = targetNode.getNodeData().getcLabFormula();

        if (null != sourceCNodeFormula && null != targetCNodeFormula && !"".equals(sourceCNodeFormula) && !"".equals(targetCNodeFormula) &&
                null != sourceCLabFormula && null != targetCLabFormula && !"".equals(sourceCLabFormula) && !"".equals(targetCLabFormula)
                ) {
            if (sourceNode.getNodeData().getSource()) {
                HashMap<IAtomicConceptOfLabel, String> hashConceptNumber = new HashMap<IAtomicConceptOfLabel, String>();
                Object[] obj = mkAxioms(hashConceptNumber, nmtAcols, acolMapping, sourceNode, targetNode);
                String axioms = (String) obj[0];
                int num_of_axiom_clauses = (Integer) obj[1];

                ArrayList<ArrayList<String>> contextA = parseFormula(hashConceptNumber, sourceACoLs, sourceNode);
                ArrayList<ArrayList<String>> contextB = parseFormula(hashConceptNumber, targetACoLs, targetNode);
                String contextAInDIMACSFormat = DIMACSfromList(contextA);

                ArrayList<ArrayList<String>> negatedContext = new ArrayList<ArrayList<String>>();
                //LG test
                Integer numberOfVariables = negateFormulaInList(hashConceptNumber, contextB, negatedContext);
                String satProblemInDIMACS = axioms + contextAInDIMACSFormat + DIMACSfromList(negatedContext);
                Integer numberOfClauses = num_of_axiom_clauses + contextA.size() + negatedContext.size();
                String DIMACSproblem = "p cnf " + numberOfVariables + " " + numberOfClauses + "\n" + satProblemInDIMACS;

                result = isUnsatisfiable(DIMACSproblem);
            } else {
                //swap source, target and relation
                HashMap<IAtomicConceptOfLabel, String> hashConceptNumber = new HashMap<IAtomicConceptOfLabel, String>();
                Object[] obj = mkAxioms(hashConceptNumber, nmtAcols, acolMapping, targetNode, sourceNode);
                String axioms = (String) obj[0];
                int num_of_axiom_clauses = (Integer) obj[1];

                ArrayList<ArrayList<String>> contextA = parseFormula(hashConceptNumber, sourceACoLs, targetNode);
                ArrayList<ArrayList<String>> contextB = parseFormula(hashConceptNumber, targetACoLs, sourceNode);
                String contextBInDIMACSFormat = DIMACSfromList(contextB);

                ArrayList<ArrayList<String>> negatedContext = new ArrayList<ArrayList<String>>();
                //MG test
                Integer numberOfVariables = negateFormulaInList(hashConceptNumber, contextA, negatedContext);
                String satProblemInDIMACS = axioms + contextBInDIMACSFormat + DIMACSfromList(negatedContext);
                Integer numberOfClauses = num_of_axiom_clauses + contextB.size() + negatedContext.size();
                String DIMACSproblem = "p cnf " + numberOfVariables + " " + numberOfClauses + "\n" + satProblemInDIMACS;

                result = isUnsatisfiable(DIMACSproblem);
            }
        }
        return result;
    }

    // stub to allow it to be created as node matcher.

    public char nodeMatch(IContextMapping<IAtomicConceptOfLabel> acolMapping,
                          Map<INode, ArrayList<IAtomicConceptOfLabel>> nmtAcols,
                          Map<String, IAtomicConceptOfLabel> sourceACoLs,
                          Map<String, IAtomicConceptOfLabel> targetACoLs,
                          INode sourceNode, INode targetNode) throws NodeMatcherException {
        throw new NodeMatcherException("Unsupported operation");
    }
}