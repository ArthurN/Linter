package org.linter;

/**
 * Classes deriving from Service Parser Partial are meta data parsers 
 * that are assumed to be responsible for retrieving only a small 
 * or consumer-specific portion of meta data.
 * 
 * Structurally, they are no different from ServiceParsers, but
 * are run last in the chain
 */
public abstract class ServiceParserPartial extends ServiceParser {

}
