# JimpleLSP
This is an implementation of the Language Server Protocol for Jimple.
A LSP Server implementation allows an IDE, which supports the LSP, to provide well known features of programming languages for a specific Language.

# Installation
## Get the server Jar.
**Either:** download the JimpleLSP Server Release and extract it to any Location.
  
**Or:** Compile from source.
You need a fresh build of FutureSoot in your local Maven Repository.
1. If you have access to FutureSoot run `git clone https://github.com/secure-software-engineering/soot-reloaded.git && cd soot-reloaded && mvn install`
(Latest working commit is #6bb957a82d28062f74586d4333da90172db48f18). 
Otherwise download the FutureSoot Snapshot and install it to your local repository via
   `mvn install:install-file -Dfile=<path-to-file> -DgroupId=de.upb.swt -DartifactId=soot -Dversion=4.0.0-SNAPSHOT -Dpackaging=jar`

2. run `git clone https://github.com/swissiety/JimpleLSP` to clone this Repository.
3. run `mvn package` to build a Jar. The generated Jar can be found in target/jimplelsp-0.0.1-SNAPSHOT-jar-with-dependencies.jar.


## IntelliJ Idea
1. Install or use an LSP Plugin in your IDE to enable Language Server Protocol Support.
You can use IntelliJLSP from https://github.com/MagpieBridge/IntelliJLSP/tree/intellijclientlib
2. Configure the LSP Plugin in your IDE to use the JimpleLSP Server Jar.

## VSCode

Assumes: npm is installed

1. `cd vscode/ && npm install` to install the dependencies.
2. `npm install -g vsce` to install the Visual Studio Code Extension Commandline tool.
3. `vsce package` to package the Plugin.
4. `code --install-extension JimpleLSP-0.0.1.vsix` to install the packaged Plugin.
5. restart VSCode

# Usage

You can import already extracted Jimple files into your workspace.

JimpleLSP can extract Jimple from a .jar or an .apk file do simplify your workflow. To enable JimpleLSP to extract
Jimple your .apk or .jar file needs to be in your workspace on startup of your IDE, but the workspace must not contain a
.jimple file. Additionally you need to configure the following config key in your IDE: **
JimpleLSP.jimpleextraction.androidplatforms** to tell Soot where it can find the Android runtimes usually located in
ANDROID_HOME/platforms.

A Collection of android.jars is available in the [android-platforms](https://github.com/Sable/android-platforms/) Repo.

## Server Capabilities

This Server is oriented towards LSP 3.15. The currently implemented features are focussed on improving code exploring in
Jimple. For checked capabilities there are previews of the functionality in the linked Issue.

### Text Document Capabilities

- ✅ didOpen
- ❌ [planned #17] didChange
  - ❌ Full text sync
  - ❌ Incremental text sync
- ✅ didSave
    - ✅ Include text
- ❌ completion
- ✅ [hover](/../../issues/15)
- ❌ signatureHelp
- ✅ [declaration](/../../issues/4)
    - ❌ [planned #18] link support
- ✅ [definition](/../../issues/6)
    - ❌ [planned #18] link support
- ✅ [typeDefinition](/../../issues/5)
    - ❌ [planned #18] link support
- ✅ [implementation](/../../issues/2)
    - ❌ [planned #18] link support
- ✅ [references](/../../issues/3)
- ❌ [planned #11] documentHighlight
- ✅ [documentSymbol](/../../issues/12)
- ❌ codeAction
    - ❌ resolve
- ❌ codeLens
- ❌ documentLink
- ❌ formatting
- ❌ rangeFormatting
- ❌ onTypeFormatting
- ❌ rename
- ✅ publishDiagnostics
- ❌ [WIP #16] foldingRange
- ❌ selectionRange
- ❌ [planned #1] semanticToken
- ❌ callHierarchy

### Workspace Capabilities
- ❌ [planned #9] applyEdit
- ❌ didChangeConfiguration
- ❌ [planned #17] didChangeWatchedFiles
- ✅ [symbol](/../../issues/13)
- ❌ executeCommand

### Window Capabilities

- ❌ workDoneProgress
    - ❌ create
    - ❌ cancel
- ✅ logMessage
- ✅ showMessage
- ✅ showMessage request


## About
This piece of software was created as part of a bachelor thesis at UPB (University of Paderborn, Germany).