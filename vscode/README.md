# JimpleLSP

This is a Plugin to use [JimpleLSP](https://github.com/swissiety/JimpleLSP) - a Language Server Protocol implementation
for Jimple - with Visual Studio Code.

### Usage
Open existing **.jimple** files in your workspace and get spoiled by syntax highlighting as well as support for code exploring.

Or to enable extraction of Jimple from **.apk**s or **.jar**s in the workspace adapt the configuration accordingly.
For more information on this please visit [JimpleLSP](https://github.com/swissiety/JimpleLSP).

**Hint:** The extraction is only triggered if no jimple files are in the workspace on language server startup!


### Production mode

Set Lsp Transport to stdio (default).

### Development mode

1. Set Lsp Transport to socket.
2. Execute the JimpleLSP Jar with argument -s to communicate via socket 2403.