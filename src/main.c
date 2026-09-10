/*
 * sysinfo - Windows realtime system information TUI
 *
 * Language:
 *     ISO C89
 *
 * Requirements:
 *     - No third-party libraries
 *     - Single application thread
 *     - No application file I/O
 *     - Fixed-size buffers
 *     - Windows native API
 *     - Hard process memory limit: 256 MiB
 *
 * Build:
 *     x86_64-w64-mingw32-gcc -std=c89 -pedantic -Wall -Wextra \
 *         -O2 -s -mwindows -o sysinfo.exe src/main.c
 *
 * Note:
 *     This program does not create additional threads.
 *     Windows itself may create/manage threads outside the application's
 *     control. The application logic is single-threaded.
 */

#define WIN32_LEAN_AND_MEAN

#include <windows.h>
#include <psapi.h>
#include <stdio.h>
#include <conio.h>
#include <string.h>
#include <time.h>

/* ------------------------------------------------------------
 * Configuration
 * ------------------------------------------------------------ */

#define MEMORY_LIMIT_BYTES (256ULL * 1024ULL * 1024ULL)

#define SCREEN_WIDTH 78

#define TRUE_C  1
#define FALSE_C 0

/* ------------------------------------------------------------
 * Job Object definitions
 *
 * We define the structures ourselves so that no third-party
 * library or external Windows binding is required.
 * ------------------------------------------------------------ */

typedef struct _IO_COUNTERS_C89 {
    ULONGLONG ReadOperationCount;
    ULONGLONG WriteOperationCount;
    ULONGLONG OtherOperationCount;
    ULONGLONG ReadTransferCount;
    ULONGLONG WriteTransferCount;
    ULONGLONG OtherTransferCount;
} IO_COUNTERS_C89;

typedef struct _BASIC_LIMIT_INFORMATION_C89 {
    LARGE_INTEGER PerProcessUserTimeLimit;
    LARGE_INTEGER PerJobUserTimeLimit;
    DWORD LimitFlags;
    SIZE_T MinimumWorkingSetSize;
    SIZE_T MaximumWorkingSetSize;
    DWORD ActiveProcessLimit;
    ULONG_PTR Affinity;
    DWORD PriorityClass;
    DWORD SchedulingClass;
} BASIC_LIMIT_INFORMATION_C89;

typedef struct _JOBOBJECT_EXTENDED_LIMIT_INFORMATION_C89 {
    BASIC_LIMIT_INFORMATION_C89 BasicLimitInformation;
    IO_COUNTERS_C89 IoInfo;
    SIZE_T ProcessMemoryLimit;
    SIZE_T JobMemoryLimit;
    SIZE_T PeakProcessMemoryUsed;
    SIZE_T PeakJobMemoryUsed;
} JOBOBJECT_EXTENDED_LIMIT_INFORMATION_C89;

/*
 * PROCESS_MEMORY_COUNTERS is normally supplied by psapi.h,
 * but defining our own structure keeps the program explicit.
 */
typedef struct _PROCESS_MEMORY_COUNTERS_C89 {
    DWORD cb;
    DWORD PageFaultCount;
    SIZE_T PeakWorkingSetSize;
    SIZE_T WorkingSetSize;
    SIZE_T QuotaPeakPagedPoolUsage;
    SIZE_T QuotaPagedPoolUsage;
    SIZE_T QuotaPeakNonPagedPoolUsage;
    SIZE_T QuotaNonPagedPoolUsage;
    SIZE_T PagefileUsage;
    SIZE_T PeakPagefileUsage;
} PROCESS_MEMORY_COUNTERS_C89;

/* ------------------------------------------------------------
 * Constants
 * ------------------------------------------------------------ */

#define JOB_OBJECT_EXTENDED_LIMIT_INFORMATION_C89 9
#define JOB_OBJECT_LIMIT_PROCESS_MEMORY_C89 0x00000100

#define ENABLE_VIRTUAL_TERMINAL_PROCESSING_C89 0x0004

/* ------------------------------------------------------------
 * Globals
 * ------------------------------------------------------------ */

static HANDLE g_job = NULL;
static HANDLE g_stdout = NULL;
static DWORD g_original_console_mode = 0;
static int g_console_mode_changed = FALSE_C;

/* ------------------------------------------------------------
 * Utility
 * ------------------------------------------------------------ */

static void print_error(const char *text)
{
    DWORD error_code;
    char buffer[128];

    error_code = GetLastError();

    sprintf(buffer, "%s (Windows error %lu)\r\n",
            text,
            (unsigned long)error_code);

    fputs(buffer, stdout);
}

