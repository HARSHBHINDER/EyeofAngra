; Inno Setup script for AngraiPhoneTransfer.
;
; Builds a single self-contained Windows installer around the published output
; in ..\publish. Per-user by default, so installing never needs an admin
; prompt; pass /ALLUSERS to install machine-wide.
;
;   iscc AngraiPhoneTransfer.iss

#define AppName "AngraiPhoneTransfer"
#define AppVersion "1.0.0"
#define AppPublisher "Angra"
#define AppExe "AngraiPhoneTransfer.exe"

[Setup]
AppId={{7F2C9A64-7C1E-4C2E-9D2B-2A1E5C7D3B10}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
DisableDirPage=no
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog commandline
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\dist
OutputBaseFilename={#AppName}-Setup-{#AppVersion}-x64
SetupIconFile=..\src\AngraiPhoneTransfer\Assets\AngraiPhoneTransfer.ico
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppName}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
WizardSizePercent=110
AppPublisherURL=https://github.com/HARSHBHINDER/EyeofAngra
AppSupportURL=https://github.com/HARSHBHINDER/EyeofAngra/issues
VersionInfoVersion={#AppVersion}
VersionInfoDescription={#AppName} — wired iPhone transfer for Windows
MinVersion=10.0.17763

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"

[Files]
Source: "..\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "Start {#AppName}"; Flags: nowait postinstall skipifsilent
