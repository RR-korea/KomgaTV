# Drive the Tizen TV emulator without touching the user's keyboard, mouse or focus.
# (ASCII only on purpose: Windows PowerShell 5.1 reads a BOM-less file as ANSI and Korean text breaks the C# below.)
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File dev\emu.ps1 launch
#       Starts the VM. While it boots, every emulator window is moved off-screen (x = -9000) without activation;
#       if the window grabs the foreground it is minimized (focus goes back) and shown again without activation.
#       The emulator does grab focus for moments during boot - 2026-11-27: 87 times in 90 s - so warn the user first.
#   ... -File dev\emu.ps1 park
#       Moves an already running emulator window off-screen (no activation).
#   ... -File dev\emu.ps1 run "28 w3500 s:reader 106 w800 s:next" [-OutDir <folder>]
#       Sends remote keys with ecp-cli (network, no window focus) and captures the window with PrintWindow.
#       <number>   key code, evdev: 103 up, 108 down, 105 left, 106 right, 28 OK, 1 Back
#       w<ms>      wait
#       h<key>:<ms> hold a key (auto-repeat), e.g. h106:7000
#       s:<name>   capture to <OutDir>\<name>.png (default OutDir = %TEMP%\komgatv-emu)
#
# Install and start the app first:
#   tizen install -n <wgt> -s emulator-26101 -- <dist folder>;  tizen run -p KomgaTVtz0.KomgaTV -s emulator-26101
# NoDefaultCurrentDirectoryInExePath must be unset for the Tizen Studio tools (done below for this process).
param(
  [Parameter(Position = 0)][string]$Action = 'park',
  [Parameter(Position = 1)][string]$Steps = '',
  [string]$OutDir = (Join-Path $env:TEMP 'komgatv-emu'),
  [string]$Vm = 'T-samsung-10.0-x86_64',
  [string]$Serial = 'emulator-26101',
  [string]$TizenStudio = $(if ($env:TIZEN_STUDIO) { $env:TIZEN_STUDIO } else { 'D:\tizen-studio' })
)

Add-Type -ReferencedAssemblies System.Drawing @'
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Drawing;
using System.Drawing.Imaging;
public static class EmuWin {
  public delegate bool EnumProc(IntPtr hwnd, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr lParam);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hwnd);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hwnd, int cmd);
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr hwnd);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hwnd, IntPtr after, int x, int y, int cx, int cy, uint flags);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd, out RECT rect);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hwnd, IntPtr hdc, uint flags);
  [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr hwnd, StringBuilder text, int max);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }

  public static List<IntPtr> WindowsOf(HashSet<uint> pids) {
    var found = new List<IntPtr>();
    EnumWindows((hwnd, l) => {
      uint pid; GetWindowThreadProcessId(hwnd, out pid);
      if (pids.Contains(pid) && IsWindowVisible(hwnd)) found.Add(hwnd);
      return true;
    }, IntPtr.Zero);
    return found;
  }
  public static IntPtr Biggest(HashSet<uint> pids) {
    IntPtr best = IntPtr.Zero; long area = 0;
    foreach (IntPtr hwnd in WindowsOf(pids)) {
      RECT r; GetWindowRect(hwnd, out r);
      long a = (long)(r.Right - r.Left) * (r.Bottom - r.Top);
      if (a > area) { area = a; best = hwnd; }
    }
    return best;
  }
  public static string Title(IntPtr hwnd) { var sb = new StringBuilder(256); GetWindowText(hwnd, sb, 256); return sb.ToString(); }

  // Off-screen, behind everything, never activated. If it already took the foreground: minimize (focus returns to the
  // previous window), then show again without activation.
  public static bool Park(IntPtr hwnd) {
    bool stole = GetForegroundWindow() == hwnd;
    if (stole) ShowWindow(hwnd, 6 /*SW_MINIMIZE*/);
    if (IsIconic(hwnd)) ShowWindow(hwnd, 4 /*SW_SHOWNOACTIVATE*/);
    RECT r; GetWindowRect(hwnd, out r);
    if (r.Left > -3000) SetWindowPos(hwnd, new IntPtr(1) /*HWND_BOTTOM*/, -9000, 75, 0, 0, 0x0001 /*NOSIZE*/ | 0x0010 /*NOACTIVATE*/);
    return stole;
  }
  public static string Shot(IntPtr hwnd, string path) {
    RECT r; GetWindowRect(hwnd, out r);
    int w = r.Right - r.Left, h = r.Bottom - r.Top;
    using (var bmp = new Bitmap(w, h, PixelFormat.Format32bppArgb)) {
      // a capture taken while the window repaints comes out all white: retry while the bezel is white
      for (int attempt = 0; attempt < 8; attempt++) {
        using (var g = Graphics.FromImage(bmp)) {
          IntPtr hdc = g.GetHdc();
          PrintWindow(hwnd, hdc, 2 /*PW_RENDERFULLCONTENT*/);
          g.ReleaseHdc(hdc);
        }
        Color edge = bmp.GetPixel(2, h / 2);
        if (edge.R < 240 || edge.G < 240 || edge.B < 240) break;
        System.Threading.Thread.Sleep(200);
      }
      bmp.Save(path, ImageFormat.Png);
    }
    return w + "x" + h + " at " + r.Left + "," + r.Top + (GetForegroundWindow() == hwnd ? " FOREGROUND!" : "");
  }
}
'@
[void][EmuWin]::SetProcessDPIAware()
Remove-Item Env:NoDefaultCurrentDirectoryInExePath -ErrorAction SilentlyContinue
$bin = Join-Path $TizenStudio 'tools\emulator\bin'

