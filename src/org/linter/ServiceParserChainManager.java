package org.linter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * Manage ServiceParser Chains of Responsibility
 * Linter and consumers register ServiceParsers capable of parsing all or specific URLs
 * LintedPages requires ServiceParsers appropriate for their URL
 */
public class ServiceParserChainManager {
	
	// Logging
	static protected Logger logger = Logger.getLogger( ServiceParserChainManager.class );
	
	// Singleton instance
	private static ServiceParserChainManager _instance = null;

	// Mapping or registered services and their patterns
	private HashMap<Pattern, Class<?> > _servicePatterns;
	
	/*
	 * Get ChainManager Instance
	 * @return ServiceParserChainManager singleton instance
	 */
	public static ServiceParserChainManager getInstance() {
		if( _instance == null ) {
			_instance = new ServiceParserChainManager();
		}
		return _instance;
	}
	
	/*
	 * Private constructor
	 */
	private ServiceParserChainManager() {
		_servicePatterns = new HashMap<Pattern, Class<?> >();
	}
	
	/*
	 * Register a ServiceParser with the ChainManager
	 * @param serviceParserClass Class name of ServiceParser type
	 */
	public void registerServiceParser(Class<?> serviceParserClass) {
		logger.info( "Registering ServiceParser type: " + serviceParserClass );
		
		try {
			ServiceParser parser = (ServiceParser) serviceParserClass.newInstance();
			_servicePatterns.put( parser.getServicePattern(), serviceParserClass );
		} catch( Exception e ) {
			logger.error( "Exception instantiating ServiceParser class: " + e );
		}
		
	}
	
	/*
	 * Get ServiceParser chain appropriate for URL
	 * @param url 
	 * @return ServiceParser all relevant ServiceParsers from registered list
	 */
	public ServiceParser getServiceParser(String url) {
		logger.trace( "Determining appropriate ServiceParsers for url: " + url );
		
		ArrayList<ServiceParser> parserList = new ArrayList<ServiceParser>();
		
		for( Pattern p : _servicePatterns.keySet() ) {

			if( p != null && p.matcher( url ).matches() ) {
				try {
					ServiceParser parser = (ServiceParser) _servicePatterns.get( p ).newInstance();					
					parserList.add( parser );
					logger.trace( "Found parser: " + parser.getClass() );
				} catch( Exception e ) {
					logger.error( "Failed to instantiate ServiceParser: " + e );
				}
			}
		}
		
		if( parserList.size() == 0 ) {
			parserList.add( new ServiceParserAlgorithmic() );
		}
		
		sortParserListByComplexity( parserList );		
		ServiceParser ret = linkParserList( parserList );
		ret.initialize( url );
		return ret;
	}

	/*
	 * Sort Parser List by Complexity
	 * REMOVE/MODIFY
	 */
	private void sortParserListByComplexity( ArrayList<ServiceParser> parserList ) {
		boolean finished = false;
		
		while( !finished ) {
			boolean changes = false;
			
			for( int i = 0; i < parserList.size() - 1; i++ ) {
				if( parserList.get( i + 1 ).getServicePattern().toString().length() > parserList.get( i ).getServicePattern().toString().length() ) {
					ServiceParser temp = parserList.get( i + 1 );
					parserList.set( i + 1, parserList.get( i ) );
					parserList.set( i, temp );
					changes = true;
				}
			}
			
			if( !changes ) {
				finished = true;
			}
		}
	}
	
	/*
	 * Link Parser List
	 * Link ServiceParser array into a linked list
	 */
	private ServiceParser linkParserList( ArrayList<ServiceParser> parserList ) {
		
		for( int i = 0; i < parserList.size() - 1; i++ ) {
			parserList.get( i ).setSuccessor( parserList.get( i + 1 ) );
		}
		
		return parserList.get( 0 );
	}
}