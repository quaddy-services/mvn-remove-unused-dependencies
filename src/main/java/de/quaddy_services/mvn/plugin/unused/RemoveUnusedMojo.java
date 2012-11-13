package de.quaddy_services.mvn.plugin.unused;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Removed unused
 *
 * @goal remove
 * @requiresProject true
 * 
 */
public class RemoveUnusedMojo extends AbstractMojo {
	/**
	 * Complete Project pom.xml as Object
	 * 
	 * @parameter default-value="${project}"
	 * @required
	 * @readonly
	 */
	private MavenProject project;

	@Override
	public void execute() throws MojoExecutionException {
		Log tempLog = getLog();
		tempLog.info("Hello, world.");
		tempLog.info("Current Dir = " + new File(".").getAbsolutePath());
		File tempPomFile = project.getFile();
		tempLog.info("Project.file = " + tempPomFile);

		try {
			callMaven("package", tempPomFile.getName(), tempPomFile.getParentFile());
		} catch (MavenCallFailedException e) {
			throw new MojoExecutionException("clean package goal must work!", e);
		}
		Document tempDoc;
		try {
			DocumentBuilderFactory tempDBF = DocumentBuilderFactory.newInstance();
			tempDBF.setNamespaceAware(true);

			DocumentBuilder tempNewDocumentBuilder = tempDBF.newDocumentBuilder();

			tempDoc = tempNewDocumentBuilder.parse(tempPomFile);
			tempLog.debug("tempDoc.childNodes.length=" + tempDoc.getChildNodes().getLength());
		} catch (Exception e) {
			throw new MojoExecutionException("Initialization Error", e);
		}
		List<Dependency> tempDependencies = project.getDependencies();
		boolean tempModified = false;
		for (Dependency tempDependency : tempDependencies) {
			String tempScope = tempDependency.getScope();
			if (tempScope == null || tempScope.equals("compile")) {
				Document tempModifiedDoc = removeDependency(tempDoc, tempDependency, tempPomFile.getParent()
						+ "/pom-temp.xml");
				if (tempModifiedDoc != null) {
					try {
						callMaven("package", "pom-temp.xml", tempPomFile.getParentFile());
						tempLog.info("Dependency " + tempDependency.getArtifactId() + " is not needed");
						tempDoc = tempModifiedDoc;
						tempModified = true;
					} catch (MavenCallFailedException e) {
						tempLog.debug("Dependency " + tempDependency.getArtifactId() + " is needed for compile");
						tempModifiedDoc = changeScope(tempDoc, tempDependency, tempPomFile.getParent()
								+ "/pom-temp.xml", "test");
						try {
							callMaven("package", "pom-temp.xml", tempPomFile.getParentFile());
							tempLog.info("Dependency " + tempDependency.getArtifactId() + " is for test only");
							tempDoc = tempModifiedDoc;
							tempModified = true;
						} catch (MavenCallFailedException e2) {
							tempLog.debug("Dependency " + tempDependency.getArtifactId() + " is needed for test");
						}
					}
				}
			} else if (tempScope.equals("test")) {
				Document tempModifiedDoc = removeDependency(tempDoc, tempDependency, tempPomFile.getParent()
						+ "/pom-temp.xml");
				if (tempModifiedDoc != null) {
					try {
						callMaven("package", "pom-temp.xml", tempPomFile.getParentFile());
						tempLog.info("Dependency " + tempDependency.getArtifactId() + " is not needed");
						tempDoc = tempModifiedDoc;
						tempModified = true;
					} catch (MavenCallFailedException e) {
						tempLog.debug("Dependency " + tempDependency.getArtifactId() + " is needed for test");
					}
				}
			}
		}
		if (tempModified) {
			tempLog.info("Update " + tempPomFile);
			try {
				// Use a Transformer for output
				TransformerFactory tFactory = TransformerFactory.newInstance();
				Transformer transformer = tFactory.newTransformer();
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");

				DOMSource source = new DOMSource(tempDoc);
				StreamResult result = new StreamResult(tempPomFile);
				transformer.transform(source, result);
			} catch (Exception e) {
				throw new MojoExecutionException("Error writing to " + tempPomFile, e);
			}
			tempLog.info("Finally deploy the version (for further reactor projects)");
			try {
				callMaven("deploy", tempPomFile.getName(), tempPomFile.getParentFile());
			} catch (MavenCallFailedException e) {
				throw new MojoExecutionException("Failed to deploy "+tempPomFile, e);
			}
		} else {
			tempLog.info("Nothing changed. All dependencies needed.");
		}
	}

