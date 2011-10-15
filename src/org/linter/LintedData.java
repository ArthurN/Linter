package org.linter;

import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;

/*
 * Wrapper for JSONObject to allow easy writing and merging
 */
public class LintedData {
	static protected Logger logger = Logger.getLogger( LintedData.class );
	
	// Meta data hash map
	HashMap<String, Object> _data;
	
	public LintedData() {
		_data = new HashMap<String, Object>();
	}

	/*
	 * Get the meta data hash map
	 * @return HashMap<String,Object> meta data
	 */
	public HashMap<String, Object> getData() {
		return _data;
	}
	
	/*
	 * Merge meta data from another parser
	 * Overwrites existing meta data for like fields.
	 * @param source Merge data
	 */	
	public void mergeLintedData( LintedData source ) {

		Iterator<String> itr = source.getData().keySet().iterator();
		
		while( itr.hasNext() ) {
			String key = itr.next();
			Object value = get( key );
			put( key, value );
		}		
	}

	/*
	 * Get Object from Meta Data
	 * @param key
	 * @return Object
	 */
	public Object get( String key ) {
		return _data.get( key );
	}
	
	/*
	 * Get Object from Meta Data as String
	 * @param key
	 * @return String
	 */
	public String getString( String key ) {
		return _data.get( key ).toString();
	}
	
	/*
	 * Pub Object into Meta Data
	 * @param key
	 * @param value
	 */
	public void put( String key, Object value ) {
		_data.put( key, value );				
	}
	
	/*
	 * Write Meta Data to Formatted String
	 * @return String
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
