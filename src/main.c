/*
 * main.c - single-file hardware-only video player
 *
 *  - strict C89 / GNU dialect (gnu89)
 *  - FFmpeg 4.0.0 for demux + decode
 *  - hardware decoding only (no software fallback)
 *  - self-implemented base layer, own code does NOT call libc
 *    (no stdio.h / stdlib.h / string.h)
 *  - heap via av_malloc / av_free only
 *  - cross platform: Win32 / X11
 *
 * build (MSVC):
 *   cl /TC /O2 /MD main.c ^
 *      /I ffmpeg\include ^
 *      /link /LIBPATH:ffmpeg\lib ^
 *      avformat.lib avcodec.lib avutil.lib swscale.lib ^
 *      user32.lib gdi32.lib
 *
 * build (MinGW):
 *   gcc -std=gnu89 -O2 main.c -o player.exe ^
 *      -Iffmpeg/include -Lffmpeg/lib ^
 *      -lavformat -lavcodec -lavutil -lswscale ^
 *      -lgdi32 -luser32
 *
 * build (Linux):
 *   gcc -std=gnu89 -O2 main.c -o player \
 *      -lavformat -lavcodec -lavutil -lswscale -lX11
 */

/* =========================================================
 * 0. platform detection
 * ========================================================= */

#if defined(_WIN32)
    #define MY_PLATFORM_WIN32 1
#elif defined(__APPLE__)
    #define MY_PLATFORM_MACOS 1
#else
    #define MY_PLATFORM_X11   1
#endif

/* =========================================================
 * 1. FFmpeg headers (allowed)
 * ========================================================= */

#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/imgutils.h>
#include <libavutil/hwcontext.h>
#include <libavutil/pixfmt.h>
#include <libavutil/pixdesc.h>
#include <libavutil/mem.h>
#include <libswscale/swscale.h>

/* =========================================================
 * 2. OS native headers
 * ========================================================= */

#if MY_PLATFORM_WIN32
    #include <windows.h>
#else
    #include <unistd.h>
    #if MY_PLATFORM_X11
        #include <X11/Xlib.h>
        #include <X11/Xutil.h>
    #endif
#endif

/* =========================================================
 * 3. self-implemented base layer
 *    (no stdio.h / stdlib.h / string.h)
 * ========================================================= */

typedef unsigned char       my_u8;
typedef signed char         my_s8;
typedef unsigned short      my_u16;
typedef signed short        my_s16;
typedef unsigned int        my_u32;
typedef signed int          my_s32;
typedef unsigned long       my_usize;

#define MY_OK             0
#define MY_ERR_NOMEM     -1
#define MY_ERR_PARAM     -2
#define MY_ERR_RANGE     -3
#define MY_ERR_OVERFLOW  -4
#define MY_ERR_IO        -5

/* ---- memory ---- */

static void *my_memcpy(void *dst, const void *src, my_usize n)
{
    my_u8       *d;
    const my_u8 *s;

    if (dst == NULL || src == NULL) return NULL;
    if (n == 0) return dst;

    d = (my_u8 *)dst;
    s = (const my_u8 *)src;
    while (n >= 8) {
        d[0]=s[0]; d[1]=s[1]; d[2]=s[2]; d[3]=s[3];
        d[4]=s[4]; d[5]=s[5]; d[6]=s[6]; d[7]=s[7];
        d += 8; s += 8; n -= 8;
    }
    while (n > 0) { *d++ = *s++; n--; }
    return dst;
}

static void *my_memset(void *dst, int c, my_usize n)
{
    my_u8 *d;

    if (dst == NULL) return NULL;
    if (n == 0) return dst;

    d = (my_u8 *)dst;
    while (n >= 8) {
        d[0]=(my_u8)c; d[1]=(my_u8)c; d[2]=(my_u8)c; d[3]=(my_u8)c;
        d[4]=(my_u8)c; d[5]=(my_u8)c; d[6]=(my_u8)c; d[7]=(my_u8)c;
        d += 8; n -= 8;
    }
    while (n > 0) { *d++ = (my_u8)c; n--; }
    return dst;
}

