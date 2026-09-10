// Windows 平台 TUI 系统信息实时刷新工具
// 单文件，无第三方库，只用 D 标准库 + Win32 API
// 约束：单线程 / 内存安全（@safe 尽量） / 不写磁盘 / 全程内存运行

module main;

import core.stdc.wchar_;
import core.sys.windows.windows;
import core.sys.windows.windef;
import core.sys.windows.winbase;
import core.sys.windows.wincon;
import core.sys.windows.winnt;
import std.stdio;
import std.string;
import std.conv : to;
import std.datetime;
import std.format;
import std.array : replicate;

// ── 显式声明缺失的 Win32 函数 ───────────────────────────────
extern(Windows) nothrow @nogc:
    ULONGLONG GetTickCount64();

extern(C) nothrow @nogc:
    int _kbhit();
    int _getch();

// ── 全局开关 ────────────────────────────────────────────────
__gshared bool g_running = true;

// ── TUI 基础 ────────────────────────────────────────────────
enum string ESC = "\x1b";

void enableVT()
{
    HANDLE h = GetStdHandle(STD_OUTPUT_HANDLE);
    DWORD mode = 0;
    if (GetConsoleMode(h, &mode))
    {
        SetConsoleMode(h, mode | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
    }
}

void hideCursor() { printf("%s[?25l", ESC.ptr); }
void showCursor() { printf("%s[?25h", ESC.ptr); }
void clearScreen() { printf("%s[2J%s[H", ESC.ptr, ESC.ptr); }
void moveTo(int row, int col) { printf("%s[%d;%dH", ESC.ptr, row, col); }

void putAt(int row, int col, string text)
{
    moveTo(row, col);
    printf("%.*s", cast(int)text.length, text.ptr);
}

void drawBox(int top, int left, int height, int width, string title)
{
    string hline = replicate("-", width - 2);
    moveTo(top, left);
    printf("+%.*s+", cast(int)hline.length, hline.ptr);
    if (title.length > 0 && title.length < width - 4)
        putAt(top, left + 2, " " ~ title ~ " ");

    foreach (r; top + 1 .. top + height - 1)
    {
        putAt(r, left, "|");
        putAt(r, left + width - 1, "|");
    }
    putAt(top + height - 1, left, "+" ~ hline ~ "+");
}

// ── 系统信息采集 ────────────────────────────────────────────
struct SysInfo
{
    string hostname;
    string os;
    string uptime;
    string memTotal;
    string memFree;
    string localTime;
    string cpuLoad;
}

string img(ulong v) { return to!string(v); }

string pad2(int v)
{
    return v < 10 ? "0" ~ to!string(v) : to!string(v);
}

SysInfo gather()
{
    SysInfo s;

    // 主机名
    char[256] buf;
    DWORD size = cast(DWORD)buf.length;
    if (GetComputerNameA(buf.ptr, &size))
        s.hostname = buf[0 .. size].idup;
    else
        s.hostname = "unknown";

    // OS 版本
    s.os = "Windows";

    // 运行时间
    ulong ms = GetTickCount64();
    ulong sec = ms / 1000;
    s.uptime = format("%dh %dm %ds", sec / 3600, (sec % 3600) / 60, sec % 60);

    // 内存
    MEMORYSTATUSEX mem;
    mem.dwLength = cast(DWORD)MEMORYSTATUSEX.sizeof;
    if (GlobalMemoryStatusEx(&mem))
    {
        s.memTotal = format("%d MB", mem.ullTotalPhys / (1024 * 1024));
        s.memFree  = format("%d MB", mem.ullAvailPhys / (1024 * 1024));
    }
    else
    {
        s.memTotal = "N/A";
        s.memFree  = "N/A";
    }

    // 本地时间
    auto now = Clock.currTime();
    s.localTime = format("%04d-%02d-%02d %02d:%02d:%02d",
        now.year, now.month, now.day, now.hour, now.minute, now.second);

    // CPU 负载：简单占位
    s.cpuLoad = "N/A";

    return s;
}

// ── 渲染 ────────────────────────────────────────────────────
void render(const ref SysInfo s)
{
    clearScreen();
    drawBox(1, 1, 18, 78, "System Information (D / Windows)");

    putAt(3,  3, "Hostname : " ~ s.hostname);
    putAt(4,  3, "OS       : " ~ s.os);
    putAt(6,  3, "Uptime   : " ~ s.uptime);
    putAt(8,  3, "MemTotal : " ~ s.memTotal);
    putAt(9,  3, "MemFree  : " ~ s.memFree);
    putAt(11, 3, "LocalTime: " ~ s.localTime);
    putAt(12, 3, "CPULoad  : " ~ s.cpuLoad);

    putAt(16, 3, "Press Q to quit, R to refresh...");
    moveTo(16, 36);
}

// ── 主循环 ──────────────────────────────────────────────────
void main()
{
    enableVT();
    hideCursor();
    clearScreen();

    scope(exit)
    {
        showCursor();
        clearScreen();
    }

    bool needRefresh = true;

    while (g_running)
    {
        if (needRefresh)
        {
            auto info = gather();
            render(info);
            needRefresh = false;
        }

        // 非阻塞检查按键
        if (_kbhit())
        {
            int ch = _getch();
            switch (ch)
            {
                case 'q', 'Q':
                    g_running = false;
                    break;
                case 'r', 'R':
                    needRefresh = true;
                    break;
                default:
                    break;
            }
        }

        // 每 1 秒自动刷新
        Sleep(1000);
        needRefresh = true;
    }
}
