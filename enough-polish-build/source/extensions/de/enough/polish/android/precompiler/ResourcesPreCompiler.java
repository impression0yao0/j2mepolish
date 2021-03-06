/*
 * Created on 18-Aug-2005 at 15:58:25.
 * 
 * Copyright (c) 2005 Robert Virkus / Enough Software
 *
 * This file is part of J2ME Polish.
 *
 * J2ME Polish is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * J2ME Polish is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with J2ME Polish; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 * Commercial licenses are also available, please
 * refer to the accompanying LICENSE.txt or visit
 * http://www.j2mepolish.org for details.
 */
package de.enough.polish.android.precompiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import de.enough.polish.Attribute;
import de.enough.polish.Device;
import de.enough.polish.Environment;
import de.enough.polish.ant.Jad;
import de.enough.polish.ant.android.ArgumentHelper;
import de.enough.polish.ant.build.Midlet;
import de.enough.polish.descriptor.DescriptorCreator;
import de.enough.polish.manifest.ManifestCreator;
import de.enough.polish.precompile.PreCompiler;
import de.enough.polish.propertyfunctions.VersionFunction;
import de.enough.polish.util.FileUtil;
import de.enough.polish.util.ProcessUtil;

/**
 * <p>Creates the R.java and Manifest.java</p>
 *
 * <p>Copyright Enough Software 2008 - 2011</p>
 * @author Andre Schmidt, j2mepolish@enough.de
 */
