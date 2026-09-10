module main;

/*
    RAM-only Windows System Monitor
    D language / Windows x64

    Design:
      - Single thread only
      - No file I/O
      - No configuration file
      - No log file
      - No temporary file
      - Runtime state exists only in memory
      - Windows Job Object memory limit
      - TUI using Windows Virtual Terminal
*/

import core.sys.windows.windows;
import core.sys.windows.winbase;
import core.sys.windows.wincon;
import core.sys.windows.winnt;

import std.stdio;
import std.string;
import std.format;
import std.conv : to;
import std.datetime : Clock;


/* ============================================================
   Win32 functions/constants not consistently exposed by D
   ============================================================ */

extern(Windows)
{
    ULONGLONG GetTickCount64();

    BOOL GetSystemTimes(
        FILETIME* idleTime,
        FILETIME* kernelTime,
        FILETIME* userTime
    );

    BOOL GetConsoleMode(
        HANDLE hConsoleHandle,
        DWORD* lpMode
    );

    BOOL SetConsoleMode(
        HANDLE hConsoleHandle,
        DWORD dwMode
    );

    BOOL SetConsoleCursorInfo(
        HANDLE hConsoleOutput,
        ref CONSOLE_CURSOR_INFO lpConsoleCursorInfo
    );

    HANDLE CreateJobObjectA(
        LPSECURITY_ATTRIBUTES lpJobAttributes,
        LPCSTR lpName
    );

    BOOL SetInformationJobObject(
        HANDLE hJob,
        int JobObjectInformationClass,
        LPVOID lpJobObjectInformation,
        DWORD cbJobObjectInformationLength
    );

    BOOL AssignProcessToJobObject(
        HANDLE hJob,
        HANDLE hProcess
    );

    BOOL CloseHandle(
        HANDLE hObject
    );
}


/* ============================================================
   Constants
   ============================================================ */

enum DWORD ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;
enum DWORD ENABLE_PROCESSED_OUTPUT            = 0x0001;

enum int JobObjectExtendedLimitInformation = 9;

enum DWORD JOB_OBJECT_LIMIT_PROCESS_MEMORY = 0x00000100;


/*
    Maximum memory of this process.

    256 MiB is intentionally conservative.

    If you want 128 MiB:
        128UL * 1024UL * 1024UL

    If you want 512 MiB:
        512UL * 1024UL * 1024UL
*/
enum ulong MEMORY_LIMIT = 256UL * 1024UL * 1024UL;


/* ============================================================
   Structures
   ============================================================ */

struct IO_COUNTERS
{
    ulong ReadOperationCount;
    ulong WriteOperationCount;
    ulong OtherOperationCount;

    ulong ReadTransferCount;
    ulong WriteTransferCount;
    ulong OtherTransferCount;
}


/*
    JOB_OBJECT_EXTENDED_LIMIT_INFORMATION
*/
struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION
{
    ulong BasicLimitInformation_PerProcessUserTimeLimit;
    ulong BasicLimitInformation_PerJobUserTimeLimit;

    DWORD BasicLimitInformation_LimitFlags;

    nuint BasicLimitInformation_MinimumWorkingSetSize;
    nuint BasicLimitInformation_MaximumWorkingSetSize;

    DWORD BasicLimitInformation_ActiveProcessLimit;

    nuint BasicLimitInformation_Affinity;

    DWORD BasicLimitInformation_PriorityClass;
    DWORD BasicLimitInformation_SchedulingClass;

    IO_COUNTERS IoInfo;

    nuint ProcessMemoryLimit;
    nuint JobMemoryLimit;
    nuint PeakProcessMemoryUsed;
    nuint PeakJobMemoryUsed;
}


/* ============================================================
   CPU statistics
   ============================================================ */

struct CpuTimes
{
    ulong idle;
    ulong kernel;
    ulong user;
}


/* ============================================================
   Application state
   ============================================================ */

struct SystemInfo
{
    string hostname;
    string os;
    string architecture;

    string uptime;
    string localTime;

    ulong memoryTotal;
    ulong memoryAvailable;
    ulong memoryUsed;

    double cpuUsage;

    ulong processMemory;

    ulong memoryLimit;
}


