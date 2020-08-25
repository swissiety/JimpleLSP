# JimpleLSP
This is an implementation of the Language Server Protocol for Jimple.
It allows you to use well known features of your IDE to be used while exploring and editing .jimple files.

# Usage
## IntelliJ Idea
1. Install or use an LSP Plugin in your IDE to enable Language Server Protocoll Support for your IDE.
e.g. for IntelliJ you can use https://github.com/MagpieBridge/IntelliJLSP
2.
    a)  **Either** Download the JimpleLSP Server Release and extract it to any Location.
  
    b) **OR** Compile it yourself 
    `git clone https://github.com/swissiety/JimpleLSP` this Repository. Build the Jar via `mvn package` generated Jar can be found in ./target/jimplelsp-0.1-SNAPSHOT-jar-with-dependencies.jar 
    
4. Configure the LSP Plugin in your IDE to use the JimpleLSP Server Jar that was extracted or build in the previous step.
5. Maybe restart its necessary to restart your IDE to activate.
6. Enjoy!

This piece of Software was created as part of my Bachelor Thesis at UPB (University of Paderborn, Germany).