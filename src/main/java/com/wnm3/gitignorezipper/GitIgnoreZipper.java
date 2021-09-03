/**
 * (c) Copyright 2021 IBM Corporation
 * 1 New Orchard Road, 
 * Armonk, New York, 10504-1722
 * United States
 * +1 914 499 1900
 * support: Nathaniel Mills wnm3@us.ibm.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.wnm3.gitignorezipper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InvalidObjectException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.management.modelmbean.InvalidTargetObjectTypeException;

/**
 * Utility to recusively examine .gitignore files to determine what is not being
 * stored in github repositories (e.g., customer data, certificates) and create
 * an input file that zip can use to capture these files. An optional .overrides
 * file can be used to keep .gitignored files from being added to the output
 * file (e.g., zipper.files) to reduce the size of the resulting zip file.
 * 
 * @author wnm3
 *
 */
public class GitIgnoreZipper {

   /**
    * Set to true to enable debugging output
    */
   static boolean debug = false;

   /**
    * Entry point to run this utility.
    * 
    * @param args
    *           output file name (e.g., zipper.files), input directory (e.g.,
    *           the directory to be searched recursively for .gitignore files to
    *           determine what has been left out of github repos to include in
    *           the output file, overrides file name (e.g., .overrides) listing
    *           directories and file names to be left out of the output file.
    */
   public static void main(String[] args) {
      GitIgnoreZipper pgm = new GitIgnoreZipper();
      String gitIgnore = ".gitignore";
      Set<String> gatheredContent = new HashSet<String>();
      String temp = "";
      System.out.println(GitIgnoreZipper.class.getSimpleName()
         + " Utility to recursively search .gitignore files for content to zip.");
      while (true) {
         // outputFilePath
         if (args.length > 0) {
            pgm._outputFilePath = args[0];
         } else {
            temp = pgm.prompt("Enter the output script name or q to exit (\""
               + pgm._outputFilePath + "\"):");
            if ("q".equalsIgnoreCase(temp)) {
               break;
            }
            if (temp.length() != 0) {
               pgm._outputFilePath = temp;
            }
         }
         // input Path
         if (args.length > 1) {
            pgm._inputPath = args[1];
         } else {
            temp = pgm.prompt("Enter the input path or q to exit (\""
               + pgm._inputPath + "\":");
            if ("q".equalsIgnoreCase(temp)) {
               break;
            }
            if (temp.length() != 0) {
               pgm._inputPath = temp;
               while (pgm._inputPath.endsWith(File.separator) == true) {
                  pgm._inputPath = pgm._inputPath.substring(0,
                     pgm._inputPath.length() - 1);
               }
            }
         }
         // ensure the inputPath is fully qualified without relative references
         File testPath = new File(pgm._inputPath);
         if (testPath.exists() == false || testPath.isDirectory() == false) {
            System.out.println(
               "The input directory either doesn't exist, or is not a directory. Goodbye.");
            System.exit(-1);
         }
         try {
            pgm._inputPath = testPath.getCanonicalPath().toString();
         } catch (IOException e1) {
            e1.printStackTrace();
            System.out.println("Goodbye");
            System.exit(-1);
         }
         if (args.length > 2) {
            pgm._overrides = args[2];
         } else {
            temp = pgm.prompt("Enter the overrides filename or q to exit(\""
               + pgm._overrides + "\":");
            if ("q".equalsIgnoreCase(temp)) {
               break;
            }
            if (temp.length() != 0) {
               pgm._overrides = temp;
            }
         }
         List<String> searchTests = new ArrayList<String>();
         try {
            pgm.recurseForIgnored(pgm._inputPath, gatheredContent, gitIgnore,
               searchTests);
         } catch (Exception e) {
            e.printStackTrace();
         }
         break;
      }

      System.out.println("\n\nExporting Files to " + pgm._outputFilePath);
      List<String> exportFiles = new ArrayList<String>(gatheredContent);
      Collections.sort(exportFiles);
      StringBuffer sb = new StringBuffer();
      for (String exportFile : exportFiles) {
         System.out.println(exportFile
            + ((exportFile.endsWith(".gz") | exportFile.endsWith(".tar")
               | exportFile.endsWith(".zip")) ? "  --  filtered" : ""));
         if ((exportFile.endsWith(".gz") | exportFile.endsWith(".tar")
            | exportFile.endsWith(".zip")) == false) {
            sb.append(exportFile + "\n");
         }
      }
      try {
         pgm.saveTextFile(pgm._outputFilePath, sb.toString());
      } catch (Exception e) {
         e.printStackTrace();
      }
      System.out.println("Goodbye");
   }