/* ============================================================
   CPU
   ============================================================ */

bool readCpuTimes(ref CpuTimes t)
{
    FILETIME idle;
    FILETIME kernel;
    FILETIME user;

    if (!GetSystemTimes(&idle, &kernel, &user))
        return false;

    t.idle =
        (cast(ulong) idle.dwHighDateTime << 32) |
        cast(ulong) idle.dwLowDateTime;

    t.kernel =
        (cast(ulong) kernel.dwHighDateTime << 32) |
        cast(ulong) kernel.dwLowDateTime;

    t.user =
        (cast(ulong) user.dwHighDateTime << 32) |
        cast(ulong) user.dwLowDateTime;

    return true;
}


double calculateCpu(
    ref CpuTimes previous,
    ref CpuTimes current
)
{
    ulong idleDelta =
        current.idle - previous.idle;

    ulong kernelDelta =
        current.kernel - previous.kernel;

    ulong userDelta =
        current.user - previous.user;

    ulong totalDelta =
        kernelDelta + userDelta;

    /*
        kernel time includes idle time.
    */
    if (totalDelta == 0)
        return 0.0;

    if (idleDelta > totalDelta)
        return 0.0;

    return
        (cast(double)(totalDelta - idleDelta) /
         cast(double)totalDelta) * 100.0;
}


/* ============================================================
   Memory
   ============================================================ */

void readMemory(
    ref ulong total,
    ref ulong available,
    ref ulong used
)
{
    MEMORYSTATUSEX mem;

    mem.dwLength =
        cast(DWORD) MEMORYSTATUSEX.sizeof;

    if (!GlobalMemoryStatusEx(&mem))
    {
        total = 0;
        available = 0;
        used = 0;
        return;
    }

    total = mem.ullTotalPhys;
    available = mem.ullAvailPhys;

    if (total >= available)
        used = total - available;
    else
        used = 0;
}


/* ============================================================
   Process memory
   ============================================================ */

extern(Windows)
{
    BOOL GetProcessMemoryInfo(
        HANDLE Process,
        void* ppsmemCounters,
        DWORD cb
    );
}


struct PROCESS_MEMORY_COUNTERS
{
    DWORD cb;

    DWORD PageFaultCount;

    nuint PeakWorkingSetSize;
    nuint WorkingSetSize;

    nuint QuotaPeakPagedPoolUsage;
    nuint QuotaPagedPoolUsage;

    nuint QuotaPeakNonPagedPoolUsage;
    nuint QuotaNonPagedPoolUsage;

    nuint PagefileUsage;
    nuint PeakPagefileUsage;
}


ulong getProcessMemory()
{
    PROCESS_MEMORY_COUNTERS counters;

    counters.cb =
        cast(DWORD) PROCESS_MEMORY_COUNTERS.sizeof;

    HANDLE process =
        GetCurrentProcess();

    if (process is null)
        return 0;

    if (!GetProcessMemoryInfo(
        process,
        &counters,
        counters.cb))
    {
        return 0;
    }

    return cast(ulong)counters.WorkingSetSize;
}


/* ============================================================
   Hostname
   ============================================================ */

string getHostname()
{
    char[256] buffer;

    DWORD size =
        cast(DWORD) buffer.length;

    if (GetComputerNameA(
        buffer.ptr,
        &size))
    {
        return buffer[0 .. size].idup;
    }

    return "UNKNOWN";
}


/* ============================================================
   Time
   ============================================================ */

string getUptime()
{
    ulong milliseconds =
        GetTickCount64();

    ulong seconds =
        milliseconds / 1000;

    ulong days =
        seconds / 86400;

    seconds %= 86400;

    ulong hours =
        seconds / 3600;

    seconds %= 3600;

    ulong minutes =
        seconds / 60;

    seconds %= 60;

    return format(
        "%dd %02dh %02dm %02ds",
        days,
        hours,
        minutes,
        seconds
    );
}


string getLocalTime()
{
    auto now = Clock.currTime();

    return format(
        "%04d-%02d-%02d %02d:%02d:%02d",
        now.year,
        now.month,
        now.day,
        now.hour,
        now.minute,
        now.second
    );
}


