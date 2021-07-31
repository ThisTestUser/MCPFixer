package com.thistestuser.mcpfixer;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main
{
    public static void main(String[] args)
    {
        System.exit(run(args));
    }
    
	public static int run(String[] args)
	{
		Options options = new Options();
		options.addOption("i", "input", true,
			"The input where files will be read (patch mode only)");
		options.addOption("o", "output", true, "The output to place files (patch mode only)");
		options.addOption("c", "conf", true, "The config folder in your mcp workspace (csv mode only)");
		options.addOption("m", "mode", true, "Either \"patch\", \"csv\", or \"libraries\"");
		
		CommandLineParser cmdlineParser = new DefaultParser();
		CommandLine cmdLine;
		try
		{
			cmdLine = cmdlineParser.parse(options, args);
		}catch(ParseException e)
		{
			e.printStackTrace();
			System.out.println("Argument parsing error");
			return 1;
		}
		
		if(!cmdLine.hasOption("mode"))
		{
			System.out.println("No mode (patch, csv, or libraries) specified");
			return 2;
		}
		
		String mode = cmdLine.getOptionValue("mode");
		if(mode.equalsIgnoreCase("patch"))
		{
			if(!cmdLine.hasOption("input"))
			{
				System.out.println("No input specified");
				return 3;
			}
			
			String input = cmdLine.getOptionValue("input");
			
			File inputFolder = new File(input);
			if(!inputFolder.exists())
			{
				System.out.println("Invaild input location");
				return 3;
			}
			
			if(!cmdLine.hasOption("output"))
			{
				System.out.println("No output specified");
				return 3;
			}
			
			String output = cmdLine.getOptionValue("output");
			
			File outputFolder = new File(output);
			if(!outputFolder.exists())
			{
				System.out.println("Invaild output location");
				return 3;
			}
			return new PatchFixer(inputFolder, outputFolder).run();
		}
		if(mode.equalsIgnoreCase("csv"))
		{
			if(!cmdLine.hasOption("conf"))
			{
				System.out.println("No conf folder specified");
				return 3;
			}
			
			String conf = cmdLine.getOptionValue("conf");
			File confFolder = new File(conf);
			if(!confFolder.exists())
			{
				System.out.println("Invaild conf location");
				return 3;
			}
			
			return new MappingWriter(confFolder).run();
		}
		System.out.println("Invalid mode (patch, csv, or libraries) specified");
		return 2;
	}
}
