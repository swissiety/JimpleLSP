# JimpleLSP
This is an implementation of the Language Server Protocol for Jimple.
It allows you to use well known features of your IDE to be used while exploring and editing .jimple files.

# Usage
## IntelliJ Idea
1. Install or use an LSP Plugin in your IDE to enable Language Server Protocoll Support for your IDE.
e.g. for IntelliJ you can use https://github.com/MagpieBridge/IntelliJLSP/tree/intellijclientlib
2.
    a)  **Either** Download the JimpleLSP Server Release and extract it to any Location.
  
    b) **OR** Compile it yourself 
   You need a fresh build of FuturSoot in your local Maven Repository.
   To get it run `git clone https://github.com/secure-software-engineering/soot-reloaded.git && cd soot-reloaded && mvn install`
   (Latest working commit is #6bb957a82d28062f74586d4333da90172db48f18).
   
    Then run
    `git clone https://github.com/swissiety/JimpleLSP` to clone this Repository.
    Build the Jar via `mvn package`. The generated Jar can be found in ./target/jimplelsp-0.0.1-SNAPSHOT-jar-with-dependencies.jar.
    
4. Configure the LSP Plugin in your IDE to use the JimpleLSP Server Jar that was extracted or build in the previous step.
5. Enjoy!


## VSCode
1. Install the provided Plugin from vscode/ folder.
2. 



This piece of Software was created as part of my Bachelor Thesis at UPB (University of Paderborn, Germany).