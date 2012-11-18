//
// NodeModifyAction.java
//
// Copyright:
// RSC Commercial Services OHG, Duesseldorf (Germany)
// All rights reserved.
//
//
package de.quaddyservices.mvn.plugin.unused;

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
	 * modify
	 * @param aAnUpdateDoc
	 * @param aFoundDependency
	 * @param aDependencyId void
	 */
	void modify(Document aAnUpdateDoc, Node aFoundDependency, String aDependencyId);

}

