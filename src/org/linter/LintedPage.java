package org.linter;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
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
	
	public static final String HTTP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.6; rv:5.0.1) Gecko/20100101 Firefox/5.0.1"; // Firefox 5.0.1 on Snow Leopard
	public static final int HTTP_CONNECT_TIMEOUT = 5000;	// 5 sec
	
	private boolean _parseOk = false;
	private String _parseError;
	
	private String _originalUrl;
	private String[] _aliases = {};
	private String _destinationUrl;
	private String _title;
	private String _description;
	private String _favIconUrl;
	
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
				connection.setRequestProperty("User-Agent", LintedPage.HTTP_USER_AGENT);
				if (lastLocation != null)
					connection.setRequestProperty("Referer", lastLocation);
				connection.connect();
				
				String nextLocation = connection.getHeaderField("Location");
				if (nextLocation != null) {
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
					logger.trace("URL resolved to its destination");
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
			}
		}
		
		_aliases = aliases.toArray(new String[0]);

		return true;
	}
	
	/**
	 * Scrapes the metadata on this page (can be called separately from {@link process}
	 */
	public void scrapeMetadata() {
		InputStream inStr = null;
		try {
			URL url = new URL(this.getDestinationUrl());
			HttpURLConnection connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
			connection.setConnectTimeout(LintedPage.HTTP_CONNECT_TIMEOUT);
			connection.setRequestProperty("User-Agent", LintedPage.HTTP_USER_AGENT);
			connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
			
			String encoding = connection.getContentEncoding();
	
			if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
			    inStr = new GZIPInputStream(connection.getInputStream());
			} else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
			    inStr = new InflaterInputStream(connection.getInputStream(),
			      new Inflater(true));
			} else {
			    inStr = connection.getInputStream();
			}
		} catch (Exception ex) {
			logger.error("Unable to download page [" + this.getDestinationUrl() + "]: " + ex);
			_parseError = ex.toString();
			return;
		}
		
		// Jericho HTML Parser
		
		//source.setLogger(logger);
		Source source = null;
		try {
			 source = new Source(inStr);
		} catch (Exception ex) {
			logger.error("Unable to parse HTML: " + ex.toString());
			_parseError = ex.toString();
			return;
		}

		// Page title
		Element titleElement = source.getFirstElement(HTMLElementName.TITLE);
		if (titleElement != null)
			_title = CharacterReference.decodeCollapseWhiteSpace(titleElement.getContent());
		else
			logger.trace("[" + this.getDestinationUrl() + "] Could not extract the page title");

		// Description
		// NOTE: we assume that the first element with attribute name="description" is the meta description tag; else this will fail
		Element descElement = source.getFirstElement("name", "description", false);
		if (descElement != null && descElement.getName().equalsIgnoreCase(HTMLElementName.META))
			_description = CharacterReference.decodeCollapseWhiteSpace(descElement.getAttributeValue("content"));
		else
			logger.trace("[" + this.getDestinationUrl() + "] Could not extract the page description");
		
		// Favicon
		// NOTE: we assume that the first element with attribute rel="icon" is the link icon tag; else this will fail
		Element faviconElement = source.getFirstElement("rel", "icon", false);
		if (faviconElement != null  && faviconElement.getName().equalsIgnoreCase(HTMLElementName.LINK))
			_favIconUrl = CharacterReference.decodeCollapseWhiteSpace(faviconElement.getAttributeValue("href"));
		else
			logger.trace("[" + this.getDestinationUrl() + "] Could not extract the fav icon URL");
		
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
		sb.append("} in "); sb.append(this.getProcessingTimeForHumans()); sb.append(" s\n");
		return sb.toString();
	}
}
