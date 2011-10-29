package it.unitn.disi.nlptools.components.utils;

import it.unitn.disi.nlptools.components.PipelineComponentException;
import it.unitn.disi.nlptools.data.ILabel;
import it.unitn.disi.nlptools.data.IToken;
import it.unitn.disi.nlptools.pipelines.PipelineComponent;
import org.apache.log4j.Logger;

/**
 * Reports senseless tokens.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class SenselessTokensReporter extends PipelineComponent {

    private static final Logger log = Logger.getLogger(SenselessTokensReporter.class);

    public void process(ILabel label) throws PipelineComponentException {
        for (IToken token : label.getTokens()) {
            if (0 == token.getSenses().size()) {
                log.debug("Unrecognized word: " + token.getText());
            }
        }
    }
}