/* ============================================================
   Formatting
   ============================================================ */

string formatBytes(ulong bytes)
{
    if (bytes >= 1024UL * 1024UL * 1024UL)
    {
        return format(
            "%.2f GiB",
            cast(double)bytes /
            (1024.0 * 1024.0 * 1024.0)
        );
    }

    if (bytes >= 1024UL * 1024UL)
    {
        return format(
            "%.2f MiB",
            cast(double)bytes /
            (1024.0 * 1024.0)
        );
    }

    if (bytes >= 1024UL)
    {
        return format(
            "%.2f KiB",
            cast(double)bytes /
            1024.0
        );
    }

    return format(
        "%d B",
        bytes
    );
}


string formatPercent(double value)
{
    return format(
        "%6.2f%%",
        value
    );
}


/* ============================================================
   TUI
   ============================================================ */

enum string ESC = "\x1b";


void enableVT()
{
    HANDLE output =
        GetStdHandle(STD_OUTPUT_HANDLE);

    DWORD mode = 0;

    if (GetConsoleMode(
        output,
        &mode))
    {
        mode |=
            ENABLE_PROCESSED_OUTPUT |
            ENABLE_VIRTUAL_TERMINAL_PROCESSING;

        SetConsoleMode(
            output,
            mode);
    }
}


void hideCursor()
{
    HANDLE output =
        GetStdHandle(STD_OUTPUT_HANDLE);

    CONSOLE_CURSOR_INFO info;

    info.dwSize = 1;
    info.bVisible = FALSE;

    SetConsoleCursorInfo(
        output,
        info);
}


void showCursor()
{
    HANDLE output =
        GetStdHandle(STD_OUTPUT_HANDLE);

    CONSOLE_CURSOR_INFO info;

    info.dwSize = 1;
    info.bVisible = TRUE;

    SetConsoleCursorInfo(
        output,
        info);
}


void clearScreen()
{
    printf(
        "%s[2J%s[H",
        ESC.ptr,
        ESC.ptr
    );
}


void moveTo(
    int row,
    int col
)
{
    printf(
        "%s[%d;%dH",
        ESC.ptr,
        row,
        col
    );
}


void clearLine()
{
    printf(
        "%s[2K",
        ESC.ptr
    );
}


void printLine(
    int row,
    string text
)
{
    moveTo(row, 1);
    clearLine();

    printf(
        "%s",
        text.ptr
    );
}


void drawHorizontal(
    int row,
    int width
)
{
    moveTo(row, 1);

    for (int i = 0; i < width; ++i)
        putchar('-');
}


void drawUI(
    ref SystemInfo info
)
{
    clearScreen();

    enum int WIDTH = 78;

    drawHorizontal(1, WIDTH);

    printLine(
        2,
        "  D / Windows RAM System Monitor"
    );

    drawHorizontal(3, WIDTH);

    printLine(
        5,
        format(
            "  Hostname       : %s",
            info.hostname
        )
    );

    printLine(
        6,
        format(
            "  OS             : %s",
            info.os
        )
    );

    printLine(
        7,
        format(
            "  Architecture   : %s",
            info.architecture
        )
    );

    printLine(
        9,
        format(
            "  CPU Usage      : %s",
            formatPercent(info.cpuUsage)
        )
    );

    printLine(
        10,
        format(
            "  RAM Total      : %s",
            formatBytes(info.memoryTotal)
        )
    );

    printLine(
        11,
        format(
            "  RAM Used       : %s",
            formatBytes(info.memoryUsed)
        )
    );

    printLine(
        12,
        format(
            "  RAM Available  : %s",
            formatBytes(info.memoryAvailable)
        )
    );

    printLine(
        14,
        format(
            "  Process Memory : %s",
            formatBytes(info.processMemory)
        )
    );

    printLine(
        15,
        format(
            "  Memory Limit   : %s",
            formatBytes(info.memoryLimit)
        )
    );

    printLine(
        17,
        format(
            "  System Uptime  : %s",
            info.uptime
        )
    );

    printLine(
        18,
        format(
            "  Local Time     : %s",
            info.localTime
        )
    );

    drawHorizontal(20, WIDTH);

    printLine(
        22,
        "  [Q] Quit"
    );

    printLine(
        23,
        "  [R] Refresh"
    );

    printLine(
        24,
        "  Refresh interval: 1 second"
    );

    printLine(
        26,
        "  Security: single-thread / no file I/O / 256 MiB limit"
    );

    moveTo(28, 1);

    fflush(stdout);
}