	/**
	 * Insert the method description here.
	 * 
	 * @param aDoc
	 * @param aDependency
	 * @param aString
	 * @param aString2
	 * @return
	 * @author dt0b35
	 * @throws MojoExecutionException 
	 * @since 13.11.2012 23:05:35
	 */
	private Document changeScope(Document aDoc, Dependency aDependency, String aFileName, final String aScope)
			throws MojoExecutionException {
		return updateDocument(aDoc, aDependency, aFileName, new NodeModifyAction() {
			@Override
			public void modify(Document anUpdateDoc, Node aFoundDependency) {
				boolean tempScopeUpdated = false;
				NodeList tempChildNodes = aFoundDependency.getChildNodes();
				for (int c = 0; c < tempChildNodes.getLength(); c++) {
					Node tempChild = tempChildNodes.item(c);
					String tempNodeName = tempChild.getNodeName();
					//					tempLog.debug("Child=" + tempNodeName);
					if (tempNodeName.equals("scope")) {
						tempChild.setTextContent("test");
						tempScopeUpdated = true;
					}
				}
				if (!tempScopeUpdated) {
					Element tempScope = anUpdateDoc.createElementNS(anUpdateDoc.getNamespaceURI(), "scope");
					tempScope.setTextContent("test");
					aFoundDependency.appendChild(tempScope);
				}
			}
		});
	}

	/**
	 * Insert the method description here.
	 * 
	 * @param aDoc
	 * @param aDependency
	 * @param aString
	 * @author dt0b35
	 * @return 
	 * @throws MojoExecutionException 
	 * @since 13.11.2012 21:31:27
	 */
	private Document removeDependency(Document aDoc, Dependency aDependency, String aFileName)
			throws MojoExecutionException {
		return updateDocument(aDoc, aDependency, aFileName, new NodeModifyAction() {
			@Override
			public void modify(Document anUpdateDoc, Node aFoundDependency) {
				aFoundDependency.getParentNode().removeChild(aFoundDependency);
			}
		});
	}

