//
// NodeModifyAction.java
//
// Copyright:
// RSC Commercial Services OHG, Duesseldorf (Germany)
// All rights reserved.
//
//
package de.quaddy_services.mvn.plugin.unused;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Insert the type description here.
 * 
 * @author <br>dt0b35
 * @since 13.11.2012 23:06:34
 */
public interface NodeModifyAction {


	/**
	 * Insert the method description here.
	 * 
	 * @param aAnUpdateDoc
	 * @param aFoundDependency
	 * @author dt0b35
	 * @since 13.11.2012 23:21:16
	 */
	void modify(Document aAnUpdateDoc, Node aFoundDependency);

}