function Get-EmuPids {
  $set = New-Object 'System.Collections.Generic.HashSet[uint32]'
  Get-Process -Name 'emulator-x86_64', 'qt5_msgbox' -ErrorAction SilentlyContinue | ForEach-Object { [void]$set.Add([uint32]$_.Id) }
  return , $set
}

if ($Action -eq 'launch') {
  Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', (Join-Path $bin 'em-cli.bat'), 'launch', '-n', $Vm -WindowStyle Hidden
  $stolen = 0
  $seen = 0
  $until = (Get-Date).AddSeconds(90)
  while ((Get-Date) -lt $until) {
    $pids = Get-EmuPids
    if ($pids.Count -gt 0) {
      foreach ($hwnd in [EmuWin]::WindowsOf($pids)) {
        $seen++
        if ([EmuWin]::Park($hwnd)) { $stolen++ }
      }
    }
    Start-Sleep -Milliseconds 25
  }
  Write-Output "launch watch done: windows parked=$seen focus returned=$stolen"
}
elseif ($Action -eq 'park') {
  foreach ($hwnd in [EmuWin]::WindowsOf((Get-EmuPids))) { [void][EmuWin]::Park($hwnd); Write-Output ("parked: " + [EmuWin]::Title($hwnd)) }
}
elseif ($Action -eq 'run') {
  $ecp = Join-Path $bin 'ecp-cli.bat'
  $pids = Get-EmuPids
  if ($pids.Count -eq 0) { Write-Output 'emulator is not running'; exit 1 }
  [void](New-Item -ItemType Directory -Force $OutDir)
  foreach ($step in ($Steps -split '\s+' | Where-Object { $_ })) {
    if ($step -match '^w(\d+)$') { Start-Sleep -Milliseconds ([int]$Matches[1]) }
    elseif ($step -match '^h(\d+):(\d+)$') {
      & cmd /c $ecp --target $Serial keycode $Matches[1] press 2>&1 | Out-Null
      Start-Sleep -Milliseconds ([int]$Matches[2])
      & cmd /c $ecp --target $Serial keycode $Matches[1] release 2>&1 | Out-Null
    }
    elseif ($step -match '^s:(.+)$') {
      $hwnd = [EmuWin]::Biggest($pids)
      if ($hwnd -eq [IntPtr]::Zero) { Write-Output 'no emulator window'; exit 1 }
      $png = Join-Path $OutDir ($Matches[1] + '.png')
      Write-Output ("shot " + $png + ": " + [EmuWin]::Shot($hwnd, $png))
    }
    elseif ($step -match '^\d+$') {
      & cmd /c $ecp --target $Serial keycode $step 80 2>&1 | Out-Null
      Start-Sleep -Milliseconds 350
    }
    else { Write-Output "unknown step: $step" }
  }
}
else { Write-Output "unknown action: $Action (launch | park | run)" }
