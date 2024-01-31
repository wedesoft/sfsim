!include "MUI2.nsh"

!define COMPANYNAME "wedesoft"
!define APPNAME "sfsim"

Name "sfsim"

Outfile "sfsim-installer.exe"
InstallDir "$PROGRAMFILES64\sfsim"
InstallDirRegKey HKLM "Software\NSIS_sfsim" "Install_Dir"

!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Section "sfsim (required)"
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
  WriteRegStr HKLM "SOFTWARE\NSIS_sfsim" "Install_Dir" "$INSTDIR"
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
  CreateDirectory "$SMPROGRAMS\sfsim"
  CreateShortCut "$SMPROGRAMS\sfsim\Uninstall.lnk" "$INSTDIR\uninstall.exe" "" "$INSTDIR\uninstall.exe" 0
  CreateShortCut "$SMPROGRAMS\sfsim\sfsim.lnk" "$INSTDIR\sfsim.exe" "" "$INSTDIR\logo.ico" 0
SectionEnd

Section "Desktop Shortcut"
  CreateShortCut "$DESKTOP\sfsim.lnk" "$INSTDIR\sfsim.exe" "" "$INSTDIR\logo.ico" 0
SectionEnd

Section "Uninstall"
  ; Remove registry keys
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\${COMPANYNAME} ${APPNAME}"
  DeleteRegKey HKLM "SOFTWARE\NSIS_sfsim"
  ; Remove files and uninstaller
  RmDir /r "$INSTDIR\*.*"
  Rmdir "$INSTDIR"
  ; Remove shortcuts, if any
  Delete "$SMPROGRAMS\sfsim\*.*"
  Delete "$DESKTOP\sfsim.lnk"
  ; Remove directories used
  RMDir "$SMPROGRAMS\sfsim"
SectionEnd
