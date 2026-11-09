#include <windows.h>

#define PATH_CAP 1024

static wchar_t g_bin[PATH_CAP];
static wchar_t g_path[PATH_CAP];

static int cut_last(wchar_t *path) {
    int i = lstrlenW(path) - 1;
    while (i >= 0 && path[i] != L'\\' && path[i] != L'/') {
        i--;
    }
    if (i <= 0) {
        return 0;
    }
    path[i] = 0;
    return 1;
}

static void preload(const wchar_t *name) {
    if (lstrlenW(g_bin) + lstrlenW(name) + 2 >= PATH_CAP) {
        return;
    }
    lstrcpyW(g_path, g_bin);
    lstrcatW(g_path, L"\\");
    lstrcatW(g_path, name);
    LoadLibraryW(g_path);
}

/* ProjectZomboid64.exe loads jvm.dll by full path, so instrument.dll's dependencies in the
   runtime's bin directory are not on the DLL search path when -javaagent is processed. */
__declspec(dllexport) int Agent_OnLoad(void *vm, char *options, void *reserved) {
    HMODULE jvm;
    DWORD n;
    UNREFERENCED_PARAMETER(vm);
    UNREFERENCED_PARAMETER(options);
    UNREFERENCED_PARAMETER(reserved);
    jvm = GetModuleHandleW(L"jvm.dll");
    if (jvm == NULL) {
        return 0;
    }
    n = GetModuleFileNameW(jvm, g_bin, PATH_CAP);
    if (n == 0 || n >= PATH_CAP) {
        return 0;
    }
    if (!cut_last(g_bin) || !cut_last(g_bin)) {
        return 0;
    }
    preload(L"java.dll");
    preload(L"jli.dll");
    return 0;
}

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID reserved) {
    UNREFERENCED_PARAMETER(instance);
    UNREFERENCED_PARAMETER(reason);
    UNREFERENCED_PARAMETER(reserved);
    return TRUE;
}
