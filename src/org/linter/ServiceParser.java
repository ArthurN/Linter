package org.linter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.htmlparser.jericho.Source;

import org.apache.log4j.Logger;

/**
 * ServiceParser
 * 
 * Parse meta data from a web page. Abstract class for developing
 * new parsers
 */
public abstract class ServiceParser {
	
	static protected Logger logger = Logger.getLogger( ServiceParser.class );
	
	protected static final String RELATIVE_URL_TEST = "://";
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
	
	// Raw HTML
	protected InputStream _rawContent = null;
	
	// URL
	protected String _url;
	
	protected ArrayList<String> _redirectUrlList;
	
	// Error state
	protected String _parseError;
	
	// Fallback option for parsing additional content or failure
	private ServiceParser _successor;
	
	// Jericho Source Parser
	protected Source _jerichoSource;
	
	// Meta Data
	protected LintedData _metaData;	
	
		
	public ServiceParser() {		
		_successor = null;
		_url = "";
		//_metaData = new JSONObject();
		_metaData = new LintedData();
		_redirectUrlList = null;
	}

	/*
	 * Initialize ServiceParser
	 * Not included in constructor for simplicity when dynamically instantiating classes
	 */
	public void initialize(String url) {
		_url = url;		
		parseProviderNameAndUrl();		
	}
	
	/*
	 * Get the ServicePattern used to match the appropriateness
	 * of a ServiceParser for a particular URL
	 * @return Pattern used for determining appropriateness of ServiceParser for a URL
	 */
	abstract public Pattern getServicePattern();
	
	/*
	 * Parse meta data from raw HTML
	 * @return boolean true if successful
	 */
	abstract public boolean parse();
	
	/*
	 * Set the raw HTML used by the parser
	 * @param InputStream HTML
	 */
	public void setRawContent(InputStream rawContent) {
		_rawContent = rawContent;
		initJerichoSource();
	}
	
	/*
	 * Set the successor ServiceParser for failure case or extracting additional
	 * meta data not provided by more specific parsers.
	 */
	public void setSuccessor(ServiceParser successor) {
		_successor = successor;
	}
	
	/*
	 * Set the URL Redirection List
	 * @param redirectUrlList
	 */
	public void setRedirectUrlList( ArrayList<String> redirectUrlList ) {
		_redirectUrlList = redirectUrlList;
	}
	
	/*
	 * Continue parsing with successor ServiceParser, if available
	 * @return true if success, false if failed or end of chainj
	 */
	protected boolean parseWithSuccessor() {
		boolean ret = false;
		
		if( _successor != null ) {
			_successor.setJerichoSource( getJerichoSource() );
			ret  = _successor.parse();
			if( ret ) {
				getMetaData().mergeLintedData( _successor.getMetaData() );
			}
		}
		
		return ret;
	}
	
	/*
	 * Set the Jericho Source
	 * @param Source Jericho source HTML parser
	 */
	public void setJerichoSource(Source source) {
		_jerichoSource = source;
	}
	
	/*
	 * Get the Provider URL from the URL
	 * @return String provider url
	 */
	public String getProviderUrl() {
		return getMetaData().getString( "provider_url" );
	}	

	/**
	 * Determine the provider name and url from url 
	 * From: http://www.webtalkforums.com/showthread.php/37600-Simple-JavaScript-RegEx-to-Parse-Domain-Name
	 * @param url
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
			
	/*
	 * Initialize Jerichio parser from raw HTML
	 */
	private void initJerichoSource() {		
		try {
			 _jerichoSource = new Source(_rawContent);
		} catch (Exception ex) {
			logger.error( "Exception initializing Jericho source: " + ex );
		}
	}
	
	/*
	 * Get current Jericho parser
	 * @return Jericho parser
	 */
	protected Source getJerichoSource() {
		return _jerichoSource;
	}

	/*
	 * Get meta data JSON
	 * @return Meta data
	 */
	protected LintedData getMetaData() {
		return _metaData;
	}	
}
