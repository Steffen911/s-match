package it.unitn.disi.annotation.pipelines;

import it.unitn.disi.annotation.pipelines.components.TagCounter;
import it.unitn.disi.nlptools.components.PipelineComponentException;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Reports tag counts.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class TagCounterContextPipeline extends BaseContextPipeline {

    private static final Logger log = Logger.getLogger(TagCounterContextPipeline.class);

    @Override
    public void beforeProcessing() throws PipelineComponentException {
        TagCounter.getTagCounts().clear();
        super.beforeProcessing();
    }

    @Override
    public void afterProcessing() throws PipelineComponentException {
        super.afterProcessing();
        Map<String, Long> tagCounts = TagCounter.getTagCounts();
        //sort by counts and print
        MapValueComparator<String, Long> mvc = new MapValueComparator<String, Long>(tagCounts);
        ArrayList<String> tags = new ArrayList<String>(tagCounts.keySet());
        Collections.sort(tags, mvc);

        for (String tag : tags) {
            log.info(tag + "\t" + tagCounts.get(tag));
        }
    }

    private static class MapValueComparator<A, B extends Comparable<? super B>> implements Comparator<A> {

        private Map<A, B> base;

        public MapValueComparator(Map<A, B> base) {
            this.base = base;
        }

        public int compare(A a, A b) {
            return base.get(b).compareTo(base.get(a));
        }
    }
}
