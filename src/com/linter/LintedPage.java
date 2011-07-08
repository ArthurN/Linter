package com.linter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

public class LintedPage {
	static private Logger logger = Logger.getLogger(LintedPage.class);
	
	String _originalUrl;
	String[] _aliases = {};
	String _title;
	String _description;
	TagNode _node;
	
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
		
		logger.debug("Scraping & cleaning HTML...");
		HtmlCleaner cleaner = new HtmlCleaner();
		try {
			_node = cleaner.clean(new URL(this.getDestinationUrl()));
		} catch (MalformedURLException mue) {
			logger.error("Invalid URL [" + this.getDestinationUrl() + "]: " + mue.toString());
			return;
		} catch (Exception ex) {
			logger.error("Unable to scrape and clean HTML: " + ex.toString());
			return;
		}

		// Page title
		try {
			Object[] titleNodes = _node.evaluateXPath("//head/title");
			if (titleNodes != null) {
				_title = ((TagNode)titleNodes[0]).getText().toString().trim();
			}
		} catch (Exception e) {
			logger.warn("Could not extract the page title: " + e.toString());
		}
		
		// Page description
		try {
			Object[] descNodes = _node.evaluateXPath("//head/meta[@name='description']");
			if (descNodes != null) {
				_description = ((TagNode)descNodes[0]).getAttributeByName("content").trim();
			}
		} catch (Exception e) {
			logger.warn("Could not extract the page description: " + e.toString());
		}
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
		if (_aliases.length == 0)
			return _originalUrl;
		else
			// The last alias should be the final URL
			return _aliases[_aliases.length - 1];
	}
	
	/***
	 * Gets the page descripton (from <meta name='description'>)
	 * @return
	 */
	public String getDescription() {
		return _description;
	}
	
	public String toDebugString() {
		StringBuilder sb = new StringBuilder(_originalUrl);
		sb.append(" {\n");
		sb.append("\tALIASES:");
			if (_aliases.length == 0)
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
		sb.append("}\n");
		return sb.toString();
	}
}
