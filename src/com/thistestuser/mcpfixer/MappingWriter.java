package com.thistestuser.mcpfixer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IMappingFile.IClass;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;
import net.minecraftforge.srgutils.IMappingFile.IParameter;

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
			
			addFieldsAndMethods(clientMapping, srg, clientFields, clientMethods);
			addFieldsAndMethods(serverMapping, srg, serverFields, serverMethods);
			
			String[] header = new String[]{"searge", "name", "side", "desc"};
			List<String[]> fields = new ArrayList<>();
			List<String[]> methods = new ArrayList<>();
			List<String[]> params = new ArrayList<>();
			fields.add(header);
			methods.add(header);
			
			Map<String, String> methodJavadocs = new TreeMap<>();
			Map<String, String> fieldJavadocs = new TreeMap<>();
			
			File parchment = new File(confFolder, "parchment.json");
			if(parchment.exists())
			{
				System.out.println("Found parchment.json, mapping params and adding javadocs");
				
				try
				{
					// Load file as JSON
					Reader reader = Files.newBufferedReader(parchment.toPath());
					JsonElement element = JsonParser.parseReader(reader);
					
					JsonArray classes = element.getAsJsonObject().get("classes").getAsJsonArray();
					for(JsonElement cls : classes)
					{
						JsonObject classObj = cls.getAsJsonObject();
						
						IClass clientClazz = clientMapping.getClass(classObj.get("name").getAsString());
						IClass serverClazz = serverMapping.getClass(classObj.get("name").getAsString());
						
						if(clientClazz == null && serverClazz == null)
						{
							System.out.println("Warning: Could not find class " + classObj.get("name").getAsString());
							continue;
						}
						
						if(classObj.has("fields"))
						{
							JsonArray fieldsList = classObj.get("fields").getAsJsonArray();
							for(JsonElement field : fieldsList)
							{
								IField clientField = clientClazz != null ?
									clientClazz.getField(field.getAsJsonObject().get("name").getAsString()) : null;
								IField serverField = serverClazz != null ?
									serverClazz.getField(field.getAsJsonObject().get("name").getAsString()) : null;
								
								if(clientField == null && serverField == null)
								{
									System.out.println("Warning: Could not find field " + field.getAsJsonObject().get("name").getAsString()
										+ " in class " + classObj.get("name").getAsString());
									continue;
								}
								
								IClass joinedClazz = clientClazz == null ? serverClazz : clientClazz;
								IField joinedField = clientField == null ? serverField : clientField;
								
								// Add javadoc to field
								JsonArray javadoc = field.getAsJsonObject().get("javadoc").getAsJsonArray();
								javadoc.asList().stream().map(JsonElement::getAsString).reduce((a, b) -> a + "\n" + b).ifPresent(s -> {
									IClass srgClazz = srg.getClass(joinedClazz.getMapped());
									String name = srgClazz.remapField(joinedField.getMapped());
									s = s.replace("\"", "\"\"").replace("\n", "\\n");
									if(s.contains(","))
										s = "\"" + s + "\"";
									fieldJavadocs.put(name, s);
								});
							}
						}
						
						if(classObj.has("methods"))
						{
							JsonArray methodsList = classObj.get("methods").getAsJsonArray();
							for(JsonElement method : methodsList)
							{
								IMethod clientMethod = clientClazz != null ?
									clientClazz.getMethod(method.getAsJsonObject().get("name").getAsString(),
										method.getAsJsonObject().get("descriptor").getAsString()) : null;
								IMethod serverMethod = serverClazz != null ?
									serverClazz.getMethod(method.getAsJsonObject().get("name").getAsString(),
										method.getAsJsonObject().get("descriptor").getAsString()) : null;
								
								if(clientMethod == null && serverMethod == null)
								{
									System.out.println("Warning: Could not find method " + method.getAsJsonObject().get("name").getAsString()
										+ method.getAsJsonObject().get("descriptor").getAsString()
										+ " in class " + classObj.get("name").getAsString());
									continue;
								}
								
								IClass joinedClazz = clientClazz == null ? serverClazz : clientClazz;
								IMethod joinedMethod = clientMethod == null ? serverMethod : clientMethod;
								String side = serverMethod == null ? "0" : clientMethod == null ? "1" : "2";
								List<String> javadocs = new ArrayList<>();
								
								// Append javadoc to method
								if(method.getAsJsonObject().has("javadoc"))
								{
									JsonArray javadoc = method.getAsJsonObject().get("javadoc").getAsJsonArray();
									javadoc.asList().stream().map(JsonElement::getAsString).forEach(javadocs::add);
								}
								
								if(method.getAsJsonObject().has("parameters"))
								{
									// Get parameter list in srg mapping
									IClass srgClazz = srg.getClass(joinedClazz.getMapped());
									IMethod srgMethod = srgClazz.getMethod(joinedMethod.getMapped(), joinedMethod.getMappedDescriptor());
									Collection<? extends IParameter> srgParams = srgMethod.getParameters();
									
									// Get JVM indexes of parameters
									Map<Integer, Integer> paramIndexes =
										getParamIndexes(joinedMethod.getMappedDescriptor(), srgMethod.getMetadata().containsKey("is_static"));
									
									JsonArray paramsList = method.getAsJsonObject().get("parameters").getAsJsonArray();
									for(JsonElement paramMapping : paramsList)
									{
										IParameter param = srgParams.stream()
											.filter(p -> paramIndexes.get(p.getIndex()) == paramMapping.getAsJsonObject().get("index").getAsInt())
											.findFirst().orElse(null);
										if(param == null)
										{
											System.out.println("Warning: Could not find parameter "
												+ paramMapping.getAsJsonObject().get("index").getAsInt()
												+ " in method " + method.getAsJsonObject().get("name").getAsString()
												+ method.getAsJsonObject().get("descriptor").getAsString()
												+ " in class " + classObj.get("name").getAsString());
											continue;
										}
										
										// Add parameter mapping
										if(paramMapping.getAsJsonObject().has("name") && param.getMapped().startsWith("p_"))
										{
											String paramName = paramMapping.getAsJsonObject().get("name").getAsString();
											if(!paramName.startsWith("p"))
											{
												System.out.println("Only checked ParchmentMC mappings are supported!");
												System.out.println("Please redownload the mappings which should contain \"checked\" in the name");
												return 4;
											}
											params.add(new String[]{param.getMapped(), paramName, side});
										}
										
										// Append parameter javadoc to method
										if(paramMapping.getAsJsonObject().has("javadoc"))
										{
											String paramName = paramMapping.getAsJsonObject().has("name")
												? paramMapping.getAsJsonObject().get("name").getAsString()
													: param.getMapped();
											javadocs.add("@param " + paramName + " "
												+ paramMapping.getAsJsonObject().get("javadoc").getAsString());
										}
									}
								}
								
								// Add javadoc to method
								IClass srgClazz = srg.getClass(joinedClazz.getMapped());
								String name = srgClazz.remapMethod(joinedMethod.getMapped(), joinedMethod.getMappedDescriptor());
								String javadoc = javadocs.stream().reduce((a, b) -> a + "\n" + b).orElse("");
								javadoc = javadoc.replace("\"", "\"\"").replace("\n", "\\n");
								if(javadoc.contains(","))
									javadoc = "\"" + javadoc + "\"";
								methodJavadocs.put(name, javadoc);
							}
						}
					}
					System.out.println("Done parsing parchment.json");
				}catch(IllegalStateException e)
				{
					e.printStackTrace();
					System.out.println("Unexpected JSON format in parchment.json");
					return 4;
				}
			}
			params.sort((a, b) -> {
				// Assume format p_<number>_
				int aIndex = Integer.parseInt(a[0].substring(2, a[0].indexOf('_', 2)));
				int bIndex = Integer.parseInt(b[0].substring(2, b[0].indexOf('_', 2)));
				
				return Integer.compare(aIndex, bIndex);
			});
			params.add(0, new String[]{"param", "name", "side"});
			
			System.out.println("Writing to CSV");
			for(String name : clientFields.keySet())
			{
				String cname = clientFields.get(name);
				String sname = serverFields.get(name);
				String javadoc = fieldJavadocs.getOrDefault(name, "");
				if(cname.equals(sname))
				{
					fields.add(new String[]{name, cname, "2", javadoc});
					serverFields.remove(name);
				}else
					fields.add(new String[]{name, cname, "0", javadoc});
			}
			
			for(String name : clientMethods.keySet())
			{
				String cname = clientMethods.get(name);
				String sname = serverMethods.get(name);
				String javadoc = methodJavadocs.getOrDefault(name, "");
				if(cname.equals(sname))
				{
					methods.add(new String[]{name, cname, "2", javadoc});
					serverMethods.remove(name);
				}else
					methods.add(new String[]{name, cname, "0", javadoc});
			}
			
			serverFields.forEach((k,v) -> fields.add(new String[] {k, v, "1", ""}));
			serverMethods.forEach((k,v) -> methods.add(new String[] {k, v, "1", ""}));
			writeToCSV(new File(confFolder, "fields.csv"), fields);
			writeToCSV(new File(confFolder, "methods.csv"), methods);
			writeToCSV(new File(confFolder, "params.csv"), params);
		}catch(Exception e)
		{
			e.printStackTrace();
			System.out.println("Error while writing csv files");
			return 4;
		}
		
		System.out.println("Done");
		return 0;
	}
	
	private Map<Integer, Integer> getParamIndexes(String desc, boolean isStatic)
	{
		Map<Integer, Integer> indexes = new HashMap<>();
		int argIndex = 0;
		int jvmIndex = isStatic ? 0 : 1;
		if(desc.charAt(0) != '(')
			throw new IllegalArgumentException("Invalid method descriptor found: " + desc);
		
		int i = 1;
		while (i < desc.length())
		{
			char c = desc.charAt(i);
			switch(c)
			{
				case 'I':
				case 'F':
				case 'B':
				case 'C':
				case 'S':
				case 'Z':
					// Primitive types
					indexes.put(argIndex++, jvmIndex++);
					i++;
					break;
				case 'J':
				case 'D':
					// Primitive types (2 entries)
					indexes.put(argIndex++, jvmIndex);
					jvmIndex += 2;
					i++;
					break;
				case 'L':
					// Object type (skip until ';')
					indexes.put(argIndex++, jvmIndex++);
					while(desc.charAt(i) != ';')
						i++;
					i++;
					break;
				case '[':
					// Array type
					while(desc.charAt(i) == '[')
						i++;
					if(desc.charAt(i) == 'L')
						while(desc.charAt(i) != ';')
							i++;
					// Skip ';' or primitive type
					indexes.put(argIndex++, jvmIndex++);
					i++;
					break;
				case ')':
					i = desc.length();
					break;
				default:
					throw new IllegalArgumentException("Unknown type in method descriptor: " + c);
			}
		}
		
		return indexes;
	}
	
	private void addFieldsAndMethods(IMappingFile file, IMappingFile srg, Map<String, String> fields, Map<String, String> methods)
	{
		for(IClass cls : file.getClasses())
		{
			IClass obf = srg.getClass(cls.getMapped());
			if(obf == null)
				continue;
			for(IField fld : cls.getFields())
			{
				String name = obf.remapField(fld.getMapped());
				if(name.startsWith("f_") || name.startsWith("field_"))
					fields.put(name, fld.getOriginal());
			}
			for(IMethod mtd : cls.getMethods())
			{
				String name = obf.remapMethod(mtd.getMapped(), mtd.getMappedDescriptor());
				if(name.startsWith("m_") || name.startsWith("func_"))
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
