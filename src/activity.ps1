$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class CutizerActivity {
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hwnd, StringBuilder text, int count);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint processId);
  [StructLayout(LayoutKind.Sequential)] public struct LASTINPUTINFO { public uint cbSize; public uint dwTime; }
  [DllImport("user32.dll")] public static extern bool GetLastInputInfo(ref LASTINPUTINFO info);
  public static double IdleSeconds() {
    var info = new LASTINPUTINFO(); info.cbSize = (uint)Marshal.SizeOf(info);
    if (!GetLastInputInfo(ref info)) return 0;
    return unchecked((uint)Environment.TickCount - info.dwTime) / 1000.0;
  }
}
'@
while ($true) {
  try {
    $handle = [CutizerActivity]::GetForegroundWindow()
    [uint32]$foregroundProcessId = 0
    [void][CutizerActivity]::GetWindowThreadProcessId($handle, [ref]$foregroundProcessId)
    $processName = [System.Diagnostics.Process]::GetProcessById($foregroundProcessId).ProcessName
    $title = [System.Text.StringBuilder]::new(1024)
    [void][CutizerActivity]::GetWindowText($handle, $title, $title.Capacity)
    @{ process = $processName; title = $title.ToString(); idleSeconds = [CutizerActivity]::IdleSeconds() } | ConvertTo-Json -Compress
  } catch { '{"process":"","title":"","idleSeconds":0}' }
  Start-Sleep -Milliseconds 1000
}
