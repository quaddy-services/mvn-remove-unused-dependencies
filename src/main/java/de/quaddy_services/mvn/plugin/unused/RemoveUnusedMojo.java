package de.quaddy_services.mvn.plugin.unused;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils.StringStreamConsumer;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.w3c.dom.Document;
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
			callMaven("package", tempPomFile.getName());
		} catch (MavenCallFailedException e) {
			throw new MojoExecutionException("deply goal must work!", e);
		}
		Document tempDoc;
		try {
			DocumentBuilderFactory tempDBF = DocumentBuilderFactory.newInstance();

			DocumentBuilder tempNewDocumentBuilder = tempDBF.newDocumentBuilder();

			tempDoc = tempNewDocumentBuilder.parse(tempPomFile);

		} catch (Exception e) {
			throw new MojoExecutionException("Initialization Error", e);
		}
		List<Dependency> tempDependencies = project.getDependencies();
		for (Dependency tempDependency : tempDependencies) {
			String tempScope = tempDependency.getScope();
			if (tempScope == null || tempScope.equals("compile")) {
				Document tempModifiedDoc = removeDependency(tempDoc, tempDependency, "pom-temp.xml");
			} else if (tempScope.equals("test")) {

			}
		}

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
		try {
			Node originalRoot = aDoc.getDocumentElement();
			DocumentBuilderFactory tempDBF = DocumentBuilderFactory.newInstance();

			DocumentBuilder tempNewDocumentBuilder = tempDBF.newDocumentBuilder();

			Document copiedDocument = tempNewDocumentBuilder.newDocument();
			Node copiedRoot = copiedDocument.importNode(originalRoot, true);
			copiedDocument.appendChild(copiedRoot);

			String tempDependencyId = aDependency.getGroupId() + ":" + aDependency.getArtifactId();
			Log tempLog = getLog();
			tempLog.info("Remove " + tempDependencyId);
			NodeList tempDependencies = copiedDocument.getElementsByTagNameNS(copiedDocument.getNamespaceURI(),
					"dependency");
			Node tempDependencyToBeRemoved = null;
			for (int i = 0; i < tempDependencies.getLength(); i++) {
				Node tempDependency = tempDependencies.item(i);
				NodeList tempChildNodes = tempDependency.getChildNodes();
				String tempGroupId = null;
				String tempArtifactId = null;
				for (int c = 0; c < tempChildNodes.getLength(); c++) {
					Node tempChild = tempChildNodes.item(c);
					String tempNodeName = tempChild.getNodeName();
					if (tempNodeName.equals("groupId")) {
						tempGroupId = tempNodeName;
					}
					if (tempNodeName.equals("artifactId")) {
						tempArtifactId = tempNodeName;
					}
				}
				if (aDependency.getGroupId().equals(tempGroupId)) {
					if (aDependency.getArtifactId().equals(tempArtifactId)) {
						tempDependencyToBeRemoved = tempDependency;
					}
				}
			}
			if (tempDependencyToBeRemoved == null) {
				throw new MojoExecutionException("No Dependency " + tempDependencyId + " in " + copiedDocument);
			}
			tempDependencyToBeRemoved.getParentNode().removeChild(tempDependencyToBeRemoved);

			// Use a Transformer for output
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer transformer = tFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");

			DOMSource source = new DOMSource(copiedDocument);
			File tempFile = new File(aFileName);
			StreamResult result = new StreamResult(tempFile);
			transformer.transform(source, result);

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
	 * @throws MavenCallFailedException 
	 * @since 13.11.2012 21:14:17
	 */
	private void callMaven(String aGoal, String aPomFileName) throws MavenCallFailedException {
		Commandline cl = new Commandline("mvn");
		if (getLog().isDebugEnabled()) {
			cl.addArguments(new String[] { aGoal, "-f", aPomFileName, "-X" });
		} else {
			cl.addArguments(new String[] { aGoal, "-f", aPomFileName });
		}
		InputStream input = null;
		StreamConsumer output = new StreamConsumer() {
			@Override
			public void consumeLine(String aLine) {
				getLog().info("[Unused] "+aLine);
			}
		};
		StreamConsumer error = new StreamConsumer() {
			@Override
			public void consumeLine(String aLine) {
				getLog().error("[Unused] "+aLine);
			}
		};
		Log tempLog = getLog();
		tempLog.info("Execute " + cl + " ...");
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
