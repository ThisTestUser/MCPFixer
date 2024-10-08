package com.thistestuser.mcpfixer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PatchFixer
{
	private final File input;
	private final File output;

	public PatchFixer(File input, File output)
	{
		this.input = input;
		this.output = output;
	}

	public int run()
	{
		System.out.println("Running MCP patch fixer");
		File inject = new File(input, "inject");
		if(!inject.exists())
		{
			System.out.println("Inject folder not found");
			return 4;
		}

		try
		{
			File outputPatch = new File(output, "patches");
			if(outputPatch.exists())
			{
				System.out.println("Please delete patches folder before running");
				return 4;
			}
			outputPatch.mkdir();
			File outputInject = new File(outputPatch, "inject");
			outputInject.mkdir();
			List<File> files = new ArrayList<>();
			getAllFiles(inject.toString(), files);
			for(File f : files)
			{
				if(f.toString().endsWith("Start.java"))
				{
					File dest = new File(outputPatch, "Start.java");
					Files.copy(Paths.get(f.toURI()), Paths.get(dest.toURI()), StandardCopyOption.REPLACE_EXISTING);
					Scanner scanner = new Scanner(dest);
					scanner.nextLine();
					scanner.nextLine();
					BufferedWriter out = new BufferedWriter(new FileWriter(dest));
					while(scanner.hasNextLine())
					{
					    String next = scanner.nextLine();
					    if(next.equals("\n")) 
					    	out.newLine();
					    else 
					    	out.write(next);
					    out.newLine(); 
					}
					out.close();
					scanner.close();
				}else if(f.toString().endsWith("MethodsReturnNonnullByDefault.java"))
				{
					File mcp = new File(new File(outputInject, "common"), "mcp");
					mcp.mkdirs();
					Files.copy(Paths.get(f.toURI()), Paths.get(new File(mcp, "MethodsReturnNonnullByDefault.java").toURI()),
						StandardCopyOption.REPLACE_EXISTING);
				}else if(f.toString().endsWith("package-info-template.java"))
					Files.copy(Paths.get(f.toURI()), Paths.get(new File(outputInject, "package-info-template.java").toURI()),
						StandardCopyOption.REPLACE_EXISTING);
			}
			System.out.println("Copied inject folder from patches");
		}catch(Exception e)
		{
			e.printStackTrace();
			System.out.println("Could not move inject folder");
			return 4;
		}

		File patches = new File(input, "patches");
		if(!patches.exists())
		{
			System.out.println("Patches folder not found");
			return 4;
		}

		try
		{
			List<File> files = new ArrayList<>();
			getAllFiles(patches.toString(), files);
			File newPatches = new File(output.getPath(), "patches");
			File client = new File(newPatches, "minecraft_ff");
			File both = new File(newPatches, "minecraft_merged_ff");
			File server = new File(newPatches, "minecraft_server_ff");
			
			for(File f : files)
			{
				String relativeName = patches.toURI().relativize(f.toURI()).getPath();
				if(!relativeName.endsWith(".patch"))
				{
					System.out.println("Unknown file " + relativeName + ", skipping");
					continue;
				}
				File parent;
				if(relativeName.startsWith("client"))
					parent = client;
				else if(relativeName.startsWith("joined"))
					parent = both;
				else if(relativeName.startsWith("server"))
					parent = server;
				else if(relativeName.startsWith("shared"))
					//Add to all 3 folders
					parent = null;
				else
				{
					System.out.println("Unknown file " + relativeName + ", skipping");
					continue;
				}
				String javaName = relativeName.substring(relativeName.indexOf('/')).replace(".patch", "").replace("/", "\\");
				List<String> write = new ArrayList<>();
				try(BufferedReader reader = new BufferedReader(new FileReader(f)))
				{
					//Skipping first 2 lines
					reader.readLine();
					reader.readLine();
					String line;
					while((line = reader.readLine()) != null)
						write.add(line);
					write.add(0, "+++ minecraft_patched" + javaName);
					write.add(0, "--- minecraft" + javaName);
					write.add(0, "diff -r -U 3 minecraft" + javaName + " minecraft_patched" + javaName);
				}
				File[] writeParents = parent == null ? new File[] {client, both, server} : new File[] {parent};
				for(File par : writeParents)
				{
					File output = new File(par, javaName.substring(1, javaName.length()).replace("\\", ".") + ".patch");
					output.getParentFile().mkdirs();
					output.createNewFile();
					FileWriter writer = new FileWriter(output);
					for(String w : write)
						writer.write(w + "\n");
					writer.close();
				}
			}
			System.out.println("Wrote all patches");
		}catch(Exception e)
		{
			e.printStackTrace();
			System.out.println("Error writing patches");
			return 4;
		}
		
		System.out.println("Done");
		return 0;
	}

	private void getAllFiles(String directoryName, List<File> files)
	{
		File directory = new File(directoryName);
		
		File[] fileList = directory.listFiles();
		if(fileList != null)
			for(File file : fileList)
				if(file.isFile())
					files.add(file);
				else if(file.isDirectory())
					getAllFiles(file.getAbsolutePath(), files);
	}
}
