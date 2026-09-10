with Ada.Text_IO;              use Ada.Text_IO;
with Ada.Strings.Fixed;        use Ada.Strings.Fixed;
with Ada.Environment_Variables;
with Interfaces.C;             use Interfaces.C;
with System;

procedure Main is

   ESC : constant Character := Character'Val (27);

   ---------------------------------------------------------------------------
   -- Win32 API 绑定（修正：DWORD 用 32 位 unsigned）
   ---------------------------------------------------------------------------
   subtype DWORD  is unsigned;          -- 修正点
   subtype WORD   is unsigned_short;
   subtype BOOL   is int;
   subtype HANDLE is System.Address;

   STD_OUTPUT_HANDLE : constant DWORD := DWORD (-11);
   ENABLE_VIRTUAL_TERMINAL_PROCESSING : constant DWORD := 16#0004#;

   ...
