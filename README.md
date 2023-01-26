# JimpleLSP
This is an implementation of the Language Server Protocol for Jimple.
A LSP Server implementation allows an IDE, which supports the LSP, to provide well known features of programming languages for a specific Language.

# Installation
## Get the server Jar.
**Either:** download the JimpleLSP Server Release and extract it to any Location.
  
**Or:** Compile from source.
1. run `git clone https://github.com/swissiety/JimpleLSP` to clone this Repository.
2. run `mvn package` to build a Jar. The generated Jar can be found in target/jimplelsp-0.0.1-SNAPSHOT-jar-with-dependencies.jar.


## IntelliJ Idea
1. Install or use an LSP Plugin in your IDE to enable Language Server Protocol Support.
You can use [IntelliJLSP](https://github.com/MagpieBridge/IntelliJLSP/tree/intellijclientlib)
2. Configure the LSP Plugin in your IDE to use the JimpleLSP Server Jar.

## VSCode
Install the [JimpleLSP Extension](https://marketplace.visualstudio.com/items?itemName=swissiety.jimplelsp) from the Marketplace.

Or if you want to compile it yourself:
1. `cd vscode/ && npm install` to install the dependencies.
2. `npm install -g vsce` to install the Visual Studio Code Extension commandline tool.
3. `vsce package` to package the plugin.
4. `code --install-extension JimpleLSP-0.0.5.vsix` to install the packaged plugin.
5. restart VSCode

# Usage

You can import already extracted Jimple files into your workspace.

JimpleLSP can extract Jimple from a .jar or an .apk file do simplify your workflow. To enable JimpleLSP to extract
Jimple your .apk or .jar file needs to be in your workspace on startup of your IDE, but the workspace must not contain a
.jimple file. Additionally you need to configure the following config key in your IDE: **
JimpleLSP.jimpleextraction.androidplatforms** to tell Soot where it can find the Android runtimes usually located in
ANDROID_HOME/platforms.

A Collection of android.jars is available in the [android-platforms](https://github.com/Sable/android-platforms/) Repo.

# Development

The Language Server starts by default in STDIO mode. Use `-s` to start the Language Server executable in socket mode on
Port 2403, to change the port use `-p <portnumber>`.

## Server Capabilities

This Server implementation was initially oriented towards LSP 3.15. The currently implemented features are focussed on improving code exploring in
Jimple. For checked capabilities there are previews of the functionality in the linked Issue.

### Text Document Capabilities

- ✅ didOpen
- ✅ didChange
  - ✅ Full text sync
  - ❌ Incremental text sync
- ✅ didSave
  - ✅ Include text
- ❌ completion
- ✅ [hover](/../../issues/15)
- ❌ signatureHelp
- ✅ [declaration](/../../issues/4)
    - ❌ [planned #18] link support
- ✅ [definition](/../../issues/6)
    - ✅ link support
- ✅ [typeDefinition](/../../issues/5)
    - ❌ [planned #18] link support
- ✅ [implementation](/../../issues/2)
    - ❌ [planned #18] link support
- ✅ [references](/../../issues/3)
- ✅ [documentHighlight](/../../issues/11)
  - ✅ for Locals
  - ❌ types
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
- ✅ [semanticTokens](/../../issues/1)
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
This piece of software was created as part of a bachelor thesis at University of Paderborn (UPB), Germany.