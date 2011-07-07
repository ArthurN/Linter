package com.linter;

import java.util.ArrayList;

import org.apache.log4j.Logger;

public class LintedPage {
	static private Logger logger = Logger.getLogger(LintedPage.class);
	
	String _originalUrl;
	String[] _aliases = {};
	
	public LintedPage(String originalUrl) {
		_originalUrl = originalUrl;
	}
	
	/***
	 * Process the original URL associated with this LintedPage
	 */
	public void process() {
		logger.info("Processing URL: " + _originalUrl);
		
		logger.debug("Expanding any shortened URLs...");
		ArrayList<String> aliases = Linter.expandShortenedUrls(_originalUrl);
		if (aliases != null && aliases.size() > 0)
			_aliases = aliases.toArray(new String[0]);
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
	 * Gets the final destination, after expanding any shortened URLs starting from the original URL
	 * @return
	 */
	public String getDestinationUrl() {
		if (_aliases.length == 0)
			return _originalUrl;
		else
			// The last alias should be the final URL
			return _aliases[_aliases.length - 1];
	}
	
	public String toDebugString() {
		StringBuilder sb = new StringBuilder(_originalUrl);
		sb.append(" {\n");
		sb.append("\tALIASES:");
			if (_aliases.length == 0)
				sb.append("\tNONE\n");
			else {
				sb.append('\n');
				for (String alias : _aliases) {
					sb.append("\t\t"); sb.append(alias); sb.append('\n');
				}
			}
		sb.append("\tDESTINATION URL:\t\t"); sb.append(this.getDestinationUrl()); sb.append('\n');
		sb.append("}\n");
		return sb.toString();
	}
}
