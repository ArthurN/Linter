package org.linter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import net.htmlparser.jericho.CharacterReference;
import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.Source;

import org.apache.log4j.Logger;

public class LintedPage {
	static private Logger logger = Logger.getLogger(LintedPage.class);
	
	private static final Pattern URL_PATTERN = Pattern.compile(
			"^((\\w+)://)?" +
			"((\\w+):?(\\w+)?@)?" + 
			"([^/\\?:]+)" +
			":?" +
			"(\\d+)?" +
			"(/?[^\\?]+)?" +
			"\\??" +
			"([^#]+)?" +
			"#?(\\w*)");
	private static final String RELATIVE_URL_TEST = "://";
	public static final String HTTP_USER_AGENT = "Mozilla/5.0 (compatible; Linter/1.0)"; // our own custom user agent based on Googlebot
	public static final int HTTP_CONNECT_TIMEOUT = 10000;	// 10 sec
	public static final int HTTP_READ_TIMEOUT = 5000;	// 5 sec
	public static final int HTTP_MAX_CONTENT_LENGTH = 1048576; 	// 1 MB in bytes 
	
	private boolean _parseOk = false;
	private String _parseError;
	
	private String _originalUrl;
	private String[] _aliases = {};
	private String _destinationUrl;
	private String _title;
	private String _description;
	private String _favIconUrl;
	private String _providerName;
	private String _providerUrl;
	
	private long _processingTime;
	
	/**
	 * Create a blank linted page, expects you to call {@link process} at some point 
	 * @param originalUrl - the URL to be processed
	 */
	public LintedPage(String originalUrl) {
		_originalUrl = originalUrl;
	}
	
	/***
	 * Process the original URL, including alias resolution, scraping and metadata extraction
	 */
	void process() {
		final long startTime = System.nanoTime();
		final long endTime;
		try {
			processRunner();
		} finally {
		  endTime = System.nanoTime();
		}
		_processingTime = endTime - startTime;
	}
	
	/***
	 * Process the original URL associated with this LintedPage
	 */
	protected void processRunner() {
		logger.info("Processing URL: " + _originalUrl);
		
		logger.debug("Expanding any shortened URLs...");
		if (followUrlRedirects()) {
			logger.debug("Scraping & cleaning HTML...");
			scrapeMetadata();
		}
	}
	
	/***
	 * Follows the originalUrl to its destination, saving any aliases along the way. This is useful to expand
	 * URL shortening services.
	 * @return True if successful, false otherwise
	 */
	public boolean followUrlRedirects() {
		ArrayList<String> aliases = new ArrayList<String>();
		
		String currentLocation = _originalUrl;
		String lastLocation = null;
		
		while (currentLocation != null) {
			try {
				URL url = new URL(currentLocation);
				
				logger.trace("Following " + currentLocation + "...");
				
				HttpURLConnection connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
				connection.setInstanceFollowRedirects(false);
				connection.setRequestMethod("HEAD"); // only want the headers
				connection.setConnectTimeout(LintedPage.HTTP_CONNECT_TIMEOUT);
				connection.setReadTimeout(LintedPage.HTTP_READ_TIMEOUT);
				connection.setRequestProperty("User-Agent", LintedPage.HTTP_USER_AGENT);
				if (lastLocation != null)
					connection.setRequestProperty("Referer", lastLocation);
				connection.connect();
				
				/*
				Map<String, List<String>> headers = connection.getHeaderFields();
				for (String key : headers.keySet())
					System.out.println("HEAD " + key + "=" + headers.get(key));
				*/
				
				String nextLocation = connection.getHeaderField("Location");
				if (nextLocation != null) {
					// Did we get a relative redirect?
					if (!nextLocation.contains(LintedPage.RELATIVE_URL_TEST)) {
						String prefix;
						
						Matcher m = getUrlMatcher(currentLocation);
						if (m.matches()) {
							prefix = m.group(1) + m.group(6);
						} else {
							// graceful degradation (not that great but it should work) -- find everything to the left of the 1st '/' after ://
							int endIndex = currentLocation.indexOf('/', currentLocation.indexOf(LintedPage.RELATIVE_URL_TEST) + LintedPage.RELATIVE_URL_TEST.length());
							prefix = currentLocation.substring(0, endIndex);
						}
						
						logger.trace("Relative URL redirect. Appending prefix: " + prefix);
						nextLocation = prefix + nextLocation;
					}
					
					if (nextLocation.equals(currentLocation) || aliases.contains(nextLocation)) {
						logger.trace("Discovered loop redirect. Not following redirect to " + nextLocation);
						_destinationUrl = currentLocation;
						currentLocation = null;
					} else {
						logger.trace("Discovered redirect to " + nextLocation);
						aliases.add(currentLocation);
						lastLocation = currentLocation;
						currentLocation = nextLocation;
					}
				} else {
					logger.trace("URL resolved to its destination: " + currentLocation);
					_destinationUrl = currentLocation;
					currentLocation = null;
				}
				connection.disconnect();
			} catch (MalformedURLException ex) {
				logger.error("Invalid URL [" + currentLocation + "]: " + ex);
				_parseError = ex.toString();
				return false;
			} catch (IOException ioe) {
				logger.error("IO Exception [" + currentLocation + "]: " + ioe);
				_parseError = ioe.toString();
				return false;
			} catch (Exception ex) {
				logger.error("Exception [" + currentLocation + "]: " + ex);
				_parseError = ex.toString();
				return false;
			}
		}
		
		_aliases = aliases.toArray(new String[0]);

		return true;
	}
	
