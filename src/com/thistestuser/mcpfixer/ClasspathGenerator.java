package com.thistestuser.mcpfixer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

public class ClasspathGenerator
{
	private final File mcpFolder;
	
	public ClasspathGenerator(File mcpFolder)
	{
		this.mcpFolder = mcpFolder;
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
		String version = versions.toURI().relativize(versions.listFiles()[0].toURI()).getPath();
		//Remove slash
		version = version.substring(0, version.length() - 1);
		File classpath = new File(mcpFolder, "eclipse/Client/.classpath");
		if(!classpath.exists())
		{
			System.out.println(".classpath is missing in eclipse/Client");
			return 4;
		}
		File libraries = new File(mcpFolder, "jars/libraries");
		if(!libraries.exists())
		{
			System.out.println("libraries folder is missing (did you run decompile?)");
			return 4;
		}
		try
		{
			System.out.println("Writing to .classpath");
			String[] split = version.split("\\.");
			boolean oldJava = Integer.parseInt(split[0]) == 1 && Integer.parseInt(split[1]) <= 16;
			boolean java16  = !oldJava && Integer.parseInt(split[0]) == 1 && Integer.parseInt(split[1]) == 17;
			FileWriter writer = new FileWriter(classpath);
			writeLine(writer, 0, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			writeLine(writer, 0, "<classpath>");
			writeLine(writer, 1, "<classpathentry kind=\"src\" path=\"src\"/>");
			String javaVersion = oldJava ? "1.6" : java16 ? "16" : "17";
			writeLine(writer, 1, "<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER/"
				+ "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-" + javaVersion + "\"/>");
			writeLine(writer, 1, "<classpathentry kind=\"lib\" path=\"jars/versions/" + version + "/"
				+ version +".jar\">");
			writeLine(writer, 2, "<attributes>");
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
		}catch(Exception e)
		{
			e.printStackTrace();
			System.out.println("Exception writing .classpath");
			return 4;
		}
		System.out.println("Done");
		return 0;
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
