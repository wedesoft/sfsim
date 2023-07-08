!include "MUI2.nsh"

!define COMPANYNAME "wedesoft"
!define APPNAME "sfsim25"

Name "sfsim25"

Outfile "sfsim25-installer.exe"
InstallDir "$PROGRAMFILES64\sfsim25"
InstallDirRegKey HKLM "Software\NSIS_sfsim25" "Install_Dir"

!insertmacro MUI_LANGUAGE "English"

!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

Section "sfsim25 (required)"
  SectionIn RO
  # Install JRE, JAR, config, and icon
  SetOutPath $INSTDIR
  File "logo.ico"
  File /r "out-windows\*.*"
  # Write data
  SetOutPath "$INSTDIR\data"
  File /r "data\*.*"
  # Create uninstaller
  setOutPath $INSTDIR
  writeUninstaller "$INSTDIR\uninstall.exe"
  # Write installation path
  WriteRegStr HKLM "SOFTWARE\NSIS_sfsim25" "Install_Dir" "$INSTDIR"
  # Register uninstaller
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${COMPANYNAME} ${APPNAME}" "DisplayName" "${APPNAME}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${COMPANYNAME} ${APPNAME}" "DisplayVersion" "0.1"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${COMPANYNAME} ${APPNAME}" "DisplayIcon" '"$INSTDIR\logo.ico"'
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${COMPANYNAME} ${APPNAME}" "Publisher" "${COMPANYNAME}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${COMPANYNAME} ${APPNAME}" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${COMPANYNAME} ${APPNAME}" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${COMPANYNAME} ${APPNAME}" "NoRepair" 1
SectionEnd

Section "Start Menu Shortcuts"
  CreateDirectory "$SMPROGRAMS\sfsim25"
  CreateShortCut "$SMPROGRAMS\sfsim25\Uninstall.lnk" "$INSTDIR\uninstall.exe" "" "$INSTDIR\uninstall.exe" 0
  CreateShortCut "$SMPROGRAMS\sfsim25\sfsim25.lnk" "$INSTDIR\sfsim25.exe" "" "$INSTDIR\logo.ico" 0
SectionEnd

Section "Desktop Shortcut"
  CreateShortCut "$DESKTOP\sfsim25.lnk" "$INSTDIR\sfsim25.exe" "" "$INSTDIR\logo.ico" 0
SectionEnd

Section "Uninstall"
  ; Remove registry keys
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${COMPANYNAME} ${APPNAME}"
  DeleteRegKey HKLM "SOFTWARE\NSIS_sfsim25"
  ; Remove files and uninstaller
  RmDir /r "$INSTDIR\*.*"
  Rmdir "$INSTDIR"
  ; Remove shortcuts, if any
  Delete "$SMPROGRAMS\sfsim25\*.*"
  Delete "$DESKTOP\sfsim25.lnk"
  ; Remove directories used
  RMDir "$SMPROGRAMS\sfsim25"
SectionEnd
