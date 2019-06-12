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
			"The input where files will be read");
		options.addOption("o", "output", true, "The output to place files");
		
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
		
		if(!cmdLine.hasOption("input"))
		{
			System.out.println("No input specified");
			return 2;
		}
		
		String input = cmdLine.getOptionValue("input");
		
		File inputFolder = new File(input);
		if(!inputFolder.exists())
		{
			System.out.println("Invaild input location");
			return 2;
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
		
		return new Fixer(inputFolder, outputFolder).run();
	}
}
