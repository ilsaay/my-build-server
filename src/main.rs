#![allow(non_snake_case)]
#![allow(dead_code)]

use std::ffi::c_void;
use std::mem::{size_of, zeroed};
use std::ptr::{null, null_mut};

type BOOL = i32;
type BYTE = u8;
type WORD = u16;
type DWORD = u32;
type UINT = u32;
type LONG = i32;
type ULONG = u32;
type ULONGLONG = u64;
type WCHAR = u16;
type HANDLE = *mut c_void;
type HKEY = HANDLE;
type HMODULE = HANDLE;
type LPVOID = *mut c_void;
type LPCVOID = *const c_void;
type LPWSTR = *mut WCHAR;
type LPCWSTR = *const WCHAR;
type LPSTR = *mut i8;
type LPCSTR = *const i8;

const TRUE: BOOL = 1;
const FALSE: BOOL = 0;

const STD_INPUT_HANDLE: DWORD = 0xFFFFFFF6;
const STD_OUTPUT_HANDLE: DWORD = 0xFFFFFFF5;

const ERROR_SUCCESS: LONG = 0;
const ERROR_INSUFFICIENT_BUFFER: DWORD = 122;

const KEY_READ: DWORD = 0x20019;

const HKEY_LOCAL_MACHINE: HKEY = 0x80000002usize as HKEY;

const REG_SZ: DWORD = 1;

const INVALID_HANDLE_VALUE: HANDLE = -1isize as HANDLE;

const TH32CS_SNAPPROCESS: DWORD = 0x00000002;

const MAX_PATH: usize = 260;

const AF_INET: ULONG = 2;

const NO_ERROR: ULONG = 0;

const PROCESSOR_ARCHITECTURE_AMD64: WORD = 9;
const PROCESSOR_ARCHITECTURE_ARM64: WORD = 12;
const PROCESSOR_ARCHITECTURE_INTEL: WORD = 0;

const DRIVE_UNKNOWN: UINT = 0;
const DRIVE_NO_ROOT_DIR: UINT = 1;
const DRIVE_REMOVABLE: UINT = 2;
const DRIVE_FIXED: UINT = 3;
const DRIVE_REMOTE: UINT = 4;
const DRIVE_CDROM: UINT = 5;
const DRIVE_RAMDISK: UINT = 6;

#[repr(C)]
struct SYSTEM_INFO {
    wProcessorArchitecture: WORD,
    wReserved: WORD,
    dwPageSize: DWORD,
    lpMinimumApplicationAddress: LPVOID,
    lpMaximumApplicationAddress: LPVOID,
    dwActiveProcessorMask: usize,
    dwNumberOfProcessors: DWORD,
    dwProcessorType: DWORD,
    dwAllocationGranularity: DWORD,
    wProcessorLevel: WORD,
    wProcessorRevision: WORD,
}

#[repr(C)]
struct MEMORYSTATUSEX {
    dwLength: DWORD,
    dwMemoryLoad: DWORD,
    ullTotalPhys: ULONGLONG,
    ullAvailPhys: ULONGLONG,
    ullTotalPageFile: ULONGLONG,
    ullAvailPageFile: ULONGLONG,
    ullTotalVirtual: ULONGLONG,
    ullAvailVirtual: ULONGLONG,
    ullAvailExtendedVirtual: ULONGLONG,
}

#[repr(C)]
struct FILETIME {
    dwLowDateTime: DWORD,
    dwHighDateTime: DWORD,
}

#[repr(C)]
struct SYSTEMTIME {
    wYear: WORD,
    wMonth: WORD,
    wDayOfWeek: WORD,
    wDay: WORD,
    wHour: WORD,
    wMinute: WORD,
    wSecond: WORD,
    wMilliseconds: WORD,
}

#[repr(C)]
struct WIN32_FIND_DATAW {
    dwFileAttributes: DWORD,
    ftCreationTime: FILETIME,
    ftLastAccessTime: FILETIME,
    ftLastWriteTime: FILETIME,
    nFileSizeHigh: DWORD,
    nFileSizeLow: DWORD,
    dwReserved0: DWORD,
    dwReserved1: DWORD,
    cFileName: [WCHAR; MAX_PATH],
    cAlternateFileName: [WCHAR; 14],
}

