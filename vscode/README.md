# JimpleLSP

This is a Plugin to use [JimpleLSP](https://github.com/swissiety/JimpleLSP) - a Language Server Protocol implementation
for Jimple - with Visual Studio Code.

### Usage

To enable extraction of Jimple from APKs or Jars in the Workspace adapt the configuration accordingly.

**Hint:** The extraction is only triggered if no jimple files are in the workspace!

### Production mode

Set Lsp Transport to stdio (default).

### Development mode

1. Set Lsp Transport to socket.
2. Execute the JimpleLSP Jar with argument -s to communicate via socket 2403.