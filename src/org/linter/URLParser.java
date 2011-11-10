package org.linter;

import java.util.HashMap;

/**
 * Miscellaneous URL functions 
 */
public class URLParser {
	
	/**
	 * Remove a list of parameters from a URL
	 * 
	 * @param url				URL with parameters to remove
	 * @param removeParameters	List of unwanted parameters to remove
	 * @return 					Original url with all unwanted parameters removed
	 */
	public static String removeParameters( String url, String[] removeParameters ) {		
		
		// Bail on bad data
		if( url == null ||
			url.isEmpty() ||
			removeParameters == null ||
			removeParameters.length == 0 ) {
			return url;
		}
			
		// Parse Parameter List
		String[] urlComponents = url.split( "\\?" );
		
		// Bail if URL doesn't contain exactly 1 questionmark. No parameters, or something weird
		if( urlComponents.length != 2 || urlComponents[1].isEmpty() ) {
			return url;
		}
		
		// Parse each parameter
		HashMap<String, String> parameterMap = new HashMap<String, String>();
		String[] parameterList = urlComponents[1].split( "&" );
		
		for( String parameter : parameterList ) {		
			String[] parameterComponents = parameter.split( "=" );
			if( parameterComponents.length == 2 && !parameterComponents[0].isEmpty() && !parameterComponents[1].isEmpty() ) {		
				parameterMap.put( parameterComponents[0].trim(), parameterComponents[1].trim() );
			}
		}

		// Remove Parameters
		for( String removeParameter : removeParameters ) {
			if( parameterMap.containsKey( removeParameter ) ) {
				parameterMap.remove( removeParameter );
			}
		}
		
		// Resassemble URL
		String ret = urlComponents[0];
		if( parameterMap.size() > 0 ) {
			ret += "?";
			for( String key : parameterMap.keySet() ) {
				ret += key + "=" + parameterMap.get( key ) + "&";				
			}
		}
		if( ret.lastIndexOf( "&" ) == ( ret.length() - 1 ) ) {
			ret = ret.substring( 0, ret.length() - 1 );
		}
		
		return ret;
	}
	
}