#[repr(C)]
struct PROCESSENTRY32W {
    dwSize: DWORD,
    cntUsage: DWORD,
    th32ProcessID: DWORD,
    th32DefaultHeapID: usize,
    th32ModuleID: DWORD,
    cntThreads: DWORD,
    th32ParentProcessID: DWORD,
    pcPriClassBase: LONG,
    dwFlags: DWORD,
    szExeFile: [WCHAR; 260],
}

#[repr(C)]
struct IP_ADDR_STRING {
    Next: *mut IP_ADDR_STRING,
    IpAddress: [i8; 16],
    IpMask: [i8; 16],
    Context: DWORD,
}

#[repr(C)]
struct IP_ADAPTER_INFO {
    Next: *mut IP_ADAPTER_INFO,
    ComboIndex: DWORD,
    AdapterName: [i8; 260],
    Description: [i8; 132],
    AddressLength: UINT,
    Address: [BYTE; 8],
    Index: DWORD,
    Type: UINT,
    DhcpEnabled: UINT,
    CurrentIpAddress: *mut IP_ADDR_STRING,
    IpAddressList: IP_ADDR_STRING,
    GatewayList: IP_ADDR_STRING,
    DhcpServer: IP_ADDR_STRING,
    HaveWins: BOOL,
    PrimaryWinsServer: IP_ADDR_STRING,
    SecondaryWinsServer: IP_ADDR_STRING,
    LeaseObtained: DWORD,
    LeaseExpires: DWORD,
}

extern "system" {
    fn GetLastError() -> DWORD;

    fn GetStdHandle(nStdHandle: DWORD) -> HANDLE;

    fn GetConsoleMode(
        hConsoleHandle: HANDLE,
        lpMode: *mut DWORD,
    ) -> BOOL;

    fn SetConsoleMode(
        hConsoleHandle: HANDLE,
        dwMode: DWORD,
    ) -> BOOL;

    fn WriteConsoleW(
        hConsoleOutput: HANDLE,
        lpBuffer: LPCVOID,
        nNumberOfCharsToWrite: DWORD,
        lpNumberOfCharsWritten: *mut DWORD,
        lpReserved: LPVOID,
    ) -> BOOL;

    fn ReadConsoleW(
        hConsoleInput: HANDLE,
        lpBuffer: LPVOID,
        nNumberOfCharsToRead: DWORD,
        lpNumberOfCharsRead: *mut DWORD,
        pInputControl: LPVOID,
    ) -> BOOL;

    fn FillConsoleOutputCharacterW(
        hConsoleOutput: HANDLE,
        cCharacter: WCHAR,
        nLength: DWORD,
        dwWriteCoord: COORD,
        lpNumberOfCharsWritten: *mut DWORD,
    ) -> BOOL;

    fn FillConsoleOutputAttribute(
        hConsoleOutput: HANDLE,
        wAttribute: WORD,
        nLength: DWORD,
        dwWriteCoord: COORD,
        lpNumberOfAttrsWritten: *mut DWORD,
    ) -> BOOL;

    fn SetConsoleCursorPosition(
        hConsoleOutput: HANDLE,
        dwCursorPosition: COORD,
    ) -> BOOL;

    fn GetConsoleScreenBufferInfo(
        hConsoleOutput: HANDLE,
        lpConsoleScreenBufferInfo: *mut CONSOLE_SCREEN_BUFFER_INFO,
    ) -> BOOL;

    fn GetSystemInfo(
        lpSystemInfo: *mut SYSTEM_INFO,
    );

    fn GlobalMemoryStatusEx(
        lpBuffer: *mut MEMORYSTATUSEX,
    ) -> BOOL;

    fn GetComputerNameW(
        lpBuffer: LPWSTR,
        nSize: *mut DWORD,
    ) -> BOOL;

    fn GetUserNameW(
        lpBuffer: LPWSTR,
        pcbBuffer: *mut DWORD,
    ) -> BOOL;

    fn GetSystemTime(
        lpSystemTime: *mut SYSTEMTIME,
    );

    fn GetLogicalDrives() -> DWORD;

    fn GetDriveTypeW(
        lpRootPathName: LPCWSTR,
    ) -> UINT;

    fn GetDiskFreeSpaceExW(
        lpDirectoryName: LPCWSTR,
        lpFreeBytesAvailable: *mut ULONGLONG,
        lpTotalNumberOfBytes: *mut ULONGLONG,
        lpTotalNumberOfFreeBytes: *mut ULONGLONG,
    ) -> BOOL;

    fn GetVolumeInformationW(
        lpRootPathName: LPCWSTR,
        lpVolumeNameBuffer: LPWSTR,
        nVolumeNameSize: DWORD,
        lpVolumeSerialNumber: *mut DWORD,
        lpMaximumComponentLength: *mut DWORD,
        lpFileSystemFlags: *mut DWORD,
        lpFileSystemNameBuffer: LPWSTR,
        nFileSystemNameSize: DWORD,
    ) -> BOOL;

    fn RegOpenKeyExW(
        hKey: HKEY,
        lpSubKey: LPCWSTR,
        ulOptions: DWORD,
        samDesired: DWORD,
        phkResult: *mut HKEY,
    ) -> LONG;

    fn RegQueryValueExW(
        hKey: HKEY,
        lpValueName: LPCWSTR,
        lpReserved: *mut DWORD,
        lpType: *mut DWORD,
        lpData: *mut BYTE,
        lpcbData: *mut DWORD,
    ) -> LONG;

    fn RegCloseKey(
        hKey: HKEY,
    ) -> LONG;

    fn CreateToolhelp32Snapshot(
        dwFlags: DWORD,
        th32ProcessID: DWORD,
    ) -> HANDLE;

    fn Process32FirstW(
        hSnapshot: HANDLE,
        lppe: *mut PROCESSENTRY32W,
    ) -> BOOL;

    fn Process32NextW(
        hSnapshot: HANDLE,
        lppe: *mut PROCESSENTRY32W,
    ) -> BOOL;

    fn CloseHandle(
        hObject: HANDLE,
    ) -> BOOL;

    fn GetAdaptersInfo(
        pAdapterInfo: *mut IP_ADAPTER_INFO,
        pOutBufLen: *mut ULONG,
    ) -> ULONG;
}