	/**
	 * Scrapes the metadata on this page (can be called separately from {@link process}
	 */
	public void scrapeMetadata() {
		final String logPrefix = "[" + this.getDestinationUrl() + "] ";
		
		logger.trace(logPrefix + "Downloading and scraping page contents...");
		
		InputStream inStr = null;
		HttpURLConnection connection = null;
		try {
			URL url = new URL(this.getDestinationUrl());
			connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
			connection.setConnectTimeout(LintedPage.HTTP_CONNECT_TIMEOUT);
			connection.setRequestProperty("User-Agent", LintedPage.HTTP_USER_AGENT);
			connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
			connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
			
			String contentType = connection.getContentType();
			if (contentType == null)
				contentType = "unknown";
			if (!contentType.toLowerCase().contains("text/html") && !contentType.toLowerCase().contains("text/plain")) {
				logger.warn(logPrefix + "Not downloading or scraping page because content-type was: " + contentType);
				return;
			}
			
			int contentLength = connection.getContentLength();
			if (contentLength > LintedPage.HTTP_MAX_CONTENT_LENGTH) {
				logger.warn(logPrefix + "Not downloading or scraping page because content-length was too large: " + Integer.toString(contentLength) + " (max: " + Integer.toString(LintedPage.HTTP_MAX_CONTENT_LENGTH) + ")");
				return;
			}
			
			String encoding = connection.getContentEncoding();
			if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
			    inStr = new GZIPInputStream(connection.getInputStream());
			} else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
			    inStr = new InflaterInputStream(connection.getInputStream(),
			      new Inflater(true));
			} else {
			    inStr = connection.getInputStream();
			}
		} catch (FileNotFoundException fnf) {
			_parseError = "HTTP ERROR 404";
			logger.error(logPrefix + " " + _parseError);
			return;
		} catch (IOException ioe) {
			try {
				_parseError = "HTTP ERROR " + Integer.toString(connection.getResponseCode());
			} catch (IOException e) {
				_parseError = " Unable to download page: " + e;
			}
			logger.error(logPrefix + " " + _parseError);
			return;
		} catch (Exception ex) {
			logger.error(logPrefix + "Unable to download page: " + ex);
			_parseError = ex.toString();
			return;
		}
		
		// Jericho HTML Parser
		
		//source.setLogger(logger);
		Source source = null;
		try {
			 source = new Source(inStr);
		} catch (Exception ex) {
			logger.error(logPrefix + "Unable to parse HTML: " + ex.toString());
			_parseError = ex.toString();
			return;
		}

		// Page title
		logger.trace(logPrefix + "Scraping page title...");
		try {
			Element titleElement = source.getFirstElement(HTMLElementName.TITLE);
			if (titleElement != null) {
				_title = CharacterReference.decodeCollapseWhiteSpace(titleElement.getContent());
				logger.trace(logPrefix + "TITLE: " + _title);
			}
			else {
				logger.trace(logPrefix + "Could not extract the page title");
			}
		} catch (Exception ex) {
			logger.error(logPrefix + "Error extracting page title: ", ex);
		}
		
		// Description
		// NOTE: we assume that the first element with attribute name="description" is the meta description tag; else this will fail
		logger.trace(logPrefix + "Scraping description...");
		try {
			Element descElement = source.getFirstElement("name", "description", false);
			if (descElement != null && descElement.getName().equalsIgnoreCase(HTMLElementName.META)) {
				String contentAttr = descElement.getAttributeValue("content");
				if (contentAttr != null)
					_description = CharacterReference.decodeCollapseWhiteSpace(contentAttr);
			}
			
			if (_description != null) {
				logger.trace(logPrefix + "DESCRIPTION: " + _description);
			} else {
				logger.trace(logPrefix + "Could not extract the page description");
			}
		} catch (Exception ex) {
			logger.error(logPrefix + "Error extracting page description: ", ex);
		}
		
		// Favicon
		logger.trace(logPrefix + "Scraping favicon URL...");
		try {
			// Get a list of all 'icon' and 'shortcut icon' elements
			List<Element> relIconElements = new ArrayList<Element>();
			relIconElements.addAll(source.getAllElements("rel", "icon", false));
			relIconElements.addAll(source.getAllElements("rel", "shortcut icon", false));
			
			for (Element element : relIconElements) {
				if (element.getName().equalsIgnoreCase(HTMLElementName.LINK)) {
					String hrefAttr = element.getAttributeValue("href");
					if (hrefAttr != null) {
						_favIconUrl = CharacterReference.decodeCollapseWhiteSpace(hrefAttr);
						break;
					}
				}
			}
			
			if (_favIconUrl != null) {
				if (!_favIconUrl.contains(LintedPage.RELATIVE_URL_TEST)) {
					logger.trace("Relative URL for favicon. Prefixing provider URL: " + getProviderUrl());
					_favIconUrl = getProviderUrl() + _favIconUrl;
				}
				
				logger.trace(logPrefix + "FAVICON URL: " + _favIconUrl);
			} else {
				logger.trace("[" + this.getDestinationUrl() + "] Could not extract the fav icon URL");
			}
		} catch (Exception ex) {
			logger.error(logPrefix + "Error extracting fav icon URL: ", ex);
		}
		
		logger.trace(logPrefix + "Scraping complete.");
		
		_parseOk = true;
	}
	
	/***
	 * Whether or not the parse completed successfully
	 * @return
	 */
	public Boolean getParseOk() {
		return _parseOk;
	}
	
	/**
	 * The error message we encountered during parsing, if any
	 * @return
	 */
	public String getParseError() {
		return _parseError;
	}
	
	/***
	 * Returns the original URL (before any shortened URLs were expanded)
	 * @return
	 */
	public String getOriginalUrl() {
		return _originalUrl;
	}
	
	/***
	 * Gets any aliases for this URL -- e.g. any redirects between the original URL and its actual destination
	 * @return
	 */
	public String[] getAliases() {
		return _aliases;
	}
	
	/***
	 * Gets the page title
	 * @return
	 */
	public String getTitle() {
		return _title;
	}
	
	/***
	 * Gets the final destination, after expanding any shortened URLs starting from the original URL
	 * @return
	 */
	public String getDestinationUrl() {
		// If we failed to parse, the destination URL might not be set, so let's just make it the original URL since
		// that's the best we can do.
		if (_destinationUrl == null && !getParseOk())
			_destinationUrl = _originalUrl;
		return _destinationUrl;
	}
	
	/***
	 * Gets the page descripton (from <meta name='description'>)
	 * @return
	 */
	public String getDescription() {
		return _description;
	}
	
	/***
	 * Gets the page's favicon (from <link rel='icon'>)
	 * @return
	 */
	public String getFavIconUrl() {
		return _favIconUrl;
	}
	
	/***
	 * Gets the processing time in milliseconds
	 * @return
	 */
	public long getProcessingTimeMillis() {
		return TimeUnit.NANOSECONDS.toMillis(_processingTime);
	}
	
	/***
	 * Gets the processing time in human-readable format
	 * @return
	 */
	public String getProcessingTimeForHumans() {

		// mm:ss is:
		//	    TimeUnit.NANOSECONDS.toMinutes(_processingTime),
		//	    TimeUnit.NANOSECONDS.toSeconds(_processingTime) - 
		//	    TimeUnit.MINUTES.toSeconds(TimeUnit.NANOSECONDS.toMinutes(_processingTime))
		
		Float millis = new Float(getProcessingTimeMillis() / 1000);
		return String.format("%.4f", millis);
	}
	
	public String toDebugString() {
		StringBuilder sb = new StringBuilder(_originalUrl);
		sb.append(" {\n");
		sb.append("\tPARSE OK:\t\t"); sb.append(this.getParseOk()); sb.append('\n');
		if (!this.getParseOk()) {
			sb.append("\tPARSE ERROR:\t\t"); sb.append(this.getParseError()); sb.append('\n');
		}
		sb.append("\tALIASES:");
			if (_aliases == null || _aliases.length == 0)
				sb.append("\t\tNONE\n");
			else {
				sb.append('\n');
				for (String alias : _aliases) {
					sb.append("\t\t"); sb.append(alias); sb.append('\n');
				}
			}
		sb.append("\tDEST URL:\t\t"); sb.append(this.getDestinationUrl()); sb.append('\n');
		sb.append("\tPAGE TITLE:\t\t"); sb.append(this.getTitle()); sb.append('\n');
		sb.append("\tDESCRIPTION:\t\t"); sb.append(this.getDescription()); sb.append('\n');
		sb.append("\tFAV ICON:\t\t"); sb.append(this.getFavIconUrl()); sb.append('\n');
		sb.append("\tPROVIDER NAME:\t\t"); sb.append(this.getProviderName()); sb.append('\n');
		sb.append("\tPROVIDER URL:\t\t"); sb.append(this.getProviderUrl()); sb.append('\n');
		sb.append("} in "); sb.append(this.getProcessingTimeForHumans()); sb.append(" s\n");
		return sb.toString();
	}
	
	public String getProviderUrl() {
		if (_providerUrl == null) {
			parseProviderNameAndUrl(_destinationUrl);
		}
		
		return _providerUrl;
	}
	
	public String getProviderName() {
		if (_providerName == null) {
			// Force the parsing via getProviderUrl
			getProviderUrl();
		}
		
		return _providerName;
	}
	
	/**
	 * Returns the provider URL 
	 * From: http://www.webtalkforums.com/showthread.php/37600-Simple-JavaScript-RegEx-to-Parse-Domain-Name
	 * @param url
	 * @return
	 */
	private void parseProviderNameAndUrl(String url) {
		Matcher m = getUrlMatcher(url);
		if (m.matches()) {
			_providerUrl = m.group(1) + m.group(6);
			_providerName = m.group(6).replace("www.", "");
		} else {
			// graceful degradation (not that great but it should work) -- find everything to the left of the 1st '/' after ://
			logger.trace("Graceful degradation on parsing provider name/url");
			int endIndex = url.indexOf('/', url.indexOf(LintedPage.RELATIVE_URL_TEST) + LintedPage.RELATIVE_URL_TEST.length());
			_providerUrl = url.substring(0, endIndex);
			_providerName = _providerUrl.replace("http://", "").replace("https://", "").replace("www.", "");
		}
	}
	
	private Matcher getUrlMatcher(String url) {
		return LintedPage.URL_PATTERN.matcher(url);
	}
}
