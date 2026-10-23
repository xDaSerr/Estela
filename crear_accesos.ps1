# Crea los accesos directos de Estela (escritorio y menu Inicio) con su icono y su
# AppUserModelID, para que la barra de tareas muestre el icono de Estela y no el de Python.
$app = $PSScriptRoot
$aumid = "Estela.Downloader"

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class AumidShortcut {
    [StructLayout(LayoutKind.Sequential, Pack = 4)]
    struct PropertyKey { public Guid fmtid; public uint pid; }

    [StructLayout(LayoutKind.Sequential)]
    struct PropVariant { public ushort vt; ushort r1, r2, r3; public IntPtr p; public IntPtr p2; }

    [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IPropertyStore {
        void GetCount(out uint count);
        void GetAt(uint index, out PropertyKey key);
        void GetValue(ref PropertyKey key, out PropVariant value);
        void SetValue(ref PropertyKey key, ref PropVariant value);
        void Commit();
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
    static extern void SHGetPropertyStoreFromParsingName(string path, IntPtr bc, int flags, ref Guid iid, out IPropertyStore store);

    public static void SetAppId(string lnkPath, string appId) {
        Guid iid = typeof(IPropertyStore).GUID;
        IPropertyStore store;
        SHGetPropertyStoreFromParsingName(lnkPath, IntPtr.Zero, 2 /* GPS_READWRITE */, ref iid, out store);
        // PKEY_AppUserModel_ID
        PropertyKey key = new PropertyKey { fmtid = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3"), pid = 5 };
        PropVariant value = new PropVariant { vt = 31 /* VT_LPWSTR */, p = Marshal.StringToCoTaskMemUni(appId) };
        try {
            store.SetValue(ref key, ref value);
            store.Commit();
        } finally {
            Marshal.FreeCoTaskMem(value.p);
            Marshal.ReleaseComObject(store);
        }
    }
}
"@

$targets = @(
    [Environment]::GetFolderPath('Desktop'),
    (Join-Path ([Environment]::GetFolderPath('Programs')) "")
)
$shell = New-Object -ComObject WScript.Shell
foreach ($dir in $targets) {
    $lnk = Join-Path $dir "Estela.lnk"
    $sc = $shell.CreateShortcut($lnk)
    $sc.TargetPath = Join-Path $app "Estela.exe"
    $sc.WorkingDirectory = $app
    $sc.IconLocation = "$(Join-Path $app 'icon.ico'),0"
    $sc.Description = "Estela - descargas educativas"
    $sc.Save()
    [AumidShortcut]::SetAppId($lnk, $aumid)
    Write-Output "Creado: $lnk"
}