	private Document updateDocument(Document aDoc, Dependency aDependency, String aFileName,
			NodeModifyAction aNodeModifyAction) throws MojoExecutionException {
		try {
			TransformerFactory tfactory = TransformerFactory.newInstance();
			Transformer tx = tfactory.newTransformer();
			DOMSource copySource = new DOMSource(aDoc);
			DOMResult copyResult = new DOMResult();
			tx.transform(copySource, copyResult);
			Document copiedDocument = (Document) copyResult.getNode();

			String tempDependencyId = aDependency.getGroupId() + ":" + aDependency.getArtifactId();
			Log tempLog = getLog();
			tempLog.info("Remove " + tempDependencyId);
			tempLog.debug("aDoc.getNamespaceURI()=" + aDoc.getNamespaceURI());
			tempLog.debug("copiedDocument.getNamespaceURI()=" + copiedDocument.getNamespaceURI());
			tempLog.debug("copiedDocument.childNodes.length=" + copiedDocument.getChildNodes().getLength());
			NodeList tempDependencies = copiedDocument.getElementsByTagNameNS(copiedDocument.getNamespaceURI(),
					"dependency");
			tempLog.debug("tempDependenciesNS.length=" + tempDependencies.getLength());
			if (tempDependencies.getLength() == 0) {
				tempDependencies = copiedDocument.getElementsByTagName("dependency");
				tempLog.debug("tempDependencies.length=" + tempDependencies.getLength());
			}
			Node tempFoundDependency = null;
			for (int i = 0; i < tempDependencies.getLength(); i++) {
				Node tempDependency = tempDependencies.item(i);
				NodeList tempChildNodes = tempDependency.getChildNodes();
				String tempGroupId = null;
				String tempArtifactId = null;
				for (int c = 0; c < tempChildNodes.getLength(); c++) {
					Node tempChild = tempChildNodes.item(c);
					String tempNodeName = tempChild.getNodeName();
					//					tempLog.debug("Child=" + tempNodeName);
					if (tempNodeName.equals("groupId")) {
						tempGroupId = tempChild.getTextContent();
					}
					if (tempNodeName.equals("artifactId")) {
						tempArtifactId = tempChild.getTextContent();
					}
				}
				if (aDependency.getGroupId().equals(tempGroupId)) {
					if (aDependency.getArtifactId().equals(tempArtifactId)) {
						tempFoundDependency = tempDependency;
					}
				}
			}
			if (tempFoundDependency == null) {
				tempLog.warn("No Dependency " + tempDependencyId + " in " + copiedDocument + " probably in parent pom");
				return null;
			}
			aNodeModifyAction.modify(copiedDocument, tempFoundDependency);

			// Use a Transformer for output
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer transformer = tFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");

			DOMSource source = new DOMSource(copiedDocument);
			File tempFile = new File(aFileName);
			tempFile.deleteOnExit();
			StreamResult result = new StreamResult(tempFile);
			transformer.transform(source, result);

			if (tempLog.isDebugEnabled()) {
				tempLog.debug("Modified pom:");
				BufferedReader tempReader = new BufferedReader(new FileReader(tempFile));
				while (tempReader.ready()) {
					tempLog.debug(tempReader.readLine());
				}
				tempReader.close();
				tempLog.debug("");
			}
			return copiedDocument;
		} catch (Exception e) {
			throw new MojoExecutionException("XML Failure", e);
		}
	}

	/**
	 * Insert the method description here.
	 * 
	 * @param aGoal
	 * @param aName
	 * @author dt0b35
	 * @param aWorkingDirectory 
	 * @throws MavenCallFailedException 
	 * @since 13.11.2012 21:14:17
	 */
	private void callMaven(String aGoal, String aPomFileName, File aWorkingDirectory) throws MavenCallFailedException {
		Commandline cl = new Commandline("mvn");
		if (getLog().isDebugEnabled()) {
			cl.addArguments(new String[] { "clean", aGoal, "-f", aPomFileName, "-X" });
		} else {
			cl.addArguments(new String[] { "clean", aGoal, "-f", aPomFileName });
		}
		cl.setWorkingDirectory(aWorkingDirectory);
		InputStream input = null;
		StreamConsumer output = new StreamConsumer() {
			@Override
			public void consumeLine(String aLine) {
				getLog().info("[Unused] " + aLine);
			}
		};
		StreamConsumer error = new StreamConsumer() {
			@Override
			public void consumeLine(String aLine) {
				getLog().error("[Unused] " + aLine);
			}
		};
		Log tempLog = getLog();
		tempLog.info("Execute " + cl + " in " + aWorkingDirectory + " ...");
		int tempReturnValue;
		try {
			tempReturnValue = CommandLineUtils.executeCommandLine(cl, input, output, error);
		} catch (CommandLineException e) {
			throw new MavenCallFailedException("CL=" + cl, e);
		}
		tempLog.info("Result=" + tempReturnValue);
		if (tempReturnValue != 0) {
			throw new MavenCallFailedException("Returnvalue = " + tempReturnValue + " cl=" + cl);
		}
	}
}
