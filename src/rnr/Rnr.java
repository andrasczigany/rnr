package rnr;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class Rnr {
    private static final String TMP = "tmp-zip\\";
    private static String seriesNamePattern;
    private static String seasonPattern;
    private static boolean zipMode;

    public static void main(String[] args) {
        String subRoot = ".";
        String mkvRoot = ".";

        if (args.length == 4) {
            subRoot = args[0];
            mkvRoot = args[1];
            seriesNamePattern = args[2];
            seasonPattern = args[3];
        } else if (args.length == 3) {
            seriesNamePattern = args[0];
            seasonPattern = args[1];
            zipMode = "zip".equals(args[2]);
        } else if (args.length == 2) {
            seriesNamePattern = args[0];
            seasonPattern = args[1];
        } else {
            System.out.println("\nReNameR: finds and pairs subtitles for series video files, if given name and season patterns match (not case sensitive).");
            System.out.println("Also looks for subtitles either in a given .zip file or finds .zip file by given name and season if \"zip\" switch is used.");
            System.out.println("Supported file formats: .srt and .mkv\n");
            System.out.println("args: [subtitles zip/folder, mkv root folder,] movie pattern, season pattern [, \"zip\"]");
            System.out.println("e.g. \"java rnr.Rnr wire s01\"");
            System.out.println("e.g. \"java rnr.Rnr wire s01 zip\"");
            System.out.println("e.g. \"java rnr.Rnr d:\\subtitle_folder . wire s01 \"");
            System.out.println("e.g. \"java rnr.Rnr subtitle.zip d:\\series_folder wire s01\"");
            return;
        }

        if (subRoot.endsWith(".zip")) subRoot = unzipSubs(subRoot);

        if (zipMode) {
            LinkedList<Path> zipPaths = new LinkedList<>();
            findZip(subRoot, seriesNamePattern, seasonPattern, zipPaths);
            if (zipPaths.size() > 1)
                System.out.println("\nWarning: more zip files found with matching name, the first will be used. See all matches: " + zipPaths);
            System.out.println("\nZip file to be searched for subtitles: " + zipPaths.getFirst().toAbsolutePath().normalize().toString());
            subRoot = unzipSubs(zipPaths.getFirst().toString());
        }

        HashMap<String, Path> srtFiles = new HashMap<>();
        collectFiles(subRoot, 1, srtFiles, "srt", seriesNamePattern, seasonPattern, "sample");
        System.out.println("\nSubtitle files found in " + (TMP.equals(subRoot) ? "zip" : (subRoot == "." ? System.getProperty("user.dir") : subRoot)) + " with series name \"" + seriesNamePattern + "\", season " + seasonPattern + ":");
        for (String key : srtFiles.keySet()) System.out.println("  episode #" + key + " -> " + srtFiles.get(key));

        HashMap<String, Path> mkvFiles = new HashMap<>();
        collectFiles(mkvRoot, 9, mkvFiles, "mkv", seriesNamePattern, seasonPattern, "sample");
        System.out.println("\nVideo files found in " + (mkvRoot == "." ? System.getProperty("user.dir") : mkvRoot) + " with series name \"" + seriesNamePattern + "\", season " + seasonPattern + ":");
        for (String key : mkvFiles.keySet()) System.out.println("  episode #" + key + " -> " + mkvFiles.get(key));

        System.out.println("\nCopying:");
        for (String s : srtFiles.keySet()) renameAndCopy(s, srtFiles.get(s), mkvFiles.get(s));

        if (subRoot.equals(TMP))
            try {
                Files.walk(Paths.get(TMP))
                        .map(Path::toFile)
                        .sorted((o1, o2) -> -o1.compareTo(o2))
                        .forEach(File::delete);
            } catch (IOException e) {
                System.out.println("IO exception while deleting temporary files: " + e.getMessage());
            }
    }

    private static String unzipSubs(String zipPath) {
        try {
            Files.createDirectories(Paths.get(TMP));
            ZipFile zipFile = new ZipFile(zipPath);
            ArrayList<String> subs = new ArrayList<>();
            FileSystem fs = FileSystems.newFileSystem(Paths.get(zipPath), null);
            zipFile.stream().forEach(f -> subs.add(f.toString()));
            for (String s : subs) {
                zipFile.stream().filter(f -> s.equals(f.getName()))
                        .forEach(f -> {
                            try {
                                Files.copy(fs.getPath(s), Paths.get(TMP + s), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
                            } catch (IOException e) {
                                System.out.println("IO exception while copying subtitle files from zip: " + e.getMessage());
                            }
                        });
            }
            zipFile.close();
            return TMP;
        } catch (IOException e) {
            System.out.println("IO exception while processing zip file: " + e.getMessage());
        }
        return ".";
    }

    private static void findZip(String root, String match1, String match2, LinkedList<Path> result) {
        try {
            Files.walk(Paths.get(root), 1)
                    .filter(p -> p.toString().toLowerCase().endsWith("zip".toLowerCase()))
                    .filter(p -> p.toString().toLowerCase().contains(match1.toLowerCase()))
                    .filter(p -> p.toString().toLowerCase().contains(match2.toLowerCase()))
                    .forEach(p -> result.add(p));
        } catch (IOException ioe) {
            System.out.println("IO exception while collecting subtitle zip: " + ioe.getMessage());
        } catch (Exception e) {
            System.out.println("Exception while collecting subtitle zip: " + e.getMessage());
        }
    }

    private static void collectFiles(String root, int level, HashMap<String, Path> results, String extension, String match1, String match2, String exclude) {
        try {
            Files.walk(Paths.get(root), level)
                    .filter(p -> p.toString().toLowerCase().endsWith(extension.toLowerCase()))
                    .filter(p -> p.toString().toLowerCase().contains(match1.toLowerCase()))
                    .filter(p -> p.toString().toLowerCase().contains(match2.toLowerCase()))
                    .filter(p -> !p.toString().toLowerCase().contains(exclude.toLowerCase()))
                    .forEach(p -> results.put(getEpisodeNumber(p.toString()), p));
        } catch (IOException ioe) {
            System.out.println("IO exception while collecting subtitle files: " + ioe.getMessage());
        } catch (Exception e) {
            System.out.println("Exception while collecting subtitle files: " + e.getMessage());
        }
    }

    private static String getEpisodeNumber(String name) {
        Matcher m = Pattern.compile("\\d{2}").matcher(name.substring(name.lastIndexOf("\\")).replaceAll("(?i)" + seasonPattern, ""));
        while (m.find())
            return m.group();
        return "";
    }

    private static void renameAndCopy(String episode, Path sub, Path mov) {
        if (mov == null) return;
        Path target = Paths.get(mov.getParent() + "/" + mov.getFileName().toString().replaceAll("mkv", "srt"));
        try {
            Files.copy(sub, target, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
            System.out.println("  episode #" + episode + ": " + sub + " -> " + target);
        } catch (IOException e) {
            System.out.println("IO exception while copying subtitle files: " + e.getMessage());
        }
    }
}