static int my_mul_overflow_u(my_usize a, my_usize b, my_usize *out)
{
    if (out == NULL) return MY_ERR_PARAM;
    if (a == 0 || b == 0) { *out = 0; return MY_OK; }
    if (a > (~(my_usize)0) / b) return MY_ERR_OVERFLOW;
    *out = a * b;
    return MY_OK;
}

static void *my_alloc(my_usize size)
{
    if (size == 0) return NULL;
    return av_malloc(size);
}

static void *my_calloc(my_usize count, my_usize size)
{
    my_usize total;
    void    *p;

    if (count == 0 || size == 0) return NULL;
    if (my_mul_overflow_u(count, size, &total) != MY_OK) return NULL;
    p = av_malloc(total);
    if (p == NULL) return NULL;
    my_memset(p, 0, total);
    return p;
}

static void my_free(void *ptr)
{
    if (ptr != NULL) av_free(ptr);
}

/* ---- strings ---- */

static my_usize my_strlen(const char *s)
{
    my_usize n;
    if (s == NULL) return 0;
    n = 0;
    while (s[n] != '\0') n++;
    return n;
}

/* ---- output ---- */

#if MY_PLATFORM_WIN32

static void my_print(const char *s)
{
    HANDLE   h;
    DWORD    written;
    my_usize len;

    if (s == NULL) return;
    h = GetStdHandle(STD_ERROR_HANDLE);
    if (h == INVALID_HANDLE_VALUE || h == NULL) return;
    len = my_strlen(s);
    if (len == 0) return;
    if (len > 0x7FFFFFFF) len = 0x7FFFFFFF;
    WriteFile(h, s, (DWORD)len, &written, NULL);
}

static void my_print_int(int v)
{
    char         buf[16];
    int          i;
    int          neg;
    unsigned int u;
    int          a, b;

    i = 0; neg = 0;
    if (v < 0) {
        neg = 1;
        u = (unsigned int)(-(v + 1)) + 1u;
    } else {
        u = (unsigned int)v;
    }
    if (u == 0) { buf[i++] = '0'; }
    else {
        while (u > 0 && i < (int)sizeof(buf) - 1) {
            buf[i++] = (char)('0' + (u % 10u));
            u /= 10u;
        }
    }
    if (neg && i < (int)sizeof(buf) - 1) buf[i++] = '-';
    a = 0; b = i - 1;
    while (a < b) { char t = buf[a]; buf[a] = buf[b]; buf[b] = t; a++; b--; }
    buf[i] = '\0';
    my_print(buf);
}

#else /* POSIX */

static void my_print(const char *s)
{
    my_usize len;

    if (s == NULL) return;
    len = my_strlen(s);
    if (len == 0) return;
    while (len > 0) {
        ssize_t w = write(2, s, len);
        if (w <= 0) break;
        s += w;
        len -= (my_usize)w;
    }
}

static void my_print_int(int v)
{
    char         buf[16];
    int          i;
    int          neg;
    unsigned int u;
    int          a, b;

    i = 0; neg = 0;
    if (v < 0) {
        neg = 1;
        u = (unsigned int)(-(v + 1)) + 1u;
    } else {
        u = (unsigned int)v;
    }
    if (u == 0) { buf[i++] = '0'; }
    else {
        while (u > 0 && i < (int)sizeof(buf) - 1) {
            buf[i++] = (char)('0' + (u % 10u));
            u /= 10u;
        }
    }
    if (neg && i < (int)sizeof(buf) - 1) buf[i++] = '-';
    a = 0; b = i - 1;
    while (a < b) { char t = buf[a]; buf[a] = buf[b]; buf[b] = t; a++; b--; }
    buf[i] = '\0';
    my_print(buf);
}

#endif

/* =========================================================
 * 4. FFmpeg hardware helpers
 * ========================================================= */