   private String _inputPath = ".";
   private String _outputFilePath = "." + File.separator + "zipper.files";
   private String _overrides = ".overrides";

   /**
    * Close a buffered reader opened using {@link #openTextFile(String)}
    * 
    * @param br
    */
   public void closeTextFile(BufferedReader br) {
      if (br != null) {
         try {
            br.close();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
   }

   /**
    * Construct and return a sorted list of files in a directory identified by
    * the dir
    * 
    * @param dir
    *           the path to the directory containing files to be returned in the
    *           list
    * @return sorted list of files in a directory identified by the dir
    * @throws IOException
    *            if there is difficulty accessing the files in the supplied dir
    */
   public List<Path> listFiles(Path dir) throws IOException {
      String ext = "";
      return listFiles(dir, ext);
   }

   /**
    * Construct and return a sorted list of files in a directory identified by
    * the dir that have extensions matching the ext
    * 
    * @param dir
    *           the path to the directory containing files to be returned in the
    *           list
    * @param ext
    *           the file extension (without the leading period) used to filter
    *           files in the dir. If ext is an empty string then all files are
    *           returned.
    * @return sorted list of files in a directory identified by the dir that
    *         have extensions matching the ext
    * @throws IOException
    *            if there is difficulty accessing the files in the supplied dir
    */
   public List<Path> listFiles(Path dir, String ext) throws IOException {
      if (ext == null) {
         ext = "";
      }
      List<Path> result = new ArrayList<Path>();
      DirectoryStream<Path> stream = null;
      try {
         if (ext.length() == 0) {
            stream = Files.newDirectoryStream(dir);
         } else {
            stream = Files.newDirectoryStream(dir, "*.{" + ext + "}");
         }
         for (Path entry : stream) {
            result.add(entry);
         }
      } catch (DirectoryIteratorException ex) {
         // I/O error encountered during the iteration, the cause is an
         // IOException
         throw ex.getCause();
      } finally {
         if (stream != null) {
            stream.close();
         }
      }
      result.sort(null);
      return result;
   }

   /**
    * Construct and return a sorted list of directories in a directory
    * identified by the dir variable
    * 
    * @param dir
    *           the path to the directory containing directories to be returned
    *           in the list
    * @return sorted list of subdirectories in a directory identified by the dir
    * @throws IOException
    *            if there is difficulty accessing the content in the supplied
    *            dir
    */
   public List<Path> listSubdirectories(Path dir) throws IOException {
      File testFile = null;
      List<Path> result = new ArrayList<Path>();
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
         for (Path entry : stream) {
            testFile = new File(entry.toString());
            if (testFile.isDirectory()) {
               result.add(entry);
            }
         }
      } catch (DirectoryIteratorException ex) {
         // I/O error encountered during the iteration, the cause is an
         // IOException
         throw ex.getCause();
      } // stream is closed due to try_with_resources
      result.sort(null);
      return result;
   }

   /**
    * Reads the lines of a text file into a list of strings and returns that
    * list. If no lines are present (e.g., empty file) then an empty list is
    * returned.
    * 
    * @param fqFilename
    *           fully qualified filename
    * @return list of strings read from the file
    * @throws Exception
    *            if the file can not be read.
    */
   public List<String> loadTextFile(String fqFilename) throws Exception {
      List<String> result = new ArrayList<String>();
      BufferedReader br = openTextFile(fqFilename);
      String line = br.readLine();
      while (line != null) {
         result.add(line);
         line = br.readLine();
      }
      closeTextFile(br);
      return result;
   }

   /**
    * @param fqFilename
    *           fully qualified name of the text file to be opened
    * @return open buffered reader to allow individual lines of a text file to
    *         be read
    * @throws Exception
    * @see #closeTextFile(BufferedReader) to close the reader returned by this
    *      function
    */
   public BufferedReader openTextFile(String fqFilename) throws Exception {
      BufferedReader input = null;
      File inputFile = new File(fqFilename);
      if (inputFile.exists() == false) {
         throw new FileNotFoundException(
            inputFile.getCanonicalPath() + " does not exist.");
      }
      if (inputFile.isFile() == false) {
         throw new IOException(
            "Input is not a file: " + inputFile.getCanonicalPath()
               + File.separator + inputFile.getName());
      }
      if (inputFile.canRead() == false) {
         throw new IOException(
            "Can not read file " + inputFile.getCanonicalPath() + File.separator
               + inputFile.getName());
      }
      input = new BufferedReader(new FileReader(inputFile));
      return input;
   }

   /**
    * Print the supplied prompt (if not null) and return the trimmed response
    * 
    * @param strPrompt
    * @return the trimmed response to the prompt (may be the empty String ("")
    *         if nothing entered)
    */
   public String prompt(String strPrompt) {
      return prompt(strPrompt, true);
   }

   /**
    * Print the supplied prompt (if not null) and return the trimmed response
    * according to the supplied trim control
    * 
    * @param strPrompt
    * @param bTrim
    * @return the trimmed response (if so commanded) to the prompt (may be the
    *         empty String ("") if nothing entered)
    */
   public String prompt(String strPrompt, boolean bTrim) {
      String strReply = "";
      try {
         BufferedReader in = new BufferedReader(
            new InputStreamReader(System.in));
         if ((strPrompt != null) && (strPrompt.length() != 0)) {
            System.out.println(strPrompt);
         }
         strReply = in.readLine();
         if (bTrim && strReply != null) {
            strReply = strReply.trim();
         }

      } catch (IOException ioe) {
         ioe.printStackTrace();
      }
      return strReply;
   }

   /**
    * Search through the currentPath for .gitignore files to determine what
    * should be added to the output file (taking into account any overrides).
    * 
    * @param currentPath
    *           the starting point for reviewing files and gathering directories
    *           to be searched
    * @param gatheredContent
    *           the files and directories to be placed in the output file
    * @param gitIgnore
    *           the name of the .gitignore file
    * @param searchTests
    *           names of files or directories from parent .gitignores that
    *           should be tested for content in the currentPath
    * @return whether there was content to review (e.g., false if an error
    *         occurred (e.g., no .gitignore file) or the currentPath was empty)
    * @throws Exception
    *            if accessing files has a problem
    */
   private boolean recurseForIgnored(String currentPath,
      Set<String> gatheredContent, String gitIgnore, List<String> searchTests)
      throws Exception {
      boolean isEmpty = true;
      // check for .gitignore first to add its content
      Set<String> subDirectories = new HashSet<String>();
      List<String> newSearchTests = new ArrayList<String>(searchTests);
      String dirName = "";
      String fileName = "";
      File testFile = null;
      String overrideFileName = "";
      Set<String> overrides = new HashSet<String>();
      try {
         fileName = currentPath + File.separator + gitIgnore;
         overrideFileName = currentPath + File.separator + _overrides;
         List<String> ignored = loadTextFile(fileName);
         // prepare overrides
         overrides.clear();
         try {
            List<String> overridden = loadTextFile(overrideFileName);
            for (String ovLine : overridden) {
               ovLine = ovLine.trim();
               if (ovLine.length() == 0 || ovLine.startsWith("#")) {
                  continue;
               }
               if (ovLine.startsWith("/") == false) {
                  ovLine = File.separator + ovLine;
               }
               ovLine = currentPath + ovLine;
               overrides.add(ovLine);
            }
         } catch (FileNotFoundException e) {
            ; // okay if not found
         }
         for (String line : ignored) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) {
               continue;
            }
            if (line.startsWith("/") == false) {
               line = File.separator + line;
            }
            testFile = new File(currentPath + line);
            if (testFile.isDirectory()) {
               dirName = currentPath + line;
               // skip if in the overrides
               if (overrides.contains(dirName)) {
                  continue;
               }
               subDirectories.add(dirName);
               if (debug) {
                  System.out.println("Added subdirectory " + dirName);
               }
               if (line.endsWith("/")) {
                  // add line to check subdirectories to add it
                  newSearchTests.add(line);
                  if (debug) {
                     System.out.println("newSearchTests added " + line);
                  }
               }
               continue;
            }
            // TODO:
            // **/foo (find any match to foo at any depth, file or directory)
            // a/**/b (find any combinations of intervening directories below a
            // and above b)
            // foo/** (all files inside foo)
            // !xyz (include)
            if (testFile.isFile() == false) {
               if (debug) {
                  System.out.println("Skipping non-file " + testFile);
               }
               continue;
            }
            // this is a file we want to gather
            fileName = currentPath + line;

            // skip if in the overrides
            if (overrides.contains(fileName)) {
               continue;
            }

            gatheredContent.add(fileName);
            if (debug) {
               System.out.println("gatheredContent added " + fileName);
            }
         }
      } catch (FileNotFoundException e) {
         if (debug) {
            System.out.println("Skipping non-existant file: " + fileName);
         }
      } // else throw exception
      List<Path> subdirs = listSubdirectories(
         FileSystems.getDefault().getPath(currentPath));
      if (subdirs.size() > 0) {
         isEmpty = false;
      }
      for (Path subdir : subdirs) {
         dirName = subdir.toString();

         // skip if in overrides
         if (overrides.contains(dirName + "/")) {
            continue;
         }

         subDirectories.add(dirName);
         if (debug) {
            System.out.println("subDirectories added " + dirName);
         }
      }
      List<Path> files = null;
      // process files against the searchTests
      files = listFiles(FileSystems.getDefault().getPath(currentPath));
      Collections.sort(files);
      if (files.size() > 0) {
         isEmpty = false;
      }
      for (Path file : files) {
         testFile = new File(file.toString());
         if (testFile.isDirectory()) {

            // skip overridden directory
            if (overrides.contains(testFile.toString() + "/")) {
               continue;
            }

            subDirectories.add(testFile.toString());
            if (debug) {
               System.out
                  .println("subDirectories added " + testFile.toString());
            }
         }
         // check if a file matches anything in the searchTests
         for (String searchFile : searchTests) {
            if (file.toString().contains(searchFile)) {

               // skip overriden files
               if (overrides.contains(file.toString())) {
                  continue;
               }

               gatheredContent.add(file.toString());
               if (debug) {
                  System.out
                     .println("gatheredContent added " + file.toString());
               }
               break;
            }
         }
      }

