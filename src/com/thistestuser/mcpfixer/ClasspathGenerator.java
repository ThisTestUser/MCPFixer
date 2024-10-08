package com.thistestuser.mcpfixer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

public class ClasspathGenerator
{
	private final File mcpFolder;
	private String javaVersion;
	
	public ClasspathGenerator(File mcpFolder, String javaVersion)
	{
		this.mcpFolder = mcpFolder;
		this.javaVersion = javaVersion;
	}
	
	public int run()
	{
		System.out.println("Running classpath generator");
		File versions = new File(mcpFolder, "jars/versions");
		if(!versions.exists() || versions.listFiles().length != 1)
		{
			System.out.println("Need jars/versions folder inside MCP workspace with version name");
			return 4;
		}
		File libraries = new File(mcpFolder, "jars/libraries");
		if(!libraries.exists())
		{
			System.out.println("libraries folder is missing (did you run decompile?)");
			return 4;
		}
		String version = versions.toURI().relativize(versions.listFiles()[0].toURI()).getPath();
		//Remove slash
		version = version.substring(0, version.length() - 1);
		if(javaVersion == null)
		{
			if(!version.matches("^\\d+(\\.\\d+){1,2}$"))
			{
				System.out.println("Snapshot version detected. You must specify a Java version with -j");
				return 4;
			}
			javaVersion = getJavaVersion(version.split("\\."));
		}
		System.out.println("Using Java version " + javaVersion);
		int result = writeClientClasspath(libraries, version);
		if(result != 0)
			return result;
		result = writeServerClasspath(version);
		if(result != 0)
			return result;
		
		// Fix Eclipse configuration files
		System.out.println("Fixing Eclipse configuration files");
		editConfigFile("eclipse/.metadata/.plugins/org.eclipse.core.runtime/.settings/org.eclipse.jdt.core.prefs", "=1.6", "=" + javaVersion);
		if(!javaVersion.equals("1.8"))
		{
			editConfigFile("eclipse/Client/.settings/org.eclipse.jdt.core.prefs", "=1.8", "=" + javaVersion);
			editConfigFile("eclipse/Server/.settings/org.eclipse.jdt.core.prefs", "=1.8", "=" + javaVersion);
		}
		editConfigFile("eclipse/.metadata/.plugins/org.eclipse.debug.core/.launches/Client.launch", "-Xincgc ", "");
		editConfigFile("eclipse/.metadata/.plugins/org.eclipse.debug.core/.launches/Server.launch", "-Xincgc ", "");
		
		if(javaVersion.equals("17") || javaVersion.equals("21")
			|| new File(mcpFolder, "src/minecraft_server/net/minecraft/server/Main.java").exists())
			editConfigFile("eclipse/.metadata/.plugins/org.eclipse.debug.core/.launches/Server.launch",
				"net.minecraft.server.MinecraftServer", "net.minecraft.server.Main");
		
		if(javaVersion.equals("17") || javaVersion.equals("21"))
		{
			editConfigFile("eclipse/Server/.project", "<arguments>1.0-name-matches-false-false-libraries</arguments>",
				"<arguments>1.0-name-matches-false-true-(libraries|serverLibraries)</arguments>");
			addServerFilter("eclipse/Server/.project");
		}
		
		System.out.println("Done");
		return 0;
	}
	
	private String getJavaVersion(String[] split)
	{
		if(Integer.parseInt(split[1]) <= 16)
			return "1.8";
		
		if(Integer.parseInt(split[1]) <= 17)
			return "16";
		
		if(Integer.parseInt(split[1]) <= 19)
			return "17";
		
		if(Integer.parseInt(split[1]) == 20 && (split.length == 2 || Integer.parseInt(split[2]) <= 4))
			return "17";
		
		return "21";
	}
	
