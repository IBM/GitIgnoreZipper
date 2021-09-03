#! /bin/bash
echo "Utility to create file of names being igored by .gitignore"
echo ""
echo "Enter:"
echo "  1) the output file name (e.g., zipper.files)"
echo "  2) the input directory (e.g., src)"
echo "  3) the overrides file name (e.g., .overrides)"
echo "  4) the zipfile name."
echo "Press Enter or Ctrl+C to abort..."
read
java -cp "./target/GitIgnoreZipper-1.0.0-jar-with-dependencies.jar" com.wnm3.gitignorezipper.GitIgnoreZipper $1 $2
if [ -z $4 ]; then
    echo "Use a command like:";
    echo "zip WAAOrchFiles -@ <zipper.files";
    echo "To copy the files into a zip archive.";
else
    zip $4 -@ < $1
fi