static enum AVPixelFormat g_hw_pix_fmt = AV_PIX_FMT_NONE;

static enum AVPixelFormat get_hw_format(AVCodecContext *ctx,
                                        const enum AVPixelFormat *pix_fmts)
{
    const enum AVPixelFormat *p;
    (void)ctx;
    for (p = pix_fmts; *p != AV_PIX_FMT_NONE; p++) {
        if (*p == g_hw_pix_fmt) return *p;
    }
    return AV_PIX_FMT_NONE;
}

static enum AVHWDeviceType pick_hw_device(void)
{
#if MY_PLATFORM_WIN32
    return AV_HWDEVICE_TYPE_D3D11VA;
#elif MY_PLATFORM_MACOS
    return AV_HWDEVICE_TYPE_VIDEOTOOLBOX;
#else
    return AV_HWDEVICE_TYPE_VAAPI;
#endif
}

static enum AVPixelFormat hw_pix_fmt_of(enum AVHWDeviceType t)
{
    switch (t) {
    case AV_HWDEVICE_TYPE_D3D11VA:      return AV_PIX_FMT_D3D11;
    case AV_HWDEVICE_TYPE_DXVA2:        return AV_PIX_FMT_DXVA2_VLD;
    case AV_HWDEVICE_TYPE_VAAPI:        return AV_PIX_FMT_VAAPI;
    case AV_HWDEVICE_TYPE_VDPAU:        return AV_PIX_FMT_VDPAU;
    case AV_HWDEVICE_TYPE_VIDEOTOOLBOX: return AV_PIX_FMT_VIDEOTOOLBOX;
    case AV_HWDEVICE_TYPE_CUDA:         return AV_PIX_FMT_CUDA;
    default:                            return AV_PIX_FMT_NONE;
    }
}

/* =========================================================
 * 5. minimal window + present layer
 * ========================================================= */

#if MY_PLATFORM_WIN32

typedef struct {
    HWND   hwnd;
    HDC    hdc;
    int    width;
    int    height;
    int    quit;
} MyWindow;

static LRESULT CALLBACK my_wnd_proc(HWND hwnd, UINT msg,
                                    WPARAM wp, LPARAM lp)
{
    (void)wp; (void)lp;
    if (msg == WM_DESTROY) {
        PostQuitMessage(0);
        return 0;
    }
    if (msg == WM_KEYDOWN) {
        if (wp == VK_ESCAPE) {
            PostQuitMessage(0);
            return 0;
        }
    }
    return DefWindowProcA(hwnd, msg, wp, lp);
}

static int my_window_create(MyWindow *w, const char *title,
                            int width, int height)
{
    WNDCLASSA   wc;
    RECT        rc;
    HINSTANCE   hinst;

    if (w == NULL || title == NULL) return MY_ERR_PARAM;
    if (width <= 0 || height <= 0)  return MY_ERR_PARAM;

    my_memset(&wc, 0, sizeof(wc));
    my_memset(&rc, 0, sizeof(rc));

    hinst = GetModuleHandleA(NULL);

    wc.style         = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc   = my_wnd_proc;
    wc.hInstance     = hinst;
    wc.hCursor       = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
    wc.lpszClassName = "MyPlayerWnd";

    if (!RegisterClassA(&wc)) return MY_ERR_IO;

    rc.left = 0; rc.top = 0; rc.right = width; rc.bottom = height;
    AdjustWindowRect(&rc, WS_OVERLAPPEDWINDOW, FALSE);

    w->hwnd = CreateWindowExA(
        0, "MyPlayerWnd", title,
        WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT, CW_USEDEFAULT,
        rc.right - rc.left, rc.bottom - rc.top,
        NULL, NULL, hinst, NULL);

    if (w->hwnd == NULL) return MY_ERR_IO;

    w->hdc    = GetDC(w->hwnd);
    w->width  = width;
    w->height = height;
    w->quit   = 0;

    ShowWindow(w->hwnd, SW_SHOW);
    UpdateWindow(w->hwnd);
    return MY_OK;
}

