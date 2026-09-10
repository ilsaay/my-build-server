#define _WIN32_WINNT 0x0600

#include <windows.h>
#include <stdio.h>
#include <conio.h>
#include <string.h>

#define REFRESH_MS 1000
#define HOSTNAME_SIZE 256

static void clear_screen(void)
{
    printf("\033[2J\033[H");
}

static void enable_console_vt(void)
{
    HANDLE h;
    DWORD mode;

    h = GetStdHandle(STD_OUTPUT_HANDLE);

    if (h == INVALID_HANDLE_VALUE) {
        return;
    }

    if (!GetConsoleMode(h, &mode)) {
        return;
    }

    mode = mode | ENABLE_VIRTUAL_TERMINAL_PROCESSING;
    SetConsoleMode(h, mode);
}

static unsigned long filetime_low(const FILETIME *ft)
{
    return (unsigned long)ft->dwLowDateTime;
}

static unsigned long filetime_high(const FILETIME *ft)
{
    return (unsigned long)ft->dwHighDateTime;
}

static void print_u64_decimal(ULARGE_INTEGER value)
{
    char buffer[32];
    int pos;
    unsigned long remainder;

    pos = 0;

    if (value.QuadPart == 0) {
        putchar('0');
        return;
    }

    while (value.QuadPart != 0) {
        remainder = (unsigned long)(value.QuadPart % 10);
        buffer[pos] = (char)('0' + remainder);
        pos++;

        value.QuadPart = value.QuadPart / 10;
    }

    while (pos > 0) {
        pos--;
        putchar(buffer[pos]);
    }
}

static void print_size_mb(ULARGE_INTEGER bytes)
{
    ULARGE_INTEGER mb;

    mb.QuadPart = bytes.QuadPart / 1024;
    mb.QuadPart = mb.QuadPart / 1024;

    print_u64_decimal(mb);
}

static ULARGE_INTEGER filetime_to_u64(FILETIME ft)
{
    ULARGE_INTEGER value;

    value.LowPart = ft.dwLowDateTime;
    value.HighPart = ft.dwHighDateTime;

    return value;
}

static int get_cpu_usage(double *usage)
{
    FILETIME idle_time;
    FILETIME kernel_time;
    FILETIME user_time;

    static ULARGE_INTEGER old_idle;
    static ULARGE_INTEGER old_kernel;
    static ULARGE_INTEGER old_user;

    ULARGE_INTEGER idle;
    ULARGE_INTEGER kernel;
    ULARGE_INTEGER user;

    ULARGE_INTEGER idle_delta;
    ULARGE_INTEGER kernel_delta;
    ULARGE_INTEGER user_delta;
    ULARGE_INTEGER total_delta;

    static int initialized = 0;

    if (!GetSystemTimes(
            &idle_time,
            &kernel_time,
            &user_time)) {
        *usage = 0.0;
        return 0;
    }

    idle = filetime_to_u64(idle_time);
    kernel = filetime_to_u64(kernel_time);
    user = filetime_to_u64(user_time);

    if (!initialized) {
        old_idle = idle;
        old_kernel = kernel;
        old_user = user;
        initialized = 1;

        *usage = 0.0;
        return 1;
    }

    if (idle.QuadPart < old_idle.QuadPart ||
        kernel.QuadPart < old_kernel.QuadPart ||
        user.QuadPart < old_user.QuadPart) {

        old_idle = idle;
        old_kernel = kernel;
        old_user = user;

        *usage = 0.0;
        return 1;
    }

    idle_delta.QuadPart = idle.QuadPart - old_idle.QuadPart;
    kernel_delta.QuadPart = kernel.QuadPart - old_kernel.QuadPart;
    user_delta.QuadPart = user.QuadPart - old_user.QuadPart;

    total_delta.QuadPart =
        kernel_delta.QuadPart + user_delta.QuadPart;

    old_idle = idle;
    old_kernel = kernel;
    old_user = user;

    if (total_delta.QuadPart == 0) {
        *usage = 0.0;
        return 1;
    }

    if (idle_delta.QuadPart > total_delta.QuadPart) {
        *usage = 0.0;
        return 1;
    }

    *usage =
        100.0 -
        ((double)idle_delta.QuadPart * 100.0 /
         (double)total_delta.QuadPart);

    if (*usage < 0.0) {
        *usage = 0.0;
    }

    if (*usage > 100.0) {
        *usage = 100.0;
    }

    return 1;
}

