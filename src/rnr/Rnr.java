package rnr;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class Rnr
{
    private static final String TMP = "tmp-zip";
    private static String moviePattern;
    private static String seasonPattern;
    public static void main(String[] args)
    {
        String subRoot = ".";
        String mkvRoot = ".";

        if ( args.length == 4) {
            subRoot = args[0];
            mkvRoot = args[1];
            moviePattern = args[2];
            seasonPattern = args[3];
        }
        else if ( args.length == 2) {
            moviePattern = args[0];
            seasonPattern = args[1];
        }
        else
        {
            System.out.println("args: [subtitles zip/folder, mkv root folder,] movie pattern, season pattern)");
            return;
        }
        
        if (subRoot.endsWith(".zip")) subRoot = unzipSubs(subRoot);
        
        HashMap<String, Path> srtFiles = new HashMap<>();
        collectFiles(subRoot, 1, srtFiles, "srt", "", "", "sample");
        System.out.println("SRTs:" + srtFiles);

        HashMap<String, Path> mkvFiles = new HashMap<>();
        collectFiles(mkvRoot, 9, mkvFiles, "mkv", moviePattern, seasonPattern, "sample");
        System.out.println("MKVs:" + mkvFiles);
        
        for (String s : srtFiles.keySet()) renameAndCopy(srtFiles.get(s), mkvFiles.get(s));
        
        if (subRoot.equals(TMP))
            try
            {
                Files.walk(Paths.get(TMP))
                .map(Path::toFile)
                .sorted((o1, o2) -> -o1.compareTo(o2))
                .forEach(File::delete);
            }
            catch ( IOException e )
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
    }

    private static String unzipSubs(String zipPath)
    {
        try
        {
            Files.createDirectories(Paths.get(TMP));
            ZipFile zipFile = new ZipFile(zipPath);
            ArrayList<String> subs = new ArrayList<>();
            FileSystem fs = FileSystems.newFileSystem(Paths.get(zipPath), null);
            zipFile.stream().forEach(f -> subs.add(f.toString()));
            for ( String s : subs )
            {
                zipFile.stream().filter(f -> s.equals(f.getName()))
                    .forEach(f -> {
                        try
                        {
                            Files.copy(fs.getPath(s), Paths.get("tmp-zip/"+s), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
                        }
                        catch ( IOException e )
                        {
                            System.out.println("IO exception while copying subtitle files from zip: " + e.getMessage());
                        }
                    });
            }
            zipFile.close();
            return TMP;
        }
        catch ( IOException e )
        {
            System.out.println("IO exception while processing zip file: " + e.getMessage());
        }
        return ".";
    }

    private static void collectFiles(String root, int level, HashMap<String, Path> results, String extension, String match1, String match2, String exclude)
    {
        try
        {
            Files.walk(Paths.get(root), level)
                .filter(p -> p.toString().toLowerCase().endsWith(extension.toLowerCase()))
                .filter(p -> p.toString().toLowerCase().contains(match1.toLowerCase()))
                .filter(p -> p.toString().toLowerCase().contains(match2.toLowerCase()))
                .filter(p -> !p.toString().toLowerCase().contains(exclude.toLowerCase()))
                .forEach(p -> results.put(getEpisodeNumber(p.toString()), p));
        }
        catch ( IOException ioe )
        {
            System.out.println("IO exception while collecting subtitle files: " + ioe.getMessage());
        }
        catch ( Exception e)
        {
            System.out.println("Exception while collecting subtitle files: " + e.getMessage());
        }
    }

    private static String getEpisodeNumber(String name) {
        Matcher m = Pattern.compile("\\d{2}").matcher(name.replaceAll("(?i)"+seasonPattern, ""));
        while(m.find())
            return m.group();
        return "";
    }

    private static void renameAndCopy(Path sub, Path mov)
    {
        if (mov == null) return;
        Path target = Paths.get(mov.getParent() + "/" + mov.getFileName().toString().replaceAll("mkv", "srt"));
        try
        {
            Files.copy(sub, target, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
            System.out.println("COPY:" + sub + " -> " + target);
        }
        catch ( IOException e )
        {
            System.out.println("IO exception while copying subtitle files: " + e.getMessage());
        }
    }
}