/* ------------------------------------------------------------
 * Memory limit
 * ------------------------------------------------------------ */

static int apply_memory_limit(void)
{
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION_C89 info;

    g_job = CreateJobObjectA(NULL, NULL);

    if (g_job == NULL) {
        print_error("CreateJobObject failed");
        return FALSE_C;
    }

    memset(&info, 0, sizeof(info));

    info.BasicLimitInformation.LimitFlags =
        JOB_OBJECT_LIMIT_PROCESS_MEMORY_C89;

    info.ProcessMemoryLimit =
        (SIZE_T)MEMORY_LIMIT_BYTES;

    if (!SetInformationJobObject(
            g_job,
            JOB_OBJECT_EXTENDED_LIMIT_INFORMATION_C89,
            &info,
            (DWORD)sizeof(info))) {

        print_error("SetInformationJobObject failed");
        CloseHandle(g_job);
        g_job = NULL;

        return FALSE_C;
    }

    if (!AssignProcessToJobObject(
            g_job,
            GetCurrentProcess())) {

        print_error("AssignProcessToJobObject failed");
        CloseHandle(g_job);
        g_job = NULL;

        return FALSE_C;
    }

    return TRUE_C;
}

/* ------------------------------------------------------------
 * Console
 * ------------------------------------------------------------ */

static int enable_virtual_terminal(void)
{
    DWORD mode;

    g_stdout = GetStdHandle(STD_OUTPUT_HANDLE);

    if (g_stdout == NULL ||
        g_stdout == INVALID_HANDLE_VALUE) {
        return FALSE_C;
    }

    if (!GetConsoleMode(g_stdout, &mode)) {
        /*
         * stdout may not be a real console.
         * We can still operate using normal output.
         */
        return FALSE_C;
    }

    g_original_console_mode = mode;

    mode |= ENABLE_VIRTUAL_TERMINAL_PROCESSING_C89;

    if (!SetConsoleMode(g_stdout, mode)) {
        return FALSE_C;
    }

    g_console_mode_changed = TRUE_C;

    return TRUE_C;
}

static void restore_console(void)
{
    if (g_console_mode_changed &&
        g_stdout != NULL) {

        SetConsoleMode(
            g_stdout,
            g_original_console_mode);
    }

    if (g_job != NULL) {
        CloseHandle(g_job);
        g_job = NULL;
    }
}

/* ------------------------------------------------------------
 * TUI
 * ------------------------------------------------------------ */

static void clear_screen(void)
{
    fputs("\033[2J\033[H", stdout);
}

static void hide_cursor(void)
{
    fputs("\033[?25l", stdout);
}

static void show_cursor(void)
{
    fputs("\033[?25h", stdout);
}

static void draw_line(void)
{
    int i;

    for (i = 0; i < SCREEN_WIDTH; ++i) {
        putchar('-');
    }

    putchar('\n');
}

static void draw_title(void)
{
    clear_screen();

    draw_line();

    printf("  SYSINFO - Windows Realtime System Monitor\n");

    draw_line();
}

static void format_bytes(
    unsigned long long value,
    char *buffer,
    size_t buffer_size)
{
    double v;

    v = (double)value;

    if (v >= 1024.0 * 1024.0 * 1024.0) {
        sprintf(buffer, "%.2f GiB",
                v / (1024.0 * 1024.0 * 1024.0));
    }
    else if (v >= 1024.0 * 1024.0) {
        sprintf(buffer, "%.2f MiB",
                v / (1024.0 * 1024.0));
    }
    else if (v >= 1024.0) {
        sprintf(buffer, "%.2f KiB",
                v / 1024.0);
    }
    else {
        sprintf(buffer, "%llu bytes", value);
    }

    /*
     * Keep the compiler aware that buffer_size is intentional.
     */
    if (buffer_size == 0) {
        buffer[0] = '\0';
    }
}

/* ------------------------------------------------------------
 * CPU
 * ------------------------------------------------------------ */

static ULONGLONG filetime_to_u64(
    const FILETIME *ft)
{
    ULARGE_INTEGER value;

    value.LowPart = ft->dwLowDateTime;
    value.HighPart = ft->dwHighDateTime;

    return value.QuadPart;
}

static int get_cpu_times(
    ULONGLONG *idle,
    ULONGLONG *kernel,
    ULONGLONG *user)
{
    FILETIME idle_time;
    FILETIME kernel_time;
    FILETIME user_time;

    if (!GetSystemTimes(
            &idle_time,
            &kernel_time,
            &user_time)) {
        return FALSE_C;
    }

    *idle = filetime_to_u64(&idle_time);
    *kernel = filetime_to_u64(&kernel_time);
    *user = filetime_to_u64(&user_time);

    return TRUE_C;
}

