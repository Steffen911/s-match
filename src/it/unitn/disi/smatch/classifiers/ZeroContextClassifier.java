package it.unitn.disi.smatch.classifiers;

import it.unitn.disi.smatch.components.Configurable;
import it.unitn.disi.smatch.data.IContext;

/**
 * Does nothing.
 *
 * @author Mikalai Yatskevich mikalai.yatskevich@comlab.ox.ac.uk
 * @author Aliaksandr Autayeu avtaev@gmail.com
 */

public class ZeroContextClassifier extends Configurable implements IContextClassifier {

    public void buildCNodeFormulas(IContext context) throws ContextClassifierException {
    }
}