	private int writeClientClasspath(File libraries, String version)
	{
		File classpath = new File(mcpFolder, "eclipse/Client/.classpath");
		if(!classpath.exists())
		{
			System.out.println(".classpath is missing in eclipse/Client");
			return 4;
		}
		try
		{
			System.out.println("Writing to .classpath for client");
			File nativesFolder = new File(mcpFolder, "jars/versions/" + version + "/" + version + "-natives");
			boolean inclNatives = nativesFolder.exists() && nativesFolder.listFiles().length > 0;
			
			FileWriter writer = new FileWriter(classpath);
			writeLine(writer, 0, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			writeLine(writer, 0, "<classpath>");
			writeLine(writer, 1, "<classpathentry kind=\"src\" path=\"src\"/>");
			writeLine(writer, 1, "<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER/"
				+ "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-" + javaVersion + "\"/>");
			writeLine(writer, 1, "<classpathentry kind=\"lib\" path=\"jars/versions/" + version + "/"
				+ version +".jar\">");
			writeLine(writer, 2, "<attributes>");
			if(inclNatives)
				writeLine(writer, 3, "<attribute name=\"org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY\" "
					+ "value=\"Client/jars/versions/" + version + "/" + version + "-natives\"/>");
			writeLine(writer, 2, "</attributes>");
			writeLine(writer, 1, "</classpathentry>");
			List<File> files = new ArrayList<>();
			LinkedHashMap<File, File> sourceFiles = new LinkedHashMap<>();
			getAllFiles(libraries, files);
			for(File file : files)
				if(file.getName().endsWith("-sources.jar"))
				{
					File mainFile = files.stream().filter(f -> f.getParent().equals(file.getParent())
						&& (file.getName().substring(0, file.getName().length() - 12) + ".jar").equals(f.getName()))
							.findFirst().orElse(null);
					if(mainFile == null)
						System.out.println("Warning: Source JAR was found but main JAR was not: " + file.getName());
					else
						sourceFiles.put(mainFile, file);
				}
			for(Entry<File, File> entry : sourceFiles.entrySet())
			{
				files.remove(entry.getKey());
				files.remove(entry.getValue());
			}
			//Write libraries
			for(File file : files)
			{
				String relPath = mcpFolder.toURI().relativize(file.toURI()).getPath();
				writeLine(writer, 1, "<classpathentry kind=\"lib\" path=\"" + relPath + "\"/>");
			}
			//Write libraries with sources
			for(Entry<File, File> entry : sourceFiles.entrySet())
			{
				String relPathMain = mcpFolder.toURI().relativize(entry.getKey().toURI()).getPath();
				String sourcePathMain = mcpFolder.toURI().relativize(entry.getValue().toURI()).getPath();
				writeLine(writer, 1, "<classpathentry kind=\"lib\" sourcepath=\""+ sourcePathMain + "\" "
					+ "path=\"" + relPathMain + "\"/>");
			}
			writeLine(writer, 1, "<classpathentry kind=\"output\" path=\"bin\"/>");
			writeLine(writer, 0, "</classpath>");
			writer.close();
			System.out.println("Done writing .classpath for client");
			return 0;
		}catch(Exception e)
		{
			e.printStackTrace();
			System.out.println("Exception writing .classpath for client");
			return 4;
		}
	}
	
	private int writeServerClasspath(String version)
	{
		if(!new File(mcpFolder, "jars/minecraft_server." + version + ".jar").exists())
		{
			System.out.println("Skipping write to .classpath for server as server JAR is missing");
			return 0;
		}
		File classpath = new File(mcpFolder, "eclipse/Server/.classpath");
		if(!classpath.exists())
		{
			System.out.println(".classpath is missing in eclipse/Server");
			return 4;
		}
		try
		{
			System.out.println("Writing to .classpath for server");
			
			FileWriter writer = new FileWriter(classpath);
			writeLine(writer, 0, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			writeLine(writer, 0, "<classpath>");
			writeLine(writer, 1, "<classpathentry kind=\"src\" path=\"src\"/>");
			writeLine(writer, 1, "<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER/"
				+ "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-" + javaVersion + "\"/>");
			writeLine(writer, 1, "<classpathentry kind=\"lib\" path=\"jars/minecraft_server." + version + ".jar\"/>");
			if(javaVersion.equals("1.8"))
				writeLine(writer, 1, "<classpathentry kind=\"lib\" sourcepath=\"jars/libraries/com/google/code/findbugs/jsr305/"
					+ "3.0.1/jsr305-3.0.1-sources.jar\" path=\"jars/libraries/com/google/code/findbugs/jsr305/3.0.1/jsr305-3.0.1.jar\"/>");
			else
			{
				List<String> librariesToMove = new ArrayList<>();
				librariesToMove.add("com/google/code/findbugs/jsr305");
				librariesToMove.add("org/jetbrains/annotations");
				
				// Find extra libraries in joined.fernflower.libs.txt
				File libraries = new File(mcpFolder, "conf/joined.fernflower.libs.txt");
				if(!libraries.exists())
				{
					System.out.println("joined.fernflower.libs.txt is missing");
					return 4;
				}
				
				// Read libraries list from file
				outer:
				for(String line : Files.readAllLines(libraries.toPath()))
				{
					String fullLibPath = line.substring(3).replace("\\", "/");
					int libStart = fullLibPath.lastIndexOf("libraries/");
					String partialLibPath = fullLibPath.substring(libStart + 10);
					Iterator<String> itr = librariesToMove.iterator();
					while(itr.hasNext())
					{
						String library = itr.next();
						if(partialLibPath.contains(library))
						{
							File libFile = new File(mcpFolder, "jars/libraries/" + partialLibPath);
							if(!libFile.exists())
								System.out.println("Library was not found in expected location: " + libFile.getAbsolutePath());
							String relPath = mcpFolder.toURI().relativize(libFile.toURI()).getPath();
							writeLine(writer, 1, "<classpathentry kind=\"lib\" path=\"" + relPath + "\"/>");
							itr.remove();
							if(librariesToMove.isEmpty())
								break outer;
						}
					}
				}
			}
			if(javaVersion.equals("17") || javaVersion.equals("21"))
			{
				File libraries = new File(mcpFolder, "jars/serverLibraries");
				if(!libraries.exists())
				{
					System.out.println("Server libraries folder is missing (jars/serverLibraries)");
					System.out.println("You should use the fixer's downloader option to extract server libraries");
					return 4;
				}
				List<File> files = new ArrayList<>();
				getAllFiles(libraries, files);
				//Write libraries
				for(File file : files)
				{
					String relPath = mcpFolder.toURI().relativize(file.toURI()).getPath();
					writeLine(writer, 1, "<classpathentry kind=\"lib\" path=\"" + relPath + "\"/>");
				}
			}
			writeLine(writer, 1, "<classpathentry kind=\"output\" path=\"bin\"/>");
			writeLine(writer, 0, "</classpath>");
			writer.close();
			System.out.println("Done writing .classpath for server");
			return 0;
		}catch(Exception e)
		{
			e.printStackTrace();
			System.out.println("Exception writing .classpath for server");
			return 4;
		}
	}
	
	private void editConfigFile(String relPath, String find, String replace)
	{
		try
		{
			File file = new File(mcpFolder, relPath);
			
			if(!file.exists())
			{
				System.out.println("Warning: " + file.getAbsolutePath() + " is missing, skipping");
				return;
			}
			
			boolean modified = false;
			List<String> lines = Files.readAllLines(file.toPath());
			for(int i = 0; i < lines.size(); i++)
			{
				String line = lines.get(i);
				if(line.contains(find))
				{
					lines.set(i, line.replace(find, replace));
					modified = true;
				}
			}
			
			if(modified)
			{
				Files.write(file.toPath(), lines);
				System.out.println("Fixed configuration at " + relPath);
			}
		}catch(Exception e)
		{
			e.printStackTrace();
			System.out.println("Exception fixing configuration at " + relPath);
		}
	}
	
	private void addServerFilter(String relPath)
	{
		try
		{
			File file = new File(mcpFolder, relPath);
			
			if(!file.exists())
			{
				System.out.println("Warning: " + file.getAbsolutePath() + " is missing, skipping");
				return;
			}
			
			List<String> lines = Files.readAllLines(file.toPath());
			// Count </filter>
			int filterCount = 0;
			int lastFilterIdx = -1;
			for(int i = 0; i < lines.size(); i++)
			{
				String line = lines.get(i);
				if(line.contains("</filter>"))
				{
					filterCount++;
					lastFilterIdx = i;
				}
			}
			
			if(filterCount == 3)
			{
				List<String> filterObj = new ArrayList<>();
				filterObj.add("\t\t</filter>");
				filterObj.add("\t\t<filter>");
				filterObj.add("\t\t\t<id>1462950235937</id>");
				filterObj.add("\t\t\t<name>jars/serverLibraries</name>");
				filterObj.add("\t\t\t<type>29</type>");
				filterObj.add("\t\t\t<matcher>");
				filterObj.add("\t\t\t\t<id>org.eclipse.ui.ide.multiFilter</id>");
				filterObj.add("\t\t\t\t<arguments>1.0-name-matches-false-false-*</arguments>");
				filterObj.add("\t\t\t</matcher>");
				Collections.reverse(filterObj);
				
				for(String line : filterObj)
					lines.add(lastFilterIdx, line);
				
				Files.write(file.toPath(), lines);
				System.out.println("Added serverLibraries filter to Server project");
			}
		}catch(Exception e)
		{
			e.printStackTrace();
			System.out.println("Exception fixing Server project filters");
		}
	}
	
	private void writeLine(FileWriter writer, int numTabs, String str) throws IOException
	{
		for(int i = 0; i < numTabs; i++)
			writer.write("\t");
		writer.write(str + "\n");
	}
	
	private void getAllFiles(File directory, List<File> files)
	{
		File[] fileList = directory.listFiles();
		if(fileList != null)
			for(File file : fileList)
				if(file.isFile())
					files.add(file);
				else if(file.isDirectory())
					getAllFiles(file, files);
	}
}
