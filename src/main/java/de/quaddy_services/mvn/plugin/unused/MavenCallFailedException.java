//
// MavenCallFailedException.java
//
// Copyright:
// RSC Commercial Services OHG, Duesseldorf (Germany)
// All rights reserved.
//
//
package de.quaddy_services.mvn.plugin.unused;

/**
 * Insert the type description here.
 * 
 * @author <br>dt0b35
 * @since 13.11.2012 21:15:14
 */
public class MavenCallFailedException extends Exception {

	/**
	 * Constructor for MavenCallFailedException 
	 *
	 * @param aMessage
	 * @param aCause
	 */
	public MavenCallFailedException(String aMessage, Throwable aCause) {
		super(aMessage, aCause);
	}

	/**
	 * Constructor for MavenCallFailedException 
	 *
	 * @param aMessage
	 */
	public MavenCallFailedException(String aMessage) {
		super(aMessage);
	}

	/**
	 * Constructor for MavenCallFailedException 
	 *
	 * @param aE
	 */
	public MavenCallFailedException(Throwable e) {
		super(e);
	}

}