#[repr(C)]
struct COORD {
    X: i16,
    Y: i16,
}

#[repr(C)]
struct SMALL_RECT {
    Left: i16,
    Top: i16,
    Right: i16,
    Bottom: i16,
}

#[repr(C)]
struct CONSOLE_SCREEN_BUFFER_INFO {
    dwSize: COORD,
    dwCursorPosition: COORD,
    wAttributes: WORD,
    srWindow: SMALL_RECT,
    dwMaximumWindowSize: COORD,
}

fn wide_null(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

fn wide_to_string(buf: &[u16]) -> String {
    let len = buf.iter().position(|&x| x == 0).unwrap_or(buf.len());
    String::from_utf16_lossy(&buf[..len])
}

fn write_console(text: &str) {
    unsafe {
        let handle = GetStdHandle(STD_OUTPUT_HANDLE);

        if handle.is_null() || handle == INVALID_HANDLE_VALUE {
            return;
        }

        let wide: Vec<u16> = text.encode_utf16().collect();

        let mut written: DWORD = 0;

        if !wide.is_empty() {
            WriteConsoleW(
                handle,
                wide.as_ptr() as LPCVOID,
                wide.len() as DWORD,
                &mut written,
                null_mut(),
            );
        }
    }
}

fn write_line(text: &str) {
    write_console(text);
    write_console("\r\n");
}

fn read_line() -> String {
    unsafe {
        let handle = GetStdHandle(STD_INPUT_HANDLE);

        if handle.is_null() || handle == INVALID_HANDLE_VALUE {
            return String::new();
        }

        let mut buffer = [0u16; 512];
        let mut read: DWORD = 0;

        let ok = ReadConsoleW(
            handle,
            buffer.as_mut_ptr() as LPVOID,
            (buffer.len() - 1) as DWORD,
            &mut read,
            null_mut(),
        );

        if ok == FALSE {
            return String::new();
        }

        let mut result = String::from_utf16_lossy(&buffer[..read as usize]);

        while result.ends_with('\n') || result.ends_with('\r') {
            result.pop();
        }

        result
    }
}

fn pause() {
    write_line("");
    write_console("Press ENTER to continue...");
    let _ = read_line();
}

fn clear_screen() {
    unsafe {
        let handle = GetStdHandle(STD_OUTPUT_HANDLE);

        if handle.is_null() || handle == INVALID_HANDLE_VALUE {
            return;
        }

        let mut info: CONSOLE_SCREEN_BUFFER_INFO = zeroed();

        if GetConsoleScreenBufferInfo(handle, &mut info) == FALSE {
            return;
        }

        let width = info.dwSize.X as u32;
        let height = info.dwSize.Y as u32;
        let cells = width.saturating_mul(height);

        let origin = COORD { X: 0, Y: 0 };

        let mut written: DWORD = 0;

        FillConsoleOutputCharacterW(
            handle,
            b' ' as u16,
            cells,
            origin,
            &mut written,
        );

        FillConsoleOutputAttribute(
            handle,
            info.wAttributes,
            cells,
            origin,
            &mut written,
        );

        SetConsoleCursorPosition(handle, origin);
    }
}

fn get_error() -> DWORD {
    unsafe { GetLastError() }
}

fn bytes_to_gb(bytes: u64) -> f64 {
    bytes as f64 / 1024.0 / 1024.0 / 1024.0
}

fn print_header(title: &str) {
    clear_screen();

    write_line("============================================================");
    write_line(title);
    write_line("============================================================");
    write_line("");
}

fn system_information() {
    print_header("System Information");

    unsafe {
        let mut info: SYSTEM_INFO = zeroed();
        GetSystemInfo(&mut info);

        let arch = match info.wProcessorArchitecture {
            PROCESSOR_ARCHITECTURE_AMD64 => "x64 / AMD64",
            PROCESSOR_ARCHITECTURE_ARM64 => "ARM64",
            PROCESSOR_ARCHITECTURE_INTEL => "x86 / Intel",
            _ => "Unknown",
        };

        write_line(&format!("Architecture       : {}", arch));
        write_line(&format!(
            "Logical processors  : {}",
            info.dwNumberOfProcessors
        ));
        write_line(&format!(
            "Page size           : {} bytes",
            info.dwPageSize
        ));
        write_line(&format!(
            "Allocation gran.    : {} bytes",
            info.dwAllocationGranularity
        ));
        write_line(&format!(
            "Processor type      : {}",
            info.dwProcessorType
        ));

        let mut computer = [0u16; 256];
        let mut computer_len = computer.len() as DWORD;

        if GetComputerNameW(
            computer.as_mut_ptr(),
            &mut computer_len,
        ) != FALSE {
            write_line(&format!(
                "Computer name       : {}",
                wide_to_string(&computer)
            ));
        } else {
            write_line(&format!(
                "Computer name       : <error {}>",
                get_error()
            ));
        }

        let mut username = [0u16; 256];
        let mut username_len = username.len() as DWORD;

        if GetUserNameW(
            username.as_mut_ptr(),
            &mut username_len,
        ) != FALSE {
            write_line(&format!(
                "Current user        : {}",
                wide_to_string(&username)
            ));
        } else {
            write_line(&format!(
                "Current user        : <error {}>",
                get_error()
            ));
        }

        let mut st: SYSTEMTIME = zeroed();
        GetSystemTime(&mut st);

        write_line(&format!(
            "UTC time            : {:04}-{:02}-{:02} {:02}:{:02}:{:02}",
            st.wYear,
            st.wMonth,
            st.wDay,
            st.wHour,
            st.wMinute,
            st.wSecond
        ));
    }

    pause();
}

fn cpu_information() {
    print_header("CPU Information");

    unsafe {
        let mut info: SYSTEM_INFO = zeroed();
        GetSystemInfo(&mut info);

        write_line(&format!(
            "Logical processors  : {}",
            info.dwNumberOfProcessors
        ));

        write_line(&format!(
            "Processor type      : {}",
            info.dwProcessorType
        ));

        write_line(&format!(
            "Processor level     : {}",
            info.wProcessorLevel
        ));

        write_line(&format!(
            "Processor revision  : {}",
            info.wProcessorRevision
        ));

        let key_path = wide_null(
            "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0"
        );

        let value_name = wide_null("ProcessorNameString");

        let mut key: HKEY = null_mut();

        let result = RegOpenKeyExW(
            HKEY_LOCAL_MACHINE,
            key_path.as_ptr(),
            0,
            KEY_READ,
            &mut key,
        );

        if result == ERROR_SUCCESS {
            let mut data_type: DWORD = 0;
            let mut data_size: DWORD = 0;

            let query_size = RegQueryValueExW(
                key,
                value_name.as_ptr(),
                null_mut(),
                &mut data_type,
                null_mut(),
                &mut data_size,
            );

            if query_size == ERROR_SUCCESS && data_size > 0 {
                let count = ((data_size as usize) + 1) / 2;
                let mut data = vec![0u16; count];

                let mut size = data_size;

                let query = RegQueryValueExW(
                    key,
                    value_name.as_ptr(),
                    null_mut(),
                    &mut data_type,
                    data.as_mut_ptr() as *mut BYTE,
                    &mut size,
                );

                if query == ERROR_SUCCESS && data_type == REG_SZ {
                    write_line(&format!(
                        "CPU name            : {}",
                        wide_to_string(&data)
                    ));
                } else {
                    write_line("CPU name            : <unable to read>");
                }
            } else {
                write_line("CPU name            : <not available>");
            }

            RegCloseKey(key);
        } else {
            write_line(&format!(
                "Registry error      : {}",
                result
            ));
        }
    }

    pause();
}

fn memory_information() {
    print_header("Memory Information");

    unsafe {
        let mut mem: MEMORYSTATUSEX = zeroed();

        mem.dwLength = size_of::<MEMORYSTATUSEX>() as DWORD;

        if GlobalMemoryStatusEx(&mut mem) == FALSE {
            write_line(&format!(
                "GlobalMemoryStatusEx failed: {}",
                get_error()
            ));

            pause();
            return;
        }

        let total = mem.ullTotalPhys;
        let available = mem.ullAvailPhys;
        let used = total.saturating_sub(available);

        write_line(&format!(
            "Memory load         : {}%",
            mem.dwMemoryLoad
        ));

        write_line(&format!(
            "Total physical RAM  : {:.2} GB",
            bytes_to_gb(total)
        ));

        write_line(&format!(
            "Available RAM       : {:.2} GB",
            bytes_to_gb(available)
        ));

        write_line(&format!(
            "Used RAM            : {:.2} GB",
            bytes_to_gb(used)
        ));

        write_line("");

        write_line(&format!(
            "Total page file     : {:.2} GB",
            bytes_to_gb(mem.ullTotalPageFile)
        ));

        write_line(&format!(
            "Available page file : {:.2} GB",
            bytes_to_gb(mem.ullAvailPageFile)
        ));

        write_line("");

        write_line(&format!(
            "Total virtual       : {:.2} GB",
            bytes_to_gb(mem.ullTotalVirtual)
        ));

        write_line(&format!(
            "Available virtual   : {:.2} GB",
            bytes_to_gb(mem.ullAvailVirtual)
        ));
    }

    pause();
}

fn drive_type_name(t: UINT) -> &'static str {
    match t {
        DRIVE_UNKNOWN => "Unknown",
        DRIVE_NO_ROOT_DIR => "No root",
        DRIVE_REMOVABLE => "Removable",
        DRIVE_FIXED => "Fixed",
        DRIVE_REMOTE => "Network",
        DRIVE_CDROM => "CD-ROM",
        DRIVE_RAMDISK => "RAM Disk",
        _ => "Other",
    }
}

fn disk_information() {
    print_header("Disk Information");

    unsafe {
        let drives = GetLogicalDrives();

        if drives == 0 {
            write_line(&format!(
                "GetLogicalDrives failed: {}",
                get_error()
            ));

            pause();
            return;
        }

        for i in 0..26 {
            if (drives & (1u32 << i)) == 0 {
                continue;
            }

            let letter = (b'A' + i as u8) as char;

            let root_string = format!("{}:\\", letter);
            let root = wide_null(&root_string);

            let drive_type = GetDriveTypeW(root.as_ptr());

            if drive_type == DRIVE_NO_ROOT_DIR {
                continue;
            }

            let mut free_available: ULONGLONG = 0;
            let mut total: ULONGLONG = 0;
            let mut free_total: ULONGLONG = 0;

            let mut volume_name = [0u16; 256];
            let mut fs_name = [0u16; 256];

            let mut serial: DWORD = 0;
            let mut max_component: DWORD = 0;
            let mut fs_flags: DWORD = 0;

            let disk_ok = GetDiskFreeSpaceExW(
                root.as_ptr(),
                &mut free_available,
                &mut total,
                &mut free_total,
            );

            let volume_ok = GetVolumeInformationW(
                root.as_ptr(),
                volume_name.as_mut_ptr(),
                volume_name.len() as DWORD,
                &mut serial,
                &mut max_component,
                &mut fs_flags,
                fs_name.as_mut_ptr(),
                fs_name.len() as DWORD,
            );

            write_line(&format!(
                "{}:  {}",
                letter,
                drive_type_name(drive_type)
            ));

            if disk_ok != FALSE {
                let used = total.saturating_sub(free_total);

                write_line(&format!(
                    "    Total : {:.2} GB",
                    bytes_to_gb(total)
                ));

                write_line(&format!(
                    "    Used  : {:.2} GB",
                    bytes_to_gb(used)
                ));

                write_line(&format!(
                    "    Free  : {:.2} GB",
                    bytes_to_gb(free_total)
                ));
            } else {
                write_line(&format!(
                    "    Disk information error: {}",
                    get_error()
                ));
            }

            if volume_ok != FALSE {
                let volume = wide_to_string(&volume_name);
                let fs = wide_to_string(&fs_name);

                write_line(&format!(
                    "    Volume: {}",
                    if volume.is_empty() {
                        "<none>"
                    } else {
                        &volume
                    }
                ));

                write_line(&format!(
                    "    FS    : {}",
                    if fs.is_empty() {
                        "<unknown>"
                    } else {
                        &fs
                    }
                ));

                write_line(&format!(
                    "    Serial: {:08X}",
                    serial
                ));
            }

            write_line("");
        }
    }

    pause();
}

