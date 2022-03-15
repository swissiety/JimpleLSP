'use strict';
import * as net from 'net';
import { XMLHttpRequest } from 'xmlhttprequest-ts';
import { commands, workspace, ExtensionContext, window, ViewColumn, env, Uri} from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions, StreamInfo, DynamicFeature, ClientCapabilities, DocumentSelector, InitializeParams, RegistrationData, ServerCapabilities, VersionedTextDocumentIdentifier, RegistrationType } from 'vscode-languageclient/node';

var client: LanguageClient = null;

async function configureAndStartClient(context: ExtensionContext) {

	// Startup options for the language server
	const settings = workspace.getConfiguration("JimpleLSP");
	const lspTransport: string = settings.get("lspTransport");
	let executable = 'java';
	let relativePath = "jimplelsp-"+ context.extension.packageJSON.version +".jar";
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
					"Failed to connect to the Jimple language server. Make sure that the language server is running " +
					"-or- configure the extension to connect via standard IO.", "Open settings", "Reconnect")
					.then( function( str ){
						if( str.startsWith("Open") ){
							commands.executeCommand('workbench.action.openSettings', '@ext:' + context.extension.id);
						 }else if(str.startsWith("Reconnect")){
							configureAndStartClient(context);
						 }
					});
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
    	client.registerFeature(new SupportsShowHTML(client));
        let disposable = client.start();
        context.subscriptions.push(disposable);
        await client.onReady();
}

export class SupportsShowHTML implements DynamicFeature<undefined> {

	registrationType: RegistrationType<undefined>;

	constructor(private _client: LanguageClient) {

    }
	
	fillInitializeParams?: (params: InitializeParams) => void;
	fillClientCapabilities(capabilities: ClientCapabilities): void {
		capabilities.experimental = {
			supportsShowHTML: true,
		}
	}

	initialize(capabilities: ServerCapabilities<any>, documentSelector: DocumentSelector): void {
		let client = this._client;
        client.onNotification("magpiebridge/showHTML",(content: string)=>{
			 const panel = window.createWebviewPanel("Configuration", "MagpieBridge Control Panel",ViewColumn.One,{
				 enableScripts: true
			 });
			 panel.webview.html = content;
			 panel.webview.onDidReceiveMessage(
				message => {
					switch(message.command){
						case 'action':
							 var httpRequest = new XMLHttpRequest();
							 var url = message.text;
							 httpRequest.open('GET',url);
							 httpRequest.send();
							 return ;
						case 'configuration':
							 var httpRequest = new XMLHttpRequest();
							 var splits = message.text.split("?");
							 var url = splits[0];
							 var formData = splits[1];
							 httpRequest.open('POST',url);
							 httpRequest.send(formData);
							 return ;

					}
				}
			 );
			})
	}

	register( data: RegistrationData<undefined>): void {

	}
	unregister(id: string): void {

	}
	dispose(): void {

	}

}


function showWelcomeMessage(context: ExtensionContext) {
	let previousVersion = context.globalState.get<string>('jimplelsp-version');
	let currentVersion = context.extension.packageJSON.version;
	let message : string | null = null;
	let previousVersionArray = previousVersion ? previousVersion.split('.').map((s: string) => Number(s)) : [0, 0, 0];
	let currentVersionArray = currentVersion.split('.').map((s: string) => Number(s));
	if (previousVersion === undefined || previousVersion.length === 0) {
		message = "Thanks for using JimpleLSP!\n";
	} else if (currentVersion !== previousVersion && (
		(previousVersionArray[0] === currentVersionArray[0] && previousVersionArray[1] === currentVersionArray[1] && previousVersionArray[2] < currentVersionArray[2]) ||
		(previousVersionArray[0] === currentVersionArray[0] && previousVersionArray[1] < currentVersionArray[1]) ||
		(previousVersionArray[0] < currentVersionArray[0])
	)
	) {
		message = "JimpleLSP Plugin updated to " + currentVersion + ".\n";
	}
	if (message) {
		window.showInformationMessage(message, 'Settings', '⭐️ Star on Github', '🐞 Report Bug')
			.then(function (val: string | undefined) {
				/*if (val === '⭐️ Rate') {
					env.openExternal(vscode.Uri.parse('https://marketplace.visualstudio.com/items?itemName=swissiety.jimplelsp&ssr=false#review-details'));
				} else */
				if( val === 'Settings'){
    				commands.executeCommand('workbench.action.openSettings', '@ext:' + context.extension.id);
				}else if (val === '🐞 Report Bug') {
					env.openExternal(Uri.parse('https://github.com/swissiety/JimpleLSP/issues'));
				} else if (val === '⭐️ Star on Github') {
					env.openExternal(Uri.parse('https://github.com/swissiety/JimpleLSP'));
				}
			});
		context.globalState.update('jimplelsp-version', currentVersion);
	}
}

export async function activate(context: ExtensionContext) {
    showWelcomeMessage(context);
	configureAndStartClient(context);
	workspace.onDidChangeConfiguration(e => {
		if (client)
			client.stop().then(() => configureAndStartClient(context));
		else
			configureAndStartClient(context)
	})
}