static double calculate_cpu_usage(
    ULONGLONG old_idle,
    ULONGLONG old_kernel,
    ULONGLONG old_user,
    ULONGLONG new_idle,
    ULONGLONG new_kernel,
    ULONGLONG new_user)
{
    ULONGLONG idle_delta;
    ULONGLONG kernel_delta;
    ULONGLONG user_delta;
    ULONGLONG total_delta;
    ULONGLONG busy_delta;

    idle_delta = new_idle - old_idle;
    kernel_delta = new_kernel - old_kernel;
    user_delta = new_user - old_user;

    /*
     * GetSystemTimes() kernel time includes idle time.
     */
    total_delta = kernel_delta + user_delta;

    if (total_delta == 0) {
        return 0.0;
    }

    if (idle_delta > total_delta) {
        idle_delta = total_delta;
    }

    busy_delta = total_delta - idle_delta;

    return ((double)busy_delta /
            (double)total_delta) * 100.0;
}

/* ------------------------------------------------------------
 * RAM
 * ------------------------------------------------------------ */

static int get_memory_info(
    unsigned long long *total,
    unsigned long long *available,
    unsigned long long *used)
{
    MEMORYSTATUSEX state;

    memset(&state, 0, sizeof(state));

    state.dwLength = sizeof(state);

    if (!GlobalMemoryStatusEx(&state)) {
        return FALSE_C;
    }

    *total = (unsigned long long)state.ullTotalPhys;

    *available =
        (unsigned long long)state.ullAvailPhys;

    if (*total >= *available) {
        *used = *total - *available;
    }
    else {
        *used = 0;
    }

    return TRUE_C;
}

/* ------------------------------------------------------------
 * Process memory
 * ------------------------------------------------------------ */

static unsigned long long get_process_memory(void)
{
    PROCESS_MEMORY_COUNTERS_C89 pmc;
    HANDLE process;
    BOOL result;

    memset(&pmc, 0, sizeof(pmc));

    pmc.cb = sizeof(pmc);

    process = GetCurrentProcess();

    result = GetProcessMemoryInfo(
        process,
        (PROCESS_MEMORY_COUNTERS *)&pmc,
        sizeof(pmc));

    if (!result) {
        return 0;
    }

    return (unsigned long long)pmc.WorkingSetSize;
}

/* ------------------------------------------------------------
 * Computer name
 * ------------------------------------------------------------ */

static void get_hostname(char *buffer, DWORD buffer_size)
{
    DWORD size;

    size = buffer_size;

    if (!GetComputerNameA(buffer, &size)) {
        strcpy(buffer, "Unknown");
    }
}

/* ------------------------------------------------------------
 * Uptime
 * ------------------------------------------------------------ */

static void format_uptime(
    ULONGLONG milliseconds,
    char *buffer)
{
    ULONGLONG seconds;
    ULONGLONG minutes;
    ULONGLONG hours;
    ULONGLONG days;

    seconds = milliseconds / 1000ULL;

    days = seconds / 86400ULL;
    seconds %= 86400ULL;

    hours = seconds / 3600ULL;
    seconds %= 3600ULL;

    minutes = seconds / 60ULL;
    seconds %= 60ULL;

    sprintf(
        buffer,
        "%llu days, %02llu:%02llu:%02llu",
        days,
        hours,
        minutes,
        seconds);
}

/* ------------------------------------------------------------
 * System information
 * ------------------------------------------------------------ */

static DWORD get_cpu_count(void)
{
    SYSTEM_INFO info;

    memset(&info, 0, sizeof(info));

    GetSystemInfo(&info);

    return info.dwNumberOfProcessors;
}

/* ------------------------------------------------------------
 * Render
 * ------------------------------------------------------------ */

