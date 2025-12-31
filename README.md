[![Release](https://jitpack.io/v/umjammer/vavi-sound-muap.svg)](https://jitpack.io/#umjammer/vavi-sound-muap)
[![Java CI](https://github.com/umjammer/vavi-sound-muap/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-sound-muap/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-sound-muap/actions/workflows/codeql.yml/badge.svg)](https://github.com/umjammer/vavi-sound-muap/actions/workflows/codeql.yml)
![Java](https://img.shields.io/badge/Java-21-b07219)

# vavi-sound-muap

🪕 Java version of Muap.

this is a fork of [muapDotNET](https://github.com/kuma4649/muapDotNET)

## Install

* [maven](https://jitpack.io/#umjammer/vavi-sound-muap)

## Usage

 [sample](src/test/java/TestCase.java)

## References

* https://packensoft.music.coocan.jp/

## TODO

* compiler
* test more samples

---

# [Original](https://github.com/kuma4649/muapDotNET)

This is the .NET version of muap98iv (NAX).

## Note
Please do not send any opinions, feedback, inquiries, reports, or other information regarding muapDotNET (including MDPlay and mml2vgmIDE) directly to Pakkunsoft (MyuApp affiliates).
Please contact me via DM on X or GitHub ISSUES.

## Summary
This is a port of muap98iv (NAX) for .NET.
It uses OPNAx1, OPN2x1, and CS4231x1 simultaneously.
The source files from muap98/iv (V6.41A) have been ported to C# as closely as possible.
Official Page

Pakkunsoft Page

## Features

This primarily ports the assembly (compilation) function and NAX (music performance program) of muap98iv.
Unfortunately, the IDE function, a major feature of muap, has not been ported.
You can compile .mus files and play .o files by launching the compiler and player from the console.

Usualy, it's easiest to use them with MDPlayer or mml2vgmIDE.

## Before Use

    -- T.B.D. --
    Run removeZoneIdent.bat included in the archive to remove the zone identifier.
    (A zone identifier is security information added to a file to prevent it from running when an unintentionally downloaded program is executed. It is added even when the download was intended, so it may cause problems.)
    -- T.B.D. --

## Build

You should be able to build without any problems using Visual Studio or similar.

## Quick Start

Compiling
Drop .mus files into the included Console.exe to compile.
Playing
Drop .o files into the included Player.exe to play.

## About Environment Variables

muapDotNET allows you to specify the paths to the files required for compilation and playback by setting the appropriate environment variables.
The following environment variables are available, so please set them as needed.
If not set, the current path will be referenced.
When using with MDPlayer or mml2vgmIDE, it is recommended to leave everything except UDP unspecified and place the standard muap TONES.DTA etc. in the same path as the program.
For user PCM, it is recommended to set UDP and have MDPlayer and mml2vgmIDE reference the same path, but if you have enough HDD space, you may also want to create appropriate folders in the same path as each program and place the files there.

### DTA
Specifies the location of muap's FM tone file (TONES.DTA).
Example:
DTA=C:\FM\muap\tone
If not set, it will default to DTA=.

### PCM
Specifies the location of muap's PCM tone files (the following four).
PCM.TBL ADPCM table file
PCM.DTA ADPCM data file
SSGPCM.DTA SSGPCM data file
SSGPCM.TBL SSGPCM table file
Example
PCM=C:\FM\muap\pcm
If not set, PCM=.

### UDP
Specifies the location of the tone file (optional) for Muap's user PCM sound source.
User PCM files will also be searched for in child paths of the specified path.
Example
UDP=C:\FM\muap\userpcm
If not set, UDP=.

### SUD
Specifies the location of the tone file (optional) for Muap's SUB user PCM sound source.
SUD will not search child paths.
Example
SUD=C:\FM\muap\userpcm
If not set, SUD=.

## Unique Features

### @J Command
  This is the J command commonly found in other drivers.
  This function skips playback until this command appears.
  Intended for use when programming.

### Added the ability to specify the path to open user-defined PCM files with the highest priority.
  This is intended to prioritize PCM files located in the .mus or .O file locations.
  If not found, the existing behavior will be restored.

## Copyright/Disclaimer
muapDotNET is licensed under the GPLv3.
Copyright is held by the author.
This software is provided without warranty, and the author assumes no responsibility for any damages resulting from the use of this software.

The source code of the following software has been modified for C# and is used.
Or code/dll is used.
These source/binaries are copyrighted by their respective authors.
For license details, please refer to the respective documentation.

 * muap98iv -> Custom License -> Code Modification
 * MDSound -> GPLv3 -> Used with dynamic dll linking
 * musicDriverInterface -> MIT -> Used with dynamic dll linking
 * NAudio -> MS-PL -> Used with dynamic dll linking

## Special Thanks
This tool is supported by the following people. We also use and reference the following software and websites.
Thank you very much.

 * muap98iv
 * Visual Studio Community 2022
 * Sakura Editor
