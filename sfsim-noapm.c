#include <windows.h>

int main(int argc, char *argv[]) {
  // Set the environment variable for this process
  SetEnvironmentVariableA("NEW_RELIC_AGENT_ENABLED", "false");

  STARTUPINFOA si;
  PROCESS_INFORMATION pi;

  ZeroMemory(&si, sizeof(si));
  si.cb = sizeof(si);
  ZeroMemory(&pi, sizeof(pi));

  // Command to launch sfsim.exe (expects sfsim.exe in the same folder)
  char cmd[] = "sfsim.exe";

  // Launch sfsim.exe, passing NULL for environment block so it inherits our variables
  if (!CreateProcessA(NULL, cmd, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi)) {
    return 1; // Failed to launch
  }

  // Clean up handles
  CloseHandle(pi.hProcess);
  CloseHandle(pi.hThread);

  return 0;
}