static void my_window_destroy(MyWindow *w)
{
    if (w == NULL) return;
    if (w->hdc != NULL && w->hwnd != NULL) {
        ReleaseDC(w->hwnd, w->hdc);
        w->hdc = NULL;
    }
    if (w->hwnd != NULL) {
        DestroyWindow(w->hwnd);
        w->hwnd = NULL;
    }
}

static int my_window_pump(MyWindow *w)
{
    MSG msg;
    if (w == NULL) return 0;
    while (PeekMessageA(&msg, NULL, 0, 0, PM_REMOVE)) {
        if (msg.message == WM_QUIT) {
            w->quit = 1;
            return 1;
        }
        TranslateMessage(&msg);
        DispatchMessageA(&msg);
    }
    return w->quit;
}

/* present one RGB24 buffer (top-down) */
static int my_window_present_rgb24(MyWindow *w,
                                   const my_u8 *rgb,
                                   int width, int height,
                                   int stride)
{
    BITMAPINFO  bi;
    int         dst_w, dst_h;

    if (w == NULL || rgb == NULL) return MY_ERR_PARAM;
    if (w->hdc == NULL || w->hwnd == NULL) return MY_ERR_IO;
    if (stride <= 0) return MY_ERR_PARAM;

    my_memset(&bi, 0, sizeof(bi));
    bi.bmiHeader.biSize        = sizeof(BITMAPINFOHEADER);
    bi.bmiHeader.biWidth       = width;
    bi.bmiHeader.biHeight      = -height;    /* negative = top-down */
    bi.bmiHeader.biPlanes      = 1;
    bi.bmiHeader.biBitCount    = 24;
    bi.bmiHeader.biCompression = BI_RGB;

    dst_w = w->width;
    dst_h = w->height;

    SetStretchBltMode(w->hdc, COLORONCOLOR);
    StretchDIBits(w->hdc,
                  0, 0, dst_w, dst_h,
                  0, 0, width, height,
                  rgb, &bi, DIB_RGB_COLORS, SRCCOPY);
    return MY_OK;
}

#elif MY_PLATFORM_X11

typedef struct {
    Display *dpy;
    Window   win;
    GC       gc;
    XImage  *img;
    Visual  *visual;
    int      depth;
    int      screen;
    int      width;
    int      height;
    int      quit;
    Atom     wm_delete;
} MyWindow;

static int my_window_create(MyWindow *w, const char *title,
                            int width, int height)
{
    int        screen;
    Display   *dpy;
    Window     root;
    XSetWindowAttributes attrs;
    XSizeHints hints;

    if (w == NULL || title == NULL) return MY_ERR_PARAM;
    if (width <= 0 || height <= 0)  return MY_ERR_PARAM;

    my_memset(w, 0, sizeof(*w));

    dpy = XOpenDisplay(NULL);
    if (dpy == NULL) return MY_ERR_IO;

    screen = DefaultScreen(dpy);
    root   = RootWindow(dpy, screen);

    my_memset(&attrs, 0, sizeof(attrs));
    attrs.background_pixel = BlackPixel(dpy, screen);
    attrs.event_mask = ExposureMask | KeyPressMask | StructureNotifyMask;

    w->dpy    = dpy;
    w->screen = screen;
    w->depth  = DefaultDepth(dpy, screen);
    w->visual = DefaultVisual(dpy, screen);

    w->win = XCreateWindow(dpy, root,
                           0, 0, (unsigned)width, (unsigned)height, 0,
                           w->depth, InputOutput, w->visual,
                           CWBackPixel | CWEventMask, &attrs);
    if (w->win == 0) {
        XCloseDisplay(dpy);
        w->dpy = NULL;
        return MY_ERR_IO;
    }

    XStoreName(dpy, w->win, title);

    my_memset(&hints, 0, sizeof(hints));
    hints.flags      = PSize;
    hints.width      = width;
    hints.height     = height;
    XSetWMNormalHints(dpy, w->win, &hints);

    w->wm_delete = XInternAtom(dpy, "WM_DELETE_WINDOW", False);
    XSetWMProtocols(dpy, w->win, &w->wm_delete, 1);

    w->gc = XCreateGC(dpy, w->win, 0, NULL);
    if (w->gc == NULL) {
        XDestroyWindow(dpy, w->win);
        XCloseDisplay(dpy);
        w->dpy = NULL; w->win = 0;
        return MY_ERR_IO;
    }

    XMapWindow(dpy, w->win);
    XFlush(dpy);

    w->img = XCreateImage(dpy, w->visual, (unsigned)w->depth,
                          ZPixmap, 0, NULL,
                          (unsigned)width, (unsigned)height,
                          32, 0);
    if (w->img == NULL) {
        XFreeGC(dpy, w->gc);
        XDestroyWindow(dpy, w->win);
        XCloseDisplay(dpy);
        w->dpy = NULL; w->win = 0;
        return MY_ERR_IO;
    }

    {
        my_usize bytes;
        if (my_mul_overflow_u((my_usize)w->img->bytes_per_line,
                              (my_usize)height, &bytes) != MY_OK) {
            XDestroyImage(w->img);
            XFreeGC(dpy, w->gc);
            XDestroyWindow(dpy, w->win);
            XCloseDisplay(dpy);
            w->dpy = NULL; w->win = 0; w->img = NULL;
            return MY_ERR_OVERFLOW;
        }
        w->img->data = (char *)my_alloc(bytes);
        if (w->img->data == NULL) {
            XDestroyImage(w->img);
            XFreeGC(dpy, w->gc);
            XDestroyWindow(dpy, w->win);
            XCloseDisplay(dpy);
            w->dpy = NULL; w->win = 0; w->img = NULL;
            return MY_ERR_NOMEM;
        }
        my_memset(w->img->data, 0, bytes);
    }

    w->width  = width;
    w->height = height;
    w->quit   = 0;
    return MY_OK;
}

static void my_window_destroy(MyWindow *w)
{
    if (w == NULL) return;
    if (w->dpy != NULL) {
        if (w->img != NULL) {
            /* XDestroyImage frees img->data */
            XDestroyImage(w->img);
            w->img = NULL;
        }
        if (w->gc != NULL) {
            XFreeGC(w->dpy, w->gc);
            w->gc = NULL;
        }
        if (w->win != 0) {
            XDestroyWindow(w->dpy, w->win);
            w->win = 0;
        }
        XCloseDisplay(w->dpy);
        w->dpy = NULL;
    }
}

static int my_window_pump(MyWindow *w)
{
    if (w == NULL || w->dpy == NULL) return 0;

    while (XPending(w->dpy) > 0) {
        XEvent ev;
        XNextEvent(w->dpy, &ev);

        if (ev.type == ClientMessage) {
            if ((Atom)ev.xclient.data.l[0] == w->wm_delete) {
                w->quit = 1;
                return 1;
            }
        } else if (ev.type == KeyPress) {
            KeySym ks = XLookupKeysym(&ev.xkey, 0);
            if (ks == XK_Escape) {
                w->quit = 1;
                return 1;
            }
        }
    }
    return w->quit;
}

/* present RGB24 into X11 window.
 * X11 typically wants BGRX / BGRA; we convert here. */
static int my_window_present_rgb24(MyWindow *w,
                                   const my_u8 *rgb,
                                   int width, int height,
                                   int stride)
{
    int y, x;

    if (w == NULL || rgb == NULL) return MY_ERR_PARAM;
    if (w->img == NULL || w->dpy == NULL) return MY_ERR_IO;
    if (stride <= 0) return MY_ERR_PARAM;

    for (y = 0; y < height; y++) {
        const my_u8 *src = rgb + (my_usize)y * (my_usize)stride;
        char        *dst = w->img->data + (my_usize)y * (my_usize)w->img->bytes_per_line;
        for (x = 0; x < width; x++) {
            my_u8 r = src[x*3 + 0];
            my_u8 g = src[x*3 + 1];
            my_u8 b = src[x*3 + 2];
            dst[x*4 + 0] = (char)b;
            dst[x*4 + 1] = (char)g;
            dst[x*4 + 2] = (char)r;
            dst[x*4 + 3] = (char)0;
        }
    }

    XPutImage(w->dpy, w->win, w->gc, w->img,
              0, 0, 0, 0, (unsigned)width, (unsigned)height);
    XFlush(w->dpy);
    return MY_OK;
}

