// TO PREPARE, CHECK YOUR CURRENT config in Preferences->Interpreter:
// you should be using the internal SCClassLibrary and HelpSource,
// not an external git repository!
// To test exporting extensions, it is best to have some quarks
// or other extensions installed, as seen in the includePaths.

// 0 - define name and location of new app + helper functions
(
// define the name for the new app and its location:
~newAppName = "BEASTmulch";
~newAppLocation = "~/Desktop".standardizePath;

// --- nothing to be changed below this line --- \\
// some helper functions
~readText = { |path|
    var f = File(path, "r"); var t = f.readAllString; f.close; t;
};
~writeText = { |path, string| File(path, "w").write(string).close; };
~copyFolder = { |orig, dest|
    var str = "cp -ir" + quote(orig) + quote(dest);
    str.postcs;
    unixCmd(str);
};

// make some needed paths and folders
~pathToThisApp = String.scDir.dirname.dirname;
~thisAppName = ~pathToThisApp.basename.splitext.first;
~pathToNewApp = ~newAppLocation +/+ ~newAppName ++ ".app";
~newAppResDir = ~pathToNewApp +/+ "Contents/Resources";
~newAppSupportDir = Platform.userConfigDir.dirname +/+ ~newAppName;
);


(
// 1. make a copy of thisApp with the newAppName:
// can take a while on slow harddisks.
// wait for RESULT = 0 to show!

if (File.exists(~pathToNewApp).not) {
    ~copyFolder.(~pathToThisApp, ~pathToNewApp);
    File.mkdir(~newAppSupportDir);
} { warn("supportDir % already exists.".format(~pathToNewApp)) };
);

(
// 2. fixups in the new app:
// a. fix the Info.plist file by replacing the name:
~infoPlistPath = ~pathToNewApp +/+ "Contents/Info.plist";
// get its string, replace the SC names, write again
~infoString = ~readText.(~infoPlistPath);
~executableIndex = ~infoString.find("<key>CFBundleExecutable</key>");
~nameToReplace = ~infoString.copyRange(
    ~infoString.find("<string>", offset: ~executableIndex) + 8,
    ~infoString.find("</string>", offset: ~executableIndex) - 1
);

"replacing bundleName % in plist file at these locations: "
.postf(~nameToReplace.cs);
~foundNameLocs = ~infoString.findAll(~nameToReplace).postln;

~newInfoString = ~infoString.replace(~nameToReplace, ~newAppName );
~writeText.(~infoPlistPath, ~newInfoString);
// ~readText.(~infoPlistPath);

// b. rename the binary file:
"renaming macos binary to: ".post;
// fixups in the new app - 2. rename the binary inside the app folder
unixCmd("mv -i"
    + quote(~pathToNewApp +/+ "Contents/MacOS/" +/+ ~nameToReplace)
    + quote(~pathToNewApp +/+ "Contents/MacOS/" ++ ~newAppName).postln
);
);


// 3. *** time for the first wakeup kiss *** //
unixCmd("open" + ~pathToNewApp);

// ... and independence test:
// IN THE NEW APP, run this code:
Platform.userAppSupportDir.postcs;


///////////////////////////////////////////////////////////////////////////


(
// 4. write a class extension file to look for the startupFile
// in the app, in String.scDir for self-containment.
~overDir = ~newAppResDir +/+ "SCClassLibrary/SystemOverwrites";
File.mkdir(~overDir);
~writeText.value(~overDir +/+ "extModStartupFile.sc",
    "+ OSXPlatform {
    startupFiles {
        ^[String.scDir +/+ \"startup.scd\"];
    }
}
"
);
~overDir.openOS;
)

