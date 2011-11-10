package org.linter;

import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;

/**
 * Wrapper for Hash to allow easy writing and merging
 */
public class LintedData {
	
	/**
	 * Log4J Logger
	 */
	static protected Logger logger = Logger.getLogger( LintedData.class );
	
	/**
	 *  Meta data hash map
	 */
	private HashMap<String, Object> _data;
	
	/**
	 * Constructor
	 */
	public LintedData() {
		_data = new HashMap<String, Object>();
	}

	/**
	 * Get the meta data hash map
	 * @return HashMap<String,Object> meta data
	 */
	public HashMap<String, Object> getData() {
		return _data;
	}
	
	/**
	 * Merge meta data from another parser, overwrites existing meta data for like fields
	 * @param source	LintedData to merge with this object 
	 */	
	public void mergeLintedData( LintedData source ) {

		Iterator<String> itr = source.getData().keySet().iterator();
		
		while( itr.hasNext() ) {
			String key = itr.next();
			Object value = get( key );
			put( key, value );
		}		
	}

	/**
	 * Get object by key
	 * @param key		Key of object
	 * @return Object	Object matching key, null if DNE
	 */
	public Object get( String key ) {
		return _data.get( key );
	}
	
	/**
	 * Determine if LintedData has a record matching the key
	 * @param key		Key of object
	 * @return 			true if object exists in data
	 */
	public boolean hasKey( String key ) {
		return _data.containsKey( key );
	}
	
	/**
	 * Get object by key as String
	 * @param key		Key of object
	 * @return 			Object matching key as String, null if DNE
	 */
	public String getString( String key ) {
		String ret = null;
		if( _data.containsKey( key ) ) {
			ret = _data.get( key ).toString(); 
		}
		return ret;
	}
	
	/**
	 * Store object in data, overwrites existing
	 * @param key		Key of object to store
	 * @param value		Object to store
	 */
	public void put( String key, Object value ) {
		_data.put( key, value );				
	}
	
	/**
	 * Write data to formatted string
	 * @return Formatted string
	 */
	public String getPrettyDebugString() {
		String ret = new String();

		Iterator<String> itr = _data.keySet().iterator();
		while( itr.hasNext() ) {
			String key = itr.next();
			Object value = get( key );
			ret += "\t" + key + ": " + value.toString() + "\n";
		}
		
		return ret;
	}
}