/* ============================================================
   Non-blocking keyboard
   ============================================================ */

extern(C)
{
    int _kbhit();
    int _getch();
}


bool processKeyboard()
{
    if (!_kbhit())
        return true;

    int key =
        _getch();

    if (key == 'q' ||
        key == 'Q')
    {
        return false;
    }

    /*
        Windows special keys normally return
        0 / 224 first. Consume second byte.
    */
    if (key == 0 ||
        key == 224)
    {
        if (_kbhit())
            _getch();

        return true;
    }

    return true;
}


/* ============================================================
   Memory restriction
   ============================================================ */

bool applyMemoryLimit()
{
    HANDLE job =
        CreateJobObjectA(
            null,
            null
        );

    if (job is null)
        return false;

    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limit;

    limit.BasicLimitInformation_LimitFlags =
        JOB_OBJECT_LIMIT_PROCESS_MEMORY;

    limit.ProcessMemoryLimit =
        cast(nuint)MEMORY_LIMIT;

    if (!SetInformationJobObject(
        job,
        JobObjectExtendedLimitInformation,
        &limit,
        cast(DWORD)
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION.sizeof))
    {
        CloseHandle(job);
        return false;
    }

    HANDLE process =
        GetCurrentProcess();

    if (!AssignProcessToJobObject(
        job,
        process))
    {
        CloseHandle(job);
        return false;
    }

    /*
        Intentionally do NOT close the job handle here.

        Windows keeps the Job Object alive while this process
        remains assigned to it.

        The handle itself stays open until process exit.
    */

    return true;
}


/* ============================================================
   Gather system information
   ============================================================ */

SystemInfo gather(
    double cpu
)
{
    SystemInfo info;

    info.hostname =
        getHostname();

    info.os =
        "Microsoft Windows";

    info.architecture =
        "x86_64";

    info.uptime =
        getUptime();

    info.localTime =
        getLocalTime();

    readMemory(
        info.memoryTotal,
        info.memoryAvailable,
        info.memoryUsed
    );

    info.cpuUsage =
        cpu;

    info.processMemory =
        getProcessMemory();

    info.memoryLimit =
        MEMORY_LIMIT;

    return info;
}


/* ============================================================
   Main
   ============================================================ */

void main()
{
    /*
        IMPORTANT:
        There is intentionally no thread creation here.
    */

    /*
        Apply process memory limit as early as possible.
    */
    bool memoryLimitOK =
        applyMemoryLimit();

    enableVT();
    hideCursor();

    scope(exit)
    {
        showCursor();

        printf(
            "%s[0m%s[2J%s[H",
            ESC.ptr,
            ESC.ptr,
            ESC.ptr
        );

        fflush(stdout);
    }


    CpuTimes previous;
    CpuTimes current;

    bool haveCpuSample =
        readCpuTimes(previous);

    /*
        If the Job Object cannot be created,
        continue running but display the warning.
    */

    while (true)
    {
        /*
            One-second interval.

            This is deliberately a single blocking sleep.
            No worker thread exists.
        */
        Sleep(1000);


        if (!processKeyboard())
            break;


        double cpu = 0.0;


        if (haveCpuSample &&
            readCpuTimes(current))
        {
            cpu =
                calculateCpu(
                    previous,
                    current
                );

            previous =
                current;
        }
        else
        {
            haveCpuSample =
                readCpuTimes(previous);
        }


        SystemInfo info =
            gather(cpu);


        drawUI(info);


        if (!memoryLimitOK)
        {
            printLine(
                30,
                "  WARNING: Windows memory limit could not be applied."
            );
        }
        else
        {
            printLine(
                30,
                "  Memory limit: ACTIVE"
            );
        }


        printLine(
            31,
            "  Disk I/O: NONE"
        );

        printLine(
            32,
            "  Threads created by application: 0"
        );

        fflush(stdout);
    }
}