      // now recurse to process any subdirectories
      for (String subDir : subDirectories) {
         boolean isEmptySubdir = recurseForIgnored(subDir, gatheredContent,
            gitIgnore, newSearchTests);
         if (isEmptySubdir) {
            gatheredContent.add(subDir);
            if (debug) {
               System.out
                  .println("gatheredContent added empty directory " + subDir);
            }
         }
      }
      return isEmpty;
   }

   /**
    * Save the specified JSONObject in serialized form to the specified file or
    * throw the appropriate exception.
    * 
    * @param textFileName
    *           fully qualified name of the JSON file to be saved
    * @param content
    *           the content to be saved to a file.
    * @throws Exception
    *            {@link IOException}) if there is a problem writing the file
    */
   public void saveTextFile(String textFileName, String content)
      throws Exception {
      if (content == null) {
         throw new InvalidObjectException("content is null");
      }
      if (textFileName == null || textFileName.trim().length() == 0) {
         throw new InvalidTargetObjectTypeException(
            "Output filename is null or empty.");
      }
      BufferedWriter br = null;
      try {
         File outputFile = new File(textFileName);
         br = new BufferedWriter(new FileWriter(outputFile));
         br.write(content);
      } catch (IOException e) {
         throw new IOException("Can not write file \"" + textFileName + "\"",
            e);
      } finally {
         try {
            br.close();
         } catch (IOException e) {
            // error trying to close writer ...
         }
      }

      return;
   }
}
