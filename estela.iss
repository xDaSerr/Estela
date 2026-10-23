; Instalador de Estela (Inno Setup). Se compila con crear_instalador.bat
#define MyAppName "Estela"
#define MyAppVersion "1.0.2"
#define MyAppExe "Estela.exe"

[Setup]
; No cambiar el AppId: Windows lo usa para reconocer actualizaciones de la app
AppId={{0F6CFE1C-DBF1-4103-A85D-F8D645826A7C}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
DefaultDirName={autopf}\{#MyAppName}
DisableProgramGroupPage=yes
; Instalación por usuario: no pide permisos de administrador
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=Instalador
OutputBaseFilename=Estela-Setup-{#MyAppVersion}
SetupIconFile=icon.ico
UninstallDisplayIcon={app}\{#MyAppExe}
WizardStyle=modern
Compression=lzma2
SolidCompression=yes
CloseApplications=yes

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "build\dist\Estela\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "ui\*"; DestDir: "{app}\ui"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "icon.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "bin\ffmpeg.exe"; DestDir: "{app}\bin"; Flags: ignoreversion

[Icons]
; El AppUserModelID debe coincidir con el de app.py para que la barra de tareas muestre el icono
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExe}"; AppUserModelID: "Estela.Downloader"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExe}"; AppUserModelID: "Estela.Downloader"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExe}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent
