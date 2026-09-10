# escape=`

FROM mcr.microsoft.com/windows/servercore:ltsc2022

SHELL ["cmd", "/S", "/C"]

ENV RUSTUP_HOME=C:\rustup
ENV CARGO_HOME=C:\cargo
ENV PATH=C:\cargo\bin;C:\Windows\System32;C:\Windows

WORKDIR C:\build

# ------------------------------------------------------------
# Install Visual Studio Build Tools 2022
# ------------------------------------------------------------

ADD https://aka.ms/vs/17/release/vs_buildtools.exe C:\vs_buildtools.exe

RUN C:\vs_buildtools.exe --quiet --wait --norestart --nocache `
    --installPath C:\BuildTools `
    --add Microsoft.VisualStudio.Workload.VCTools `
    --add Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
    --add Microsoft.VisualStudio.Component.Windows11SDK.22000 `
    || IF "%ERRORLEVEL%"=="3010" EXIT 0

RUN del C:\vs_buildtools.exe

# ------------------------------------------------------------
# Download rustup
# ------------------------------------------------------------

ADD https://static.rust-lang.org/rustup/archive/1.24.3/x86_64-pc-windows-msvc/rustup-init.exe C:\rustup-init.exe

# ------------------------------------------------------------
# Initialize rustup without installing stable
# ------------------------------------------------------------

RUN C:\rustup-init.exe -y --default-toolchain none --profile minimal

RUN del C:\rustup-init.exe

# ------------------------------------------------------------
# IMPORTANT:
# Rust 1.60.1 MSVC is installed from the historical
# Rust distribution archive rather than the current channel
# metadata.
# ------------------------------------------------------------

ADD https://static.rust-lang.org/dist/2022-04-07/rust-1.60.1-x86_64-pc-windows-msvc.tar.gz C:\rust.tar.gz

RUN mkdir C:\rust-source

RUN tar -xf C:\rust.tar.gz -C C:\rust-source

RUN C:\rust-source\rust-1.60.1-x86_64-pc-windows-msvc\install.bat --prefix=C:\Rust-1.60.1

RUN del C:\rust.tar.gz

# ------------------------------------------------------------
# Environment
# ------------------------------------------------------------

ENV PATH=C:\Rust-1.60.1\bin;C:\BuildTools\VC\Tools\MSVC\14.3.XXXXX\bin\Hostx64\x64;C:\Windows\System32;C:\Windows

# ------------------------------------------------------------
# Verify
# ------------------------------------------------------------

RUN C:\Rust-1.60.1\bin\rustc.exe --version

WORKDIR C:\build

CMD ["cmd.exe"]
