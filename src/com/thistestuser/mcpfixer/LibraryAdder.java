package com.thistestuser.mcpfixer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;

public class LibraryAdder
{
	private final File mcpFolder;
	private List<String> librariesToMove = new ArrayList<>();
	
	public LibraryAdder(File mcpFolder)
	{
		this.mcpFolder = mcpFolder;
		librariesToMove.add("ca/weblite/java-objc-bridge");
		librariesToMove.add("com/google/code/findbugs/jsr305");
		librariesToMove.add("org/jetbrains/annotations");
	}
	
	public int run()
	{
		// Find conf/joined.fernflower.libs.txt
		File confFolder = new File(mcpFolder, "conf");
		File libsFile = new File(confFolder, "joined.fernflower.libs.txt");
		
		if(!libsFile.exists())
		{
			System.out.println("joined.fernflower.libs.txt not found");
			System.out.println("Did you copy the file from MCPConfig?");
			System.out.println("Remember that this tool is only needed for 1.17 and above");
			return 4;
		}
		
		try
		{
			List<String> modifiedLines = new ArrayList<>();
			
			// Read libraries list from file
			for(String line : Files.readAllLines(libsFile.toPath()))
			{
				String fullLibPath = line.substring(3).replace("\\", "/");
				int libStart = fullLibPath.lastIndexOf("build/libraries/");
				if(libStart == -1)
				{
					System.out.println("One of the libraries linked does not appear to be in MCPConfig: " + fullLibPath);
					System.out.println("You may have run the adder tool already");
					return 4;
				}
				
				String partialLibPath = fullLibPath.substring(libStart + 6);
				File mcpFile = new File(mcpFolder, "jars/" + partialLibPath);
				
				if(mcpFile.exists())
					// Replace with path in MCP
                    modifiedLines.add("-e=" + mcpFile.getAbsolutePath());
				else
				{
					boolean moved = false;
					Iterator<String> itr = librariesToMove.iterator();
					while(itr.hasNext())
					{
						String library = itr.next();
						if(partialLibPath.contains(library))
						{
							File orig = new File(fullLibPath);
							if(!orig.exists())
							{
								System.out.println("Library referenced was not found: " + orig);
								System.out.println("Ensure that MCPConfig has not been moved or deleted");
								return 4;
							}
							FileUtils.forceMkdir(mcpFile.getParentFile());
							Files.copy(orig.toPath(), mcpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
							itr.remove();
							System.out.println("Moved library from " + orig.toPath());
							moved = true;
							break;
						}
					}
					if(moved)
						modifiedLines.add("-e=" + mcpFile.getAbsolutePath());
					else
						modifiedLines.add(line);
				}
			}
			
			// Write back to file
			Files.write(libsFile.toPath(), modifiedLines);
			System.out.println("Saving changes to joined.fernflower.libs.txt");
		}catch(Exception e)
		{
			e.printStackTrace();
			System.out.println("Exception occurred while processing joined.fernflower.libs.txt");
			return 4;
		}
		
		return 0;
	}
}