public class ResourcesPreCompiler extends PreCompiler {
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see de.enough.polish.precompile.PreCompiler#preCompile(java.io.File,
	 * de.enough.polish.Device)
	 */
	public void preCompile(File classesDir, Device device)
			throws BuildException 
	{
		Environment env = device.getEnvironment();

		System.out.println("aapt: Copying resources to " + ArgumentHelper.getRaw(env) + "...");		
		try {
			// generate jadprops.txt:
			storeJadProperties( new File( ArgumentHelper.getAssets(env)), env);
			copyResources(device, env);
		} catch (IOException e) {
			e.printStackTrace();
			throw new BuildException("Unable to copy resources: " + e);
		}

		String aapt = ArgumentHelper.aapt(env);
		if (aapt != null) {
			ArrayList<String> arguments = getDefaultArguments(aapt,env);
			try {
				
				System.out.println("aapt: Generating R.java / AndroidManifest.xml from the resources...");
				
				int result = ProcessUtil.exec( arguments, "aapt: ", true, null, null);
				if (result != 0) {
					System.out.println("aapt arguments were:");
					System.out.println(ProcessUtil.toString(arguments));
					throw new BuildException("Unable to execute aapt - got result: " + result);
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("aapt arguments were:");
				System.out.println(ProcessUtil.toString(arguments));
				throw new BuildException("Unable to execute aapt - got exception: " + e);
			}
		}
		String manifestPath = ArgumentHelper.getActivity(env) + "/AndroidManifest.xml";
		SAXBuilder builder = new SAXBuilder();
		Document document;
		File manifestFile = new File(manifestPath);
		try {
			document = builder.build(manifestFile);
			//System.out.println("Got MANIFEST " + print(document));
		} catch (JDOMException e) {
			throw new BuildException("Could not parse file '"+manifestPath+"': " + e, e);
		} catch (IOException e) {
			throw new BuildException("Could not read file '"+manifestPath+"': " + e, e);
		}
		Element rootElement = document.getRootElement();
		Namespace namespace = rootElement.getNamespace("android");

		Element permissionElement;
		String permissionsString = env.getVariable("polish.build.android.permissions");
		if(permissionsString == null){
			// Allow application without any permissions.
			permissionsString = "";
//			throw new BuildException("The variable 'polish.build.android.permissions' must be defined. Normally it is defined in the platforms.xml file for the Android platforms.");
		}
		
		String[] permissions = permissionsString.split(",");
		
		for (int i = 0; i < permissions.length; i++) {
			permissionElement = new Element("uses-permission");
			String permission = permissions[i];
			permissionElement.setAttribute("name","android.permission."+permission, namespace);
			rootElement.addContent(permissionElement);
		}
		
		Element usesSdkElement = new Element("uses-sdk");
		String minSdkVersion = env.getVariable("android.minSdkVersion");
		if(minSdkVersion == null || minSdkVersion.length() == 0) {
			minSdkVersion = "3";
		}
		usesSdkElement.setAttribute("minSdkVersion", minSdkVersion, namespace);
		
		String targetSdkVersion = env.getVariable("android.targetSdkVersion");
		if(targetSdkVersion != null && targetSdkVersion.length() > 0) {
			usesSdkElement.setAttribute("targetSdkVersion", targetSdkVersion, namespace);
		}
		
		String maxSdkVersion = env.getVariable("android.maxSdkVersion");
		if(maxSdkVersion != null && maxSdkVersion.length() > 0) {
			usesSdkElement.setAttribute("maxSdkVersion", maxSdkVersion, namespace);
		}
		
		rootElement.addContent(usesSdkElement);
		
		String version = env.getVariable("MIDlet-Version");
		if(version == null || version.length() == 0) {
			version = "1";
		}
		String versionCode = env.getVariable("android.versionCode");
		if (versionCode == null || versionCode.length() == 0) {
			int versionCodeNumber = computeVersionCode(version);
			versionCode = String.valueOf(versionCodeNumber);
		}
		rootElement.setAttribute("versionCode", versionCode, namespace);
		
		String versionName = env.getVariable("android.versionName");
		if(versionName == null || versionName.length() == 0) {
			versionName = version;
		}
		rootElement.setAttribute("versionName", versionName, namespace);
		
		Midlet midlet = env.getBuildSetting().getMidlets(env)[0];
		String midletName = midlet.name;

		Element applicationElement = rootElement.getChild("application");
		applicationElement.setAttribute("label",midletName, namespace);
		applicationElement.setAttribute("debuggable","true", namespace);
		
		Element activityElement = applicationElement.getChild("activity");
		activityElement.setAttribute("configChanges","mcc|mnc|locale|touchscreen|keyboard|keyboardHidden|navigation|orientation|fontScale", namespace);
		activityElement.setAttribute("label", midletName, namespace);
		String screenOrientation = env.getVariable("android.screenOrientation");
		if (screenOrientation != null) {
			activityElement.setAttribute("screenOrientation", screenOrientation, namespace);
		}
		
		String trapHomeButtonFlag = env.getVariable("android.traphomebutton");
		if("true".equals(trapHomeButtonFlag)) {
			Element intentFilterElement = activityElement.getChild("intent-filter");
			Element intentCategoryElement;
			intentCategoryElement = new Element("category");
			intentCategoryElement.setAttribute("name", "android.intent.category.HOME", namespace);
			intentFilterElement.addContent(intentCategoryElement);
			intentCategoryElement = new Element("category");
			intentCategoryElement.setAttribute("name", "android.intent.category.DEFAULT", namespace);
			intentFilterElement.addContent(intentCategoryElement);
		}
		
		String iconUrl = env.getVariable("MIDlet-Icon");
		if (iconUrl != null && iconUrl.length() > 0) {
			String cleanedIconUrl = cleanResourceName(iconUrl);
			try {
				FileUtil.copy( new File(device.getResourceDir(), iconUrl.substring(1)), new File( ArgumentHelper.getRaw(env) + cleanedIconUrl));
				applicationElement.setAttribute("icon","@raw"+cleanedIconUrl, namespace);
				activityElement.setAttribute("icon","@raw"+cleanedIconUrl, namespace);
			} catch (Exception e) {
				System.err.println("Unable to copy MIDlet-Icon \"" + iconUrl + "\": " + e);
				e.printStackTrace();
			}
		} else {
			System.err.println("Warning: No icon was defined in this build. You will not be able to deploy this application in the Android Market. Please define an icon with the attribte \"icon\" in the <info> tag of the build.xml file.");
		}
		// add PuhsRegistry receiver to <application>:
		//	    <receiver android:enabled="true"
		//	              android:exported="false"
		//	              android:label="PushRegistry"
		//	              android:name="de.enough.polish.io.PushRegistry"
		//	              android:process=":remote">
		//	    </receiver>
		Element receiverElement = new Element("receiver");
		receiverElement.setAttribute("enabled", "true", namespace);
		receiverElement.setAttribute("exported", "false", namespace);
		receiverElement.setAttribute("label", "PushRegistry", namespace);
		receiverElement.setAttribute("name", "de.enough.polish.android.io.PushRegistry", namespace);
		receiverElement.setAttribute("process", ":remote", namespace);
		applicationElement.addContent(receiverElement);

		// add Service for MIDlet.resumeRequest(): 
		//		<service
		//			android:name="de.enough.polish.android.midlet.LaunchService"
		//			android:label="LaunchService"
		//			>
		//		</service>
		Element serviceElement = new Element("service");
		serviceElement.setAttribute("name", "de.enough.polish.android.midlet.LaunchService", namespace);
		serviceElement.setAttribute("label", "LaunchService", namespace);
		applicationElement.addContent(serviceElement);

		
		// add default supports-screens element:
		if (!env.hasSymbol("android.skipSupportsScreens")) {
			Element supportsScreensElement = new Element("supports-screens");
			supportsScreensElement.setAttribute("anyDensity", "true", namespace);
			supportsScreensElement.setAttribute("resizeable", "true", namespace);
			supportsScreensElement.setAttribute("smallScreens", "true", namespace);
			supportsScreensElement.setAttribute("normalScreens", "true", namespace);
			supportsScreensElement.setAttribute("largeScreens", "true", namespace);
			if (env.isConditionFulfilled("polish.JavaPackage >= Android/2.3")) {
				supportsScreensElement.setAttribute("xlargeScreens", "true", namespace);
			}
			rootElement.addContent(supportsScreensElement);
		}
		
		// check if further elements should be added:
		String furtherManifestPath = env.getVariable("android.manifest");
		if (furtherManifestPath != null) {
			File furtherManifestFile = env.resolveFile(furtherManifestPath );
			Document furtherManifestDocument = null;
			try {
				builder = new SAXBuilder();
				furtherManifestDocument = builder.build(furtherManifestFile);
				//System.out.println("Got MANIFEST " + print(document));
			} catch (Exception e) {
				e.printStackTrace();
				throw new BuildException("Could not read or parse file '"+furtherManifestPath+"': please check your \"android.manifest\" variable in your build.xml script: " + e, e);
			}
			Element furtherManifestRootElement = furtherManifestDocument.getRootElement();
			//Namespace namespace = rootElement.getNamespace("android");
			List children = furtherManifestRootElement.getChildren();
			for (int childIndex=0; childIndex < children.size(); childIndex++ ) {
				Element childElement = (Element) children.get(childIndex);
				childElement.detach();
				Element existingElement = rootElement.getChild( childElement.getName() );
				if (existingElement == null) {					
					rootElement.addContent(childElement);
				} else {
					List attributes = childElement.getAttributes();
					for (int attributesIndex=0; attributesIndex < attributes.size(); attributesIndex++) {
						org.jdom.Attribute attribute = (org.jdom.Attribute) attributes.get(attributesIndex);
						existingElement.setAttribute( attribute.getName(), attribute.getValue(), namespace);
					}
					List subchildren = childElement.getChildren();
					while (subchildren.size() > 0 ) {
						Element subchildElement = (Element) subchildren.get(0);
						subchildElement.detach();
						existingElement.addContent(subchildElement);
					}
				}
			}
		}
		
		
		
		// TODO: This does not work. Instead we need to alter the file res/values/string.xml, add the description as a string resource
		// and reference this resource as value '@string/mystring' to this property.
//		String description = env.getVariable("MIDlet-Description");
//		if(description == null || description.length() == 0) {
//			description = "";
//		}
//		applicationElement.setAttribute("description",description, namespace);
		
		XMLOutputter xmlOutputter = new XMLOutputter();
		FileWriter fileWriter;
		try {
			fileWriter = new FileWriter(manifestFile);
		} catch (IOException e) {
			throw new BuildException("Could not create FileWriter for file '"+manifestPath+"'",e);
		}
		try {
			xmlOutputter.output(document,fileWriter);
		} catch (IOException e) {
			throw new BuildException("Could not write the android manifest to file  '"+manifestPath+"'",e);
		}
		
	}
	
//	private String print(Document document) {
//		StringBuffer buffer = new StringBuffer();
//		Element e = document.getRootElement();
//		add( e, "", buffer );
//		return buffer.toString();
//	}
//
//	private void add(Element e, String start, StringBuffer buffer) {
//		buffer.append(start).append('<').append( e.getName() );
//		List l = e.getAttributes();
//		for (Iterator iterator = l.iterator(); iterator.hasNext();) {
//			org.jdom.Attribute attr = (org.jdom.Attribute) iterator.next();
//			buffer.append(' ').append(attr.getName()).append("=\"").append( attr.getValue() + "\"");
//		}
//		buffer.append(">\n");
//		if (e.getTextTrim() != null) {
//			buffer.append( start ).append( e.getTextTrim() );
//		}
//		String st = start + "  ";
//		l = e.getChildren();
//		for (Iterator iterator = l.iterator(); iterator.hasNext();) {
//			Element child = (Element)iterator.next();
//			add( child, st, buffer );
//		}
//		buffer.append(start).append("</").append( e.getName() ).append(">\n");
//		
//	}

	/**
	 * Writes the JAD properties in a way so that the MIDlet can load them.
	 * 
	 * @param jadFile the JAD file
	 * @param classesDir the classes directory to which the JAD properties should be saved
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws UnsupportedEncodingException
	 */
	private void storeJadProperties(File targetDir, Environment env) 
	throws FileNotFoundException, IOException, UnsupportedEncodingException
	{
		File txtJadFile = new File( targetDir, "jadprops.txt");
		if (!targetDir.exists()) {
			targetDir.mkdirs();
		}
		Attribute[] descriptorAttributes = (Attribute[]) env.get(ManifestCreator.MANIFEST_ATTRIBUTES_KEY);
		Jad jad = new Jad( env );
		jad.setAttributes( descriptorAttributes );
		descriptorAttributes = (Attribute[]) env.get(DescriptorCreator.DESCRIPTOR_ATTRIBUTES_KEY);
		jad.addAttributes( descriptorAttributes );
		String[] jadPropertiesLines = jad.getContent();
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < jadPropertiesLines.length; i++)
		{
			String line = jadPropertiesLines[i];
			buffer.append(line).append('\n');
		}
		FileOutputStream fileOut = new FileOutputStream(  txtJadFile );
		fileOut.write( buffer.toString().getBytes("UTF-8") );
		fileOut.flush();
		fileOut.close();
	}

	/**
	 * This method sums all numerical version components in the version string. It is helpful to compare version strings with a
	 * simple ordering. It will break with versions like 1.0.1-preview1 and 1.0.1 as the former has a higher numerical score but
	 * is 'lower' as a version.
	 * @param version
	 * @return
	 */
	private int computeVersionCode(String version) {
		if(version == null || version.length() == 0) {
			version = "1";
		}
		String versionCodeString = VersionFunction.process(version);
		int versionCode = Integer.parseInt(versionCodeString);
		return versionCode;
	}
	
	/**
	 * Returns the default arguments for executable
	 * @param executable the executable
	 * @param env the environment
	 * @return the ArrayList
	 */
	static ArrayList<String> getDefaultArguments(String executable, Environment env)
	{
		String androidJar = ArgumentHelper.getAndroidJar(env); 
		
		ArrayList<String> arguments = new ArrayList<String>();
		arguments.add(executable);
		arguments.add("package");
		arguments.add("-m");
		arguments.add("-J");
		arguments.add(ArgumentHelper.getSrc(env));
		arguments.add("-M");
		arguments.add(ArgumentHelper.getActivity(env) + "/AndroidManifest.xml");
		arguments.add("-S");
		arguments.add(ArgumentHelper.getRes(env));
		arguments.add("-I");
		arguments.add(androidJar);
		return arguments;
	}
	
	/**
	 * Copies the resources in lower case
	 * @param device the device
	 * @param env the environment
	 * @throws IOException if an error occurs during the copying
	 */
	void copyResources(Device device, Environment env) throws IOException
	{
		AtTargetFilenameFilter filter = null;
		if (!env.hasSymbol("polish.android.DoNotFilterFiles")) {
			filter = new AtTargetFilenameFilter();
		}
		String assetsPath = ArgumentHelper.getAssets(env);
		FileUtil.copyDirectoryContents( device.getResourceDir(), new File( assetsPath ), true, filter);
		if (filter != null) {
			File resDir = new File( ArgumentHelper.getRes(env) );
			filter.copyToTargetDir( resDir );
		}
//		
//		String rawPath = ArgumentHelper.getRaw(env);
//
//		FilenameFilter filenameFilter = new FilenameFilter() {
//			public boolean accept(File dir, String name) {
//				if(".svn".equals(name)) {
//					return false;
//				}
//				if("CVS".equals(name)) {
//					return false;
//				}
//				return true;
//			}
//			
//		};
//		String[] fileNames = FileUtil.filterDirectory(device.getResourceDir(), null, filenameFilter, true);
//		String resourceDir = device.getResourceDir().getAbsolutePath();		
//		String rawDirectory = ArgumentHelper.getRaw(env);
//		
//		for (int i = 0; i < fileNames.length; i++) {
//			String fileName = fileNames[i];
//			File srcFile = new File(resourceDir + File.separator + fileName);
//			String targetFileName = fileName.toLowerCase();
//			int pathSeparatorIndex = targetFileName.lastIndexOf(File.separatorChar );
//			if (pathSeparatorIndex != -1) {
//				targetFileName = targetFileName.substring( pathSeparatorIndex + 1 );
//			}
//			String cleanedTargetFileName = cleanResourceName(targetFileName);
//			File destFile = new File(rawDirectory + File.separator + cleanedTargetFileName);
//			
//			FileUtil.copy(srcFile, destFile);
//		}
	}
	
	/**
	 * Cleans the filename. Android does not allow the minus sign in the name of a resource file.
	 * The path is also flattened as android does not allow resources in subfolders.
	 * TODO: This method must be equal to the one in de.enough.polish.android.helper.ResourceHelper. Its hard to take the same class as
	 * the classpath is different for building and at runtime.
	 * @param resourceName Must not be null
	 * @return the resource name that only contains valid Android resource characters
	 */
	private String cleanResourceName(String resourceName) {
		String cleanedName = resourceName.replace('-', '_');
		cleanedName = cleanedName.replace(' ', '_');
		cleanedName = cleanedName.replace(".", "_dot_");
		int lastIndexOfSlash = cleanedName.lastIndexOf(File.separatorChar);
		if (lastIndexOfSlash > 0) {
			cleanedName = cleanedName.substring(lastIndexOfSlash+1);
		}
		return cleanedName;
	}
	
	private static class AtTargetFilenameFilter implements FilenameFilter {
		
		private ArrayList<AtFile> files = new ArrayList<AtFile>();

		public boolean accept(File dir, String name) {
			int atIndex = name.indexOf('@');
			if (atIndex != -1) {
				String targetName = name.substring(0, atIndex);
				int dotIndex = name.lastIndexOf('.');
				if (dotIndex > atIndex) {
					targetName += name.substring(dotIndex);
					targetName = name.substring(atIndex+1, dotIndex) + File.separatorChar + targetName;
				} else {
					targetName = name.substring(atIndex+1) + File.separatorChar + targetName;
				}
				AtFile file = new AtFile( new File(dir, name), targetName);
				this.files.add(file);
				return false;
			}
			return true;
		}
		
		public void copyToTargetDir( File resDir ) throws FileNotFoundException, IOException {
			for (int fileIndex=0; fileIndex < this.files.size(); fileIndex++) {
				AtFile file = this.files.get(fileIndex);
				file.copyToTargetDir(resDir);
			}
		}
		
		private static class AtFile {
			private File file;
			private String targetFile;
			
			public AtFile(File file, String targetFile) {
				this.file = file;
				this.targetFile = targetFile;
			}
			
			public void copyToTargetDir( File resDir ) throws FileNotFoundException, IOException {
				FileUtil.copy(this.file, new File(resDir, this.targetFile));
			}
		}
		
		
	}
}
