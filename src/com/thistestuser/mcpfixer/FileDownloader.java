package com.thistestuser.mcpfixer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FileDownloader
{
	private final String version;
	private final File mcpFolder;
	
	public FileDownloader(String version, File mcpFolder)
	{
		this.version = version;
		this.mcpFolder = mcpFolder;
	}
	
	public int run() throws IOException
	{
		System.out.println("Downloading version manifest");
		URL manifestURL = new URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
		File manifest = new File(mcpFolder, "jars/version_manifest_v2.json");
		FileUtils.copyURLToFile(manifestURL, manifest);
		if(!manifest.exists())
		{
			System.out.println("Unable to download version manifest");
			return 4;
		}
		return downloadJson(manifest, version, mcpFolder);
	}
	
	private JsonObject getJsonAsObject(File json) throws IOException
	{
		Reader reader = Files.newBufferedReader(json.toPath());
		JsonElement element = JsonParser.parseReader(reader);
		return element.getAsJsonObject();
	}
	
	private int downloadJson(File manifest, String version, File mcpFolder)
		throws IOException
	{
		JsonObject manifestObject = getJsonAsObject(manifest);
		JsonArray versions = manifestObject.getAsJsonArray("versions");
		System.out.println("Downloading version JSON");
		for(int i = 0; i < versions.size(); i++)
		{
			JsonElement id = versions.get(i).getAsJsonObject().get("id");
			
			if(id.getAsString().equals(version))
			{
				URL url = new URL(
					versions.get(i).getAsJsonObject().get("url").getAsString());
				File versionJson = new File(mcpFolder,
					"jars/versions/" + version + "/" + version + ".json");
				FileUtils.copyURLToFile(url, versionJson);
				
				downloadSide(versionJson, mcpFolder, "client", version);
				downloadSide(versionJson, mcpFolder, "server", version);
				parseJson(version, versionJson, mcpFolder);
				
				System.out.println("Deleting version manifest");
				FileUtils.deleteQuietly(manifest);
				System.out.println("Done");
				return 0;
			}
		}
		System.out.println("Warning: The version " + version + " was not found in the manifest");
		return 4;
	}
	
	private void downloadSide(File json, File mcpFolder, String side,
		String version) throws IOException
	{
		JsonObject versionObject = getJsonAsObject(json);
		JsonObject downloads = versionObject.getAsJsonObject("downloads");
		JsonObject sideObject = downloads.getAsJsonObject(side);
		URL url = new URL(sideObject.get("url").getAsString());
		
		System.out.println("Downloading " + side);
		
		switch(side)
		{
			case "client":
				File clientJar = new File(mcpFolder, "jars/versions/" + version + File.separator + version + ".jar");
				FileUtils.copyURLToFile(url, clientJar);
				break;
			case "server":
				File serverJar = new File(mcpFolder, "jars/minecraft_server." + version + ".jar");
				FileUtils.copyURLToFile(url, serverJar);
				break;
		}
	}
	
	private void parseJson(String version, File json, File mcpFolder)
		throws IOException
	{
		JsonObject versionObject = getJsonAsObject(json);
		JsonArray libraries = versionObject.getAsJsonArray("libraries");
		
		for(int i = 0; i < libraries.size(); i++)
		{
			JsonObject entry = libraries.get(i).getAsJsonObject();
			JsonObject downloads = entry.getAsJsonObject("downloads");
			
			if(entry.has("rules"))
			{
				JsonArray rules = entry.getAsJsonArray("rules");
				
				if(isAllowed(rules))
				{
					downloadLibrary(downloads, mcpFolder);
					if(downloads.has("classifiers"))
						downloadNative(version, downloads.getAsJsonObject("classifiers"), mcpFolder, entry.has("extract"));
				}
				continue;
			}
			
			if(downloads.has("classifiers"))
			{
				downloadNative(version, downloads.getAsJsonObject("classifiers"), mcpFolder, entry.has("extract"));
				continue;
			}
			downloadLibrary(downloads, mcpFolder);
		}
	}
	
	private boolean isAllowed(JsonArray rules)
	{
		String os = OS.getOS().name;
		
		for(int i = 0; i < rules.size(); i++)
		{
			JsonObject ruleEntry = rules.get(i).getAsJsonObject();
			JsonElement action = ruleEntry.get("action");
			
			JsonObject osRule = ruleEntry.getAsJsonObject("os");
			
			if(Objects.equals(action.getAsString(), "disallow"))
				if(ruleEntry.has("os"))
				{
					JsonElement name = osRule.get("name");
					if(Objects.equals(name.getAsString(), os) || name.getAsString().contains("osx") && os.contains("mac"))
						return false;
					else
						return true;
				}else
					continue;
			
			if(Objects.equals(action.getAsString(), "allow"))
				if(ruleEntry.has("os"))
				{
					JsonElement name = osRule.get("name");
					if(Objects.equals(name.getAsString(), os) || name.getAsString().contains("osx") && os.contains("mac"))
						return true;
				}
		}
		return false;
	}
	
	private void downloadLibrary(JsonObject downloads, File mcpFolder)
		throws IOException
	{
		String path = downloads.getAsJsonObject("artifact").get("path").getAsString();
		System.out.println("Downloading library " + path.substring(path.lastIndexOf('/') + 1));
		
		File file = new File(mcpFolder, "jars/libraries/" + path);
		URL url = new URL(downloads.getAsJsonObject("artifact").get("url").getAsString());
		FileUtils.copyURLToFile(url, file);
	}
	
	// < 1.19
	private void downloadNative(String version, JsonObject classifiers,
		File mcpFolder, boolean extract) throws IOException
	{
		String os = OS.getOS().name;
		
		if(classifiers.has("natives-" + os))
		{
			String path = classifiers.getAsJsonObject("natives-" + os).get("path").getAsString();
			String url = classifiers.getAsJsonObject("natives-" + os).get("url").getAsString();
			
			System.out.println("Downloading native " + path.substring(path.lastIndexOf('/') + 1));
			File nativeFile = new File(mcpFolder, "jars/libraries/" + path);
			FileUtils.copyURLToFile(new URL(url), nativeFile);
			
			if(extract)
			{
				System.out.println("Extracting native " + path.substring(path.lastIndexOf('/') + 1));
				extractNative(version, nativeFile, mcpFolder);
			}
		}
	}
	
	// < 1.19
	private void extractNative(String version, File nativeFile, File mcpFolder)
		throws IOException
	{
		File nativesDir =
			new File(mcpFolder + File.separator + "jars/versions/" + version
				+ File.separator + version + "-natives" + File.separator);
		
		if(!nativesDir.exists())
			FileUtils.forceMkdir(nativesDir);
		
		ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(Paths.get(nativeFile.getAbsolutePath())));
		ZipEntry entry = inputStream.getNextEntry();
		
		while(entry != null)
		{
			String name = entry.getName();
			String output = nativesDir.getAbsolutePath() + File.separator + name.substring(name.lastIndexOf('/') + 1);
			String[] included = new String[]{"dll", "dylib", "so"};
			
			if(!entry.isDirectory() && Arrays.stream(included).anyMatch(entry.getName()::endsWith))
			{
				BufferedOutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(Paths.get(output)));
				byte[] b = new byte[4096];
				int len;
				while((len = inputStream.read(b)) > 0)
					outputStream.write(b, 0, len);
				outputStream.close();
			}else if(entry.isDirectory())
			{
				File subdir = new File(output);
				FileUtils.forceMkdir(subdir);
			}
			inputStream.closeEntry();
			entry = inputStream.getNextEntry();
		}
		inputStream.close();
	}
	
	//Derived from https://github.com/MinecraftForge/ForgeGradle/blob/FG_5.0/src/common/java/net/minecraftforge/gradle/common/util/VersionJson.java
	private enum OS
	{
		WINDOWS("windows"),
		MACOS("macos"),
		LINUX("linux"),
		UNKNOWN("unknown");
		
		private final String name;
		
		OS(String name)
		{
			this.name = name;
		}
		
		public static OS getOS()
		{
			String property =
				System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
			
			for(OS os : OS.values())
				if(property.contains(os.name))
					return os;
			return UNKNOWN;
		}
	}
}
