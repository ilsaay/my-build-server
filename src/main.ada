--  main.adb
--  Windows TUI 系统信息查看器
--  单线程、无并发、无磁盘写入

with Ada.Text_IO;              use Ada.Text_IO;
with Ada.Strings.Fixed;        use Ada.Strings.Fixed;
with Ada.Environment_Variables;
with Interfaces.C;             use Interfaces.C;
with Interfaces.C.Strings;     use Interfaces.C.Strings;
with System;

package body Main is

   ------------------------------------------------------------------
   --  常量
   ------------------------------------------------------------------
   ESC : constant Character := Character'Val (27);

   ------------------------------------------------------------------
   --  Win32 API 绑定
   ------------------------------------------------------------------
   subtype DWORD  is unsigned_long;
   subtype WORD   is unsigned_short;
   subtype BOOL   is int;
   subtype HANDLE is System.Address;

   STD_OUTPUT_HANDLE : constant DWORD := DWORD (-11);

   ENABLE_VIRTUAL_TERMINAL_PROCESSING : constant DWORD := 16#0004#;

   type COORD is record
      X : short;
      Y : short;
   end record
     with Convention => C;

   type SMALL_RECT is record
      Left   : short;
      Top    : short;
      Right  : short;
      Bottom : short;
   end record
     with Convention => C;

   type CONSOLE_SCREEN_BUFFER_INFO is record
      dwSize              : COORD;
      dwCursorPosition    : COORD;
      wAttributes         : WORD;
      srWindow            : SMALL_RECT;
      dwMaximumWindowSize : COORD;
   end record
     with Convention => C;

   type MEMORYSTATUSEX is record
      dwLength                : DWORD;
      dwMemoryLoad            : DWORD;
      ullTotalPhys            : unsigned_long_long;
      ullAvailPhys            : unsigned_long_long;
      ullTotalPageFile        : unsigned_long_long;
      ullAvailPageFile        : unsigned_long_long;
      ullTotalVirtual         : unsigned_long_long;
      ullAvailVirtual         : unsigned_long_long;
      ullAvailExtendedVirtual : unsigned_long_long;
   end record
     with Convention => C;

   function GetStdHandle (nStdHandle : DWORD) return HANDLE
     with Import, Convention => Stdcall, External_Name => "GetStdHandle";

   function GetConsoleMode (hConsoleHandle : HANDLE;
                            lpMode         : access DWORD) return BOOL
     with Import, Convention => Stdcall, External_Name => "GetConsoleMode";

   function SetConsoleMode (hConsoleHandle : HANDLE;
                            dwMode         : DWORD) return BOOL
     with Import, Convention => Stdcall, External_Name => "SetConsoleMode";

   function GetConsoleScreenBufferInfo
     (hConsoleOutput : HANDLE;
      lpConsoleScreenBufferInfo : access CONSOLE_SCREEN_BUFFER_INFO) return BOOL
     with Import, Convention => Stdcall,
          External_Name => "GetConsoleScreenBufferInfo";

   function GlobalMemoryStatusEx (lpBuffer : access MEMORYSTATUSEX) return BOOL
     with Import, Convention => Stdcall,
          External_Name => "GlobalMemoryStatusEx";

   function GetTickCount64 return unsigned_long_long
     with Import, Convention => Stdcall, External_Name => "GetTickCount64";

   ------------------------------------------------------------------
   --  TUI 基础
   ------------------------------------------------------------------
   procedure Enable_VT_Mode is
      H    : constant HANDLE := GetStdHandle (STD_OUTPUT_HANDLE);
      Mode : aliased DWORD := 0;
      Ok   : BOOL;
   begin
      Ok := GetConsoleMode (H, Mode'Access);
      if Ok /= 0 then
         Ok := SetConsoleMode (H, Mode or ENABLE_VIRTUAL_TERMINAL_PROCESSING);
      end if;
      pragma Unreferenced (Ok);
   end Enable_VT_Mode;

   procedure Clear_Screen is
   begin
      Put (ESC & "[2J" & ESC & "[H");
   end Clear_Screen;

   procedure Move_To (Row, Col : Positive) is
   begin
      Put (ESC & "[" & Trim (Positive'Image (Row), Ada.Strings.Left) &
           ";" & Trim (Positive'Image (Col), Ada.Strings.Left) & "H");
   end Move_To;

   procedure Hide_Cursor is
   begin
      Put (ESC & "[?25l");
   end Hide_Cursor;

   procedure Show_Cursor is
   begin
      Put (ESC & "[?25h");
   end Show_Cursor;

   procedure Put_At (Row, Col : Positive; Text : String) is
   begin
      Move_To (Row, Col);
      Put (Text);
   end Put_At;

   procedure Draw_Box (Top, Left, Height, Width : Positive; Title : String) is
      H_Line : constant String (1 .. Width - 2) := (others => '-');
   begin
      Move_To (Top, Left);
      Put ("+" & H_Line & "+");
      if Title'Length > 0 then
         Put_At (Top, Left + 2, " " & Title & " ");
      end if;
      for R in Top + 1 .. Top + Height - 2 loop
         Put_At (R, Left, "|");
         Put_At (R, Left + Width - 1, "|");
      end loop;
      Put_At (Top + Height - 1, Left, "+" & H_Line & "+");
   end Draw_Box;

   ------------------------------------------------------------------
   --  信息采集
   ------------------------------------------------------------------
   type System_Info is record
      Hostname      : String (1 .. 64)  := (others => ' ');
      Hostname_Len  : Natural := 0;
      OS_Version    : String (1 .. 128) := (others => ' ');
      OS_Version_Len : Natural := 0;
      Uptime        : String (1 .. 64)  := (others => ' ');
      Uptime_Len    : Natural := 0;
      Mem_Total     : String (1 .. 32)  := (others => ' ');
      Mem_Total_Len : Natural := 0;
      Mem_Free      : String (1 .. 32)  := (others => ' ');
      Mem_Free_Len  : Natural := 0;
   end record;

   procedure Copy_To (Src : String; Dst : out String; Dst_Len : out Natural) is
      N : constant Natural := Natural'Min (Src'Length, Dst'Length);
   begin
      Dst_Len := N;
      if N > 0 then
         Dst (Dst'First .. Dst'First + N - 1) := Src (Src'First .. Src'First + N - 1);
      end if;
   end Copy_To;

   function Img (V : unsigned_long_long) return String is
      S : constant String := unsigned_long_long'Image (V);
   begin
      return S (S'First + 1 .. S'Last); -- 去掉前导空格
   end Img;

   procedure Gather (S : out System_Info) is
   begin
      -- 主机名：环境变量
      declare
         Name : constant String :=
           Ada.Environment_Variables.Value ("COMPUTERNAME", "");
      begin
         Copy_To (Name, S.Hostname, S.Hostname_Len);
      end;

      -- OS 版本：环境变量
      declare
         OS : constant String :=
           Ada.Environment_Variables.Value ("OS", "Windows");
      begin
         Copy_To (OS, S.OS_Version, S.OS_Version_Len);
      end;

      -- 运行时间：GetTickCount64
      declare
         Ms      : constant unsigned_long_long := GetTickCount64;
         Seconds : constant unsigned_long_long := Ms / 1000;
         H       : constant unsigned_long_long := Seconds / 3600;
         M       : constant unsigned_long_long := (Seconds mod 3600) / 60;
         Sec     : constant unsigned_long_long := Seconds mod 60;
      begin
         Copy_To (Img (H) & "h " & Img (M) & "m " & Img (Sec) & "s",
                  S.Uptime, S.Uptime_Len);
      end;

      -- 内存：GlobalMemoryStatusEx
      declare
         St : aliased MEMORYSTATUSEX :=
           (dwLength                => DWORD (MEMORYSTATUSEX'Size / 8),
            others                  => <>);
         Ok : BOOL;
      begin
         Ok := GlobalMemoryStatusEx (St'Access);
         if Ok /= 0 then
            Copy_To (Img (St.ullTotalPhys / (1024 * 1024)) & " MB",
                     S.Mem_Total, S.Mem_Total_Len);
            Copy_To (Img (St.ullAvailPhys / (1024 * 1024)) & " MB",
                     S.Mem_Free, S.Mem_Free_Len);
         end if;
      end;
   end Gather;

   ------------------------------------------------------------------
   --  主流程
   ------------------------------------------------------------------
   procedure Run is
      S : System_Info;
   begin
      Enable_VT_Mode;
      Hide_Cursor;
      Clear_Screen;

      Gather (S);

      Draw_Box (1, 1, 16, 78, "System Information (Windows)");

      Put_At (3,  3, "Hostname : " & S.Hostname (1 .. S.Hostname_Len));
      Put_At (4,  3, "OS       : " & S.OS_Version (1 .. S.OS_Version_Len));
      Put_At (6,  3, "Uptime   : " & S.Uptime (1 .. S.Uptime_Len));
      Put_At (8,  3, "MemTotal : " & S.Mem_Total (1 .. S.Mem_Total_Len));
      Put_At (9,  3, "MemFree  : " & S.Mem_Free (1 .. S.Mem_Free_Len));

      Put_At (14, 3, "Press Enter to exit...");
      Move_To (14, 26);

      declare
         Dummy : String (1 .. 1);
         Last  : Natural;
      begin
         Get_Line (Dummy, Last);
      end;

      Show_Cursor;
      Clear_Screen;
   exception
      when others =>
         Show_Cursor;
         Clear_Screen;
         raise;
   end Run;

end Main;
