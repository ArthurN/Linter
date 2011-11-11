package org.linter;

import org.apache.log4j.Logger;

/**
 * Linter command-line application
 */
public class Linter {
	
	/**
	 * Log4J Logger
	 */
	static private Logger logger = Logger.getLogger(Linter.class);
	
	/**
	 * Run Linter as a command-line to optionally test URLs via CLI
	 * @param args	List of URLs to resolve
	 */
	public static void main(String[] args) {				
		if (args.length == 0) {
			System.out.println("Invalid number of arguments. You must include at least one URL to process.");
			System.exit(1);
		}
		
		logger.info("Running Linter");
		
		// Register additional ServiceParsers
		ServiceParserChainManager.getInstance().registerServiceParser( ServiceParserTypesetter.class );
		
		LintedPage lp;
		for (int i = 0; i < args.length; i++)
		{
			lp = Linter.processUrl(args[i]);			
			System.out.println(lp.toDebugString());
		}
	}
	
	/**
	 * Process a url
	 * @param url	URL to process
	 * @return		Scraped LintedPage
	 */
	private static final LintedPage processUrl(String url) {
		LintedPage lp = new LintedPage(url);
		lp.process();
		return lp;
	}
	
	
}