static void print_uptime(void)
{
    ULARGE_INTEGER ticks;
    ULONGLONG milliseconds;
    ULONGLONG seconds;
    ULONGLONG minutes;
    ULONGLONG hours;
    ULONGLONG days;

    ticks.QuadPart = GetTickCount64();

    milliseconds = ticks.QuadPart;
    seconds = milliseconds / 1000;
    minutes = seconds / 60;
    hours = minutes / 60;
    days = hours / 24;

    seconds = seconds % 60;
    minutes = minutes % 60;
    hours = hours % 24;

    printf("%lu days, %lu hours, %lu minutes, %lu seconds",
           (unsigned long)days,
           (unsigned long)hours,
           (unsigned long)minutes,
           (unsigned long)seconds);
}

static void print_memory(void)
{
    MEMORYSTATUSEX memory;
    ULARGE_INTEGER total;
    ULARGE_INTEGER available;
    ULARGE_INTEGER used;

    memory.dwLength = sizeof(MEMORYSTATUSEX);

    if (!GlobalMemoryStatusEx(&memory)) {
        printf("Memory information unavailable");
        return;
    }

    total.QuadPart = memory.ullTotalPhys;
    available.QuadPart = memory.ullAvailPhys;

    if (total.QuadPart >= available.QuadPart) {
        used.QuadPart =
            total.QuadPart - available.QuadPart;
    } else {
        used.QuadPart = 0;
    }

    printf("Used: ");
    print_size_mb(used);

    printf(" MB / ");

    print_size_mb(total);

    printf(" MB");

    printf("  (%lu%%)",
           (unsigned long)memory.dwMemoryLoad);
}

static void print_hostname(void)
{
    char hostname[HOSTNAME_SIZE];
    DWORD size;

    size = HOSTNAME_SIZE;

    memset(hostname, 0, sizeof(hostname));

    if (GetComputerNameA(hostname, &size)) {
        printf("%s", hostname);
    } else {
        printf("Unknown");
    }
}

static void draw_screen(void)
{
    double cpu;

    clear_screen();

    printf("============================================================\n");
    printf("                    Windows System Info\n");
    printf("============================================================\n");
    printf("\n");

    printf("Hostname : ");
    print_hostname();
    printf("\n");

    printf("CPU      : ");

    if (get_cpu_usage(&cpu)) {
        printf("%.1f%%", cpu);
    } else {
        printf("Unavailable");
    }

    printf("\n");

    printf("Memory   : ");
    print_memory();
    printf("\n");

    printf("Uptime   : ");
    print_uptime();
    printf("\n");

    printf("\n");
    printf("Refresh  : %d ms\n", REFRESH_MS);
    printf("\n");

    printf("------------------------------------------------------------\n");
    printf("Q = Quit    R = Refresh\n");
    printf("------------------------------------------------------------\n");

    fflush(stdout);
}

int main(void)
{
    int running;
    int key;

    enable_console_vt();

    running = 1;

    draw_screen();

    while (running) {

        Sleep(REFRESH_MS);

        if (_kbhit()) {
            key = _getch();

            if (key == 'q' || key == 'Q') {
                running = 0;
            } else if (key == 'r' || key == 'R') {
                draw_screen();
            }
        }

        if (running) {
            draw_screen();
        }
    }

    clear_screen();

    printf("Exiting...\n");

    return 0;
}