unsafe fn read_c_string(buf: &[i8]) -> String {
    let mut len = 0usize;

    while len < buf.len() && buf[len] != 0 {
        len += 1;
    }

    let bytes = std::slice::from_raw_parts(
        buf.as_ptr() as *const u8,
        len,
    );

    String::from_utf8_lossy(bytes).to_string()
}

unsafe fn print_ip_address(addr: *const IP_ADDR_STRING) {
    if addr.is_null() {
        return;
    }

    let mut current = addr;

    while !current.is_null() {
        let item = &*current;

        let ip = read_c_string(&item.IpAddress);

        if !ip.is_empty() {
            write_line(&format!("    IPv4    : {}", ip));
        }

        current = item.Next;
    }
}

fn network_information() {
    print_header("Network Information");

    unsafe {
        let mut size: ULONG = 0;

        let result = GetAdaptersInfo(
            null_mut(),
            &mut size,
        );

        if result != ERROR_INSUFFICIENT_BUFFER {
            write_line(&format!(
                "GetAdaptersInfo failed: {}",
                result
            ));

            pause();
            return;
        }

        let mut buffer = vec![0u8; size as usize];

        let adapter = buffer.as_mut_ptr() as *mut IP_ADAPTER_INFO;

        let result = GetAdaptersInfo(
            adapter,
            &mut size,
        );

        if result != NO_ERROR {
            write_line(&format!(
                "GetAdaptersInfo failed: {}",
                result
            ));

            pause();
            return;
        }

        let mut current = adapter;

        while !current.is_null() {
            let a = &*current;

            let description = read_c_string(&a.Description);

            write_line(&format!(
                "Adapter: {}",
                if description.is_empty() {
                    "<unknown>"
                } else {
                    &description
                }
            ));

            write_line(&format!(
                "    Index   : {}",
                a.Index
            ));

            write_line(&format!(
                "    Type    : {}",
                a.Type
            ));

            if a.AddressLength > 0 {
                let count = a.AddressLength.min(8) as usize;

                let mut mac = String::new();

                for i in 0..count {
                    if i != 0 {
                        mac.push(':');
                    }

                    mac.push_str(&format!(
                        "{:02X}",
                        a.Address[i]
                    ));
                }

                write_line(&format!(
                    "    MAC     : {}",
                    mac
                ));
            }

            print_ip_address(
                &a.IpAddressList as *const IP_ADDR_STRING
            );

            write_line("");

            current = a.Next;
        }
    }

    pause();
}