#else
/* macOS: 需要 .m 文件调 Cocoa，纯 .c 不实现，直接报错 */
typedef struct { int unused; } MyWindow;
#error "macOS build requires a .m wrapper; pure .c not supported here"
#endif

/* =========================================================
 * 6. main
 * ========================================================= */

int main(int argc, char *argv[])
{
    AVFormatContext   *fmt_ctx;
    AVCodecContext    *dec_ctx;
    AVCodec           *dec;
    AVBufferRef       *hw_dev;
    AVFrame           *frame;
    AVFrame           *sw_frame;
    AVPacket          *pkt;
    struct SwsContext *sws;
    MyWindow           win;
    my_u8             *rgb;

    int                video_idx;
    int                ret;
    int                w, h;
    int                dst_linesize[4];

    enum AVHWDeviceType  dev_type;
    enum AVPixelFormat   hw_pix_fmt;

    /* ---- C89: all declarations above ---- */

    fmt_ctx   = NULL;
    dec_ctx   = NULL;
    dec       = NULL;
    hw_dev    = NULL;
    frame     = NULL;
    sw_frame  = NULL;
    pkt       = NULL;
    sws       = NULL;
    rgb       = NULL;
    w         = 0;
    h         = 0;

    if (argc < 2) {
        my_print("usage: player <file>\n");
        return 1;
    }

    /* ---- 1. FFmpeg init ---- */
    av_register_all();

    if (avformat_open_input(&fmt_ctx, argv[1], NULL, NULL) < 0) {
        my_print("open input failed\n");
        return 1;
    }
    if (avformat_find_stream_info(fmt_ctx, NULL) < 0) {
        my_print("find stream info failed\n");
        return 1;
    }

    video_idx = av_find_best_stream(fmt_ctx, AVMEDIA_TYPE_VIDEO,
                                    -1, -1, &dec, 0);
    if (video_idx < 0) {
        my_print("no video stream\n");
        return 1;
    }

    /* ---- 2. hw device ---- */
    dev_type   = pick_hw_device();
    hw_pix_fmt = hw_pix_fmt_of(dev_type);
    if (hw_pix_fmt == AV_PIX_FMT_NONE) {
        my_print("no hw pixel format\n");
        return 1;
    }

    ret = av_hwdevice_ctx_create(&hw_dev, dev_type, NULL, NULL, 0);
    if (ret < 0) {
        my_print("create hw device failed\n");
        return 1;
    }

    /* ---- 3. open decoder with hw ---- */
    dec_ctx = avcodec_alloc_context3(dec);
    if (dec_ctx == NULL) {
        my_print("alloc codec ctx failed\n");
        return 1;
    }
    avcodec_parameters_to_context(dec_ctx,
        fmt_ctx->streams[video_idx]->codecpar);

    g_hw_pix_fmt           = hw_pix_fmt;
    dec_ctx->get_format    = get_hw_format;
    dec_ctx->hw_device_ctx = av_buffer_ref(hw_dev);
    if (dec_ctx->hw_device_ctx == NULL) {
        my_print("hw_device_ctx ref failed\n");
        return 1;
    }

    /* check decoder supports this hw type */
    {
        int i;
        int supported = 0;
        for (i = 0; ; i++) {
            const AVCodecHWConfig *cfg = avcodec_get_hw_config(dec, i);
            if (cfg == NULL) break;
            if ((cfg->methods & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX) &&
                cfg->device_type == dev_type) {
                supported = 1;
                break;
            }
        }
        if (!supported) {
            my_print("decoder does not support hw\n");
            return 1;
        }
    }

    if (avcodec_open2(dec_ctx, dec, NULL) < 0) {
        my_print("open codec failed\n");
        return 1;
    }

    w = dec_ctx->width;
    h = dec_ctx->height;
    if (w <= 0 || h <= 0) {
        my_print("invalid dimensions\n");
        return 1;
    }

    /* ---- 4. window ---- */
    if (my_window_create(&win, "player", w, h) != MY_OK) {
        my_print("window create failed\n");
        return 1;
    }

    /* ---- 5. rgb buffer ---- */
    {
        my_usize bytes;
        if (my_mul_overflow_u((my_usize)w, (my_usize)h, &bytes) != MY_OK) {
            my_print("size overflow\n");
            return 1;
        }
        if (my_mul_overflow_u(bytes, 3, &bytes) != MY_OK) {
            my_print("size overflow\n");
            return 1;
        }
        rgb = (my_u8 *)my_alloc(bytes);
        if (rgb == NULL) {
            my_print("alloc rgb failed\n");
            return 1;
        }
    }

    frame    = av_frame_alloc();
    sw_frame = av_frame_alloc();
    pkt      = av_packet_alloc();
    if (frame == NULL || sw_frame == NULL || pkt == NULL) {
        my_print("alloc av objects failed\n");
        return 1;
    }

    /* ---- 6. main loop ---- */
    while (!win.quit && av_read_frame(fmt_ctx, pkt) >= 0) {
        if (pkt->stream_index != video_idx) {
            av_packet_unref(pkt);
            continue;
        }

        ret = avcodec_send_packet(dec_ctx, pkt);
        av_packet_unref(pkt);
        if (ret < 0) continue;

        while (1) {
            ret = avcodec_receive_frame(dec_ctx, frame);
            if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) break;
            if (ret < 0) break;

            if (frame->format != hw_pix_fmt) {
                av_frame_unref(frame);
                continue;
            }

            if (av_hwframe_transfer_data(sw_frame, frame, 0) < 0) {
                av_frame_unref(frame);
                continue;
            }

            /* (re)create sws once we know the cpu-side pix fmt */
            if (sws == NULL) {
                sws = sws_getContext(w, h, sw_frame->format,
                                     w, h, AV_PIX_FMT_RGB24,
                                     SWS_BILINEAR, NULL, NULL, NULL);
                if (sws == NULL) {
                    my_print("sws create failed\n");
                    av_frame_unref(sw_frame);
                    av_frame_unref(frame);
                    win.quit = 1;
                    break;
                }
            }

            dst_linesize[0] = w * 3;
            dst_linesize[1] = 0;
            dst_linesize[2] = 0;
            dst_linesize[3] = 0;

            sws_scale(sws,
                      (const uint8_t * const *)sw_frame->data,
                      sw_frame->linesize, 0, h,
                      &rgb, dst_linesize);

            av_frame_unref(sw_frame);
            av_frame_unref(frame);

            if (my_window_present_rgb24(&win, rgb, w, h, w * 3) != MY_OK) {
                my_print("present failed\n");
                win.quit = 1;
                break;
            }

            if (my_window_pump(&win)) {
                break;
            }
        }

        my_window_pump(&win);
    }

    /* ---- 7. cleanup ---- */
    if (sws != NULL) { sws_freeContext(sws); sws = NULL; }
    if (frame    != NULL) av_frame_free(&frame);
    if (sw_frame != NULL) av_frame_free(&sw_frame);
    if (pkt      != NULL) av_packet_free(&pkt);
    if (dec_ctx  != NULL) avcodec_free_context(&dec_ctx);
    if (fmt_ctx  != NULL) avformat_close_input(&fmt_ctx);
    if (hw_dev   != NULL) av_buffer_unref(&hw_dev);
    if (rgb      != NULL) { my_free(rgb); rgb = NULL; }

    my_window_destroy(&win);
    return 0;
}
