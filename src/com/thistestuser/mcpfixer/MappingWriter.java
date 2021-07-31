package com.thistestuser.mcpfixer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IMappingFile.IClass;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;

public class MappingWriter
{
	private final File confFolder;
	private final File client;
	private final File server;
	private final File intermediateSrg;
	
	public MappingWriter(File confFolder)
	{
		this.confFolder = confFolder;
		client = new File(confFolder, "client.txt");
		server = new File(confFolder, "server.txt");
		intermediateSrg = new File(confFolder, "obf_to_intermediate.tsrg");
	}
	
	public int run()
	{
		System.out.println("Running CSV mapping writer");
		if(!client.exists())
		{
			System.out.println("Need client.txt (obfuscation mappings)");
			return 4;
		}
		if(!server.exists())
		{
			System.out.println("Need server.txt (obfuscation mappings)");
			return 4;
		}
		if(!intermediateSrg.exists())
		{
			System.out.println("Need obf_to_intermediate.tsrg");
			return 4;
		}
		try
		{
			System.out.println("Reading mappings");
			IMappingFile clientMapping = IMappingFile.load(client);
	        IMappingFile serverMapping = IMappingFile.load(server);
	        IMappingFile srg = IMappingFile.load(intermediateSrg);
	        Map<String, String> clientFields = new TreeMap<>();
	        Map<String, String> clientMethods = new TreeMap<>();
	        Map<String, String> serverFields = new TreeMap<>();
	        Map<String, String> serverMethods = new TreeMap<>();
	
	        addFieldMethods(clientMapping, srg, clientFields, clientMethods);
	        addFieldMethods(serverMapping, srg, serverFields, serverMethods);
	
	        String[] header = new String[]{"searge", "name", "side", "desc"};
	        List<String[]> fields = new ArrayList<>();
	        List<String[]> methods = new ArrayList<>();
	        fields.add(header);
	        methods.add(header);
	        System.out.println("Writing to CSV");
	        for(String name : clientFields.keySet())
	        {
	        	String cname = clientFields.get(name);
	        	String sname = serverFields.get(name);
	        	if(cname.equals(sname))
	        	{
	        		fields.add(new String[]{name, cname, "2", ""});
	        		serverFields.remove(name);
	        	}else
	        		fields.add(new String[]{name, cname, "0", ""});
	        }
	
	        for(String name : clientMethods.keySet())
	        {
	            String cname = clientMethods.get(name);
	            String sname = serverMethods.get(name);
	            if(cname.equals(sname))
	            {
	                methods.add(new String[]{name, cname, "2", ""});
	                serverMethods.remove(name);
	            } else
	                methods.add(new String[]{name, cname, "0", ""});
	        }
	
	        serverFields.forEach((k,v) -> fields.add(new String[] {k, v, "1", ""}));
	        serverMethods.forEach((k,v) -> methods.add(new String[] {k, v, "1", ""}));
	        writeToCSV(new File(confFolder, "fields.csv"), fields);
	        writeToCSV(new File(confFolder, "methods.csv"), methods);
	        writeToCSV(new File(confFolder, "params.csv"), Collections.singletonList(new String[] {"param", "name", "side"}));
		}catch(Exception e)
		{
			e.printStackTrace();
			System.out.println("Error while writing csv files");
			return 4;
		}
        
        System.out.println("Done");
        return 0;
	}
	
	private void addFieldMethods(IMappingFile file, IMappingFile srg, Map<String, String> fields, Map<String, String> methods)
	{
        for(IClass cls : file.getClasses())
        {
            IClass obf = srg.getClass(cls.getMapped());
            if (obf == null)
                continue;
            for(IField fld : cls.getFields())
            {
            	String name = obf.remapField(fld.getMapped());
            	if(name.startsWith("f_"))
                    fields.put(name, fld.getOriginal());
            }
            for(IMethod mtd : cls.getMethods())
            {
                String name = obf.remapMethod(mtd.getMapped(), mtd.getMappedDescriptor());
                if(name.startsWith("m_"))
                    methods.put(name, mtd.getOriginal());
            }
        }
	}
	
	private void writeToCSV(File file, List<String[]> write) throws IOException
	{
		file.createNewFile();
		FileWriter writer = new FileWriter(file);
		for(String[] w : write)
			writer.write(String.join(",", w) + "\n");
		writer.close();
	}
}