fn process_information() {
    print_header("Process List");

    unsafe {
        let snapshot = CreateToolhelp32Snapshot(
            TH32CS_SNAPPROCESS,
            0,
        );

        if snapshot == INVALID_HANDLE_VALUE {
            write_line(&format!(
                "CreateToolhelp32Snapshot failed: {}",
                get_error()
            ));

            pause();
            return;
        }

        let mut entry: PROCESSENTRY32W = zeroed();

        entry.dwSize = size_of::<PROCESSENTRY32W>() as DWORD;

        if Process32FirstW(
            snapshot,
            &mut entry,
        ) == FALSE {
            write_line(&format!(
                "Process32FirstW failed: {}",
                get_error()
            ));

            CloseHandle(snapshot);

            pause();
            return;
        }

        write_line(
            "PID       Parent    Threads   Executable"
        );

        write_line(
            "------------------------------------------------------------"
        );

        loop {
            let name = wide_to_string(
                &entry.szExeFile
            );

            write_line(&format!(
                "{:<9} {:<9} {:<9} {}",
                entry.th32ProcessID,
                entry.th32ParentProcessID,
                entry.cntThreads,
                name
            ));

            if Process32NextW(
                snapshot,
                &mut entry,
            ) == FALSE {
                break;
            }
        }

        CloseHandle(snapshot);
    }

    pause();
}