(
// 5. write a basic startupFile:
~startupCode =
"// basic example startup file for osx standalone.
// avoid clash with default SC server
s.addr.port = 57105;

// boots server and plays a little sound
s.waitForBoot {
    {
        Line.kr(1, 0, 10, doneAction: 2) * SinOsc.ar(1000)
        * EnvGen.ar(Env.perc(0.01, 0.25), Impulse.kr(2, [0, 0.5]), 0.1)

    }.play;
};
";
~writeText.(~newAppResDir +/+ "startup.scd", ~startupCode);
(~newAppResDir +/+ "startup.scd").openOS;
)

// *** time for a second wakeup kiss *** //
// should run the startup file and play a sound:
unixCmd("open" + ~pathToNewApp);


// 6. For larger projects:
//    make an example project folder with a loadfile in it,
//    and a startup file that points to the loadfile.
(
~projDirName = ~newAppName ++ "_files";
~projDir = ~newAppResDir +/+ ~projDirName;
File.mkdir(~projDir);
~projDir.openOS;

~loadFileCode =
"\"*** startup file for % loading.\".postln;
\"*** My Platform.userAppSupportDir is: \".postln;
Platform.userAppSupportDir.postcs;
thisProcess.nowExecutingPath.openOS;

// configure server options here if needed:
s.options.memSize = 8192 * 16;
// move server port away from default (57110)
// to avoid interference/clashes with SuperCollider itself:
s.addr.port = 57105;

s.waitForBoot {
    \"*** server booted, plays ping.\".postln;
    Env.perc.test;

    \"*** loading files next ? \".postln;
    // put your loading sequence here:
    // \"loadX.scd\".loadRelative;
    // s.sync; // e.g. when loading buffers
     // 1.wait;
     // \"loadY.scd\".loadRelative;
};"
.format(~newAppName);

~loadfilepath = ~projDir +/+ "00_loadMe.scd";
~writeText.value(~loadfilepath, ~loadFileCode);
~loadfilepath.openOS;


// write a startupFile that points to the load file:
~startupCode =
"// example startup file for osx standalone.
// running loadfile in this project folder:
%.loadRelative;
".format((~projDirName +/+ ~loadfilepath.basename).cs);

~writeText.(~newAppResDir +/+ "startup.scd", ~startupCode);
(~newAppResDir +/+ "startup.scd").openOS;
)

// *** time for the third wakeup kiss *** //
unixCmd("open" + ~pathToNewApp);

// this should open the new app, and its loadfile!


// 7. create an internal extensions folder inside the new app,
// and copy all the currently active includePaths there:
(
~newAppQuarksDir = ~newAppResDir +/+ "InternalExtensions";

// copy all folders in my current quarks includePaths
// to Resources/SCClassLibrary to freeze them:
File.mkdir(~newAppQuarksDir);

~pathsToCopy = LanguageConfig.includePaths.reject { |path|
    ["SCClassLibrary", "HelpSource"].includesEqual(path.basename)
};

~pathsToCopy.collect(_.basename).sort.printcsAll;

if (~newAppQuarksDir.pathMatch.notEmpty) {
    "/*** copying Quarks : ***/".postln;
    ~pathsToCopy.do { |path|
        unixCmd(
            "cd" + quote(~newAppQuarksDir) ++ ";" ++
            " cp -ir" + quote(path).postln + ".");
    };
};
"";
)
// and now the new app folder contains all quarks:
~newAppQuarksDir.openOS;

// open the new app:
unixCmd("open" + ~pathToNewApp);

// AND IN THE NEW APP, run this to create a self-contained startup config file:
(
// use the internal extensions - repeat this when you move the app!
LanguageConfig.addIncludePath(String.scDir +/+ "InternalExtensions");
// exclude the default
LanguageConfig.addExcludePath(Platform.userExtensionDir);
LanguageConfig.addExcludePath(Platform.systemExtensionDir);
LanguageConfig.store;

// see if the internal path was stored OK:
LanguageConfig.includePaths.last;
// this should not be empty:
LanguageConfig.includePaths.last.pathMatch.postln;
)

// STILL IN THE NEW APP, REBOOT THE INTERPRETER NOW!
// - the added quarks should now be present.
