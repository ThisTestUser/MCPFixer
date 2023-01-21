package com.thistestuser.mcpfixer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;

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
                File manifest = new File(mcpFolder, "jars\\version_manifest_v2.json");

                FileUtils.copyURLToFile(manifestURL, manifest);

                if (manifest.exists())
                {
                        downloadJson(manifest, version, mcpFolder);
                }
                System.out.println("Done");
                return 0;
        }

        public JsonObject getJsonAsObject(File json) throws IOException
        {
                JsonObject object;
                Reader reader = Files.newBufferedReader(json.toPath());
                JsonElement element = JsonParser.parseReader(reader);
                object = element.getAsJsonObject();
                return object;
        }

        public void downloadJson(File manifest, String version, File mcpFolder) throws IOException
        {
                JsonObject manifestObject = getJsonAsObject(manifest);
                JsonArray versions = manifestObject.getAsJsonArray("versions");
                System.out.println("Downloading version JSON");
                for (int i = 0; i < versions.size(); i++)
                {
                        JsonElement id = versions.get(i).getAsJsonObject().get("id");

                        if (id.getAsString().equals(version))
                        {
                                URL url = new URL(versions.get(i).getAsJsonObject().get("url").getAsString());
                                File versionJson = new File(mcpFolder, "jars\\versions\\" + version + "/" + version + ".json");
                                FileUtils.copyURLToFile(url, versionJson);

                                downloadSide(versionJson, mcpFolder, "client", version);
                                downloadSide(versionJson, mcpFolder, "server", version);
                                parseJson(version, versionJson, mcpFolder);

                                System.out.println("Deleting version manifest");
                                FileUtils.deleteQuietly(manifest);
                                break;
                        }
                }
        }

        public void downloadSide(File json, File mcpFolder, String side, String version) throws IOException
        {
                JsonObject versionObject = getJsonAsObject(json);
                JsonObject downloads = versionObject.getAsJsonObject("downloads");
                JsonObject sideObject = downloads.getAsJsonObject(side);
                URL url = new URL(sideObject.get("url").getAsString());

                System.out.println("Downloading " + side);

                switch (side)
                {
                        case "client":
                                File clientJar = new File(mcpFolder, "jars\\versions\\" + version + File.separator + version + ".jar");
                                FileUtils.copyURLToFile(url, clientJar);
                        case "server":
                                File serverJar = new File(mcpFolder, "jars\\minecraft_server." + version + ".jar");
                                FileUtils.copyURLToFile(url, serverJar);
                }
        }

        public void parseJson(String version, File json, File mcpFolder) throws IOException
        {
                JsonObject versionObject = getJsonAsObject(json);
                JsonArray libraries = versionObject.getAsJsonArray("libraries");

                for (int i = 0; i < libraries.size(); i++)
                {
                        JsonObject entry = libraries.get(i).getAsJsonObject();
                        JsonObject downloads = entry.getAsJsonObject("downloads");

                        if (entry.has("rules"))
                        {
                                JsonArray rules = entry.getAsJsonArray("rules");

                                if (isAllowed(rules))
                                {
                                        downloadLibrary(downloads, mcpFolder);
                                        if (downloads.has("classifiers"))
                                        {
                                                downloadNative(version, downloads.getAsJsonObject("classifiers"), mcpFolder);
                                        }
                                }
                                continue;
                        }

                        if (downloads.has("classifiers"))
                        {
                                downloadNative(version, downloads.getAsJsonObject("classifiers"), mcpFolder);
                                continue;
                        }
                        downloadLibrary(downloads, mcpFolder);
                }
        }

        public boolean isAllowed(JsonArray rules)
        {
                String os = OS.getOS().name;

                for (int i = 0; i < rules.size(); i++)
                {
                        JsonObject ruleEntry = rules.get(i).getAsJsonObject();
                        JsonElement action = ruleEntry.get("action");

                        JsonObject osRule = ruleEntry.getAsJsonObject("os");

                        if (Objects.equals(action.getAsString(), "disallow"))
                        {
                                if (ruleEntry.has("os"))
                                {
                                        JsonElement name = osRule.get("name");
                                        if (Objects.equals(name.getAsString(), os) || (name.getAsString().contains("osx") && os.contains("mac")))
                                                return false;
                                        else return true;
                                }
                                else continue;
                        }

                        if (Objects.equals(action.getAsString(), "allow"))
                        {
                                if (ruleEntry.has("os"))
                                {
                                        JsonElement name = osRule.get("name");
                                        if (Objects.equals(name.getAsString(), os) || (name.getAsString().contains("osx") && os.contains("mac")))
                                                return true;
                                }
                        }
                }
                return false;
        }

        public static String getArchClassifier()
        {
                String arch = System.getProperty("os.arch");

                if(arch.equals("x86") || (arch.startsWith("i") && (arch.endsWith("86"))))
                {
                        return "-x86";
                }
                else if(arch.equals("x86_64"))
                {
                        return "-x86_64";
                }
                else if(arch.equals("aarch64"))
                {
                        return "-arm64";
                }
                else
                {
                        return ".";
                }
        }

        public void downloadLibrary(JsonObject downloads, File mcpFolder) throws IOException
        {
                String path = downloads.getAsJsonObject("artifact").get("path").getAsString();
                System.out.println("Downloading library " + path.substring(path.lastIndexOf('/') + 1));

                File file = new File(mcpFolder, "jars\\libraries\\" + path);
                URL url = new URL(downloads.getAsJsonObject("artifact").get("url").getAsString());
                FileUtils.copyURLToFile(url, file);

                String os = OS.getOS().name;
                if(path.contains("natives"))
                {
                        if(path.contains(os + getArchClassifier()))
                        {
                                System.out.println("Extracting native " + path.substring(path.lastIndexOf('/') + 1));
								
                                extractNative(version, file, mcpFolder);
                        }
                }
        }

        // < 1.19
        public void downloadNative(String version, JsonObject classifiers, File mcpFolder) throws IOException
        {
                String os = OS.getOS().name;

                if (classifiers.has("natives-" + os))
                {
                        String path = classifiers.getAsJsonObject("natives-" + os).get("path").getAsString();
                        String url = classifiers.getAsJsonObject("natives-" + os).get("url").getAsString();

                        System.out.println("Downloading native " + path.substring(path.lastIndexOf('/') + 1));

                        File nativeFile = new File(mcpFolder, "jars\\libraries\\" + path);
                        FileUtils.copyURLToFile(new URL(url), nativeFile);

                        System.out.println("Extracting native " + path.substring(path.lastIndexOf('/') + 1));

                        extractNative(version, nativeFile, mcpFolder);
                }
        }

        // < 1.19
        public void extractNative(String version, File nativeFile, File mcpFolder) throws IOException
        {
                File nativesDir = new File(mcpFolder + File.separator + "jars\\versions\\" + version + File.separator + version + "-natives" + File.separator);

                if (!nativesDir.exists())
                {
                        FileUtils.forceMkdir(nativesDir);
                }

                ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(Paths.get(nativeFile.getAbsolutePath())));
                ZipEntry entry = inputStream.getNextEntry();

                while (entry != null)
                {
                        String name = entry.getName();
                        String output = nativesDir.getAbsolutePath() + File.separator + name.substring(name.lastIndexOf('/') + 1);
                        String[] included = new String[] { "dll", "dylib", "so" };

                        if (!entry.isDirectory() && Arrays.stream(included).anyMatch(entry.getName()::endsWith))
                        {
                                BufferedOutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(Paths.get(output)));
                                byte[] b = new byte[4096];
                                int len;
                                while ((len = inputStream.read(b)) > 0)
                                {
                                        outputStream.write(b, 0, len);
                                }
                                outputStream.close();
                        }
                        else if (entry.isDirectory())
                        {
                                File subdir = new File(output);
                                FileUtils.forceMkdir(subdir);
                        }
                        inputStream.closeEntry();
                        entry = inputStream.getNextEntry();
                }
                inputStream.close();
        }

        // I ripped this enum from the ForgeGradle class VersionJson (with modification). Kudos to them.
        public enum OS
        {
                WINDOWS("windows"), MACOS("macos"), LINUX("linux"), UNKNOWN("unknown");

                private final String name;

                OS(String name)
                {
                        this.name = name;
                }

                public static OS getOS()
                {
                        String property = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

                        for (OS os : OS.values())
                        {
                                if (property.contains(os.name))
                                {
                                        return os;
                                }
                        }
                        return UNKNOWN;
                }
        }
}
