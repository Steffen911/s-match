package it.unitn.disi.smatch.preprocessors;

import it.unitn.disi.smatch.components.IConfigurable;
import it.unitn.disi.smatch.data.trees.IContext;

/**
 * An interface for context preprocessors.
 *
 * @author Mikalai Yatskevich mikalai.yatskevich@comlab.ox.ac.uk
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public interface IContextPreprocessor extends IConfigurable {

    /**
     * This method translates natural language labels of a context into logical formulas.
     *
     * @param context context to be preprocessed
     * @throws ContextPreprocessorException ContextPreprocessorException
     */
    public void preprocess(IContext context) throws ContextPreprocessorException;
}