static void render(
    double cpu_usage,
    unsigned long long total_memory,
    unsigned long long available_memory,
    unsigned long long used_memory,
    unsigned long long process_memory,
    ULONGLONG uptime,
    const char *hostname)
{
    char total_text[64];
    char available_text[64];
    char used_text[64];
    char process_text[64];
    char limit_text[64];
    char uptime_text[64];
    double memory_usage;

    format_bytes(
        total_memory,
        total_text,
        sizeof(total_text));

    format_bytes(
        available_memory,
        available_text,
        sizeof(available_text));

    format_bytes(
        used_memory,
        used_text,
        sizeof(used_text));

    format_bytes(
        process_memory,
        process_text,
        sizeof(process_text));

    format_bytes(
        MEMORY_LIMIT_BYTES,
        limit_text,
        sizeof(limit_text));

    format_uptime(
        uptime,
        uptime_text);

    if (total_memory != 0) {
        memory_usage =
            ((double)used_memory /
             (double)total_memory) * 100.0;
    }
    else {
        memory_usage = 0.0;
    }

    draw_title();

    printf("\n");

    printf("  Hostname          : %s\n", hostname);

    printf("  CPU Usage         : %6.2f %%\n",
           cpu_usage);

    printf("  CPU Processors    : %lu\n",
           (unsigned long)get_cpu_count());

    printf("\n");

    printf("  Physical RAM      : %s\n",
           total_text);

    printf("  RAM Used          : %s\n",
           used_text);

    printf("  RAM Available     : %s\n",
           available_text);

    printf("  RAM Usage         : %6.2f %%\n",
           memory_usage);

    printf("\n");

    printf("  Process Memory    : %s\n",
           process_text);

    printf("  Hard Memory Limit : %s\n",
           limit_text);

    printf("  Memory Protection : ACTIVE\n");

    printf("\n");

    printf("  Uptime            : %s\n",
           uptime_text);

    printf("  Application       : SINGLE THREAD\n");

    printf("  Disk I/O          : DISABLED BY DESIGN\n");

    printf("  External Libraries: NONE\n");

    printf("\n");

    draw_line();

    printf("  [Q] Quit    [R] Refresh\n");

    draw_line();

    fflush(stdout);
}

/* ------------------------------------------------------------
 * Keyboard
 * ------------------------------------------------------------ */

static int check_keyboard(void)
{
    int key;

    if (!_kbhit()) {
        return TRUE_C;
    }

    key = _getch();

    if (key == 'q' || key == 'Q') {
        return FALSE_C;
    }

    /*
     * R/r simply causes the next loop to redraw.
     */
    return TRUE_C;
}

/* ------------------------------------------------------------
 * Main
 * ------------------------------------------------------------ */

int main(void)
{
    ULONGLONG old_idle;
    ULONGLONG old_kernel;
    ULONGLONG old_user;

    ULONGLONG new_idle;
    ULONGLONG new_kernel;
    ULONGLONG new_user;

    unsigned long long total_memory;
    unsigned long long available_memory;
    unsigned long long used_memory;
    unsigned long long process_memory;

    double cpu_usage;

    char hostname[256];

    int cpu_valid;

    /*
     * No heap allocation is necessary for the application.
     */

    atexit(restore_console);

    /*
     * Apply hard memory limit before entering the main loop.
     */
    if (!apply_memory_limit()) {
        fprintf(
            stderr,
            "\nFATAL: unable to apply 256 MiB process memory limit.\n");
        fprintf(
            stderr,
            "Program will exit for safety.\n");

        return 1;
    }

    enable_virtual_terminal();

    hide_cursor();

    atexit(show_cursor);

    get_hostname(
        hostname,
        sizeof(hostname));

    old_idle = 0;
    old_kernel = 0;
    old_user = 0;

    cpu_valid = get_cpu_times(
        &old_idle,
        &old_kernel,
        &old_user);

    /*
     * Initial one-second sampling interval.
     */
    Sleep(1000);

    while (TRUE_C) {

        if (!check_keyboard()) {
            break;
        }

        if (!get_memory_info(
                &total_memory,
                &available_memory,
                &used_memory)) {

            total_memory = 0;
            available_memory = 0;
            used_memory = 0;
        }

        process_memory =
            get_process_memory();

        if (get_cpu_times(
                &new_idle,
                &new_kernel,
                &new_user)) {

            if (cpu_valid) {
                cpu_usage =
                    calculate_cpu_usage(
                        old_idle,
                        old_kernel,
                        old_user,
                        new_idle,
                        new_kernel,
                        new_user);
            }
            else {
                cpu_usage = 0.0;
            }

            old_idle = new_idle;
            old_kernel = new_kernel;
            old_user = new_user;

            cpu_valid = TRUE_C;
        }
        else {
            cpu_usage = 0.0;
            cpu_valid = FALSE_C;
        }

        render(
            cpu_usage,
            total_memory,
            available_memory,
            used_memory,
            process_memory,
            GetTickCount64(),
            hostname);

        Sleep(1000);
    }

    show_cursor();

    clear_screen();

    printf("sysinfo exited.\n");

    return 0;
}
