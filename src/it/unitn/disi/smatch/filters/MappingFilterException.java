package it.unitn.disi.smatch.filters;

import it.unitn.disi.smatch.SMatchException;

/**
 * Exception for Mapping Filters.
 *
 * @author Aliaksandr Autayeu avtaev@gmail.com
 */
public class MappingFilterException extends SMatchException {

    public MappingFilterException(String errorDescription) {
        super(errorDescription);
    }

    public MappingFilterException(String errorDescription, Throwable cause) {
        super(errorDescription, cause);
    }
}
