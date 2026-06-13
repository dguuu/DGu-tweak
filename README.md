# DGu Tweak

DGu Tweak is a Fabric add-on extracted from a modified Visible Traders fork.

Visible Traders remains responsible for locked villager trade display. DGu Tweak adds:

- Record and Query buttons on the villager trading screen.
- `/vt list [profession] [distance|level|recent]`
- `/vt detail <uuid>`
- Saved villager and wandering trader records, including position, dimension, profession, level, normal trades, locked trades, record time, and recorder name.

## Build

This project targets Minecraft 1.21.11 with Fabric Loader 0.19.3, Fabric API 0.139.4+1.21.11_unobfuscated, and Visible Traders.

Build this project with JDK 21 or newer. The produced mod targets Java 21 for compatibility with Minecraft launchers that run this instance on Java 21.

```powershell
.\gradlew.bat build
```

The output jar will be under `build/libs`.

## License

This project is based on Visible Traders by Ramixin and remains under the MIT license.
