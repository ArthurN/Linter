package org.linter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.htmlparser.jericho.Source;

import org.apache.log4j.Logger;

/**
 * Parse meta data from a web page. abstract base class for all parsers
 */
public abstract class ServiceParser {
	
	/**
	 * Log4J Logger
	 */
	static protected Logger logger = Logger.getLogger( ServiceParser.class );
	
	/**
	 * Pattern for determining full vs. relative URLs
	 */
	protected static final String RELATIVE_URL_TEST = "://";
	
	/**
	 * Pattern for matching specific parts of a URL
	 */
	protected static final Pattern URL_PATTERN = Pattern.compile(
			"^((\\w+)://)?" +
			"((\\w+):?(\\w+)?@)?" + 
			"([^/\\?:]+)" +
			":?" +
			"(\\d+)?" +
			"(/?[^\\?]+)?" +
			"\\??" +
			"([^#]+)?" +
			"#?(\\w*)");
	
	/**
	 *  Raw HTML
	 */
	protected InputStream _rawContent = null;
	
	/**
	 *  URL
	 */
	protected String _url;
	
	/**
	 * URL redirection list
	 */
	protected ArrayList<String> _redirectUrlList;
	
	/**
	 * Parse error state
	 */
	protected String _parseError;
	
	/**
	 *  Fallback option for parsing additional content or failure
	 */
	private ServiceParser _successor;
	
	/**
	 *  Jericho Source Parser
	 */
	protected Source _jerichoSource;
	
	/**
	 *  Meta Data
	 */
	protected LintedData _metaData;	

	
	
	/**
	 * Constructor
	 */
	public ServiceParser() {		
		_successor = null;
		_url = "";

		_metaData = new LintedData();
		_metaData.put( "meta_provider", "linter" );
		_redirectUrlList = null;
		_parseError = null;
	}

	/**
	 * Initialize ServiceParser and all successors 
	 * Not included in constructor for simplicity when dynamically instantiating classes
	 * 
	 * @param url	Full URL of web page
	 */
	public void initialize(String url) {
		_url = url;		
		parseProviderNameAndUrl();
		
		// Recursively initialize successors
		if( _successor != null ) {
			_successor.initialize( _url );
		}
	}
	
	/**
	 * Get the ServicePattern used to match the appropriateness of a ServiceParser 
	 * for a particular URL
	 * 
	 * @return Pattern used for determining appropriateness of ServiceParser for a URL
	 */
	abstract public Pattern getServicePattern();
	
	/**
	 * Parse meta data from raw HTML
	 * 
	 * @return boolean true if successful
	 */
	abstract public boolean parse();
	
	/**
	 * Set the raw HTML used by the parser
	 * @param rawContent	InputStream of HTML source for use with Jericho parser
	 */
	public void setRawContent(InputStream rawContent) {
		_rawContent = rawContent;
		initJerichoSource();
	}
	
	/**
	 * Set the successor ServiceParser for failure case or extracting additional
	 * meta data not provided by more specific parsers
	 * 
	 * @param successor	Next parser in the ServiceParser chain of responsibility
	 */
	public void setSuccessor(ServiceParser successor) {
		_successor = successor;
	}
	
	/**
	 * Set the URL Redirection List for ServiceParsers that need access to
	 * all URLs leading to the final, resolved URL
	 * 
	 * Sometimes useful for parsers that need to backtrack if the final, resolved
	 * URL requires login or is behind a paywall
	 * 
	 * @param redirectUrlList	List of all redirection URLs leading to current URL
	 */
	public void setRedirectUrlList( ArrayList<String> redirectUrlList ) {
		_redirectUrlList = redirectUrlList;
	}
	
	/**
	 * Continue parsing with successor ServiceParser, if available
	 * 
	 * Forwards all meta data to successor and merges back with itself
	 * on completion
	 * 
	 * @return true if success, false if failed or end of chain
	 */
	protected boolean parseWithSuccessor() {
		boolean ret = false;
				
		if( _successor != null ) {
			_successor.setJerichoSource( getJerichoSource() );
			_successor.setMetaData( getMetaData() );
			ret  = _successor.parse();
			if( ret ) {
				getMetaData().mergeLintedData( _successor.getMetaData() );
				setParseError( _successor.getParseError() );
			}
		}
		
		return ret;
	}
	
	/**
	 * Set the Jericho Source
	 * 
	 * @param source	Jericho source HTML parser
	 */
	public void setJerichoSource( Source source ) {
		_jerichoSource = source;
	}
	
	/**
	 * Get the Provider URL from the full URL
	 * 
	 * @return Provider url
	 */
	public String getProviderUrl() {
		return getMetaData().getString( "provider_url" );
	}	

	/**
	 * Determine the provider name and url from url
	 * from: http://www.webtalkforums.com/showthread.php/37600-Simple-JavaScript-RegEx-to-Parse-Domain-Name 
	 */
	private void parseProviderNameAndUrl() {
		String providerUrl;
		String providerName;
		
		Matcher m = URL_PATTERN.matcher( _url );
		if (m.matches()) {
			providerUrl = m.group(1) + m.group(6);
			providerName = m.group(6).replace("www.", "");
		} else {
			// graceful degradation (not that great but it should work) -- find everything to the left of the 1st '/' after ://
			logger.trace("Graceful degradation on parsing provider name/url");
			int endIndex = _url.indexOf('/', _url.indexOf(RELATIVE_URL_TEST) + RELATIVE_URL_TEST.length());
			providerUrl = _url.substring(0, endIndex);
			providerName = providerUrl.replace("http://", "").replace("https://", "").replace("www.", "");
		}
		
		getMetaData().put( "provider_name", providerName );
		getMetaData().put( "provider_url", providerUrl );
	}
			
	/**
	 * Initialize Jerichio parser from raw HTML
	 */
	private void initJerichoSource() {		
		try {
			 _jerichoSource = new Source(_rawContent);
		} catch (Exception ex) {
			logger.error( "Exception initializing Jericho source: " + ex );
		}
	}
	
	/**
	 * Get current Jericho parser
	 * @return Jericho parser
	 */
	protected Source getJerichoSource() {
		return _jerichoSource;
	}

	/**
	 * Get meta data JSON
	 * 
	 * @return LintedData with all parsed meta data
	 */
	protected LintedData getMetaData() {
		return _metaData;
	}	
	
	/**
	 * Get Parse Error
	 * 
	 * @return Parse error string
	 */
	public String getParseError() {
		return _parseError;
	}
	
	/**
	 * Set Parse Error
	 * 
	 * @param parseError	Parse error string
	 */
	protected void setParseError( String parseError ) {
		_parseError = parseError;
	}

	/**
	 * Determine if this is a partial parser
	 * 
	 * @return true if partial parser
	 */
	public boolean isPartialParser() {
		
		Class<?> cls = this.getClass().getSuperclass();
		while( cls != null ) {
			if( cls == ServiceParserPartial.class ) {
				return true;
			}
			cls = cls.getSuperclass();
		}
		
		return false;
	}
	
	/**
	 * Set Meta Data
	 * 
	 * @param LintedData metaData
	 */
	public void setMetaData( LintedData metaData ) {
		_metaData = metaData;
	}
	
	/**
	 * Get URL
	 * 
	 * @return URL
	 */
	public String getUrl() {
		return _url;
	}
	
	/**
	 * Get URL redirection list
	 * 
	 * @return URL Redirection list
	 */
	protected ArrayList<String> getRedirectUrlList() {
		return _redirectUrlList;
	}

}
