'use strict';
import * as net from 'net';
import { workspace, ExtensionContext, window } from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo } from 'vscode-languageclient';

var client: LanguageClient = null;

async function configureAndStartClient(context: ExtensionContext) {

	// Startup options for the language server
	const settings = workspace.getConfiguration("JimpleLSP");
	const lspTransport: string = settings.get("lspTransport");
	let executable = 'java';
	let relativePath = "jimplelsp-0.0.1.jar";
	let args = ['-jar', context.asAbsolutePath(relativePath)];

	const serverOptionsStdio = {
		run: { command: executable, args: args },
		debug: { command: executable, args: args }
	}

	const serverOptionsSocket = () => {
		const socket = net.connect({ port: 2403 })
		const result: StreamInfo = {
			writer: socket,
			reader: socket
		}

		return new Promise<StreamInfo>((resolve) => {
			socket.on("connect", () => resolve(result))
			socket.on("error", _ => {

				window.showErrorMessage(
					"Failed to connect to Jimple language server. Make sure that the language server is running " +
					"-or- configure the extension to connect via standard IO.")
				client = null;
			});
		})
	}

	const serverOptions: ServerOptions =
		(lspTransport === "stdio") ? serverOptionsStdio : (lspTransport === "socket") ? serverOptionsSocket : null

	let clientOptions: LanguageClientOptions = {
		documentSelector: [{ scheme: 'file', language: 'jimple' }],
		synchronize: {
			configurationSection: 'JimpleLSP',
			fileEvents: [
                workspace.createFileSystemWatcher('**/*.jimple'),
                workspace.createFileSystemWatcher('**/*.apk'),
                workspace.createFileSystemWatcher('**/*.jar')
			],
		}
	};

	// Create the language client and start the client.
	    client = new LanguageClient('JimpleLSP', 'JimpleLSP', serverOptions, clientOptions);
        let disposable = client.start();
        context.subscriptions.push(disposable);
        await client.onReady();
}

export async function activate(context: ExtensionContext) {
	configureAndStartClient(context);
	workspace.onDidChangeConfiguration(e => {
		if (client)
			client.stop().then(() => configureAndStartClient(context));
		else
			configureAndStartClient(context)
	})
}




