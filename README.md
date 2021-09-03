# GitIgnoreZipper
Certain files are not desired to be stored in github repositories (e.g., customer data, SSL certificates, scripts with userids and passwords). These are listed in .gitignore files to keep them from being replicated. This repository provides a command line utility Eclipse Maven project for finding files that are not synchronized with a github repository (based on .gitignore file contents) and creates a file able to be read by the zip utility to capture these files in an archive. This allows a developer to share the zip of ignored files with a colleague so they can clone the repo then add files not stored in github, thus recreating the dev environment.

## Building Projects
This project can be  built by using the  command line: **mvn clean install** command in the project directory to write jar files to the target subdirectory. Alternativiely, right clicking the pom.xml file in Eclipse, selecting Run As... Maven build... and specifying  **clean  install** as the goals will build the project in Eclipse. 

### Known Limitations
Wildcards in .gitignore files are not processed, only listings of specific directories or files will be processed by this initial version. Subdirectories are recurssed and non-wildcard patterns are honored in subdirectories.

### JDK Version
Content has been build using the Open  JDK version 1.8.0_252_b09 available  for download from https://adoptopenjdk.net/

### Eclipse Version
Projects  were developed in Eclipse 2021-06 available from  https://www.eclipse.org/downloads/ installing the Java EE Profile during installation.

## License
The  code  in this repository is licensed under the  Apache 2.0 License

## Support
It is best to open an issue in this repository. You may also contact Nathaniel Mills at wnm3@us.ibm.com or wnmills3@gmail.com