fn windows_information() {
    print_header("Windows Information");

    unsafe {
        let key_path = wide_null(
            "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion"
        );

        let values = [
            "ProductName",
            "DisplayVersion",
            "CurrentBuild",
            "CurrentBuildNumber",
            "EditionID",
            "InstallationType",
        ];

        let mut key: HKEY = null_mut();

        let result = RegOpenKeyExW(
            HKEY_LOCAL_MACHINE,
            key_path.as_ptr(),
            0,
            KEY_READ,
            &mut key,
        );

        if result != ERROR_SUCCESS {
            write_line(&format!(
                "RegOpenKeyExW failed: {}",
                result
            ));

            pause();
            return;
        }

        for name in values.iter() {
            let value_name = wide_null(name);

            let mut data_type: DWORD = 0;
            let mut data_size: DWORD = 0;

            let result = RegQueryValueExW(
                key,
                value_name.as_ptr(),
                null_mut(),
                &mut data_type,
                null_mut(),
                &mut data_size,
            );

            if result != ERROR_SUCCESS || data_size == 0 {
                continue;
            }

            let count = ((data_size as usize) + 1) / 2;
            let mut data = vec![0u16; count];

            let mut size = data_size;

            let result = RegQueryValueExW(
                key,
                value_name.as_ptr(),
                null_mut(),
                &mut data_type,
                data.as_mut_ptr() as *mut BYTE,
                &mut size,
            );

            if result == ERROR_SUCCESS && data_type == REG_SZ {
                write_line(&format!(
                    "{:<20}: {}",
                    name,
                    wide_to_string(&data)
                ));
            }
        }

        RegCloseKey(key);
    }

    pause();
}

fn refresh() {
    print_header("Refreshing");

    write_line("Windows API information will be refreshed.");
    write_line("");

    unsafe {
        let mut info: SYSTEM_INFO = zeroed();
        GetSystemInfo(&mut info);

        let mut mem: MEMORYSTATUSEX = zeroed();
        mem.dwLength = size_of::<MEMORYSTATUSEX>() as DWORD;

        if GlobalMemoryStatusEx(&mut mem) != FALSE {
            write_line(&format!(
                "CPU cores : {}",
                info.dwNumberOfProcessors
            ));

            write_line(&format!(
                "RAM       : {:.2} GB",
                bytes_to_gb(mem.ullTotalPhys)
            ));

            write_line(&format!(
                "RAM free  : {:.2} GB",
                bytes_to_gb(mem.ullAvailPhys)
            ));
        }
    }

    pause();
}

fn print_menu() {
    clear_screen();

    write_line("============================================================");
    write_line("                 RUST CONTROL UTILITY");
    write_line("============================================================");
    write_line("");
    write_line("  [1] System Information");
    write_line("  [2] CPU Information");
    write_line("  [3] Memory Information");
    write_line("  [4] Disk Information");
    write_line("  [5] Network Information");
    write_line("  [6] Process List");
    write_line("  [7] Windows Information");
    write_line("  [8] Refresh");
    write_line("  [0] Exit");
    write_line("");
    write_console("Select: ");
}

fn main() {
    loop {
        print_menu();

        let choice = read_line();

        match choice.trim() {
            "1" => system_information(),
            "2" => cpu_information(),
            "3" => memory_information(),
            "4" => disk_information(),
            "5" => network_information(),
            "6" => process_information(),
            "7" => windows_information(),
            "8" => refresh(),
            "0" => {
                clear_screen();
                write_line("Rust Control Utility");
                write_line("Goodbye.");
                break;
            }
            _ => {
                write_line("");
                write_line("Invalid selection.");
                pause();
            }
        }
    }
}
