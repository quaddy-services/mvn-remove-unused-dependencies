package de.quaddyservices.mvn.plugin.unused;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
 * <br/>
 * Remove unused maven dependencies out of your pom.xml.<br/>
 * Goal will remove one by one and try <br/>
 * to call mvn package without that dependency for all dependencies.
 * 
 * @goal remove
 * @requiresProject true
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

	/**
	 * Set IncludeParent to true the check dependencies in parents poms or to
	 * check in stand alone projects.
	 * 
	 * @parameter default-value="false" expression="${remove.includeParent}"
	 */
	private boolean includeParent;

	/**
	 * Set debug2ndMaven to true to see the output of called maven goals as
	 * debug output, too.
	 * 
	 * Specify just "-X" will not show all output of delegated maven.
	 * 
	 * @parameter default-value="false" expression="${remove.debug2ndMaven}"
	 */
	private boolean debug2ndMaven;

	@Override
	public void execute() throws MojoExecutionException {
		Log tempLog = getLog();
		tempLog.debug("Hello, world.");
		File tempCurrentDir = new File(".");
		tempLog.debug("Current Dir = " + tempCurrentDir.getAbsolutePath());
		File tempPomFile = project.getFile();
		tempLog.debug("Project.file = " + tempPomFile);
		boolean tempIsParent = new File(tempPomFile.getName()).getAbsolutePath().equals(tempPomFile.getAbsolutePath());
		if (!includeParent) {
			if (tempIsParent) {
				tempLog.warn("Ignore parent set -Dremove.includeParent=true to check the parent/stand-alone.");
				return;
			}
		}
		String tempPackaging = project.getPackaging();
		Boolean tempJavaProject = null;
		if (project.getArtifactId().endsWith("-runtime") && (tempPackaging == null || tempPackaging.equals("jar"))) {
			tempLog.debug("ok:ArtifactId=*-runtime:" + tempPackaging);
			tempJavaProject = false;
		}
		if (tempJavaProject == null) {
			if (tempPackaging == null || tempPackaging.equals("jar") || tempPackaging.equals("ejb")) {
				tempLog.debug("ok:Java=" + tempPackaging);
				tempJavaProject = true;
			}
		}
		if (tempJavaProject == null) {
			if (tempPackaging != null) {
				if (tempPackaging.equals("ear") || tempPackaging.equals("war") || tempPackaging.equals("zip")) {
					tempJavaProject = false;
					tempLog.debug("ok:Resource=" + tempPackaging);
				}
			}
		}
		if (tempJavaProject == null) {
			tempLog.debug("Packaging not supported: " + tempPackaging);
			return;
		}
		File tempDepencyResolveFile = new File(tempPomFile.getParentFile() + "/target/dependencies.txt");
		String tempDepencyResolveContent = "";
		if (tempJavaProject) {
			try {
				callMaven("package", tempPomFile.getParentFile(), false);
			} catch (MavenCallFailedException e) {
				throw new MojoExecutionException("package goal must work!", e);
			}
		} else {
			// Not Java but Resource Project
			try {
				callMaven("package", tempPomFile.getParentFile(), false, "dependency:resolve",
						"-DincludeScope=compile", "-DexcludeScope=test", "-DoutputFile=" + tempDepencyResolveFile,
						"-Dsort=true");
			} catch (MavenCallFailedException e) {
				throw new MojoExecutionException("dependency:resolve goal must work!", e);
			}
			if (!tempDepencyResolveFile.exists()) {
				throw new MojoExecutionException("Could not dependency:resolve to "
						+ tempDepencyResolveFile.getAbsolutePath());
			}
			try {
				tempDepencyResolveContent = readContent(tempDepencyResolveFile);
			} catch (IOException e) {
				throw new MojoExecutionException("Cannot read " + tempDepencyResolveFile.getAbsolutePath(), e);
			}
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
		List<Dependency> tempDependencies = getDependencies();
		boolean tempModified = false;
		File tempPomBackupFile = new File(tempPomFile.getParent() + "/pom-backup-remove-unused-mojo.xml");
		if (tempPomBackupFile.exists()) {
			throw new MojoExecutionException("Backup File exists. Probably aborted? Rename "
					+ tempPomBackupFile.getAbsolutePath() + " back to " + tempPomFile.getAbsolutePath()
					+ " before running!");
		}
		tempLog.debug("Rename " + tempPomFile.getAbsolutePath() + " to " + tempPomBackupFile.getAbsolutePath());
		if (!tempPomFile.renameTo(tempPomBackupFile)) {
			throw new MojoExecutionException("Could not rename " + tempPomFile.getAbsolutePath() + " to "
					+ tempPomBackupFile.getAbsolutePath());
		}
		if (tempJavaProject) {
			for (Dependency tempDependency : tempDependencies) {
				String tempScope = tempDependency.getScope();
				if (tempScope == null || tempScope.equals("compile") || tempScope.equals("provided")) {
					tempLog.debug("Check compile dependency " + tempDependency.getArtifactId());
					Document tempModifiedDoc = removeDependency(tempDoc, tempDependency, tempPomFile);
					if (tempModifiedDoc != null) {
						try {
							callMaven("package", tempPomFile.getParentFile(), true);
							tempLog.info("-------------------------------------------------------------------------");
							tempLog.info("Dependency " + tempDependency.getArtifactId() + " is not needed");
							tempLog.info("-------------------------------------------------------------------------");
							tempDoc = tempModifiedDoc;
							tempModified = true;
						} catch (MavenCallFailedException e) {
							tempLog.debug("Dependency " + tempDependency.getArtifactId() + " is needed for compile");
							tempModifiedDoc = changeScope(tempDoc, tempDependency, tempPomFile, "test");
							try {
								callMaven("package", tempPomFile.getParentFile(), true);
								tempLog.info("-------------------------------------------------------------------------");
								tempLog.info("Dependency " + tempDependency.getArtifactId() + " is for test only");
								tempLog.info("-------------------------------------------------------------------------");
								tempDoc = tempModifiedDoc;
								tempModified = true;
							} catch (MavenCallFailedException e2) {
								tempLog.debug("Dependency " + tempDependency.getArtifactId() + " is needed for test");
							}
						}
					}
				} else if (tempScope.equals("test")) {
					tempLog.debug("Remove test dependency " + tempDependency.getArtifactId());
					Document tempModifiedDoc = removeDependency(tempDoc, tempDependency, tempPomFile);
					if (tempModifiedDoc != null) {
						try {
							callMaven("package", tempPomFile.getParentFile(), true);
							tempLog.info("-------------------------------------------------------------------------");
							tempLog.info("Dependency " + tempDependency.getArtifactId() + " is not needed");
							tempLog.info("-------------------------------------------------------------------------");
							tempDoc = tempModifiedDoc;
							tempModified = true;
						} catch (MavenCallFailedException e) {
							tempLog.debug("Dependency " + tempDependency.getArtifactId() + " is needed for test");
						}
					}
				} else {
					tempLog.info("Skip dependency " + tempDependency.getArtifactId() + " Scope=" + tempScope);
				}
			}
		} else {
			// Not Java but Resource Project
			for (Dependency tempDependency : tempDependencies) {
				String tempScope = tempDependency.getScope();
				if (tempScope == null || tempScope.equals("compile")) {
					tempLog.debug("Check resource dependency " + tempDependency.getArtifactId());
					Document tempModifiedDoc = removeDependency(tempDoc, tempDependency, tempPomFile);
					if (tempModifiedDoc != null) {
						try {
							callMaven("package", tempPomFile.getParentFile(), true, "dependency:resolve",
									"-DincludeScope=compile", "-DexcludeScope=test", "-DoutputFile="
											+ tempDepencyResolveFile, "-Dsort=true");
							String tempNewDependencies = readContent(tempDepencyResolveFile);
							if (tempNewDependencies.equals(tempDepencyResolveContent)) {
								tempLog.info("-------------------------------------------------------------------------");
								tempLog.info("Dependency " + tempDependency.getArtifactId() + " is not needed");
								tempLog.info("-------------------------------------------------------------------------");
								tempDoc = tempModifiedDoc;
								tempModified = true;
							} else {
								tempLog.info("Dependeny is needed because other resolved info: "
										+ tempDependency.getArtifactId());
							}
						} catch (IOException e) {
							tempLog.info("Dependeny is needed: " + tempDependency.getArtifactId() + " Scope="
									+ tempScope + " " + e);
							tempLog.debug("Stacktrace", e);
						} catch (MavenCallFailedException e) {
							tempLog.debug("Dependency " + tempDependency.getArtifactId() + " is needed for test");
						}
					}
				} else {
					tempLog.info("Skip dependency " + tempDependency.getArtifactId() + " Scope=" + tempScope);
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
				callMaven("deploy", tempPomFile.getParentFile(), false);
			} catch (MavenCallFailedException e) {
				throw new MojoExecutionException("Failed to deploy " + tempPomFile, e);
			}
			if (!tempPomBackupFile.delete()) {
				throw new MojoExecutionException("Could not delete backup " + tempPomBackupFile.getAbsolutePath());
			}
			// if (!tempIsParent) {
			// tempLog.info("And checkin the result");
			// try {
			// callMaven("scm:checkin", tempPomFile.getName(),
			// tempCurrentDir.getAbsoluteFile(), "-Dincludes="
			// + tempPomFile.getParentFile().getName() + "/pom.xml",
			// "-Dmessage=" + getClass().getName(),
			// "-N");
			// } catch (MavenCallFailedException e) {
			// tempLog.warn("Could not checkin: " + e.getMessage());
			// tempLog.warn("You need to checkin manually");
			// tempLog.debug("Details:", e);
			// }
			// }
		} else {
			tempLog.info("Nothing changed. All dependencies needed.");
			tempLog.debug("Rename back " + tempPomBackupFile.getAbsolutePath() + " to " + tempPomFile.getAbsolutePath());
			tempPomFile.delete();
			if (!tempPomBackupFile.renameTo(tempPomFile)) {
				throw new MojoExecutionException("Could not rename back " + tempPomBackupFile.getAbsolutePath()
						+ " to " + tempPomFile.getAbsolutePath());
			}
		}
		// Clean up disk space.
		tempLog.info("Clean up the target folder.");
		try {
			// "clean" is added in callMaven
			callMaven((String) null, tempPomFile.getParentFile(), false);
		} catch (MavenCallFailedException e) {
			throw new MojoExecutionException("Failed to deploy " + tempPomFile, e);
		}
	}

	/**
	 * readContent
	 * 
	 * @param aDepencyResolveFile
	 * @return
	 * @throws MojoExecutionException 
	 * @since 05.06.2013 22:21:30
	 */
	private String readContent(File aDepencyResolveFile) throws IOException {
		int tempSize = (int) aDepencyResolveFile.length();
		byte[] tempBuff = new byte[tempSize];
		FileInputStream tempIn = new FileInputStream(aDepencyResolveFile);
		tempIn.read(tempBuff);
		tempIn.close();
		return new String(tempBuff);
	}

	/**
	 * getDependencies
	 * 
	 * @return List
	 */
	@SuppressWarnings("unchecked")
	private List<Dependency> getDependencies() {
		return project.getDependencies();
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
	private Document changeScope(Document aDoc, Dependency aDependency, File aFile, final String aScope)
			throws MojoExecutionException {
		return updateDocument(aDoc, aDependency, aFile, new NodeModifyAction() {
			@Override
			public void modify(Document anUpdateDoc, Node aFoundDependency, String aDependencyId) {
				Log tempLog = getLog();
				tempLog.info("Try to set to scope " + aScope + " for " + aDependencyId);
				boolean tempScopeUpdated = false;
				NodeList tempChildNodes = aFoundDependency.getChildNodes();
				for (int c = 0; c < tempChildNodes.getLength(); c++) {
					Node tempChild = tempChildNodes.item(c);
					String tempNodeName = tempChild.getNodeName();
					// tempLog.debug("Child=" + tempNodeName);
					if (tempNodeName.equals("scope")) {
						tempChild.setTextContent(aScope);
						tempScopeUpdated = true;
					}
				}
				if (!tempScopeUpdated) {
					Element tempScope = anUpdateDoc.createElementNS(anUpdateDoc.getNamespaceURI(), "scope");
					tempScope.setTextContent(aScope);
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
	private Document removeDependency(Document aDoc, Dependency aDependency, File aFile) throws MojoExecutionException {
		return updateDocument(aDoc, aDependency, aFile, new NodeModifyAction() {
			@Override
			public void modify(@SuppressWarnings("unused") Document anUpdateDoc, Node aFoundDependency,
					String aDependencyId) {
				Log tempLog = getLog();
				tempLog.info("Try to remove " + aDependencyId);
				aFoundDependency.getParentNode().removeChild(aFoundDependency);
			}
		});
	}

	private Document updateDocument(Document aDoc, Dependency aDependency, File aFile,
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
					// tempLog.debug("Child=" + tempNodeName);
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
			aNodeModifyAction.modify(copiedDocument, tempFoundDependency, tempDependencyId);

			// Use a Transformer for output
			TransformerFactory tFactory = TransformerFactory.newInstance();
			Transformer transformer = tFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");

			DOMSource source = new DOMSource(copiedDocument);
			tempLog.debug("Write " + aFile.getAbsolutePath());
			StreamResult result = new StreamResult(aFile);
			transformer.transform(source, result);

			if (tempLog.isDebugEnabled()) {
				tempLog.debug("Modified pom:");
				BufferedReader tempReader = new BufferedReader(new FileReader(aFile));
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

	private static final String CRLF = System.getProperty("line.separator", "\r\n");

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
	private void callMaven(String aGoal, File aWorkingDirectory, boolean aSilentExceptionFlag,
			String... anAdditionalParameters) throws MavenCallFailedException {
		Commandline cl = new Commandline("mvn");
		final Log tempLog = getLog();
		List<String> tempArgs = new ArrayList<String>();
		tempArgs.add("clean");
		if (aGoal != null) {
			tempArgs.add(aGoal);
		}

		if (debug2ndMaven) {
			if (tempLog.isDebugEnabled()) {
				tempArgs.add("-X");
			}
		}

		for (String tempAddArg : anAdditionalParameters) {
			tempArgs.add(tempAddArg);
		}

		final StringBuilder tempLines = new StringBuilder();
		cl.addArguments(tempArgs.toArray(new String[tempArgs.size()]));
		cl.setWorkingDirectory(aWorkingDirectory);
		InputStream input = null;
		StreamConsumer output = new StreamConsumer() {
			@Override
			public void consumeLine(String aLine) {
				if (debug2ndMaven) {
					tempLog.info("[Unused] " + aLine);
				} else {
					if (tempLog.isDebugEnabled()) {
						tempLog.debug("[Unused] " + aLine);
					}
				}
				tempLines.append(aLine);
				tempLines.append(CRLF);
			}
		};
		StreamConsumer error = new StreamConsumer() {
			@Override
			public void consumeLine(String aLine) {
				if (debug2ndMaven) {
					tempLog.info("[Unused] " + aLine);
				} else {
					if (tempLog.isDebugEnabled()) {
						tempLog.debug("[Unused] " + aLine);
					}
				}
				tempLines.append(aLine);
				tempLines.append(CRLF);
			}
		};
		tempLog.info("Execute " + cl + " in " + aWorkingDirectory + " ...");
		int tempReturnValue;
		try {
			tempReturnValue = CommandLineUtils.executeCommandLine(cl, input, output, error);
		} catch (CommandLineException e) {
			throw new MavenCallFailedException("CL=" + cl, e);
		}
		tempLog.info("Result=" + tempReturnValue);
		if (tempReturnValue != 0) {
			if (aSilentExceptionFlag) {
				throw new MavenCallFailedException("Returnvalue = " + tempReturnValue + " cl=" + cl);
			}
			throw new MavenCallFailedException("Returnvalue = " + tempReturnValue + " cl=" + cl + CRLF + tempLines);
		}
	}
}
